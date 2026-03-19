package com.ecommerce.repository;

import com.azure.cosmos.CosmosContainer;
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

@Repository
public class OrderRepository {

    private final CosmosContainer container;

    public OrderRepository(CosmosContainer ordersContainer) {
        this.container = ordersContainer;
    }

    public Order createOrder(Order order) {
        container.createItem(order, new PartitionKey(order.getCustomerId()), new CosmosItemRequestOptions());
        return order;
    }

    public Order findById(String orderId) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.orderId = @orderId",
                List.of(new SqlParameter("@orderId", orderId))
        );
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setQueryMetricsEnabled(false);

        CosmosPagedIterable<Order> results = container.queryItems(query, options, Order.class);
        for (Order order : results) {
            return order;
        }
        return null;
    }

    public List<Order> findByCustomerId(String customerId) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.customerId = @customerId",
                List.of(new SqlParameter("@customerId", customerId))
        );
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(new PartitionKey(customerId));

        List<Order> orders = new ArrayList<>();
        container.queryItems(query, options, Order.class).forEach(orders::add);
        return orders;
    }

    public List<Order> findByStatus(String status) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.status = @status",
                List.of(new SqlParameter("@status", status))
        );
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();

        List<Order> orders = new ArrayList<>();
        container.queryItems(query, options, Order.class).forEach(orders::add);
        return orders;
    }

    public List<Order> findByDateRange(String startDate, String endDate) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.createdAt >= @startDate AND c.createdAt <= @endDate",
                List.of(
                        new SqlParameter("@startDate", startDate),
                        new SqlParameter("@endDate", endDate + "T23:59:59.999Z")
                )
        );
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();

        List<Order> orders = new ArrayList<>();
        container.queryItems(query, options, Order.class).forEach(orders::add);
        return orders;
    }

    public Order updateOrder(Order order) {
        container.upsertItem(order, new PartitionKey(order.getCustomerId()), new CosmosItemRequestOptions());
        return order;
    }

    public void deleteOrder(String orderId, String customerId) {
        container.deleteItem(orderId, new PartitionKey(customerId), new CosmosItemRequestOptions());
    }
}
