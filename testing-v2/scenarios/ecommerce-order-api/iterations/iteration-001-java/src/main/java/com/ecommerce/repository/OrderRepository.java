package com.ecommerce.repository;

import com.azure.cosmos.CosmosAsyncContainer;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.ecommerce.model.Order;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Repository
public class OrderRepository {

    private final CosmosAsyncContainer container;

    public OrderRepository(CosmosAsyncContainer container) {
        this.container = container;
    }

    public Mono<Order> createOrder(Order order) {
        return container.createItem(order, new PartitionKey(order.getCustomerId()), new CosmosItemRequestOptions())
                .map(CosmosItemResponse::getItem);
    }

    public Mono<Order> getOrderById(String orderId) {
        SqlQuerySpec querySpec = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.id = @orderId",
                Collections.singletonList(new SqlParameter("@orderId", orderId))
        );
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        return container.queryItems(querySpec, options, Order.class)
                .byPage(1)
                .flatMap(page -> Flux.fromIterable(page.getResults()))
                .next();
    }

    public Flux<Order> getOrdersByCustomer(String customerId) {
        SqlQuerySpec querySpec = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.customerId = @customerId ORDER BY c.createdAt DESC",
                Collections.singletonList(new SqlParameter("@customerId", customerId))
        );
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(new PartitionKey(customerId));
        return container.queryItems(querySpec, options, Order.class)
                .byPage()
                .flatMap(page -> Flux.fromIterable(page.getResults()));
    }

    public Flux<Order> getOrdersByStatus(String status) {
        SqlQuerySpec querySpec = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.status = @status ORDER BY c.createdAt DESC",
                Collections.singletonList(new SqlParameter("@status", status))
        );
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        return container.queryItems(querySpec, options, Order.class)
                .byPage()
                .flatMap(page -> Flux.fromIterable(page.getResults()));
    }

    public Flux<Order> getOrdersByDateRange(String startDate, String endDate) {
        SqlQuerySpec querySpec = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.createdAt >= @startDate AND c.createdAt <= @endDate ORDER BY c.createdAt DESC",
                Arrays.asList(
                        new SqlParameter("@startDate", startDate),
                        new SqlParameter("@endDate", endDate)
                )
        );
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        return container.queryItems(querySpec, options, Order.class)
                .byPage()
                .flatMap(page -> Flux.fromIterable(page.getResults()));
    }

    public Mono<Order> updateOrder(Order order) {
        return container.replaceItem(order, order.getId(), new PartitionKey(order.getCustomerId()), new CosmosItemRequestOptions())
                .map(CosmosItemResponse::getItem);
    }

    public Mono<Void> deleteOrder(String orderId, String customerId) {
        return container.deleteItem(orderId, new PartitionKey(customerId), new CosmosItemRequestOptions())
                .then();
    }
}
