package main.messaging;

import java.util.List;

/**
 * Alle Nachrichtentypen für die Kommunikation
 * Einfache POJOs für JSON-Serialisierung mit Gson
 */
public class MessageTypes {
    
    // Bestellung vom Kunden
    public static class OrderRequest {
        public String orderId;
        public List<ProductOrder> products;
        public String customerId;
        
        public static class ProductOrder {
            public String productId;
            public int quantity;
            
            public ProductOrder(String productId, int quantity) {
                this.productId = productId;
                this.quantity = quantity;
            }
        }
    }
    
    // Reservierungsanfrage an Seller
    public static class ReserveRequest {
        public String orderId;
        public String productId;
        public int quantity;
        public String marketplaceId;
    }
    
    // Antwort vom Seller
    public static class ReserveResponse {
        public String orderId;
        public String productId;
        public String sellerId;
        public String status; // "RESERVED" oder "FAILED"
        public String reason; // Bei Fehler: Grund
    }
    
    // Rollback-Anfrage (SAGA Compensation)
    public static class CancelRequest {
        public String orderId;
        public String productId;
        public String sellerId;
    }
    
    // Bestätigung des Rollbacks
    public static class CancelResponse {
        public String orderId;
        public String productId;
        public String sellerId;
        public String status; // "CANCELLED" oder "FAILED"
    }
    
    // Finale Bestätigung an Seller
    public static class ConfirmRequest {
        public String orderId;
        public String productId;
        public String sellerId;
    }
}