package main.seller;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages a seller's products and reservations
 * Thread-safe for concurrent access
 */
public class SellerInventory {
    private final Map<String, ProductInfo> products = new HashMap<>();
    private final Map<String, Map<String, Integer>> reservations = new ConcurrentHashMap<>();
    
    /**
     * Internal product class
     */
    private static class ProductInfo {
        private final String productId;
        private final String name;
        private int stock;
        
        public ProductInfo(String productId, String name, int stock) {
            this.productId = productId;
            this.name = name;
            this.stock = stock;
        }
        
        public synchronized boolean reserve(int quantity) {
            if (stock >= quantity) {
                stock -= quantity;
                return true;
            }
            return false;
        }
        
        public synchronized void release(int quantity) {
            stock += quantity;
        }
        
        public synchronized int getStock() {
            return stock;
        }
        
        public String getProductId() {
            return productId;
        }
        
        public String getName() {
            return name;
        }
    }
    
    public SellerInventory() {
    }
    
    public void addProduct(String productId, String name, int stock) {
        products.put(productId, new ProductInfo(productId, name, stock));
    }
    
    public synchronized boolean reserve(String orderId, String productId, int quantity) {
        ProductInfo product = products.get(productId);
        if (product == null) {
            return false;
        }
        
        if (product.reserve(quantity)) {
            reservations.computeIfAbsent(orderId, k -> new HashMap<>())
                       .put(productId, quantity);
            return true;
        }
        return false;
    }
    
    public synchronized boolean cancelReservation(String orderId, String productId) {
        Map<String, Integer> orderReservations = reservations.get(orderId);
        if (orderReservations == null) {
            return false;
        }
        
        Integer quantity = orderReservations.get(productId);
        if (quantity == null) {
            return false;
        }
        
        ProductInfo product = products.get(productId);
        if (product != null) {
            product.release(quantity);
            orderReservations.remove(productId);
            
            if (orderReservations.isEmpty()) {
                reservations.remove(orderId);
            }
            return true;
        }
        return false;
    }
    
    public synchronized void confirmReservation(String orderId) {
        reservations.remove(orderId);
    }
    
    public boolean hasProduct(String productId) {
        return products.containsKey(productId);
    }
    
    public synchronized int getStock(String productId) {
        ProductInfo product = products.get(productId);
        return product != null ? product.getStock() : 0;
    }
    
    public String getProductName(String productId) {
        ProductInfo product = products.get(productId);
        return product != null ? product.getName() : null;
    }
    
    public Map<String, String> getAllProductNames() {
        Map<String, String> result = new HashMap<>();
        for (ProductInfo product : products.values()) {
            result.put(product.getProductId(), product.getName());
        }
        return result;
    }
    
    public void printStatus() {
        System.out.println("\n=== Inventory Status ===");
        for (ProductInfo p : products.values()) {
            System.out.println(p.getProductId() + ": " + p.getName() + 
                             " - Available: " + p.getStock());
        }
        if (!reservations.isEmpty()) {
            System.out.println("Active reservations: " + reservations.size());
        }
    }
}