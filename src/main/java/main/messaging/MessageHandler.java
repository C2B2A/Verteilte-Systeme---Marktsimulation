package main.messaging;

import main.messaging.MessageTypes.*;
import java.util.ArrayList;

/**
 * Message Handler - KEINE externen Libraries!
 * Format: TYPE|field1|field2|field3...
 */
public class MessageHandler {
    
    /**
     * Konvertiert eine Nachricht zu String
     */
    public static String toJson(Object msg) {
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
            
            for (int i = 0; i < o.products.size(); i++) {
                OrderRequest.ProductOrder p = o.products.get(i);
                sb.append(p.productId).append(":").append(p.quantity);
                if (i < o.products.size() - 1) sb.append(",");
            }
            return sb.toString();
        }
        
        return "UNKNOWN|" + msg.toString();
    }
    
    /**
     * Parst einen String zu einer Nachricht
     */
    @SuppressWarnings("unchecked")
    public static <T> T fromJson(String message, Class<T> clazz) {
        if (message == null || message.isEmpty()) {
            return null;
        }
        
        String[] parts = message.split("\\|");
        if (parts.length < 1) {
            return null;
        }
        
        String type = parts[0];
        Object parsed = null;
        
        try {
            switch (type) {
                case "RESERVE":
                    if (parts.length >= 5) {
                        ReserveRequest req = new ReserveRequest();
                        req.orderId = parts[1];
                        req.productId = parts[2];
                        req.quantity = Integer.parseInt(parts[3]);
                        req.marketplaceId = parts[4];
                        parsed = req;
                    }
                    break;
                    
                case "RESERVED":
                    if (parts.length >= 4) {
                        ReserveResponse res = new ReserveResponse();
                        res.orderId = parts[1];
                        res.productId = parts[2];
                        res.sellerId = parts[3];
                        res.status = "RESERVED";
                        parsed = res;
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
                        parsed = res;
                    }
                    break;
                    
                case "CANCEL":
                    if (parts.length >= 4) {
                        CancelRequest req = new CancelRequest();
                        req.orderId = parts[1];
                        req.productId = parts[2];
                        req.sellerId = parts[3];
                        parsed = req;
                    }
                    break;
                    
                case "CANCELLED":
                    if (parts.length >= 4) {
                        CancelResponse res = new CancelResponse();
                        res.orderId = parts[1];
                        res.productId = parts[2];
                        res.sellerId = parts[3];
                        res.status = "CANCELLED";
                        parsed = res;
                    }
                    break;
                    
                case "CONFIRM":
                    if (parts.length >= 4) {
                        ConfirmRequest req = new ConfirmRequest();
                        req.orderId = parts[1];
                        req.productId = parts[2];
                        req.sellerId = parts[3];
                        parsed = req;
                    }
                    break;
                    
                case "ORDER":
                    if (parts.length >= 4) {
                        OrderRequest order = new OrderRequest();
                        order.orderId = parts[1];
                        order.customerId = parts[2];
                        order.products = new ArrayList<>();
                        
                        String[] productPairs = parts[3].split(",");
                        for (String pair : productPairs) {
                            String[] productQty = pair.split(":");
                            if (productQty.length == 2) {
                                String productId = productQty[0];
                                int quantity = Integer.parseInt(productQty[1]);
                                order.products.add(new OrderRequest.ProductOrder(productId, quantity));
                            }
                        }
                        parsed = order;
                    }
                    break;
            }
        } catch (NumberFormatException e) {
            System.err.println("Fehler beim Parsen der Nachricht: " + e.getMessage());
        }
        
        if (parsed != null && clazz.isInstance(parsed)) {
            return (T) parsed;
        }
        
        return null;
    }
    
    /**
     * Ermittelt den Nachrichtentyp
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
}