package com.ecommerce.controller;

import com.ecommerce.model.Order;
import com.ecommerce.model.OrderItem;
import com.ecommerce.repository.OrderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class OrderController {

    private static final Set<String> VALID_STATUSES = Set.of("pending", "shipped", "delivered", "cancelled");

    private final OrderRepository orderRepository;

    public OrderController(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @PostMapping("/orders")
    public ResponseEntity<?> createOrder(@RequestBody Map<String, Object> body) {
        String customerId = (String) body.get("customerId");
        if (customerId == null || customerId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "customerId is required"));
        }

        Object itemsObj = body.get("items");
        if (!(itemsObj instanceof List<?> rawItems)) {
            return ResponseEntity.badRequest().body(Map.of("error", "items is required"));
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> itemMaps = (List<Map<String, Object>>) (List<?>) rawItems;
        if (itemMaps.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "items must not be empty"));
        }

        List<OrderItem> items = itemMaps.stream().map(m -> {
            OrderItem item = new OrderItem();
            item.setProductId((String) m.get("productId"));
            item.setProductName((String) m.get("productName"));
            item.setQuantity(((Number) m.get("quantity")).intValue());
            item.setUnitPrice(((Number) m.get("unitPrice")).doubleValue());
            return item;
        }).toList();

        double total = items.stream()
                .mapToDouble(item -> item.getQuantity() * item.getUnitPrice())
                .sum();

        String orderId = UUID.randomUUID().toString();

        Order order = new Order();
        order.setId(orderId);
        order.setOrderId(orderId);
        order.setCustomerId(customerId);
        order.setStatus("pending");
        order.setItems(items);
        order.setTotal(total);
        order.setCreatedAt(Instant.now().toString());

        String shippingAddress = (String) body.get("shippingAddress");
        if (shippingAddress != null) {
            order.setShippingAddress(shippingAddress);
        }

        Order created = orderRepository.createOrder(order);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<?> getOrder(@PathVariable String orderId) {
        Order order = orderRepository.getOrderById(orderId);
        if (order == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Order not found"));
        }
        return ResponseEntity.ok(order);
    }

    @GetMapping("/customers/{customerId}/orders")
    public ResponseEntity<List<Order>> getCustomerOrders(@PathVariable String customerId) {
        List<Order> orders = orderRepository.getOrdersByCustomer(customerId);
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/customers/{customerId}/orders/summary")
    public ResponseEntity<?> getCustomerSummary(@PathVariable String customerId) {
        List<Order> orders = orderRepository.getOrdersByCustomer(customerId);

        int totalOrders = orders.size();
        double totalSpent = orders.stream().mapToDouble(Order::getTotal).sum();
        double averageOrderValue = totalOrders > 0 ? totalSpent / totalOrders : 0;

        return ResponseEntity.ok(Map.of(
                "customerId", customerId,
                "totalOrders", totalOrders,
                "totalSpent", totalSpent,
                "averageOrderValue", averageOrderValue
        ));
    }

    @GetMapping("/orders")
    public ResponseEntity<List<Order>> queryOrders(
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

        return ResponseEntity.ok(List.of());
    }

    @PatchMapping("/orders/{orderId}/status")
    public ResponseEntity<?> updateOrderStatus(@PathVariable String orderId,
                                                @RequestBody Map<String, String> body) {
        String newStatus = body.get("status");
        if (newStatus == null || newStatus.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "status is required"));
        }

        if (!VALID_STATUSES.contains(newStatus)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid status value"));
        }

        Order order = orderRepository.getOrderById(orderId);
        if (order == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Order not found"));
        }

        if (!isValidTransition(order.getStatus(), newStatus)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Invalid status transition from " + order.getStatus() + " to " + newStatus));
        }

        order.setStatus(newStatus);
        Order updated = orderRepository.updateOrder(order);
        return ResponseEntity.ok(updated);
    }

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

        orderRepository.deleteOrder(orderId, order.getCustomerId());
        return ResponseEntity.noContent().build();
    }

    private boolean isValidTransition(String currentStatus, String newStatus) {
        return switch (currentStatus) {
            case "pending" -> VALID_STATUSES.contains(newStatus) && !newStatus.equals("pending");
            case "shipped" -> "delivered".equals(newStatus);
            default -> false; // delivered and cancelled are terminal
        };
    }
}
