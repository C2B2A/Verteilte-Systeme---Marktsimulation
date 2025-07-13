package main.seller;

/**
 * Einfache Produktklasse
 * Jeder Seller hat 2 verschiedene Produkte
 */
public class Product {
    private final String productId;
    private final String name;
    private int stock;
    
    public Product(String productId, String name, int stock) {
        this.productId = productId;
        this.name = name;
        this.stock = stock;
    }
    
    // Thread-safe stock operations
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