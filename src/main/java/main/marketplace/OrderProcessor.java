package main.marketplace;

import main.messaging.Messages;
import main.simulation.ConfigLoader;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQException;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;

/**
 * Verarbeitet Bestellungen und kommuniziert mit Sellern
 * Unterstützt Failover wenn ein Produkt bei mehreren Sellern verfügbar ist
 */
public class OrderProcessor {
    private final String marketplaceId;
    private final SagaManager sagaManager;
    private final ZContext context;
    private final ExecutorService executor;
    private final Map<String, Integer> sellerPorts;
    private final Map<String, ZMQ.Socket> sellerSockets; // Ein Socket pro Seller
    private final ZMQ.Socket dealerSocket; // Für direkte Kommunikation
    private final int networkLatencyMs = 50; // Simulierte Latenz (konfigurierbar)
    
    // Seller-Port-Konfiguration
    private static final Map<String, Integer> DEFAULT_SELLERS = Map.of(
        "S1", 5556,
        "S2", 5557,
        "S3", 5558,
        "S4", 5559,
        "S5", 5560
    );
    
    // Produkt-zu-Seller Mapping (korrekte Verteilung)
    private static final Map<String, List<String>> PRODUCT_SELLER_MAP = Map.of(
        "PA", Arrays.asList("S1"),
        "PB", Arrays.asList("S1"),
        "PC", Arrays.asList("S2"),  
        "PD", Arrays.asList("S2"),  
        "PE", Arrays.asList("S3"),  
        "PF", Arrays.asList("S3"),
        "PG", Arrays.asList("S4"),
        "PH", Arrays.asList("S4"),
        "PI", Arrays.asList("S5"),
        "PJ", Arrays.asList("S5")
    );
    
    private BiConsumer<String, String> statusCallback; // orderId, statusMessage

    public void setStatusCallback(BiConsumer<String, String> callback) {
        this.statusCallback = callback;
    }

    private final ScheduledExecutorService scheduler; // For periodic SAGA cleanup
    private final long sagaTimeoutMs = ConfigLoader.getTimeout() * 4; // Timeout for active SAGAs

