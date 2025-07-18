package main.seller;

import main.messaging.MessageFormat;
import main.messaging.MessageHandler;
import main.messaging.MessageTypes.*;
import main.simulation.ConfigLoader;
import main.simulation.ErrorSimulator;
import main.simulation.ErrorSimulator.ErrorType;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

/**
 * Seller mit korrekter Produktverteilung laut Anforderung
 * S1: A,B | S2: C,D | S3: C,E | S4: D,E | S5: F,B
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
        
        // Initialisiere Produkte gemäß Anforderung
        initializeProducts();
    }
    
    /**
     * Initialisiert die korrekten Produkte für jeden Seller
     */
    private void initializeProducts() {
        int initialStock = ConfigLoader.getInitialStock();
        
        switch (sellerId) {
            case "S1":
                inventory.addProduct(new Product("PA", "Produkt A", initialStock));
                inventory.addProduct(new Product("PB", "Produkt B", initialStock - 2));
                break;
            case "S2":
                inventory.addProduct(new Product("PC", "Produkt C", initialStock));
                inventory.addProduct(new Product("PD", "Produkt D", initialStock - 2));
                break;
            case "S3":
                inventory.addProduct(new Product("PC", "Produkt C", initialStock));
                inventory.addProduct(new Product("PE", "Produkt E", initialStock - 2));
                break;
            case "S4":
                inventory.addProduct(new Product("PD", "Produkt D", initialStock));
                inventory.addProduct(new Product("PE", "Produkt E", initialStock - 2));
                break;
            case "S5":
                inventory.addProduct(new Product("PF", "Produkt F", initialStock));
                inventory.addProduct(new Product("PB", "Produkt B", initialStock - 2));
                break;
            default:
                // Fallback für unbekannte Seller
                System.err.println("Warnung: Unbekannte Seller-ID " + sellerId);
                inventory.addProduct(new Product("PX", "Produkt X", initialStock));
                inventory.addProduct(new Product("PY", "Produkt Y", initialStock - 2));
        }
    }
    
    public void start() {
        System.out.println("\n╔════════════════════════════════════════════╗");
        System.out.println("║        SELLER " + sellerId + " GESTARTET                 ║");
        System.out.println("╠════════════════════════════════════════════╣");
        System.out.println("║ Port: " + port + "                                  ║");
        System.out.println("║ Produkte:                                  ║");
        
        // Zeige alle Produkte des Sellers
        for (Product p : inventory.getAllProducts().values()) {
            System.out.println("║   - " + p.getProductId() + " (" + p.getName() + "): Bestand " + 
                             String.format("%-2d", p.getStock()) + "      ║");
        }
        
        System.out.println("╚════════════════════════════════════════════╝\n");
        
        ConfigLoader.printConfig();
        
        try (ZContext context = new ZContext()) {
            ZMQ.Socket socket = context.createSocket(SocketType.REP);
            socket.bind("tcp://127.0.0.1:" + port);
            
            System.out.println("[" + sellerId + "] Bereit für Anfragen auf Port " + port);
            
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
        
        // Fachlicher Fehler 1: Unbekanntes Produkt
        if (product == null) {
            response.status = "FAILED";
            response.reason = "Produkt nicht im Sortiment";
            System.out.println("[" + sellerId + "] ✗ Produkt " + req.productId + 
                             " nicht im Sortiment (habe nur: " + 
                             String.join(", ", inventory.getAllProducts().keySet()) + ")");
        } 
        // Fachlicher Fehler 2: Produkt "versehentlich" nicht verfügbar
        else if (ErrorSimulator.getBusinessError() == ErrorSimulator.BusinessError.PRODUCT_UNAVAILABLE) {
            response.status = "FAILED";
            response.reason = "Produkt temporär nicht verfügbar";
            System.out.println("[" + sellerId + "] ✗ FACHLICHER FEHLER: Produkt " + req.productId + 
                             " als nicht verfügbar markiert (trotz Bestand: " + product.getStock() + ")");
        }
        // Normale Reservierung versuchen
        else if (inventory.reserve(req.orderId, req.productId, req.quantity)) {
            response.status = "RESERVED";
            System.out.println("[" + sellerId + "] ✓ Reserviert: " + req.quantity + "x " + 
                             product.getName() + " für Order " + req.orderId);
            inventory.printStatus();
        } 
        // Fachlicher Fehler 3: Nicht genug auf Lager
        else {
            response.status = "FAILED";
            response.reason = "Nicht genug auf Lager";
            System.out.println("[" + sellerId + "] ✗ Nicht genug auf Lager " +
                             "(Angefordert: " + req.quantity + ", Verfügbar: " + product.getStock() + ")");
        }
        
        return MessageFormat.format(response);
    }
    
    private String handleCancel(CancelRequest req) {
        CancelResponse response = new CancelResponse();
        response.orderId = req.orderId;
        response.productId = req.productId;
        response.sellerId = sellerId;
        
        if (inventory.cancelReservation(req.orderId, req.productId)) {
            response.status = "CANCELLED";
            System.out.println("[" + sellerId + "] ↻ Storniert: Order " + req.orderId);
            inventory.printStatus();
        } else {
            response.status = "FAILED";
            System.out.println("[" + sellerId + "] ✗ Stornierung fehlgeschlagen für Order " + req.orderId);
        }
        
        return MessageHandler.toJson(response);
    }
    
    private String handleConfirm(ConfirmRequest req) {
        inventory.confirmReservation(req.orderId);
        System.out.println("[" + sellerId + "] ✓ Bestätigt: Order " + req.orderId);
        return "CONFIRMED|" + req.orderId + "|" + req.productId + "|" + req.sellerId;
    }
    
    // Main-Methode
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
        
        // Config laden
        if (configFile != null) {
            ConfigLoader.loadConfig(configFile);
        }
        
        // Seller starten
        SellerApp seller = new SellerApp(sellerId, port);
        seller.start();
    }
}