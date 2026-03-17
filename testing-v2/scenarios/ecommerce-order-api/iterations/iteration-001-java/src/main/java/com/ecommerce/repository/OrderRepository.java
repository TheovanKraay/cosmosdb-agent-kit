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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Repository for Order documents in Cosmos DB.
 *
 * Partition key strategy: /customerId (Rule 2.6).
 * - Customer queries: single-partition (efficient)
 * - Status/date queries: cross-partition (unavoidable given access patterns)
 * - Get by orderId: cross-partition query (orderId != partition key)
 *
 * Uses parameterized queries to prevent injection (Rule 3.5).
 * Projects only needed fields in list queries (Rule 3.7).
 */
@Repository
public class OrderRepository {

    private static final Logger logger = LoggerFactory.getLogger(OrderRepository.class);

    private final CosmosContainer container;

    public OrderRepository(CosmosContainer ordersContainer) {
        this.container = ordersContainer;
    }

    /**
     * Create a new order document in Cosmos DB.
     * contentResponseOnWriteEnabled(true) set on client ensures getItem() is non-null.
     */
    public Order createOrder(Order order) {
        CosmosItemResponse<Order> response = container.createItem(
                order,
                new PartitionKey(order.getCustomerId()),
                new CosmosItemRequestOptions()
        );
        Order created = response.getItem();
        // Capture ETag for future optimistic concurrency (Rule 4.7)
        if (created != null) {
            created.setEtag(response.getETag());
        }
        return created;
    }

    /**
     * Find an order by its orderId (cross-partition query).
     * Partition key is customerId; finding by orderId requires cross-partition scan.
     * Parameterized query to prevent injection (Rule 3.5).
     */
    public Order findByOrderId(String orderId) {
        // Cross-partition query: SELECT * FROM c WHERE c.id = @orderId
        // Use point-read semantics for full document (items embedded, Rule 1.3)
        SqlQuerySpec querySpec = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.id = @orderId",
                Arrays.asList(new SqlParameter("@orderId", orderId))
        );

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setMaxDegreeOfParallelism(-1); // Allow cross-partition parallel query

        CosmosPagedIterable<Order> results = container.queryItems(querySpec, options, Order.class);
        for (Order order : results) {
            return order; // Return first match (orderId is unique)
        }
        return null;
    }

    /**
     * Find all orders for a customer (single-partition query - efficient).
     * Projects only fields needed by the API response (Rule 3.7).
     * Parameterized query (Rule 3.5).
     */
    public List<Order> findByCustomerId(String customerId) {
        // Single-partition query: customerId IS the partition key
        SqlQuerySpec querySpec = new SqlQuerySpec(
                "SELECT c.id, c.orderId, c.customerId, c.status, c.items, c.total, c.createdAt " +
                "FROM c WHERE c.customerId = @customerId " +
                "ORDER BY c.createdAt DESC",
                Arrays.asList(new SqlParameter("@customerId", customerId))
        );

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(new PartitionKey(customerId)); // Single-partition query

        List<Order> orders = new ArrayList<>();
        container.queryItems(querySpec, options, Order.class).forEach(orders::add);
        return orders;
    }

    /**
     * Find all orders by status (cross-partition query).
     * Status is low-cardinality so must be cross-partition (Rule 3.1 - minimize when possible).
     * Parameterized query (Rule 3.5). Consistent enum values stored/queried (Rule 4.17).
     */
    public List<Order> findByStatus(String status) {
        SqlQuerySpec querySpec = new SqlQuerySpec(
                "SELECT c.id, c.orderId, c.customerId, c.status, c.total, c.createdAt " +
                "FROM c WHERE c.status = @status",
                Arrays.asList(new SqlParameter("@status", status))
        );

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setMaxDegreeOfParallelism(-1);

        List<Order> orders = new ArrayList<>();
        container.queryItems(querySpec, options, Order.class).forEach(orders::add);
        return orders;
    }

    /**
     * Find orders within a date range (cross-partition query).
     * Parameterized query (Rule 3.5). Filters ordered by selectivity (Rule 3.3).
     */
    public List<Order> findByDateRange(String startDate, String endDate) {
        // ISO-8601 string comparison works correctly for date range filtering
        SqlQuerySpec querySpec = new SqlQuerySpec(
                "SELECT c.id, c.orderId, c.customerId, c.status, c.total, c.createdAt " +
                "FROM c WHERE c.createdAt >= @startDate AND c.createdAt <= @endDate " +
                "ORDER BY c.createdAt DESC",
                Arrays.asList(
                        new SqlParameter("@startDate", startDate),
                        new SqlParameter("@endDate", endDate)
                )
        );

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setMaxDegreeOfParallelism(-1);

        List<Order> orders = new ArrayList<>();
        container.queryItems(querySpec, options, Order.class).forEach(orders::add);
        return orders;
    }

    /**
     * Update an order document using ETag for optimistic concurrency (Rule 4.7).
     * Uses upsert with if-match ETag to prevent lost updates on concurrent status changes.
     */
    public Order updateOrder(Order order) {
        CosmosItemRequestOptions options = new CosmosItemRequestOptions();
        if (order.getEtag() != null && !order.getEtag().isEmpty()) {
            options.setIfMatchETag(order.getEtag()); // Optimistic concurrency (Rule 4.7)
        }

        CosmosItemResponse<Order> response = container.replaceItem(
                order,
                order.getId(),
                new PartitionKey(order.getCustomerId()),
                options
        );
        Order updated = response.getItem();
        if (updated != null) {
            updated.setEtag(response.getETag());
        }
        return updated;
    }

    /**
     * Delete an order by orderId and customerId (partition key required for delete).
     */
    public void deleteOrder(String orderId, String customerId) {
        container.deleteItem(orderId, new PartitionKey(customerId), new CosmosItemRequestOptions());
    }

}
