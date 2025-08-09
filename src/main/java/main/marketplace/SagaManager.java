package main.marketplace;

import main.messaging.Messages;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

// Manages SAGA transactions for orders
// Saves status and coordinates rollbacks
public class SagaManager {

    // Saga Status
    public enum SagaStatus {
        STARTED,
        RESERVING,
        RESERVED,
        CONFIRMING,
        COMPLETED,
        COMPENSATING,
        FAILED
    }

    // Saga for an order
    public static class OrderSaga {
        public final String orderId;
        public final Messages.OrderRequest order;
        public SagaStatus status;
        public final Map<String, Messages.ReserveResponse> reservations;
        public final Set<String> pendingReservations;
        public final Set<String> failedReservations;
        public final long startTimeMs; // Timestamp for timeout

        public OrderSaga(Messages.OrderRequest order) {
            this.orderId = order.orderId;
            this.order = order;
            this.status = SagaStatus.STARTED;
            this.reservations = new ConcurrentHashMap<>();
            this.pendingReservations = ConcurrentHashMap.newKeySet();
            this.failedReservations = ConcurrentHashMap.newKeySet();
            this.startTimeMs = System.currentTimeMillis();
        }

        public boolean isComplete() {
            return pendingReservations.isEmpty();
        }

        public boolean hasFailures() {
            return !failedReservations.isEmpty();
        }

        public List<Messages.ReserveResponse> getSuccessfulReservations() {
            List<Messages.ReserveResponse> successful = new ArrayList<>();
            for (Messages.ReserveResponse res : reservations.values()) {
                if ("RESERVED".equals(res.status)) {
                    successful.add(res);
                }
            }
            return successful;
        }
    }

    // Active SAGAs
    private final Map<String, OrderSaga> activeSagas = new ConcurrentHashMap<>();

    // Starts a new SAGA for an order
    public OrderSaga startSaga(Messages.OrderRequest order) {
        OrderSaga saga = new OrderSaga(order);
        activeSagas.put(order.orderId, saga);

        System.out.println("[SAGA] Started for Order " + order.orderId +
                " with " + order.products.size() + " products");
        return saga;
    }

    // Registers a pending reservation
    public void registerPendingReservation(String orderId, String productId) {
        OrderSaga saga = activeSagas.get(orderId);
        if (saga != null) {
            saga.pendingReservations.add(productId);
            saga.status = SagaStatus.RESERVING;
        }
    }

    // Processes a reservation response
    public void handleReservationResponse(Messages.ReserveResponse response) {
        OrderSaga saga = activeSagas.get(response.orderId);
        if (saga == null) {
            System.err.println("[SAGA] No active SAGA (anymore) for Order " + response.orderId);
            return;
        }

        // Store response
        saga.reservations.put(response.productId, response);
        saga.pendingReservations.remove(response.productId);

        if ("FAILED".equals(response.status)) {
            saga.failedReservations.add(response.productId);
            System.out.println("[SAGA] Reservation failed for " +
                    response.productId + ": " + response.reason);
        } else {
            System.out.println("[SAGA] Reservation successful for " +
                    response.productId + " at " + response.sellerId);
        }

        // Check if all responses are in
        if (saga.isComplete()) {
            if (saga.hasFailures()) {
                System.out.println("[SAGA] Order " + saga.orderId +
                        " has failures - starting compensation");
                saga.status = SagaStatus.COMPENSATING;
            } else {
                System.out.println("[SAGA] Order " + saga.orderId +
                        " fully reserved - ready for confirmation");
                saga.status = SagaStatus.RESERVED;
            }
        }
    }

    // Handles timeout for a reservation
    public void handleReservationTimeout(String orderId, String productId) {
        OrderSaga saga = activeSagas.get(orderId);
        if (saga != null) {
            saga.pendingReservations.remove(productId);
            saga.failedReservations.add(productId);

            System.out.println("[SAGA] Timeout for product " + productId +
                    " in order " + orderId);

            // Create artificial error response
            Messages.ReserveResponse timeoutResponse = new Messages.ReserveResponse();
            timeoutResponse.orderId = orderId;
            timeoutResponse.productId = productId;
            timeoutResponse.status = "FAILED";
            timeoutResponse.reason = "Timeout";
            saga.reservations.put(productId, timeoutResponse);

            if (saga.isComplete()) {
                saga.status = SagaStatus.COMPENSATING;
            }
        }
    }

    // Returns all successful reservations for rollback
    public List<Messages.ReserveResponse> getReservationsForRollback(String orderId) {
        OrderSaga saga = activeSagas.get(orderId);
        if (saga != null) {
            return saga.getSuccessfulReservations();
        }
        return new ArrayList<>();
    }

    // Marks the saga as completed
    public void completeSaga(String orderId, boolean success) {
        OrderSaga saga = activeSagas.get(orderId);
        if (saga != null) {
            saga.status = success ? SagaStatus.COMPLETED : SagaStatus.FAILED;
            long duration = System.currentTimeMillis() - saga.startTimeMs;

            System.out.println("[SAGA] Order " + orderId + " completed: " +
                    saga.status + " (Duration: " + duration + "ms)");

            // Optional: Remove saga after completion
            activeSagas.remove(orderId);
        }
    }

    // Returns the saga status
    public OrderSaga getSaga(String orderId) {
        return activeSagas.get(orderId);
    }

    // Debug: All active Sagas
    public void printActiveSagas() {
        System.out.println("\n=== Active SAGAs ===");
        for (OrderSaga saga : activeSagas.values()) {
            System.out.println("Order " + saga.orderId + ": " + saga.status +
                    " (Pending: " + saga.pendingReservations.size() +
                    ", Failed: " + saga.failedReservations.size() + ")");
        }
        System.out.println("====================\n");
    }

    // Returns all Sagas that exceed the timeout
    public List<OrderSaga> getTimedOutSagas(long timeoutMs) {
        long now = System.currentTimeMillis();
        List<OrderSaga> result = new ArrayList<>();
        for (OrderSaga saga : activeSagas.values()) {
            if (!saga.isComplete() && (now - saga.startTimeMs) > timeoutMs) {
                result.add(saga);
            }
        }
        return result;
    }

    // Removes a saga from the active sagas
    public void removeSaga(String orderId) {
        activeSagas.remove(orderId);
    }
}