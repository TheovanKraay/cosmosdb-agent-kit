package com.ecommerce.orders.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Order document stored in Cosmos DB.
 * Uses customerId as partition key, orderId as the document id.
 * Items are embedded (denormalized) for read performance.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
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

    @JsonProperty("updatedAt")
    private String updatedAt;

    @JsonProperty("shippingAddress")
    private String shippingAddress;

    @JsonProperty("type")
    private String type = "order";

    @JsonProperty("schemaVersion")
    private int schemaVersion = 1;

    @JsonProperty("_etag")
    private String etag;

    public Order() {
    }

    /**
     * Create a new order with auto-generated ID and calculated total.
     */
    public static Order create(String customerId, List<OrderItem> items, String shippingAddress) {
        Order order = new Order();
        String generatedId = UUID.randomUUID().toString();
        order.id = generatedId;
        order.orderId = generatedId;
        order.customerId = customerId;
        order.status = "pending";
        order.items = items;
        order.total = items.stream()
                .mapToDouble(i -> i.getQuantity() * i.getUnitPrice())
                .sum();
        // Round to 2 decimal places to avoid floating-point drift
        order.total = Math.round(order.total * 100.0) / 100.0;
        order.createdAt = Instant.now().toString();
        order.updatedAt = order.createdAt;
        order.shippingAddress = shippingAddress;
        order.type = "order";
        order.schemaVersion = 1;
        return order;
    }

    // Getters and setters

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

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public String getShippingAddress() { return shippingAddress; }
    public void setShippingAddress(String shippingAddress) { this.shippingAddress = shippingAddress; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public int getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(int schemaVersion) { this.schemaVersion = schemaVersion; }

    public String getEtag() { return etag; }
    public void setEtag(String etag) { this.etag = etag; }
}
