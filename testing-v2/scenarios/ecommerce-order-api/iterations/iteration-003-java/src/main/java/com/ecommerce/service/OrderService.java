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
import com.ecommerce.model.CustomerSummary;
import com.ecommerce.model.Order;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class OrderService {

    private final CosmosContainer container;

    private static final Set<String> VALID_STATUSES = Set.of("pending", "shipped", "delivered", "cancelled");
    private static final Set<String> TERMINAL_STATUSES = Set.of("delivered", "cancelled");

    public OrderService(CosmosContainer container) {
        this.container = container;
    }

    public Order createOrder(String customerId, List<com.ecommerce.model.OrderItem> items, String shippingAddress) {
        Order order = new Order();
        String orderId = UUID.randomUUID().toString();
        order.setId(orderId);
        order.setOrderId(orderId);
        order.setCustomerId(customerId);
        order.setStatus("pending");
        order.setItems(items);
        order.setTotal(order.calculateTotal());
        order.setCreatedAt(Instant.now().toString());
        order.setShippingAddress(shippingAddress);
        order.setType("order");
        order.setSchemaVersion("1.0");

        container.createItem(order, new PartitionKey(customerId), new CosmosItemRequestOptions());
        return order;
    }

    public Order getOrder(String orderId) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.orderId = @orderId",
                Collections.singletonList(new SqlParameter("@orderId", orderId)));

        CosmosPagedIterable<Order> results = container.queryItems(query, new CosmosQueryRequestOptions(), Order.class);
        for (Order order : results) {
            return order;
        }
        return null;
    }

    public List<Order> getCustomerOrders(String customerId) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.customerId = @customerId",
                Collections.singletonList(new SqlParameter("@customerId", customerId)));

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(new PartitionKey(customerId));

        List<Order> orders = new ArrayList<>();
        container.queryItems(query, options, Order.class).forEach(orders::add);
        return orders;
    }

    public CustomerSummary getCustomerSummary(String customerId) {
        List<Order> orders = getCustomerOrders(customerId);
        int totalOrders = orders.size();
        double totalSpent = 0.0;
        for (Order o : orders) {
            totalSpent += o.getTotal();
        }
        double averageOrderValue = totalOrders > 0 ? totalSpent / totalOrders : 0.0;
        return new CustomerSummary(customerId, totalOrders, totalSpent, averageOrderValue);
    }

    public List<Order> getOrdersByStatus(String status) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.status = @status",
                Collections.singletonList(new SqlParameter("@status", status)));

        List<Order> orders = new ArrayList<>();
        container.queryItems(query, new CosmosQueryRequestOptions(), Order.class).forEach(orders::add);
        return orders;
    }

    public List<Order> getOrdersByDateRange(String startDate, String endDate) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.createdAt >= @startDate AND c.createdAt <= @endDate",
                Arrays.asList(
                        new SqlParameter("@startDate", startDate),
                        new SqlParameter("@endDate", endDate)));

        List<Order> orders = new ArrayList<>();
        container.queryItems(query, new CosmosQueryRequestOptions(), Order.class).forEach(orders::add);
        return orders;
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

        // Terminal states cannot transition
        if (TERMINAL_STATUSES.contains(currentStatus)) {
            throw new ConflictException("Cannot transition from " + currentStatus);
        }

        // From shipped, only delivered is allowed
        if ("shipped".equals(currentStatus) && !"delivered".equals(newStatus)) {
            throw new ConflictException("Cannot transition from shipped to " + newStatus);
        }

        // From pending, any valid status is allowed
        order.setStatus(newStatus);
        container.upsertItem(order, new PartitionKey(order.getCustomerId()), new CosmosItemRequestOptions());
        return order;
    }

    public boolean deleteOrder(String orderId) {
        Order order = getOrder(orderId);
        if (order == null) {
            throw new NotFoundException("Order not found");
        }

        if (!"pending".equals(order.getStatus())) {
            throw new ConflictException("Only pending orders can be deleted");
        }

        container.deleteItem(order.getId(), new PartitionKey(order.getCustomerId()), new CosmosItemRequestOptions());
        return true;
    }

    public static class ConflictException extends RuntimeException {
        public ConflictException(String message) {
            super(message);
        }
    }

    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String message) {
            super(message);
        }
    }
}
