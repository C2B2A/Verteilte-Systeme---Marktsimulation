package main.customer;

import main.messaging.Messages;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// Configuration for customer orders - Allows switching between generated and predefined orders

public class CustomerOrdersConfig {

    // Main switch: true = generate automatically, false = use predefined
    private static final boolean GENERATE_ORDERS = true;

    // Predefined orders - can be adjusted as needed
    private static final List<PredefinedOrder> PREDEFINED_ORDERS = Arrays.asList(
            // Order 1: Simple order from S1
            new PredefinedOrder(
                    Arrays.asList(
                            new Messages.OrderRequest.ProductOrder("PA", 2),
                            new Messages.OrderRequest.ProductOrder("PB", 1))),

            // Order 2: Products from multiple sellers including new products
            new PredefinedOrder(
                    Arrays.asList(
                            new Messages.OrderRequest.ProductOrder("PC", 1),
                            new Messages.OrderRequest.ProductOrder("PE", 2),
                            new Messages.OrderRequest.ProductOrder("PF", 1))),

            // Order 3: Test for failover (PC available at S2 and S3), plus new product
            new PredefinedOrder(
                    Arrays.asList(
                            new Messages.OrderRequest.ProductOrder("PC", 3),
                            new Messages.OrderRequest.ProductOrder("PD", 2),
                            new Messages.OrderRequest.ProductOrder("PG", 1))),

            // Order 4: Large order with new products
            new PredefinedOrder(
                    Arrays.asList(
                            new Messages.OrderRequest.ProductOrder("PA", 5),
                            new Messages.OrderRequest.ProductOrder("PB", 4),
                            new Messages.OrderRequest.ProductOrder("PH", 2))),

            // Order 5: Test for duplicate products (technical error) and new product
            new PredefinedOrder(
                    Arrays.asList(
                            new Messages.OrderRequest.ProductOrder("PE", 2),
                            new Messages.OrderRequest.ProductOrder("PE", 1), // Duplicate!
                            new Messages.OrderRequest.ProductOrder("PD", 1),
                            new Messages.OrderRequest.ProductOrder("PI", 1))),

            // Order 6: Only new products
            new PredefinedOrder(
                    Arrays.asList(
                            new Messages.OrderRequest.ProductOrder("PF", 2),
                            new Messages.OrderRequest.ProductOrder("PG", 1),
                            new Messages.OrderRequest.ProductOrder("PH", 1),
                            new Messages.OrderRequest.ProductOrder("PI", 3),
                            new Messages.OrderRequest.ProductOrder("PJ", 2))),

            // Order 7: Mixture of old and new products
            new PredefinedOrder(
                    Arrays.asList(
                            new Messages.OrderRequest.ProductOrder("PB", 1),
                            new Messages.OrderRequest.ProductOrder("PG", 2),
                            new Messages.OrderRequest.ProductOrder("PJ", 1))));

    // Helper class for predefined orders
    private static class PredefinedOrder {
        final List<Messages.OrderRequest.ProductOrder> products;

        PredefinedOrder(List<Messages.OrderRequest.ProductOrder> products) {
            this.products = products;
        }
    }

    // Index for Round-Robin through predefined orders
    private static int currentOrderIndex = 0;

    // Returns whether orders should be generated

    public static boolean shouldGenerateOrders() {
        return GENERATE_ORDERS;
    }

    // Returns the next predefined order (Round-Robin)

    public static synchronized Messages.OrderRequest getNextPredefinedOrder(String orderId, String customerId) {
        if (PREDEFINED_ORDERS.isEmpty()) {
            throw new IllegalStateException("No predefined orders configured!");
        }

        // Get next order in round-robin fashion
        PredefinedOrder predefined = PREDEFINED_ORDERS.get(currentOrderIndex);
        currentOrderIndex = (currentOrderIndex + 1) % PREDEFINED_ORDERS.size();

        // Create OrderRequest
        Messages.OrderRequest order = new Messages.OrderRequest();
        order.orderId = orderId;
        order.customerId = customerId;
        order.products = new ArrayList<>(predefined.products);

        return order;
    }

    // Returns the number of predefined orders
    public static int getPredefinedOrderCount() {
        return PREDEFINED_ORDERS.size();
    }
}