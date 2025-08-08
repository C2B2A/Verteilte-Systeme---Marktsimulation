package main.seller;

import main.messaging.Messages;
import main.simulation.ConfigLoader;
import main.simulation.ErrorSimulator;
import main.simulation.ErrorSimulator.ErrorType;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import java.util.Map;

/**
 * Seller mit korrekter Produktverteilung
 */
public class SellerApp {
    private final String sellerId;
    private final int port;
    private final SellerInventory inventory;
    private boolean running = true;
    
    public SellerApp(String sellerId, int port) {
        this.sellerId = sellerId;
        this.port = port;
        this.inventory = new SellerInventory();
        
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
                inventory.addProduct("PA", "Produkt A", initialStock);
                inventory.addProduct("PB", "Produkt B", initialStock - 2);
                break;
            case "S2":
                inventory.addProduct("PC", "Produkt C", initialStock);
                inventory.addProduct("PD", "Produkt D", initialStock - 2);
                break;
            case "S3":
                inventory.addProduct("PE", "Produkt E", initialStock);
                inventory.addProduct("PF", "Produkt F", initialStock - 2);
                break;
            case "S4":
                inventory.addProduct("PG", "Produkt G", initialStock);
                inventory.addProduct("PH", "Produkt H", initialStock - 2);
                break;
            case "S5":
                inventory.addProduct("PI", "Produkt I", initialStock);
                inventory.addProduct("PJ", "Produkt J", initialStock - 2);
                break;
            default:
                // Fallback für unbekannte Seller
                System.err.println("Warnung: Unbekannte Seller-ID " + sellerId);
                inventory.addProduct("PX", "Produkt X", initialStock);
                inventory.addProduct("PY", "Produkt Y", initialStock - 2);
        }
    }
    
    public void start() {
        System.out.println("\n╔════════════════════════════════════════════╗");
        System.out.println("║        SELLER " + sellerId + " GESTARTET                 ║");
        System.out.println("╠════════════════════════════════════════════╣");
        System.out.println("║ Port: " + port + "                                  ║");
        System.out.println("║ Produkte:                                  ║");
        for (Map.Entry<String, String> entry : inventory.getAllProductNames().entrySet()) {
            String productId = entry.getKey();
            String name = entry.getValue();
            int stock = inventory.getStock(productId);
            System.out.println("║   - " + productId + " (" + name + "): Bestand " + 
                             String.format("%-2d", stock) + "      ║");
        }
        System.out.println("╚════════════════════════════════════════════╝\n");
        ConfigLoader.printConfig();
        int networkLatencyMs = 50; // Simulierte Latenz (konfigurierbar)
        try (ZContext context = new ZContext()) {
            ZMQ.Socket socket = context.createSocket(SocketType.ROUTER);
            socket.bind("tcp://127.0.0.1:" + port);
            System.out.println("[" + sellerId + "] Bereit für Anfragen auf Port " + port);
            while (running) {
                try {
                    // ROUTER: Empfange Identität und Nachricht
                    byte[] identity = socket.recv(0);
                    String request = socket.recvStr(0);
                    System.out.println("\n[" + sellerId + "] Empfangen von " + new String(identity) + ": " + request);
                    Thread.sleep(networkLatencyMs); // Simuliere Netzwerklatenz
                    ErrorType error = ErrorSimulator.getNextError();
                    System.out.println("[" + sellerId + "] Fehlertyp: " + error);
                    ErrorSimulator.simulateProcessing();
                    String response = "";
                    switch (error) {
                        case SUCCESS:
                            response = processMessage(request);
                            System.out.println("[" + sellerId + "] Gesendet: " + response);
                            break;
                        case FAIL_NO_RESPONSE:
                            System.out.println("[" + sellerId + "] TECHNISCHER FEHLER: Keine Antwort (Timeout)");
                            response = "";
                            break;
                        case FAIL_CRASH:
                            processMessage(request);
                            System.out.println("[" + sellerId + "] TECHNISCHER FEHLER: Crash nach Verarbeitung");
                            response = "";
                            break;
                    }
                    // Sende Antwort (auch leere, damit DEALER nicht blockiert)
                    socket.sendMore(identity);
                    socket.send(response);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    running = false;
                } catch (Exception e) {
                    System.err.println("[" + sellerId + "] Fehler: " + e.getMessage());
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
            // Versuche, orderId und productId trotzdem zu extrahieren
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
        
        // Fachlicher Fehler 1: Unbekanntes Produkt
        if (!inventory.hasProduct(req.productId)) {
            response.status = "FAILED";
            response.reason = "Produkt nicht im Sortiment";
            System.out.println("[" + sellerId + "] ✗ Produkt " + req.productId + 
                             " nicht im Sortiment (habe nur: " + 
                             String.join(", ", inventory.getAllProductNames().keySet()) + ")");
        } 
        // Fachlicher Fehler 2: Produkt "versehentlich" nicht verfügbar
        else if (ErrorSimulator.getBusinessError() == ErrorSimulator.BusinessError.PRODUCT_UNAVAILABLE) {
            response.status = "FAILED";
            response.reason = "Produkt temporär nicht verfügbar";
            System.out.println("[" + sellerId + "] ✗ FACHLICHER FEHLER: Produkt " + req.productId + 
                             " als nicht verfügbar markiert (trotz Bestand: " + inventory.getStock(req.productId) + ")");
        }
        // Normale Reservierung versuchen
        else if (inventory.reserve(req.orderId, req.productId, req.quantity)) {
            response.status = "RESERVED";
            System.out.println("[" + sellerId + "] ✓ Reserviert: " + req.quantity + "x " + 
                             inventory.getProductName(req.productId) + " für Order " + req.orderId);
            inventory.printStatus();
        } 
        // Fachlicher Fehler 3: Nicht genug auf Lager
        else {
            response.status = "FAILED";
            response.reason = "Nicht genug auf Lager";
            System.out.println("[" + sellerId + "] ✗ Nicht genug auf Lager " +
                             "(Angefordert: " + req.quantity + ", Verfügbar: " + inventory.getStock(req.productId) + ")");
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
            System.out.println("[" + sellerId + "] ↻ Storniert: Order " + req.orderId);
            inventory.printStatus();
        } else {
            response.status = "FAILED";
            System.out.println("[" + sellerId + "] ✗ Stornierung fehlgeschlagen für Order " + req.orderId);
        }
        
        return Messages.toJson(response);
    }
    
    private String handleConfirm(Messages.ConfirmRequest req) {
    inventory.confirmReservation(req.orderId);
    System.out.println("[" + sellerId + "] ✓ Bestätigt: Order " + req.orderId);
    // Rückmeldung als JSON (optional, falls Marketplace das irgendwann auswertet)
    // Hier ein einfaches Bestätigungsobjekt:
    Messages.ReserveResponse response = new Messages.ReserveResponse();
    response.orderId = req.orderId;
    response.productId = req.productId;
    response.sellerId = sellerId;
    response.status = "CONFIRMED";
    return Messages.toJson(response);
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