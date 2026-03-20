package com.ecommerce.repository;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.azure.cosmos.util.CosmosPagedIterable;
import com.ecommerce.model.Order;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class OrderRepository {

    private final CosmosContainer container;

    public OrderRepository(CosmosContainer ordersContainer) {
        this.container = ordersContainer;
    }

    public Order createOrder(Order order) {
        CosmosItemResponse<Order> response = container.createItem(order, new PartitionKey(order.getCustomerId()), new CosmosItemRequestOptions());
        return response.getItem();
    }

    public Optional<Order> findById(String orderId) {
        String query = "SELECT * FROM c WHERE c.orderId = @orderId";
        SqlQuerySpec querySpec = new SqlQuerySpec(query, List.of(new SqlParameter("@orderId", orderId)));
        CosmosPagedIterable<Order> results = container.queryItems(querySpec, new CosmosQueryRequestOptions(), Order.class);

        for (Order order : results) {
            return Optional.of(order);
        }
        return Optional.empty();
    }

    public List<Order> findByCustomerId(String customerId) {
        String query = "SELECT * FROM c WHERE c.customerId = @customerId";
        SqlQuerySpec querySpec = new SqlQuerySpec(query, List.of(new SqlParameter("@customerId", customerId)));
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(new PartitionKey(customerId));
        CosmosPagedIterable<Order> results = container.queryItems(querySpec, options, Order.class);

        List<Order> orders = new ArrayList<>();
        results.forEach(orders::add);
        return orders;
    }

    public List<Order> findByStatus(String status) {
        String query = "SELECT * FROM c WHERE c.status = @status";
        SqlQuerySpec querySpec = new SqlQuerySpec(query, List.of(new SqlParameter("@status", status)));
        CosmosPagedIterable<Order> results = container.queryItems(querySpec, new CosmosQueryRequestOptions(), Order.class);

        List<Order> orders = new ArrayList<>();
        results.forEach(orders::add);
        return orders;
    }

    public List<Order> findByDateRange(String startDate, String endDate) {
        String query = "SELECT * FROM c WHERE c.createdAt >= @startDate AND c.createdAt <= @endDate";
        SqlQuerySpec querySpec = new SqlQuerySpec(query, List.of(
                new SqlParameter("@startDate", startDate),
                new SqlParameter("@endDate", endDate)
        ));
        CosmosPagedIterable<Order> results = container.queryItems(querySpec, new CosmosQueryRequestOptions(), Order.class);

        List<Order> orders = new ArrayList<>();
        results.forEach(orders::add);
        return orders;
    }

    public Order replaceOrder(Order order) {
        CosmosItemResponse<Order> response = container.replaceItem(order, order.getId(), new PartitionKey(order.getCustomerId()), new CosmosItemRequestOptions());
        return response.getItem();
    }

    public void deleteOrder(String id, String customerId) {
        container.deleteItem(id, new PartitionKey(customerId), new CosmosItemRequestOptions());
    }
}
