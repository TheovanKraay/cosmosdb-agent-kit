package com.ecommerce.repository;

import com.azure.cosmos.CosmosAsyncContainer;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.ecommerce.model.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Repository
public class OrderRepository {

    private static final Logger logger = LoggerFactory.getLogger(OrderRepository.class);
    private final CosmosAsyncContainer container;

    public OrderRepository(CosmosAsyncContainer container) {
        this.container = container;
    }

    public Mono<Order> createOrder(Order order) {
        return container.createItem(order, new PartitionKey(order.getCustomerId()), new CosmosItemRequestOptions())
                .map(response -> response.getItem());
    }

    public Mono<Order> getOrderById(String orderId) {
        String query = "SELECT * FROM c WHERE c.orderId = @orderId";
        SqlQuerySpec querySpec = new SqlQuerySpec(query,
                Collections.singletonList(new SqlParameter("@orderId", orderId)));

        return container.queryItems(querySpec, new CosmosQueryRequestOptions(), Order.class)
                .collectList()
                .flatMap(list -> list.isEmpty() ? Mono.empty() : Mono.just(list.get(0)));
    }

    public Mono<List<Order>> getOrdersByCustomer(String customerId) {
        String query = "SELECT * FROM c WHERE c.customerId = @customerId ORDER BY c.createdAt DESC";
        SqlQuerySpec querySpec = new SqlQuerySpec(query,
                Collections.singletonList(new SqlParameter("@customerId", customerId)));

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(new PartitionKey(customerId));

        return container.queryItems(querySpec, options, Order.class)
                .collectList();
    }

    public Mono<List<Order>> getOrdersByStatus(String status) {
        String query = "SELECT * FROM c WHERE c.status = @status ORDER BY c.createdAt DESC";
        SqlQuerySpec querySpec = new SqlQuerySpec(query,
                Collections.singletonList(new SqlParameter("@status", status)));

        return container.queryItems(querySpec, new CosmosQueryRequestOptions(), Order.class)
                .collectList();
    }

    public Mono<List<Order>> getOrdersByDateRange(String startDate, String endDate) {
        String query = "SELECT * FROM c WHERE c.createdAt >= @startDate AND c.createdAt <= @endDate ORDER BY c.createdAt DESC";
        SqlQuerySpec querySpec = new SqlQuerySpec(query,
                Arrays.asList(
                        new SqlParameter("@startDate", startDate),
                        new SqlParameter("@endDate", endDate)
                ));

        return container.queryItems(querySpec, new CosmosQueryRequestOptions(), Order.class)
                .collectList();
    }

    public Mono<Order> updateOrder(Order order) {
        return container.upsertItem(order, new PartitionKey(order.getCustomerId()), new CosmosItemRequestOptions())
                .map(response -> response.getItem());
    }

    public Mono<Void> deleteOrder(String id, String customerId) {
        return container.deleteItem(id, new PartitionKey(customerId), new CosmosItemRequestOptions())
                .then();
    }
}
