package com.ecommerce.orders.controller;

import com.ecommerce.orders.model.Order;
import com.ecommerce.orders.model.OrderItem;
import com.ecommerce.orders.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST controller implementing the e-commerce order API contract.
 * All endpoints follow the exact paths, field names, and status codes
 * defined in api-contract.yaml.
 */
@RestController
@RequestMapping("/api")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);
    private final OrderRepository orderRepository;

    /**
     * Valid status transitions state machine.
     * pending → shipped, cancelled, delivered
     * shipped → delivered
     * delivered → (terminal)
     * cancelled → (terminal)
     */
    private static final Map<String, Set<String>> VALID_TRANSITIONS = Map.of(
            "pending", Set.of("shipped", "cancelled", "delivered"),
            "shipped", Set.of("delivered"),
            "delivered", Set.of(),
            "cancelled", Set.of()
    );

    public OrderController(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    // ==================== POST /api/orders ====================

    @PostMapping("/orders")
    public ResponseEntity<?> createOrder(@RequestBody(required = false) Map<String, Object> body) {
        // Input validation
        if (body == null || body.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Request body is required"));
        }

        String customerId = (String) body.get("customerId");
        if (customerId == null || customerId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "customerId is required"));
        }

        Object itemsObj = body.get("items");
        if (itemsObj == null || !(itemsObj instanceof List) || ((List<?>) itemsObj).isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "items is required and must be a non-empty array"));
        }

        List<?> rawItems = (List<?>) itemsObj;
        List<OrderItem> items = new ArrayList<>();
        for (Object raw : rawItems) {
            if (!(raw instanceof Map)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Each item must be an object"));
            }
            Map<?, ?> itemMap = (Map<?, ?>) raw;
            OrderItem item = new OrderItem();
            item.setProductId((String) itemMap.get("productId"));
            item.setProductName((String) itemMap.get("productName"));
            item.setQuantity(toInt(itemMap.get("quantity")));
            item.setUnitPrice(toDouble(itemMap.get("unitPrice")));
            items.add(item);
        }

        String shippingAddress = (String) body.get("shippingAddress");

        Order order = Order.create(customerId, items, shippingAddress);

        try {
            Order created = orderRepository.createOrder(order);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (Exception e) {
            log.error("Failed to create order: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to create order: " + e.getMessage()));
        }
    }

    // ==================== GET /api/orders/{orderId} ====================

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
            log.error("Failed to get order {}: {}", orderId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to get order: " + e.getMessage()));
        }
    }

    // ==================== GET /api/customers/{customerId}/orders ====================

    @GetMapping("/customers/{customerId}/orders")
    public ResponseEntity<?> getCustomerOrders(@PathVariable String customerId) {
        try {
            List<Order> orders = orderRepository.getOrdersByCustomer(customerId);
            return ResponseEntity.ok(orders);
        } catch (Exception e) {
            log.error("Failed to get orders for customer {}: {}", customerId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to get customer orders: " + e.getMessage()));
        }
    }

    // ==================== GET /api/customers/{customerId}/orders/summary ====================

    @GetMapping("/customers/{customerId}/orders/summary")
    public ResponseEntity<?> getCustomerSummary(@PathVariable String customerId) {
        try {
            List<Order> orders = orderRepository.getOrdersByCustomer(customerId);

            int totalOrders = orders.size();
            double totalSpent = orders.stream().mapToDouble(Order::getTotal).sum();
            totalSpent = Math.round(totalSpent * 100.0) / 100.0;
            double averageOrderValue = totalOrders > 0 ? totalSpent / totalOrders : 0.0;
            averageOrderValue = Math.round(averageOrderValue * 1000.0) / 1000.0;

            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("customerId", customerId);
            summary.put("totalOrders", totalOrders);
            summary.put("totalSpent", totalSpent);
            summary.put("averageOrderValue", averageOrderValue);

            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            log.error("Failed to get summary for customer {}: {}", customerId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to get customer summary: " + e.getMessage()));
        }
    }

    // ==================== GET /api/orders?status=X or ?startDate=X&endDate=Y ====================

    @GetMapping("/orders")
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

            // No filter provided — return empty list or all orders
            return ResponseEntity.ok(Collections.emptyList());
        } catch (Exception e) {
            log.error("Failed to query orders: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to query orders: " + e.getMessage()));
        }
    }

    // ==================== PATCH /api/orders/{orderId}/status ====================

    @PatchMapping("/orders/{orderId}/status")
    public ResponseEntity<?> updateOrderStatus(
            @PathVariable String orderId,
            @RequestBody(required = false) Map<String, Object> body) {
        try {
            // Validate request body
            if (body == null || body.isEmpty() || body.get("status") == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "status is required"));
            }

            String newStatus = body.get("status").toString().toLowerCase();

            // Validate status value
            if (!VALID_TRANSITIONS.containsKey(newStatus) && !isValidStatus(newStatus)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid status value: " + newStatus));
            }

            // Find the order first (cross-partition)
            Optional<Order> existingOrder = orderRepository.getOrderById(orderId);
            if (existingOrder.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Order not found"));
            }

            Order order = existingOrder.get();
            String currentStatus = order.getStatus().toLowerCase();

            // Validate status transition
            Set<String> allowed = VALID_TRANSITIONS.getOrDefault(currentStatus, Set.of());
            if (!allowed.contains(newStatus)) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("error",
                                "Invalid status transition: " + currentStatus + " → " + newStatus));
            }

            // Apply the update using Patch API
            Order updated = orderRepository.updateOrderStatus(orderId, order.getCustomerId(), newStatus);
            return ResponseEntity.ok(updated);

        } catch (Exception e) {
            log.error("Failed to update order status {}: {}", orderId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update order status: " + e.getMessage()));
        }
    }

    // ==================== DELETE /api/orders/{orderId} ====================

    @DeleteMapping("/orders/{orderId}")
    public ResponseEntity<?> deleteOrder(@PathVariable String orderId) {
        try {
            Optional<Order> existingOrder = orderRepository.getOrderById(orderId);
            if (existingOrder.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Order not found"));
            }

            Order order = existingOrder.get();
            String currentStatus = order.getStatus().toLowerCase();

            if (!"pending".equals(currentStatus)) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("error", "Only pending orders can be deleted. Current status: " + currentStatus));
            }

            orderRepository.deleteOrder(orderId, order.getCustomerId());
            return ResponseEntity.noContent().build();

        } catch (Exception e) {
            log.error("Failed to delete order {}: {}", orderId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to delete order: " + e.getMessage()));
        }
    }

    // ==================== Helper methods ====================

    private boolean isValidStatus(String status) {
        return Set.of("pending", "shipped", "delivered", "cancelled").contains(status);
    }

    private int toInt(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            return Integer.parseInt((String) value);
        }
        return 0;
    }

    private double toDouble(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String) {
            return Double.parseDouble((String) value);
        }
        return 0.0;
    }
}
