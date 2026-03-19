package com.ecommerce.controller;

import com.ecommerce.model.Order;
import com.ecommerce.model.OrderItem;
import com.ecommerce.repository.OrderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class OrderController {

    private static final Set<String> VALID_STATUSES =
            Set.of("pending", "shipped", "delivered", "cancelled");

    private final OrderRepository repository;

    public OrderController(OrderRepository repository) {
        this.repository = repository;
    }

    @PostMapping("/orders")
    @SuppressWarnings("unchecked")
    public ResponseEntity<?> createOrder(@RequestBody(required = false) Map<String, Object> body) {
        if (body == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Request body is required"));
        }

        if (!body.containsKey("customerId") || body.get("customerId") == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "customerId is required"));
        }
        String customerId;
        try {
            customerId = (String) body.get("customerId");
        } catch (ClassCastException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "customerId must be a string"));
        }

        if (!body.containsKey("items") || body.get("items") == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "items is required"));
        }

        List<Map<String, Object>> itemMaps;
        try {
            itemMaps = (List<Map<String, Object>>) body.get("items");
        } catch (ClassCastException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "items must be an array"));
        }

        if (itemMaps.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "items cannot be empty"));
        }

        List<OrderItem> items = new ArrayList<>();
        double total = 0.0;

        try {
            for (Map<String, Object> itemMap : itemMaps) {
                OrderItem item = new OrderItem();
                item.setProductId((String) itemMap.get("productId"));
                item.setProductName((String) itemMap.get("productName"));
                item.setQuantity(((Number) itemMap.get("quantity")).intValue());
                item.setUnitPrice(((Number) itemMap.get("unitPrice")).doubleValue());
                items.add(item);
                total += item.getQuantity() * item.getUnitPrice();
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid item format"));
        }

        total = Math.round(total * 100.0) / 100.0;

        String orderId = UUID.randomUUID().toString();

        Order order = new Order();
        order.setId(orderId);
        order.setOrderId(orderId);
        order.setCustomerId(customerId);
        order.setStatus("pending");
        order.setItems(items);
        order.setTotal(total);
        order.setCreatedAt(Instant.now().toString());

        repository.createOrder(order);
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<?> getOrder(@PathVariable String orderId) {
        Optional<Order> order = repository.getOrderById(orderId);
        if (order.isPresent()) {
            return ResponseEntity.ok(order.get());
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/customers/{customerId}/orders")
    public ResponseEntity<List<Order>> getCustomerOrders(@PathVariable String customerId) {
        List<Order> orders = repository.getOrdersByCustomer(customerId);
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/customers/{customerId}/orders/summary")
    public ResponseEntity<?> getCustomerSummary(@PathVariable String customerId) {
        List<Order> orders = repository.getOrdersByCustomer(customerId);

        int totalOrders = orders.size();
        double totalSpent = 0.0;
        for (Order order : orders) {
            totalSpent += order.getTotal();
        }
        totalSpent = Math.round(totalSpent * 100.0) / 100.0;

        double averageOrderValue = totalOrders > 0 ? totalSpent / totalOrders : 0.0;

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("customerId", customerId);
        summary.put("totalOrders", totalOrders);
        summary.put("totalSpent", totalSpent);
        summary.put("averageOrderValue", averageOrderValue);

        return ResponseEntity.ok(summary);
    }

    @GetMapping("/orders")
    public ResponseEntity<List<Order>> queryOrders(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        if (status != null && !status.isEmpty()) {
            return ResponseEntity.ok(repository.getOrdersByStatus(status));
        }

        if (startDate != null && endDate != null) {
            return ResponseEntity.ok(repository.getOrdersByDateRange(startDate, endDate));
        }

        return ResponseEntity.ok(Collections.emptyList());
    }

    @PatchMapping("/orders/{orderId}/status")
    public ResponseEntity<?> updateOrderStatus(
            @PathVariable String orderId,
            @RequestBody(required = false) Map<String, String> body) {

        if (body == null || !body.containsKey("status")
                || body.get("status") == null || body.get("status").isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "status is required"));
        }

        String newStatus = body.get("status");

        if (!VALID_STATUSES.contains(newStatus)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid status value: " + newStatus));
        }

        Optional<Order> existing = repository.getOrderById(orderId);
        if (existing.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Order order = existing.get();
        String currentStatus = order.getStatus();

        if (!isValidTransition(currentStatus, newStatus)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error",
                        "Invalid status transition from " + currentStatus + " to " + newStatus));
        }

        order.setStatus(newStatus);
        repository.updateOrder(order);
        return ResponseEntity.ok(order);
    }

    @DeleteMapping("/orders/{orderId}")
    public ResponseEntity<?> deleteOrder(@PathVariable String orderId) {
        Optional<Order> existing = repository.getOrderById(orderId);
        if (existing.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Order order = existing.get();
        if (!"pending".equals(order.getStatus())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Only pending orders can be deleted"));
        }

        repository.deleteOrder(order.getId(), order.getCustomerId());
        return ResponseEntity.noContent().build();
    }

    private boolean isValidTransition(String currentStatus, String newStatus) {
        return switch (currentStatus) {
            case "pending" -> VALID_STATUSES.contains(newStatus) && !"pending".equals(newStatus);
            case "shipped" -> "delivered".equals(newStatus);
            default -> false;
        };
    }
}
