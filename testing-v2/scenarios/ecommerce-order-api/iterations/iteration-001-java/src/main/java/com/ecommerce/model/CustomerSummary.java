package com.ecommerce.model;

public class CustomerSummary {
    private String customerId;
    private int totalOrders;
    private double totalSpent;
    private double averageOrderValue;

    public CustomerSummary(String customerId, int totalOrders, double totalSpent, double averageOrderValue) {
        this.customerId = customerId;
        this.totalOrders = totalOrders;
        this.totalSpent = totalSpent;
        this.averageOrderValue = averageOrderValue;
    }

    public String getCustomerId() { return customerId; }
    public int getTotalOrders() { return totalOrders; }
    public double getTotalSpent() { return totalSpent; }
    public double getAverageOrderValue() { return averageOrderValue; }
}
