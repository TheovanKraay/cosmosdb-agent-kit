package com.ecommerce.repository;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.util.CosmosPagedIterable;
import com.ecommerce.model.Order;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
public class OrderRepository {

    private final CosmosContainer container;
    private final ObjectMapper objectMapper;

    public OrderRepository(CosmosDatabase cosmosDatabase) {
        this.container = cosmosDatabase.getContainer("orders");
        this.objectMapper = new ObjectMapper();
    }

    public Order save(Order order) {
        ObjectNode doc = objectMapper.valueToTree(order);
        container.createItem(doc);
        return order;
    }

    public Order update(Order order) {
        ObjectNode doc = objectMapper.valueToTree(order);
        container.replaceItem(doc, order.getId(), new PartitionKey(order.getCustomerId()),
                new CosmosItemRequestOptions());
        return order;
    }

    public Order findById(String orderId) {
        String query = "SELECT * FROM c WHERE c.orderId = @orderId";
        CosmosPagedIterable<ObjectNode> results = container.queryItems(
                new com.azure.cosmos.models.SqlQuerySpec(query,
                        List.of(new com.azure.cosmos.models.SqlParameter("@orderId", orderId))),
                new CosmosQueryRequestOptions(),
                ObjectNode.class);

        for (ObjectNode item : results) {
            try {
                return objectMapper.treeToValue(item, Order.class);
            } catch (Exception e) {
                throw new RuntimeException("Failed to deserialize order", e);
            }
        }
        return null;
    }

    public List<Order> findByCustomerId(String customerId) {
        String query = "SELECT * FROM c WHERE c.customerId = @customerId";
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(new PartitionKey(customerId));

        CosmosPagedIterable<ObjectNode> results = container.queryItems(
                new com.azure.cosmos.models.SqlQuerySpec(query,
                        List.of(new com.azure.cosmos.models.SqlParameter("@customerId", customerId))),
                options,
                ObjectNode.class);

        List<Order> orders = new ArrayList<>();
        for (ObjectNode item : results) {
            try {
                orders.add(objectMapper.treeToValue(item, Order.class));
            } catch (Exception e) {
                throw new RuntimeException("Failed to deserialize order", e);
            }
        }
        return orders;
    }

    public List<Order> findByStatus(String status) {
        String query = "SELECT * FROM c WHERE c.status = @status";
        CosmosPagedIterable<ObjectNode> results = container.queryItems(
                new com.azure.cosmos.models.SqlQuerySpec(query,
                        List.of(new com.azure.cosmos.models.SqlParameter("@status", status))),
                new CosmosQueryRequestOptions(),
                ObjectNode.class);

        List<Order> orders = new ArrayList<>();
        for (ObjectNode item : results) {
            try {
                orders.add(objectMapper.treeToValue(item, Order.class));
            } catch (Exception e) {
                throw new RuntimeException("Failed to deserialize order", e);
            }
        }
        return orders;
    }

    public List<Order> findByDateRange(String startDate, String endDate) {
        String query = "SELECT * FROM c WHERE c.createdAt >= @startDate AND c.createdAt <= @endDate";
        CosmosPagedIterable<ObjectNode> results = container.queryItems(
                new com.azure.cosmos.models.SqlQuerySpec(query,
                        List.of(
                                new com.azure.cosmos.models.SqlParameter("@startDate", startDate),
                                new com.azure.cosmos.models.SqlParameter("@endDate", endDate + "T23:59:59Z")
                        )),
                new CosmosQueryRequestOptions(),
                ObjectNode.class);

        List<Order> orders = new ArrayList<>();
        for (ObjectNode item : results) {
            try {
                orders.add(objectMapper.treeToValue(item, Order.class));
            } catch (Exception e) {
                throw new RuntimeException("Failed to deserialize order", e);
            }
        }
        return orders;
    }

    public void delete(Order order) {
        container.deleteItem(order.getId(), new PartitionKey(order.getCustomerId()),
                new CosmosItemRequestOptions());
    }
}
