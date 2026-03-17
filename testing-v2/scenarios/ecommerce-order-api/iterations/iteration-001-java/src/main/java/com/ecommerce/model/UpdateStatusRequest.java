package com.ecommerce.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for updating order status (PATCH /api/orders/{orderId}/status).
 */
@Data
@NoArgsConstructor
public class UpdateStatusRequest {

    @JsonProperty("status")
    private String status;
}
