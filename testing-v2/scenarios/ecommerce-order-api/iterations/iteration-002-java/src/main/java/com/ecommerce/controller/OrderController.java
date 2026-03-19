package com.ecommerce.controller;

import com.ecommerce.model.CreateOrderRequest;
import com.ecommerce.model.CustomerSummary;
import com.ecommerce.model.Order;
import com.ecommerce.model.StatusUpdateRequest;
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

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "healthy"));
    }

    @PostMapping("/api/orders")
    public ResponseEntity<?> createOrder(@RequestBody(required = false) CreateOrderRequest request) {
        if (request == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Request body is required"));
        }
        if (request.getCustomerId() == null || request.getCustomerId().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "customerId is required"));
        }
        if (request.getItems() == null || request.getItems().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "items array is required and must not be empty"));
        }

        Order order = new Order();
        String orderId = UUID.randomUUID().toString();
        order.setId(orderId);
        order.setOrderId(orderId);
        order.setCustomerId(request.getCustomerId());
        order.setItems(request.getItems());
        order.setStatus("pending");
        order.setCreatedAt(Instant.now().toString());
        order.setTotal(order.calculateTotal());
        order.setShippingAddress(request.getShippingAddress());
        order.setType("order");
        order.setSchemaVersion("1.0");

        Order created = orderRepository.createOrder(order);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/api/orders/{orderId}")
    public ResponseEntity<?> getOrder(@PathVariable String orderId) {
        Optional<Order> order = orderRepository.findById(orderId);
        if (order.isPresent()) {
            return ResponseEntity.ok(order.get());
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Order not found"));
    }

    @GetMapping("/api/customers/{customerId}/orders")
    public ResponseEntity<List<Order>> getCustomerOrders(@PathVariable String customerId) {
        List<Order> orders = orderRepository.findByCustomerId(customerId);
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/api/customers/{customerId}/orders/summary")
    public ResponseEntity<CustomerSummary> getCustomerSummary(@PathVariable String customerId) {
        List<Order> orders = orderRepository.findByCustomerId(customerId);
        int totalOrders = orders.size();
        double totalSpent = orders.stream().mapToDouble(Order::getTotal).sum();
        double averageOrderValue = totalOrders > 0 ? totalSpent / totalOrders : 0.0;

        CustomerSummary summary = new CustomerSummary(customerId, totalOrders, totalSpent, averageOrderValue);
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
    public ResponseEntity<?> updateOrderStatus(@PathVariable String orderId,
                                                @RequestBody(required = false) StatusUpdateRequest request) {
        if (request == null || request.getStatus() == null || request.getStatus().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "status is required"));
        }

        String newStatus = request.getStatus();
        if (!VALID_STATUSES.contains(newStatus)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid status value: " + newStatus));
        }

        Optional<Order> existing = orderRepository.findById(orderId);
        if (existing.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Order not found"));
        }

        Order order = existing.get();
        String currentStatus = order.getStatus();

        if (!isValidTransition(currentStatus, newStatus)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Invalid status transition from " + currentStatus + " to " + newStatus));
        }

        order.setStatus(newStatus);
        Order updated = orderRepository.updateOrder(order);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/api/orders/{orderId}")
    public ResponseEntity<?> deleteOrder(@PathVariable String orderId) {
        Optional<Order> existing = orderRepository.findById(orderId);
        if (existing.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Order not found"));
        }

        Order order = existing.get();
        if (!"pending".equals(order.getStatus())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Only pending orders can be deleted"));
        }

        orderRepository.deleteOrder(order);
        return ResponseEntity.noContent().build();
    }

    private boolean isValidTransition(String currentStatus, String newStatus) {
        if (currentStatus.equals(newStatus)) {
            return false;
        }
        return switch (currentStatus) {
            case "pending" -> "shipped".equals(newStatus) || "cancelled".equals(newStatus) || "delivered".equals(newStatus);
            case "shipped" -> "delivered".equals(newStatus);
            default -> false;
        };
    }
}
