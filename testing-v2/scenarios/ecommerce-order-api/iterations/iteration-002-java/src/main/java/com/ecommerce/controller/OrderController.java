package com.ecommerce.controller;

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

    // Valid status transitions: pending→shipped, pending→cancelled, pending→delivered,
    // shipped→delivered. Terminal states: delivered, cancelled.
    private static final Map<String, Set<String>> VALID_TRANSITIONS = Map.of(
            "pending", Set.of("shipped", "cancelled", "delivered"),
            "shipped", Set.of("delivered")
    );

    public OrderController(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @PostMapping("/api/orders")
    public ResponseEntity<?> createOrder(@RequestBody Map<String, Object> body) {
        String customerId = (String) body.get("customerId");
        if (customerId == null || customerId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "customerId is required"));
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> itemMaps = (List<Map<String, Object>>) body.get("items");
        if (itemMaps == null || itemMaps.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "items are required"));
        }

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
        order.setType("order");
        order.setSchemaVersion("1.0");

        Order created = orderRepository.createOrder(order);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponseMap(created));
    }

    @GetMapping("/api/orders/{orderId}")
    public ResponseEntity<?> getOrder(@PathVariable String orderId) {
        Order order = orderRepository.getOrderById(orderId);
        if (order == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Order not found"));
        }
        return ResponseEntity.ok(toResponseMap(order));
    }

    @GetMapping("/api/customers/{customerId}/orders")
    public ResponseEntity<List<Map<String, Object>>> getCustomerOrders(
            @PathVariable String customerId) {
        List<Order> orders = orderRepository.getOrdersByCustomerId(customerId);
        List<Map<String, Object>> response = new ArrayList<>();
        for (Order o : orders) {
            response.add(toResponseMap(o));
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/customers/{customerId}/orders/summary")
    public ResponseEntity<Map<String, Object>> getCustomerSummary(
            @PathVariable String customerId) {
        List<Order> orders = orderRepository.getOrdersByCustomerId(customerId);
        int totalOrders = orders.size();
        double totalSpent = 0;
        for (Order o : orders) {
            totalSpent += o.getTotal();
        }
        double averageOrderValue = totalOrders > 0 ? totalSpent / totalOrders : 0;

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
            orders = orderRepository.getOrdersByStatus(status);
        } else if (startDate != null && endDate != null) {
            orders = orderRepository.getOrdersByDateRange(startDate, endDate);
        } else {
            orders = orderRepository.getOrdersByStatus("pending");
        }

        List<Map<String, Object>> response = new ArrayList<>();
        for (Order o : orders) {
            response.add(toResponseMap(o));
        }
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/api/orders/{orderId}/status")
    public ResponseEntity<?> updateOrderStatus(@PathVariable String orderId,
                                                @RequestBody Map<String, String> body) {
        String newStatus = body.get("status");
        if (newStatus == null || newStatus.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "status is required"));
        }

        Order order = orderRepository.getOrderById(orderId);
        if (order == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Order not found"));
        }

        String currentStatus = order.getStatus();
        Set<String> allowed = VALID_TRANSITIONS.get(currentStatus);
        if (allowed == null || !allowed.contains(newStatus)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Invalid status transition from " +
                            currentStatus + " to " + newStatus));
        }

        order.setStatus(newStatus);
        Order updated = orderRepository.updateOrder(order);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("orderId", updated.getOrderId());
        response.put("status", updated.getStatus());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/api/orders/{orderId}")
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

    private Map<String, Object> toResponseMap(Order order) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("orderId", order.getOrderId());
        map.put("customerId", order.getCustomerId());
        map.put("status", order.getStatus());
        map.put("items", order.getItems());
        map.put("total", order.getTotal());
        map.put("createdAt", order.getCreatedAt());
        if (order.getShippingAddress() != null) {
            map.put("shippingAddress", order.getShippingAddress());
        }
        return map;
    }
}
