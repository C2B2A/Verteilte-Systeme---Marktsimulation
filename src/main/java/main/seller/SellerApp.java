package main.seller;

import main.messaging.Messages;
import main.simulation.ConfigLoader;
import main.simulation.ErrorSimulator;
import main.simulation.ErrorSimulator.ErrorType;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import java.util.Map;

// Seller with correct product distribution
public class SellerApp {
    private final String sellerId;
    private final int port;
    private final SellerInventory inventory;
    private boolean running = true;
    
    public SellerApp(String sellerId, int port) {
        this.sellerId = sellerId;
        this.port = port;
        this.inventory = new SellerInventory();

        // Initialize products according to requirements
        initializeProducts();
    }

    // Initializes the products for each seller - evenly distributed for simplified traceability
    private void initializeProducts() {
        int initialStock = ConfigLoader.getInitialStock();
        
        switch (sellerId) {
            case "S1":
                inventory.addProduct("PA", "product A", initialStock);
                inventory.addProduct("PB", "product B", initialStock - 2);
                break;
            case "S2":
                inventory.addProduct("PC", "product C", initialStock);
                inventory.addProduct("PD", "product D", initialStock - 2);
                break;
            case "S3":
                inventory.addProduct("PE", "product E", initialStock);
                inventory.addProduct("PF", "product F", initialStock - 2);
                break;
            case "S4":
                inventory.addProduct("PG", "product G", initialStock);
                inventory.addProduct("PH", "product H", initialStock - 2);
                break;
            case "S5":
                inventory.addProduct("PI", "product I", initialStock);
                inventory.addProduct("PJ", "product J", initialStock - 2);
                break;
            default:
                // Fallback for unknown sellers
                System.err.println("Warning: Unknown seller ID " + sellerId);
                inventory.addProduct("PX", "product X", initialStock);
                inventory.addProduct("PY", "product Y", initialStock - 2);
        }
    }
    
    public void start() {
        System.out.println("\n╔════════════════════════════════════════════╗");
        System.out.println("║        SELLER " + sellerId + " STARTED                 ║");
        System.out.println("╠════════════════════════════════════════════╣");
        System.out.println("║ Port: " + port + "                                  ║");
        System.out.println("║ Products:                                  ║");
        for (Map.Entry<String, String> entry : inventory.getAllProductNames().entrySet()) {
            String productId = entry.getKey();
            String name = entry.getValue();
            int stock = inventory.getStock(productId);
            System.out.println("║   - " + productId + " (" + name + "): Stock " + 
                             String.format("%-2d", stock) + "      ║");
        }
        System.out.println("╚════════════════════════════════════════════╝\n");
        ConfigLoader.printConfig();
        int networkLatencyMs = 50; // Simulated latency (configurable in config.properties)
        try (ZContext context = new ZContext()) {
            ZMQ.Socket socket = context.createSocket(SocketType.ROUTER);
            socket.bind("tcp://127.0.0.1:" + port);
            System.out.println("[" + sellerId + "] Ready for requests on port " + port);
            while (running) {
                try {
                    // ROUTER: Receive identity and message
                    byte[] identity = socket.recv(0);
                    String request = socket.recvStr(0);
                    System.out.println("\n[" + sellerId + "] Received from " + new String(identity) + ": " + request);
                    Thread.sleep(networkLatencyMs); // Simulate network latency
                    ErrorType error = ErrorSimulator.getNextError();
                    System.out.println("[" + sellerId + "] Error type: " + error);
                    ErrorSimulator.simulateProcessing();
                    String response = "";
                    switch (error) {
                        case SUCCESS:
                            response = processMessage(request);
                            System.out.println("[" + sellerId + "] Sent: " + response);
                            break;
                        case FAIL_NO_RESPONSE:
                            System.out.println("[" + sellerId + "] TECHNICAL ERROR: No response (Timeout)");
                            response = "";
                            break;
                        case FAIL_CRASH:
                            processMessage(request);
                            System.out.println("[" + sellerId + "] TECHNICAL ERROR: Crash after processing");
                            response = "";
                            break;
                    }
                    // Send response (even empty, so DEALER is not blocked)
                    socket.sendMore(identity);
                    socket.send(response);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    running = false;
                } catch (Exception e) {
                    System.err.println("[" + sellerId + "] Error: " + e.getMessage());
                    running = false;
                }
            }
        }
    }
    
private String processMessage(String message) {
    String messageType = Messages.getMessageType(message);
    switch (messageType) {
        case "ReserveRequest":
            Messages.ReserveRequest req = Messages.fromJson(message, Messages.ReserveRequest.class);
            return handleReserve(req);
        case "CancelRequest":
            Messages.CancelRequest cancel = Messages.fromJson(message, Messages.CancelRequest.class);
            return handleCancel(cancel);
        case "ConfirmRequest":
            Messages.ConfirmRequest confirm = Messages.fromJson(message, Messages.ConfirmRequest.class);
            return handleConfirm(confirm);
        default:
            // Try to extract orderId and productId anyway
            Messages.ReserveResponse response = new Messages.ReserveResponse();
            response.orderId = Messages.extractJsonValue(message, "orderId");
            response.productId = Messages.extractJsonValue(message, "productId");
            response.sellerId = sellerId;
            response.status = "FAILED";
            response.reason = "Unknown message type: " + messageType;
            return Messages.toJson(response);
    }
}
    
