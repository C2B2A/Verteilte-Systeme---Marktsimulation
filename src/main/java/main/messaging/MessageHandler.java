package main.messaging;

import main.messaging.MessageTypes.*;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Message Handler - KEINE externen Libraries!
 * Format: JSON-String
 */
public class MessageHandler {

    // Hilfsfunktion: String zu JSON-Feld
    private static String jsonField(String key, String value) {
        return "\"" + key + "\":\"" + value + "\"";
    }
    private static String jsonField(String key, int value) {
        return "\"" + key + "\":" + value;
    }

    /**
     * Konvertiert eine Nachricht zu JSON-String
     */
    public static String toJson(Object msg) {
        if (msg instanceof ReserveRequest) {
            ReserveRequest r = (ReserveRequest) msg;
            return "{" +
                jsonField("messageType", "ReserveRequest") + "," +
                jsonField("orderId", r.orderId) + "," +
                jsonField("productId", r.productId) + "," +
                jsonField("quantity", r.quantity) + "," +
                jsonField("marketplaceId", r.marketplaceId) +
                "}";
        }
        else if (msg instanceof ReserveResponse) {
            ReserveResponse r = (ReserveResponse) msg;
            StringBuilder sb = new StringBuilder("{");
            sb.append(jsonField("messageType", "ReserveResponse")).append(",");
            sb.append(jsonField("orderId", r.orderId)).append(",");
            sb.append(jsonField("productId", r.productId)).append(",");
            sb.append(jsonField("sellerId", r.sellerId)).append(",");
            sb.append(jsonField("status", r.status));
            if (r.reason != null) {
                sb.append(",").append(jsonField("reason", r.reason));
            }
            sb.append("}");
            return sb.toString();
        }
        else if (msg instanceof CancelRequest) {
            CancelRequest c = (CancelRequest) msg;
            return "{" +
                jsonField("messageType", "CancelRequest") + "," +
                jsonField("orderId", c.orderId) + "," +
                jsonField("productId", c.productId) + "," +
                jsonField("sellerId", c.sellerId) +
                "}";
        }
        else if (msg instanceof CancelResponse) {
            CancelResponse c = (CancelResponse) msg;
            return "{" +
                jsonField("messageType", "CancelResponse") + "," +
                jsonField("orderId", c.orderId) + "," +
                jsonField("productId", c.productId) + "," +
                jsonField("sellerId", c.sellerId) + "," +
                jsonField("status", c.status) +
                "}";
        }
        else if (msg instanceof ConfirmRequest) {
            ConfirmRequest c = (ConfirmRequest) msg;
            return "{" +
                jsonField("messageType", "ConfirmRequest") + "," +
                jsonField("orderId", c.orderId) + "," +
                jsonField("productId", c.productId) + "," +
                jsonField("sellerId", c.sellerId) +
                "}";
        }
        else if (msg instanceof OrderRequest) {
            OrderRequest o = (OrderRequest) msg;
            StringBuilder sb = new StringBuilder("{");
            sb.append(jsonField("messageType", "OrderRequest")).append(",");
            sb.append(jsonField("orderId", o.orderId)).append(",");
            sb.append(jsonField("customerId", o.customerId)).append(",");
            sb.append("\"products\":[");
            for (int i = 0; i < o.products.size(); i++) {
                OrderRequest.ProductOrder p = o.products.get(i);
                sb.append("{")
                  .append(jsonField("productId", p.productId)).append(",")
                  .append(jsonField("quantity", p.quantity))
                  .append("}");
                if (i < o.products.size() - 1) sb.append(",");
            }
            sb.append("]}");
            return sb.toString();
        }
        return "{\"messageType\":\"Unknown\",\"raw\":\"" + msg.toString() + "\"}";
    }

