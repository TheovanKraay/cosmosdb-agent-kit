package com.ecommerce.controller;

import com.ecommerce.model.CreateOrderRequest;
import com.ecommerce.model.CustomerSummary;
import com.ecommerce.model.Order;
import com.ecommerce.model.UpdateStatusRequest;
import com.ecommerce.service.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/orders")
    public ResponseEntity<?> createOrder(@RequestBody(required = false) CreateOrderRequest request) {
        if (request == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Request body is required"));
        }
        if (request.getCustomerId() == null || request.getCustomerId().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "customerId is required"));
        }
        if (request.getItems() == null || request.getItems().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "items is required and must not be empty"));
        }

        Order order = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<?> getOrder(@PathVariable String orderId) {
        Order order = orderService.getOrder(orderId);
        if (order == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Order not found"));
        }
        return ResponseEntity.ok(order);
    }

    @GetMapping("/customers/{customerId}/orders")
    public ResponseEntity<List<Order>> getCustomerOrders(@PathVariable String customerId) {
        List<Order> orders = orderService.getCustomerOrders(customerId);
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/customers/{customerId}/orders/summary")
    public ResponseEntity<CustomerSummary> getCustomerSummary(@PathVariable String customerId) {
        CustomerSummary summary = orderService.getCustomerSummary(customerId);
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/orders")
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

        return ResponseEntity.ok(List.of());
    }

    @PatchMapping("/orders/{orderId}/status")
    public ResponseEntity<?> updateOrderStatus(
            @PathVariable String orderId,
            @RequestBody(required = false) UpdateStatusRequest request) {

        if (request == null || request.getStatus() == null || request.getStatus().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "status is required"));
        }

        try {
            Order updated = orderService.updateOrderStatus(orderId, request.getStatus());
            return ResponseEntity.ok(updated);
        } catch (OrderService.OrderNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (OrderService.InvalidTransitionException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage()));
        } catch (OrderService.InvalidStatusException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/orders/{orderId}")
    public ResponseEntity<?> deleteOrder(@PathVariable String orderId) {
        try {
            orderService.deleteOrder(orderId);
            return ResponseEntity.noContent().build();
        } catch (OrderService.OrderNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (OrderService.InvalidTransitionException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
