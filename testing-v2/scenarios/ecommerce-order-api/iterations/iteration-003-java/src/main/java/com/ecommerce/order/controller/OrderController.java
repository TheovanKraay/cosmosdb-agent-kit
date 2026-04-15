package com.ecommerce.order.controller;

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
import java.util.*;

/**
 * REST controller for order management.
 * Implements all endpoints defined in api-contract.yaml.
 */
@RestController
@RequestMapping("/api")
public class OrderController {

    private static final Logger logger = LoggerFactory.getLogger(OrderController.class);

    // Valid status transitions: only these are allowed
    private static final Map<String, Set<String>> VALID_TRANSITIONS = Map.of(
            "pending", Set.of("shipped", "cancelled", "delivered"),
            "shipped", Set.of("delivered")
    );

    private final OrderRepository orderRepository;

    public OrderController(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    /**
     * POST /api/orders — Create a new order with items.
     * Auto-calculates total as sum of (quantity * unitPrice).
     * New orders default to "pending" status.
     */
    @PostMapping("/orders")
    public ResponseEntity<?> createOrder(@RequestBody CreateOrderRequest request) {
        try {
            if (request.getCustomerId() == null || request.getItems() == null || request.getItems().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "customerId and items are required"));
            }

            String orderId = UUID.randomUUID().toString();
            Order order = new Order();
            order.setId(orderId);
            order.setOrderId(orderId);
            order.setCustomerId(request.getCustomerId());
            order.setItems(request.getItems());
            order.setStatus("pending");
            order.setCreatedAt(Instant.now().toString());
            order.setShippingAddress(request.getShippingAddress());

            // Auto-calculate total: sum of (quantity * unitPrice)
            double total = 0;
            for (OrderItem item : request.getItems()) {
                total += item.getQuantity() * item.getUnitPrice();
            }
            order.setTotal(total);

            Order createdOrder = orderRepository.createOrder(order);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdOrder);
        } catch (Exception e) {
            logger.error("Error creating order", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to create order"));
        }
    }

    /**
     * GET /api/orders/{orderId} — Get a specific order by ID.
     * Returns 404 if not found.
     */
    @GetMapping("/orders/{orderId}")
    public ResponseEntity<?> getOrder(@PathVariable String orderId) {
        try {
            Optional<Order> order = orderRepository.getOrderById(orderId);
            if (order.isPresent()) {
                return ResponseEntity.ok(order.get());
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Order not found"));
        } catch (Exception e) {
            logger.error("Error getting order {}", orderId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to get order"));
        }
    }

    /**
     * GET /api/customers/{customerId}/orders — Get all orders for a customer.
     */
    @GetMapping("/customers/{customerId}/orders")
    public ResponseEntity<?> getCustomerOrders(@PathVariable String customerId) {
        try {
            List<Order> orders = orderRepository.getOrdersByCustomerId(customerId);
            return ResponseEntity.ok(orders);
        } catch (Exception e) {
            logger.error("Error getting orders for customer {}", customerId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to get customer orders"));
        }
    }

    /**
     * GET /api/customers/{customerId}/orders/summary — Customer order summary.
     * Returns totalOrders, totalSpent, averageOrderValue.
     */
    @GetMapping("/customers/{customerId}/orders/summary")
    public ResponseEntity<?> getCustomerSummary(@PathVariable String customerId) {
        try {
            List<Order> orders = orderRepository.getOrdersByCustomerId(customerId);
            int totalOrders = orders.size();
            double totalSpent = orders.stream().mapToDouble(Order::getTotal).sum();
            double averageOrderValue = totalOrders > 0 ? totalSpent / totalOrders : 0;

            CustomerSummary summary = new CustomerSummary(
                    customerId, totalOrders, totalSpent, averageOrderValue);
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            logger.error("Error getting summary for customer {}", customerId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to get customer summary"));
        }
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
        try {
            if (status != null) {
                List<Order> orders = orderRepository.getOrdersByStatus(status);
                return ResponseEntity.ok(orders);
            }
            if (startDate != null && endDate != null) {
                List<Order> orders = orderRepository.getOrdersByDateRange(startDate, endDate);
                return ResponseEntity.ok(orders);
            }
            // No filters — return empty array
            return ResponseEntity.ok(Collections.emptyList());
        } catch (Exception e) {
            logger.error("Error querying orders", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to query orders"));
        }
    }

    /**
     * PATCH /api/orders/{orderId}/status — Update order status.
     * Valid transitions: pending→shipped, pending→cancelled, shipped→delivered.
     * Invalid transitions return 409.
     */
    @PatchMapping("/orders/{orderId}/status")
    public ResponseEntity<?> updateOrderStatus(
            @PathVariable String orderId,
            @RequestBody UpdateStatusRequest request) {
        try {
            Optional<Order> existingOpt = orderRepository.getOrderById(orderId);
            if (existingOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Order not found"));
            }

            Order existing = existingOpt.get();
            String currentStatus = existing.getStatus();
            String newStatus = request.getStatus();

            // Validate status transition
            Set<String> allowedTransitions = VALID_TRANSITIONS.getOrDefault(currentStatus, Collections.emptySet());
            if (!allowedTransitions.contains(newStatus)) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("error",
                                "Invalid status transition from " + currentStatus + " to " + newStatus));
            }

            existing.setStatus(newStatus);
            Order updated = orderRepository.replaceOrder(existing);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            logger.error("Error updating order status {}", orderId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update order status"));
        }
    }

    /**
     * DELETE /api/orders/{orderId} — Delete an order.
     * Only pending orders can be deleted; non-pending returns 409.
     * Returns 204 on success, 404 if not found.
     */
    @DeleteMapping("/orders/{orderId}")
    public ResponseEntity<?> deleteOrder(@PathVariable String orderId) {
        try {
            Optional<Order> existingOpt = orderRepository.getOrderById(orderId);
            if (existingOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Order not found"));
            }

            Order existing = existingOpt.get();
            if (!"pending".equals(existing.getStatus())) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("error", "Only pending orders can be deleted"));
            }

            orderRepository.deleteOrder(existing.getId(), existing.getCustomerId());
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error deleting order {}", orderId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to delete order"));
        }
    }
}
