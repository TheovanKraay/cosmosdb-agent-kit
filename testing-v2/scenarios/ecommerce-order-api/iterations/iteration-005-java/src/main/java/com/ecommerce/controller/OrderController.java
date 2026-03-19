package com.ecommerce.controller;

import com.ecommerce.model.Order;
import com.ecommerce.model.OrderItem;
import com.ecommerce.repository.OrderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

@RestController
public class OrderController {

    private final OrderRepository orderRepository;

    private static final Set<String> VALID_STATUSES = Set.of("pending", "shipped", "delivered", "cancelled");

    public OrderController(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @PostMapping("/api/orders")
    public ResponseEntity<?> createOrder(@RequestBody Map<String, Object> body) {
        String customerId = (String) body.get("customerId");
        if (customerId == null || customerId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "customerId is required"));
        }

        Object itemsObj = body.get("items");
        if (itemsObj == null || !(itemsObj instanceof List)) {
            return ResponseEntity.badRequest().body(Map.of("error", "items is required"));
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> itemsList = (List<Map<String, Object>>) itemsObj;
        if (itemsList.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "items must not be empty"));
        }

        List<OrderItem> orderItems = new ArrayList<>();
        double total = 0.0;

        for (Map<String, Object> itemMap : itemsList) {
            String productId = (String) itemMap.get("productId");
            String productName = (String) itemMap.get("productName");
            int quantity = ((Number) itemMap.get("quantity")).intValue();
            double unitPrice = ((Number) itemMap.get("unitPrice")).doubleValue();

            OrderItem item = new OrderItem(productId, productName, quantity, unitPrice);
            orderItems.add(item);
            total += quantity * unitPrice;
        }

        String orderId = UUID.randomUUID().toString();

        Order order = new Order();
        order.setId(orderId);
        order.setOrderId(orderId);
        order.setCustomerId(customerId);
        order.setStatus("pending");
        order.setItems(orderItems);
        order.setTotal(total);
        order.setCreatedAt(Instant.now().toString());
        order.setShippingAddress((String) body.get("shippingAddress"));

        orderRepository.createOrder(order);

        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }

    @GetMapping("/api/orders/{orderId}")
    public ResponseEntity<?> getOrder(@PathVariable String orderId) {
        Order order = orderRepository.findById(orderId);
        if (order == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Order not found"));
        }
        return ResponseEntity.ok(order);
    }

    @GetMapping("/api/customers/{customerId}/orders")
    public ResponseEntity<List<Order>> getCustomerOrders(@PathVariable String customerId) {
        List<Order> orders = orderRepository.findByCustomerId(customerId);
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/api/customers/{customerId}/orders/summary")
    public ResponseEntity<?> getCustomerSummary(@PathVariable String customerId) {
        List<Order> orders = orderRepository.findByCustomerId(customerId);

        int totalOrders = orders.size();
        double totalSpent = orders.stream().mapToDouble(Order::getTotal).sum();
        double averageOrderValue = totalOrders > 0 ? totalSpent / totalOrders : 0.0;

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("customerId", customerId);
        summary.put("totalOrders", totalOrders);
        summary.put("totalSpent", totalSpent);
        summary.put("averageOrderValue", averageOrderValue);

        return ResponseEntity.ok(summary);
    }

    @GetMapping("/api/orders")
    public ResponseEntity<List<Order>> queryOrders(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        if (status != null && !status.isBlank()) {
            List<Order> orders = orderRepository.findByStatus(status);
            return ResponseEntity.ok(orders);
        }

        if (startDate != null && endDate != null) {
            List<Order> orders = orderRepository.findByDateRange(startDate, endDate);
            return ResponseEntity.ok(orders);
        }

        return ResponseEntity.ok(List.of());
    }

    @PatchMapping("/api/orders/{orderId}/status")
    public ResponseEntity<?> updateOrderStatus(@PathVariable String orderId, @RequestBody(required = false) Map<String, String> body) {
        if (body == null || body.get("status") == null || body.get("status").isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "status is required"));
        }

        String newStatus = body.get("status");
        if (!VALID_STATUSES.contains(newStatus)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid status value: " + newStatus));
        }

        Order order = orderRepository.findById(orderId);
        if (order == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Order not found"));
        }

        String currentStatus = order.getStatus();

        if (!isValidTransition(currentStatus, newStatus)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Invalid status transition from " + currentStatus + " to " + newStatus));
        }

        order.setStatus(newStatus);
        orderRepository.updateOrder(order);

        return ResponseEntity.ok(order);
    }

    @DeleteMapping("/api/orders/{orderId}")
    public ResponseEntity<?> deleteOrder(@PathVariable String orderId) {
        Order order = orderRepository.findById(orderId);
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

    private boolean isValidTransition(String currentStatus, String newStatus) {
        if (currentStatus.equals(newStatus)) {
            return false;
        }
        return switch (currentStatus) {
            case "pending" -> VALID_STATUSES.contains(newStatus);
            case "shipped" -> "delivered".equals(newStatus);
            default -> false; // delivered and cancelled are terminal
        };
    }
}
