package com.ecommerce.service;

import com.ecommerce.model.CreateOrderRequest;
import com.ecommerce.model.CustomerSummary;
import com.ecommerce.model.Order;
import com.ecommerce.model.OrderItem;
import com.ecommerce.repository.OrderRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class OrderService {

    private final OrderRepository orderRepository;

    // Valid status transitions
    private static final Set<String> VALID_STATUSES = Set.of("pending", "shipped", "delivered", "cancelled");
    private static final Set<String> TERMINAL_STATUSES = Set.of("delivered", "cancelled");

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public Order createOrder(CreateOrderRequest request) {
        String orderId = UUID.randomUUID().toString();

        double total = 0.0;
        if (request.getItems() != null) {
            for (OrderItem item : request.getItems()) {
                total += item.getQuantity() * item.getUnitPrice();
            }
        }

        Order order = new Order();
        order.setId(orderId);
        order.setOrderId(orderId);
        order.setCustomerId(request.getCustomerId());
        order.setStatus("pending");
        order.setItems(request.getItems());
        order.setTotal(total);
        order.setCreatedAt(Instant.now().toString());
        order.setShippingAddress(request.getShippingAddress());

        return orderRepository.createOrder(order);
    }

    public Optional<Order> getOrder(String orderId) {
        return orderRepository.findById(orderId);
    }

    public List<Order> getCustomerOrders(String customerId) {
        return orderRepository.findByCustomerId(customerId);
    }

    public CustomerSummary getCustomerSummary(String customerId) {
        List<Order> orders = orderRepository.findByCustomerId(customerId);
        int totalOrders = orders.size();
        double totalSpent = orders.stream()
                .mapToDouble(Order::getTotal)
                .sum();
        double averageOrderValue = totalOrders > 0 ? totalSpent / totalOrders : 0.0;

        return new CustomerSummary(customerId, totalOrders, totalSpent, averageOrderValue);
    }

    public List<Order> getOrdersByStatus(String status) {
        return orderRepository.findByStatus(status);
    }

    public List<Order> getOrdersByDateRange(String startDate, String endDate) {
        return orderRepository.findByDateRange(startDate, endDate);
    }

    public record StatusUpdateResult(Order order, int statusCode, String message) {}

    public StatusUpdateResult updateOrderStatus(String orderId, String newStatus) {
        if (!VALID_STATUSES.contains(newStatus)) {
            return new StatusUpdateResult(null, 400, "Invalid status: " + newStatus);
        }

        Optional<Order> optionalOrder = orderRepository.findById(orderId);
        if (optionalOrder.isEmpty()) {
            return new StatusUpdateResult(null, 404, "Order not found");
        }

        Order order = optionalOrder.get();
        String currentStatus = order.getStatus();

        if (!isValidTransition(currentStatus, newStatus)) {
            return new StatusUpdateResult(null, 409,
                    "Invalid status transition from " + currentStatus + " to " + newStatus);
        }

        order.setStatus(newStatus);
        Order updated = orderRepository.updateOrder(order);
        return new StatusUpdateResult(updated, 200, "Status updated");
    }

    public record DeleteResult(int statusCode, String message) {}

    public DeleteResult deleteOrder(String orderId) {
        Optional<Order> optionalOrder = orderRepository.findById(orderId);
        if (optionalOrder.isEmpty()) {
            return new DeleteResult(404, "Order not found");
        }

        Order order = optionalOrder.get();
        if (!"pending".equals(order.getStatus())) {
            return new DeleteResult(409, "Only pending orders can be deleted");
        }

        orderRepository.deleteOrder(order);
        return new DeleteResult(204, "Order deleted");
    }

    private boolean isValidTransition(String currentStatus, String newStatus) {
        // Terminal statuses cannot transition to anything
        if (TERMINAL_STATUSES.contains(currentStatus)) {
            return false;
        }

        // From pending: can go to shipped, cancelled, or delivered
        if ("pending".equals(currentStatus)) {
            return "shipped".equals(newStatus) || "cancelled".equals(newStatus) || "delivered".equals(newStatus);
        }

        // From shipped: can only go to delivered
        if ("shipped".equals(currentStatus)) {
            return "delivered".equals(newStatus);
        }

        return false;
    }
}
