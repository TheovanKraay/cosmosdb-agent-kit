package com.ecommerce.repository;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.ecommerce.model.Order;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class OrderRepository {

    private final CosmosContainer container;

    public OrderRepository(CosmosContainer ordersContainer) {
        this.container = ordersContainer;
    }

    public Order createOrder(Order order) {
        container.createItem(order, new PartitionKey(order.getCustomerId()),
                new CosmosItemRequestOptions());
        return order;
    }

    public Optional<Order> findById(String orderId) {
        String query = "SELECT * FROM c WHERE c.orderId = @orderId";
        SqlParameter param = new SqlParameter("@orderId", orderId);
        SqlQuerySpec querySpec = new SqlQuerySpec(query, List.of(param));

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();

        List<Order> results = container.queryItems(querySpec, options, Order.class)
                .stream()
                .collect(Collectors.toList());

        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public List<Order> findByCustomerId(String customerId) {
        String query = "SELECT * FROM c WHERE c.customerId = @customerId";
        SqlParameter param = new SqlParameter("@customerId", customerId);
        SqlQuerySpec querySpec = new SqlQuerySpec(query, List.of(param));

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(new PartitionKey(customerId));

        return container.queryItems(querySpec, options, Order.class)
                .stream()
                .collect(Collectors.toList());
    }

    public List<Order> findByStatus(String status) {
        String query = "SELECT * FROM c WHERE c.status = @status";
        SqlParameter param = new SqlParameter("@status", status);
        SqlQuerySpec querySpec = new SqlQuerySpec(query, List.of(param));

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();

        return container.queryItems(querySpec, options, Order.class)
                .stream()
                .collect(Collectors.toList());
    }

    public List<Order> findByDateRange(String startDate, String endDate) {
        String query = "SELECT * FROM c WHERE c.createdAt >= @startDate AND c.createdAt <= @endDate";
        SqlParameter startParam = new SqlParameter("@startDate", startDate);
        SqlParameter endParam = new SqlParameter("@endDate", endDate);
        SqlQuerySpec querySpec = new SqlQuerySpec(query, List.of(startParam, endParam));

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();

        return container.queryItems(querySpec, options, Order.class)
                .stream()
                .collect(Collectors.toList());
    }

    public Order updateOrder(Order order) {
        container.replaceItem(order, order.getId(),
                new PartitionKey(order.getCustomerId()),
                new CosmosItemRequestOptions());
        return order;
    }

    public void deleteOrder(Order order) {
        container.deleteItem(order.getId(),
                new PartitionKey(order.getCustomerId()),
                new CosmosItemRequestOptions());
    }
}
