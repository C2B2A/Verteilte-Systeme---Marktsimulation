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
 * Nutzt Thread-Pool für parallele Reservierungen
 */
public class OrderProcessor {
    private final String marketplaceId;
    private final SagaManager sagaManager;
    private final ZContext context;
    private final ExecutorService executor;
    private final Map<String, Integer> sellerPorts;
    
    // Seller-Konfiguration (würde normalerweise aus Config kommen)
    private static final Map<String, Integer> DEFAULT_SELLERS = Map.of(
        "S1", 5556,
        "S2", 5557,
        "S3", 5558,
        "S4", 5559,
        "S5", 5560
    );
    
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
        
        // Ermittle welcher Seller welches Produkt hat
        Map<String, String> productToSeller = mapProductsToSellers(order);
        
        // Sende parallele Reservierungsanfragen
        for (OrderRequest.ProductOrder productOrder : order.products) {
            String sellerId = productToSeller.get(productOrder.productId);
            if (sellerId == null) {
                System.err.println("[OrderProcessor] Kein Seller für Produkt " + 
                                 productOrder.productId);
                sagaManager.handleReservationTimeout(order.orderId, productOrder.productId);
                continue;
            }
            
            // Registriere ausstehende Reservierung
            sagaManager.registerPendingReservation(order.orderId, productOrder.productId);
            
            // Sende asynchron
            executor.submit(() -> sendReservationRequest(
                order.orderId, 
                productOrder.productId, 
                productOrder.quantity, 
                sellerId
            ));
        }
        
        // Starte Überwachung für diese Bestellung
        executor.submit(() -> monitorOrder(saga));
    }
    
    /**
     * Sendet Reservierungsanfrage an einen Seller
     */
    private void sendReservationRequest(String orderId, String productId, 
                                      int quantity, String sellerId) {
        Integer port = sellerPorts.get(sellerId);
        if (port == null) {
            System.err.println("[OrderProcessor] Unbekannter Seller: " + sellerId);
            sagaManager.handleReservationTimeout(orderId, productId);
            return;
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
                // Timeout
                System.out.println("[OrderProcessor] Timeout von " + sellerId);
                sagaManager.handleReservationTimeout(orderId, productId);
            } else {
                // Antwort erhalten
                ReserveResponse reserveResponse = MessageHandler.fromJson(
                    response, ReserveResponse.class);
                if (reserveResponse != null) {
                    sagaManager.handleReservationResponse(reserveResponse);
                }
            }
            
            socket.close();
            
        } catch (ZMQException e) {
            System.err.println("[OrderProcessor] ZMQ Fehler: " + e.getMessage());
            sagaManager.handleReservationTimeout(orderId, productId);
        }
    }
    
    /**
     * Überwacht eine Bestellung und führt ggf. Rollback aus
     */
    private void monitorOrder(SagaManager.OrderSaga saga) {
        // Warte bis alle Reservierungen abgeschlossen sind
        int maxWaitTime = ConfigLoader.getTimeout() * 2;
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
        // Implementierung ähnlich wie sendReservationRequest
        System.out.println("[OrderProcessor] Sende Bestätigung an " + sellerId);
        // TODO: Implementieren
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
     * Mappt Produkte zu Sellern (vereinfacht)
     */
    private Map<String, String> mapProductsToSellers(OrderRequest order) {
        Map<String, String> mapping = new HashMap<>();
        
        // Einfache Zuordnung: Produkt-ID enthält Seller-ID
        // z.B. "PS1A" -> Seller S1
        for (OrderRequest.ProductOrder product : order.products) {
            String productId = product.productId;
            if (productId.startsWith("PS") && productId.length() >= 4) {
                String sellerId = productId.substring(1, 3); // "PS1A" -> "S1"
                mapping.put(productId, sellerId);
            }
        }
        
        return mapping;
    }
    
    /**
     * Cleanup
     */
    public void shutdown() {
        executor.shutdown();
        context.close();
    }
}