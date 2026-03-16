package com.example.ecommerce.controller;

import com.example.ecommerce.dto.CreateOrderRequest;
import com.example.ecommerce.dto.CustomerSummaryResponse;
import com.example.ecommerce.dto.OrderResponse;
import com.example.ecommerce.dto.UpdateStatusRequest;
import com.example.ecommerce.service.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API controller for order management.
 * Implements all endpoints defined in api-contract.yaml.
 */
@RestController
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    // -----------------------------------------------------------------------
    // Health check
    // -----------------------------------------------------------------------

    /** GET /health — Returns 200 when the application is ready. */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    // -----------------------------------------------------------------------
    // Order endpoints
    // -----------------------------------------------------------------------

    /**
     * POST /api/orders — Create a new order.
     * Validates required fields and returns 400 for invalid input.
     */
    @PostMapping("/api/orders")
    public ResponseEntity<?> createOrder(@RequestBody(required = false) CreateOrderRequest request) {
        // Validate required fields (return 400, not 500, on bad input)
        if (request == null || request.getCustomerId() == null || request.getCustomerId().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "customerId is required"));
        }
        if (request.getItems() == null || request.getItems().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "items must be a non-empty array"));
        }

        OrderResponse created = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * GET /api/orders/{orderId} — Get order by ID.
     * Returns 404 if the order does not exist.
     */
    @GetMapping("/api/orders/{orderId}")
    public ResponseEntity<?> getOrder(@PathVariable String orderId) {
        OrderResponse order = orderService.getOrderById(orderId);
        if (order == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Order not found: " + orderId));
        }
        return ResponseEntity.ok(order);
    }

    /**
     * GET /api/orders?status=X — Query orders by status.
     * GET /api/orders?startDate=X&endDate=Y — Query orders by date range.
     * Both are admin-level cross-partition queries.
     */
    @GetMapping("/api/orders")
    public ResponseEntity<?> queryOrders(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        if (status != null) {
            List<OrderResponse> orders = orderService.getOrdersByStatus(status);
            return ResponseEntity.ok(orders);
        }

        if (startDate != null && endDate != null) {
            List<OrderResponse> orders = orderService.getOrdersByDateRange(startDate, endDate);
            return ResponseEntity.ok(orders);
        }

        return ResponseEntity.badRequest()
                .body(Map.of("error", "Provide either 'status' or 'startDate' and 'endDate' query parameters"));
    }

    /**
     * PATCH /api/orders/{orderId}/status — Update order status.
     * Returns 409 for invalid transitions.
     * Returns 404 if the order does not exist.
     */
    @PatchMapping("/api/orders/{orderId}/status")
    public ResponseEntity<?> updateOrderStatus(
            @PathVariable String orderId,
            @RequestBody(required = false) UpdateStatusRequest request) {

        if (request == null || request.getStatus() == null || request.getStatus().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "status is required"));
        }

        try {
            OrderResponse updated = orderService.updateOrderStatus(orderId, request.getStatus());
            if (updated == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Order not found: " + orderId));
            }
            return ResponseEntity.ok(updated);
        } catch (OrderService.InvalidTransitionException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * DELETE /api/orders/{orderId} — Delete a pending order.
     * Returns 204 on success, 404 if not found, 409 if not pending.
     */
    @DeleteMapping("/api/orders/{orderId}")
    public ResponseEntity<?> deleteOrder(@PathVariable String orderId) {
        try {
            boolean deleted = orderService.deleteOrder(orderId);
            if (!deleted) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Order not found: " + orderId));
            }
            return ResponseEntity.noContent().build();
        } catch (OrderService.NonPendingDeleteException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // -----------------------------------------------------------------------
    // Customer endpoints
    // -----------------------------------------------------------------------

    /**
     * GET /api/customers/{customerId}/orders — Get customer order history.
     * Single-partition query using customerId partition key.
     */
    @GetMapping("/api/customers/{customerId}/orders")
    public ResponseEntity<List<OrderResponse>> getCustomerOrders(@PathVariable String customerId) {
        List<OrderResponse> orders = orderService.getOrdersByCustomer(customerId);
        return ResponseEntity.ok(orders);
    }

    /**
     * GET /api/customers/{customerId}/orders/summary — Customer order statistics.
     * Returns aggregated totals and averages for a customer.
     */
    @GetMapping("/api/customers/{customerId}/orders/summary")
    public ResponseEntity<CustomerSummaryResponse> getCustomerSummary(@PathVariable String customerId) {
        CustomerSummaryResponse summary = orderService.getCustomerSummary(customerId);
        return ResponseEntity.ok(summary);
    }
}
