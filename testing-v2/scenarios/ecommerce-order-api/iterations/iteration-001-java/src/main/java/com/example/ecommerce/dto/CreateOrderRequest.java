package com.example.ecommerce.dto;

import java.util.List;

/**
 * Request body for POST /api/orders.
 */
public class CreateOrderRequest {

    private String customerId;
    private List<OrderItemRequest> items;
    private String shippingAddress;

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public List<OrderItemRequest> getItems() { return items; }
    public void setItems(List<OrderItemRequest> items) { this.items = items; }

    public String getShippingAddress() { return shippingAddress; }
    public void setShippingAddress(String shippingAddress) { this.shippingAddress = shippingAddress; }
}
