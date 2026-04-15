package com.ecommerce.order.controller;

import com.azure.cosmos.CosmosException;
import com.ecommerce.order.model.CreateOrderRequest;
import com.ecommerce.order.model.CustomerSummary;
import com.ecommerce.order.model.Order;
import com.ecommerce.order.model.OrderItem;
import com.ecommerce.order.model.UpdateStatusRequest;
import com.ecommerce.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * REST controller implementing the e-commerce order API contract.
 *
 * Endpoints:
 *   POST   /api/orders                           - Create order
 *   GET    /api/orders/{orderId}                  - Get order by ID
 *   GET    /api/orders?status=X                   - Query by status
 *   GET    /api/orders?startDate=X&endDate=Y      - Query by date range
 *   GET    /api/customers/{customerId}/orders      - Customer order history
 *   GET    /api/customers/{customerId}/orders/summary - Customer summary
 *   PATCH  /api/orders/{orderId}/status           - Update order status
 *   DELETE /api/orders/{orderId}                  - Delete order (pending only)
 */
@RestController
public class OrderController {

    private static final Logger logger = LoggerFactory.getLogger(OrderController.class);
    private static final Set<String> VALID_STATUSES = Set.of("pending", "shipped", "delivered", "cancelled");

    private final OrderRepository orderRepository;

