package main.simulation;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Lädt Konfiguration aus Properties-Datei
 * Einfache statische Methoden für Zugriff
 */
public class ConfigLoader {
    private static Properties properties = new Properties();
    private static boolean loaded = false;
    
    // Standard-Werte falls keine Config geladen wird
    private static final Properties defaults = new Properties();
    static {
        // Fehlerwahrscheinlichkeiten
        defaults.setProperty("simulation.probability.success", "0.7");
        defaults.setProperty("simulation.probability.fail_without_response", "0.15");
        defaults.setProperty("simulation.probability.fail_with_crash", "0.15");
        
        // Zeiten
        defaults.setProperty("simulation.processing_time.average", "500");
        defaults.setProperty("simulation.processing_time.std_dev", "150");
        
        // System
        defaults.setProperty("marketplace.order.delay", "2000");
        defaults.setProperty("marketplace.timeout", "5000");
        defaults.setProperty("seller.initial_stock", "5");
    }
    
    // Config laden
    public static void loadConfig(String filename) {
        try (InputStream input = new FileInputStream(filename)) {
            properties.load(input);
            loaded = true;
            System.out.println("Konfiguration geladen aus: " + filename);
        } catch (IOException e) {
            System.err.println("Konnte Config nicht laden: " + filename);
            System.err.println("Verwende Standard-Werte");
            properties = new Properties(defaults);
        }
    }
    
    // Wenn keine Config geladen wurde, Standard verwenden
    private static void ensureLoaded() {
        if (!loaded) {
            properties = new Properties(defaults);
            loaded = true;
        }
    }
    
    // Getter-Methoden
    public static double getSuccessProbability() {
        ensureLoaded();
        return Double.parseDouble(properties.getProperty("simulation.probability.success"));
    }
    
    public static double getFailWithoutResponseProbability() {
        ensureLoaded();
        return Double.parseDouble(properties.getProperty("simulation.probability.fail_without_response"));
    }
    
    public static double getFailWithCrashProbability() {
        ensureLoaded();
        return Double.parseDouble(properties.getProperty("simulation.probability.fail_with_crash"));
    }
    
    public static int getAverageProcessingTime() {
        ensureLoaded();
        return Integer.parseInt(properties.getProperty("simulation.processing_time.average"));
    }
    
    public static int getProcessingTimeStdDev() {
        ensureLoaded();
        return Integer.parseInt(properties.getProperty("simulation.processing_time.std_dev"));
    }
    
    public static int getOrderDelay() {
        ensureLoaded();
        return Integer.parseInt(properties.getProperty("marketplace.order.delay"));
    }
    
    public static int getTimeout() {
        ensureLoaded();
        return Integer.parseInt(properties.getProperty("marketplace.timeout"));
    }
    
    public static int getInitialStock() {
        ensureLoaded();
        return Integer.parseInt(properties.getProperty("seller.initial_stock"));
    }
    
    /**
     * Wahrscheinlichkeit dass ein Produkt "versehentlich" nicht verfügbar ist
     * (Fachlicher Fehler - Simulation)
     */
    public static double getProductNotAvailableProbability() {
        ensureLoaded();
        return Double.parseDouble(properties.getProperty(
            "simulation.probability.product_not_available", "0.2"));
    }
    
    /**
     * Wahrscheinlichkeit für doppelte Produktanforderung in Bestellung
     * (Fachlicher Fehler - Simulation)
     */
    public static double getDuplicateProductProbability() {
        ensureLoaded();
        return Double.parseDouble(properties.getProperty(
            "simulation.probability.duplicate_product", "0.1"));
    }
    
    // Config ausgeben (erweitert)
    public static void printConfig() {
        ensureLoaded();
        System.out.println("\n=== Aktuelle Konfiguration ===");
        System.out.println("TECHNISCHE FEHLER:");
        System.out.println("  Erfolg: " + (getSuccessProbability() * 100) + "%");
        System.out.println("  Timeout: " + (getFailWithoutResponseProbability() * 100) + "%");
        System.out.println("  Crash: " + (getFailWithCrashProbability() * 100) + "%");
        System.out.println("\nFACHLICHE FEHLER:");
        System.out.println("  Produkt nicht verfügbar: " + (getProductNotAvailableProbability() * 100) + "%");
        System.out.println("  Doppelte Produktanforderung: " + (getDuplicateProductProbability() * 100) + "%");
        System.out.println("\nZEITEN:");
        System.out.println("  Bestellverzögerung: " + getOrderDelay() + "ms");
        System.out.println("  Verarbeitungszeit: " + getAverageProcessingTime() + "ms (±" + getProcessingTimeStdDev() + "ms)");
        System.out.println("  Timeout: " + getTimeout() + "ms");
        System.out.println("\nLAGER:");
        System.out.println("  Initial-Bestand: " + getInitialStock());
        System.out.println("==============================\n");
    }
}