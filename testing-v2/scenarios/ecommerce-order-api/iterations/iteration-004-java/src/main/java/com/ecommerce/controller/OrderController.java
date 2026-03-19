package com.ecommerce.controller;

import com.ecommerce.model.Order;
import com.ecommerce.model.OrderItem;
import com.ecommerce.service.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/api/orders")
    public ResponseEntity<?> createOrder(@RequestBody(required = false) Map<String, Object> body) {
        if (body == null || body.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Request body is required"));
        }

        String customerId = body.get("customerId") instanceof String s ? s : null;
        if (customerId == null || customerId.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "customerId is required"));
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rawItems = body.get("items") instanceof List<?> list
                ? (List<Map<String, Object>>) list : null;

        if (rawItems == null || rawItems.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "items is required and must not be empty"));
        }

        String shippingAddress = body.get("shippingAddress") instanceof String s ? s : null;

        List<OrderItem> items = rawItems.stream().map(raw -> {
            OrderItem item = new OrderItem();
            item.setProductId((String) raw.get("productId"));
            item.setProductName((String) raw.get("productName"));
            item.setQuantity(raw.get("quantity") instanceof Number n ? n.intValue() : Integer.parseInt(raw.get("quantity").toString()));
            item.setUnitPrice(raw.get("unitPrice") instanceof Number n ? n.doubleValue() : Double.parseDouble(raw.get("unitPrice").toString()));
            return item;
        }).toList();

        Order order = orderService.createOrder(customerId, items, shippingAddress);
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }

    @GetMapping("/api/orders/{orderId}")
    public ResponseEntity<Order> getOrder(@PathVariable String orderId) {
        return orderService.getOrder(orderId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/api/customers/{customerId}/orders")
    public ResponseEntity<List<Order>> getCustomerOrders(@PathVariable String customerId) {
        List<Order> orders = orderService.getCustomerOrders(customerId);
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/api/customers/{customerId}/orders/summary")
    public ResponseEntity<Map<String, Object>> getCustomerSummary(@PathVariable String customerId) {
        Map<String, Object> summary = orderService.getCustomerSummary(customerId);
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/api/orders")
    public ResponseEntity<List<Order>> queryOrders(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        if (status != null && !status.isEmpty()) {
            return ResponseEntity.ok(orderService.getOrdersByStatus(status));
        }

        if (startDate != null && endDate != null) {
            return ResponseEntity.ok(orderService.getOrdersByDateRange(startDate, endDate));
        }

        return ResponseEntity.ok(List.of());
    }

    @PatchMapping("/api/orders/{orderId}/status")
    public ResponseEntity<?> updateOrderStatus(
            @PathVariable String orderId,
            @RequestBody(required = false) Map<String, String> body) {

        if (body == null || body.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Request body with status field is required"));
        }

        String newStatus = body.get("status");
        if (newStatus == null || newStatus.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "status field is required"));
        }

        try {
            Optional<Order> updated = orderService.updateOrderStatus(orderId, newStatus);
            if (updated.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(updated.get());
        } catch (OrderService.InvalidStatusTransitionException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/api/orders/{orderId}")
    public ResponseEntity<?> deleteOrder(@PathVariable String orderId) {
        try {
            boolean deleted = orderService.deleteOrder(orderId);
            if (!deleted) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.noContent().build();
        } catch (OrderService.InvalidStatusTransitionException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
