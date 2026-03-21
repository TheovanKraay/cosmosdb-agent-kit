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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class OrderService {

    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);
    private static final Set<String> VALID_STATUSES = new HashSet<>(
            Arrays.asList("pending", "shipped", "delivered", "cancelled"));

    private final CosmosContainer container;

    public OrderService(CosmosContainer container) {
        this.container = container;
    }

    public Order createOrder(Map<String, Object> requestBody) {
        String customerId = (String) requestBody.get("customerId");
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("customerId is required");
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rawItems = (List<Map<String, Object>>) requestBody.get("items");
        if (rawItems == null || rawItems.isEmpty()) {
            throw new IllegalArgumentException("items is required and must not be empty");
        }

        List<OrderItem> items = new ArrayList<>();
        double total = 0.0;

        for (Map<String, Object> rawItem : rawItems) {
            OrderItem item = new OrderItem();
            item.setProductId((String) rawItem.get("productId"));
            item.setProductName((String) rawItem.get("productName"));
            item.setQuantity(toInt(rawItem.get("quantity")));
            item.setUnitPrice(toDouble(rawItem.get("unitPrice")));

            if (item.getQuantity() < 1) {
                throw new IllegalArgumentException("Item quantity must be >= 1");
            }

            total += item.getQuantity() * item.getUnitPrice();
            items.add(item);
        }

        String orderId = UUID.randomUUID().toString();
        Order order = new Order();
        order.setId(orderId);
        order.setOrderId(orderId);
        order.setCustomerId(customerId);
        order.setStatus("pending");
        order.setItems(items);
        order.setTotal(Math.round(total * 100.0) / 100.0);
        order.setCreatedAt(Instant.now().toString());

        String shippingAddress = (String) requestBody.get("shippingAddress");
        if (shippingAddress != null) {
            order.setShippingAddress(shippingAddress);
        }

        CosmosItemResponse<Order> response = container.createItem(order,
                new PartitionKey(customerId), new CosmosItemRequestOptions());

        logger.debug("Created order {} - RU charge: {}", orderId, response.getRequestCharge());
        return order;
    }

    public Order getOrder(String orderId) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.orderId = @orderId",
                List.of(new SqlParameter("@orderId", orderId)));

        List<Order> results = container.queryItems(query, new CosmosQueryRequestOptions(), Order.class)
                .stream().toList();

        if (results.isEmpty()) {
            return null;
        }

        logger.debug("Retrieved order {}", orderId);
        return results.get(0);
    }

    public List<Order> getCustomerOrders(String customerId) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.customerId = @customerId ORDER BY c.createdAt DESC",
                List.of(new SqlParameter("@customerId", customerId)));

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(new PartitionKey(customerId));

        List<Order> results = container.queryItems(query, options, Order.class)
                .stream().toList();

        logger.debug("Found {} orders for customer {}", results.size(), customerId);
        return results;
    }

    public List<Order> queryOrdersByStatus(String status) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.status = @status ORDER BY c.createdAt DESC",
                List.of(new SqlParameter("@status", status)));

        List<Order> results = container.queryItems(query, new CosmosQueryRequestOptions(), Order.class)
                .stream().toList();

        logger.debug("Found {} orders with status {}", results.size(), status);
        return results;
    }

    public List<Order> queryOrdersByDateRange(String startDate, String endDate) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.createdAt >= @startDate AND c.createdAt <= @endDate ORDER BY c.createdAt DESC",
                List.of(
                        new SqlParameter("@startDate", startDate),
                        new SqlParameter("@endDate", endDate)));

        List<Order> results = container.queryItems(query, new CosmosQueryRequestOptions(), Order.class)
                .stream().toList();

        logger.debug("Found {} orders in date range {} to {}", results.size(), startDate, endDate);
        return results;
    }

    public Order updateOrderStatus(String orderId, String newStatus) {
        if (!VALID_STATUSES.contains(newStatus)) {
            throw new InvalidStatusException("Invalid status: " + newStatus);
        }

        Order order = getOrder(orderId);
        if (order == null) {
            return null;
        }

        String currentStatus = order.getStatus();

        if (!isValidTransition(currentStatus, newStatus)) {
            throw new InvalidTransitionException(
                    "Invalid transition from " + currentStatus + " to " + newStatus);
        }

        order.setStatus(newStatus);

        CosmosItemResponse<Order> response = container.replaceItem(order, order.getId(),
                new PartitionKey(order.getCustomerId()), new CosmosItemRequestOptions());

        logger.debug("Updated order {} status to {} - RU charge: {}",
                orderId, newStatus, response.getRequestCharge());
        return order;
    }

    public Map<String, Object> getCustomerSummary(String customerId) {
        List<Order> orders = getCustomerOrders(customerId);

        int totalOrders = orders.size();
        double totalSpent = orders.stream().mapToDouble(Order::getTotal).sum();
        totalSpent = Math.round(totalSpent * 100.0) / 100.0;
        double averageOrderValue = totalOrders > 0
                ? Math.round((totalSpent / totalOrders) * 100.0) / 100.0 : 0.0;

        return Map.of(
                "customerId", customerId,
                "totalOrders", totalOrders,
                "totalSpent", totalSpent,
                "averageOrderValue", averageOrderValue);
    }

    public boolean deleteOrder(String orderId) {
        Order order = getOrder(orderId);
        if (order == null) {
            throw new OrderNotFoundException("Order not found: " + orderId);
        }

        if (!"pending".equals(order.getStatus())) {
            throw new InvalidTransitionException(
                    "Only pending orders can be deleted. Current status: " + order.getStatus());
        }

        CosmosItemResponse<Object> response = container.deleteItem(order.getId(),
                new PartitionKey(order.getCustomerId()), new CosmosItemRequestOptions());

        logger.debug("Deleted order {} - RU charge: {}", orderId, response.getRequestCharge());
        return true;
    }

    private boolean isValidTransition(String currentStatus, String newStatus) {
        return switch (currentStatus) {
            case "pending" -> VALID_STATUSES.contains(newStatus) && !"pending".equals(newStatus);
            case "shipped" -> "delivered".equals(newStatus);
            default -> false; // delivered and cancelled are terminal
        };
    }

    private int toInt(Object value) {
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Number) return ((Number) value).intValue();
        return Integer.parseInt(value.toString());
    }

    private double toDouble(Object value) {
        if (value instanceof Double) return (Double) value;
        if (value instanceof Number) return ((Number) value).doubleValue();
        return Double.parseDouble(value.toString());
    }

    public static class OrderNotFoundException extends RuntimeException {
        public OrderNotFoundException(String message) {
            super(message);
        }
    }

    public static class InvalidTransitionException extends RuntimeException {
        public InvalidTransitionException(String message) {
            super(message);
        }
    }

    public static class InvalidStatusException extends RuntimeException {
        public InvalidStatusException(String message) {
            super(message);
        }
    }
}
