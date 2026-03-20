package com.ecommerce.service;

import com.ecommerce.model.CreateOrderRequest;
import com.ecommerce.model.CustomerSummary;
import com.ecommerce.model.Order;
import com.ecommerce.repository.OrderRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class OrderService {

    private static final Set<String> VALID_STATUSES = Set.of("pending", "shipped", "delivered", "cancelled");
    private static final Set<String> TERMINAL_STATUSES = Set.of("delivered", "cancelled");

    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public Order createOrder(CreateOrderRequest request) {
        Order order = new Order();
        String orderId = UUID.randomUUID().toString();
        order.setId(orderId);
        order.setOrderId(orderId);
        order.setCustomerId(request.getCustomerId());
        order.setStatus("pending");
        order.setItems(request.getItems());
        order.setTotal(order.calculateTotal());
        order.setCreatedAt(Instant.now().toString());
        order.setShippingAddress(request.getShippingAddress());
        order.setType("order");
        order.setSchemaVersion("1.0");

        return orderRepository.createOrder(order);
    }

    public Order getOrder(String orderId) {
        return orderRepository.findById(orderId);
    }

    public List<Order> getCustomerOrders(String customerId) {
        return orderRepository.findByCustomerId(customerId);
    }

    public CustomerSummary getCustomerSummary(String customerId) {
        List<Order> orders = orderRepository.findByCustomerId(customerId);

        int totalOrders = orders.size();
        double totalSpent = orders.stream().mapToDouble(Order::getTotal).sum();
        double averageOrderValue = totalOrders > 0 ? totalSpent / totalOrders : 0.0;

        return new CustomerSummary(customerId, totalOrders, totalSpent, averageOrderValue);
    }

    public List<Order> getOrdersByStatus(String status) {
        return orderRepository.findByStatus(status);
    }

    public List<Order> getOrdersByDateRange(String startDate, String endDate) {
        return orderRepository.findByDateRange(startDate, endDate);
    }

    public Order updateOrderStatus(String orderId, String newStatus) {
        if (!VALID_STATUSES.contains(newStatus)) {
            throw new InvalidStatusException("Invalid status: " + newStatus);
        }

        Order order = orderRepository.findById(orderId);
        if (order == null) {
            throw new OrderNotFoundException("Order not found: " + orderId);
        }

        String currentStatus = order.getStatus();

        if (TERMINAL_STATUSES.contains(currentStatus)) {
            throw new InvalidTransitionException(
                    "Cannot transition from " + currentStatus + " to " + newStatus);
        }

        if ("shipped".equals(currentStatus) && !"delivered".equals(newStatus)) {
            throw new InvalidTransitionException(
                    "Cannot transition from shipped to " + newStatus);
        }

        order.setStatus(newStatus);
        return orderRepository.updateOrder(order);
    }

    public void deleteOrder(String orderId) {
        Order order = orderRepository.findById(orderId);
        if (order == null) {
            throw new OrderNotFoundException("Order not found: " + orderId);
        }

        if (!"pending".equals(order.getStatus())) {
            throw new InvalidTransitionException("Only pending orders can be deleted");
        }

        orderRepository.deleteOrder(order);
    }

    public static class OrderNotFoundException extends RuntimeException {
        public OrderNotFoundException(String message) {
            super(message);
        }
    }

    public static class InvalidTransitionException extends RuntimeException {
        public InvalidTransitionException(String message) {
            super(message);
        }
    }

    public static class InvalidStatusException extends RuntimeException {
        public InvalidStatusException(String message) {
            super(message);
        }
    }
}
