package com.ecommerce.orderapi.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request body for updating order status.
 */
public class UpdateStatusRequest {

    @JsonProperty("status")
    private String status;

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
