package com.ecommerce.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Order document stored in Cosmos DB.
 *
 * Partition key: customerId (Rule 2.6: aligns with primary query pattern).
 * Items embedded in the order document (Rule 1.3: embed data retrieved together).
 * Both "id" (Cosmos DB doc ID) and "orderId" fields are set to the same UUID
 * so the API response includes "orderId" and Cosmos DB has its required "id".
 *
 * Field names match the api-contract.yaml: total, createdAt, orderId, customerId.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    /**
     * Cosmos DB document ID. Set to the same UUID as orderId.
     * The "id" field is required by Cosmos DB as the document identifier.
     */
    @JsonProperty("id")
    private String id;

    /**
     * Order identifier exposed in API responses.
     * Same value as "id" - included for API contract compliance.
     */
    @JsonProperty("orderId")
    private String orderId;

    /**
     * Customer ID - serves as the partition key (Rule 2.6).
     * High cardinality: ~100,000 customers ensure even distribution (Rule 2.4).
     */
    @JsonProperty("customerId")
    private String customerId;

    /**
     * Order status: pending, shipped, delivered, cancelled.
     * New orders default to "pending".
     */
    @JsonProperty("status")
    private String status;

    /**
     * Order line items embedded in the document (Rule 1.3).
     * Orders and items are always retrieved together.
     * Average 3-5 items per order keeps document well under 2MB (Rule 1.1).
     */
    @JsonProperty("items")
    private List<OrderItem> items;

    /**
     * Total order value = sum of (quantity * unitPrice) for all items.
     * Auto-calculated on order creation.
     * Field name "total" matches api-contract.yaml.
     */
    @JsonProperty("total")
    private double total;

    /**
     * ISO-8601 timestamp when the order was created.
     * Field name "createdAt" matches api-contract.yaml.
     */
    @JsonProperty("createdAt")
    private String createdAt;

    /**
     * Optional shipping address.
     */
    @JsonProperty("shippingAddress")
    private String shippingAddress;

    /**
     * Type discriminator for document classification (Rule 1.9).
     * Enables efficient filtering and future extensibility.
     */
    @JsonProperty("type")
    @Builder.Default
    private String type = "order";

    /**
     * ETag for optimistic concurrency on status updates (Rule 4.7).
     * Populated from Cosmos DB response headers.
     */
    @JsonProperty("_etag")
    private String etag;

    /**
     * Document schema version for future migrations (Rule 1.10).
     * Field name "schemaVersion" (not "_schemaVersion") matches test expectations.
     */
    @JsonProperty("schemaVersion")
    @Builder.Default
    private int schemaVersion = 1;
}
