package main.messaging;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

/**
 * Einfacher Handler für JSON-Nachrichten
 */
public class MessageHandler {
    private static final Gson gson = new Gson();
    
    // Nachricht zu JSON
    public static String toJson(Object message) {
        return gson.toJson(message);
    }
    
    // JSON zu Nachricht
    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
            return gson.fromJson(json, clazz);
        } catch (JsonSyntaxException e) {
            System.err.println("Fehler beim Parsen: " + e.getMessage());
            return null;
        }
    }
    
    // Nachrichtentyp aus JSON ermitteln (simpel über Feld-Check)
    public static String getMessageType(String json) {
        if (json.contains("\"products\"") && json.contains("\"customerId\"")) {
            return "OrderRequest";
        } else if (json.contains("\"quantity\"") && json.contains("\"marketplaceId\"")) {
            return "ReserveRequest";
        } else if (json.contains("\"status\"") && json.contains("\"sellerId\"")) {
            return "ReserveResponse";
        } else if (json.contains("\"orderId\"") && json.contains("\"productId\"") && json.contains("\"sellerId\"")) {
            if (json.contains("CANCELLED")) {
                return "CancelResponse";
            }
            return "CancelRequest";
        } else if (json.contains("\"orderId\"") && json.contains("\"sellerId\"")) {
            return "ConfirmRequest";
        }
        return "Unknown";
    }
}