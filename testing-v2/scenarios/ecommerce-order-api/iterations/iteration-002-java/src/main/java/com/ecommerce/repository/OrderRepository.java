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

    public Order getOrderById(String orderId) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.orderId = @orderId",
                List.of(new SqlParameter("@orderId", orderId))
        );
        CosmosPagedIterable<Order> results = container.queryItems(query, new CosmosQueryRequestOptions(), Order.class);
        for (Order order : results) {
            return order;
        }
        return null;
    }

    public List<Order> getOrdersByCustomerId(String customerId) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.customerId = @customerId ORDER BY c.createdAt DESC",
                List.of(new SqlParameter("@customerId", customerId))
        );
        CosmosPagedIterable<Order> results = container.queryItems(query, new CosmosQueryRequestOptions().setPartitionKey(new PartitionKey(customerId)), Order.class);
        List<Order> orders = new ArrayList<>();
        results.forEach(orders::add);
        return orders;
    }

    public List<Order> getOrdersByStatus(String status) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.status = @status ORDER BY c.createdAt DESC",
                List.of(new SqlParameter("@status", status))
        );
        CosmosPagedIterable<Order> results = container.queryItems(query, new CosmosQueryRequestOptions(), Order.class);
        List<Order> orders = new ArrayList<>();
        results.forEach(orders::add);
        return orders;
    }

    public List<Order> getOrdersByDateRange(String startDate, String endDate) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.createdAt >= @startDate AND c.createdAt <= @endDate ORDER BY c.createdAt DESC",
                List.of(
                        new SqlParameter("@startDate", startDate),
                        new SqlParameter("@endDate", endDate)
                )
        );
        CosmosPagedIterable<Order> results = container.queryItems(query, new CosmosQueryRequestOptions(), Order.class);
        List<Order> orders = new ArrayList<>();
        results.forEach(orders::add);
        return orders;
    }

    public Order updateOrder(Order order) {
        CosmosItemResponse<Order> response = container.replaceItem(order, order.getId(), new PartitionKey(order.getCustomerId()), new CosmosItemRequestOptions());
        return response.getItem();
    }

    public void deleteOrder(Order order) {
        container.deleteItem(order.getId(), new PartitionKey(order.getCustomerId()), new CosmosItemRequestOptions());
    }
}
