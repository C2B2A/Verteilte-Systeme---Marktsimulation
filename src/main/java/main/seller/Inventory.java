package main.seller;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Verwaltet die Produkte und Reservierungen eines Sellers
 * Thread-safe für gleichzeitige Zugriffe
 */
public class Inventory {
    private final Map<String, Product> products = new HashMap<>();
    // Speichert Reservierungen: orderId -> (productId -> quantity)
    private final Map<String, Map<String, Integer>> reservations = new ConcurrentHashMap<>();
    
    public Inventory() {
    }
    
    // Produkt hinzufügen
    public void addProduct(Product product) {
        products.put(product.getProductId(), product);
    }
    
    // Produkt reservieren
    public synchronized boolean reserve(String orderId, String productId, int quantity) {
        Product product = products.get(productId);
        if (product == null) {
            return false;
        }
        
        if (product.reserve(quantity)) {
            // Reservierung speichern für späteren Rollback
            reservations.computeIfAbsent(orderId, k -> new HashMap<>())
                       .put(productId, quantity);
            return true;
        }
        return false;
    }
    
    // Reservierung stornieren (SAGA Rollback)
    public synchronized boolean cancelReservation(String orderId, String productId) {
        Map<String, Integer> orderReservations = reservations.get(orderId);
        if (orderReservations == null) {
            return false;
        }
        
        Integer quantity = orderReservations.get(productId);
        if (quantity == null) {
            return false;
        }
        
        Product product = products.get(productId);
        if (product != null) {
            product.release(quantity);
            orderReservations.remove(productId);
            
            // Aufräumen wenn keine Reservierungen mehr für diese Order
            if (orderReservations.isEmpty()) {
                reservations.remove(orderId);
            }
            return true;
        }
        return false;
    }
    
    // Reservierung bestätigen (aus Reservierungen entfernen)
    public synchronized void confirmReservation(String orderId) {
        reservations.remove(orderId);
    }
    
    // Produkt abrufen
    public Product getProduct(String productId) {
        return products.get(productId);
    }
    
    // Alle Produkte
    public Map<String, Product> getAllProducts() {
        return new HashMap<>(products);
    }
    
    // Status ausgeben
    public void printStatus() {
        System.out.println("\n=== Inventory Status ===");
        for (Product p : products.values()) {
            System.out.println(p.getProductId() + ": " + p.getName() + 
                             " - Bestand: " + p.getStock());
        }
        if (!reservations.isEmpty()) {
            System.out.println("Aktive Reservierungen: " + reservations.size());
        }
    }
}