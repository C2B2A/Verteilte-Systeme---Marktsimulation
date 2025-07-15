package main;

import main.messaging.MessageTypes.OrderRequest;
import main.messaging.MessageTypes.OrderRequest.ProductOrder;
import java.util.*;

/**
 * Repräsentiert einen Kunden der Bestellungen aufgibt
 * Kann für Tests oder manuelle Bestellungen genutzt werden
 */
public class Customer {
    private final String customerId;
    private final Random random = new Random();
    
    public Customer(String customerId) {
        this.customerId = customerId;
    }
    
    /**
     * Erstellt eine zufällige Bestellung
     */
    public OrderRequest createRandomOrder() {
        OrderRequest order = new OrderRequest();
        order.orderId = "ORD-" + System.currentTimeMillis();
        order.customerId = customerId;
        order.products = new ArrayList<>();
        
        // 1-3 zufällige Produkte
        int productCount = random.nextInt(3) + 1;
        Set<String> usedProducts = new HashSet<>();
        
        for (int i = 0; i < productCount; i++) {
            String productId = getRandomProduct();
            while (usedProducts.contains(productId)) {
                productId = getRandomProduct();
            }
            usedProducts.add(productId);
            
            int quantity = random.nextInt(3) + 1;
            order.products.add(new ProductOrder(productId, quantity));
        }
        
        return order;
    }
    
    /**
     * Erstellt eine spezifische Bestellung
     */
    public OrderRequest createOrder(Map<String, Integer> products) {
        OrderRequest order = new OrderRequest();
        order.orderId = "ORD-" + System.currentTimeMillis();
        order.customerId = customerId;
        order.products = new ArrayList<>();
        
        for (Map.Entry<String, Integer> entry : products.entrySet()) {
            order.products.add(new ProductOrder(entry.getKey(), entry.getValue()));
        }
        
        return order;
    }
    
    private String getRandomProduct() {
        // Zufälliger Seller (S1-S5)
        int sellerId = random.nextInt(5) + 1;
        // Zufälliges Produkt (A oder B)
        char productType = random.nextBoolean() ? 'A' : 'B';
        
        return "PS" + sellerId + productType;
    }
    
    public String getCustomerId() {
        return customerId;
    }
}