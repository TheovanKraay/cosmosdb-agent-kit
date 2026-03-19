package com.ecommerce.controller;

import com.ecommerce.model.Order;
import com.ecommerce.model.OrderItem;
import com.ecommerce.repository.OrderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

/**
 * REST controller for order management endpoints.
 */
@RestController
public class OrderController {

    private final OrderRepository orderRepository;

    public OrderController(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    // ---------------------------------------------------------------
    // Health
    // ---------------------------------------------------------------

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    // ---------------------------------------------------------------
    // Create Order — POST /api/orders
    // ---------------------------------------------------------------

    @PostMapping("/api/orders")
    public ResponseEntity<?> createOrder(@RequestBody Map<String, Object> body) {
        String customerId = (String) body.get("customerId");
        if (customerId == null || customerId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "customerId is required"));
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rawItems = (List<Map<String, Object>>) body.get("items");
        if (rawItems == null || rawItems.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "items is required and must not be empty"));
        }

        List<OrderItem> items = new ArrayList<>();
        double total = 0.0;
        for (Map<String, Object> raw : rawItems) {
            String productId   = (String) raw.get("productId");
            String productName = (String) raw.get("productName");
            int    quantity    = ((Number) raw.get("quantity")).intValue();
            double unitPrice   = ((Number) raw.get("unitPrice")).doubleValue();
            items.add(new OrderItem(productId, productName, quantity, unitPrice));
            total += quantity * unitPrice;
        }

        String orderId = UUID.randomUUID().toString();
        String createdAt = Instant.now().toString();

        Order order = new Order();
        order.setId(orderId);
        order.setOrderId(orderId);
        order.setCustomerId(customerId);
        order.setStatus("pending");
        order.setItems(items);
        order.setTotal(Math.round(total * 100.0) / 100.0);
        order.setCreatedAt(createdAt);
        order.setShippingAddress((String) body.get("shippingAddress"));

        orderRepository.save(order);
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }

    // ---------------------------------------------------------------
    // Get Order — GET /api/orders/{orderId}
    // ---------------------------------------------------------------

    @GetMapping("/api/orders/{orderId}")
    public ResponseEntity<?> getOrder(@PathVariable String orderId) {
        Optional<Order> opt = orderRepository.findByOrderId(orderId);
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Order not found"));
        }
        return ResponseEntity.ok(opt.get());
    }

    // ---------------------------------------------------------------
    // Query Orders — GET /api/orders?status=X or ?startDate=X&endDate=Y
    // ---------------------------------------------------------------

    @GetMapping("/api/orders")
    public ResponseEntity<?> queryOrders(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        if (status != null) {
            List<Order> orders = orderRepository.findByStatus(status);
            return ResponseEntity.ok(orders);
        }

        if (startDate != null && endDate != null) {
            // Normalise: if only date (no time) append T00:00:00Z / T23:59:59Z
            String start = normalizeDate(startDate, "T00:00:00Z");
            String end   = normalizeDate(endDate,   "T23:59:59Z");
            List<Order> orders = orderRepository.findByDateRange(start, end);
            return ResponseEntity.ok(orders);
        }

        return ResponseEntity.badRequest().body(Map.of("error", "Provide status or startDate+endDate query parameters"));
    }

    // ---------------------------------------------------------------
    // Update Order Status — PATCH /api/orders/{orderId}/status
    // ---------------------------------------------------------------

    @PatchMapping("/api/orders/{orderId}/status")
    public ResponseEntity<?> updateOrderStatus(@PathVariable String orderId,
                                                @RequestBody Map<String, String> body) {
        String newStatus = body.get("status");
        if (newStatus == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "status is required"));
        }

        Optional<Order> opt = orderRepository.findByOrderId(orderId);
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Order not found"));
        }

        Order order = opt.get();
        String current = order.getStatus();

        if (!isValidTransition(current, newStatus)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Invalid status transition: " + current + " → " + newStatus));
        }

        order.setStatus(newStatus);
        orderRepository.update(order);
        return ResponseEntity.ok(order);
    }

    // ---------------------------------------------------------------
    // Delete Order — DELETE /api/orders/{orderId}
    // ---------------------------------------------------------------

    @DeleteMapping("/api/orders/{orderId}")
    public ResponseEntity<?> deleteOrder(@PathVariable String orderId) {
        Optional<Order> opt = orderRepository.findByOrderId(orderId);
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Order not found"));
        }

        Order order = opt.get();
        if (!"pending".equals(order.getStatus())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Only pending orders can be deleted"));
        }

        orderRepository.delete(order.getId(), order.getCustomerId());
        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------------
    // Customer Orders — GET /api/customers/{customerId}/orders
    // ---------------------------------------------------------------

    @GetMapping("/api/customers/{customerId}/orders")
    public ResponseEntity<?> getCustomerOrders(@PathVariable String customerId) {
        List<Order> orders = orderRepository.findByCustomerId(customerId);
        return ResponseEntity.ok(orders);
    }

    // ---------------------------------------------------------------
    // Customer Summary — GET /api/customers/{customerId}/orders/summary
    // ---------------------------------------------------------------

    @GetMapping("/api/customers/{customerId}/orders/summary")
    public ResponseEntity<?> getCustomerSummary(@PathVariable String customerId) {
        List<Order> orders = orderRepository.findByCustomerId(customerId);

        int totalOrders = orders.size();
        double totalSpent = orders.stream().mapToDouble(Order::getTotal).sum();
        double averageOrderValue = totalOrders == 0 ? 0.0 : totalSpent / totalOrders;

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("customerId", customerId);
        summary.put("totalOrders", totalOrders);
        summary.put("totalSpent", Math.round(totalSpent * 100.0) / 100.0);
        summary.put("averageOrderValue", Math.round(averageOrderValue * 100.0) / 100.0);

        return ResponseEntity.ok(summary);
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    /**
     * Valid status transitions:
     *   pending  → shipped, cancelled, delivered  (pending can go to any valid status)
     *   shipped  → delivered
     *   delivered  → (terminal)
     *   cancelled  → (terminal)
     */
    private boolean isValidTransition(String from, String to) {
        return switch (from) {
            case "pending"   -> Set.of("shipped", "cancelled", "delivered").contains(to);
            case "shipped"   -> "delivered".equals(to);
            default          -> false;  // delivered and cancelled are terminal
        };
    }

    private String normalizeDate(String date, String timeSuffix) {
        if (date.length() == 10) {
            return date + timeSuffix;
        }
        return date;
    }
}
