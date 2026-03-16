package com.ecommerce.model;

public class CustomerOrderSummary {

    private String customerId;
    private int totalOrders;
    private double totalSpent;
    private double averageOrderValue;

    public CustomerOrderSummary() {
    }

    public CustomerOrderSummary(String customerId, int totalOrders, double totalSpent, double averageOrderValue) {
        this.customerId = customerId;
        this.totalOrders = totalOrders;
        this.totalSpent = totalSpent;
        this.averageOrderValue = averageOrderValue;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public int getTotalOrders() {
        return totalOrders;
    }

    public void setTotalOrders(int totalOrders) {
        this.totalOrders = totalOrders;
    }

    public double getTotalSpent() {
        return totalSpent;
    }

    public void setTotalSpent(double totalSpent) {
        this.totalSpent = totalSpent;
    }

    public double getAverageOrderValue() {
        return averageOrderValue;
    }

    public void setAverageOrderValue(double averageOrderValue) {
        this.averageOrderValue = averageOrderValue;
    }
}
