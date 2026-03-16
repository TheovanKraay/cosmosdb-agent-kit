package com.example.ecommerce.repository;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.azure.cosmos.util.CosmosPagedIterable;
import com.example.ecommerce.model.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

/**
 * Data access layer for Order documents in Cosmos DB.
 *
 * Design decisions (AGENTS.md):
 * - Rule 2.6: Queries by customerId use partition key for efficiency
 * - Rule 3.5: All queries use parameterized SQL (no string interpolation)
 * - Rule 3.1: Customer queries target single partition; status/date queries are
 *             cross-partition (acceptable for admin queries, unavoidable given PK)
 * - Rule 4.7: ETags used for optimistic concurrency on status updates
 */
@Repository
public class OrderRepository {

    private static final Logger logger = LoggerFactory.getLogger(OrderRepository.class);

    private final CosmosContainer container;

    public OrderRepository(CosmosContainer container) {
        this.container = container;
    }

    /**
     * Save a new order document.
     * Returns the created order (contentResponseOnWriteEnabled must be true).
     */
    public Order save(Order order) {
        CosmosItemResponse<Order> response = container.createItem(
                order,
                new PartitionKey(order.getCustomerId()),
                new CosmosItemRequestOptions()
        );
        logger.debug("Created order {}, RU={}", order.getId(), response.getRequestCharge());
        return response.getItem();
    }

    /**
     * Find order by ID using cross-partition query.
     * Since we partition by customerId, lookups by orderId require cross-partition.
     * AGENTS.md rule 3.1: Cross-partition is acceptable for this infrequent admin operation.
     */
    public Order findById(String orderId) {
        // Parameterized query (AGENTS.md 3.5)
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.id = @orderId",
                new SqlParameter("@orderId", orderId)
        );

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        // Cross-partition query needed since we don't know customerId
        CosmosPagedIterable<Order> iterable = container.queryItems(query, options, Order.class);

        for (Order order : iterable) {
            return order;
        }
        return null;
    }

    /**
     * Find all orders for a customer — single partition query (AGENTS.md 3.1).
     * Most common access pattern; uses partition key for efficiency.
     */
    public List<Order> findByCustomerId(String customerId) {
        // Parameterized query targeting a single partition (AGENTS.md 3.5, 3.1)
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.customerId = @customerId",
                new SqlParameter("@customerId", customerId)
        );

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions()
                .setPartitionKey(new PartitionKey(customerId));  // Single-partition!

        List<Order> orders = new ArrayList<>();
        container.queryItems(query, options, Order.class).forEach(orders::add);
        logger.debug("Found {} orders for customer {}", orders.size(), customerId);
        return orders;
    }

    /**
     * Find orders by status (cross-partition admin query).
     * Parameterized to prevent injection (AGENTS.md 3.5).
     */
    public List<Order> findByStatus(String status) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.status = @status",
                new SqlParameter("@status", status)
        );

        List<Order> orders = new ArrayList<>();
        container.queryItems(query, new CosmosQueryRequestOptions(), Order.class)
                .forEach(orders::add);
        return orders;
    }

    /**
     * Find orders within a date range (cross-partition admin query).
     * Uses ISO-8601 string comparison (lexicographic order equals chronological order).
     * AGENTS.md rule 3.5: parameterized queries.
     */
    public List<Order> findByDateRange(String startDate, String endDate) {
        // Append time components to cover the full day boundary
        String startTs = startDate + "T00:00:00Z";
        String endTs = endDate + "T23:59:59Z";

        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.createdAt >= @startDate AND c.createdAt <= @endDate",
                new SqlParameter("@startDate", startTs),
                new SqlParameter("@endDate", endTs)
        );

        List<Order> orders = new ArrayList<>();
        container.queryItems(query, new CosmosQueryRequestOptions(), Order.class)
                .forEach(orders::add);
        return orders;
    }

    /**
     * Update an order's status using ETag-based optimistic concurrency.
     * AGENTS.md rule 4.7: ETags prevent lost updates on concurrent status changes.
     *
     * Steps:
     * 1. Cross-partition query to find the order and get customerId
     * 2. Point read (using customerId as partition key) to obtain ETag
     * 3. Upsert with IfMatchEtag to fail if document changed since read
     *
     * @param orderId     the order to update
     * @param newStatus   the new status value
     * @return the updated Order, or null if not found
     * @throws CosmosException with 412 if ETag conflict
     */
    public Order updateStatus(String orderId, String newStatus) {
        // Step 1: Find the order to get customerId (cross-partition query)
        Order order = findById(orderId);
        if (order == null) {
            return null;
        }

        final String customerId = order.getCustomerId();

        // Step 2: Point read using partition key to get the ETag (AGENTS.md 4.7)
        CosmosItemResponse<Order> readResponse = container.readItem(
                orderId,
                new PartitionKey(customerId),
                Order.class
        );
        Order current = readResponse.getItem();
        String etag = readResponse.getETag();

        // Step 3: Apply the status change
        current.setStatus(newStatus);

        // Step 4: Upsert with ETag condition — fails with HTTP 412 if document
        // was modified by a concurrent request between our read and write (AGENTS.md 4.7)
        CosmosItemRequestOptions options = new CosmosItemRequestOptions();
        options.setIfMatchETag(etag);

        CosmosItemResponse<Order> response = container.upsertItem(
                current,
                new PartitionKey(customerId),
                options
        );

        logger.debug("Updated order {} status to {}, RU={}", orderId, newStatus, response.getRequestCharge());
        return response.getItem();
    }

    /**
     * Delete an order by ID. Requires the order to be fetched first to get the partition key.
     *
     * @return true if deleted, false if not found
     */
    public boolean delete(String orderId) {
        Order order = findById(orderId);
        if (order == null) {
            return false;
        }

        try {
            container.deleteItem(
                    order.getId(),
                    new PartitionKey(order.getCustomerId()),
                    new CosmosItemRequestOptions()
            );
            logger.debug("Deleted order {}", orderId);
            return true;
        } catch (CosmosException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND.value()) {
                return false;
            }
            throw e;
        }
    }
}
