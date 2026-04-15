package com.ecommerce.order.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Customer order summary response.
 */
public class CustomerSummary {

    private String customerId;
    private int totalOrders;
    private double totalSpent;
    private double averageOrderValue;

    public CustomerSummary() {}

    public CustomerSummary(String customerId, int totalOrders, double totalSpent, double averageOrderValue) {
        this.customerId = customerId;
        this.totalOrders = totalOrders;
        this.totalSpent = totalSpent;
        this.averageOrderValue = averageOrderValue;
    }

    @JsonProperty("customerId")
    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    @JsonProperty("totalOrders")
    public int getTotalOrders() {
        return totalOrders;
    }

    public void setTotalOrders(int totalOrders) {
        this.totalOrders = totalOrders;
    }

    @JsonProperty("totalSpent")
    public double getTotalSpent() {
        return totalSpent;
    }

    public void setTotalSpent(double totalSpent) {
        this.totalSpent = totalSpent;
    }

    @JsonProperty("averageOrderValue")
    public double getAverageOrderValue() {
        return averageOrderValue;
    }

    public void setAverageOrderValue(double averageOrderValue) {
        this.averageOrderValue = averageOrderValue;
    }
}
