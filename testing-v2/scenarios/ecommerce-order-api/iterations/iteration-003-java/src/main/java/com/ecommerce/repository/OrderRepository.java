package com.ecommerce.repository;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.azure.cosmos.util.CosmosPagedIterable;
import com.ecommerce.model.Order;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Order documents in Cosmos DB.
 * Uses parameterized queries (best practice) and partition key alignment.
 */
@Repository
public class OrderRepository {

    private final CosmosContainer container;

    public OrderRepository(CosmosContainer container) {
        this.container = container;
    }

    /**
     * Create a new order. Uses partition key (customerId) for efficient writes.
     */
    public Order createOrder(Order order) {
        CosmosItemResponse<Order> response = container.createItem(
                order,
                new PartitionKey(order.getCustomerId()),
                new CosmosItemRequestOptions());
        return response.getItem();
    }

    /**
     * Get order by ID. Since we don't know the customerId, we query by orderId.
     * This is a cross-partition query, but is acceptable for single-item lookups.
     */
    public Optional<Order> findById(String orderId) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.orderId = @orderId",
                List.of(new SqlParameter("@orderId", orderId)));

        CosmosPagedIterable<Order> results = container.queryItems(
                query, new CosmosQueryRequestOptions(), Order.class);

        return results.stream().findFirst();
    }

    /**
     * Get all orders for a customer. Single-partition query (efficient).
     */
    public List<Order> findByCustomerId(String customerId) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.customerId = @customerId",
                List.of(new SqlParameter("@customerId", customerId)));

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(new PartitionKey(customerId));

        CosmosPagedIterable<Order> results = container.queryItems(
                query, options, Order.class);

        List<Order> orders = new ArrayList<>();
        results.forEach(orders::add);
        return orders;
    }

    /**
     * Query orders by status. Cross-partition query (necessary for admin queries).
     */
    public List<Order> findByStatus(String status) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.status = @status",
                List.of(new SqlParameter("@status", status)));

        CosmosPagedIterable<Order> results = container.queryItems(
                query, new CosmosQueryRequestOptions(), Order.class);

        List<Order> orders = new ArrayList<>();
        results.forEach(orders::add);
        return orders;
    }

    /**
     * Query orders by date range. Cross-partition query (necessary for admin queries).
     */
    public List<Order> findByDateRange(String startDate, String endDate) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.createdAt >= @startDate AND c.createdAt <= @endDate",
                List.of(
                        new SqlParameter("@startDate", startDate),
                        new SqlParameter("@endDate", endDate)));

        CosmosPagedIterable<Order> results = container.queryItems(
                query, new CosmosQueryRequestOptions(), Order.class);

        List<Order> orders = new ArrayList<>();
        results.forEach(orders::add);
        return orders;
    }

    /**
     * Replace (update) an existing order. Uses partition key for efficient write.
     */
    public Order replaceOrder(Order order) {
        CosmosItemResponse<Order> response = container.replaceItem(
                order,
                order.getId(),
                new PartitionKey(order.getCustomerId()),
                new CosmosItemRequestOptions());
        return response.getItem();
    }

    /**
     * Delete an order by ID and partition key.
     */
    public void deleteOrder(String id, String customerId) {
        container.deleteItem(id, new PartitionKey(customerId), new CosmosItemRequestOptions());
    }
}
