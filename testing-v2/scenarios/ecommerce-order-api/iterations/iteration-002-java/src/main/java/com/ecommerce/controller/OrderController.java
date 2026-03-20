package com.ecommerce.controller;

import com.azure.cosmos.CosmosException;
import com.ecommerce.model.Order;
import com.ecommerce.model.OrderItem;
import com.ecommerce.repository.OrderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

@RestController
public class OrderController {

    private final OrderRepository orderRepository;

    // Valid status transitions: pending→shipped, pending→cancelled, pending→delivered, shipped→delivered
    // Terminal states: delivered, cancelled (no transitions allowed out)
    private static final Set<String> VALID_STATUSES = Set.of("pending", "shipped", "delivered", "cancelled");

    public OrderController(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }

    @PostMapping("/api/orders")
    public ResponseEntity<?> createOrder(@RequestBody(required = false) Map<String, Object> body) {
        if (body == null || body.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Request body is required"));
        }

        String customerId = (String) body.get("customerId");
        if (customerId == null || customerId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "customerId is required"));
        }

        Object itemsObj = body.get("items");
        if (itemsObj == null || !(itemsObj instanceof List) || ((List<?>) itemsObj).isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "items must be a non-empty array"));
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> itemMaps = (List<Map<String, Object>>) itemsObj;

        List<OrderItem> items = new ArrayList<>();
        double total = 0;

        for (Map<String, Object> itemMap : itemMaps) {
            OrderItem item = new OrderItem();
            item.setProductId((String) itemMap.get("productId"));
            item.setProductName((String) itemMap.get("productName"));
            item.setQuantity(((Number) itemMap.get("quantity")).intValue());
            item.setUnitPrice(((Number) itemMap.get("unitPrice")).doubleValue());
            items.add(item);
            total += item.getQuantity() * item.getUnitPrice();
        }

        // Round total to avoid floating-point precision issues
        total = Math.round(total * 100.0) / 100.0;

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

        orderRepository.createOrder(order);

        return ResponseEntity.status(HttpStatus.CREATED).body(orderToResponse(order));
    }

    @GetMapping("/api/orders/{orderId}")
    public ResponseEntity<?> getOrder(@PathVariable String orderId) {
        Optional<Order> orderOpt = orderRepository.findById(orderId);
        if (orderOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Order not found"));
        }
        return ResponseEntity.ok(orderToResponse(orderOpt.get()));
    }

    @GetMapping("/api/customers/{customerId}/orders")
    public ResponseEntity<List<Map<String, Object>>> getCustomerOrders(@PathVariable String customerId) {
        List<Order> orders = orderRepository.findByCustomerId(customerId);
        List<Map<String, Object>> response = orders.stream()
                .map(this::orderToResponse)
                .toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/customers/{customerId}/orders/summary")
    public ResponseEntity<Map<String, Object>> getCustomerSummary(@PathVariable String customerId) {
        List<Order> orders = orderRepository.findByCustomerId(customerId);

        int totalOrders = orders.size();
        double totalSpent = orders.stream().mapToDouble(Order::getTotal).sum();
        totalSpent = Math.round(totalSpent * 100.0) / 100.0;
        double averageOrderValue = totalOrders > 0 ? Math.round((totalSpent / totalOrders) * 100.0) / 100.0 : 0;

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("customerId", customerId);
        summary.put("totalOrders", totalOrders);
        summary.put("totalSpent", totalSpent);
        summary.put("averageOrderValue", averageOrderValue);

        return ResponseEntity.ok(summary);
    }

    @GetMapping("/api/orders")
    public ResponseEntity<List<Map<String, Object>>> queryOrders(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        List<Order> orders;

        if (status != null && !status.isBlank()) {
            orders = orderRepository.findByStatus(status);
        } else if (startDate != null && endDate != null) {
            orders = orderRepository.findByDateRange(startDate, endDate);
        } else {
            orders = orderRepository.findByStatus("pending");
        }

        List<Map<String, Object>> response = orders.stream()
                .map(this::orderToResponse)
                .toList();
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/api/orders/{orderId}/status")
    public ResponseEntity<?> updateOrderStatus(@PathVariable String orderId, @RequestBody(required = false) Map<String, Object> body) {
        if (body == null || !body.containsKey("status")) {
            return ResponseEntity.badRequest().body(Map.of("error", "status is required"));
        }

        String newStatus = (String) body.get("status");
        if (newStatus == null || !VALID_STATUSES.contains(newStatus)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid status value. Must be one of: pending, shipped, delivered, cancelled"));
        }

        Optional<Order> orderOpt = orderRepository.findById(orderId);
        if (orderOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Order not found"));
        }

        Order order = orderOpt.get();
        String currentStatus = order.getStatus();

        // Check valid transitions
        if (!isValidTransition(currentStatus, newStatus)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error",
                    "Invalid status transition from '" + currentStatus + "' to '" + newStatus + "'"));
        }

        order.setStatus(newStatus);
        orderRepository.replaceOrder(order);

        return ResponseEntity.ok(orderToResponse(order));
    }

    @DeleteMapping("/api/orders/{orderId}")
    public ResponseEntity<?> deleteOrder(@PathVariable String orderId) {
        Optional<Order> orderOpt = orderRepository.findById(orderId);
        if (orderOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Order not found"));
        }

        Order order = orderOpt.get();
        if (!"pending".equals(order.getStatus())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Only pending orders can be deleted"));
        }

        orderRepository.deleteOrder(order.getId(), order.getCustomerId());
        return ResponseEntity.noContent().build();
    }

    private boolean isValidTransition(String currentStatus, String newStatus) {
        if (currentStatus.equals(newStatus)) {
            return false;
        }
        return switch (currentStatus) {
            case "pending" -> VALID_STATUSES.contains(newStatus) && !newStatus.equals("pending");
            case "shipped" -> "delivered".equals(newStatus);
            case "delivered", "cancelled" -> false;
            default -> false;
        };
    }

    private Map<String, Object> orderToResponse(Order order) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("orderId", order.getOrderId());
        response.put("customerId", order.getCustomerId());
        response.put("status", order.getStatus());
        response.put("items", order.getItems());
        response.put("total", order.getTotal());
        response.put("createdAt", order.getCreatedAt());
        if (order.getShippingAddress() != null) {
            response.put("shippingAddress", order.getShippingAddress());
        }
        return response;
    }
}
