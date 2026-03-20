package com.ecommerce.service;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
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

@Service
public class OrderService {

    private final CosmosContainer container;

    private static final Set<String> VALID_STATUSES =
            new HashSet<>(Arrays.asList("pending", "shipped", "delivered", "cancelled"));

    public OrderService(CosmosContainer container) {
        this.container = container;
    }

    public Order createOrder(String customerId, List<OrderItem> items, String shippingAddress) {
        String orderId = UUID.randomUUID().toString();

        double total = 0.0;
        for (OrderItem item : items) {
            total += item.getQuantity() * item.getUnitPrice();
        }

        Order order = new Order();
        order.setId(orderId);
        order.setOrderId(orderId);
        order.setCustomerId(customerId);
        order.setStatus("pending");
        order.setItems(items);
        order.setTotal(total);
        order.setCreatedAt(Instant.now().toString());
        if (shippingAddress != null) {
            order.setShippingAddress(shippingAddress);
        }

        CosmosItemResponse<Order> response = container.createItem(
                order, new PartitionKey(customerId), new CosmosItemRequestOptions());
        return response.getItem();
    }

    public Order getOrder(String orderId) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.orderId = @orderId",
                Arrays.asList(new SqlParameter("@orderId", orderId)));

        List<Order> results = container.queryItems(query, new CosmosQueryRequestOptions(), Order.class)
                .stream().toList();

        if (results.isEmpty()) {
            return null;
        }
        return results.get(0);
    }

    public List<Order> getCustomerOrders(String customerId) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.customerId = @customerId",
                Arrays.asList(new SqlParameter("@customerId", customerId)));

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(new PartitionKey(customerId));

        return container.queryItems(query, options, Order.class)
                .stream().toList();
    }

    public List<Order> getOrdersByStatus(String status) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.status = @status",
                Arrays.asList(new SqlParameter("@status", status)));

        return container.queryItems(query, new CosmosQueryRequestOptions(), Order.class)
                .stream().toList();
    }

    public List<Order> getOrdersByDateRange(String startDate, String endDate) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.createdAt >= @startDate AND c.createdAt <= @endDate",
                Arrays.asList(
                        new SqlParameter("@startDate", startDate),
                        new SqlParameter("@endDate", endDate)));

        return container.queryItems(query, new CosmosQueryRequestOptions(), Order.class)
                .stream().toList();
    }

    public Order updateOrderStatus(String orderId, String newStatus) {
        Order order = getOrder(orderId);
        if (order == null) {
            return null;
        }

        String currentStatus = order.getStatus();

        // Validate status transition
        if (!isValidTransition(currentStatus, newStatus)) {
            throw new InvalidStatusTransitionException(
                    "Invalid status transition from '" + currentStatus + "' to '" + newStatus + "'");
        }

        order.setStatus(newStatus);

        CosmosItemResponse<Order> response = container.upsertItem(
                order, new PartitionKey(order.getCustomerId()), new CosmosItemRequestOptions());
        return response.getItem();
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

        return switch (currentStatus) {
            case "pending" -> "shipped".equals(newStatus) || "cancelled".equals(newStatus) || "delivered".equals(newStatus);
            case "shipped" -> "delivered".equals(newStatus);
            case "delivered", "cancelled" -> false;
            default -> false;
        };
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