    public OrderController(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    /**
     * POST /api/orders - Create a new order.
     * Auto-calculates total as sum of (quantity * unitPrice) for all items.
     * New orders default to "pending" status.
     */
    @PostMapping("/api/orders")
    public ResponseEntity<?> createOrder(@RequestBody CreateOrderRequest request) {
        // Validate required fields
        if (request.getCustomerId() == null || request.getCustomerId().isBlank()) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "customerId is required"));
        }
        if (request.getItems() == null || request.getItems().isEmpty()) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "items is required and must not be empty"));
        }

        // Validate each item
        for (OrderItem item : request.getItems()) {
            if (item.getProductId() == null || item.getProductId().isBlank()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Each item must have a productId"));
            }
            if (item.getProductName() == null || item.getProductName().isBlank()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Each item must have a productName"));
            }
            if (item.getQuantity() < 1) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Each item quantity must be >= 1"));
            }
            if (item.getUnitPrice() < 0) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Each item unitPrice must be >= 0"));
            }
        }

        // Calculate total
        double total = 0;
        for (OrderItem item : request.getItems()) {
            total += item.getQuantity() * item.getUnitPrice();
        }
        // Round to 2 decimal places to avoid floating-point drift
        total = Math.round(total * 100.0) / 100.0;

        String orderId = UUID.randomUUID().toString();
        Order order = new Order();
        order.setId(orderId);
        order.setOrderId(orderId);
        order.setCustomerId(request.getCustomerId());
        order.setItems(request.getItems());
        order.setTotal(total);
        order.setStatus("pending");
        order.setCreatedAt(Instant.now().toString());
        order.setShippingAddress(request.getShippingAddress());

        try {
            Order created = orderRepository.createOrder(order);
            // If contentResponseOnWriteEnabled returns the item, use it; otherwise use our local
            if (created == null) {
                created = order;
            }
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (CosmosException e) {
            logger.error("Failed to create order: {}", e.getMessage());
            return ResponseEntity.status(e.getStatusCode())
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * GET /api/orders/{orderId} - Get order by ID.
     */
    @GetMapping("/api/orders/{orderId}")
    public ResponseEntity<?> getOrder(@PathVariable String orderId) {
        try {
            Order order = orderRepository.getOrderById(orderId);
            if (order == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Order not found"));
            }
            return ResponseEntity.ok(order);
        } catch (CosmosException e) {
            if (e.getStatusCode() == 404) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Order not found"));
            }
            logger.error("Failed to get order: {}", e.getMessage());
            return ResponseEntity.status(e.getStatusCode())
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * GET /api/orders?status=X or GET /api/orders?startDate=X&endDate=Y
     * Query orders by status or date range.
     */
    @GetMapping("/api/orders")
    public ResponseEntity<?> queryOrders(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        try {
            if (status != null && !status.isBlank()) {
                List<Order> orders = orderRepository.getOrdersByStatus(status);
                return ResponseEntity.ok(orders);
            }
            if (startDate != null && endDate != null) {
                List<Order> orders = orderRepository.getOrdersByDateRange(startDate, endDate);
                return ResponseEntity.ok(orders);
            }
            // No filters provided - return empty list
            return ResponseEntity.ok(List.of());
        } catch (CosmosException e) {
            logger.error("Failed to query orders: {}", e.getMessage());
            return ResponseEntity.status(e.getStatusCode())
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * GET /api/customers/{customerId}/orders - Customer order history.
     */
    @GetMapping("/api/customers/{customerId}/orders")
    public ResponseEntity<?> getCustomerOrders(@PathVariable String customerId) {
        try {
            List<Order> orders = orderRepository.getOrdersByCustomerId(customerId);
            return ResponseEntity.ok(orders);
        } catch (CosmosException e) {
            logger.error("Failed to get customer orders: {}", e.getMessage());
            return ResponseEntity.status(e.getStatusCode())
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * GET /api/customers/{customerId}/orders/summary - Customer order summary.
     * Returns totalOrders, totalSpent, averageOrderValue.
     */
    @GetMapping("/api/customers/{customerId}/orders/summary")
    public ResponseEntity<?> getCustomerSummary(@PathVariable String customerId) {
        try {
            List<Order> orders = orderRepository.getOrdersByCustomerId(customerId);
            int totalOrders = orders.size();
            double totalSpent = 0;
            for (Order order : orders) {
                totalSpent += order.getTotal();
            }
            // Round to avoid floating-point drift
            totalSpent = Math.round(totalSpent * 100.0) / 100.0;
            double averageOrderValue = totalOrders > 0 ? totalSpent / totalOrders : 0;

            CustomerSummary summary = new CustomerSummary(
                customerId, totalOrders, totalSpent, averageOrderValue);
            return ResponseEntity.ok(summary);
        } catch (CosmosException e) {
            logger.error("Failed to get customer summary: {}", e.getMessage());
            return ResponseEntity.status(e.getStatusCode())
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * PATCH /api/orders/{orderId}/status - Update order status.
     * Valid transitions: pending→shipped, pending→cancelled, shipped→delivered.
     * Invalid transitions return 409.
     */
    @PatchMapping("/api/orders/{orderId}/status")
    public ResponseEntity<?> updateOrderStatus(
            @PathVariable String orderId,
            @RequestBody UpdateStatusRequest request) {
        if (request.getStatus() == null || !VALID_STATUSES.contains(request.getStatus())) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Invalid status. Must be one of: " + VALID_STATUSES));
        }

        try {
            Order order = orderRepository.getOrderById(orderId);
            if (order == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Order not found"));
            }

            String currentStatus = order.getStatus();
            String newStatus = request.getStatus();

            // Validate status transition
            if (!isValidTransition(currentStatus, newStatus)) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error",
                        String.format("Invalid status transition from '%s' to '%s'", currentStatus, newStatus)));
            }

            order.setStatus(newStatus);
            Order updated = orderRepository.replaceOrder(order);
            if (updated == null) {
                updated = order;
            }
            return ResponseEntity.ok(updated);
        } catch (CosmosException e) {
            if (e.getStatusCode() == 404) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Order not found"));
            }
            logger.error("Failed to update order status: {}", e.getMessage());
            return ResponseEntity.status(e.getStatusCode())
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * DELETE /api/orders/{orderId} - Delete a pending order.
     * Only pending orders can be deleted; non-pending returns 409.
     */
    @DeleteMapping("/api/orders/{orderId}")
    public ResponseEntity<?> deleteOrder(@PathVariable String orderId) {
        try {
            Order order = orderRepository.getOrderById(orderId);
            if (order == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Order not found"));
            }

            if (!"pending".equals(order.getStatus())) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Only pending orders can be deleted"));
            }

            orderRepository.deleteOrder(order.getId(), order.getCustomerId());
            return ResponseEntity.noContent().build();
        } catch (CosmosException e) {
            if (e.getStatusCode() == 404) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Order not found"));
            }
            logger.error("Failed to delete order: {}", e.getMessage());
            return ResponseEntity.status(e.getStatusCode())
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Validate status transitions.
     * Allowed: pending→shipped, pending→cancelled, shipped→delivered.
     * All others are invalid.
     */
    private boolean isValidTransition(String from, String to) {
        if (from.equals(to)) {
            return false;
        }
        return switch (from) {
            case "pending" -> "shipped".equals(to) || "cancelled".equals(to);
            case "shipped" -> "delivered".equals(to);
            default -> false;
        };
    }
}
