package main.marketplace;

import main.messaging.Messages;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Verwaltet SAGA-Transaktionen für Bestellungen
 * Speichert Status und koordiniert Rollbacks
 */
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
    
    // Saga für eine Bestellung
    public static class OrderSaga {
        public final String orderId;
        public final Messages.OrderRequest order;
        public SagaStatus status;
        public final Map<String, Messages.ReserveResponse> reservations;
        public final Set<String> pendingReservations;
        public final Set<String> failedReservations;
        public long startTime;
        
        public OrderSaga(String orderId, Messages.OrderRequest order) {
            this.orderId = orderId;
            this.order = order;
            this.status = SagaStatus.STARTED;
            this.reservations = new ConcurrentHashMap<>();
            this.pendingReservations = ConcurrentHashMap.newKeySet();
            this.failedReservations = ConcurrentHashMap.newKeySet();
            this.startTime = System.currentTimeMillis();
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
    
    // Aktive Sagas
    private final Map<String, OrderSaga> activeSagas = new ConcurrentHashMap<>();
    
    /**
     * Startet eine neue SAGA für eine Bestellung
     */
    public OrderSaga startSaga(Messages.OrderRequest order) {
        String orderId = order.orderId;
        OrderSaga saga = new OrderSaga(orderId, order);
        activeSagas.put(orderId, saga);
        
        System.out.println("[SAGA] Gestartet für Order " + orderId + 
                         " mit " + order.products.size() + " Produkten");
        return saga;
    }
    
    /**
     * Registriert eine ausstehende Reservierung
     */
    public void registerPendingReservation(String orderId, String productId) {
        OrderSaga saga = activeSagas.get(orderId);
        if (saga != null) {
            saga.pendingReservations.add(productId);
            saga.status = SagaStatus.RESERVING;
        }
    }
    
    /**
     * Verarbeitet eine Reservierungsantwort
     */
    public void handleReservationResponse(Messages.ReserveResponse response) {
        OrderSaga saga = activeSagas.get(response.orderId);
        if (saga == null) {
            System.err.println("[SAGA] Keine Saga gefunden für Order " + response.orderId);
            return;
        }
        
        // Speichere Antwort
        saga.reservations.put(response.productId, response);
        saga.pendingReservations.remove(response.productId);
        
        if ("FAILED".equals(response.status)) {
            saga.failedReservations.add(response.productId);
            System.out.println("[SAGA] Reservierung fehlgeschlagen für " + 
                             response.productId + ": " + response.reason);
        } else {
            System.out.println("[SAGA] Reservierung erfolgreich für " + 
                             response.productId + " bei " + response.sellerId);
        }
        
        // Prüfe ob alle Antworten da sind
        if (saga.isComplete()) {
            if (saga.hasFailures()) {
                System.out.println("[SAGA] Order " + saga.orderId + 
                                 " hat Fehler - starte Kompensation");
                saga.status = SagaStatus.COMPENSATING;
            } else {
                System.out.println("[SAGA] Order " + saga.orderId + 
                                 " komplett reserviert - bereit zur Bestätigung");
                saga.status = SagaStatus.RESERVED;
            }
        }
    }
    
    /**
     * Behandelt Timeout für eine Reservierung
     */
    public void handleReservationTimeout(String orderId, String productId) {
        OrderSaga saga = activeSagas.get(orderId);
        if (saga != null) {
            saga.pendingReservations.remove(productId);
            saga.failedReservations.add(productId);
            
            System.out.println("[SAGA] Timeout für Produkt " + productId + 
                             " in Order " + orderId);
            
            // Erstelle künstliche Fehler-Response
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
    
    /**
     * Gibt alle erfolgreichen Reservierungen für Rollback zurück
     */
    public List<Messages.ReserveResponse> getReservationsForRollback(String orderId) {
        OrderSaga saga = activeSagas.get(orderId);
        if (saga != null) {
            return saga.getSuccessfulReservations();
        }
        return new ArrayList<>();
    }
    
    /**
     * Markiert Saga als abgeschlossen
     */
    public void completeSaga(String orderId, boolean success) {
        OrderSaga saga = activeSagas.get(orderId);
        if (saga != null) {
            saga.status = success ? SagaStatus.COMPLETED : SagaStatus.FAILED;
            long duration = System.currentTimeMillis() - saga.startTime;
            
            System.out.println("[SAGA] Order " + orderId + " abgeschlossen: " + 
                             saga.status + " (Dauer: " + duration + "ms)");
            
            // Optional: Saga nach Abschluss entfernen
            activeSagas.remove(orderId);
        }
    }
    
    /**
     * Gibt Saga-Status zurück
     */
    public OrderSaga getSaga(String orderId) {
        return activeSagas.get(orderId);
    }
    
    /**
     * Debug: Alle aktiven Sagas ausgeben
     */
    public void printActiveSagas() {
        System.out.println("\n=== Aktive SAGAs ===");
        for (OrderSaga saga : activeSagas.values()) {
            System.out.println("Order " + saga.orderId + ": " + saga.status + 
                             " (Pending: " + saga.pendingReservations.size() + 
                             ", Failed: " + saga.failedReservations.size() + ")");
        }
        System.out.println("====================\n");
    }
}