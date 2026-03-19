package com.ecommerce.repository;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.ecommerce.model.Order;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

@Repository
public class OrderRepository {

    private final CosmosContainer container;

    public OrderRepository(CosmosContainer container) {
        this.container = container;
    }

    public Order createOrder(Order order) {
        CosmosItemRequestOptions options = new CosmosItemRequestOptions();
        options.setContentResponseOnWriteEnabled(true);
        container.createItem(order, new PartitionKey(order.getCustomerId()), options);
        return order;
    }

    public Optional<Order> getOrderById(String orderId) {
        String query = "SELECT * FROM c WHERE c.orderId = @orderId";
        SqlQuerySpec querySpec = new SqlQuerySpec(query,
                Arrays.asList(new SqlParameter("@orderId", orderId)));
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();

        Iterator<Order> iterator = container.queryItems(querySpec, options, Order.class)
                .iterator();
        if (iterator.hasNext()) {
            return Optional.of(iterator.next());
        }
        return Optional.empty();
    }

    public List<Order> getOrdersByCustomer(String customerId) {
        String query = "SELECT * FROM c WHERE c.customerId = @customerId";
        SqlQuerySpec querySpec = new SqlQuerySpec(query,
                Arrays.asList(new SqlParameter("@customerId", customerId)));
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(new PartitionKey(customerId));

        List<Order> orders = new ArrayList<>();
        container.queryItems(querySpec, options, Order.class)
                .forEach(orders::add);
        return orders;
    }

    public List<Order> getOrdersByStatus(String status) {
        String query = "SELECT * FROM c WHERE c.status = @status";
        SqlQuerySpec querySpec = new SqlQuerySpec(query,
                Arrays.asList(new SqlParameter("@status", status)));
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();

        List<Order> orders = new ArrayList<>();
        container.queryItems(querySpec, options, Order.class)
                .forEach(orders::add);
        return orders;
    }

    public List<Order> getOrdersByDateRange(String startDate, String endDate) {
        String adjustedEndDate = endDate;
        if (!endDate.contains("T")) {
            adjustedEndDate = endDate + "T23:59:59.999Z";
        }

        String query = "SELECT * FROM c WHERE c.createdAt >= @startDate AND c.createdAt <= @endDate";
        SqlQuerySpec querySpec = new SqlQuerySpec(query,
                Arrays.asList(
                    new SqlParameter("@startDate", startDate),
                    new SqlParameter("@endDate", adjustedEndDate)));
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();

        List<Order> orders = new ArrayList<>();
        container.queryItems(querySpec, options, Order.class)
                .forEach(orders::add);
        return orders;
    }

    public Order updateOrder(Order order) {
        CosmosItemRequestOptions options = new CosmosItemRequestOptions();
        options.setContentResponseOnWriteEnabled(true);
        container.replaceItem(order, order.getId(),
                new PartitionKey(order.getCustomerId()), options);
        return order;
    }

    public void deleteOrder(String id, String customerId) {
        container.deleteItem(id, new PartitionKey(customerId),
                new CosmosItemRequestOptions());
    }
}