    public OrderProcessor(String marketplaceId, SagaManager sagaManager) {
        this.marketplaceId = marketplaceId;
        this.sagaManager = sagaManager;
        this.context = new ZContext();
        this.executor = Executors.newFixedThreadPool(10);
        this.sellerPorts = new HashMap<>(DEFAULT_SELLERS);
        this.sellerSockets = new HashMap<>();
        this.dealerSocket = context.createSocket(SocketType.DEALER);
        this.dealerSocket.setIdentity(marketplaceId.getBytes(ZMQ.CHARSET));
        
        // Erstelle einen DEALER Socket für jeden Seller
        for (Map.Entry<String, Integer> entry : DEFAULT_SELLERS.entrySet()) {
            String sellerId = entry.getKey();
            int port = entry.getValue();
            
            ZMQ.Socket socket = context.createSocket(SocketType.DEALER);
            // Setze die Identität für den Socket!
            socket.setIdentity(marketplaceId.getBytes(ZMQ.CHARSET)); // z.B. "Marketplace-1"
            socket.connect("tcp://localhost:" + port);
            sellerSockets.put(sellerId, socket);
        }
        
        // Starte Empfangs-Thread für jeden Seller
        for (Map.Entry<String, ZMQ.Socket> entry : sellerSockets.entrySet()) {
            String sellerId = entry.getKey();
            ZMQ.Socket socket = entry.getValue();
            executor.submit(() -> receiveSellerResponses(sellerId, socket));
        }

        // Starte periodische Überprüfung für SAGA-Timeouts
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::cleanupTimedOutSagas, sagaTimeoutMs, sagaTimeoutMs, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Verarbeitet eine neue Bestellung
     */
    public void processOrder(Messages.OrderRequest order) {
        System.out.println("\n[OrderProcessor] Neue Bestellung: " + order.orderId);
        
        // Starte SAGA
        SagaManager.OrderSaga saga = sagaManager.startSaga(order);
        
        // Sende parallele Reservierungsanfragen
        for (Messages.OrderRequest.ProductOrder productOrder : order.products) {
            // Registriere ausstehende Reservierung
            sagaManager.registerPendingReservation(order.orderId, productOrder.productId);
            
            // Starte Reservierungsversuch
            executor.submit(() -> tryReserveProduct(
                order.orderId, 
                productOrder.productId, 
                productOrder.quantity
            ));
        }
        
        // Starte Überwachung für diese Bestellung
        executor.submit(() -> monitorOrder(saga));
    }
    
    /**
     * Versucht ein Produkt zu reservieren, mit Failover zu alternativen Sellern
     */
    private void tryReserveProduct(String orderId, String productId, int quantity) {
        List<String> possibleSellers = PRODUCT_SELLER_MAP.getOrDefault(productId, new ArrayList<>());
        
        if (possibleSellers.isEmpty()) {
            System.err.println("[OrderProcessor] Kein Seller für Produkt " + productId);
            sagaManager.handleReservationTimeout(orderId, productId);
            return;
        }
        
        // Versuche alle möglichen Seller
        for (String sellerId : possibleSellers) {
            System.out.println("[OrderProcessor] Versuche Seller " + sellerId + 
                             " für Produkt " + productId);
            
            boolean success = sendReservationRequest(orderId, productId, quantity, sellerId);
            
            if (success) {
                // Erfolgreich reserviert oder endgültig fehlgeschlagen
                return;
            }
            
            // Bei Timeout oder temporärem Fehler: Nächsten Seller versuchen
            System.out.println("[OrderProcessor] Seller " + sellerId + 
                             " nicht verfügbar, versuche Alternative...");
        }
        
        // Alle Seller versucht, keiner konnte liefern
        System.err.println("[OrderProcessor] Kein Seller konnte " + productId + " liefern");
        sagaManager.handleReservationTimeout(orderId, productId);
    }
    
    /**
     * Sendet Reservierungsanfrage an einen spezifischen Seller
     * @return true wenn erfolgreich reserviert oder endgültig fehlgeschlagen
     */
    private boolean sendReservationRequest(String orderId, String productId, int quantity, String sellerId) {
        try {
            Thread.sleep(networkLatencyMs);
            
            Messages.ReserveRequest request = new Messages.ReserveRequest();
            request.orderId = orderId;
            request.productId = productId;
            request.quantity = quantity;
            request.marketplaceId = marketplaceId;
            
            String json = Messages.toJson(request);
            System.out.println("[OrderProcessor] Sende an " + sellerId + ": " + json);
            
            // Hole den richtigen Socket für diesen Seller
            ZMQ.Socket sellerSocket = sellerSockets.get(sellerId);
            if (sellerSocket == null) {
                System.err.println("[OrderProcessor] Kein Socket für Seller " + sellerId);
                return false;
            }
            
            // Sende direkt über den dedizierten Socket
            sellerSocket.send(json);
            return true;
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ZMQException e) {
            System.err.println("[OrderProcessor] ZMQ Fehler bei " + sellerId + ": " + e.getMessage());
            return false;
        }
        return false;
    }
    
    /**
     * Empfangs-Thread für einen spezifischen Seller
     */
    private void receiveSellerResponses(String sellerId, ZMQ.Socket socket) {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                String response = socket.recvStr(0);
                if (response != null && !response.isEmpty()) {
                    System.out.println("[OrderProcessor] Antwort von " + sellerId + ": " + response);
                    
                    String type = Messages.getMessageType(response);
                    switch (type) {
                        case "ReserveResponse":
                            Messages.ReserveResponse reserveResponse = Messages.fromJson(response, Messages.ReserveResponse.class);
                            sagaManager.handleReservationResponse(reserveResponse);
                            break;
                        case "CancelResponse":
                            // Kann optional verarbeitet werden
                            break;
                        case "ConfirmResponse":
                            // Kann optional verarbeitet werden
                            break;
                        default:
                            System.err.println("[OrderProcessor] Unbekannter Nachrichtentyp: " + type);
                            break;
                    }
                }
            } catch (Exception e) {
                if (!Thread.currentThread().isInterrupted()) {
                    System.err.println("[OrderProcessor] Fehler beim Empfang von " + sellerId + ": " + e.getMessage());
                }
            }
        }
    }
    
    /**
     * Überwacht eine Bestellung und führt ggf. Rollback aus
     */
    private void monitorOrder(SagaManager.OrderSaga saga) {
        // Warte bis alle Reservierungen abgeschlossen sind
        int maxWaitTime = ConfigLoader.getTimeout() * 3; // Mehr Zeit für Failover
        long startTime = System.currentTimeMillis();
        
        while (!saga.isComplete() && 
               System.currentTimeMillis() - startTime < maxWaitTime) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        
        // Prüfe Status
        if (saga.status == SagaManager.SagaStatus.RESERVED) {
            // Alle erfolgreich - sende Bestätigungen
            confirmOrder(saga);
        } else if (saga.status == SagaManager.SagaStatus.COMPENSATING || 
                   saga.hasFailures()) {
            // Fehler aufgetreten - Rollback
            rollbackOrder(saga);
        }
    }
    
    /**
     * Bestätigt eine erfolgreiche Bestellung
     */
    private void confirmOrder(SagaManager.OrderSaga saga) {
        System.out.println("\n[OrderProcessor] Bestätige Order " + saga.orderId);

        for (Messages.ReserveResponse reservation : saga.getSuccessfulReservations()) {
            executor.submit(() -> {
                Messages.ConfirmRequest confirm = new Messages.ConfirmRequest();
                confirm.orderId = saga.orderId;
                confirm.productId = reservation.productId;
                confirm.sellerId = reservation.sellerId;
                sendConfirmation(confirm, reservation.sellerId);
            });
            // Entferne die Reservierung nach Bestätigung
            saga.reservations.remove(reservation.productId);
        }

        sagaManager.completeSaga(saga.orderId, true);
        System.out.println("[OrderProcessor] Order " + saga.orderId + " erfolgreich abgeschlossen!");

        // Nach Abschluss der Bestellung:
        if (statusCallback != null) {
            // Rückantwort enthält jetzt auch die marketplaceId
            statusCallback.accept(saga.orderId, marketplaceId + ": Bestellung " + saga.orderId + " erfolgreich abgeschlossen");
        }
    }
    
    /**
     * Führt Rollback für fehlgeschlagene Bestellung aus
     */
    private void rollbackOrder(SagaManager.OrderSaga saga) {
        System.out.println("\n[OrderProcessor] Rollback für Order " + saga.orderId);
        
        List<Messages.ReserveResponse> toRollback = sagaManager.getReservationsForRollback(saga.orderId);
        CountDownLatch latch = new CountDownLatch(toRollback.size());
        
        for (Messages.ReserveResponse reservation : toRollback) {
            executor.submit(() -> {
                try {
                    Messages.CancelRequest cancel = new Messages.CancelRequest();
                    cancel.orderId = saga.orderId;
                    cancel.productId = reservation.productId;
                    cancel.sellerId = reservation.sellerId;
                    
                    sendCancellation(cancel, reservation.sellerId);
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // Warte auf alle Rollbacks
        try {
            latch.await(ConfigLoader.getTimeout(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        sagaManager.completeSaga(saga.orderId, false);
        System.out.println("[OrderProcessor] Order " + saga.orderId + 
                         " wurde zurückgerollt!");

        // Nach Abschluss der Bestellung:
        if (statusCallback != null) {
            statusCallback.accept(saga.orderId, marketplaceId + ": Bestellung " + saga.orderId + " fehlgeschlagen (Rollback durchgeführt)");
        }
    }
    
    /**
     * Sendet Bestätigung an Seller
     */
    private void sendConfirmation(Messages.ConfirmRequest confirm, String sellerId) {
        try {
            Thread.sleep(networkLatencyMs);
            
            String json = Messages.toJson(confirm);
            System.out.println("[OrderProcessor] Sende Bestätigung an " + sellerId + ": " + json);
            
            ZMQ.Socket sellerSocket = sellerSockets.get(sellerId);
            if (sellerSocket != null) {
                sellerSocket.send(json);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ZMQException e) {
            System.err.println("[OrderProcessor] Fehler bei Bestätigung: " + e.getMessage());
        }
    }
    
    /**
     * Sendet Stornierung an Seller
     */
    private void sendCancellation(Messages.CancelRequest cancel, String sellerId) {
        try {
            Thread.sleep(networkLatencyMs);
            
            String json = Messages.toJson(cancel);
            System.out.println("[OrderProcessor] Sende Stornierung an " + sellerId + ": " + json);
            
            ZMQ.Socket sellerSocket = sellerSockets.get(sellerId);
            if (sellerSocket != null) {
                sellerSocket.send(json);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ZMQException e) {
            System.err.println("[OrderProcessor] Fehler beim Rollback: " + e.getMessage());
        }
    }
    
    /**
     * Entfernt SAGAs, die zu lange aktiv sind und führt Rollback beim Seller durch
     */
    private void cleanupTimedOutSagas() {
        List<SagaManager.OrderSaga> timedOutSagas = sagaManager.getTimedOutSagas(sagaTimeoutMs);
        for (SagaManager.OrderSaga saga : timedOutSagas) {
            System.out.println("[OrderProcessor] Entferne Order " + saga.orderId + " wegen Timeout.");

            // Rollback für alle erfolgreichen Reservierungen
            List<Messages.ReserveResponse> toRollback = sagaManager.getReservationsForRollback(saga.orderId);
            CountDownLatch latch = new CountDownLatch(toRollback.size());
            for (Messages.ReserveResponse reservation : toRollback) {
                executor.submit(() -> {
                    try {
                        Messages.CancelRequest cancel = new Messages.CancelRequest();
                        cancel.orderId = saga.orderId;
                        cancel.productId = reservation.productId;
                        cancel.sellerId = reservation.sellerId;
                        sendCancellation(cancel, reservation.sellerId);
                    } finally {
                        latch.countDown();
                    }
                });
            }
            try {
                latch.await(ConfigLoader.getTimeout(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            sagaManager.removeSaga(saga.orderId);
            if (statusCallback != null) {
                statusCallback.accept(saga.orderId, "Bestellung " + saga.orderId + " entfernt wegen Timeout (Rollback durchgeführt)");
            }
        }
    }

    /**
     * Cleanup
     */
    public void shutdown() {
        executor.shutdown();
        scheduler.shutdown(); // Stop periodic cleanup
        
        // Schließe alle Seller-Sockets
        for (ZMQ.Socket socket : sellerSockets.values()) {
            socket.close();
        }
        
        context.close();
    }
}