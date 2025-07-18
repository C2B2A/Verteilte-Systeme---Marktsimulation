package main.messaging;

import main.messaging.MessageTypes.*;
import java.util.ArrayList;

/**
 * Ultra-simpler Message Handler - KEINE externen Libraries!
 * Format: TYPE|field1|field2|field3...
 * 
 * Nachrichtentypen:
 * - RESERVE|orderId|productId|quantity|marketplaceId
 * - RESERVED|orderId|productId|sellerId|reason (reason optional)
 * - FAILED|orderId|productId|sellerId|reason
 * - CANCEL|orderId|productId|sellerId
 * - CANCELLED|orderId|productId|sellerId
 * - CONFIRM|orderId|productId|sellerId
 * - CONFIRMED|orderId|productId|sellerId
 * - ORDER|orderId|customerId|product1:qty1,product2:qty2...
 */
public class MessageFormat {
    
    // ==================== NACHRICHTEN ZU STRING ====================
    
    public static String format(Object msg) {
        if (msg instanceof ReserveRequest) {
            ReserveRequest r = (ReserveRequest) msg;
            return "RESERVE|" + r.orderId + "|" + r.productId + "|" + 
                   r.quantity + "|" + r.marketplaceId;
        }
        else if (msg instanceof ReserveResponse) {
            ReserveResponse r = (ReserveResponse) msg;
            if ("RESERVED".equals(r.status)) {
                return "RESERVED|" + r.orderId + "|" + r.productId + "|" + r.sellerId;
            } else {
                // Bei FAILED immer einen Grund mitgeben
                String reason = r.reason != null ? r.reason : "Unknown";
                return "FAILED|" + r.orderId + "|" + r.productId + "|" + r.sellerId + "|" + reason;
            }
        }
        else if (msg instanceof CancelRequest) {
            CancelRequest c = (CancelRequest) msg;
            return "CANCEL|" + c.orderId + "|" + c.productId + "|" + c.sellerId;
        }
        else if (msg instanceof CancelResponse) {
            CancelResponse c = (CancelResponse) msg;
            return "CANCELLED|" + c.orderId + "|" + c.productId + "|" + c.sellerId;
        }
        else if (msg instanceof ConfirmRequest) {
            ConfirmRequest c = (ConfirmRequest) msg;
            return "CONFIRM|" + c.orderId + "|" + c.productId + "|" + c.sellerId;
        }
        else if (msg instanceof OrderRequest) {
            OrderRequest o = (OrderRequest) msg;
            StringBuilder sb = new StringBuilder("ORDER|");
            sb.append(o.orderId).append("|").append(o.customerId).append("|");
            
            // Produkte als product:qty,product:qty
            for (int i = 0; i < o.products.size(); i++) {
                OrderRequest.ProductOrder p = o.products.get(i);
                sb.append(p.productId).append(":").append(p.quantity);
                if (i < o.products.size() - 1) sb.append(",");
            }
            return sb.toString();
        }
        
        // Fallback für unbekannte Nachrichten
        return "UNKNOWN|" + msg.toString();
    }
    
    // ==================== STRING ZU NACHRICHTEN ====================
    
    public static Object parse(String message) {
        if (message == null || message.isEmpty()) {
            return null;
        }
        
        String[] parts = message.split("\\|");
        if (parts.length < 1) {
            return null;
        }
        
        String type = parts[0];
        
        try {
            switch (type) {
                case "RESERVE":
                    if (parts.length >= 5) {
                        ReserveRequest req = new ReserveRequest();
                        req.orderId = parts[1];
                        req.productId = parts[2];
                        req.quantity = Integer.parseInt(parts[3]);
                        req.marketplaceId = parts[4];
                        return req;
                    }
                    break;
                    
                case "RESERVED":
                    if (parts.length >= 4) {
                        ReserveResponse res = new ReserveResponse();
                        res.orderId = parts[1];
                        res.productId = parts[2];
                        res.sellerId = parts[3];
                        res.status = "RESERVED";
                        return res;
                    }
                    break;
                    
                case "FAILED":
                    if (parts.length >= 4) {
                        ReserveResponse res = new ReserveResponse();
                        res.orderId = parts[1];
                        res.productId = parts[2];
                        res.sellerId = parts[3];
                        res.status = "FAILED";
                        res.reason = parts.length > 4 ? parts[4] : "Unknown";
                        return res;
                    }
                    break;
                    
                case "CANCEL":
                    if (parts.length >= 4) {
                        CancelRequest req = new CancelRequest();
                        req.orderId = parts[1];
                        req.productId = parts[2];
                        req.sellerId = parts[3];
                        return req;
                    }
                    break;
                    
                case "CANCELLED":
                    if (parts.length >= 4) {
                        CancelResponse res = new CancelResponse();
                        res.orderId = parts[1];
                        res.productId = parts[2];
                        res.sellerId = parts[3];
                        res.status = "CANCELLED";
                        return res;
                    }
                    break;
                    
                case "CONFIRM":
                    if (parts.length >= 4) {
                        ConfirmRequest req = new ConfirmRequest();
                        req.orderId = parts[1];
                        req.productId = parts[2];
                        req.sellerId = parts[3];
                        return req;
                    }
                    break;
                    
                case "ORDER":
                    if (parts.length >= 4) {
                        OrderRequest order = new OrderRequest();
                        order.orderId = parts[1];
                        order.customerId = parts[2];
                        order.products = new ArrayList<>();
                        
                        // Parse products: product1:qty1,product2:qty2
                        String[] productPairs = parts[3].split(",");
                        for (String pair : productPairs) {
                            String[] productQty = pair.split(":");
                            if (productQty.length == 2) {
                                String productId = productQty[0];
                                int quantity = Integer.parseInt(productQty[1]);
                                order.products.add(new OrderRequest.ProductOrder(productId, quantity));
                            }
                        }
                        return order;
                    }
                    break;
            }
        } catch (NumberFormatException e) {
            System.err.println("Fehler beim Parsen der Nachricht: " + e.getMessage());
        }
        
        return null;
    }
    
    // ==================== HILFSMETHODEN ====================
    
    /**
     * Ermittelt den Nachrichtentyp aus der Nachricht
     */
    public static String getMessageType(String message) {
        if (message == null || message.isEmpty()) {
            return "Unknown";
        }
        
        if (message.startsWith("RESERVE|")) return "ReserveRequest";
        if (message.startsWith("RESERVED|")) return "ReserveResponse";
        if (message.startsWith("FAILED|")) return "ReserveResponse";
        if (message.startsWith("CANCEL|")) return "CancelRequest";
        if (message.startsWith("CANCELLED|")) return "CancelResponse";
        if (message.startsWith("CONFIRM|")) return "ConfirmRequest";
        if (message.startsWith("CONFIRMED|")) return "ConfirmResponse";
        if (message.startsWith("ORDER|")) return "OrderRequest";
        
        return "Unknown";
    }
    
    /**
     * Spezielle Methode für generische Antworten (z.B. CONFIRMED)
     */
    public static String createSimpleResponse(String status) {
        return "{\"status\":\"" + status + "\"}";
    }
}