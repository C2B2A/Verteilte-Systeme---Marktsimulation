package main.simulation;

import java.util.Random;

/**
 * Simuliert Fehler basierend auf Wahrscheinlichkeiten
 * Verwendet Normal-Verteilung für Zeiten
 */
public class ErrorSimulator {
    private static final Random random = new Random();
    
    // Fehlertypen
    public enum ErrorType {
        SUCCESS,              // Alles OK
        FAIL_NO_RESPONSE,    // Keine Antwort (Timeout)
        FAIL_CRASH          // Crash nach Empfang
    }
    
    /**
     * Entscheidet welcher Fehlertyp auftreten soll
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
     * Simuliert ob Produkt auf Lager ist (fachlicher Fehler)
     */
    public static boolean isProductAvailable(int currentStock, int requested) {
        // 20% Chance dass "versehentlich" nicht verfügbar (Simulation)
        if (random.nextDouble() < 0.2) {
            return false;
        }
        return currentStock >= requested;
    }
    
    /**
     * Hilfsmethode: Thread schlafen lassen
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
        System.out.println("Erfolg: " + (int)(total * ConfigLoader.getSuccessProbability()));
        System.out.println("Timeout: " + (int)(total * ConfigLoader.getFailWithoutResponseProbability()));
        System.out.println("Crash: " + (int)(total * ConfigLoader.getFailWithCrashProbability()));
    }
}