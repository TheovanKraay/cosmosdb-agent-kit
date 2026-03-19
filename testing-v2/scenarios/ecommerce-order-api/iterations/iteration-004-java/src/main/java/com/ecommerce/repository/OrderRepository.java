package com.ecommerce.repository;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.ecommerce.model.Order;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Repository
public class OrderRepository {

    private final CosmosContainer container;

    public OrderRepository(CosmosContainer ordersContainer) {
        this.container = ordersContainer;
    }

    public Order save(Order order) {
        container.createItem(order, new PartitionKey(order.getCustomerId()), new CosmosItemRequestOptions());
        return order;
    }

    public Order update(Order order) {
        container.replaceItem(order, order.getId(), new PartitionKey(order.getCustomerId()), new CosmosItemRequestOptions());
        return order;
    }

    public Optional<Order> findById(String orderId) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.orderId = @orderId",
                Arrays.asList(new SqlParameter("@orderId", orderId)));

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        List<Order> results = container.queryItems(query, options, Order.class)
                .stream().toList();

        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public List<Order> findByCustomerId(String customerId) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.customerId = @customerId",
                Arrays.asList(new SqlParameter("@customerId", customerId)));

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(new PartitionKey(customerId));

        return container.queryItems(query, options, Order.class)
                .stream().toList();
    }

    public List<Order> findByStatus(String status) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.status = @status",
                Arrays.asList(new SqlParameter("@status", status)));

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        return container.queryItems(query, options, Order.class)
                .stream().toList();
    }

    public List<Order> findByDateRange(String startDate, String endDate) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.createdAt >= @startDate AND c.createdAt <= @endDate",
                Arrays.asList(
                        new SqlParameter("@startDate", startDate),
                        new SqlParameter("@endDate", endDate)));

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        return container.queryItems(query, options, Order.class)
                .stream().toList();
    }

    public void deleteById(String id, String customerId) {
        container.deleteItem(id, new PartitionKey(customerId), new CosmosItemRequestOptions());
    }
}
