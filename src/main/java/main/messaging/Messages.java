package main.messaging;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Consolidated messaging class containing message types and handler functionality
public class Messages {
    
    // Message Types
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
    
    public static class ReserveRequest {
        public String orderId;
        public String productId;
        public int quantity;
        public String marketplaceId;
    }
    
    public static class ReserveResponse {
        public String orderId;
        public String productId;
        public String sellerId;
        public String status;
        public String reason;
    }
    
    public static class CancelRequest {
        public String orderId;
        public String productId;
        public String sellerId;
    }
    
    public static class CancelResponse {
        public String orderId;
        public String productId;
        public String sellerId;
        public String status;
    }
    
    public static class ConfirmRequest {
        public String orderId;
        public String productId;
        public String sellerId;
    }
    
    // Message Handler Methods
    private static String jsonField(String key, String value) {
        return "\"" + key + "\":\"" + value + "\"";
    }
    
    private static String jsonField(String key, int value) {
        return "\"" + key + "\":" + value;
    }

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
            System.err.println("Error parsing message: " + e.getMessage());
        }
        return null;
    }

    public static String getMessageType(String message) {
        if (message == null || message.isEmpty()) return "Unknown";
        String type = extractJsonValue(message, "messageType");
        return type != null ? type : "Unknown";
    }

    public static String extractJsonValue(String json, String key) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*\"?([^\"]+?)\"?(,|}|\\])");
        Matcher m = p.matcher(json);
        return m.find() ? m.group(1) : null;
    }

    private static String extractJsonArray(String json, String key) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*(\\[.*?\\])");
        Matcher m = p.matcher(json);
        return m.find() ? m.group(1) : null;
    }
}