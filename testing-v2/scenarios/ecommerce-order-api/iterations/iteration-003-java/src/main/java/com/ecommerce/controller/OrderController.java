package com.ecommerce.controller;

import com.ecommerce.model.Order;
import com.ecommerce.service.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/api/orders")
    public ResponseEntity<Order> createOrder(@RequestBody Map<String, Object> request) {
        Order order = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }

    @GetMapping("/api/orders/{orderId}")
    public ResponseEntity<Order> getOrder(@PathVariable String orderId) {
        Order order = orderService.getOrder(orderId);
        if (order == null) {
            return ResponseEntity.notFound().build();
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
        Map<String, Object> summary = orderService.getCustomerSummary(customerId);
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/api/orders")
    public ResponseEntity<List<Order>> queryOrders(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        if (status != null && !status.isEmpty()) {
            return ResponseEntity.ok(orderService.queryByStatus(status));
        }
        if (startDate != null && endDate != null) {
            return ResponseEntity.ok(orderService.queryByDateRange(startDate, endDate));
        }
        return ResponseEntity.ok(List.of());
    }

    @PatchMapping("/api/orders/{orderId}/status")
    public ResponseEntity<?> updateOrderStatus(
            @PathVariable String orderId,
            @RequestBody Map<String, String> request) {
        try {
            Order updated = orderService.updateOrderStatus(orderId, request.get("status"));
            if (updated == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(updated);
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
