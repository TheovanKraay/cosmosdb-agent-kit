package com.example.ecommerce.dto;

import com.example.ecommerce.model.Order;
import com.example.ecommerce.model.OrderItem;
import java.util.List;

/**
 * API response for order endpoints.
 * Maps Cosmos DB document fields to contract field names.
 * Key mapping: Cosmos "id" -> API "orderId"
 */
public class OrderResponse {

    private String orderId;
    private String customerId;
    private String status;
    private List<OrderItem> items;
    private double total;
    private String createdAt;

    public OrderResponse() {}

    /**
     * Build an OrderResponse from a Cosmos DB Order document.
     * Maps internal "id" field to the API contract's "orderId".
     */
    public static OrderResponse from(Order order) {
        OrderResponse resp = new OrderResponse();
        resp.orderId = order.getId();
        resp.customerId = order.getCustomerId();
        resp.status = order.getStatus();
        resp.items = order.getItems();
        resp.total = order.getTotal();
        resp.createdAt = order.getCreatedAt();
        return resp;
    }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public List<OrderItem> getItems() { return items; }
    public void setItems(List<OrderItem> items) { this.items = items; }

    public double getTotal() { return total; }
    public void setTotal(double total) { this.total = total; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
