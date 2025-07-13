package main;

import main.messaging.MessageHandler;
import main.messaging.MessageTypes.*;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

/**
 * Einfacher Test-Client zum Testen der Seller
 * Simuliert Marketplace-Anfragen
 */
public class TestClient {
    
    public static void main(String[] args) {
        System.out.println("Test-Client startet...");
        
        try (ZContext context = new ZContext()) {
            // Verbinde zu Seller auf Port 5556
            ZMQ.Socket socket = context.createSocket(SocketType.REQ);
            socket.connect("tcp://localhost:5556");
            
            // Test 1: Reservierung
            System.out.println("\n=== Test 1: Reservierung ===");
            ReserveRequest reserve = new ReserveRequest();
            reserve.orderId = "ORD001";
            reserve.productId = "PS1A";
            reserve.quantity = 2;
            reserve.marketplaceId = "M1";
            
            String request = MessageHandler.toJson(reserve);
            System.out.println("Sende: " + request);
            socket.send(request);
            
            String response = socket.recvStr();
            System.out.println("Empfangen: " + response);
            
            // Test 2: Stornierung
            System.out.println("\n=== Test 2: Stornierung ===");
            CancelRequest cancel = new CancelRequest();
            cancel.orderId = "ORD001";
            cancel.productId = "PS1A";
            cancel.sellerId = "S1";
            
            request = MessageHandler.toJson(cancel);
            System.out.println("Sende: " + request);
            socket.send(request);
            
            response = socket.recvStr();
            System.out.println("Empfangen: " + response);
            
            System.out.println("\nTest abgeschlossen!");
        }
    }
}