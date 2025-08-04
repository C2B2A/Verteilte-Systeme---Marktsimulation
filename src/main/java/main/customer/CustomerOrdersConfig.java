package main.customer;

import main.messaging.Messages;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Konfiguration für Customer-Bestellungen
 * Ermöglicht Umschaltung zwischen generierten und vordefinierten Bestellungen
 */
public class CustomerOrdersConfig {
    
    // Hauptschalter: true = automatisch generieren, false = vordefinierte verwenden
    private static final boolean GENERATE_ORDERS = false;
    
    // Vordefinierte Bestellungen - können nach Bedarf angepasst werden
    private static final List<PredefinedOrder> PREDEFINED_ORDERS = Arrays.asList(
        // Bestellung 1: Einfache Bestellung von S1
        new PredefinedOrder(
            Arrays.asList(
                new Messages.OrderRequest.ProductOrder("PA", 2),
                new Messages.OrderRequest.ProductOrder("PB", 1)
            )
        ),
        
        // Bestellung 2: Produkte von mehreren Sellern
        new PredefinedOrder(
            Arrays.asList(
                new Messages.OrderRequest.ProductOrder("PC", 1),
                new Messages.OrderRequest.ProductOrder("PE", 2),
                new Messages.OrderRequest.ProductOrder("PF", 1)
            )
        ),
        
        // Bestellung 3: Test für Failover (PC bei S2 und S3 verfügbar)
        new PredefinedOrder(
            Arrays.asList(
                new Messages.OrderRequest.ProductOrder("PC", 3),
                new Messages.OrderRequest.ProductOrder("PD", 2)
            )
        ),
        
        // Bestellung 4: Große Bestellung (könnte fehlschlagen)
        new PredefinedOrder(
            Arrays.asList(
                new Messages.OrderRequest.ProductOrder("PA", 5),
                new Messages.OrderRequest.ProductOrder("PB", 4)
            )
        ),
        
        // Bestellung 5: Test für doppelte Produkte (fachlicher Fehler)
        new PredefinedOrder(
            Arrays.asList(
                new Messages.OrderRequest.ProductOrder("PE", 2),
                new Messages.OrderRequest.ProductOrder("PE", 1), // Duplikat!
                new Messages.OrderRequest.ProductOrder("PD", 1)
            )
        )
    );
    
    // Hilfklasse für vordefinierte Bestellungen
    private static class PredefinedOrder {
        final List<Messages.OrderRequest.ProductOrder> products;
        
        PredefinedOrder(List<Messages.OrderRequest.ProductOrder> products) {
            this.products = products;
        }
    }
    
    // Index für Round-Robin durch vordefinierte Bestellungen
    private static int currentOrderIndex = 0;
    
    /**
     * Gibt zurück ob Bestellungen generiert werden sollen
     */
    public static boolean shouldGenerateOrders() {
        return GENERATE_ORDERS;
    }
    
    /**
     * Holt die nächste vordefinierte Bestellung (Round-Robin)
     */
    public static synchronized Messages.OrderRequest getNextPredefinedOrder(String orderId, String customerId) {
        if (PREDEFINED_ORDERS.isEmpty()) {
            throw new IllegalStateException("Keine vordefinierten Bestellungen konfiguriert!");
        }
        
        // Hole nächste Bestellung im Round-Robin Verfahren
        PredefinedOrder predefined = PREDEFINED_ORDERS.get(currentOrderIndex);
        currentOrderIndex = (currentOrderIndex + 1) % PREDEFINED_ORDERS.size();
        
        // Erstelle OrderRequest
        Messages.OrderRequest order = new Messages.OrderRequest();
        order.orderId = orderId;
        order.customerId = customerId;
        order.products = new ArrayList<>(predefined.products);
        
        return order;
    }
    
    /**
     * Gibt die Anzahl der vordefinierten Bestellungen zurück
     */
    public static int getPredefinedOrderCount() {
        return PREDEFINED_ORDERS.size();
    }
}