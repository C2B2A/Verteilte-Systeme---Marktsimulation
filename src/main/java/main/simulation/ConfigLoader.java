package main.simulation;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

// Loads configuration from properties file
// Simple static methods for access
public class ConfigLoader {
    private static Properties properties;
    private static boolean loaded = false;

    // Default values if no config is loaded
    private static final Properties defaults = new Properties();
    static {
        // Error probabilities
        defaults.setProperty("simulation.probability.success", "0.7");
        defaults.setProperty("simulation.probability.fail_without_response", "0.15");
        defaults.setProperty("simulation.probability.fail_with_crash", "0.15");

        // Times
        defaults.setProperty("simulation.processing_time.average", "500");
        defaults.setProperty("simulation.processing_time.std_dev", "150");

        // System
        defaults.setProperty("marketplace.order.delay", "2000");
        defaults.setProperty("marketplace.timeout", "3000");
        defaults.setProperty("seller.initial_stock", "5");

        // Properties with defaults initialize
        properties = new Properties(defaults);
    }

    // Load config
    public static void loadConfig(String filename) {
        try (InputStream input = new FileInputStream(filename)) {
            // Load config.properties (overrides defaults)
            properties.load(input);
            loaded = true;
            System.out.println("Configuration loaded from: " + filename);
            // Debug output of loaded properties
            // System.out.println("Loaded properties: " + properties);
        } catch (IOException e) {
            System.err.println("Could not load config: " + filename);
            System.err.println("Error: " + e.getMessage());
            System.err.println("Using default values");
            properties = new Properties(defaults);
            loaded = true; // Set loaded to true so ensureLoaded() doesn't reload defaults
        }
    }

    // If no config is loaded, use defaults
    private static void ensureLoaded() {
        if (!loaded) {
            // Try to automatically load config.properties
            try (InputStream input = new FileInputStream("config/config.properties")) {
                properties.load(input);
                loaded = true;
                System.out.println("Configuration automatically loaded from config/config.properties.");
                //System.out.println("Loaded properties: " + properties);
            } catch (IOException e) {
                System.out.println("Could not find config/config.properties, using default values");
                properties = new Properties(defaults);
                loaded = true;
            }
        }
    }

    // Getter methods
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

    // Probability that a product is "accidentally" unavailable
    // (business error - simulation)
    public static double getProductNotAvailableProbability() {
        ensureLoaded();
        return Double.parseDouble(properties.getProperty(
                "simulation.probability.product_not_available", "0.2"));
    }

    // Probability of duplicate product requests in purchase orders
    // (business error - simulation)
    public static double getDuplicateProductProbability() {
        ensureLoaded();
        return Double.parseDouble(properties.getProperty(
                "simulation.probability.duplicate_product", "0.1"));
    }

    // Print config (extended)
    public static void printConfig() {
        ensureLoaded();
        System.out.println("\n=== Current Configuration ===");
        System.out.println("TECHNICAL ERRORS:");
        System.out.println("  Success: " + (getSuccessProbability() * 100) + "%");
        System.out.println("  Timeout: " + (getFailWithoutResponseProbability() * 100) + "%");
        System.out.println("  Crash: " + (getFailWithCrashProbability() * 100) + "%");
        System.out.println("\nBUSINESS ERRORS:");
        System.out.println("  Product not available: " + (getProductNotAvailableProbability() * 100) + "%");
        System.out.println("  Duplicate product requests: " + (getDuplicateProductProbability() * 100) + "%");
        System.out.println("\nTIMES:");
        System.out.println("  Order delay: " + getOrderDelay() + "ms");
        System.out.println(
                "  Processing time: " + getAverageProcessingTime() + "ms (±" + getProcessingTimeStdDev() + "ms)");
        System.out.println("  Timeout: " + getTimeout() + "ms");
        System.out.println("\nSTOCK:");
        System.out.println("  Initial stock: " + getInitialStock());
        System.out.println("==============================\n");
    }
}