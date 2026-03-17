package com.ecommerce.service;

import com.azure.cosmos.CosmosException;
import com.ecommerce.model.CreateOrderRequest;
import com.ecommerce.model.CustomerSummary;
import com.ecommerce.model.Order;
import com.ecommerce.model.OrderItem;
import com.ecommerce.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Service layer for order business logic.
 *
 * Handles:
 * - Order creation with total calculation
 * - Status transition validation
 * - Customer order summary aggregation
 * - Input validation
 */
@Service
public class OrderService {

    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);

    // Valid status values (Rule 4.17: consistent enum serialization)
    private static final Set<String> VALID_STATUSES = new HashSet<>(
            Arrays.asList("pending", "shipped", "delivered", "cancelled")
    );

    // Valid status transitions (from → allowed set)
    // Tests confirm: pending→any is valid; shipped→delivered only; delivered/cancelled are terminal
    private static final Map<String, Set<String>> VALID_TRANSITIONS = Map.of(
            "pending", new HashSet<>(Arrays.asList("shipped", "delivered", "cancelled")),
            "shipped", new HashSet<>(Arrays.asList("delivered")),
            "delivered", new HashSet<>(),
            "cancelled", new HashSet<>()
    );

    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    /**
     * Create a new order.
     * Auto-calculates total as sum of (quantity * unitPrice) for all items.
     * New orders default to "pending" status.
     */
    public Order createOrder(CreateOrderRequest request) {
        // Input validation
        if (request.getCustomerId() == null || request.getCustomerId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "customerId is required");
        }
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "items are required and must not be empty");
        }

        // Validate each item
        for (OrderItem item : request.getItems()) {
            if (item.getProductId() == null || item.getProductId().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Each item must have a productId");
            }
            if (item.getQuantity() < 1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Item quantity must be >= 1");
            }
            if (item.getUnitPrice() < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Item unitPrice must be >= 0");
            }
        }

        // Calculate total = sum of (quantity * unitPrice) for all items
        double total = request.getItems().stream()
                .mapToDouble(item -> item.getQuantity() * item.getUnitPrice())
                .sum();

        // Round to 2 decimal places to avoid floating point drift
        total = Math.round(total * 100.0) / 100.0;

        // Generate UUID for orderId (Rule 1.4: follow ID constraints)
        String orderId = UUID.randomUUID().toString();

        Order order = Order.builder()
                .id(orderId)
                .orderId(orderId)
                .customerId(request.getCustomerId())
                .status("pending")
                .items(request.getItems())
                .total(total)
                .createdAt(Instant.now().toString()) // ISO-8601 format
                .shippingAddress(request.getShippingAddress())
                .schemaVersion(1)
                .build();

        Order created = orderRepository.createOrder(order);
        logger.info("Created order {} for customer {}", orderId, request.getCustomerId());
        return created;
    }

    /**
     * Get an order by ID (cross-partition query).
     */
    public Order getOrder(String orderId) {
        Order order = orderRepository.findByOrderId(orderId);
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Order not found: " + orderId);
        }
        return order;
    }

    /**
     * Get all orders for a customer (single-partition query).
     */
    public List<Order> getCustomerOrders(String customerId) {
        return orderRepository.findByCustomerId(customerId);
    }

    /**
     * Get aggregated order statistics for a customer.
     * totalSpent = sum of all order totals.
     * averageOrderValue = totalSpent / totalOrders.
     */
    public CustomerSummary getCustomerSummary(String customerId) {
        List<Order> orders = orderRepository.findByCustomerId(customerId);

        int totalOrders = orders.size();
        double totalSpent = orders.stream()
                .mapToDouble(Order::getTotal)
                .sum();

        // Round to 2 decimal places
        totalSpent = Math.round(totalSpent * 100.0) / 100.0;

        double averageOrderValue = totalOrders > 0 ? totalSpent / totalOrders : 0.0;
        averageOrderValue = Math.round(averageOrderValue * 100.0) / 100.0;

        return CustomerSummary.builder()
                .customerId(customerId)
                .totalOrders(totalOrders)
                .totalSpent(totalSpent)
                .averageOrderValue(averageOrderValue)
                .build();
    }

    /**
     * Query orders by status (cross-partition query).
     */
    public List<Order> getOrdersByStatus(String status) {
        if (!VALID_STATUSES.contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid status: " + status + ". Must be one of: pending, shipped, delivered, cancelled");
        }
        return orderRepository.findByStatus(status);
    }

    /**
     * Query orders by date range (cross-partition query).
     * Dates are ISO-8601 strings for lexicographic comparison.
     */
    public List<Order> getOrdersByDateRange(String startDate, String endDate) {
        if (startDate == null || endDate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Both startDate and endDate are required");
        }
        // Normalize dates: if just a date (YYYY-MM-DD), add time boundaries
        String normalizedStart = normalizeStartDate(startDate);
        String normalizedEnd = normalizeEndDate(endDate);

        return orderRepository.findByDateRange(normalizedStart, normalizedEnd);
    }

    /**
     * Update the status of an order with transition validation.
     * Uses ETag for optimistic concurrency (Rule 4.7).
     *
     * Valid transitions:
     * - pending → shipped, delivered, cancelled
     * - shipped → delivered
     * - delivered → (terminal, no transitions)
     * - cancelled → (terminal, no transitions)
     */
    public Order updateOrderStatus(String orderId, String newStatus) {
        // Validate new status value (Rule 4.17: consistent enum values)
        if (newStatus == null || !VALID_STATUSES.contains(newStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid status value: " + newStatus);
        }

        Order order = orderRepository.findByOrderId(orderId);
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Order not found: " + orderId);
        }

        String currentStatus = order.getStatus();

        // Validate transition
        Set<String> allowedTransitions = VALID_TRANSITIONS.getOrDefault(currentStatus, new HashSet<>());
        if (!allowedTransitions.contains(newStatus)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Invalid status transition from '" + currentStatus + "' to '" + newStatus + "'");
        }

        order.setStatus(newStatus);
        Order updated = orderRepository.updateOrder(order);
        logger.info("Updated order {} status from {} to {}", orderId, currentStatus, newStatus);
        return updated;
    }

    /**
     * Delete an order (only pending orders can be deleted).
     */
    public void deleteOrder(String orderId) {
        Order order = orderRepository.findByOrderId(orderId);
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Order not found: " + orderId);
        }
        if (!"pending".equals(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only pending orders can be deleted. Order status is: " + order.getStatus());
        }
        orderRepository.deleteOrder(order.getId(), order.getCustomerId());
        logger.info("Deleted order {}", orderId);
    }

    /**
     * Normalize a date string to include time for start of day comparison.
     * "2024-01-01" → "2024-01-01T00:00:00Z"
     */
    private String normalizeStartDate(String date) {
        if (date.length() == 10) { // YYYY-MM-DD format
            return date + "T00:00:00Z";
        }
        return date;
    }

    /**
     * Normalize a date string to include time for end of day comparison.
     * "2024-12-31" → "2024-12-31T23:59:59Z"
     */
    private String normalizeEndDate(String date) {
        if (date.length() == 10) { // YYYY-MM-DD format
            return date + "T23:59:59Z";
        }
        return date;
    }
}
