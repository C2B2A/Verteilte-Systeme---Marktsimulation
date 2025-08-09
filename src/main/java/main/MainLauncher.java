package main;

import main.marketplace.MarketplaceApp;
import main.seller.SellerApp;
import main.customer.CustomerApp;

// Main class for starting Marketplace, Seller, or Customer
// Usage: java -jar <jar> --mode=<marketplace|seller|customer> [more options]
public class MainLauncher {
    
    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            return;
        }
        
        String mode = null;

        // Parse mode from different formats
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
            System.err.println("ERROR: --mode parameter is missing!");
            printUsage();
            return;
        }

        // Start corresponding mode
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
                System.err.println("ERROR: Unknown mode: " + mode);
                printUsage();
        }
    }
    
    private static void printUsage() {
        System.out.println("\n=== SCB Marketplace System ===");
        System.out.println("\nUsage:");
        System.out.println("  java -jar marktsimulation.jar --mode=<mode> [options]");
        System.out.println("\nModes:");
        System.out.println("  marketplace  - Start a Marketplace");
        System.out.println("  seller       - Start a Seller");
        System.out.println("  customer     - Start a Customer");
        System.out.println("\nMarketplace Options:");
        System.out.println("  --id=<id>              Marketplace ID (default: M1)");
        System.out.println("  --config=<datei>       Configuration file");
        System.out.println("\nSeller Options:");
        System.out.println("  --id=<id>              Seller ID (default: S1)");
        System.out.println("  --port=<port>          Port number (default: 5556)");
        System.out.println("  --config=<datei>       Configuration file");
        System.out.println("\nCustomer Options:");
        System.out.println("  --id=<id>              Customer ID (default: C1)");
        System.out.println("  --config=<datei>       Configuration file");
        System.out.println("\nExamples:");
        System.out.println("  java -jar marktsimulation.jar --mode=seller --id=S1 --port=5556");
        System.out.println("  java -jar marktsimulation.jar --mode=marketplace --id=M1");
        System.out.println("  java -jar marktsimulation.jar --mode=customer --id=C1");
        System.out.println("\nCorrect Product Distribution:");
        System.out.println("  Seller S1: PA, PB");
        System.out.println("  Seller S2: PC, PD");
        System.out.println("  Seller S3: PC, PE");
        System.out.println("  Seller S4: PD, PE");
        System.out.println("  Seller S5: PF, PB");
        System.out.println();
    }
}