package com.ecommerce.controller;

import com.ecommerce.model.CreateOrderRequest;
import com.ecommerce.model.CustomerSummary;
import com.ecommerce.model.Order;
import com.ecommerce.model.UpdateStatusRequest;
import com.ecommerce.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * REST controller implementing all endpoints from the API contract.
 *
 * Implements:
 * - GET  /health                              → 200
 * - POST /api/orders                          → 201 with order
 * - GET  /api/orders/{orderId}                → 200 or 404
 * - GET  /api/customers/{customerId}/orders   → 200 with array
 * - GET  /api/customers/{customerId}/orders/summary → 200 with summary
 * - GET  /api/orders?status=X                 → 200 with filtered array
 * - GET  /api/orders?startDate=X&endDate=Y    → 200 with filtered array
 * - PATCH /api/orders/{orderId}/status        → 200 or 409
 * - DELETE /api/orders/{orderId}              → 204 or 409
 */
@RestController
public class OrderController {

    private static final Logger logger = LoggerFactory.getLogger(OrderController.class);

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * Health check endpoint.
     * Returns 200 when the application is ready.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    /**
     * Create a new order.
     * POST /api/orders
     * Body: {customerId, items[{productId, productName, quantity, unitPrice}]}
     * Returns: 201 with full order object
     */
    @PostMapping("/api/orders")
    public ResponseEntity<Order> createOrder(@RequestBody CreateOrderRequest request) {
        Order created = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Get an order by ID.
     * GET /api/orders/{orderId}
     * Returns: 200 with order or 404 if not found.
     */
    @GetMapping("/api/orders/{orderId}")
    public ResponseEntity<Order> getOrder(@PathVariable String orderId) {
        Order order = orderService.getOrder(orderId);
        return ResponseEntity.ok(order);
    }

    /**
     * Query orders by status or date range.
     * GET /api/orders?status=X
     * GET /api/orders?startDate=X&endDate=Y
     */
    @GetMapping("/api/orders")
    public ResponseEntity<List<Order>> queryOrders(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        if (status != null) {
            return ResponseEntity.ok(orderService.getOrdersByStatus(status));
        } else if (startDate != null || endDate != null) {
            return ResponseEntity.ok(orderService.getOrdersByDateRange(startDate, endDate));
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Query parameter 'status' or 'startDate'/'endDate' is required");
        }
    }

    /**
     * Get all orders for a customer.
     * GET /api/customers/{customerId}/orders
     * Returns: 200 with array of orders.
     */
    @GetMapping("/api/customers/{customerId}/orders")
    public ResponseEntity<List<Order>> getCustomerOrders(@PathVariable String customerId) {
        List<Order> orders = orderService.getCustomerOrders(customerId);
        return ResponseEntity.ok(orders);
    }

    /**
     * Get customer order summary.
     * GET /api/customers/{customerId}/orders/summary
     * Returns: 200 with {customerId, totalOrders, totalSpent, averageOrderValue}.
     */
    @GetMapping("/api/customers/{customerId}/orders/summary")
    public ResponseEntity<CustomerSummary> getCustomerSummary(@PathVariable String customerId) {
        CustomerSummary summary = orderService.getCustomerSummary(customerId);
        return ResponseEntity.ok(summary);
    }

    /**
     * Update order status.
     * PATCH /api/orders/{orderId}/status
     * Body: {status}
     * Returns: 200 with updated order or 409 for invalid transition.
     */
    @PatchMapping("/api/orders/{orderId}/status")
    public ResponseEntity<Order> updateOrderStatus(
            @PathVariable String orderId,
            @RequestBody UpdateStatusRequest request) {
        if (request.getStatus() == null || request.getStatus().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status is required");
        }
        Order updated = orderService.updateOrderStatus(orderId, request.getStatus());
        return ResponseEntity.ok(updated);
    }

    /**
     * Delete an order (pending only).
     * DELETE /api/orders/{orderId}
     * Returns: 204 on success, 404 if not found, 409 if not pending.
     */
    @DeleteMapping("/api/orders/{orderId}")
    public ResponseEntity<Void> deleteOrder(@PathVariable String orderId) {
        orderService.deleteOrder(orderId);
        return ResponseEntity.noContent().build();
    }
}
