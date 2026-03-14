package com.example.ecommerce.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class Order {

    @JsonProperty("id")
    private String id;

    @JsonProperty("orderId")
    private String orderId;

    @JsonProperty("customerId")
    private String customerId;

    @JsonProperty("status")
    private String status;

    @JsonProperty("items")
    private List<OrderItem> items;

    @JsonProperty("total")
    private double total;

    @JsonProperty("createdAt")
    private String createdAt;

    @JsonProperty("shippingAddress")
    private String shippingAddress;

    public Order() {}

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

    public String getShippingAddress() {
        return shippingAddress;
    }

    public void setShippingAddress(String shippingAddress) {
        this.shippingAddress = shippingAddress;
    }
}
