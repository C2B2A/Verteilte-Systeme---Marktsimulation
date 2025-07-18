package main.marketplace;

import main.messaging.MessageHandler;
import main.messaging.MessageTypes.*;
import main.simulation.ConfigLoader;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQException;

import java.util.*;
import java.util.concurrent.*;

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
        "PB", Arrays.asList("S1", "S5"),  // B bei S1 und S5!
        "PC", Arrays.asList("S2", "S3"),  // C bei S2 und S3!
        "PD", Arrays.asList("S2", "S4"),  // D bei S2 und S4!
        "PE", Arrays.asList("S3", "S4"),  // E bei S3 und S4!
        "PF", Arrays.asList("S5")
    );
    
    // Verfolgt bereits versuchte Seller für Failover
    private final Map<String, Set<String>> triedSellers = new ConcurrentHashMap<>();
    
    public OrderProcessor(String marketplaceId, SagaManager sagaManager) {
        this.marketplaceId = marketplaceId;
        this.sagaManager = sagaManager;
        this.context = new ZContext();
        this.executor = Executors.newFixedThreadPool(10);
        this.sellerPorts = new HashMap<>(DEFAULT_SELLERS);
    }
    
    /**
     * Verarbeitet eine neue Bestellung
     */
    public void processOrder(OrderRequest order) {
        System.out.println("\n[OrderProcessor] Neue Bestellung: " + order.orderId);
        
        // Starte SAGA
        SagaManager.OrderSaga saga = sagaManager.startSaga(order);
        
        // Initialisiere Tracking für diese Order
        String orderKey = order.orderId;
        triedSellers.put(orderKey, ConcurrentHashMap.newKeySet());
        
        // Sende parallele Reservierungsanfragen
        for (OrderRequest.ProductOrder productOrder : order.products) {
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
        
        String orderKey = orderId;
        Set<String> tried = triedSellers.get(orderKey);
        
        // Versuche alle möglichen Seller
        for (String sellerId : possibleSellers) {
            if (tried.contains(sellerId + "-" + productId)) {
                continue; // Bereits versucht
            }
            
            tried.add(sellerId + "-" + productId);
            
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
    private boolean sendReservationRequest(String orderId, String productId, 
                                         int quantity, String sellerId) {
        Integer port = sellerPorts.get(sellerId);
        if (port == null) {
            System.err.println("[OrderProcessor] Unbekannter Seller: " + sellerId);
            return false;
        }
        
        try {
            // Erstelle Socket für diesen Request
            ZMQ.Socket socket = context.createSocket(SocketType.REQ);
            socket.setReceiveTimeOut(ConfigLoader.getTimeout());
            socket.connect("tcp://localhost:" + port);
            
            // Erstelle Anfrage
            ReserveRequest request = new ReserveRequest();
            request.orderId = orderId;
            request.productId = productId;
            request.quantity = quantity;
            request.marketplaceId = marketplaceId;
            
            // Sende
            String json = MessageHandler.toJson(request);
            System.out.println("[OrderProcessor] Sende an " + sellerId + 
                             " (Port " + port + "): " + json);
            socket.send(json);
            
            // Warte auf Antwort
            String response = socket.recvStr();
            if (response == null || response.isEmpty()) {
                // Timeout - Seller nicht erreichbar
                System.out.println("[OrderProcessor] Timeout von " + sellerId);
                socket.close();
                return false; // Nächsten Seller versuchen
            } else {
                // Antwort erhalten
                ReserveResponse reserveResponse = MessageHandler.fromJson(
                    response, ReserveResponse.class);
                if (reserveResponse != null) {
                    sagaManager.handleReservationResponse(reserveResponse);
                    
                    // Bei FAILED mit Grund "nicht im Sortiment" -> nächsten Seller versuchen
                    if ("FAILED".equals(reserveResponse.status) && 
                        "Produkt nicht im Sortiment".equals(reserveResponse.reason)) {
                        socket.close();
                        return false; // Nächsten Seller versuchen
                    }
                    
                    socket.close();
                    return true; // Erfolgreich oder endgültig fehlgeschlagen
                }
            }
            
            socket.close();
            
        } catch (ZMQException e) {
            System.err.println("[OrderProcessor] ZMQ Fehler bei " + sellerId + ": " + e.getMessage());
            return false; // Nächsten Seller versuchen
        }
        
        return false;
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
        
        // Cleanup Tracking
        triedSellers.remove(saga.orderId);
        
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
        
        for (ReserveResponse reservation : saga.getSuccessfulReservations()) {
            executor.submit(() -> {
                ConfirmRequest confirm = new ConfirmRequest();
                confirm.orderId = saga.orderId;
                confirm.productId = reservation.productId;
                confirm.sellerId = reservation.sellerId;
                
                sendConfirmation(confirm, reservation.sellerId);
            });
        }
        
        sagaManager.completeSaga(saga.orderId, true);
        System.out.println("[OrderProcessor] Order " + saga.orderId + 
                         " erfolgreich abgeschlossen!");
    }
    
    /**
     * Führt Rollback für fehlgeschlagene Bestellung aus
     */
    private void rollbackOrder(SagaManager.OrderSaga saga) {
        System.out.println("\n[OrderProcessor] Rollback für Order " + saga.orderId);
        
        List<ReserveResponse> toRollback = sagaManager.getReservationsForRollback(saga.orderId);
        CountDownLatch latch = new CountDownLatch(toRollback.size());
        
        for (ReserveResponse reservation : toRollback) {
            executor.submit(() -> {
                try {
                    CancelRequest cancel = new CancelRequest();
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
    }
    
    /**
     * Sendet Bestätigung an Seller
     */
    private void sendConfirmation(ConfirmRequest confirm, String sellerId) {
        Integer port = sellerPorts.get(sellerId);
        if (port == null) return;
        
        try {
            ZMQ.Socket socket = context.createSocket(SocketType.REQ);
            socket.setReceiveTimeOut(ConfigLoader.getTimeout());
            socket.connect("tcp://localhost:" + port);
            
            String json = MessageHandler.toJson(confirm);
            System.out.println("[OrderProcessor] Sende Bestätigung an " + 
                             sellerId + ": " + json);
            socket.send(json);
            
            String response = socket.recvStr();
            if (response != null && !response.isEmpty()) {
                System.out.println("[OrderProcessor] Bestätigung erhalten von " + sellerId);
            }
            
            socket.close();
        } catch (ZMQException e) {
            System.err.println("[OrderProcessor] Fehler bei Bestätigung: " + e.getMessage());
        }
    }
    
    /**
     * Sendet Stornierung an Seller
     */
    private void sendCancellation(CancelRequest cancel, String sellerId) {
        Integer port = sellerPorts.get(sellerId);
        if (port == null) return;
        
        try {
            ZMQ.Socket socket = context.createSocket(SocketType.REQ);
            socket.setReceiveTimeOut(ConfigLoader.getTimeout());
            socket.connect("tcp://localhost:" + port);
            
            String json = MessageHandler.toJson(cancel);
            System.out.println("[OrderProcessor] Sende Stornierung an " + 
                             sellerId + ": " + json);
            socket.send(json);
            
            String response = socket.recvStr();
            if (response != null && !response.isEmpty()) {
                System.out.println("[OrderProcessor] Stornierung bestätigt von " + sellerId);
            }
            
            socket.close();
        } catch (ZMQException e) {
            System.err.println("[OrderProcessor] Fehler beim Rollback: " + e.getMessage());
        }
    }
    
    /**
     * Cleanup
     */
    public void shutdown() {
        executor.shutdown();
        context.close();
    }
}