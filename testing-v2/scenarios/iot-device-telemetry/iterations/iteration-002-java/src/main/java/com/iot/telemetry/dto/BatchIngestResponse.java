package com.iot.telemetry.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class BatchIngestResponse {

    @JsonProperty("ingested")
    private int ingested;

    public BatchIngestResponse() {}

    public BatchIngestResponse(int ingested) {
        this.ingested = ingested;
    }

    public int getIngested() { return ingested; }
    public void setIngested(int ingested) { this.ingested = ingested; }
}
