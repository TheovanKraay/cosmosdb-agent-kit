package com.ecommerce.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class CreateOrderRequest {

    @JsonProperty("customerId")
    private String customerId;

    @JsonProperty("items")
    private List<OrderItem> items;

    @JsonProperty("shippingAddress")
    private String shippingAddress;

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public void setItems(List<OrderItem> items) {
        this.items = items;
    }

    public String getShippingAddress() {
        return shippingAddress;
    }

    public void setShippingAddress(String shippingAddress) {
        this.shippingAddress = shippingAddress;
    }
}
