package com.ecommerce.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Order {

    private String id;
    private String orderId;
    private String customerId;
    private String status;
    private List<OrderItem> items;
    private double total;
    private String createdAt;
    private String type;
    private String schemaVersion;
    private String shippingAddress;

    public Order() {
        this.type = "order";
        this.schemaVersion = "1.0";
    }

    public static Order create(String customerId, List<OrderItem> items, String shippingAddress) {
        Order order = new Order();
        String generatedId = UUID.randomUUID().toString();
        order.setId(generatedId);
        order.setOrderId(generatedId);
        order.setCustomerId(customerId);
        order.setStatus("pending");
        order.setItems(items);
        order.setTotal(calculateTotal(items));
        order.setCreatedAt(Instant.now().toString());
        order.setShippingAddress(shippingAddress);
        return order;
    }

    private static double calculateTotal(List<OrderItem> items) {
        double total = 0.0;
        for (OrderItem item : items) {
            total += item.getQuantity() * item.getUnitPrice();
        }
        return Math.round(total * 100.0) / 100.0;
    }

    // Getters and Setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public void setItems(List<OrderItem> items) {
        this.items = items;
    }

    public double getTotal() {
        return total;
    }

    public void setTotal(double total) {
        this.total = total;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(String schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public String getShippingAddress() {
        return shippingAddress;
    }

    public void setShippingAddress(String shippingAddress) {
        this.shippingAddress = shippingAddress;
    }
}
