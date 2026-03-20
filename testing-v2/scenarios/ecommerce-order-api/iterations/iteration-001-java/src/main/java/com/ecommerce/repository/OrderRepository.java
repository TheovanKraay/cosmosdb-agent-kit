package com.ecommerce.repository;

import com.azure.cosmos.CosmosAsyncContainer;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.ecommerce.model.Order;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Repository
public class OrderRepository {

    private final CosmosAsyncContainer container;

    public OrderRepository(CosmosAsyncContainer ordersContainer) {
        this.container = ordersContainer;
    }

    public Order save(Order order) {
        container.createItem(order, new PartitionKey(order.getCustomerId()),
                new CosmosItemRequestOptions()).block();
        return order;
    }

    public Optional<Order> findById(String orderId) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.orderId = @orderId",
                Collections.singletonList(new SqlParameter("@orderId", orderId))
        );
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();

        List<Order> results = container.queryItems(query, options, Order.class)
                .collectList().block();

        return results != null && !results.isEmpty()
                ? Optional.of(results.get(0))
                : Optional.empty();
    }

    public List<Order> findByCustomerId(String customerId) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.customerId = @customerId",
                Collections.singletonList(new SqlParameter("@customerId", customerId))
        );
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(new PartitionKey(customerId));

        List<Order> results = container.queryItems(query, options, Order.class)
                .collectList().block();

        return results != null ? results : Collections.emptyList();
    }

    public List<Order> findByStatus(String status) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.status = @status",
                Collections.singletonList(new SqlParameter("@status", status))
        );
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();

        List<Order> results = container.queryItems(query, options, Order.class)
                .collectList().block();

        return results != null ? results : Collections.emptyList();
    }

    public List<Order> findByDateRange(String startDate, String endDate) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.createdAt >= @startDate AND c.createdAt <= @endDate",
                Arrays.asList(
                        new SqlParameter("@startDate", startDate),
                        new SqlParameter("@endDate", endDate)
                )
        );
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();

        List<Order> results = container.queryItems(query, options, Order.class)
                .collectList().block();

        return results != null ? results : Collections.emptyList();
    }

    public Order update(Order order) {
        container.replaceItem(order, order.getId(),
                new PartitionKey(order.getCustomerId()),
                new CosmosItemRequestOptions()).block();
        return order;
    }

    public void delete(String id, String customerId) {
        container.deleteItem(id, new PartitionKey(customerId),
                new CosmosItemRequestOptions()).block();
    }
}
