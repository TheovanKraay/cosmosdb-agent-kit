package com.ecommerce.order.repository;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.azure.cosmos.util.CosmosPagedIterable;
import com.ecommerce.order.config.CosmosDbConfiguration;
import com.ecommerce.order.model.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Order CRUD operations against Cosmos DB.
 * Uses parameterized queries (Rule 3.6).
 * Uses point reads where possible (Rule 3.7).
 * Partition key: customerId.
 */
@Repository
public class OrderRepository {

    private static final Logger logger = LoggerFactory.getLogger(OrderRepository.class);

    private final CosmosDbConfiguration cosmosConfig;

    public OrderRepository(CosmosDbConfiguration cosmosConfig) {
        this.cosmosConfig = cosmosConfig;
    }

    /**
     * Create a new order. Uses partition key (customerId) for efficient write.
     */
    public Order createOrder(Order order) {
        CosmosContainer container = cosmosConfig.getContainer();
        CosmosItemResponse<Order> response = container.createItem(
                order,
                new PartitionKey(order.getCustomerId()),
                new CosmosItemRequestOptions());
        return response.getItem();
    }

    /**
     * Get order by ID. Requires cross-partition query since orderId != partition key.
     * We query by id field with orderId mapped to Cosmos id.
     */
    public Optional<Order> getOrderById(String orderId) {
        CosmosContainer container = cosmosConfig.getContainer();
        SqlQuerySpec querySpec = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.orderId = @orderId",
                Arrays.asList(new SqlParameter("@orderId", orderId)));
        CosmosPagedIterable<Order> results = container.queryItems(
                querySpec, new CosmosQueryRequestOptions(), Order.class);

        for (Order order : results) {
            return Optional.of(order);
        }
        return Optional.empty();
    }

    /**
     * Get all orders for a customer. Efficient single-partition query (customerId is PK).
     */
    public List<Order> getOrdersByCustomerId(String customerId) {
        CosmosContainer container = cosmosConfig.getContainer();
        SqlQuerySpec querySpec = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.customerId = @customerId ORDER BY c.createdAt DESC",
                Arrays.asList(new SqlParameter("@customerId", customerId)));
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(new PartitionKey(customerId));

        CosmosPagedIterable<Order> results = container.queryItems(
                querySpec, options, Order.class);

        List<Order> orders = new ArrayList<>();
        results.forEach(orders::add);
        return orders;
    }

    /**
     * Query orders by status. Cross-partition query (status not aligned with PK).
     */
    public List<Order> getOrdersByStatus(String status) {
        CosmosContainer container = cosmosConfig.getContainer();
        SqlQuerySpec querySpec = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.status = @status",
                Arrays.asList(new SqlParameter("@status", status)));

        CosmosPagedIterable<Order> results = container.queryItems(
                querySpec, new CosmosQueryRequestOptions(), Order.class);

        List<Order> orders = new ArrayList<>();
        results.forEach(orders::add);
        return orders;
    }

    /**
     * Query orders by date range. Cross-partition query.
     */
    public List<Order> getOrdersByDateRange(String startDate, String endDate) {
        CosmosContainer container = cosmosConfig.getContainer();
        SqlQuerySpec querySpec = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.createdAt >= @startDate AND c.createdAt <= @endDate",
                Arrays.asList(
                        new SqlParameter("@startDate", startDate),
                        new SqlParameter("@endDate", endDate)));

        CosmosPagedIterable<Order> results = container.queryItems(
                querySpec, new CosmosQueryRequestOptions(), Order.class);

        List<Order> orders = new ArrayList<>();
        results.forEach(orders::add);
        return orders;
    }

    /**
     * Replace (update) an order. Uses partition key for efficient write.
     */
    public Order replaceOrder(Order order) {
        CosmosContainer container = cosmosConfig.getContainer();
        CosmosItemResponse<Order> response = container.replaceItem(
                order,
                order.getId(),
                new PartitionKey(order.getCustomerId()),
                new CosmosItemRequestOptions());
        return response.getItem();
    }

    /**
     * Delete an order by ID. Requires knowing customerId (partition key).
     */
    public void deleteOrder(String id, String customerId) {
        CosmosContainer container = cosmosConfig.getContainer();
        container.deleteItem(id, new PartitionKey(customerId), new CosmosItemRequestOptions());
    }
}
