package com.example.ecommerce.controller;

import com.example.ecommerce.model.CreateOrderRequest;
import com.example.ecommerce.model.Order;
import com.example.ecommerce.model.UpdateStatusRequest;
import com.example.ecommerce.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class OrderController {

    @Autowired
    private OrderService orderService;

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }

    @PostMapping("/api/orders")
    public ResponseEntity<Order> createOrder(@RequestBody CreateOrderRequest request) {
        Order order = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }

    @GetMapping("/api/orders/{orderId}")
    public ResponseEntity<Order> getOrder(@PathVariable String orderId) {
        Order order = orderService.getOrderById(orderId);
        if (order == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(order);
    }

    @GetMapping("/api/customers/{customerId}/orders")
    public ResponseEntity<List<Order>> getCustomerOrders(@PathVariable String customerId) {
        List<Order> orders = orderService.getOrdersByCustomer(customerId);
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/api/orders")
    public ResponseEntity<List<Order>> queryOrders(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        if (status != null) {
            return ResponseEntity.ok(orderService.getOrdersByStatus(status));
        } else if (startDate != null && endDate != null) {
            return ResponseEntity.ok(orderService.getOrdersByDateRange(startDate, endDate));
        } else {
            return ResponseEntity.badRequest().build();
        }
    }

    @PatchMapping("/api/orders/{orderId}/status")
    public ResponseEntity<Order> updateOrderStatus(
            @PathVariable String orderId,
            @RequestBody UpdateStatusRequest request) {
        Order order = orderService.updateOrderStatus(orderId, request.getStatus());
        if (order == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(order);
    }
}
