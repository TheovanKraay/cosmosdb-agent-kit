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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
public class OrderRepository {

    private final CosmosContainer container;
    private final ObjectMapper objectMapper;

    public OrderRepository(CosmosContainer ordersContainer) {
        this.container = ordersContainer;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(
                com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public Order createOrder(Order order) {
        container.createItem(order, new PartitionKey(order.getCustomerId()),
                new CosmosItemRequestOptions());
        return order;
    }

    public Order getOrderById(String orderId) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.orderId = @orderId",
                List.of(new SqlParameter("@orderId", orderId)));
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        CosmosPagedIterable<JsonNode> results =
                container.queryItems(query, options, JsonNode.class);
        for (JsonNode node : results) {
            return objectMapper.convertValue(node, Order.class);
        }
        return null;
    }

    public List<Order> getOrdersByCustomerId(String customerId) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.customerId = @customerId ORDER BY c.createdAt DESC",
                List.of(new SqlParameter("@customerId", customerId)));
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(new PartitionKey(customerId));
        CosmosPagedIterable<JsonNode> results =
                container.queryItems(query, options, JsonNode.class);
        List<Order> orders = new ArrayList<>();
        for (JsonNode node : results) {
            orders.add(objectMapper.convertValue(node, Order.class));
        }
        return orders;
    }

    public List<Order> getOrdersByStatus(String status) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.status = @status ORDER BY c.createdAt DESC",
                List.of(new SqlParameter("@status", status)));
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        CosmosPagedIterable<JsonNode> results =
                container.queryItems(query, options, JsonNode.class);
        List<Order> orders = new ArrayList<>();
        for (JsonNode node : results) {
            orders.add(objectMapper.convertValue(node, Order.class));
        }
        return orders;
    }

    public List<Order> getOrdersByDateRange(String startDate, String endDate) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.createdAt >= @startDate AND c.createdAt <= @endDate ORDER BY c.createdAt DESC",
                List.of(new SqlParameter("@startDate", startDate),
                        new SqlParameter("@endDate", endDate)));
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        CosmosPagedIterable<JsonNode> results =
                container.queryItems(query, options, JsonNode.class);
        List<Order> orders = new ArrayList<>();
        for (JsonNode node : results) {
            orders.add(objectMapper.convertValue(node, Order.class));
        }
        return orders;
    }

    public Order updateOrder(Order order) {
        container.upsertItem(order, new PartitionKey(order.getCustomerId()),
                new CosmosItemRequestOptions());
        return order;
    }

    public void deleteOrder(String id, String customerId) {
        container.deleteItem(id, new PartitionKey(customerId),
                new CosmosItemRequestOptions());
    }
}
