package com.ecommerce.repository;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.ecommerce.model.Order;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class OrderRepository {

    private final CosmosContainer container;

    public OrderRepository(CosmosContainer container) {
        this.container = container;
    }

    public Order createOrder(Order order) {
        CosmosItemResponse<Order> response = container.createItem(
                order, new PartitionKey(order.getCustomerId()), new CosmosItemRequestOptions());
        return response.getItem();
    }

    public Order getOrderById(String orderId) {
        SqlQuerySpec querySpec = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.orderId = @orderId",
                List.of(new SqlParameter("@orderId", orderId)));

        List<Order> results = container.queryItems(querySpec, new CosmosQueryRequestOptions(), Order.class)
                .stream().toList();

        return results.isEmpty() ? null : results.get(0);
    }

    public List<Order> getOrdersByCustomer(String customerId) {
        SqlQuerySpec querySpec = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.customerId = @customerId",
                List.of(new SqlParameter("@customerId", customerId)));

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(new PartitionKey(customerId));

        return container.queryItems(querySpec, options, Order.class)
                .stream().toList();
    }

    public List<Order> getOrdersByStatus(String status) {
        SqlQuerySpec querySpec = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.status = @status",
                List.of(new SqlParameter("@status", status)));

        return container.queryItems(querySpec, new CosmosQueryRequestOptions(), Order.class)
                .stream().toList();
    }

    public List<Order> getOrdersByDateRange(String startDate, String endDate) {
        SqlQuerySpec querySpec = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.createdAt >= @startDate AND c.createdAt <= @endDate",
                List.of(
                        new SqlParameter("@startDate", startDate),
                        new SqlParameter("@endDate", endDate)));

        return container.queryItems(querySpec, new CosmosQueryRequestOptions(), Order.class)
                .stream().toList();
    }

    public Order updateOrder(Order order) {
        CosmosItemResponse<Order> response = container.upsertItem(
                order, new PartitionKey(order.getCustomerId()), new CosmosItemRequestOptions());
        return response.getItem();
    }

    public void deleteOrder(String orderId, String customerId) {
        container.deleteItem(orderId, new PartitionKey(customerId), new CosmosItemRequestOptions());
    }
}
