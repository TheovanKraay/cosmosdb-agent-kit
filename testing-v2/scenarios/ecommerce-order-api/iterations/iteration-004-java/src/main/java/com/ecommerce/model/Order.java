package com.ecommerce.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Order {

    private String id;
    private String orderId;
    private String customerId;
    private String status;
    private List<OrderItem> items;
    private double total;
    private String createdAt;
    private String shippingAddress;
    private String type;
    private String schemaVersion;

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
        order.setShippingAddress(shippingAddress);
        order.setCreatedAt(Instant.now().toString());
        order.calculateTotal();
        return order;
    }

    public void calculateTotal() {
        if (items != null) {
            this.total = items.stream()
                    .mapToDouble(item -> item.getQuantity() * item.getUnitPrice())
                    .sum();
        } else {
            this.total = 0.0;
        }
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

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

    public String getShippingAddress() { return shippingAddress; }
    public void setShippingAddress(String shippingAddress) { this.shippingAddress = shippingAddress; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(String schemaVersion) { this.schemaVersion = schemaVersion; }
}
