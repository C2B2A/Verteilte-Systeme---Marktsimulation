package main.messaging;

/**
 * Message Handler - nutzt jetzt SimpleMessageFormat
 * KEINE externen Libraries!
 */
public class MessageHandler {
    
    /**
     * Konvertiert eine Nachricht zu String
     */
    public static String toJson(Object message) {
        return MessageFormat.format(message);
    }
    
    /**
     * Parst einen String zu einer Nachricht
     */
    @SuppressWarnings("unchecked")
    public static <T> T fromJson(String message, Class<T> clazz) {
        Object parsed = MessageFormat.parse(message);
        
        // Type-Check
        if (parsed != null && clazz.isInstance(parsed)) {
            return (T) parsed;
        }
        
        return null;
    }
    
    /**
     * Ermittelt den Nachrichtentyp
     */
    public static String getMessageType(String message) {
        return MessageFormat.getMessageType(message);
    }
}