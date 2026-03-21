package com.ecommerce.service;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.ecommerce.model.CreateOrderRequest;
import com.ecommerce.model.CustomerSummary;
import com.ecommerce.model.Order;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class OrderService {

    private static final Set<String> VALID_STATUSES = Set.of("pending", "shipped", "delivered", "cancelled");
    private static final Set<String> TERMINAL_STATUSES = Set.of("delivered", "cancelled");

    private final CosmosContainer container;

    public OrderService(CosmosContainer container) {
        this.container = container;
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

        // Auto-calculate total
        double total = request.getItems().stream()
                .mapToDouble(item -> item.getQuantity() * item.getUnitPrice())
                .sum();
        // Round to 2 decimal places to avoid floating point issues
        total = Math.round(total * 100.0) / 100.0;
        order.setTotal(total);

        container.createItem(order, new PartitionKey(order.getCustomerId()), new CosmosItemRequestOptions());
        return order;
    }

    public Order getOrder(String orderId) {
        // Query across all partitions since we don't know the customerId
        String query = "SELECT * FROM c WHERE c.orderId = @orderId";
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        List<Order> results = container.queryItems(
                new com.azure.cosmos.models.SqlQuerySpec(
                        query,
                        List.of(new com.azure.cosmos.models.SqlParameter("@orderId", orderId))
                ),
                options,
                Order.class
        ).stream().toList();

        return results.isEmpty() ? null : results.get(0);
    }

    public List<Order> getCustomerOrders(String customerId) {
        String query = "SELECT * FROM c WHERE c.customerId = @customerId ORDER BY c.createdAt DESC";
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(new PartitionKey(customerId));
        return container.queryItems(
                new com.azure.cosmos.models.SqlQuerySpec(
                        query,
                        List.of(new com.azure.cosmos.models.SqlParameter("@customerId", customerId))
                ),
                options,
                Order.class
        ).stream().toList();
    }

    public CustomerSummary getCustomerSummary(String customerId) {
        List<Order> orders = getCustomerOrders(customerId);
        int totalOrders = orders.size();
        double totalSpent = orders.stream().mapToDouble(Order::getTotal).sum();
        totalSpent = Math.round(totalSpent * 100.0) / 100.0;
        double averageOrderValue = totalOrders > 0 ? Math.round((totalSpent / totalOrders) * 100.0) / 100.0 : 0.0;
        return new CustomerSummary(customerId, totalOrders, totalSpent, averageOrderValue);
    }

    public List<Order> getOrdersByStatus(String status) {
        String query = "SELECT * FROM c WHERE c.status = @status";
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        return container.queryItems(
                new com.azure.cosmos.models.SqlQuerySpec(
                        query,
                        List.of(new com.azure.cosmos.models.SqlParameter("@status", status))
                ),
                options,
                Order.class
        ).stream().toList();
    }

    public List<Order> getOrdersByDateRange(String startDate, String endDate) {
        String query = "SELECT * FROM c WHERE c.createdAt >= @startDate AND c.createdAt <= @endDate";
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        return container.queryItems(
                new com.azure.cosmos.models.SqlQuerySpec(
                        query,
                        List.of(
                                new com.azure.cosmos.models.SqlParameter("@startDate", startDate),
                                new com.azure.cosmos.models.SqlParameter("@endDate", endDate)
                        )
                ),
                options,
                Order.class
        ).stream().toList();
    }

    public Order updateStatus(String orderId, String newStatus) {
        Order order = getOrder(orderId);
        if (order == null) {
            return null;
        }

        String currentStatus = order.getStatus();

        // Validate transition
        if (!isValidTransition(currentStatus, newStatus)) {
            throw new InvalidStatusTransitionException(
                    "Invalid status transition from '" + currentStatus + "' to '" + newStatus + "'");
        }

        order.setStatus(newStatus);
        container.replaceItem(order, order.getId(), new PartitionKey(order.getCustomerId()),
                new CosmosItemRequestOptions());
        return order;
    }

    public boolean deleteOrder(String orderId) {
        Order order = getOrder(orderId);
        if (order == null) {
            throw new OrderNotFoundException("Order not found: " + orderId);
        }

        if (!"pending".equals(order.getStatus())) {
            throw new InvalidStatusTransitionException(
                    "Only pending orders can be deleted. Current status: " + order.getStatus());
        }

        container.deleteItem(order.getId(), new PartitionKey(order.getCustomerId()),
                new CosmosItemRequestOptions());
        return true;
    }

    private boolean isValidTransition(String currentStatus, String newStatus) {
        if (!VALID_STATUSES.contains(newStatus)) {
            return false;
        }
        if (TERMINAL_STATUSES.contains(currentStatus)) {
            return false;
        }
        if ("pending".equals(currentStatus)) {
            // pending can transition to any valid status
            return VALID_STATUSES.contains(newStatus) && !"pending".equals(newStatus);
        }
        if ("shipped".equals(currentStatus)) {
            return "delivered".equals(newStatus);
        }
        return false;
    }

    public static class InvalidStatusTransitionException extends RuntimeException {
        public InvalidStatusTransitionException(String message) {
            super(message);
        }
    }

    public static class OrderNotFoundException extends RuntimeException {
        public OrderNotFoundException(String message) {
            super(message);
        }
    }
}
