package com.example.ecommerce.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Order document stored in Cosmos DB.
 *
 * Cosmos DB design decisions (AGENTS.md):
 * - Rule 1.3: Items (order line items) are embedded within the order document
 *   since they are always retrieved together
 * - Rule 2.6: partitioned by customerId to efficiently serve
 *   "get orders by customer" queries
 * - Rule 1.5: Jackson annotations ensure correct JSON field names in Cosmos
 * - Rule 1.10: Schema version field for future migration support
 *
 * The Cosmos DB primary key ("id") stores the orderId value.
 * The API response maps "id" -> "orderId" via OrderResponse DTO.
 */
public class Order {

    /** Cosmos DB primary key — stores the orderId UUID */
    @JsonProperty("id")
    private String id;

    /** Partition key — enables efficient per-customer queries (AGENTS.md 2.6) */
    @JsonProperty("customerId")
    private String customerId;

    /** Order status: pending, shipped, delivered, cancelled */
    @JsonProperty("status")
    private String status;

    /**
     * Embedded order items (AGENTS.md 1.3 — embed related data).
     * Always retrieved with the order, bounded size (3-5 items avg).
     */
    @JsonProperty("items")
    private List<OrderItem> items;

    /** Calculated total: sum of (quantity * unitPrice) for all items */
    @JsonProperty("total")
    private double total;

    /** ISO-8601 creation timestamp, e.g. "2026-01-15T10:30:00Z" */
    @JsonProperty("createdAt")
    private String createdAt;

    /** Optional shipping address */
    @JsonProperty("shippingAddress")
    private String shippingAddress;

    /** Schema version for future migration support (AGENTS.md 1.10) */
    @JsonProperty("schemaVersion")
    private int schemaVersion = 1;

    public Order() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

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

    public int getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(int schemaVersion) { this.schemaVersion = schemaVersion; }
}
