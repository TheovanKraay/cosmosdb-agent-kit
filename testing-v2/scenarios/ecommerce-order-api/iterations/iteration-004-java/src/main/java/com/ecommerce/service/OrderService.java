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

    public OrderService(CosmosContainer container) {
        this.container = container;
    }

    public Order createOrder(Order order) {
        String orderId = UUID.randomUUID().toString();
        order.setId(orderId);
        order.setOrderId(orderId);
        order.setStatus("pending");
        order.setCreatedAt(Instant.now().toString());
        order.setType("order");
        order.setSchemaVersion("1.0");

        double total = 0;
        if (order.getItems() != null) {
            for (var item : order.getItems()) {
                total += item.getQuantity() * item.getUnitPrice();
            }
        }
        order.setTotal(total);

        container.createItem(order, new PartitionKey(order.getCustomerId()), new CosmosItemRequestOptions());
        return order;
    }

    public Order getOrder(String orderId) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.orderId = @orderId",
                Collections.singletonList(new SqlParameter("@orderId", orderId)));
        CosmosPagedIterable<Order> results = container.queryItems(query, new CosmosQueryRequestOptions(), Order.class);
        for (Order o : results) {
            return o;
        }
        return null;
    }

    public List<Order> getCustomerOrders(String customerId) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.customerId = @customerId",
                Collections.singletonList(new SqlParameter("@customerId", customerId)));
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(new PartitionKey(customerId));
        CosmosPagedIterable<Order> results = container.queryItems(query, options, Order.class);
        List<Order> orders = new ArrayList<>();
        for (Order o : results) {
            orders.add(o);
        }
        return orders;
    }

    public CustomerSummary getCustomerSummary(String customerId) {
        List<Order> orders = getCustomerOrders(customerId);
        int totalOrders = orders.size();
        double totalSpent = 0;
        for (Order o : orders) {
            totalSpent += o.getTotal();
        }
        double avg = totalOrders > 0 ? totalSpent / totalOrders : 0;
        return new CustomerSummary(customerId, totalOrders, totalSpent, avg);
    }

    public List<Order> getOrdersByStatus(String status) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.status = @status",
                Collections.singletonList(new SqlParameter("@status", status)));
        CosmosPagedIterable<Order> results = container.queryItems(query, new CosmosQueryRequestOptions(), Order.class);
        List<Order> orders = new ArrayList<>();
        for (Order o : results) {
            orders.add(o);
        }
        return orders;
    }

    public List<Order> getOrdersByDateRange(String startDate, String endDate) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.createdAt >= @startDate AND c.createdAt <= @endDate",
                Arrays.asList(
                        new SqlParameter("@startDate", startDate),
                        new SqlParameter("@endDate", endDate)));
        CosmosPagedIterable<Order> results = container.queryItems(query, new CosmosQueryRequestOptions(), Order.class);
        List<Order> orders = new ArrayList<>();
        for (Order o : results) {
            orders.add(o);
        }
        return orders;
    }

    public Order updateOrderStatus(String orderId, String newStatus) {
        Order order = getOrder(orderId);
        if (order == null) {
            return null;
        }

        String currentStatus = order.getStatus();

        if (!isValidTransition(currentStatus, newStatus)) {
            throw new InvalidStatusTransitionException(
                    "Invalid status transition from " + currentStatus + " to " + newStatus);
        }

        order.setStatus(newStatus);
        container.replaceItem(order, order.getId(), new PartitionKey(order.getCustomerId()),
                new CosmosItemRequestOptions());
        return order;
    }

    public boolean deleteOrder(String orderId) {
        Order order = getOrder(orderId);
        if (order == null) {
            return false;
        }

        if (!"pending".equals(order.getStatus())) {
            throw new InvalidStatusTransitionException("Only pending orders can be deleted");
        }

        container.deleteItem(order.getId(), new PartitionKey(order.getCustomerId()),
                new CosmosItemRequestOptions());
        return true;
    }

    private boolean isValidTransition(String currentStatus, String newStatus) {
        if (!VALID_STATUSES.contains(newStatus)) {
            return false;
        }
        switch (currentStatus) {
            case "pending":
                return true;
            case "shipped":
                return "delivered".equals(newStatus);
            case "delivered":
            case "cancelled":
                return false;
            default:
                return false;
        }
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
