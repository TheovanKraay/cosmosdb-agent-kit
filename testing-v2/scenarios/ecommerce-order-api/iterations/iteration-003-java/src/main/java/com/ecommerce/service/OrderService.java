package com.ecommerce.service;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.models.CosmosItemRequestOptions;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class OrderService {

    private final CosmosContainer container;

    // Valid status transitions: key = current status, value = set of valid target statuses
    private static final Map<String, Set<String>> VALID_TRANSITIONS = Map.of(
            "pending", Set.of("shipped", "cancelled", "delivered"),
            "shipped", Set.of("delivered")
    );

    public OrderService(CosmosContainer ordersContainer) {
        this.container = ordersContainer;
    }

    public Order createOrder(Map<String, Object> request) {
        Order order = new Order();
        String orderId = UUID.randomUUID().toString();
        order.setId(orderId);
        order.setOrderId(orderId);
        order.setCustomerId((String) request.get("customerId"));
        order.setStatus("pending");
        order.setCreatedAt(Instant.now().toString());

        if (request.containsKey("shippingAddress")) {
            order.setShippingAddress((String) request.get("shippingAddress"));
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> itemMaps = (List<Map<String, Object>>) request.get("items");
        List<OrderItem> items = new ArrayList<>();
        double total = 0;
        for (Map<String, Object> itemMap : itemMaps) {
            OrderItem item = new OrderItem();
            item.setProductId((String) itemMap.get("productId"));
            item.setProductName((String) itemMap.get("productName"));
            item.setQuantity(((Number) itemMap.get("quantity")).intValue());
            item.setUnitPrice(((Number) itemMap.get("unitPrice")).doubleValue());
            items.add(item);
            total += item.getQuantity() * item.getUnitPrice();
        }
        order.setItems(items);
        // Round total to 2 decimal places to avoid floating point issues
        order.setTotal(Math.round(total * 100.0) / 100.0);

        container.createItem(order, new PartitionKey(order.getCustomerId()), new CosmosItemRequestOptions());
        return order;
    }

    public Order getOrder(String orderId) {
        // Query across all partitions since we don't know the customerId
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.orderId = @orderId",
                Arrays.asList(new SqlParameter("@orderId", orderId))
        );
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        List<Order> results = container.queryItems(query, options, Order.class)
                .stream().toList();
        return results.isEmpty() ? null : results.get(0);
    }

    public List<Order> getCustomerOrders(String customerId) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.customerId = @customerId",
                Arrays.asList(new SqlParameter("@customerId", customerId))
        );
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(new PartitionKey(customerId));
        return container.queryItems(query, options, Order.class)
                .stream().toList();
    }

    public Map<String, Object> getCustomerSummary(String customerId) {
        List<Order> orders = getCustomerOrders(customerId);
        int totalOrders = orders.size();
        double totalSpent = orders.stream().mapToDouble(Order::getTotal).sum();
        totalSpent = Math.round(totalSpent * 100.0) / 100.0;
        double avg = totalOrders > 0 ? totalSpent / totalOrders : 0;
        avg = Math.round(avg * 1000.0) / 1000.0;

        Map<String, Object> summary = new HashMap<>();
        summary.put("customerId", customerId);
        summary.put("totalOrders", totalOrders);
        summary.put("totalSpent", totalSpent);
        summary.put("averageOrderValue", avg);
        return summary;
    }

    public List<Order> queryByStatus(String status) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.status = @status",
                Arrays.asList(new SqlParameter("@status", status))
        );
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        return container.queryItems(query, options, Order.class)
                .stream().toList();
    }

    public List<Order> queryByDateRange(String startDate, String endDate) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.createdAt >= @startDate AND c.createdAt <= @endDate",
                Arrays.asList(
                        new SqlParameter("@startDate", startDate),
                        new SqlParameter("@endDate", endDate)
                )
        );
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        return container.queryItems(query, options, Order.class)
                .stream().toList();
    }

    public Order updateOrderStatus(String orderId, String newStatus) {
        Order order = getOrder(orderId);
        if (order == null) {
            return null;
        }

        String currentStatus = order.getStatus();
        Set<String> allowed = VALID_TRANSITIONS.get(currentStatus);
        if (allowed == null || !allowed.contains(newStatus)) {
            throw new InvalidStatusTransitionException(
                    "Cannot transition from '" + currentStatus + "' to '" + newStatus + "'"
            );
        }

        order.setStatus(newStatus);
        container.replaceItem(order, order.getId(),
                new PartitionKey(order.getCustomerId()),
                new CosmosItemRequestOptions());
        return order;
    }

    public boolean deleteOrder(String orderId) {
        Order order = getOrder(orderId);
        if (order == null) {
            return false;
        }

        if (!"pending".equals(order.getStatus())) {
            throw new InvalidStatusTransitionException(
                    "Only pending orders can be deleted. Current status: " + order.getStatus()
            );
        }

        container.deleteItem(order.getId(),
                new PartitionKey(order.getCustomerId()),
                new CosmosItemRequestOptions());
        return true;
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
