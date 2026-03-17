package com.ecommerce.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request body for creating a new order (POST /api/orders).
 */
@Data
@NoArgsConstructor
public class CreateOrderRequest {

    @JsonProperty("customerId")
    private String customerId;

    @JsonProperty("items")
    private List<OrderItem> items;

    @JsonProperty("shippingAddress")
    private String shippingAddress;
}
