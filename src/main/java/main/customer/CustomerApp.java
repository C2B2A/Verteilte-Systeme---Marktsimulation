package main.customer;

import main.messaging.Messages;
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
    private final ZMQ.Socket dealerSocket;
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
        this.scheduler = Executors.newScheduledThreadPool(2); // 1 für Orders, 1 für Responses
        this.random = new Random();
        this.orderCounter = new AtomicInteger(0);
        
        // Persistenter DEALER Socket wie OrderProcessor
        this.dealerSocket = context.createSocket(SocketType.DEALER);
        this.dealerSocket.setIdentity(customerId.getBytes());
        
        // Verbinde zu allen Marketplaces
        for (int port : MARKETPLACE_PORTS) {
            this.dealerSocket.connect("tcp://localhost:" + port);
        }
    }
    
    public void start() {
        System.out.println("\n╔════════════════════════════════════════════╗");
        System.out.println("║      CUSTOMER " + customerId + " GESTARTET                  ║");
        System.out.println("╠════════════════════════════════════════════╣");
        System.out.println("║ Sendet Bestellungen an Marketplaces        ║");
        System.out.println("║ Marketplace M1: Port 5570                  ║");
        System.out.println("║ Marketplace M2: Port 5571                  ║");
        System.out.println("╠════════════════════════════════════════════╣");
        
        // Zeige Bestellmodus
        if (CustomerOrdersConfig.shouldGenerateOrders()) {
            System.out.println("║ Modus: GENERIERTE Bestellungen             ║");
        } else {
            System.out.println("║ Modus: VORDEFINIERTE Bestellungen          ║");
            System.out.println("║ Anzahl: " + String.format("%-35d", CustomerOrdersConfig.getPredefinedOrderCount()) + "║");
        }
        
        System.out.println("╚════════════════════════════════════════════╝\n");
        
        ConfigLoader.printConfig();
        
        // Starte Response-Empfänger (wie OrderProcessor)
        scheduler.execute(this::receiveMarketplaceResponses);
        
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
            String orderId = customerId + "-ORD" + String.format("%04d", orderCounter.incrementAndGet());
            Messages.OrderRequest order;
            if (CustomerOrdersConfig.shouldGenerateOrders()) {
                order = createRandomOrder(orderId);
                System.out.println("\n[" + customerId + "] Generiere zufällige Bestellung");
            } else {
                order = CustomerOrdersConfig.getNextPredefinedOrder(orderId, customerId);
                System.out.println("\n[" + customerId + "] Verwende vordefinierte Bestellung");
            }
            // Immer Bestellinhalt anzeigen
            System.out.println("========================================");
            System.out.println("[" + customerId + "] Bestellung an Marketplace:");
            System.out.println("Order ID: " + order.orderId);
            System.out.println("Produkte:");
            for (Messages.OrderRequest.ProductOrder p : order.products) {
                System.out.println("  - " + p.productId + " x " + p.quantity);
            }
            System.out.println("========================================");
            
            // Sende asynchron über persistenten Socket (wie OrderProcessor)
            String message = Messages.toJson(order);
            dealerSocket.sendMore("");  // Leerer Routing-Frame für Auto-Routing
            dealerSocket.send(message); // Message-Frame
            System.out.println("[" + customerId + "] Bestellung gesendet (asynchron)!");
        } catch (Exception e) {
            System.err.println("[" + customerId + "] Fehler beim Senden: " + e.getMessage());
        }
    }
    
    private Messages.OrderRequest createRandomOrder(String orderId) {
        Messages.OrderRequest order = new Messages.OrderRequest();
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
                order.products.add(new Messages.OrderRequest.ProductOrder(duplicateProduct, quantity));
                System.out.println("  WARNUNG: Doppelte Produktanforderung simuliert!");
                break;
            } else {
                order.products.add(new Messages.OrderRequest.ProductOrder(productId, quantity));
            }
        }
        
        return order;
    }
    
    /**
     * Empfangs-Thread für Marketplace-Antworten (wie OrderProcessor)
     */
    private void receiveMarketplaceResponses() {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                String response = dealerSocket.recvStr();
                if (response != null && !response.isEmpty()) {
                    System.out.println("[" + customerId + "] Antwort vom Marketplace: " + response);
                }
            } catch (Exception e) {
                if (running) {
                    System.err.println("[" + customerId + "] Fehler beim Empfangen: " + e.getMessage());
                }
            }
        }
    }
    
    public void shutdown() {
        System.out.println("\n[" + customerId + "] Fahre herunter...");
        running = false;
        scheduler.shutdown();
        dealerSocket.close();
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