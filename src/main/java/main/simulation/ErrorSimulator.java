package main.simulation;

import java.util.Random;

// Simulates technical and professional errors based on probabilities
// Uses normal distribution for times
public class ErrorSimulator {
    private static final Random random = new Random();

    // Technical error types
    public enum ErrorType {
        SUCCESS,              // Everything OK
        FAIL_NO_RESPONSE,    // No response (Timeout)
        FAIL_CRASH          // Crash after reception
    }

    // Professional error types
    public enum BusinessError {
        NONE,                    // No error
        PRODUCT_UNAVAILABLE,     // Product "accidentally" unavailable
        UNKNOWN_PRODUCT         // Product ID unknown (checked in SellerApp)
    }

    // Determines which TECHNICAL error type should occur
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

    // Determines whether a BUSINESS error should occur
    // Is only called if everything is technically OK
    public static BusinessError getBusinessError() {
        double rand = random.nextDouble();

        // Check if product should be "accidentally" unavailable
        if (rand < ConfigLoader.getProductNotAvailableProbability()) {
            return BusinessError.PRODUCT_UNAVAILABLE;
        }
        
        return BusinessError.NONE;
    }

    // Simulates processing time (Normal-distributed)
    public static int getProcessingTime() {
        int average = ConfigLoader.getAverageProcessingTime();
        int stdDev = ConfigLoader.getProcessingTimeStdDev();

        // Normally distributed random number
        double gaussian = random.nextGaussian();
        int processingTime = (int) (average + gaussian * stdDev);

        // At least 100ms
        return Math.max(100, processingTime);
    }

    // Helper method: Put thread to sleep for processing simulation
    public static void simulateProcessing() {
        try {
            Thread.sleep(getProcessingTime());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // Debug: Display error statistics
    public static void printErrorStatistics(int total) {
        System.out.println("\n=== Error Statistics (Expectation for " + total + " Requests) ===");
        System.out.println("TECHNICAL ERRORS:");
        System.out.println("  Success: " + (int)(total * ConfigLoader.getSuccessProbability()));
        System.out.println("  Timeout: " + (int)(total * ConfigLoader.getFailWithoutResponseProbability()));
        System.out.println("  Crash: " + (int)(total * ConfigLoader.getFailWithCrashProbability()));
        System.out.println("\nBUSINESS ERRORS (for successful requests):");
        int successfulRequests = (int)(total * ConfigLoader.getSuccessProbability());
        System.out.println("  Product not available: " + 
                         (int)(successfulRequests * ConfigLoader.getProductNotAvailableProbability()));
        System.out.println("==================================================\n");
    }
}