    private String handleReserve(Messages.ReserveRequest req) {
        Messages.ReserveResponse response = new Messages.ReserveResponse();
        response.orderId = req.orderId;
        response.productId = req.productId;
        response.sellerId = sellerId;

        // Business error 1: Unknown product
        if (!inventory.hasProduct(req.productId)) {
            response.status = "FAILED";
            response.reason = "Product not in assortment";
            System.out.println("[" + sellerId + "] ✗ Product " + req.productId + 
                             " not in assortment (only have: " + 
                             String.join(", ", inventory.getAllProductNames().keySet()) + ")");
        } 
        // Business error 2: Product "accidentally" unavailable
        else if (ErrorSimulator.getBusinessError() == ErrorSimulator.BusinessError.PRODUCT_UNAVAILABLE) {
            response.status = "FAILED";
            response.reason = "Product temporarily unavailable";
            System.out.println("[" + sellerId + "] ✗ BUSINESS ERROR: Product " + req.productId + 
                             " marked as unavailable (despite stock: " + inventory.getStock(req.productId) + ")");
        }
        // Try normal reservation
        else if (inventory.reserve(req.orderId, req.productId, req.quantity)) {
            response.status = "RESERVED";
            System.out.println("[" + sellerId + "] ✓ Reserved: " + req.quantity + "x " + 
                             inventory.getProductName(req.productId) + " for Order " + req.orderId);
            inventory.printStatus();
        } 
        // Business error 3: Not enough stock
        else {
            response.status = "FAILED";
            response.reason = "Not enough stock";
            System.out.println("[" + sellerId + "] ✗ Not enough stock " +
                             "(Requested: " + req.quantity + ", Available: " + inventory.getStock(req.productId) + ")");
        }
        
        return Messages.toJson(response);
    }
    
    private String handleCancel(Messages.CancelRequest req) {
        Messages.CancelResponse response = new Messages.CancelResponse();
        response.orderId = req.orderId;
        response.productId = req.productId;
        response.sellerId = sellerId;
        
        if (inventory.cancelReservation(req.orderId, req.productId)) {
            response.status = "CANCELLED";
            System.out.println("[" + sellerId + "] ↻ Canceled: Order " + req.orderId);
            inventory.printStatus();
        } else {
            response.status = "FAILED";
            System.out.println("[" + sellerId + "] ✗ Cancellation failed for Order " + req.orderId);
        }
        
        return Messages.toJson(response);
    }
    
    private String handleConfirm(Messages.ConfirmRequest req) {
    inventory.confirmReservation(req.orderId);
    System.out.println("[" + sellerId + "] ✓ Confirmed: Order " + req.orderId);
    // Response as JSON (optional, if Marketplace evaluates this at some point)
    // Here's a simple confirmation object:
    Messages.ReserveResponse response = new Messages.ReserveResponse();
    response.orderId = req.orderId;
    response.productId = req.productId;
    response.sellerId = sellerId;
    response.status = "CONFIRMED";
    return Messages.toJson(response);
}
    
    // Main method
    public static void main(String[] args) {
        String sellerId = "S1";
        int port = 5556;
        String configFile = null;
        
        // Parse arguments
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("--id=")) {
                sellerId = args[i].substring(5);
            } else if (args[i].equals("--id") && i + 1 < args.length) {
                sellerId = args[++i];
            } else if (args[i].startsWith("--port=")) {
                port = Integer.parseInt(args[i].substring(7));
            } else if (args[i].equals("--port") && i + 1 < args.length) {
                port = Integer.parseInt(args[++i]);
            } else if (args[i].startsWith("--config=")) {
                configFile = args[i].substring(9);
            } else if (args[i].equals("--config") && i + 1 < args.length) {
                configFile = args[++i];
            }
        }
        
        // Load config
        if (configFile != null) {
            ConfigLoader.loadConfig(configFile);
        }

        // Start seller
        SellerApp seller = new SellerApp(sellerId, port);
        seller.start();
    }
}