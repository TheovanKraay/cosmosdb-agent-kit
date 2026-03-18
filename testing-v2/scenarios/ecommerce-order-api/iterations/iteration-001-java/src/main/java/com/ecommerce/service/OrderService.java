package com.ecommerce.service;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.util.CosmosPagedIterable;
import com.ecommerce.model.CreateOrderRequest;
import com.ecommerce.model.CustomerSummary;
import com.ecommerce.model.Order;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class OrderService {

    private static final Set<String> VALID_STATUSES = new HashSet<>(
            Arrays.asList("pending", "shipped", "delivered", "cancelled")
    );

    private final CosmosContainer ordersContainer;
    private final ObjectMapper objectMapper;

    public OrderService(CosmosContainer ordersContainer, ObjectMapper objectMapper) {
        this.ordersContainer = ordersContainer;
        this.objectMapper = objectMapper;
    }

    public Order createOrder(CreateOrderRequest request) {
        Order order = new Order();
        String orderId = UUID.randomUUID().toString();
        order.setId(orderId);
        order.setOrderId(orderId);
        order.setCustomerId(request.getCustomerId());
        order.setStatus("pending");
        order.setItems(request.getItems());
        order.setShippingAddress(request.getShippingAddress());
        order.setCreatedAt(Instant.now().toString());

        double total = request.getItems().stream()
                .mapToDouble(item -> BigDecimal.valueOf(item.getQuantity())
                        .multiply(BigDecimal.valueOf(item.getUnitPrice()))
                        .doubleValue())
                .sum();
        order.setTotal(BigDecimal.valueOf(total).setScale(2, RoundingMode.HALF_UP).doubleValue());

        ordersContainer.createItem(order, new PartitionKey(order.getCustomerId()), new CosmosItemRequestOptions());
        return order;
    }

    public Order getOrder(String orderId) {
        String query = "SELECT * FROM c WHERE c.orderId = @orderId";
        List<Order> results = queryOrders(query, "orderId", orderId);
        return results.isEmpty() ? null : results.get(0);
    }

    public List<Order> getCustomerOrders(String customerId) {
        String query = "SELECT * FROM c WHERE c.customerId = @customerId ORDER BY c.createdAt DESC";
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(new PartitionKey(customerId));

        List<Order> orders = new ArrayList<>();
        CosmosPagedIterable<JsonNode> items = ordersContainer.queryItems(
                new com.azure.cosmos.models.SqlQuerySpec(query,
                        List.of(new com.azure.cosmos.models.SqlParameter("@customerId", customerId))),
                options, JsonNode.class);

        items.forEach(node -> {
            Order order = objectMapper.convertValue(node, Order.class);
            orders.add(order);
        });
        return orders;
    }

    public List<Order> getOrdersByStatus(String status) {
        String query = "SELECT * FROM c WHERE c.status = @status";
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();

        List<Order> orders = new ArrayList<>();
        CosmosPagedIterable<JsonNode> items = ordersContainer.queryItems(
                new com.azure.cosmos.models.SqlQuerySpec(query,
                        List.of(new com.azure.cosmos.models.SqlParameter("@status", status))),
                options, JsonNode.class);

        items.forEach(node -> {
            Order order = objectMapper.convertValue(node, Order.class);
            orders.add(order);
        });
        return orders;
    }

    public List<Order> getOrdersByDateRange(String startDate, String endDate) {
        String query = "SELECT * FROM c WHERE c.createdAt >= @startDate AND c.createdAt <= @endDate";
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();

        List<Order> orders = new ArrayList<>();
        CosmosPagedIterable<JsonNode> items = ordersContainer.queryItems(
                new com.azure.cosmos.models.SqlQuerySpec(query,
                        List.of(
                                new com.azure.cosmos.models.SqlParameter("@startDate", startDate),
                                new com.azure.cosmos.models.SqlParameter("@endDate", endDate))),
                options, JsonNode.class);

        items.forEach(node -> {
            Order order = objectMapper.convertValue(node, Order.class);
            orders.add(order);
        });
        return orders;
    }

    public Order updateOrderStatus(String orderId, String newStatus) {
        if (newStatus == null || newStatus.isBlank()) {
            throw new IllegalArgumentException("Status is required");
        }

        if (!VALID_STATUSES.contains(newStatus)) {
            throw new IllegalArgumentException("Invalid status value: " + newStatus);
        }

        Order order = getOrder(orderId);
        if (order == null) {
            return null;
        }

        if (!isValidTransition(order.getStatus(), newStatus)) {
            throw new StatusConflictException("Invalid status transition from " + order.getStatus() + " to " + newStatus);
        }

        order.setStatus(newStatus);
        ordersContainer.replaceItem(order, order.getId(),
                new PartitionKey(order.getCustomerId()), new CosmosItemRequestOptions());
        return order;
    }

    public boolean deleteOrder(String orderId) {
        Order order = getOrder(orderId);
        if (order == null) {
            return false;
        }

        if (!"pending".equals(order.getStatus())) {
            throw new StatusConflictException("Only pending orders can be deleted");
        }

        ordersContainer.deleteItem(order.getId(),
                new PartitionKey(order.getCustomerId()), new CosmosItemRequestOptions());
        return true;
    }

    public CustomerSummary getCustomerSummary(String customerId) {
        List<Order> orders = getCustomerOrders(customerId);
        int totalOrders = orders.size();
        double totalSpent = orders.stream()
                .mapToDouble(Order::getTotal)
                .sum();
        totalSpent = BigDecimal.valueOf(totalSpent).setScale(2, RoundingMode.HALF_UP).doubleValue();
        double averageOrderValue = totalOrders > 0
                ? BigDecimal.valueOf(totalSpent / totalOrders).setScale(2, RoundingMode.HALF_UP).doubleValue()
                : 0.0;

        return new CustomerSummary(customerId, totalOrders, totalSpent, averageOrderValue);
    }

    private boolean isValidTransition(String currentStatus, String newStatus) {
        return switch (currentStatus) {
            case "pending" -> true;
            case "shipped" -> "delivered".equals(newStatus);
            default -> false;
        };
    }

    private List<Order> queryOrders(String query, String paramName, String paramValue) {
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        List<Order> orders = new ArrayList<>();

        CosmosPagedIterable<JsonNode> items = ordersContainer.queryItems(
                new com.azure.cosmos.models.SqlQuerySpec(query,
                        List.of(new com.azure.cosmos.models.SqlParameter("@" + paramName, paramValue))),
                options, JsonNode.class);

        items.forEach(node -> {
            Order order = objectMapper.convertValue(node, Order.class);
            orders.add(order);
        });
        return orders;
    }

    public static class StatusConflictException extends RuntimeException {
        public StatusConflictException(String message) {
            super(message);
        }
    }
}
