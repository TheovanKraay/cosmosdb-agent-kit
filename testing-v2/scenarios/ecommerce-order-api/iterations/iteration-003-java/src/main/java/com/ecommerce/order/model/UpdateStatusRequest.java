package com.ecommerce.order.model;

/**
 * Request body for updating order status.
 */
public class UpdateStatusRequest {

    private String status;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
