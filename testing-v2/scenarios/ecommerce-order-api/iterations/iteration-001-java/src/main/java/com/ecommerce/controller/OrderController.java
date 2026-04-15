package com.ecommerce.controller;

import com.ecommerce.model.Order;
import com.ecommerce.model.OrderItem;
import com.ecommerce.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api")
public class OrderController {

    private static final Logger logger = LoggerFactory.getLogger(OrderController.class);

    private final OrderRepository orderRepository;

    // Valid status transitions
    private static final Map<String, Set<String>> VALID_TRANSITIONS = Map.of(
        "pending", Set.of("shipped", "cancelled", "delivered"),
        "shipped", Set.of("delivered"),
        "delivered", Set.of(),
        "cancelled", Set.of()
    );

    private static final Set<String> VALID_STATUSES = Set.of(
        "pending", "shipped", "delivered", "cancelled"
    );

    public OrderController(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    /**
     * POST /api/orders — Create a new order with items.
     */
    @PostMapping("/orders")
    public ResponseEntity<?> createOrder(@RequestBody(required = false) Map<String, Object> body) {
        // Validate input
        if (body == null || !body.containsKey("customerId")) {
            return ResponseEntity.badRequest().body(Map.of("error", "customerId is required"));
        }

        String customerId = (String) body.get("customerId");
        if (customerId == null || customerId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "customerId is required"));
        }

        Object itemsObj = body.get("items");
        if (itemsObj == null || !(itemsObj instanceof List) || ((List<?>) itemsObj).isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "items is required and must not be empty"));
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> itemMaps = (List<Map<String, Object>>) itemsObj;

        List<OrderItem> items = new ArrayList<>();
        for (Map<String, Object> itemMap : itemMaps) {
            OrderItem item = new OrderItem();
            item.setProductId((String) itemMap.get("productId"));
            item.setProductName((String) itemMap.get("productName"));
            item.setQuantity(((Number) itemMap.get("quantity")).intValue());
            item.setUnitPrice(((Number) itemMap.get("unitPrice")).doubleValue());
            items.add(item);
        }

        // Build the order
        String orderId = UUID.randomUUID().toString();
        Order order = new Order();
        order.setId(orderId);
        order.setOrderId(orderId);
        order.setCustomerId(customerId);
        order.setStatus("pending");
        order.setItems(items);
        order.calculateTotal();
        order.setCreatedAt(Instant.now().toString());

        // Optional shipping address
        if (body.containsKey("shippingAddress")) {
            order.setShippingAddress((String) body.get("shippingAddress"));
        }

        try {
            Order created = orderRepository.createOrder(order);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (Exception e) {
            logger.error("Error creating order: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to create order"));
        }
    }

    /**
     * GET /api/orders/{orderId} — Get order by ID.
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
            logger.error("Error getting order {}: {}", orderId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to get order"));
        }
    }

    /**
     * GET /api/orders?status=X or GET /api/orders?startDate=X&endDate=Y
     * Query orders by status or date range (admin).
     */
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

            // No filter — return empty list
            return ResponseEntity.ok(Collections.emptyList());
        } catch (Exception e) {
            logger.error("Error querying orders: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to query orders"));
        }
    }

    /**
     * GET /api/customers/{customerId}/orders — Customer order history.
     */
    @GetMapping("/customers/{customerId}/orders")
    public ResponseEntity<?> getCustomerOrders(@PathVariable String customerId) {
        try {
            List<Order> orders = orderRepository.getOrdersByCustomerId(customerId);
            return ResponseEntity.ok(orders);
        } catch (Exception e) {
            logger.error("Error getting orders for customer {}: {}", customerId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to get customer orders"));
        }
    }

    /**
     * GET /api/customers/{customerId}/orders/summary — Customer order summary.
     */
    @GetMapping("/customers/{customerId}/orders/summary")
    public ResponseEntity<?> getCustomerSummary(@PathVariable String customerId) {
        try {
            List<Order> orders = orderRepository.getOrdersByCustomerId(customerId);

            int totalOrders = orders.size();
            double totalSpent = orders.stream()
                .mapToDouble(Order::getTotal)
                .sum();
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
            logger.error("Error getting summary for customer {}: {}", customerId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to get customer summary"));
        }
    }

    /**
     * PATCH /api/orders/{orderId}/status — Update order status.
     */
    @PatchMapping("/orders/{orderId}/status")
    public ResponseEntity<?> updateOrderStatus(
            @PathVariable String orderId,
            @RequestBody(required = false) Map<String, String> body) {

        if (body == null || !body.containsKey("status") || body.get("status") == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "status is required"));
        }

        String newStatus = body.get("status");
        if (!VALID_STATUSES.contains(newStatus)) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Invalid status value: " + newStatus));
        }

        try {
            Optional<Order> existing = orderRepository.getOrderById(orderId);
            if (existing.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Order not found"));
            }

            Order order = existing.get();
            String currentStatus = order.getStatus();

            // Validate status transition
            Set<String> allowed = VALID_TRANSITIONS.getOrDefault(currentStatus, Set.of());
            if (!allowed.contains(newStatus)) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error",
                        "Invalid status transition from '" + currentStatus + "' to '" + newStatus + "'"));
            }

            order.setStatus(newStatus);
            Order updated = orderRepository.replaceOrder(order);
            return ResponseEntity.ok(updated);

        } catch (Exception e) {
            logger.error("Error updating order {} status: {}", orderId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to update order status"));
        }
    }

    /**
     * DELETE /api/orders/{orderId} — Delete a pending order.
     */
    @DeleteMapping("/orders/{orderId}")
    public ResponseEntity<?> deleteOrder(@PathVariable String orderId) {
        try {
            Optional<Order> existing = orderRepository.getOrderById(orderId);
            if (existing.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Order not found"));
            }

            Order order = existing.get();
            if (!"pending".equals(order.getStatus())) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Only pending orders can be deleted"));
            }

            orderRepository.deleteOrder(order.getId(), order.getCustomerId());
            return ResponseEntity.noContent().build();

        } catch (Exception e) {
            logger.error("Error deleting order {}: {}", orderId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to delete order"));
        }
    }
}
