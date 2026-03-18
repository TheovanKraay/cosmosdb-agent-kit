package com.ecommerce.service;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.ecommerce.model.CreateOrderRequest;
import com.ecommerce.model.CustomerSummary;
import com.ecommerce.model.Order;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class OrderService {

    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);
    private static final Set<String> VALID_STATUSES = Set.of("pending", "shipped", "delivered", "cancelled");

    private final CosmosContainer container;

    public OrderService(CosmosContainer container) {
        this.container = container;
    }

    public Order createOrder(CreateOrderRequest request) {
        Order order = new Order();
        String id = UUID.randomUUID().toString();
        order.setId(id);
        order.setOrderId(id);
        order.setCustomerId(request.getCustomerId());
        order.setStatus("pending");
        order.setItems(request.getItems());
        order.setShippingAddress(request.getShippingAddress());
        order.setCreatedAt(Instant.now().toString());
        order.setType("order");
        order.setSchemaVersion(1);

        double total = request.getItems().stream()
                .mapToDouble(item -> item.getQuantity() * item.getUnitPrice())
                .sum();
        order.setTotal(Math.round(total * 100.0) / 100.0);

        CosmosItemResponse<Order> response = container.createItem(
                order,
                new PartitionKey(order.getCustomerId()),
                new CosmosItemRequestOptions());

        logger.info("Created order {} - RU charge: {}", id, response.getRequestCharge());
        return response.getItem();
    }

    public Order getOrder(String orderId) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT c.id, c.orderId, c.customerId, c.status, c.items, c.total, c.createdAt, c.shippingAddress, c.type, c.schemaVersion FROM c WHERE c.id = @orderId",
                Arrays.asList(new SqlParameter("@orderId", orderId)));

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();

        List<Order> results = container.queryItems(query, options, Order.class)
                .stream()
                .toList();

        if (results.isEmpty()) {
            return null;
        }

        return results.get(0);
    }

    public List<Order> getCustomerOrders(String customerId) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT c.id, c.orderId, c.customerId, c.status, c.items, c.total, c.createdAt, c.shippingAddress, c.type, c.schemaVersion FROM c WHERE c.customerId = @customerId ORDER BY c.createdAt DESC",
                Arrays.asList(new SqlParameter("@customerId", customerId)));

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(new PartitionKey(customerId));

        return container.queryItems(query, options, Order.class)
                .stream()
                .toList();
    }

    public CustomerSummary getCustomerSummary(String customerId) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT VALUE { \"totalOrders\": COUNT(1), \"totalSpent\": SUM(c.total) } FROM c WHERE c.customerId = @customerId",
                Arrays.asList(new SqlParameter("@customerId", customerId)));

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(new PartitionKey(customerId));

        List<SummaryResult> results = container.queryItems(query, options, SummaryResult.class)
                .stream()
                .toList();

        int totalOrders = 0;
        double totalSpent = 0.0;

        if (!results.isEmpty()) {
            SummaryResult result = results.get(0);
            totalOrders = result.getTotalOrders();
            totalSpent = result.getTotalSpent();
        }

        double averageOrderValue = totalOrders > 0 ? totalSpent / totalOrders : 0.0;

        return new CustomerSummary(customerId, totalOrders, totalSpent, averageOrderValue);
    }

    public List<Order> queryOrdersByStatus(String status) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT c.id, c.orderId, c.customerId, c.status, c.items, c.total, c.createdAt, c.shippingAddress, c.type, c.schemaVersion FROM c WHERE c.status = @status ORDER BY c.createdAt DESC",
                Arrays.asList(new SqlParameter("@status", status)));

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();

        return container.queryItems(query, options, Order.class)
                .stream()
                .toList();
    }

    public List<Order> queryOrdersByDateRange(String startDate, String endDate) {
        String normalizedStart = startDate;
        String normalizedEnd = endDate;

        if (normalizedStart != null && !normalizedStart.contains("T")) {
            normalizedStart = normalizedStart + "T00:00:00Z";
        }
        if (normalizedEnd != null && !normalizedEnd.contains("T")) {
            normalizedEnd = normalizedEnd + "T23:59:59.999Z";
        }

        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT c.id, c.orderId, c.customerId, c.status, c.items, c.total, c.createdAt, c.shippingAddress, c.type, c.schemaVersion FROM c WHERE c.createdAt >= @startDate AND c.createdAt <= @endDate ORDER BY c.createdAt DESC",
                Arrays.asList(
                        new SqlParameter("@startDate", normalizedStart),
                        new SqlParameter("@endDate", normalizedEnd)));

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();

        return container.queryItems(query, options, Order.class)
                .stream()
                .toList();
    }

    public Order updateOrderStatus(String orderId, String newStatus) {
        if (!VALID_STATUSES.contains(newStatus)) {
            throw new IllegalArgumentException("Invalid status: " + newStatus);
        }

        Order order = getOrder(orderId);
        if (order == null) {
            return null;
        }

        String currentStatus = order.getStatus();

        if (!isValidTransition(currentStatus, newStatus)) {
            throw new StatusTransitionException(
                    "Invalid status transition from " + currentStatus + " to " + newStatus);
        }

        order.setStatus(newStatus);

        CosmosItemResponse<Order> response = container.upsertItem(
                order,
                new PartitionKey(order.getCustomerId()),
                new CosmosItemRequestOptions());

        logger.info("Updated order {} status to {} - RU charge: {}", orderId, newStatus, response.getRequestCharge());
        return response.getItem();
    }

    public boolean deleteOrder(String orderId) {
        Order order = getOrder(orderId);
        if (order == null) {
            throw new OrderNotFoundException("Order not found: " + orderId);
        }

        if (!"pending".equals(order.getStatus())) {
            throw new StatusTransitionException(
                    "Only pending orders can be deleted. Current status: " + order.getStatus());
        }

        CosmosItemResponse<Object> response = container.deleteItem(
                order.getId(),
                new PartitionKey(order.getCustomerId()),
                new CosmosItemRequestOptions());

        logger.info("Deleted order {} - RU charge: {}", orderId, response.getRequestCharge());
        return true;
    }

    private boolean isValidTransition(String currentStatus, String newStatus) {
        return switch (currentStatus) {
            case "pending" -> true;
            case "shipped" -> "delivered".equals(newStatus);
            default -> false;
        };
    }

    public static class StatusTransitionException extends RuntimeException {
        public StatusTransitionException(String message) {
            super(message);
        }
    }

    public static class OrderNotFoundException extends RuntimeException {
        public OrderNotFoundException(String message) {
            super(message);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class SummaryResult {
        private int totalOrders;
        private double totalSpent;

        public int getTotalOrders() {
            return totalOrders;
        }

        public void setTotalOrders(int totalOrders) {
            this.totalOrders = totalOrders;
        }

        public double getTotalSpent() {
            return totalSpent;
        }

        public void setTotalSpent(double totalSpent) {
            this.totalSpent = totalSpent;
        }
    }
}
