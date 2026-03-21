package com.ecommerce.controller;

import com.ecommerce.model.CustomerSummary;
import com.ecommerce.model.Order;
import com.ecommerce.model.OrderItem;
import com.ecommerce.repository.OrderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@RestController
public class OrderController {

    private static final Set<String> VALID_STATUSES = Set.of("pending", "shipped", "delivered", "cancelled");

    private final OrderRepository orderRepository;

    public OrderController(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    /**
     * Health check endpoint.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "healthy"));
    }

    /**
     * POST /api/orders - Create a new order.
     * Auto-calculates total as sum of (quantity * unitPrice) for all items.
     * Sets status to "pending" and createdAt to current ISO-8601 timestamp.
     */
    @PostMapping("/api/orders")
    public ResponseEntity<?> createOrder(@RequestBody Map<String, Object> body) {
        String customerId = (String) body.get("customerId");
        if (customerId == null || customerId.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "customerId is required"));
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> itemsList = (List<Map<String, Object>>) body.get("items");
        if (itemsList == null || itemsList.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "items are required and must not be empty"));
        }

        List<OrderItem> items = itemsList.stream().map(item -> {
            String productId = (String) item.get("productId");
            String productName = (String) item.get("productName");
            int quantity = item.get("quantity") instanceof Number n ? n.intValue() : 0;
            double unitPrice = item.get("unitPrice") instanceof Number n ? n.doubleValue() : 0.0;
            return new OrderItem(productId, productName, quantity, unitPrice);
        }).toList();

        double total = items.stream()
                .mapToDouble(i -> i.getQuantity() * i.getUnitPrice())
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
        order.setShippingAddress((String) body.get("shippingAddress"));

        Order created = orderRepository.createOrder(order);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * GET /api/orders/{orderId} - Get order by ID.
     */
    @GetMapping("/api/orders/{orderId}")
    public ResponseEntity<?> getOrder(@PathVariable String orderId) {
        Optional<Order> order = orderRepository.findById(orderId);
        return order.<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Order not found")));
    }

    /**
     * GET /api/orders?status=X or GET /api/orders?startDate=X&endDate=Y
     */
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

    /**
     * GET /api/customers/{customerId}/orders - Get customer order history.
     */
    @GetMapping("/api/customers/{customerId}/orders")
    public ResponseEntity<List<Order>> getCustomerOrders(@PathVariable String customerId) {
        List<Order> orders = orderRepository.findByCustomerId(customerId);
        return ResponseEntity.ok(orders);
    }

    /**
     * GET /api/customers/{customerId}/orders/summary - Customer order summary.
     */
    @GetMapping("/api/customers/{customerId}/orders/summary")
    public ResponseEntity<CustomerSummary> getCustomerSummary(@PathVariable String customerId) {
        List<Order> orders = orderRepository.findByCustomerId(customerId);

        int totalOrders = orders.size();
        double totalSpent = orders.stream().mapToDouble(Order::getTotal).sum();
        double averageOrderValue = totalOrders > 0 ? totalSpent / totalOrders : 0.0;

        CustomerSummary summary = new CustomerSummary(customerId, totalOrders, totalSpent, averageOrderValue);
        return ResponseEntity.ok(summary);
    }

    /**
     * PATCH /api/orders/{orderId}/status - Update order status.
     * Valid transitions:
     *   pending → shipped, cancelled, delivered (200)
     *   shipped → delivered (200)
     *   All others → 409 Conflict
     */
    @PatchMapping("/api/orders/{orderId}/status")
    public ResponseEntity<?> updateOrderStatus(
            @PathVariable String orderId,
            @RequestBody Map<String, String> body) {

        String newStatus = body.get("status");
        if (newStatus == null || newStatus.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "status is required"));
        }

        if (!VALID_STATUSES.contains(newStatus)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid status value: " + newStatus));
        }

        Optional<Order> optOrder = orderRepository.findById(orderId);
        if (optOrder.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Order not found"));
        }

        Order order = optOrder.get();
        String currentStatus = order.getStatus();

        if (!isValidTransition(currentStatus, newStatus)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Invalid status transition from " + currentStatus + " to " + newStatus));
        }

        order.setStatus(newStatus);
        Order updated = orderRepository.replaceOrder(order);
        return ResponseEntity.ok(updated);
    }

    /**
     * DELETE /api/orders/{orderId} - Delete order (only pending orders).
     */
    @DeleteMapping("/api/orders/{orderId}")
    public ResponseEntity<?> deleteOrder(@PathVariable String orderId) {
        Optional<Order> optOrder = orderRepository.findById(orderId);
        if (optOrder.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Order not found"));
        }

        Order order = optOrder.get();
        if (!"pending".equals(order.getStatus())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Only pending orders can be deleted"));
        }

        orderRepository.deleteOrder(order.getId(), order.getCustomerId());
        return ResponseEntity.noContent().build();
    }

    /**
     * Check if a status transition is valid.
     * pending → shipped, cancelled, delivered are valid
     * shipped → delivered is valid
     * delivered and cancelled are terminal states (no transitions allowed)
     */
    private boolean isValidTransition(String currentStatus, String newStatus) {
        if (currentStatus.equals(newStatus)) {
            return false;
        }
        return switch (currentStatus) {
            case "pending" -> VALID_STATUSES.contains(newStatus) && !"pending".equals(newStatus);
            case "shipped" -> "delivered".equals(newStatus);
            default -> false; // delivered and cancelled are terminal
        };
    }
}
