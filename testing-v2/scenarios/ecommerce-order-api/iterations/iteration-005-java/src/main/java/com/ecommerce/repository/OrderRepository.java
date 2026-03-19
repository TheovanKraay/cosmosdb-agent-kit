package com.ecommerce.repository;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.models.*;
import com.azure.cosmos.util.CosmosPagedIterable;
import com.ecommerce.model.Order;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
public class OrderRepository {

    private final CosmosContainer container;
    private final ObjectMapper objectMapper;

    public OrderRepository(CosmosContainer container, ObjectMapper objectMapper) {
        this.container = container;
        this.objectMapper = objectMapper;
    }

    public Order createOrder(Order order) {
        container.createItem(order, new PartitionKey(order.getCustomerId()), new CosmosItemRequestOptions());
        return order;
    }

    public Order findById(String orderId) {
        String query = "SELECT * FROM c WHERE c.orderId = @orderId";
        SqlQuerySpec querySpec = new SqlQuerySpec(query,
                List.of(new SqlParameter("@orderId", orderId)));

        CosmosPagedIterable<JsonNode> results = container.queryItems(
                querySpec, new CosmosQueryRequestOptions(), JsonNode.class);

        for (JsonNode node : results) {
            return objectMapper.convertValue(node, Order.class);
        }
        return null;
    }

    public List<Order> findByCustomerId(String customerId) {
        String query = "SELECT * FROM c WHERE c.customerId = @customerId";
        SqlQuerySpec querySpec = new SqlQuerySpec(query,
                List.of(new SqlParameter("@customerId", customerId)));

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(new PartitionKey(customerId));

        CosmosPagedIterable<JsonNode> results = container.queryItems(
                querySpec, options, JsonNode.class);

        List<Order> orders = new ArrayList<>();
        for (JsonNode node : results) {
            orders.add(objectMapper.convertValue(node, Order.class));
        }
        return orders;
    }

    public List<Order> findByStatus(String status) {
        String query = "SELECT * FROM c WHERE c.status = @status";
        SqlQuerySpec querySpec = new SqlQuerySpec(query,
                List.of(new SqlParameter("@status", status)));

        CosmosPagedIterable<JsonNode> results = container.queryItems(
                querySpec, new CosmosQueryRequestOptions(), JsonNode.class);

        List<Order> orders = new ArrayList<>();
        for (JsonNode node : results) {
            orders.add(objectMapper.convertValue(node, Order.class));
        }
        return orders;
    }

    public List<Order> findByDateRange(String startDate, String endDate) {
        String query = "SELECT * FROM c WHERE c.createdAt >= @startDate AND c.createdAt <= @endDate";
        SqlQuerySpec querySpec = new SqlQuerySpec(query,
                List.of(new SqlParameter("@startDate", startDate),
                        new SqlParameter("@endDate", endDate)));

        CosmosPagedIterable<JsonNode> results = container.queryItems(
                querySpec, new CosmosQueryRequestOptions(), JsonNode.class);

        List<Order> orders = new ArrayList<>();
        for (JsonNode node : results) {
            orders.add(objectMapper.convertValue(node, Order.class));
        }
        return orders;
    }

    public Order updateOrder(Order order) {
        container.upsertItem(order, new PartitionKey(order.getCustomerId()), new CosmosItemRequestOptions());
        return order;
    }

    public void deleteOrder(String id, String customerId) {
        container.deleteItem(id, new PartitionKey(customerId), new CosmosItemRequestOptions());
    }
}
