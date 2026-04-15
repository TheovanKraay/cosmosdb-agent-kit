package com.ecommerce.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Order {

    // Cosmos DB document id (same as orderId)
    private String id;

    // API-facing order identifier
    private String orderId;

    // Partition key
    private String customerId;

    private String status;

    // Embedded order items (Rule 1.3: embed related data retrieved together)
    private List<OrderItem> items;

    // Auto-calculated: sum of (quantity * unitPrice) for all items
    private double total;

    // ISO-8601 timestamp
    private String createdAt;

    // Type discriminator (Rule 1.11)
    private String type;

    // Schema version (Rule 1.10)
    private int schemaVersion;

    // Optional shipping address
    private String shippingAddress;

    public Order() {
        this.type = "order";
        this.schemaVersion = 1;
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

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public int getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(int schemaVersion) { this.schemaVersion = schemaVersion; }

    public String getShippingAddress() { return shippingAddress; }
    public void setShippingAddress(String shippingAddress) { this.shippingAddress = shippingAddress; }

    /**
     * Calculate total from items.
     */
    public void calculateTotal() {
        if (items != null) {
            this.total = items.stream()
                .mapToDouble(item -> item.getQuantity() * item.getUnitPrice())
                .sum();
            // Round to 2 decimal places to avoid floating-point drift
            this.total = Math.round(this.total * 100.0) / 100.0;
        }
    }
}
