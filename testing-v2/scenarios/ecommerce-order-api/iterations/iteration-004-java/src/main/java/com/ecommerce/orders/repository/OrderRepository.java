package com.ecommerce.orders.repository;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.CosmosPatchOperations;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.azure.cosmos.util.CosmosPagedIterable;
import com.ecommerce.orders.config.CosmosConfig;
import com.ecommerce.orders.model.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Order operations against Cosmos DB.
 * Uses parameterized queries, point reads where possible,
 * and partition-scoped queries for customer operations.
 */
@Repository
public class OrderRepository {

    private static final Logger log = LoggerFactory.getLogger(OrderRepository.class);
    private final CosmosConfig cosmosConfig;

    public OrderRepository(CosmosConfig cosmosConfig) {
        this.cosmosConfig = cosmosConfig;
    }

    private CosmosContainer container() {
        return cosmosConfig.getContainer();
    }

    /**
     * Create a new order. Uses the customerId as partition key.
     */
    public Order createOrder(Order order) {
        CosmosItemResponse<Order> response = container().createItem(
                order,
                new PartitionKey(order.getCustomerId()),
                new CosmosItemRequestOptions()
        );
        log.debug("createOrder RU: {}", response.getRequestCharge());
        return response.getItem();
    }

    /**
     * Get order by orderId using cross-partition query.
     * The API contract does not include customerId in the path,
     * so we must do a cross-partition query to find the order by ID.
     */
    public Optional<Order> getOrderById(String orderId) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.id = @orderId AND c.type = 'order'",
                Arrays.asList(new SqlParameter("@orderId", orderId))
        );

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        CosmosPagedIterable<Order> results = container().queryItems(query, options, Order.class);

        for (Order order : results) {
            log.debug("getOrderById found order: {}", orderId);
            return Optional.of(order);
        }
        return Optional.empty();
    }

    /**
     * Get all orders for a customer. Single-partition query — efficient.
     */
    public List<Order> getOrdersByCustomer(String customerId) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.customerId = @customerId AND c.type = 'order' ORDER BY c.createdAt DESC",
                Arrays.asList(new SqlParameter("@customerId", customerId))
        );

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(new PartitionKey(customerId));

        List<Order> orders = new ArrayList<>();
        CosmosPagedIterable<Order> results = container().queryItems(query, options, Order.class);
        results.forEach(orders::add);

        log.debug("getOrdersByCustomer {} returned {} orders", customerId, orders.size());
        return orders;
    }

    /**
     * Query orders by status. Cross-partition query (admin operation).
     */
    public List<Order> getOrdersByStatus(String status) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.status = @status AND c.type = 'order' ORDER BY c.createdAt DESC",
                Arrays.asList(new SqlParameter("@status", status))
        );

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();

        List<Order> orders = new ArrayList<>();
        CosmosPagedIterable<Order> results = container().queryItems(query, options, Order.class);
        results.forEach(orders::add);

        log.debug("getOrdersByStatus {} returned {} orders", status, orders.size());
        return orders;
    }

    /**
     * Query orders by date range. Cross-partition query (admin operation).
     * Uses ISO-8601 string comparison which works correctly since dates are stored in ISO format.
     */
    public List<Order> getOrdersByDateRange(String startDate, String endDate) {
        // Ensure the endDate covers the entire day by appending T23:59:59Z if it's a date-only string
        String effectiveEndDate = endDate;
        if (endDate != null && endDate.length() == 10) {
            effectiveEndDate = endDate + "T23:59:59.999Z";
        }
        String effectiveStartDate = startDate;
        if (startDate != null && startDate.length() == 10) {
            effectiveStartDate = startDate + "T00:00:00.000Z";
        }

        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.createdAt >= @startDate AND c.createdAt <= @endDate AND c.type = 'order' ORDER BY c.createdAt DESC",
                Arrays.asList(
                        new SqlParameter("@startDate", effectiveStartDate),
                        new SqlParameter("@endDate", effectiveEndDate)
                )
        );

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();

        List<Order> orders = new ArrayList<>();
        CosmosPagedIterable<Order> results = container().queryItems(query, options, Order.class);
        results.forEach(orders::add);

        log.debug("getOrdersByDateRange {}-{} returned {} orders", startDate, endDate, orders.size());
        return orders;
    }

    /**
     * Update order status using Patch API for atomic update.
     */
    public Order updateOrderStatus(String orderId, String customerId, String newStatus) {
        CosmosPatchOperations patch = CosmosPatchOperations.create()
                .set("/status", newStatus)
                .set("/updatedAt", Instant.now().toString());

        CosmosItemResponse<Order> response = container().patchItem(
                orderId,
                new PartitionKey(customerId),
                patch,
                Order.class
        );

        log.debug("updateOrderStatus RU: {}", response.getRequestCharge());
        return response.getItem();
    }

    /**
     * Delete an order by its orderId and customerId.
     */
    public void deleteOrder(String orderId, String customerId) {
        CosmosItemResponse<Object> response = container().deleteItem(
                orderId,
                new PartitionKey(customerId),
                new CosmosItemRequestOptions()
        );
        log.debug("deleteOrder RU: {}", response.getRequestCharge());
    }
}
