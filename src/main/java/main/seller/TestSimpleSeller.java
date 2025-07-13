package main.seller;

import main.messaging.MessageHandler;
import main.messaging.MessageTypes.*;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

/**
 * Einfacher Seller zum Testen der Kommunikation
 * Startet mit: java -cp <jar> main.seller.SimpleSeller <sellerId> <port>
 */
public class TestSimpleSeller {
    private final String sellerId;
    private final int port;
    private final Product product1;
    private final Product product2;
    
    public TestSimpleSeller(String sellerId, int port) {
        this.sellerId = sellerId;
        this.port = port;
        
        // Jeder Seller hat 2 Produkte (laut Anforderung)
        this.product1 = new Product("P" + sellerId + "A", "Produkt A von Seller " + sellerId, 5);
        this.product2 = new Product("P" + sellerId + "B", "Produkt B von Seller " + sellerId, 3);
    }
    
    public void start() {
        System.out.println("Seller " + sellerId + " startet auf Port " + port);
        System.out.println("Produkte: " + product1.getName() + " (Bestand: " + product1.getStock() + ")");
        System.out.println("         " + product2.getName() + " (Bestand: " + product2.getStock() + ")");
        
        try (ZContext context = new ZContext()) {
            ZMQ.Socket socket = context.createSocket(SocketType.REP);
            socket.bind("tcp://127.0.0.1:" + port);
            
            while (!Thread.currentThread().isInterrupted()) {
                // Nachricht empfangen
                String request = socket.recvStr();
                System.out.println("\nEmpfangen: " + request);
                
                // Verarbeiten
                String response = processMessage(request);
                
                // Antworten
                socket.send(response);
                System.out.println("Gesendet: " + response);
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
                // Bestätigung - nichts weiter zu tun
                return "{\"status\":\"CONFIRMED\"}";
                
            default:
                return "{\"error\":\"Unknown message type\"}";
        }
    }
    
    private String handleReserve(ReserveRequest req) {
        Product product = getProduct(req.productId);
        ReserveResponse response = new ReserveResponse();
        response.orderId = req.orderId;
        response.productId = req.productId;
        response.sellerId = sellerId;
        
        if (product == null) {
            response.status = "FAILED";
            response.reason = "Produkt nicht bekannt";
        } else if (product.reserve(req.quantity)) {
            response.status = "RESERVED";
            System.out.println("Reserviert: " + req.quantity + "x " + product.getName() + 
                             " (Neuer Bestand: " + product.getStock() + ")");
        } else {
            response.status = "FAILED";
            response.reason = "Nicht genug auf Lager";
        }
        
        return MessageHandler.toJson(response);
    }
    
    private String handleCancel(CancelRequest req) {
        Product product = getProduct(req.productId);
        CancelResponse response = new CancelResponse();
        response.orderId = req.orderId;
        response.productId = req.productId;
        response.sellerId = sellerId;
        
        if (product != null) {
            // Vereinfacht: Wir geben immer 1 zurück (müsste eigentlich gespeichert werden)
            product.release(1);
            response.status = "CANCELLED";
            System.out.println("Storniert: " + product.getName() + 
                             " (Neuer Bestand: " + product.getStock() + ")");
        } else {
            response.status = "FAILED";
        }
        
        return MessageHandler.toJson(response);
    }
    
    private Product getProduct(String productId) {
        if (productId.equals(product1.getProductId())) return product1;
        if (productId.equals(product2.getProductId())) return product2;
        return null;
    }
    
    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Usage: SimpleSeller <sellerId> <port>");
            System.out.println("Example: SimpleSeller S1 5556");
            return;
        }
        
        String sellerId = args[0];
        int port = Integer.parseInt(args[1]);
        
        TestSimpleSeller seller = new TestSimpleSeller(sellerId, port);
        seller.start();
    }
}