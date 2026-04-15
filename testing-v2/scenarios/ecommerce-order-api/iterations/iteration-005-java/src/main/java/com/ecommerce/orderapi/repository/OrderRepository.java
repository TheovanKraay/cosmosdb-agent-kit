package com.ecommerce.orderapi.repository;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.azure.cosmos.util.CosmosPagedIterable;
import com.ecommerce.orderapi.config.CosmosDbConfiguration;
import com.ecommerce.orderapi.model.Order;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

/**
 * Repository for Order CRUD operations against Cosmos DB.
 *
 * Uses parameterized queries (prevents injection, enables plan caching).
 * Partition-key-scoped reads where possible for efficiency.
 */
@Repository
public class OrderRepository {

    private final CosmosDbConfiguration cosmosConfig;

    public OrderRepository(CosmosDbConfiguration cosmosConfig) {
        this.cosmosConfig = cosmosConfig;
    }

    /**
     * Create (upsert) an order. contentResponseOnWriteEnabled returns the created item.
     */
    public Order createOrder(Order order) {
        CosmosContainer container = cosmosConfig.getContainer();
        CosmosItemResponse<Order> response = container.createItem(order,
                new PartitionKey(order.getCustomerId()), new CosmosItemRequestOptions());
        return response.getItem();
    }

    /**
     * Get order by orderId. Since orderId == document id, and we need customerId
     * for partition key, we first do a cross-partition query by id.
     */
    public Order getOrderById(String orderId) {
        CosmosContainer container = cosmosConfig.getContainer();
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.id = @orderId",
                List.of(new SqlParameter("@orderId", orderId))
        );
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        CosmosPagedIterable<Order> results = container.queryItems(query, options, Order.class);
        for (Order order : results) {
            return order;
        }
        return null;
    }

    /**
     * Get all orders for a customer. Partition-key-scoped query (efficient).
     */
    public List<Order> getOrdersByCustomerId(String customerId) {
        CosmosContainer container = cosmosConfig.getContainer();
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.customerId = @customerId ORDER BY c.createdAt DESC",
                List.of(new SqlParameter("@customerId", customerId))
        );
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(new PartitionKey(customerId));
        CosmosPagedIterable<Order> results = container.queryItems(query, options, Order.class);
        List<Order> orders = new ArrayList<>();
        results.forEach(orders::add);
        return orders;
    }

    /**
     * Query orders by status. Cross-partition query (admin operation).
     */
    public List<Order> getOrdersByStatus(String status) {
        CosmosContainer container = cosmosConfig.getContainer();
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.status = @status",
                List.of(new SqlParameter("@status", status))
        );
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        CosmosPagedIterable<Order> results = container.queryItems(query, options, Order.class);
        List<Order> orders = new ArrayList<>();
        results.forEach(orders::add);
        return orders;
    }

    /**
     * Query orders by date range. Cross-partition query (admin operation).
     */
    public List<Order> getOrdersByDateRange(String startDate, String endDate) {
        CosmosContainer container = cosmosConfig.getContainer();
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.createdAt >= @startDate AND c.createdAt <= @endDate",
                List.of(
                        new SqlParameter("@startDate", startDate),
                        new SqlParameter("@endDate", endDate)
                )
        );
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        CosmosPagedIterable<Order> results = container.queryItems(query, options, Order.class);
        List<Order> orders = new ArrayList<>();
        results.forEach(orders::add);
        return orders;
    }

    /**
     * Replace (update) an order. Uses partition key for efficient point write.
     */
    public Order replaceOrder(Order order) {
        CosmosContainer container = cosmosConfig.getContainer();
        CosmosItemResponse<Order> response = container.replaceItem(order, order.getId(),
                new PartitionKey(order.getCustomerId()), new CosmosItemRequestOptions());
        return response.getItem();
    }

    /**
     * Delete an order by id and partition key.
     */
    public void deleteOrder(String orderId, String customerId) {
        CosmosContainer container = cosmosConfig.getContainer();
        container.deleteItem(orderId, new PartitionKey(customerId),
                new CosmosItemRequestOptions());
    }
}
