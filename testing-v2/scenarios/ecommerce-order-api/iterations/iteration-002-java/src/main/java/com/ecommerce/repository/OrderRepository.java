package com.ecommerce.repository;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.ecommerce.model.Order;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data access layer for Order documents in Cosmos DB.
 *
 * Uses the synchronous Cosmos SDK client. The ObjectMapper is configured to
 * ignore unknown properties (e.g. Cosmos system fields _rid, _self, _etag, _ts)
 * so that deserialization does not fail.
 */
@Repository
public class OrderRepository {

    private static final Logger log = LoggerFactory.getLogger(OrderRepository.class);

    private final CosmosContainer container;

    // Configured to ignore Cosmos system fields (_rid, _self, _etag, _ts)
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public OrderRepository(CosmosContainer container) {
        this.container = container;
    }

    // ---------------------------------------------------------------
    // Create
    // ---------------------------------------------------------------

    public Order save(Order order) {
        ObjectNode node = mapper.valueToTree(order);
        container.createItem(node, new PartitionKey(order.getCustomerId()), new CosmosItemRequestOptions());
        return order;
    }

    // ---------------------------------------------------------------
    // Read by ID
    // ---------------------------------------------------------------

    public Optional<Order> findById(String orderId, String customerId) {
        try {
            ObjectNode node = container.readItem(orderId, new PartitionKey(customerId), ObjectNode.class).getItem();
            return Optional.of(mapper.treeToValue(node, Order.class));
        } catch (com.azure.cosmos.CosmosException e) {
            if (e.getStatusCode() == 404) {
                return Optional.empty();
            }
            throw new RuntimeException("Error reading order: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException("Error reading order: " + e.getMessage(), e);
        }
    }

    /**
     * Find an order by orderId when the customerId (partition key) is not known.
     * Uses a cross-partition query filtered by orderId.
     */
    public Optional<Order> findByOrderId(String orderId) {
        String query = "SELECT * FROM c WHERE c.orderId = @orderId";
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setQueryMetricsEnabled(false);

        List<ObjectNode> results = container.queryItems(
                new com.azure.cosmos.models.SqlQuerySpec(query,
                        List.of(new com.azure.cosmos.models.SqlParameter("@orderId", orderId))),
                options,
                ObjectNode.class
        ).stream().toList();

        if (results.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(mapper.treeToValue(results.get(0), Order.class));
        } catch (Exception e) {
            throw new RuntimeException("Error deserializing order: " + e.getMessage(), e);
        }
    }

    // ---------------------------------------------------------------
    // Update
    // ---------------------------------------------------------------

    public Order update(Order order) {
        ObjectNode node = mapper.valueToTree(order);
        container.upsertItem(node, new PartitionKey(order.getCustomerId()), new CosmosItemRequestOptions());
        return order;
    }

    // ---------------------------------------------------------------
    // Delete
    // ---------------------------------------------------------------

    public void delete(String orderId, String customerId) {
        container.deleteItem(orderId, new PartitionKey(customerId), new CosmosItemRequestOptions());
    }

    // ---------------------------------------------------------------
    // Query by customer (partition-scoped — efficient)
    // ---------------------------------------------------------------

    public List<Order> findByCustomerId(String customerId) {
        String query = "SELECT * FROM c WHERE c.customerId = @customerId ORDER BY c.createdAt DESC";
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(new PartitionKey(customerId));
        options.setQueryMetricsEnabled(false);

        return queryOrders(query,
                List.of(new com.azure.cosmos.models.SqlParameter("@customerId", customerId)),
                options);
    }

    // ---------------------------------------------------------------
    // Query by status (cross-partition)
    // ---------------------------------------------------------------

    public List<Order> findByStatus(String status) {
        String query = "SELECT * FROM c WHERE c.status = @status ORDER BY c.createdAt DESC";
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setQueryMetricsEnabled(false);

        return queryOrders(query,
                List.of(new com.azure.cosmos.models.SqlParameter("@status", status)),
                options);
    }

    // ---------------------------------------------------------------
    // Query by date range (cross-partition)
    // ---------------------------------------------------------------

    public List<Order> findByDateRange(String startDate, String endDate) {
        String query = "SELECT * FROM c WHERE c.createdAt >= @startDate AND c.createdAt <= @endDate ORDER BY c.createdAt DESC";
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setQueryMetricsEnabled(false);

        return queryOrders(query,
                List.of(
                        new com.azure.cosmos.models.SqlParameter("@startDate", startDate),
                        new com.azure.cosmos.models.SqlParameter("@endDate", endDate)
                ),
                options);
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private List<Order> queryOrders(String query,
                                     List<com.azure.cosmos.models.SqlParameter> params,
                                     CosmosQueryRequestOptions options) {
        List<ObjectNode> nodes = container.queryItems(
                new com.azure.cosmos.models.SqlQuerySpec(query, params),
                options,
                ObjectNode.class
        ).stream().toList();

        List<Order> orders = new ArrayList<>();
        for (ObjectNode node : nodes) {
            try {
                orders.add(mapper.treeToValue(node, Order.class));
            } catch (Exception e) {
                log.warn("Failed to deserialize order document: {}", e.getMessage());
            }
        }
        return orders;
    }
}
