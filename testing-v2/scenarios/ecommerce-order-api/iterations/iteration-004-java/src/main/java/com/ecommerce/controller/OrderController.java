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

    public OrderController(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "healthy"));
    }

    @PostMapping("/api/orders")
    public ResponseEntity<?> createOrder(@RequestBody Map<String, Object> body) {
        String customerId = (String) body.get("customerId");
        if (customerId == null || customerId.isEmpty()) {
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

        String shippingAddress = (String) body.get("shippingAddress");
        if (shippingAddress != null) {
            order.setShippingAddress(shippingAddress);
        }

        Order created = orderRepository.createOrder(order);
        if (created == null) {
            created = order;
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(orderToMap(created));
    }

    @GetMapping("/api/orders/{orderId}")
    public ResponseEntity<?> getOrder(@PathVariable String orderId) {
        Order order = orderRepository.getOrderById(orderId);
        if (order == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Order not found"));
        }
        return ResponseEntity.ok(orderToMap(order));
    }

    @GetMapping("/api/orders")
    public ResponseEntity<?> queryOrders(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        List<Order> orders;
        if (status != null && !status.isEmpty()) {
            orders = orderRepository.getOrdersByStatus(status);
        } else if (startDate != null && endDate != null) {
            orders = orderRepository.getOrdersByDateRange(startDate, endDate);
        } else {
            orders = new ArrayList<>();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Order o : orders) {
            result.add(orderToMap(o));
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/api/customers/{customerId}/orders")
    public ResponseEntity<?> getCustomerOrders(@PathVariable String customerId) {
        List<Order> orders = orderRepository.getOrdersByCustomerId(customerId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Order o : orders) {
            result.add(orderToMap(o));
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/api/customers/{customerId}/orders/summary")
    public ResponseEntity<?> getCustomerSummary(@PathVariable String customerId) {
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

    @PatchMapping("/api/orders/{orderId}/status")
    public ResponseEntity<?> updateOrderStatus(
            @PathVariable String orderId,
            @RequestBody Map<String, String> body) {
        String newStatus = body.get("status");
        if (newStatus == null || newStatus.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "status is required"));
        }

        Order order = orderRepository.getOrderById(orderId);
        if (order == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Order not found"));
        }

        if (!isValidTransition(order.getStatus(), newStatus)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Invalid status transition from " +
                            order.getStatus() + " to " + newStatus));
        }

        order.setStatus(newStatus);
        Order updated = orderRepository.updateOrder(order);
        if (updated == null) {
            updated = order;
        }

        return ResponseEntity.ok(orderToMap(updated));
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

        orderRepository.deleteOrder(order.getId(), order.getCustomerId());
        return ResponseEntity.noContent().build();
    }

    private boolean isValidTransition(String currentStatus, String newStatus) {
        if (currentStatus.equals(newStatus)) {
            return false;
        }
        return switch (currentStatus) {
            case "pending" -> "shipped".equals(newStatus) || "cancelled".equals(newStatus)
                    || "delivered".equals(newStatus);
            case "shipped" -> "delivered".equals(newStatus);
            default -> false;
        };
    }

    private Map<String, Object> orderToMap(Order order) {
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
