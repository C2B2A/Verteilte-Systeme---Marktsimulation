package main;

import main.marketplace.MarketplaceApp;
import main.seller.SellerApp;
import main.customer.CustomerApp;

/**
 * Hauptklasse zum Starten von Marketplace, Seller oder Customer
 * Verwendung: java -jar <jar> --mode=<marketplace|seller|customer> [weitere optionen]
 */
public class MainLauncher {
    
    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            return;
        }
        
        String mode = null;
        
        // Parse mode aus verschiedenen Formaten
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("--mode=")) {
                // Format: --mode=marketplace
                mode = args[i].substring(7).toLowerCase();
                break;
            } else if (args[i].equals("--mode") && i + 1 < args.length) {
                // Format: --mode marketplace
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
                
            case "customer":
                CustomerApp.main(args);
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
        System.out.println("  customer     - Startet einen Customer");
        System.out.println("\nMarketplace Optionen:");
        System.out.println("  --id=<id>              Marketplace ID (default: M1)");
        System.out.println("  --config=<datei>       Konfigurations-Datei");
        System.out.println("\nSeller Optionen:");
        System.out.println("  --id=<id>              Seller ID (default: S1)");
        System.out.println("  --port=<port>          Port-Nummer (default: 5556)");
        System.out.println("  --config=<datei>       Konfigurations-Datei");
        System.out.println("\nCustomer Optionen:");
        System.out.println("  --id=<id>              Customer ID (default: C1)");
        System.out.println("  --config=<datei>       Konfigurations-Datei");
        System.out.println("\nBeispiele:");
        System.out.println("  java -jar marktsimulation.jar --mode=seller --id=S1 --port=5556");
        System.out.println("  java -jar marktsimulation.jar --mode=marketplace --id=M1");
        System.out.println("  java -jar marktsimulation.jar --mode=customer --id=C1");
        System.out.println("\nKorrekte Produktverteilung:");
        System.out.println("  Seller S1: PA, PB");
        System.out.println("  Seller S2: PC, PD");
        System.out.println("  Seller S3: PC, PE");
        System.out.println("  Seller S4: PD, PE");
        System.out.println("  Seller S5: PF, PB");
        System.out.println();
    }
}