package com.ecommerce.service;

import com.ecommerce.model.Order;
import com.ecommerce.model.OrderItem;
import com.ecommerce.repository.OrderRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class OrderService {

    private static final Map<String, Set<String>> VALID_TRANSITIONS = Map.of(
            "pending", Set.of("shipped", "cancelled", "delivered"),
            "shipped", Set.of("delivered")
    );

    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public Order createOrder(String customerId, List<OrderItem> items, String shippingAddress) {
        Order order = new Order();
        String id = UUID.randomUUID().toString();
        order.setId(id);
        order.setOrderId(id);
        order.setCustomerId(customerId);
        order.setStatus("pending");
        order.setItems(items);
        order.setShippingAddress(shippingAddress);
        order.setCreatedAt(Instant.now().toString());
        order.setType("order");
        order.setSchemaVersion("1.0");

        double total = 0;
        for (OrderItem item : items) {
            total += item.getQuantity() * item.getUnitPrice();
        }
        // Round to 2 decimal places to avoid floating point issues
        order.setTotal(Math.round(total * 100.0) / 100.0);

        return orderRepository.save(order);
    }

    public Optional<Order> getOrder(String orderId) {
        return orderRepository.findById(orderId);
    }

    public List<Order> getCustomerOrders(String customerId) {
        return orderRepository.findByCustomerId(customerId);
    }

    public List<Order> getOrdersByStatus(String status) {
        return orderRepository.findByStatus(status);
    }

    public List<Order> getOrdersByDateRange(String startDate, String endDate) {
        return orderRepository.findByDateRange(startDate, endDate);
    }

    public Optional<Order> updateOrderStatus(String orderId, String newStatus) {
        Optional<Order> existingOrder = orderRepository.findById(orderId);
        if (existingOrder.isEmpty()) {
            return Optional.empty();
        }

        Order order = existingOrder.get();
        String currentStatus = order.getStatus();

        Set<String> allowed = VALID_TRANSITIONS.getOrDefault(currentStatus, Set.of());
        if (!allowed.contains(newStatus)) {
            throw new InvalidStatusTransitionException(
                    "Cannot transition from " + currentStatus + " to " + newStatus);
        }

        order.setStatus(newStatus);
        return Optional.of(orderRepository.update(order));
    }

    public boolean deleteOrder(String orderId) {
        Optional<Order> existingOrder = orderRepository.findById(orderId);
        if (existingOrder.isEmpty()) {
            return false;
        }

        Order order = existingOrder.get();
        if (!"pending".equals(order.getStatus())) {
            throw new InvalidStatusTransitionException(
                    "Only pending orders can be deleted");
        }

        orderRepository.deleteById(order.getId(), order.getCustomerId());
        return true;
    }

    public Map<String, Object> getCustomerSummary(String customerId) {
        List<Order> orders = orderRepository.findByCustomerId(customerId);

        int totalOrders = orders.size();
        double totalSpent = 0;
        for (Order order : orders) {
            totalSpent += order.getTotal();
        }
        totalSpent = Math.round(totalSpent * 100.0) / 100.0;

        double averageOrderValue = totalOrders > 0 ? Math.round((totalSpent / totalOrders) * 100.0) / 100.0 : 0;

        return Map.of(
                "customerId", customerId,
                "totalOrders", totalOrders,
                "totalSpent", totalSpent,
                "averageOrderValue", averageOrderValue
        );
    }

    public static class InvalidStatusTransitionException extends RuntimeException {
        public InvalidStatusTransitionException(String message) {
            super(message);
        }
    }
}
