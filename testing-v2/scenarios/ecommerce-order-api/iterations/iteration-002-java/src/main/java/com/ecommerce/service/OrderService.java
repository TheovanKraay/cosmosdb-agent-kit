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

@Service
public class OrderService {

    private final OrderRepository repository;

    private static final Set<String> VALID_STATUSES = Set.of("pending", "shipped", "delivered", "cancelled");

    public OrderService(OrderRepository repository) {
        this.repository = repository;
    }

    public Order createOrder(String customerId, List<OrderItem> items, String shippingAddress) {
        Order order = new Order();
        order.setCustomerId(customerId);
        order.setItems(items);
        order.setStatus("pending");
        if (shippingAddress != null) {
            order.setShippingAddress(shippingAddress);
        }
        order.calculateTotal();
        return repository.save(order);
    }

    public Optional<Order> getOrder(String orderId) {
        return repository.findById(orderId);
    }

    public List<Order> getCustomerOrders(String customerId) {
        return repository.findByCustomerId(customerId);
    }

    public List<Order> getOrdersByStatus(String status) {
        return repository.findByStatus(status);
    }

    public List<Order> getOrdersByDateRange(String startDate, String endDate) {
        String start = normalizeDate(startDate);
        String end = normalizeDate(endDate);
        if (!end.contains("T")) {
            end = end + "T23:59:59.999Z";
        }
        if (!start.contains("T")) {
            start = start + "T00:00:00.000Z";
        }
        return repository.findByDateRange(start, end);
    }

    public Optional<Order> updateOrderStatus(String orderId, String newStatus) {
        Optional<Order> existing = repository.findById(orderId);
        if (existing.isEmpty()) {
            return Optional.empty();
        }

        Order order = existing.get();
        String currentStatus = order.getStatus();

        if (!isValidTransition(currentStatus, newStatus)) {
            throw new InvalidStatusTransitionException(
                    "Cannot transition from '" + currentStatus + "' to '" + newStatus + "'");
        }

        order.setStatus(newStatus);
        order.setUpdatedAt(Instant.now().toString());
        Order updated = repository.replace(order);
        return Optional.of(updated);
    }

    public boolean deleteOrder(String orderId) {
        Optional<Order> existing = repository.findById(orderId);
        if (existing.isEmpty()) {
            return false;
        }

        Order order = existing.get();
        if (!"pending".equals(order.getStatus())) {
            throw new InvalidStatusTransitionException(
                    "Only pending orders can be deleted. Current status: " + order.getStatus());
        }

        repository.delete(order.getId(), order.getCustomerId());
        return true;
    }

    public Map<String, Object> getCustomerSummary(String customerId) {
        List<Order> orders = repository.findByCustomerId(customerId);
        int totalOrders = orders.size();
        double totalSpent = orders.stream()
                .mapToDouble(Order::getTotal)
                .sum();
        totalSpent = Math.round(totalSpent * 100.0) / 100.0;
        double averageOrderValue = totalOrders > 0 ? totalSpent / totalOrders : 0.0;
        averageOrderValue = Math.round(averageOrderValue * 1000.0) / 1000.0;

        return Map.of(
                "customerId", customerId,
                "totalOrders", totalOrders,
                "totalSpent", totalSpent,
                "averageOrderValue", averageOrderValue);
    }

    private boolean isValidTransition(String currentStatus, String newStatus) {
        if (!VALID_STATUSES.contains(newStatus)) {
            return false;
        }
        switch (currentStatus) {
            case "pending":
                return true;
            case "shipped":
                return "delivered".equals(newStatus);
            case "delivered":
            case "cancelled":
                return false;
            default:
                return false;
        }
    }

    private String normalizeDate(String date) {
        if (date == null) return "";
        return date.trim();
    }

    public static class InvalidStatusTransitionException extends RuntimeException {
        public InvalidStatusTransitionException(String message) {
            super(message);
        }
    }
}
