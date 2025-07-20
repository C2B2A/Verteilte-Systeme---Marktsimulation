package main.customer;

import main.messaging.MessageHandler;
import main.messaging.MessageTypes.OrderRequest;
import main.messaging.MessageTypes.OrderRequest.ProductOrder;
import main.simulation.ConfigLoader;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Customer-Komponente die Bestellungen an Marketplaces sendet
 * Verwendet PUSH Socket für Fire-and-Forget Messaging
 */
public class CustomerApp {
    private final String customerId;
    private final ZContext context;
    private final ScheduledExecutorService scheduler;
    private final Random random;
    private final AtomicInteger orderCounter;
    private boolean running = true;
    
    // Marketplace Ports für PULL Sockets
    private static final int[] MARKETPLACE_PORTS = {5570, 5571}; // M1, M2
    
    // Verfügbare Produkte im System
    private static final List<String> AVAILABLE_PRODUCTS = Arrays.asList(
        "PA", "PB", "PC", "PD", "PE", "PF"
    );
    
    public CustomerApp(String customerId) {
        this.customerId = customerId;
        this.context = new ZContext();
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.random = new Random();
        this.orderCounter = new AtomicInteger(0);
    }
    
    public void start() {
        System.out.println("\n╔════════════════════════════════════════════╗");
        System.out.println("║      CUSTOMER " + customerId + " GESTARTET                  ║");
        System.out.println("╠════════════════════════════════════════════╣");
        System.out.println("║ Sendet Bestellungen an Marketplaces        ║");
        System.out.println("║ Marketplace M1: Port 5570                  ║");
        System.out.println("║ Marketplace M2: Port 5571                  ║");
        System.out.println("╚════════════════════════════════════════════╝\n");
        
        ConfigLoader.printConfig();
        
        // Starte Order-Generator
        int orderDelay = ConfigLoader.getOrderDelay();
        scheduler.scheduleWithFixedDelay(
            this::sendOrder,
            2000, // Initial delay
            orderDelay,
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
    
    private void sendOrder() {
        try {
            // Erstelle Bestellung
            String orderId = customerId + "-ORD" + 
                           String.format("%04d", orderCounter.incrementAndGet());
            
            OrderRequest order = createRandomOrder(orderId);
            
            // Wähle zufälligen Marketplace
            int marketplacePort = MARKETPLACE_PORTS[random.nextInt(MARKETPLACE_PORTS.length)];
            String marketplaceId = marketplacePort == 5570 ? "M1" : "M2";
            
            System.out.println("\n========================================");
            System.out.println("[" + customerId + "] Neue Bestellung an " + marketplaceId);
            System.out.println("Order ID: " + order.orderId);
            System.out.println("Produkte:");
            for (OrderRequest.ProductOrder p : order.products) {
                System.out.println("  - " + p.productId + " x " + p.quantity);
            }
            System.out.println("========================================");
            
            // Sende an Marketplace via PUSH
            ZMQ.Socket socket = context.createSocket(SocketType.PUSH);
            socket.connect("tcp://localhost:" + marketplacePort);
            
            String message = MessageHandler.toJson(order);
            socket.send(message);
            socket.close();
            
            System.out.println("[" + customerId + "] Bestellung gesendet!");
            
        } catch (Exception e) {
            System.err.println("[" + customerId + "] Fehler beim Senden: " + e.getMessage());
        }
    }
    
    private OrderRequest createRandomOrder(String orderId) {
        OrderRequest order = new OrderRequest();
        order.orderId = orderId;
        order.customerId = customerId;
        order.products = new ArrayList<>();
        
        // 1-3 zufällige Produkte
        int productCount = random.nextInt(3) + 1;
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
            
            // Chance für doppelte Produktanforderung (fachlicher Fehler)
            if (random.nextDouble() < ConfigLoader.getDuplicateProductProbability() && i > 0) {
                // Nimm ein bereits verwendetes Produkt
                String duplicateProduct = order.products.get(0).productId;
                order.products.add(new ProductOrder(duplicateProduct, quantity));
                System.out.println("  WARNUNG: Doppelte Produktanforderung simuliert!");
                break;
            } else {
                order.products.add(new ProductOrder(productId, quantity));
            }
        }
        
        return order;
    }
    
    public void shutdown() {
        System.out.println("\n[" + customerId + "] Fahre herunter...");
        running = false;
        scheduler.shutdown();
        context.close();
        
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
        
        System.out.println("[" + customerId + "] Beendet.");
    }
    
    public static void main(String[] args) {
        String customerId = "C1";
        String configFile = null;
        
        // Parse arguments
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("--id=")) {
                customerId = args[i].substring(5);
            } else if (args[i].equals("--id") && i + 1 < args.length) {
                customerId = args[++i];
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
        
        // Starte Customer
        CustomerApp app = new CustomerApp(customerId);
        app.start();
    }
}