    /**
     * Parst einen JSON-String zu einer Nachricht
     */
    @SuppressWarnings("unchecked")
    public static <T> T fromJson(String message, Class<T> clazz) {
        if (message == null || message.isEmpty()) return null;
        String type = getMessageType(message);

        try {
            switch (type) {
                case "ReserveRequest": {
                    ReserveRequest req = new ReserveRequest();
                    req.orderId = extractJsonValue(message, "orderId");
                    req.productId = extractJsonValue(message, "productId");
                    req.quantity = Integer.parseInt(extractJsonValue(message, "quantity"));
                    req.marketplaceId = extractJsonValue(message, "marketplaceId");
                    return clazz.isInstance(req) ? (T) req : null;
                }
                case "ReserveResponse": {
                    ReserveResponse res = new ReserveResponse();
                    res.orderId = extractJsonValue(message, "orderId");
                    res.productId = extractJsonValue(message, "productId");
                    res.sellerId = extractJsonValue(message, "sellerId");
                    res.status = extractJsonValue(message, "status");
                    res.reason = extractJsonValue(message, "reason");
                    return clazz.isInstance(res) ? (T) res : null;
                }
                case "CancelRequest": {
                    CancelRequest req = new CancelRequest();
                    req.orderId = extractJsonValue(message, "orderId");
                    req.productId = extractJsonValue(message, "productId");
                    req.sellerId = extractJsonValue(message, "sellerId");
                    return clazz.isInstance(req) ? (T) req : null;
                }
                case "CancelResponse": {
                    CancelResponse res = new CancelResponse();
                    res.orderId = extractJsonValue(message, "orderId");
                    res.productId = extractJsonValue(message, "productId");
                    res.sellerId = extractJsonValue(message, "sellerId");
                    res.status = extractJsonValue(message, "status");
                    return clazz.isInstance(res) ? (T) res : null;
                }
                case "ConfirmRequest": {
                    ConfirmRequest req = new ConfirmRequest();
                    req.orderId = extractJsonValue(message, "orderId");
                    req.productId = extractJsonValue(message, "productId");
                    req.sellerId = extractJsonValue(message, "sellerId");
                    return clazz.isInstance(req) ? (T) req : null;
                }
                case "OrderRequest": {
                    OrderRequest order = new OrderRequest();
                    order.orderId = extractJsonValue(message, "orderId");
                    order.customerId = extractJsonValue(message, "customerId");
                    order.products = new ArrayList<>();
                    String productsArray = extractJsonArray(message, "products");
                    if (productsArray != null) {
                        Matcher m = Pattern.compile("\\{([^}]+)\\}").matcher(productsArray);
                        while (m.find()) {
                            String prod = m.group(1);
                            String productId = extractJsonValue("{" + prod + "}", "productId");
                            int quantity = Integer.parseInt(extractJsonValue("{" + prod + "}", "quantity"));
                            order.products.add(new OrderRequest.ProductOrder(productId, quantity));
                        }
                    }
                    return clazz.isInstance(order) ? (T) order : null;
                }
            }
        } catch (Exception e) {
            System.err.println("Fehler beim Parsen der Nachricht: " + e.getMessage());
        }
        return null;
    }

    /**
     * Ermittelt den Nachrichtentyp aus JSON
     */
    public static String getMessageType(String message) {
        if (message == null || message.isEmpty()) return "Unknown";
        String type = extractJsonValue(message, "messageType");
        return type != null ? type : "Unknown";
    }

    // Hilfsfunktion: Wert aus JSON extrahieren (nur für einfache Strings/Ints)
    public static String extractJsonValue(String json, String key) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*\"?([^\"]+?)\"?(,|}|\\])");
        Matcher m = p.matcher(json);
        return m.find() ? m.group(1) : null;
    }

    // Hilfsfunktion: Array aus JSON extrahieren (nur für flache Arrays)
    private static String extractJsonArray(String json, String key) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*(\\[.*?\\])");
        Matcher m = p.matcher(json);
        return m.find() ? m.group(1) : null;
    }
}