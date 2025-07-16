package main;

import main.marketplace.MarketplaceApp;
import main.seller.SellerApp;

/**
 * Hauptklasse zum Starten von Marketplace oder Seller - entscheidet: ruft Seller und Marketplace auf.
 * Verwendung: java -jar <jar> --mode=<marketplace|seller> [weitere optionen]
 */
public class MainLauncher {
    
    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            return;
        }
        
        String mode = null;
        
        // Parse mode
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--mode") && i + 1 < args.length) {
                mode = args[i + 1].toLowerCase();
                break;
            }
        }
        
        if (mode == null) {
            System.err.println("FEHLER: --mode Parameter fehlt!");
            printUsage();
            return;
        }
        
        // Starte entsprechenden Modus
        switch (mode) {
            case "marketplace":
                MarketplaceApp.main(args);
                break;
                
            case "seller":
                SellerApp.main(args);
                break;
                
            case "test":
                TestClient.main(args);
                break;
                
            default:
                System.err.println("FEHLER: Unbekannter Modus: " + mode);
                printUsage();
        }
    }
    
    private static void printUsage() {
        System.out.println("\n=== SCB Marketplace System ===");
        System.out.println("\nVerwendung:");
        System.out.println("  java -jar marktsimulation.jar --mode=<modus> [optionen]");
        System.out.println("\nModi:");
        System.out.println("  marketplace  - Startet einen Marketplace");
        System.out.println("  seller       - Startet einen Seller");
        System.out.println("  test         - Startet Test-Client");
        System.out.println("\nMarketplace Optionen:");
        System.out.println("  --id=<id>              Marketplace ID (default: M1)");
        System.out.println("  --config=<datei>       Konfigurations-Datei");
        System.out.println("\nSeller Optionen:");
        System.out.println("  --id=<id>              Seller ID (default: S1)");
        System.out.println("  --port=<port>          Port-Nummer (default: 5556)");
        System.out.println("  --config=<datei>       Konfigurations-Datei");
        System.out.println("\nBeispiele:");
        System.out.println("  java -jar marktsimulation.jar --mode=seller --id=S1 --port=5556");
        System.out.println("  java -jar marktsimulation.jar --mode=marketplace --id=M1");
        System.out.println();
    }
}