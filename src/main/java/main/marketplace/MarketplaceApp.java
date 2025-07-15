package main.marketplace;

import main.messaging.MessageTypes.*;
import main.simulation.ConfigLoader;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Hauptklasse des Marketplace
 * Generiert Bestellungen und koordiniert mit OrderProcessor
 */
public class MarketplaceApp {
    private final String marketplaceId;
    private final SagaManager sagaManager;
    private final OrderProcessor orderProcessor;
    private final ScheduledExecutorService scheduler;
    private final AtomicInteger orderCounter;
    private boolean running = true;
    
    // Verfügbare Produkte im System
    private static final List<String> AVAILABLE_PRODUCTS = Arrays.asList(
        "PS1A", "PS1B", "PS2A", "PS2B", "PS3A", "PS3B", 
        "PS4A", "PS4B", "PS5A", "PS5B"
    );
    
    public MarketplaceApp(String marketplaceId) {
        this.marketplaceId = marketplaceId;
        this.sagaManager = new SagaManager();
        this.orderProcessor = new OrderProcessor(marketplaceId, sagaManager);
        this.scheduler = Executors.newScheduledThreadPool(2);
        this.orderCounter = new AtomicInteger(0);
    }
    
    /**
     * Startet den Marketplace
     */
    public void start() {
        System.out.println("=== Marketplace " + marketplaceId + " gestartet ===");
        ConfigLoader.printConfig();
        
        // Starte Order-Generator
        int orderDelay = ConfigLoader.getOrderDelay();
        scheduler.scheduleWithFixedDelay(
            this::generateOrder, 
            1000, // Initial delay
            orderDelay, 
            TimeUnit.MILLISECONDS
        );
        
        // Starte Status-Monitor
        scheduler.scheduleWithFixedDelay(
            sagaManager::printActiveSagas,
            5000,
            10000,
            TimeUnit.MILLISECONDS
        );
        
        // Shutdown-Hook
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
        
        // Warte auf Beendigung
        while (running) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                running = false;
            }
        }
    }
    
    /**
     * Generiert eine zufällige Bestellung
     */
    private void generateOrder() {
        try {
            String orderId = marketplaceId + "-ORD" + 
                           String.format("%04d", orderCounter.incrementAndGet());
            String customerId = "C" + (new Random().nextInt(100) + 1);
            
            // Zufällige Anzahl von Produkten (1-3)
            Random random = new Random();
            int productCount = random.nextInt(3) + 1;
            
            List<OrderRequest.ProductOrder> products = new ArrayList<>();
            Set<String> usedProducts = new HashSet<>();
            
            for (int i = 0; i < productCount; i++) {
                // Wähle zufälliges Produkt (keine Duplikate)
                String productId;
                do {
                    productId = AVAILABLE_PRODUCTS.get(
                        random.nextInt(AVAILABLE_PRODUCTS.size()));
                } while (usedProducts.contains(productId));
                usedProducts.add(productId);
                
                // Zufällige Menge (1-3)
                int quantity = random.nextInt(3) + 1;
                products.add(new OrderRequest.ProductOrder(productId, quantity));
            }
            
            // Erstelle Bestellung
            OrderRequest order = new OrderRequest();
            order.orderId = orderId;
            order.customerId = customerId;
            order.products = products;
            
            System.out.println("\n========================================");
            System.out.println("[Marketplace] Neue Bestellung generiert:");
            System.out.println("Order ID: " + orderId);
            System.out.println("Kunde: " + customerId);
            System.out.println("Produkte:");
            for (OrderRequest.ProductOrder p : products) {
                System.out.println("  - " + p.productId + " x " + p.quantity);
            }
            System.out.println("========================================");
            
            // Verarbeite Bestellung
            orderProcessor.processOrder(order);
            
        } catch (Exception e) {
            System.err.println("[Marketplace] Fehler beim Generieren: " + e.getMessage());
        }
    }
    
    /**
     * Beendet den Marketplace
     */
    public void shutdown() {
        System.out.println("\n[Marketplace] Fahre herunter...");
        running = false;
        scheduler.shutdown();
        orderProcessor.shutdown();
        
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
        
        System.out.println("[Marketplace] Beendet.");
    }
    
    /**
     * Main-Methode
     */
    public static void main(String[] args) {
        String marketplaceId = "M1";
        String configFile = null;
        
        // Parse arguments
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--id") && i + 1 < args.length) {
                marketplaceId = args[++i];
            } else if (args[i].equals("--config") && i + 1 < args.length) {
                configFile = args[++i];
            }
        }
        
        // Lade Konfiguration
        if (configFile != null) {
            ConfigLoader.loadConfig(configFile);
        }
        
        // Starte Marketplace
        MarketplaceApp marketplace = new MarketplaceApp(marketplaceId);
        marketplace.start();
    }
}