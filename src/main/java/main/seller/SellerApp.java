package main.seller;

import main.messaging.MessageHandler;
import main.messaging.MessageTypes.*;
import main.simulation.ConfigLoader;
import main.simulation.ErrorSimulator;
import main.simulation.ErrorSimulator.ErrorType;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

/**
 * Vollständiger Seller mit Inventory und Fehlersimulation
 * Start: java -jar <jar> --mode=seller --id=S1 --port=5556
 */
public class SellerApp {
    private final String sellerId;
    private final int port;
    private final Inventory inventory;
    private boolean running = true;
    
    public SellerApp(String sellerId, int port) {
        this.sellerId = sellerId;
        this.port = port;
        this.inventory = new Inventory();
        
        // Initialisiere 2 Produkte pro Seller
        int initialStock = ConfigLoader.getInitialStock();
        inventory.addProduct(new Product("P" + sellerId + "A", 
                           "Produkt A von Seller " + sellerId, initialStock));
        inventory.addProduct(new Product("P" + sellerId + "B", 
                           "Produkt B von Seller " + sellerId, initialStock - 2));
    }
    
    public void start() {
        System.out.println("=== Seller " + sellerId + " startet auf Port " + port + " ===");
        inventory.printStatus();
        
        try (ZContext context = new ZContext()) {
            ZMQ.Socket socket = context.createSocket(SocketType.REP);
            socket.bind("tcp://127.0.0.1:" + port);
            
            while (running) {
                try {
                    // Nachricht empfangen
                    String request = socket.recvStr();
                    System.out.println("\n[" + sellerId + "] Empfangen: " + request);
                    
                    // Fehlersimulation
                    ErrorType error = ErrorSimulator.getNextError();
                    System.out.println("[" + sellerId + "] Fehlertyp: " + error);
                    
                    // Verarbeitung simulieren
                    ErrorSimulator.simulateProcessing();
                    
                    switch (error) {
                        case SUCCESS:
                            // Normal verarbeiten und antworten
                            String response = processMessage(request);
                            socket.send(response);
                            System.out.println("[" + sellerId + "] Gesendet: " + response);
                            break;
                            
                        case FAIL_NO_RESPONSE:
                            // Keine Antwort senden (Timeout beim Marketplace)
                            System.out.println("[" + sellerId + "] FEHLER: Keine Antwort (Timeout)");
                            // Socket muss trotzdem antworten in ZeroMQ REQ/REP
                            socket.send("");
                            break;
                            
                        case FAIL_CRASH:
                            // Nachricht verarbeiten aber nicht antworten
                            processMessage(request); // Verarbeitet!
                            System.out.println("[" + sellerId + "] FEHLER: Crash nach Verarbeitung");
                            socket.send(""); // Leere Antwort
                            break;
                    }
                    
                } catch (Exception e) {
                    System.err.println("[" + sellerId + "] Fehler: " + e.getMessage());
                    running = false;
                }
            }
        }
    }
    
    private String processMessage(String json) {
        String messageType = MessageHandler.getMessageType(json);
        
        switch (messageType) {
            case "ReserveRequest":
                ReserveRequest req = MessageHandler.fromJson(json, ReserveRequest.class);
                return handleReserve(req);
                
            case "CancelRequest":
                CancelRequest cancel = MessageHandler.fromJson(json, CancelRequest.class);
                return handleCancel(cancel);
                
            case "ConfirmRequest":
                ConfirmRequest confirm = MessageHandler.fromJson(json, ConfirmRequest.class);
                return handleConfirm(confirm);
                
            default:
                return "{\"error\":\"Unknown message type\"}";
        }
    }
    
    private String handleReserve(ReserveRequest req) {
        ReserveResponse response = new ReserveResponse();
        response.orderId = req.orderId;
        response.productId = req.productId;
        response.sellerId = sellerId;
        
        Product product = inventory.getProduct(req.productId);
        if (product == null) {
            response.status = "FAILED";
            response.reason = "Produkt nicht bekannt";
        } else if (inventory.reserve(req.orderId, req.productId, req.quantity)) {
            response.status = "RESERVED";
            System.out.println("[" + sellerId + "] Reserviert: " + req.quantity + "x " + 
                             product.getName() + " für Order " + req.orderId);
            inventory.printStatus();
        } else {
            response.status = "FAILED";
            response.reason = "Nicht genug auf Lager";
        }
        
        return MessageHandler.toJson(response);
    }
    
    private String handleCancel(CancelRequest req) {
        CancelResponse response = new CancelResponse();
        response.orderId = req.orderId;
        response.productId = req.productId;
        response.sellerId = sellerId;
        
        if (inventory.cancelReservation(req.orderId, req.productId)) {
            response.status = "CANCELLED";
            System.out.println("[" + sellerId + "] Storniert: Order " + req.orderId);
            inventory.printStatus();
        } else {
            response.status = "FAILED";
        }
        
        return MessageHandler.toJson(response);
    }
    
    private String handleConfirm(ConfirmRequest req) {
        inventory.confirmReservation(req.orderId);
        System.out.println("[" + sellerId + "] Bestätigt: Order " + req.orderId);
        return "{\"status\":\"CONFIRMED\"}";
    }
    
    // Main-Methode
    public static void main(String[] args) {
        String sellerId = "S1";
        int port = 5556;
        String configFile = null;
        
        // Parse arguments
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--id") && i + 1 < args.length) {
                sellerId = args[++i];
            } else if (args[i].equals("--port") && i + 1 < args.length) {
                port = Integer.parseInt(args[++i]);
            } else if (args[i].equals("--config") && i + 1 < args.length) {
                configFile = args[++i];
            }
        }
        
        // Config laden
        if (configFile != null) {
            ConfigLoader.loadConfig(configFile);
        }
        ConfigLoader.printConfig();
        
        // Seller starten
        SellerApp seller = new SellerApp(sellerId, port);
        seller.start();
    }
}