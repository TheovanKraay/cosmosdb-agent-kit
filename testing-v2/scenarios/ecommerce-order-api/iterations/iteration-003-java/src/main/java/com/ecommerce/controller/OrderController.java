package com.ecommerce.controller;

import com.ecommerce.model.Order;
import com.ecommerce.model.OrderItem;
import com.ecommerce.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

@RestController
public class OrderController {

    private static final Logger logger = LoggerFactory.getLogger(OrderController.class);
    private final OrderRepository orderRepository;

    private static final Set<String> VALID_STATUSES = Set.of("pending", "shipped", "delivered", "cancelled");
    private static final Set<String> TERMINAL_STATUSES = Set.of("delivered", "cancelled");

    public OrderController(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "healthy"));
    }

    @PostMapping("/api/orders")
    public ResponseEntity<?> createOrder(@RequestBody Map<String, Object> body) {
        try {
            String customerId = (String) body.get("customerId");
            if (customerId == null || customerId.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "customerId is required"));
            }

            Object itemsObj = body.get("items");
            if (itemsObj == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "items is required"));
            }

            List<?> rawItems = (List<?>) itemsObj;
            if (rawItems.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "items must not be empty"));
            }

            List<OrderItem> items = new ArrayList<>();
            for (Object rawItem : rawItems) {
                Map<String, Object> itemMap = (Map<String, Object>) rawItem;
                OrderItem item = new OrderItem();
                item.setProductId((String) itemMap.get("productId"));
                item.setProductName((String) itemMap.get("productName"));
                item.setQuantity(((Number) itemMap.get("quantity")).intValue());
                item.setUnitPrice(((Number) itemMap.get("unitPrice")).doubleValue());
                items.add(item);
            }

            String orderId = UUID.randomUUID().toString();

            Order order = new Order();
            order.setId(orderId);
            order.setOrderId(orderId);
            order.setCustomerId(customerId);
            order.setStatus("pending");
            order.setItems(items);
            order.setCreatedAt(Instant.now().toString());
            order.setShippingAddress((String) body.get("shippingAddress"));
            order.calculateTotal();

            Order created = orderRepository.createOrder(order).block();

            Map<String, Object> response = buildOrderResponse(created);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            logger.error("Error creating order", e);
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid request: " + e.getMessage()));
        }
    }

    @GetMapping("/api/orders/{orderId}")
    public ResponseEntity<?> getOrder(@PathVariable String orderId) {
        try {
            Order order = orderRepository.getOrderById(orderId).block();
            if (order == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(buildOrderResponse(order));
        } catch (Exception e) {
            logger.error("Error getting order {}", orderId, e);
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/api/customers/{customerId}/orders")
    public ResponseEntity<?> getCustomerOrders(@PathVariable String customerId) {
        try {
            List<Order> orders = orderRepository.getOrdersByCustomer(customerId).block();
            if (orders == null) {
                orders = Collections.emptyList();
            }
            List<Map<String, Object>> response = orders.stream()
                    .map(this::buildOrderResponse)
                    .toList();
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting orders for customer {}", customerId, e);
            return ResponseEntity.ok(Collections.emptyList());
        }
    }

    @GetMapping("/api/customers/{customerId}/orders/summary")
    public ResponseEntity<?> getCustomerSummary(@PathVariable String customerId) {
        try {
            List<Order> orders = orderRepository.getOrdersByCustomer(customerId).block();
            if (orders == null) {
                orders = Collections.emptyList();
            }

            int totalOrders = orders.size();
            double totalSpent = orders.stream().mapToDouble(Order::getTotal).sum();
            totalSpent = Math.round(totalSpent * 100.0) / 100.0;
            double averageOrderValue = totalOrders > 0 ? totalSpent / totalOrders : 0.0;

            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("customerId", customerId);
            summary.put("totalOrders", totalOrders);
            summary.put("totalSpent", totalSpent);
            summary.put("averageOrderValue", averageOrderValue);

            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            logger.error("Error getting summary for customer {}", customerId, e);
            return ResponseEntity.ok(Map.of(
                    "customerId", customerId,
                    "totalOrders", 0,
                    "totalSpent", 0.0,
                    "averageOrderValue", 0.0
            ));
        }
    }

    @GetMapping("/api/orders")
    public ResponseEntity<?> queryOrders(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        try {
            List<Order> orders;
            if (status != null && !status.isBlank()) {
                orders = orderRepository.getOrdersByStatus(status).block();
            } else if (startDate != null && endDate != null) {
                orders = orderRepository.getOrdersByDateRange(startDate, endDate).block();
            } else {
                orders = Collections.emptyList();
            }

            if (orders == null) {
                orders = Collections.emptyList();
            }

            List<Map<String, Object>> response = orders.stream()
                    .map(this::buildOrderResponse)
                    .toList();
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error querying orders", e);
            return ResponseEntity.ok(Collections.emptyList());
        }
    }

    @PatchMapping("/api/orders/{orderId}/status")
    public ResponseEntity<?> updateOrderStatus(@PathVariable String orderId, @RequestBody Map<String, String> body) {
        try {
            String newStatus = body.get("status");
            if (newStatus == null || !VALID_STATUSES.contains(newStatus)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid status"));
            }

            Order order = orderRepository.getOrderById(orderId).block();
            if (order == null) {
                return ResponseEntity.notFound().build();
            }

            String currentStatus = order.getStatus();

            if (!isValidTransition(currentStatus, newStatus)) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("error", "Invalid status transition from " + currentStatus + " to " + newStatus));
            }

            order.setStatus(newStatus);
            Order updated = orderRepository.updateOrder(order).block();

            return ResponseEntity.ok(buildOrderResponse(updated));
        } catch (Exception e) {
            logger.error("Error updating order status {}", orderId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update order status"));
        }
    }

    @DeleteMapping("/api/orders/{orderId}")
    public ResponseEntity<?> deleteOrder(@PathVariable String orderId) {
        try {
            Order order = orderRepository.getOrderById(orderId).block();
            if (order == null) {
                return ResponseEntity.notFound().build();
            }

            if (!"pending".equals(order.getStatus())) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("error", "Only pending orders can be deleted"));
            }

            orderRepository.deleteOrder(order.getId(), order.getCustomerId()).block();
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error deleting order {}", orderId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to delete order"));
        }
    }

    private boolean isValidTransition(String currentStatus, String newStatus) {
        if (TERMINAL_STATUSES.contains(currentStatus)) {
            return false;
        }
        if ("pending".equals(currentStatus)) {
            return VALID_STATUSES.contains(newStatus) && !"pending".equals(newStatus);
        }
        if ("shipped".equals(currentStatus)) {
            return "delivered".equals(newStatus);
        }
        return false;
    }

    private Map<String, Object> buildOrderResponse(Order order) {
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
