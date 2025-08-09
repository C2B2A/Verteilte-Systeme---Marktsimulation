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

// Customer component that sends orders to marketplaces - Uses PUSH socket for fire-and-forget messaging

public class CustomerApp {
    private final String customerId;
    private final ZContext context;
    private final ScheduledExecutorService scheduler;
    private final Random random;
    private final AtomicInteger orderCounter;
    private final ZMQ.Socket dealerSocket;
    private boolean running = true;

    // Marketplace Ports for PULL Sockets
    private static final int[] MARKETPLACE_PORTS = { 5570, 5571 }; // M1, M2

    // Available products for random order generation
    private static final List<String> AVAILABLE_PRODUCTS = Arrays.asList(
            "PA", "PB", "PC", "PD", "PE", "PF", "PG", "PH", "PI", "PJ");

    public CustomerApp(String customerId) {
        this.customerId = customerId;
        this.context = new ZContext();
        this.scheduler = Executors.newScheduledThreadPool(2); // 1 for Orders, 1 for Responses
        this.random = new Random();
        this.orderCounter = new AtomicInteger(0);

        // Persistent DEALER Socket like OrderProcessor
        this.dealerSocket = context.createSocket(SocketType.DEALER);
        this.dealerSocket.setIdentity(customerId.getBytes());

        // Connect to all marketplaces
        for (int port : MARKETPLACE_PORTS) {
            this.dealerSocket.connect("tcp://localhost:" + port);
        }
    }

    public void start() {
        System.out.println("\n╔════════════════════════════════════════════╗");
        System.out.println("║      CUSTOMER " + customerId + " STARTED                  ║");
        System.out.println("╠════════════════════════════════════════════╣");
        System.out.println("║ Sends orders to Marketplaces               ║");
        System.out.println("║ Marketplace M1: Port 5570                  ║");
        System.out.println("║ Marketplace M2: Port 5571                  ║");
        System.out.println("╠════════════════════════════════════════════╣");

        // Shows Bestellmodus
        if (CustomerOrdersConfig.shouldGenerateOrders()) {
            System.out.println("║ Mode: GENERATED orders                     ║");
        } else {
            System.out.println("║ Mode: PREDEFINED orders                    ║");
            System.out.println(
                    "║ Number: " + String.format("%-35d", CustomerOrdersConfig.getPredefinedOrderCount()) + "║");
        }

        System.out.println("╚════════════════════════════════════════════╝\n");

        ConfigLoader.printConfig();

        // Start Response receiver (like OrderProcessor)
        scheduler.execute(this::receiveMarketplaceResponses);

        // Scheduler starts periodic sending of orders
        int orderDelay = ConfigLoader.getOrderDelay();
        scheduler.scheduleWithFixedDelay(
                this::sendOrder,
                8000L,
                orderDelay,
                TimeUnit.MILLISECONDS);

        // Shutdown-Hook
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));

        // Wait for completion
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
                System.out.println("\n[" + customerId + "] Generate random order");
            } else {
                order = CustomerOrdersConfig.getNextPredefinedOrder(orderId, customerId);
                System.out.println("\n[" + customerId + "] Use predefined order");
            }
            // Always show order content
            System.out.println("========================================");
            System.out.println("[" + customerId + "] Order on Marketplace:");
            System.out.println("Order ID: " + order.orderId);
            System.out.println("Products:");
            for (Messages.OrderRequest.ProductOrder p : order.products) {
                System.out.println("  - " + p.productId + " x " + p.quantity);
            }

            // Send asynchronously via persistent socket (like OrderProcessor)
            String message = Messages.toJson(order);
            // dealerSocket.sendMore(""); // Empty routing frame for auto-routing, not
            // necessary
            dealerSocket.send(message); // Message-Frame
            System.out.println("[" + customerId + "] Order sent!");
            System.out.println("======================================== \n");
        } catch (Exception e) {
            System.err.println("[" + customerId + "]  Error sending:" + e.getMessage());
        }
    }

    private Messages.OrderRequest createRandomOrder(String orderId) {
        Messages.OrderRequest order = new Messages.OrderRequest();
        order.orderId = orderId;
        order.customerId = customerId;
        order.products = new ArrayList<>();

        // 1-3 random products
        int productCount = random.nextInt(3) + 1;
        Set<String> usedProducts = new HashSet<>();

        for (int i = 0; i < productCount; i++) {
            // Choose random product (no duplicates)
            String productId;
            do {
                productId = AVAILABLE_PRODUCTS.get(
                        random.nextInt(AVAILABLE_PRODUCTS.size()));
            } while (usedProducts.contains(productId));
            usedProducts.add(productId);

            // Random set (1-3)
            int quantity = random.nextInt(3) + 1;

            // Chance for duplicate product requirements (technical error)
            if (random.nextDouble() < ConfigLoader.getDuplicateProductProbability() && i > 0) {
                // Take a product you have already used
                String duplicateProduct = order.products.get(0).productId;
                order.products.add(new Messages.OrderRequest.ProductOrder(duplicateProduct, quantity));
                System.out.println(" \n Attention: In the following - duplicate product requirement simulated! ");
                break;
            } else {
                order.products.add(new Messages.OrderRequest.ProductOrder(productId, quantity));
            }
        }

        return order;
    }

    // Receiving thread for Marketplace responses (like OrderProcessor)

    private void receiveMarketplaceResponses() {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                String response = dealerSocket.recvStr();
                if (response != null && !response.isEmpty()) {
                    System.out.println("[" + customerId + "] Answer from Marketplace " + response);
                }
            } catch (Exception e) {
                if (running) {
                    System.err.println("[" + customerId + "] Error receiving: " + e.getMessage());
                }
            }
        }
    }

    public void shutdown() {
        System.out.println("\n[" + customerId + "] Shut down...");
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
        System.out.println("[" + customerId + "] Finished.");
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

        // Load configuration
        if (configFile != null) {
            ConfigLoader.loadConfig(configFile);
        }

        // Start Customer
        CustomerApp app = new CustomerApp(customerId);
        app.start();
    }
}