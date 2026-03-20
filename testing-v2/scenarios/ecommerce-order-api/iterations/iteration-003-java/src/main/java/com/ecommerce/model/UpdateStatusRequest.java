package com.ecommerce.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class UpdateStatusRequest {

    @JsonProperty("status")
    private String status;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
