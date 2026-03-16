package com.example.ecommerce.dto;

/**
 * Request body for PATCH /api/orders/{orderId}/status.
 */
public class UpdateStatusRequest {

    private String status;

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
