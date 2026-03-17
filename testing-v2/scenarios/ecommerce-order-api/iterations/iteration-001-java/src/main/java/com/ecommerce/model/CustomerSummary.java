package com.ecommerce.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response body for GET /api/customers/{customerId}/orders/summary.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerSummary {

    @JsonProperty("customerId")
    private String customerId;

    @JsonProperty("totalOrders")
    private int totalOrders;

    @JsonProperty("totalSpent")
    private double totalSpent;

    @JsonProperty("averageOrderValue")
    private double averageOrderValue;
}
