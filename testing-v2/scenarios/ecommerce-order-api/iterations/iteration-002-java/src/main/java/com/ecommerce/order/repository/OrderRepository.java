package com.ecommerce.order.repository;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.ecommerce.order.config.CosmosDbConfiguration;
import com.ecommerce.order.model.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Repository for Order documents in Cosmos DB.
 * Uses parameterized queries and point reads where possible.
 * Partition key: /customerId
 */
@Repository
public class OrderRepository {

    private static final Logger logger = LoggerFactory.getLogger(OrderRepository.class);
    private final CosmosDbConfiguration cosmosConfig;

    public OrderRepository(CosmosDbConfiguration cosmosConfig) {
        this.cosmosConfig = cosmosConfig;
    }

    /**
     * Create a new order. Uses point write with partition key.
     */
    public Order createOrder(Order order) {
        CosmosContainer container = cosmosConfig.getContainer();
        CosmosItemResponse<Order> response = container.createItem(
            order,
            new PartitionKey(order.getCustomerId()),
            new CosmosItemRequestOptions()
        );
        return response.getItem();
    }

    /**
     * Get order by ID. Since we don't know the customerId for a given orderId,
     * we must do a cross-partition query. This is acceptable for single-item
     * lookups by a unique ID.
     */
    public Order getOrderById(String orderId) {
        CosmosContainer container = cosmosConfig.getContainer();
        SqlQuerySpec query = new SqlQuerySpec(
            "SELECT * FROM c WHERE c.orderId = @orderId",
            Collections.singletonList(new SqlParameter("@orderId", orderId))
        );
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        List<Order> results = container.queryItems(query, options, Order.class)
            .stream().toList();
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * Get all orders for a customer. Uses partition key for efficient single-partition query.
     */
    public List<Order> getOrdersByCustomerId(String customerId) {
        CosmosContainer container = cosmosConfig.getContainer();
        SqlQuerySpec query = new SqlQuerySpec(
            "SELECT * FROM c WHERE c.customerId = @customerId",
            Collections.singletonList(new SqlParameter("@customerId", customerId))
        );
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(new PartitionKey(customerId));
        return container.queryItems(query, options, Order.class)
            .stream().toList();
    }

    /**
     * Query orders by status. Cross-partition query since status spans customers.
     */
    public List<Order> getOrdersByStatus(String status) {
        CosmosContainer container = cosmosConfig.getContainer();
        SqlQuerySpec query = new SqlQuerySpec(
            "SELECT * FROM c WHERE c.status = @status",
            Collections.singletonList(new SqlParameter("@status", status))
        );
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        return container.queryItems(query, options, Order.class)
            .stream().toList();
    }

    /**
     * Query orders by date range. Cross-partition query.
     * createdAt is stored as ISO-8601 string which sorts lexicographically.
     */
    public List<Order> getOrdersByDateRange(String startDate, String endDate) {
        CosmosContainer container = cosmosConfig.getContainer();
        List<SqlParameter> params = new ArrayList<>();
        params.add(new SqlParameter("@startDate", startDate));
        params.add(new SqlParameter("@endDate", endDate));
        SqlQuerySpec query = new SqlQuerySpec(
            "SELECT * FROM c WHERE c.createdAt >= @startDate AND c.createdAt <= @endDate",
            params
        );
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        return container.queryItems(query, options, Order.class)
            .stream().toList();
    }

    /**
     * Replace (update) an order document. Uses point write with partition key.
     */
    public Order replaceOrder(Order order) {
        CosmosContainer container = cosmosConfig.getContainer();
        CosmosItemResponse<Order> response = container.replaceItem(
            order,
            order.getId(),
            new PartitionKey(order.getCustomerId()),
            new CosmosItemRequestOptions()
        );
        return response.getItem();
    }

    /**
     * Delete an order by ID and customerId. Uses point delete with partition key.
     */
    public void deleteOrder(String id, String customerId) {
        CosmosContainer container = cosmosConfig.getContainer();
        container.deleteItem(id, new PartitionKey(customerId), new CosmosItemRequestOptions());
    }
}
