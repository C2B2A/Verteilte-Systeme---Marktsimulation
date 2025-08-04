package main.marketplace;

import main.messaging.Messages;
import main.simulation.ConfigLoader;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Hauptklasse des Marketplace
 * Empfängt Bestellungen von Customers und koordiniert mit OrderProcessor
 */
public class MarketplaceApp {
    private final String marketplaceId;
    private final int port;
    private final SagaManager sagaManager;
    private final OrderProcessor orderProcessor;
    private final ScheduledExecutorService scheduler;
    private final ZContext context;
    private boolean running = true;
    
    // Port-Zuordnung für Marketplaces
    private static final Map<String, Integer> MARKETPLACE_PORTS = Map.of(
        "M1", 5570,
        "M2", 5571
    );
    
    public MarketplaceApp(String marketplaceId) {
        this.marketplaceId = marketplaceId;
        this.port = MARKETPLACE_PORTS.getOrDefault(marketplaceId, 5570);
        this.sagaManager = new SagaManager();
        this.orderProcessor = new OrderProcessor(marketplaceId, sagaManager);
        this.scheduler = Executors.newScheduledThreadPool(2);
        this.context = new ZContext();
    }
    
    /**
     * Startet den Marketplace
     */
    public void start() {
        System.out.println("\n╔════════════════════════════════════════════╗");
        System.out.println("║      MARKETPLACE " + marketplaceId + " GESTARTET             ║");
        System.out.println("╠════════════════════════════════════════════╣");
        System.out.println("║ Port: " + port + " (für Customer-Bestellungen)     ║");
        System.out.println("║                                            ║");
        System.out.println("║ Produktverteilung:                         ║");
        System.out.println("║ Seller S1: PA (Produkt A), PB (Produkt B) ║");
        System.out.println("║ Seller S2: PC (Produkt C), PD (Produkt D) ║");
        System.out.println("║ Seller S3: PC (Produkt C), PE (Produkt E) ║");
        System.out.println("║ Seller S4: PD (Produkt D), PE (Produkt E) ║");
        System.out.println("║ Seller S5: PF (Produkt F), PB (Produkt B) ║");
        System.out.println("╚════════════════════════════════════════════╝\n");
        
        ConfigLoader.printConfig();
        
        // Starte Order-Receiver in eigenem Thread
        scheduler.execute(this::receiveOrders);
        
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
     * Empfängt Bestellungen von Customers via ROUTER Socket
     */
    private void receiveOrders() {
        ZMQ.Socket receiver = context.createSocket(SocketType.ROUTER);
        receiver.bind("tcp://127.0.0.1:" + port);
        
        System.out.println("[" + marketplaceId + "] Warte auf Customer-Bestellungen auf Port " + port);
        
        while (running) {
            try {
                // ROUTER: Empfange Customer-ID und Nachricht
                byte[] customerIdentity = receiver.recv(0);
                String message = receiver.recvStr(0);
                
                if (message != null && !message.isEmpty()) {
                    Messages.OrderRequest order = Messages.fromJson(message, Messages.OrderRequest.class);
                    if (order != null) {
                        System.out.println("\n========================================");
                        System.out.println("[" + marketplaceId + "] Bestellung empfangen von " + order.customerId);
                        System.out.println("Order ID: " + order.orderId);
                        System.out.println("Produkte:");
                        for (Messages.OrderRequest.ProductOrder p : order.products) {
                            // Finde mögliche Seller für dieses Produkt
                            List<String> possibleSellers = findSellersForProduct(p.productId);
                            System.out.println("  - " + p.productId + " x " + p.quantity + 
                                             " (verfügbar bei: " + String.join(", ", possibleSellers) + ")");
                        }
                        System.out.println("========================================");
                        
                        // Verarbeite Bestellung
                        orderProcessor.processOrder(order);
                        
                        // Sende Bestätigung zurück an Customer
                        receiver.sendMore(customerIdentity);
                        receiver.send("Bestellung " + order.orderId + " empfangen und wird verarbeitet");
                        System.out.println("[" + marketplaceId + "] Bestätigung an " + order.customerId + " gesendet");
                    } else {
                        // Fehlerhafte Nachricht
                        receiver.sendMore(customerIdentity);
                        receiver.send("FEHLER: Ungültige Bestellung");
                    }
                } else {
                    // Leere Nachricht
                    receiver.sendMore(customerIdentity);
                    receiver.send("FEHLER: Leere Nachricht");
                }
            } catch (Exception e) {
                System.err.println("[" + marketplaceId + "] Fehler beim Empfangen: " + e.getMessage());
            }
        }
        
        receiver.close();
    }
    
    /**
     * Findet alle Seller die ein bestimmtes Produkt haben
     */
    private List<String> findSellersForProduct(String productId) {
        Map<String, List<String>> productSellerMap = new HashMap<>();
        productSellerMap.put("PA", Arrays.asList("S1"));
        productSellerMap.put("PB", Arrays.asList("S1", "S5"));
        productSellerMap.put("PC", Arrays.asList("S2", "S3"));
        productSellerMap.put("PD", Arrays.asList("S2", "S4"));
        productSellerMap.put("PE", Arrays.asList("S3", "S4"));
        productSellerMap.put("PF", Arrays.asList("S5"));
        
        return productSellerMap.getOrDefault(productId, new ArrayList<>());
    }
    
    /**
     * Beendet den Marketplace
     */
    public void shutdown() {
        System.out.println("\n[" + marketplaceId + "] Fahre herunter...");
        running = false;
        scheduler.shutdown();
        orderProcessor.shutdown();
        context.close();
        
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
        
        System.out.println("[" + marketplaceId + "] Beendet.");
    }
    
    /**
     * Main-Methode
     */
    public static void main(String[] args) {
        String marketplaceId = "M1";
        String configFile = null;
        
        // Parse arguments
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("--id=")) {
                marketplaceId = args[i].substring(5);
            } else if (args[i].equals("--id") && i + 1 < args.length) {
                marketplaceId = args[++i];
            } else if (args[i].startsWith("--config=")) {
                configFile = args[i].substring(9);
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