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
 * Processes orders and communicates with sellers
 * Supports failover when a product is available from multiple sellers
 */
public class OrderProcessor {
    private final String marketplaceId;
    private final SagaManager sagaManager;
    private final ZContext context;
    private final ExecutorService executor;
    private final Map<String, Integer> sellerPorts;
    private final Map<String, ZMQ.Socket> sellerSockets; // One socket per seller
    private final ZMQ.Socket dealerSocket; // For direct communication
    private final int networkLatencyMs = 50; // Simulated latency (configurable)

    // Seller port configuration
    private static final Map<String, Integer> DEFAULT_SELLERS = Map.of(
        "S1", 5556,
        "S2", 5557,
        "S3", 5558,
        "S4", 5559,
        "S5", 5560
    );

    // Product-to-seller mapping (correct distribution)
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

        // Create a DEALER socket for each seller
        for (Map.Entry<String, Integer> entry : DEFAULT_SELLERS.entrySet()) {
            String sellerId = entry.getKey();
            int port = entry.getValue();
            
            ZMQ.Socket socket = context.createSocket(SocketType.DEALER);
            // Set identity for the socket
            socket.setIdentity(marketplaceId.getBytes(ZMQ.CHARSET)); // e.g. "Marketplace-1"
            socket.connect("tcp://localhost:" + port);
            sellerSockets.put(sellerId, socket);
        }

        // Start receiving thread for each seller
        for (Map.Entry<String, ZMQ.Socket> entry : sellerSockets.entrySet()) {
            String sellerId = entry.getKey();
            ZMQ.Socket socket = entry.getValue();
            executor.submit(() -> receiveSellerResponses(sellerId, socket));
        }

        // Start periodic check for SAGA timeouts
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::cleanupTimedOutSagas, sagaTimeoutMs, sagaTimeoutMs, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Processes a new order
     */
    public void processOrder(Messages.OrderRequest order) {
        System.out.println("[OrderProcessor] New order: " + order.orderId);

        // Start SAGA
        SagaManager.OrderSaga saga = sagaManager.startSaga(order);

        // Send parallel reservation requests
        for (Messages.OrderRequest.ProductOrder productOrder : order.products) {
            // Register pending reservation
            sagaManager.registerPendingReservation(order.orderId, productOrder.productId);

            // Start reservation attempt
            executor.submit(() -> tryReserveProduct(
                order.orderId, 
                productOrder.productId, 
                productOrder.quantity
            ));
        }

        // Start monitoring for this order
        executor.submit(() -> monitorOrder(saga));
    }
    
    /**
     * Attempts to reserve a product, with failover to alternative sellers
     */
    private void tryReserveProduct(String orderId, String productId, int quantity) {
        List<String> possibleSellers = PRODUCT_SELLER_MAP.getOrDefault(productId, new ArrayList<>());
        
        if (possibleSellers.isEmpty()) {
            System.err.println("[OrderProcessor] No seller for product " + productId);
            sagaManager.handleReservationTimeout(orderId, productId);
            return;
        }
        
        // Try all possible sellers
        for (String sellerId : possibleSellers) {
            System.out.println("[OrderProcessor] Try Seller " + sellerId + 
                             " for product " + productId);
            
            boolean success = sendReservationRequest(orderId, productId, quantity, sellerId);
            
            if (success) {
                // Successfully reserved or definitively failed
                return;
            }

            // On timeout or temporary error: try next seller
            System.out.println("[OrderProcessor] Seller " + sellerId + 
                             " not available, trying alternative...");
        }

        // All sellers tried, none could deliver
        System.err.println("[OrderProcessor] No seller could deliver " + productId);
        sagaManager.handleReservationTimeout(orderId, productId);
    }
    
