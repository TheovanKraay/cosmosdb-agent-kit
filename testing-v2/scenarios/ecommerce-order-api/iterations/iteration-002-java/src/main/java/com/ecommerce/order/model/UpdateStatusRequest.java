package com.ecommerce.order.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request body for updating order status.
 */
public class UpdateStatusRequest {

    private String status;

    @JsonProperty("status")
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
