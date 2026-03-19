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

    private static final Set<String> VALID_STATUSES = Set.of("pending", "shipped", "delivered", "cancelled");

    public OrderController(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "healthy"));
    }

    @PostMapping("/api/orders")
    public ResponseEntity<?> createOrder(@RequestBody(required = false) Map<String, Object> body) {
        if (body == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Request body is required"));
        }

        String customerId = (String) body.get("customerId");
        if (customerId == null || customerId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "customerId is required"));
        }

        Object itemsObj = body.get("items");
        if (itemsObj == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "items is required"));
        }

        List<?> itemsList;
        if (itemsObj instanceof List<?>) {
            itemsList = (List<?>) itemsObj;
        } else {
            return ResponseEntity.badRequest().body(Map.of("error", "items must be an array"));
        }

        if (itemsList.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "items cannot be empty"));
        }

        List<OrderItem> orderItems = new ArrayList<>();
        for (Object itemObj : itemsList) {
            if (!(itemObj instanceof Map)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid item format"));
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> itemMap = (Map<String, Object>) itemObj;

            OrderItem item = new OrderItem();
            item.setProductId((String) itemMap.get("productId"));
            item.setProductName((String) itemMap.get("productName"));

            Object qtyObj = itemMap.get("quantity");
            if (qtyObj instanceof Number) {
                item.setQuantity(((Number) qtyObj).intValue());
            }

            Object priceObj = itemMap.get("unitPrice");
            if (priceObj instanceof Number) {
                item.setUnitPrice(((Number) priceObj).doubleValue());
            }

            orderItems.add(item);
        }

        Order order = new Order();
        order.setCustomerId(customerId);
        order.setItems(orderItems);
        order.calculateTotal();

        String shippingAddress = (String) body.get("shippingAddress");
        if (shippingAddress != null) {
            order.setShippingAddress(shippingAddress);
        }

        try {
            Order created = orderRepository.createOrder(order).block();
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to create order: " + e.getMessage()));
        }
    }

    @GetMapping("/api/orders/{orderId}")
    public ResponseEntity<?> getOrder(@PathVariable String orderId) {
        try {
            Order order = orderRepository.getOrderById(orderId).block();
            if (order == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Order not found"));
            }
            return ResponseEntity.ok(order);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Order not found"));
        }
    }

    @GetMapping("/api/customers/{customerId}/orders")
    public ResponseEntity<?> getCustomerOrders(@PathVariable String customerId) {
        try {
            List<Order> orders = orderRepository.getOrdersByCustomer(customerId)
                    .collectList().block();
            return ResponseEntity.ok(orders != null ? orders : Collections.emptyList());
        } catch (Exception e) {
            return ResponseEntity.ok(Collections.emptyList());
        }
    }

    @GetMapping("/api/customers/{customerId}/orders/summary")
    public ResponseEntity<?> getCustomerSummary(@PathVariable String customerId) {
        try {
            List<Order> orders = orderRepository.getOrdersByCustomer(customerId)
                    .collectList().block();

            if (orders == null) {
                orders = Collections.emptyList();
            }

            int totalOrders = orders.size();
            double totalSpent = orders.stream().mapToDouble(Order::getTotal).sum();
            totalSpent = Math.round(totalSpent * 100.0) / 100.0;
            double averageOrderValue = totalOrders > 0 ? Math.round((totalSpent / totalOrders) * 100.0) / 100.0 : 0.0;

            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("customerId", customerId);
            summary.put("totalOrders", totalOrders);
            summary.put("totalSpent", totalSpent);
            summary.put("averageOrderValue", averageOrderValue);

            return ResponseEntity.ok(summary);
        } catch (Exception e) {
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
            if (status != null && !status.isBlank()) {
                List<Order> orders = orderRepository.getOrdersByStatus(status)
                        .collectList().block();
                return ResponseEntity.ok(orders != null ? orders : Collections.emptyList());
            }

            if (startDate != null && endDate != null) {
                List<Order> orders = orderRepository.getOrdersByDateRange(startDate, endDate)
                        .collectList().block();
                return ResponseEntity.ok(orders != null ? orders : Collections.emptyList());
            }

            return ResponseEntity.ok(Collections.emptyList());
        } catch (Exception e) {
            return ResponseEntity.ok(Collections.emptyList());
        }
    }

    @PatchMapping("/api/orders/{orderId}/status")
    public ResponseEntity<?> updateOrderStatus(@PathVariable String orderId,
                                                @RequestBody(required = false) Map<String, Object> body) {
        if (body == null || !body.containsKey("status")) {
            return ResponseEntity.badRequest().body(Map.of("error", "status is required"));
        }

        String newStatus = (String) body.get("status");
        if (newStatus == null || !VALID_STATUSES.contains(newStatus)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid status value"));
        }

        try {
            Order order = orderRepository.getOrderById(orderId).block();
            if (order == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Order not found"));
            }

            String currentStatus = order.getStatus();

            // Validate status transitions
            if (!isValidTransition(currentStatus, newStatus)) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("error", "Invalid status transition from " + currentStatus + " to " + newStatus));
            }

            order.setStatus(newStatus);
            order.setUpdatedAt(Instant.now().toString());

            Order updated = orderRepository.updateOrder(order).block();
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update order status: " + e.getMessage()));
        }
    }

    @DeleteMapping("/api/orders/{orderId}")
    public ResponseEntity<?> deleteOrder(@PathVariable String orderId) {
        try {
            Order order = orderRepository.getOrderById(orderId).block();
            if (order == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Order not found"));
            }

            if (!"pending".equals(order.getStatus())) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("error", "Only pending orders can be deleted"));
            }

            orderRepository.deleteOrder(order.getId(), order.getCustomerId()).block();
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to delete order: " + e.getMessage()));
        }
    }

    private boolean isValidTransition(String currentStatus, String newStatus) {
        if (currentStatus.equals(newStatus)) {
            return false;
        }

        return switch (currentStatus) {
            case "pending" -> VALID_STATUSES.contains(newStatus);
            case "shipped" -> "delivered".equals(newStatus);
            case "delivered", "cancelled" -> false;
            default -> false;
        };
    }
}
