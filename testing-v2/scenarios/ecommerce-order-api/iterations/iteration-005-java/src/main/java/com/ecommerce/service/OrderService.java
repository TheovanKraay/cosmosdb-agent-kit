package com.ecommerce.service;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.ecommerce.model.CustomerSummary;
import com.ecommerce.model.Order;
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

    private final CosmosContainer ordersContainer;

    private static final Set<String> VALID_STATUSES = new HashSet<>(Arrays.asList(
            "pending", "shipped", "delivered", "cancelled"
    ));

    public OrderService(CosmosContainer ordersContainer) {
        this.ordersContainer = ordersContainer;
    }

    public Order createOrder(Order orderRequest) {
        String orderId = UUID.randomUUID().toString();
        orderRequest.setId(orderId);
        orderRequest.setOrderId(orderId);
        orderRequest.setStatus("pending");
        orderRequest.setCreatedAt(Instant.now().toString());
        orderRequest.setType("order");
        orderRequest.setSchemaVersion("1.0");

        double total = 0.0;
        if (orderRequest.getItems() != null) {
            for (var item : orderRequest.getItems()) {
                total += item.getQuantity() * item.getUnitPrice();
            }
        }
        orderRequest.setTotal(total);

        ordersContainer.createItem(orderRequest, new PartitionKey(orderRequest.getCustomerId()), new CosmosItemRequestOptions());
        return orderRequest;
    }

    public Order getOrder(String orderId) {
        String query = "SELECT * FROM c WHERE c.orderId = @orderId";
        SqlQuerySpec querySpec = new SqlQuerySpec(query,
                List.of(new SqlParameter("@orderId", orderId)));

        List<Order> results = ordersContainer.queryItems(querySpec, new CosmosQueryRequestOptions(), Order.class)
                .stream().toList();

        if (results.isEmpty()) {
            return null;
        }
        return results.get(0);
    }

    public List<Order> getCustomerOrders(String customerId) {
        String query = "SELECT * FROM c WHERE c.customerId = @customerId";
        SqlQuerySpec querySpec = new SqlQuerySpec(query,
                List.of(new SqlParameter("@customerId", customerId)));

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(new PartitionKey(customerId));

        return ordersContainer.queryItems(querySpec, options, Order.class)
                .stream().toList();
    }

    public CustomerSummary getCustomerSummary(String customerId) {
        List<Order> orders = getCustomerOrders(customerId);
        int totalOrders = orders.size();
        double totalSpent = orders.stream().mapToDouble(Order::getTotal).sum();
        double averageOrderValue = totalOrders > 0 ? totalSpent / totalOrders : 0.0;

        return new CustomerSummary(customerId, totalOrders, totalSpent, averageOrderValue);
    }

    public List<Order> getOrdersByStatus(String status) {
        String query = "SELECT * FROM c WHERE c.status = @status";
        SqlQuerySpec querySpec = new SqlQuerySpec(query,
                List.of(new SqlParameter("@status", status)));

        return ordersContainer.queryItems(querySpec, new CosmosQueryRequestOptions(), Order.class)
                .stream().toList();
    }

    public List<Order> getOrdersByDateRange(String startDate, String endDate) {
        String query = "SELECT * FROM c WHERE c.createdAt >= @startDate AND c.createdAt <= @endDate";
        SqlQuerySpec querySpec = new SqlQuerySpec(query,
                Arrays.asList(
                        new SqlParameter("@startDate", startDate),
                        new SqlParameter("@endDate", endDate)
                ));

        return ordersContainer.queryItems(querySpec, new CosmosQueryRequestOptions(), Order.class)
                .stream().toList();
    }

    public Order updateOrderStatus(String orderId, String newStatus) {
        Order order = getOrder(orderId);
        if (order == null) {
            return null;
        }

        if (!isValidTransition(order.getStatus(), newStatus)) {
            throw new InvalidStatusTransitionException("Invalid status transition from " + order.getStatus() + " to " + newStatus);
        }

        order.setStatus(newStatus);
        ordersContainer.replaceItem(order, order.getId(), new PartitionKey(order.getCustomerId()), new CosmosItemRequestOptions());
        return order;
    }

    public boolean deleteOrder(String orderId) {
        Order order = getOrder(orderId);
        if (order == null) {
            throw new OrderNotFoundException("Order not found: " + orderId);
        }

        if (!"pending".equals(order.getStatus())) {
            throw new InvalidStatusTransitionException("Only pending orders can be deleted");
        }

        ordersContainer.deleteItem(order.getId(), new PartitionKey(order.getCustomerId()), new CosmosItemRequestOptions());
        return true;
    }

    private boolean isValidTransition(String currentStatus, String newStatus) {
        if (!VALID_STATUSES.contains(newStatus)) {
            return false;
        }

        switch (currentStatus) {
            case "pending":
                return "shipped".equals(newStatus) || "cancelled".equals(newStatus) || "delivered".equals(newStatus);
            case "shipped":
                return "delivered".equals(newStatus);
            case "delivered":
            case "cancelled":
                return false;
            default:
                return false;
        }
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
