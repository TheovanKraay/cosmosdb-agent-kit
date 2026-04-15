package com.ecommerce.orderapi.controller;

import com.ecommerce.orderapi.model.CreateOrderRequest;
import com.ecommerce.orderapi.model.CustomerSummary;
import com.ecommerce.orderapi.model.Order;
import com.ecommerce.orderapi.model.UpdateStatusRequest;
import com.ecommerce.orderapi.repository.OrderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * REST controller for order management.
 *
 * Implements the full API contract:
 * - POST   /api/orders
 * - GET    /api/orders/{orderId}
 * - GET    /api/orders?status=X
 * - GET    /api/orders?startDate=X&endDate=Y
 * - PATCH  /api/orders/{orderId}/status
 * - DELETE /api/orders/{orderId}
 * - GET    /api/customers/{customerId}/orders
 * - GET    /api/customers/{customerId}/orders/summary
 */
@RestController
@RequestMapping("/api")
public class OrderController {

    private static final Set<String> VALID_STATUSES = Set.of("pending", "shipped", "delivered", "cancelled");

    private final OrderRepository orderRepository;

    public OrderController(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    /**
     * POST /api/orders — Create a new order.
     * Auto-calculates total as sum of (quantity × unitPrice) for all items.
     * Defaults status to "pending".
     */
    @PostMapping("/orders")
    public ResponseEntity<?> createOrder(@RequestBody(required = false) CreateOrderRequest request) {
        // Input validation
        if (request == null || request.getCustomerId() == null || request.getCustomerId().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "customerId is required"));
        }
        if (request.getItems() == null || request.getItems().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "items array is required and must not be empty"));
        }

        // Build order
        String orderId = UUID.randomUUID().toString();
        Order order = new Order();
        order.setId(orderId);
        order.setOrderId(orderId);
        order.setCustomerId(request.getCustomerId());
        order.setStatus("pending");
        order.setItems(request.getItems());
        order.setShippingAddress(request.getShippingAddress());
        order.setCreatedAt(Instant.now().toString());

        // Calculate total: sum of (quantity * unitPrice) for each item
        double total = request.getItems().stream()
                .mapToDouble(item -> item.getQuantity() * item.getUnitPrice())
                .sum();
        // Round to 2 decimal places to avoid floating point issues
        total = Math.round(total * 100.0) / 100.0;
        order.setTotal(total);

        Order created = orderRepository.createOrder(order);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * GET /api/orders/{orderId} — Get order by ID.
     */
    @GetMapping("/orders/{orderId}")
    public ResponseEntity<?> getOrder(@PathVariable String orderId) {
        Order order = orderRepository.getOrderById(orderId);
        if (order == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Order not found"));
        }
        return ResponseEntity.ok(order);
    }

    /**
     * GET /api/orders?status=X — Query orders by status.
     * GET /api/orders?startDate=X&endDate=Y — Query orders by date range.
     */
    @GetMapping("/orders")
    public ResponseEntity<?> queryOrders(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        if (status != null && !status.isBlank()) {
            List<Order> orders = orderRepository.getOrdersByStatus(status);
            return ResponseEntity.ok(orders);
        }

        if (startDate != null && endDate != null) {
            List<Order> orders = orderRepository.getOrdersByDateRange(startDate, endDate);
            return ResponseEntity.ok(orders);
        }

        // No filter — return empty list or all orders
        return ResponseEntity.ok(List.of());
    }

    /**
     * PATCH /api/orders/{orderId}/status — Update order status.
     * Valid transitions: pending→shipped, pending→cancelled, shipped→delivered.
     * Invalid transitions return 409.
     */
    @PatchMapping("/orders/{orderId}/status")
    public ResponseEntity<?> updateOrderStatus(@PathVariable String orderId,
                                                @RequestBody(required = false) UpdateStatusRequest request) {
        if (request == null || request.getStatus() == null || request.getStatus().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "status is required"));
        }

        String newStatus = request.getStatus();
        if (!VALID_STATUSES.contains(newStatus)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid status value: " + newStatus));
        }

        Order order = orderRepository.getOrderById(orderId);
        if (order == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Order not found"));
        }

        // Validate status transition
        String currentStatus = order.getStatus();
        if (!isValidTransition(currentStatus, newStatus)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error",
                            "Invalid status transition from '" + currentStatus + "' to '" + newStatus + "'"));
        }

        order.setStatus(newStatus);
        Order updated = orderRepository.replaceOrder(order);
        return ResponseEntity.ok(updated);
    }

    /**
     * DELETE /api/orders/{orderId} — Delete an order (pending only).
     */
    @DeleteMapping("/orders/{orderId}")
    public ResponseEntity<?> deleteOrder(@PathVariable String orderId) {
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
    }

    /**
     * GET /api/customers/{customerId}/orders — Get customer order history.
     */
    @GetMapping("/customers/{customerId}/orders")
    public ResponseEntity<?> getCustomerOrders(@PathVariable String customerId) {
        List<Order> orders = orderRepository.getOrdersByCustomerId(customerId);
        return ResponseEntity.ok(orders);
    }

    /**
     * GET /api/customers/{customerId}/orders/summary — Customer order summary.
     * Returns totalOrders, totalSpent, averageOrderValue.
     */
    @GetMapping("/customers/{customerId}/orders/summary")
    public ResponseEntity<?> getCustomerSummary(@PathVariable String customerId) {
        List<Order> orders = orderRepository.getOrdersByCustomerId(customerId);

        int totalOrders = orders.size();
        double totalSpent = orders.stream().mapToDouble(Order::getTotal).sum();
        // Round to avoid floating point drift
        totalSpent = Math.round(totalSpent * 100.0) / 100.0;
        double averageOrderValue = totalOrders > 0 ? Math.round((totalSpent / totalOrders) * 100.0) / 100.0 : 0.0;

        CustomerSummary summary = new CustomerSummary(customerId, totalOrders, totalSpent, averageOrderValue);
        return ResponseEntity.ok(summary);
    }

    /**
     * Valid status transitions:
     * pending → shipped
     * pending → cancelled
     * shipped → delivered
     * All others are invalid.
     */
    private boolean isValidTransition(String current, String next) {
        if ("pending".equals(current) && ("shipped".equals(next) || "cancelled".equals(next))) {
            return true;
        }
        if ("shipped".equals(current) && "delivered".equals(next)) {
            return true;
        }
        return false;
    }
}
