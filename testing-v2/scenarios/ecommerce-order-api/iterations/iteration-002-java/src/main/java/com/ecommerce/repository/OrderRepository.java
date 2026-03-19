package com.ecommerce.repository;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.azure.cosmos.util.CosmosPagedIterable;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ecommerce.model.Order;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Repository
public class OrderRepository {

    private final CosmosContainer container;
    private final ObjectMapper objectMapper;

    public OrderRepository(CosmosContainer container) {
        this.container = container;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(
                com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public Order createOrder(Order order) {
        container.createItem(order, new PartitionKey(order.getCustomerId()), new CosmosItemRequestOptions());
        return order;
    }

    public Order getOrderById(String orderId) {
        String query = "SELECT * FROM c WHERE c.orderId = @orderId";
        SqlQuerySpec querySpec = new SqlQuerySpec(query,
                Arrays.asList(new SqlParameter("@orderId", orderId)));
        CosmosPagedIterable<JsonNode> results = container.queryItems(querySpec,
                new CosmosQueryRequestOptions(), JsonNode.class);
        for (JsonNode node : results) {
            return objectMapper.convertValue(node, Order.class);
        }
        return null;
    }

    public List<Order> getOrdersByCustomerId(String customerId) {
        String query = "SELECT * FROM c WHERE c.customerId = @customerId";
        SqlQuerySpec querySpec = new SqlQuerySpec(query,
                Arrays.asList(new SqlParameter("@customerId", customerId)));
        CosmosPagedIterable<JsonNode> results = container.queryItems(querySpec,
                new CosmosQueryRequestOptions(), JsonNode.class);
        List<Order> orders = new ArrayList<>();
        for (JsonNode node : results) {
            orders.add(objectMapper.convertValue(node, Order.class));
        }
        return orders;
    }

    public List<Order> getOrdersByStatus(String status) {
        String query = "SELECT * FROM c WHERE c.status = @status";
        SqlQuerySpec querySpec = new SqlQuerySpec(query,
                Arrays.asList(new SqlParameter("@status", status)));
        CosmosPagedIterable<JsonNode> results = container.queryItems(querySpec,
                new CosmosQueryRequestOptions(), JsonNode.class);
        List<Order> orders = new ArrayList<>();
        for (JsonNode node : results) {
            orders.add(objectMapper.convertValue(node, Order.class));
        }
        return orders;
    }

    public List<Order> getOrdersByDateRange(String startDate, String endDate) {
        // If endDate is date-only (no 'T'), append end-of-day to include the full day
        String effectiveEndDate = endDate;
        if (!endDate.contains("T")) {
            effectiveEndDate = endDate + "T23:59:59.999Z";
        }
        String query = "SELECT * FROM c WHERE c.createdAt >= @startDate AND c.createdAt <= @endDate";
        SqlQuerySpec querySpec = new SqlQuerySpec(query,
                Arrays.asList(
                        new SqlParameter("@startDate", startDate),
                        new SqlParameter("@endDate", effectiveEndDate)));
        CosmosPagedIterable<JsonNode> results = container.queryItems(querySpec,
                new CosmosQueryRequestOptions(), JsonNode.class);
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

    public void deleteOrder(String orderId, String customerId) {
        container.deleteItem(orderId, new PartitionKey(customerId), new CosmosItemRequestOptions());
    }
}
