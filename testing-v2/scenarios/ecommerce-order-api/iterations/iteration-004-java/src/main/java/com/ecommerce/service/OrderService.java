package com.ecommerce.service;

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
import com.ecommerce.model.OrderItem;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private final CosmosContainer container;

    private static final Set<String> VALID_STATUSES =
            new HashSet<>(Arrays.asList("pending", "shipped", "delivered", "cancelled"));

    public OrderService(CosmosContainer container) {
        this.container = container;
    }

    public Order createOrder(Order orderRequest) {
        // Validate required fields
        if (orderRequest.getCustomerId() == null || orderRequest.getCustomerId().isEmpty()) {
            throw new IllegalArgumentException("customerId is required");
        }
        if (orderRequest.getItems() == null || orderRequest.getItems().isEmpty()) {
            throw new IllegalArgumentException("items is required and must not be empty");
        }

        Order order = new Order();
        String orderId = UUID.randomUUID().toString();
        order.setId(orderId);
        order.setOrderId(orderId);
        order.setCustomerId(orderRequest.getCustomerId());
        order.setStatus("pending");
        order.setItems(orderRequest.getItems());
        order.setShippingAddress(orderRequest.getShippingAddress());
        order.setCreatedAt(Instant.now().toString());

        // Auto-calculate total
        double total = 0.0;
        for (OrderItem item : orderRequest.getItems()) {
            total += item.getQuantity() * item.getUnitPrice();
        }
        order.setTotal(total);

        CosmosItemResponse<Order> response = container.createItem(
                order,
                new PartitionKey(order.getCustomerId()),
                new CosmosItemRequestOptions());

        return response.getItem();
    }

    public Order getOrderById(String orderId) {
        // Cross-partition query to find order by orderId
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.orderId = @orderId",
                List.of(new SqlParameter("@orderId", orderId)));

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        CosmosPagedIterable<Order> results = container.queryItems(query, options, Order.class);

        return results.stream().findFirst().orElse(null);
    }

    public List<Order> getOrdersByCustomer(String customerId) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.customerId = @customerId ORDER BY c.createdAt DESC",
                List.of(new SqlParameter("@customerId", customerId)));

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(new PartitionKey(customerId));

        CosmosPagedIterable<Order> results = container.queryItems(query, options, Order.class);
        return results.stream().collect(Collectors.toList());
    }

    public List<Order> getOrdersByStatus(String status) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.status = @status",
                List.of(new SqlParameter("@status", status)));

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        CosmosPagedIterable<Order> results = container.queryItems(query, options, Order.class);
        return results.stream().collect(Collectors.toList());
    }

    public List<Order> getOrdersByDateRange(String startDate, String endDate) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.createdAt >= @startDate AND c.createdAt <= @endDate",
                List.of(
                        new SqlParameter("@startDate", startDate),
                        new SqlParameter("@endDate", endDate)));

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        CosmosPagedIterable<Order> results = container.queryItems(query, options, Order.class);
        return results.stream().collect(Collectors.toList());
    }

    public Order updateOrderStatus(String orderId, String newStatus) {
        if (!VALID_STATUSES.contains(newStatus)) {
            throw new IllegalArgumentException("Invalid status: " + newStatus);
        }

        Order order = getOrderById(orderId);
        if (order == null) {
            return null;
        }

        String currentStatus = order.getStatus();

        // Validate status transition
        if (!isValidTransition(currentStatus, newStatus)) {
            throw new InvalidStatusTransitionException(
                    "Cannot transition from " + currentStatus + " to " + newStatus);
        }

        order.setStatus(newStatus);

        CosmosItemResponse<Order> response = container.replaceItem(
                order,
                order.getId(),
                new PartitionKey(order.getCustomerId()),
                new CosmosItemRequestOptions());

        return response.getItem();
    }

    public boolean deleteOrder(String orderId) {
        Order order = getOrderById(orderId);
        if (order == null) {
            throw new OrderNotFoundException("Order not found: " + orderId);
        }

        if (!"pending".equals(order.getStatus())) {
            throw new InvalidStatusTransitionException(
                    "Only pending orders can be deleted. Current status: " + order.getStatus());
        }

        container.deleteItem(
                order.getId(),
                new PartitionKey(order.getCustomerId()),
                new CosmosItemRequestOptions());

        return true;
    }

    public CustomerSummary getCustomerSummary(String customerId) {
        List<Order> orders = getOrdersByCustomer(customerId);

        CustomerSummary summary = new CustomerSummary();
        summary.setCustomerId(customerId);
        summary.setTotalOrders(orders.size());

        double totalSpent = orders.stream().mapToDouble(Order::getTotal).sum();
        summary.setTotalSpent(totalSpent);

        if (!orders.isEmpty()) {
            summary.setAverageOrderValue(totalSpent / orders.size());
        } else {
            summary.setAverageOrderValue(0.0);
        }

        return summary;
    }

    private boolean isValidTransition(String currentStatus, String newStatus) {
        if (currentStatus.equals(newStatus)) {
            return false;
        }
        switch (currentStatus) {
            case "pending":
                // From pending, can go to shipped, cancelled, or delivered
                return "shipped".equals(newStatus) ||
                       "cancelled".equals(newStatus) ||
                       "delivered".equals(newStatus);
            case "shipped":
                // From shipped, can only go to delivered
                return "delivered".equals(newStatus);
            case "delivered":
            case "cancelled":
                // Terminal states
                return false;
            default:
                return false;
        }
    }

    public static class CustomerSummary {
        private String customerId;
        private int totalOrders;
        private double totalSpent;
        private double averageOrderValue;

        public String getCustomerId() { return customerId; }
        public void setCustomerId(String customerId) { this.customerId = customerId; }

        public int getTotalOrders() { return totalOrders; }
        public void setTotalOrders(int totalOrders) { this.totalOrders = totalOrders; }

        public double getTotalSpent() { return totalSpent; }
        public void setTotalSpent(double totalSpent) { this.totalSpent = totalSpent; }

        public double getAverageOrderValue() { return averageOrderValue; }
        public void setAverageOrderValue(double averageOrderValue) { this.averageOrderValue = averageOrderValue; }
    }

    public static class OrderNotFoundException extends RuntimeException {
        public OrderNotFoundException(String message) {
            super(message);
        }
    }

    public static class InvalidStatusTransitionException extends RuntimeException {
        public InvalidStatusTransitionException(String message) {
            super(message);
        }
    }
}
