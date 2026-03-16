package com.example.ecommerce.service;

import com.example.ecommerce.dto.CreateOrderRequest;
import com.example.ecommerce.dto.CustomerSummaryResponse;
import com.example.ecommerce.dto.OrderItemRequest;
import com.example.ecommerce.dto.OrderResponse;
import com.example.ecommerce.model.Order;
import com.example.ecommerce.model.OrderItem;
import com.example.ecommerce.repository.OrderRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Business logic for order management.
 *
 * Status transition rules:
 * - Terminal states "delivered" and "cancelled" cannot be further updated (409)
 * - Non-terminal states can transition to any valid target status
 * - Valid transitions: pending→{shipped,cancelled,delivered}, shipped→delivered
 */
@Service
public class OrderService {

    /** Valid status values */
    private static final Set<String> VALID_STATUSES = Set.of("pending", "shipped", "delivered", "cancelled");

    /** Terminal states — no further transitions allowed (AGENTS.md rule 4.7) */
    private static final Set<String> TERMINAL_STATUSES = Set.of("delivered", "cancelled");

    private final OrderRepository repository;

    public OrderService(OrderRepository repository) {
        this.repository = repository;
    }

    /**
     * Create a new order.
     * Calculates total as sum of (quantity * unitPrice) per the API contract.
     * Sets status to "pending" for all new orders.
     */
    public OrderResponse createOrder(CreateOrderRequest request) {
        List<OrderItem> items = request.getItems().stream()
                .map(i -> new OrderItem(i.getProductId(), i.getProductName(), i.getQuantity(), i.getUnitPrice()))
                .collect(Collectors.toList());

        // Calculate total: sum of (quantity * unitPrice) for all items
        double total = items.stream()
                .mapToDouble(item -> item.getQuantity() * item.getUnitPrice())
                .sum();

        Order order = new Order();
        order.setId(UUID.randomUUID().toString());
        order.setCustomerId(request.getCustomerId());
        order.setStatus("pending");
        order.setItems(items);
        order.setTotal(total);
        order.setCreatedAt(Instant.now().toString());
        order.setShippingAddress(request.getShippingAddress());

        Order saved = repository.save(order);
        return OrderResponse.from(saved);
    }

    /**
     * Get order by ID. Returns null if not found.
     */
    public OrderResponse getOrderById(String orderId) {
        Order order = repository.findById(orderId);
        return order != null ? OrderResponse.from(order) : null;
    }

    /**
     * Get all orders for a customer (single-partition query).
     */
    public List<OrderResponse> getOrdersByCustomer(String customerId) {
        return repository.findByCustomerId(customerId).stream()
                .map(OrderResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * Get aggregated order statistics for a customer.
     */
    public CustomerSummaryResponse getCustomerSummary(String customerId) {
        List<Order> orders = repository.findByCustomerId(customerId);
        double totalSpent = orders.stream().mapToDouble(Order::getTotal).sum();
        return new CustomerSummaryResponse(customerId, orders.size(), totalSpent);
    }

    /**
     * Query orders by status (cross-partition admin query).
     */
    public List<OrderResponse> getOrdersByStatus(String status) {
        return repository.findByStatus(status).stream()
                .map(OrderResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * Query orders within a date range (cross-partition admin query).
     */
    public List<OrderResponse> getOrdersByDateRange(String startDate, String endDate) {
        return repository.findByDateRange(startDate, endDate).stream()
                .map(OrderResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * Update order status with transition validation and ETag-based retry.
     *
     * @param orderId   target order
     * @param newStatus desired new status
     * @return updated order, or null if not found
     * @throws InvalidTransitionException if the transition is not allowed
     */
    public OrderResponse updateOrderStatus(String orderId, String newStatus) {
        // Find the current order to validate the transition
        Order existing = repository.findById(orderId);
        if (existing == null) {
            return null;
        }

        String currentStatus = existing.getStatus();

        // Terminal states cannot be updated (delivered and cancelled are final)
        if (TERMINAL_STATUSES.contains(currentStatus)) {
            throw new InvalidTransitionException(
                    "Order " + orderId + " has terminal status '" + currentStatus + "' and cannot be updated");
        }

        // Shipped state can only transition to delivered
        if ("shipped".equals(currentStatus) && !"delivered".equals(newStatus)) {
            throw new InvalidTransitionException(
                    "Invalid transition from '" + currentStatus + "' to '" + newStatus + "'");
        }

        // Retry loop for ETag conflicts (AGENTS.md 4.7)
        final int maxRetries = 3;
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                Order updated = repository.updateStatus(orderId, newStatus);
                return updated != null ? OrderResponse.from(updated) : null;
            } catch (com.azure.cosmos.CosmosException ex) {
                if (ex.getStatusCode() == 412 && attempt < maxRetries - 1) {
                    // HTTP 412 Precondition Failed: concurrent modification — retry
                    continue;
                }
                throw ex;
            }
        }

        return null;
    }

    /**
     * Delete an order (only pending orders can be deleted).
     *
     * @return true if deleted, false if not found
     * @throws IllegalStateException if order is not pending
     */
    public boolean deleteOrder(String orderId) {
        Order order = repository.findById(orderId);
        if (order == null) {
            return false;
        }

        if (!"pending".equals(order.getStatus())) {
            throw new NonPendingDeleteException(
                    "Order " + orderId + " has status '" + order.getStatus() + "' and cannot be deleted");
        }

        return repository.delete(orderId);
    }

    /**
     * Validate that a status value is a known status.
     */
    public boolean isValidStatus(String status) {
        return status != null && VALID_STATUSES.contains(status);
    }

    /** Thrown when an invalid status transition is attempted (HTTP 409). */
    public static class InvalidTransitionException extends RuntimeException {
        public InvalidTransitionException(String message) { super(message); }
    }

    /** Thrown when trying to delete a non-pending order (HTTP 409). */
    public static class NonPendingDeleteException extends RuntimeException {
        public NonPendingDeleteException(String message) { super(message); }
    }
}
