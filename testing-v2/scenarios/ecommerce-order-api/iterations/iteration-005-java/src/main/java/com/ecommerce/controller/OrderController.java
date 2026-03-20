package com.ecommerce.controller;

import com.ecommerce.model.Order;
import com.ecommerce.model.OrderItem;
import com.ecommerce.service.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "healthy");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/api/orders")
    public ResponseEntity<?> createOrder(@RequestBody Map<String, Object> body) {
        String customerId = (String) body.get("customerId");
        if (customerId == null || customerId.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "customerId is required"));
        }

        Object itemsObj = body.get("items");
        if (itemsObj == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "items is required"));
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> itemsList = (List<Map<String, Object>>) itemsObj;

        if (itemsList.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "items must not be empty"));
        }

        List<OrderItem> items = itemsList.stream().map(m -> {
            OrderItem item = new OrderItem();
            item.setProductId((String) m.get("productId"));
            item.setProductName((String) m.get("productName"));
            item.setQuantity(((Number) m.get("quantity")).intValue());
            item.setUnitPrice(((Number) m.get("unitPrice")).doubleValue());
            return item;
        }).toList();

        String shippingAddress = (String) body.get("shippingAddress");

        Order order = orderService.createOrder(customerId, items, shippingAddress);
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }

    @GetMapping("/api/orders/{orderId}")
    public ResponseEntity<?> getOrder(@PathVariable String orderId) {
        Order order = orderService.getOrder(orderId);
        if (order == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Order not found"));
        }
        return ResponseEntity.ok(order);
    }

    @GetMapping("/api/customers/{customerId}/orders")
    public ResponseEntity<List<Order>> getCustomerOrders(@PathVariable String customerId) {
        List<Order> orders = orderService.getCustomerOrders(customerId);
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/api/customers/{customerId}/orders/summary")
    public ResponseEntity<Map<String, Object>> getCustomerSummary(@PathVariable String customerId) {
        List<Order> orders = orderService.getCustomerOrders(customerId);

        int totalOrders = orders.size();
        double totalSpent = orders.stream().mapToDouble(Order::getTotal).sum();
        double averageOrderValue = totalOrders > 0 ? totalSpent / totalOrders : 0.0;

        Map<String, Object> summary = new HashMap<>();
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
            return ResponseEntity.ok(orderService.getOrdersByStatus(status));
        }

        if (startDate != null && endDate != null) {
            return ResponseEntity.ok(orderService.getOrdersByDateRange(startDate, endDate));
        }

        // If no filters, return empty list
        return ResponseEntity.ok(List.of());
    }

    @PatchMapping("/api/orders/{orderId}/status")
    public ResponseEntity<?> updateOrderStatus(@PathVariable String orderId,
                                                @RequestBody(required = false) Map<String, String> body) {
        if (body == null || !body.containsKey("status") || body.get("status") == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "status is required"));
        }

        String newStatus = body.get("status");

        try {
            Order order = orderService.updateOrderStatus(orderId, newStatus);
            if (order == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Order not found"));
            }
            return ResponseEntity.ok(order);
        } catch (OrderService.InvalidStatusTransitionException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/api/orders/{orderId}")
    public ResponseEntity<?> deleteOrder(@PathVariable String orderId) {
        try {
            orderService.deleteOrder(orderId);
            return ResponseEntity.noContent().build();
        } catch (OrderService.OrderNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (OrderService.InvalidStatusTransitionException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
