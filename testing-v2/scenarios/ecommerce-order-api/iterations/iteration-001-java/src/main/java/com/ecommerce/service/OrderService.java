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
import com.ecommerce.model.CreateOrderRequest;
import com.ecommerce.model.CustomerOrderSummary;
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

    public Order createOrder(CreateOrderRequest request) {
        String orderId = UUID.randomUUID().toString();
        double total = 0;
        for (OrderItem item : request.getItems()) {
            total += item.getQuantity() * item.getUnitPrice();
        }

        Order order = new Order();
        order.setId(orderId);
        order.setOrderId(orderId);
        order.setCustomerId(request.getCustomerId());
        order.setStatus("pending");
        order.setItems(request.getItems());
        order.setTotal(total);
        order.setCreatedAt(Instant.now().toString());
        order.setShippingAddress(request.getShippingAddress());

        container.createItem(order, new PartitionKey(order.getCustomerId()), new CosmosItemRequestOptions());
        logger.info("Created order: {} for customer: {}", orderId, request.getCustomerId());
        return order;
    }

    public Order getOrder(String orderId) {
        SqlQuerySpec querySpec = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.orderId = @orderId",
                Arrays.asList(new SqlParameter("@orderId", orderId)));

        CosmosPagedIterable<Order> results = container.queryItems(querySpec, new CosmosQueryRequestOptions(), Order.class);

        for (Order order : results) {
            return order;
        }
        return null;
    }

    public List<Order> getCustomerOrders(String customerId) {
        SqlQuerySpec querySpec = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.customerId = @customerId",
                Arrays.asList(new SqlParameter("@customerId", customerId)));

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(new PartitionKey(customerId));

        CosmosPagedIterable<Order> results = container.queryItems(querySpec, options, Order.class);

        List<Order> orders = new ArrayList<>();
        for (Order order : results) {
            orders.add(order);
        }
        return orders;
    }

    public CustomerOrderSummary getCustomerSummary(String customerId) {
        List<Order> orders = getCustomerOrders(customerId);

        int totalOrders = orders.size();
        double totalSpent = 0;
        for (Order order : orders) {
            totalSpent += order.getTotal();
        }
        double averageOrderValue = totalOrders > 0 ? totalSpent / totalOrders : 0;

        return new CustomerOrderSummary(customerId, totalOrders, totalSpent, averageOrderValue);
    }

    public List<Order> queryByStatus(String status) {
        SqlQuerySpec querySpec = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.status = @status",
                Arrays.asList(new SqlParameter("@status", status)));

        CosmosPagedIterable<Order> results = container.queryItems(querySpec, new CosmosQueryRequestOptions(), Order.class);

        List<Order> orders = new ArrayList<>();
        for (Order order : results) {
            orders.add(order);
        }
        return orders;
    }

    public List<Order> queryByDateRange(String startDate, String endDate) {
        SqlQuerySpec querySpec = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.createdAt >= @startDate AND c.createdAt <= @endDate",
                Arrays.asList(
                        new SqlParameter("@startDate", startDate),
                        new SqlParameter("@endDate", endDate)));

        CosmosPagedIterable<Order> results = container.queryItems(querySpec, new CosmosQueryRequestOptions(), Order.class);

        List<Order> orders = new ArrayList<>();
        for (Order order : results) {
            orders.add(order);
        }
        return orders;
    }

    public Order updateOrderStatus(String orderId, String newStatus) {
        Order order = getOrder(orderId);
        if (order == null) {
            return null;
        }

        if (!isValidTransition(order.getStatus(), newStatus)) {
            throw new InvalidStatusTransitionException(
                    "Invalid transition from " + order.getStatus() + " to " + newStatus);
        }

        order.setStatus(newStatus);
        container.replaceItem(order, order.getId(), new PartitionKey(order.getCustomerId()), new CosmosItemRequestOptions());
        logger.info("Updated order {} status to {}", orderId, newStatus);
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

        container.deleteItem(order.getId(), new PartitionKey(order.getCustomerId()), new CosmosItemRequestOptions());
        logger.info("Deleted order: {}", orderId);
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
