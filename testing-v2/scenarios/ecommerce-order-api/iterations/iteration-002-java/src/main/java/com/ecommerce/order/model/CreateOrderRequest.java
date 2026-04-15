package com.ecommerce.order.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request body for creating an order.
 */
public class CreateOrderRequest {

    private String customerId;
    private java.util.List<OrderItem> items;
    private String shippingAddress;

    @JsonProperty("customerId")
    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    @JsonProperty("items")
    public java.util.List<OrderItem> getItems() {
        return items;
    }

    public void setItems(java.util.List<OrderItem> items) {
        this.items = items;
    }

    @JsonProperty("shippingAddress")
    public String getShippingAddress() {
        return shippingAddress;
    }

    public void setShippingAddress(String shippingAddress) {
        this.shippingAddress = shippingAddress;
    }
}
