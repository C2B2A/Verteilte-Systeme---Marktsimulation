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

// Main class of the Marketplace
// Receives orders from Customers and coordinates with OrderProcessor

public class MarketplaceApp {
    private final String marketplaceId;
    private final int port;
    private final SagaManager sagaManager;
    private final OrderProcessor orderProcessor;
    private final ScheduledExecutorService scheduler;
    private final ZContext context;
    private boolean running = true;

    // Port mapping for marketplaces
    private static final Map<String, Integer> MARKETPLACE_PORTS = Map.of(
            "M1", 5570,
            "M2", 5571);

    private final Map<String, byte[]> orderCustomerMap = new HashMap<>(); // Order-ID -> Customer-Identity
    private ZMQ.Socket receiver; // For status messages to Customer

    public MarketplaceApp(String marketplaceId) {
        this.marketplaceId = marketplaceId;
        this.port = MARKETPLACE_PORTS.getOrDefault(marketplaceId, 5570);
        this.sagaManager = new SagaManager();
        this.orderProcessor = new OrderProcessor(marketplaceId, sagaManager);
        this.scheduler = Executors.newScheduledThreadPool(2);
        this.context = new ZContext();
    }

    // Starts the Marketplace
    public void start() {
        System.out.println("\n╔════════════════════════════════════════════╗");
        System.out.println("║      MARKETPLACE " + marketplaceId + " STARTED               ║");
        System.out.println("╠════════════════════════════════════════════╣");
        System.out.println("║ Port: " + port + " (for Customer orders)     ║");
        System.out.println("║                                            ║");
        System.out.println("║ Product distribution:                      ║");
        System.out.println("║ Seller S1: PA (Product A), PB (Product B) ║");
        System.out.println("║ Seller S2: PC (Product C), PD (Product D) ║");
        System.out.println("║ Seller S3: PE (Product E), PF (Product F) ║");
        System.out.println("║ Seller S4: PG (Product G), PH (Product H) ║");
        System.out.println("║ Seller S5: PI (Product I), PJ (Product J) ║");
        System.out.println("╚════════════════════════════════════════════╝\n");

        ConfigLoader.printConfig();

        // Start order receiver in its own thread
        scheduler.execute(this::receiveOrders);

        // Start status monitor
        scheduler.scheduleWithFixedDelay(
                sagaManager::printActiveSagas,
                5000,
                10000,
                TimeUnit.MILLISECONDS);

        // Shutdown-Hook
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));

        // Wait for termination
        while (running) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                running = false;
            }
        }
    }

    // Receives orders from Customers via ROUTER Socket
    private void receiveOrders() {
        receiver = context.createSocket(SocketType.ROUTER);
        receiver.bind("tcp://127.0.0.1:" + port);

        System.out.println("[" + marketplaceId + "] Waiting for Customer orders on port " + port);

        // Callback for status messages to OrderProcessor
        orderProcessor.setStatusCallback(this::sendOrderStatusToCustomer);

        while (running) {
            try {
                // ROUTER: Receive Customer ID and message
                byte[] customerIdentity = receiver.recv(0);
                String message = receiver.recvStr(0);

                if (message != null && !message.isEmpty()) {
                    Messages.OrderRequest order = Messages.fromJson(message, Messages.OrderRequest.class);
                    if (order != null) {
                        System.out.println("\n========================================");
                        System.out.println("[" + marketplaceId + "] Order received from " + order.customerId);
                        System.out.println("Order ID: " + order.orderId);
                        System.out.println("Products:");
                        for (Messages.OrderRequest.ProductOrder p : order.products) {
                            // Find possible sellers for this product
                            List<String> possibleSellers = findSellersForProduct(p.productId);
                            System.out.println("  - " + p.productId + " x " + p.quantity +
                                    " (available at: " + String.join(", ", possibleSellers) + ")");
                        }
                        System.out.println("========================================");

                        // Customer identity for later status message
                        orderCustomerMap.put(order.orderId, customerIdentity);

                        // Process order
                        orderProcessor.processOrder(order);

                        // Send confirmation back to Customer
                        receiver.sendMore(customerIdentity);
                        receiver.send(marketplaceId + ": Order " + order.orderId + " received and is being processed");
                        System.out.println("[" + marketplaceId + "] Confirmation sent to " + order.customerId);
                    } else {
                        // Invalid message
                        receiver.sendMore(customerIdentity);
                        receiver.send("ERROR: Invalid order");
                    }
                } else {
                    // Empty message
                    receiver.sendMore(customerIdentity);
                    receiver.send("ERROR: Empty message");
                }
            } catch (Exception e) {
                System.err.println("[" + marketplaceId + "] Error receiving: " + e.getMessage());
            }
        }

        receiver.close();
    }

    // Sends status message to customer after order completion
    public void sendOrderStatusToCustomer(String orderId, String statusMessage) {
        byte[] customerIdentity = orderCustomerMap.get(orderId);
        if (customerIdentity != null && receiver != null) {
            receiver.sendMore(customerIdentity);
            receiver.send(statusMessage);
            System.out.println(
                    "[" + marketplaceId + "] Status for Order " + orderId + " sent to Customer: " + statusMessage);
            orderCustomerMap.remove(orderId); // remove after sending
        }
    }

    // Finds all sellers that have a specific product
    private List<String> findSellersForProduct(String productId) {
        Map<String, List<String>> productSellerMap = new HashMap<>();
        productSellerMap.put("PA", Arrays.asList("S1"));
        productSellerMap.put("PB", Arrays.asList("S1"));
        productSellerMap.put("PC", Arrays.asList("S2"));
        productSellerMap.put("PD", Arrays.asList("S2"));
        productSellerMap.put("PE", Arrays.asList("S3"));
        productSellerMap.put("PF", Arrays.asList("S3"));
        productSellerMap.put("PG", Arrays.asList("S4"));
        productSellerMap.put("PH", Arrays.asList("S4"));
        productSellerMap.put("PI", Arrays.asList("S5"));
        productSellerMap.put("PJ", Arrays.asList("S5"));

        return productSellerMap.getOrDefault(productId, new ArrayList<>());
    }

    // Ends the Marketplace
    public void shutdown() {
        System.out.println("\n[" + marketplaceId + "] Shut down...");
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

        System.out.println("[" + marketplaceId + "] Finished.");
    }

    // Main method
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

        // Load configuration
        if (configFile != null) {
            ConfigLoader.loadConfig(configFile);
        }

        // Start Marketplace
        MarketplaceApp marketplace = new MarketplaceApp(marketplaceId);
        marketplace.start();
    }
}