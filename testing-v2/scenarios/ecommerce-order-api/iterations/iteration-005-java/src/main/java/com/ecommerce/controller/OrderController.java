package com.ecommerce.controller;

import com.ecommerce.model.*;
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
    public ResponseEntity<?> createOrder(@RequestBody CreateOrderRequest request) {
        if (request.getCustomerId() == null || request.getCustomerId().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "customerId is required"));
        }
        if (request.getItems() == null || request.getItems().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "items are required"));
        }

        String orderId = UUID.randomUUID().toString();

        double total = 0;
        for (OrderItem item : request.getItems()) {
            total += item.getQuantity() * item.getUnitPrice();
        }

        Order order = new Order();
        order.setId(orderId);
        order.setOrderId(orderId);
        order.setCustomerId(request.getCustomerId());
        order.setStatus("pending");
        order.setItems(request.getItems());
        order.setTotal(total);
        order.setCreatedAt(Instant.now().toString());
        order.setShippingAddress(request.getShippingAddress());

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
    public ResponseEntity<CustomerSummary> getCustomerSummary(@PathVariable String customerId) {
        List<Order> orders = orderRepository.findByCustomerId(customerId);

        int totalOrders = orders.size();
        double totalSpent = orders.stream().mapToDouble(Order::getTotal).sum();
        double averageOrderValue = totalOrders > 0 ? totalSpent / totalOrders : 0;

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

        return ResponseEntity.ok(Collections.emptyList());
    }

    @PatchMapping("/api/orders/{orderId}/status")
    public ResponseEntity<?> updateOrderStatus(
            @PathVariable String orderId,
            @RequestBody StatusUpdateRequest request) {

        Order order = orderRepository.findById(orderId);
        if (order == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Order not found"));
        }

        String currentStatus = order.getStatus();
        String newStatus = request.getStatus();

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
        if (currentStatus == null || newStatus == null) return false;
        if (!VALID_STATUSES.contains(newStatus)) return false;

        return switch (currentStatus) {
            case "pending" -> true;
            case "shipped" -> "delivered".equals(newStatus);
            default -> false;
        };
    }
}