    /**
     * Sends a reservation request to a specific seller
     * @return true if successfully reserved or definitively failed
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
            System.out.println("[OrderProcessor] Sending to " + sellerId + ": " + json);

            // Get the correct socket for this seller
            ZMQ.Socket sellerSocket = sellerSockets.get(sellerId);
            if (sellerSocket == null) {
                System.err.println("[OrderProcessor] No socket for seller " + sellerId);
                return false;
            }

            // Send directly via the dedicated socket
            sellerSocket.send(json);
            return true;
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ZMQException e) {
            System.err.println("[OrderProcessor] ZMQ error at " + sellerId + ": " + e.getMessage());
            return false;
        }
        return false;
    }
    
    /**
     * Reception thread for a specific seller
     */
    private void receiveSellerResponses(String sellerId, ZMQ.Socket socket) {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                String response = socket.recvStr(0);
                if (response != null && !response.isEmpty()) {
                    System.out.println("[OrderProcessor] Answer from " + sellerId + ": " + response);

                    String type = Messages.getMessageType(response);
                    switch (type) {
                        case "ReserveResponse":
                            Messages.ReserveResponse reserveResponse = Messages.fromJson(response, Messages.ReserveResponse.class);
                            sagaManager.handleReservationResponse(reserveResponse);
                            break;
                        case "CancelResponse":
                            // Can be processed optionally
                            break;
                        case "ConfirmResponse":
                            // Can be processed optionally
                            break;
                        default:
                            System.err.println("[OrderProcessor] Unknown message type: " + type);
                            break;
                    }
                }
            } catch (Exception e) {
                if (!Thread.currentThread().isInterrupted()) {
                    System.err.println("[OrderProcessor] Error receiving from " + sellerId + ": " + e.getMessage());
                }
            }
        }
    }
    
    /**
     * Monitors an order and performs rollback if necessary
     */
    private void monitorOrder(SagaManager.OrderSaga saga) {
        // Wait until all reservations are complete
        int maxWaitTime = ConfigLoader.getTimeout() * 3; // More time for failover
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

        // Check status
        if (saga.status == SagaManager.SagaStatus.RESERVED) {
            // All successful - send confirmations
            confirmOrder(saga);
        } else if (saga.status == SagaManager.SagaStatus.COMPENSATING || 
                   saga.hasFailures()) {
            // Error occurred - rollback
            rollbackOrder(saga);
        }
    }
    
    /**
     * Confirms a successful order
     */
    private void confirmOrder(SagaManager.OrderSaga saga) {
        System.out.println("\n[OrderProcessor] Confirming order " + saga.orderId);

        for (Messages.ReserveResponse reservation : saga.getSuccessfulReservations()) {
            executor.submit(() -> {
                Messages.ConfirmRequest confirm = new Messages.ConfirmRequest();
                confirm.orderId = saga.orderId;
                confirm.productId = reservation.productId;
                confirm.sellerId = reservation.sellerId;
                sendConfirmation(confirm, reservation.sellerId);
            });
            // Remove reservation after confirmation
            saga.reservations.remove(reservation.productId);
        }

        sagaManager.completeSaga(saga.orderId, true);
        System.out.println("[OrderProcessor] Order " + saga.orderId + " successfully completed!");

        // After completing the order:
        if (statusCallback != null) {
            // Response now also includes the marketplaceId
            statusCallback.accept(saga.orderId, marketplaceId + ": Order " + saga.orderId + " successfully completed");
        }
    }
    
    /**
     * Performs rollback for failed order
     */
    private void rollbackOrder(SagaManager.OrderSaga saga) {
        System.out.println("\n[OrderProcessor] Rolling back order " + saga.orderId);

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

        // Wait for all rollbacks
        try {
            latch.await(ConfigLoader.getTimeout(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        sagaManager.completeSaga(saga.orderId, false);
        System.out.println("[OrderProcessor] Order " + saga.orderId + 
                         " rolled back!");

        // After completing the order:
        if (statusCallback != null) {
            statusCallback.accept(saga.orderId, marketplaceId + ": Order " + saga.orderId + " failed (rollback performed)");
        }
    }
    
    /**
     * Sends confirmation to seller
     */
    private void sendConfirmation(Messages.ConfirmRequest confirm, String sellerId) {
        try {
            Thread.sleep(networkLatencyMs);
            
            String json = Messages.toJson(confirm);
            System.out.println("[OrderProcessor] Sending confirmation to " + sellerId + ": " + json);

            ZMQ.Socket sellerSocket = sellerSockets.get(sellerId);
            if (sellerSocket != null) {
                sellerSocket.send(json);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ZMQException e) {
            System.err.println("[OrderProcessor] Error sending confirmation: " + e.getMessage());
        }
    }
    
    /**
     * Sends cancellation to seller
     */
    private void sendCancellation(Messages.CancelRequest cancel, String sellerId) {
        try {
            Thread.sleep(networkLatencyMs);
            
            String json = Messages.toJson(cancel);
            System.out.println("[OrderProcessor] Sending cancellation to " + sellerId + ": " + json);

            ZMQ.Socket sellerSocket = sellerSockets.get(sellerId);
            if (sellerSocket != null) {
                sellerSocket.send(json);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ZMQException e) {
            System.err.println("[OrderProcessor] Error sending cancellation: " + e.getMessage());
        }
    }
    
    /**
     * Removes SAGAs that have been active too long and performs rollback on the seller
     */
    private void cleanupTimedOutSagas() {
        List<SagaManager.OrderSaga> timedOutSagas = sagaManager.getTimedOutSagas(sagaTimeoutMs);
        for (SagaManager.OrderSaga saga : timedOutSagas) {
            System.out.println("[OrderProcessor] Removing Order " + saga.orderId + " due to timeout.");

            // Rollback for all successful reservations
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
                statusCallback.accept(saga.orderId, "Order " + saga.orderId + " removed due to timeout (Rollback performed)");
            }
        }
    }

    /**
     * Cleanup
     */
    public void shutdown() {
        executor.shutdown();
        scheduler.shutdown(); // Stop periodic cleanup

        // Close all seller sockets
        for (ZMQ.Socket socket : sellerSockets.values()) {
            socket.close();
        }
        
        context.close();
    }
}