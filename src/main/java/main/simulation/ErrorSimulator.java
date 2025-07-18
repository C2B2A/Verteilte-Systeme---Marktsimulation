package main.simulation;

import java.util.Random;

/**
 * Simuliert technische und fachliche Fehler basierend auf Wahrscheinlichkeiten
 * Verwendet Normal-Verteilung für Zeiten
 */
public class ErrorSimulator {
    private static final Random random = new Random();
    
    // Technische Fehlertypen
    public enum ErrorType {
        SUCCESS,              // Alles OK
        FAIL_NO_RESPONSE,    // Keine Antwort (Timeout)
        FAIL_CRASH          // Crash nach Empfang
    }
    
    // Fachliche Fehlertypen
    public enum BusinessError {
        NONE,                    // Kein Fehler
        PRODUCT_UNAVAILABLE,     // Produkt "versehentlich" nicht verfügbar
        UNKNOWN_PRODUCT         // Produkt-ID nicht bekannt (wird in SellerApp geprüft)
    }
    
    /**
     * Entscheidet welcher TECHNISCHE Fehlertyp auftreten soll
     */
    public static ErrorType getNextError() {
        double rand = random.nextDouble();
        double successProb = ConfigLoader.getSuccessProbability();
        double noResponseProb = ConfigLoader.getFailWithoutResponseProbability();
        
        if (rand < successProb) {
            return ErrorType.SUCCESS;
        } else if (rand < successProb + noResponseProb) {
            return ErrorType.FAIL_NO_RESPONSE;
        } else {
            return ErrorType.FAIL_CRASH;
        }
    }
    
    /**
     * Entscheidet ob ein FACHLICHER Fehler auftreten soll
     * Wird nur aufgerufen wenn technisch alles OK ist
     */
    public static BusinessError getBusinessError() {
        double rand = random.nextDouble();
        
        // Prüfe ob Produkt "versehentlich" nicht verfügbar sein soll
        if (rand < ConfigLoader.getProductNotAvailableProbability()) {
            return BusinessError.PRODUCT_UNAVAILABLE;
        }
        
        return BusinessError.NONE;
    }
    
    /**
     * Simuliert Verarbeitungszeit (Normal-verteilt)
     */
    public static int getProcessingTime() {
        int average = ConfigLoader.getAverageProcessingTime();
        int stdDev = ConfigLoader.getProcessingTimeStdDev();
        
        // Normal-verteilte Zufallszahl
        double gaussian = random.nextGaussian();
        int processingTime = (int) (average + gaussian * stdDev);
        
        // Mindestens 100ms
        return Math.max(100, processingTime);
    }
    
    /**
     * Hilfsmethode: Thread schlafen lassen für Verarbeitungssimulation
     */
    public static void simulateProcessing() {
        try {
            Thread.sleep(getProcessingTime());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Debug: Fehlerstatistik ausgeben
     */
    public static void printErrorStatistics(int total) {
        System.out.println("\n=== Fehlerstatistik (Erwartung bei " + total + " Anfragen) ===");
        System.out.println("TECHNISCHE FEHLER:");
        System.out.println("  Erfolg: " + (int)(total * ConfigLoader.getSuccessProbability()));
        System.out.println("  Timeout: " + (int)(total * ConfigLoader.getFailWithoutResponseProbability()));
        System.out.println("  Crash: " + (int)(total * ConfigLoader.getFailWithCrashProbability()));
        System.out.println("\nFACHLICHE FEHLER (bei erfolgreichen Anfragen):");
        int successfulRequests = (int)(total * ConfigLoader.getSuccessProbability());
        System.out.println("  Produkt nicht verfügbar: " + 
                         (int)(successfulRequests * ConfigLoader.getProductNotAvailableProbability()));
        System.out.println("==================================================\n");
    }
}