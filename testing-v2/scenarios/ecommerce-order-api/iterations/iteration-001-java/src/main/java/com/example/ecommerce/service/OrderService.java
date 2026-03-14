package com.example.ecommerce.service;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.models.CosmosContainerProperties;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.azure.cosmos.models.ThroughputProperties;
import com.azure.cosmos.util.CosmosPagedIterable;
import com.example.ecommerce.model.CreateOrderRequest;
import com.example.ecommerce.model.Order;
import com.example.ecommerce.model.OrderItem;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class OrderService {

    private static final String DATABASE_NAME = "ecommerce-order-api";
    private static final String CONTAINER_NAME = "orders";

    @Value("${cosmos.endpoint}")
    private String cosmosEndpoint;

    @Value("${cosmos.key}")
    private String cosmosKey;

    private CosmosClient cosmosClient;
    private CosmosContainer container;

    @PostConstruct
    public void init() {
        cosmosClient = new CosmosClientBuilder()
                .endpoint(cosmosEndpoint)
                .key(cosmosKey)
                .buildClient();

        cosmosClient.createDatabaseIfNotExists(DATABASE_NAME);
        CosmosDatabase database = cosmosClient.getDatabase(DATABASE_NAME);

        CosmosContainerProperties containerProperties = new CosmosContainerProperties(CONTAINER_NAME, "/customerId");
        database.createContainerIfNotExists(containerProperties, ThroughputProperties.createManualThroughput(400));

        container = database.getContainer(CONTAINER_NAME);
    }

    @PreDestroy
    public void close() {
        if (cosmosClient != null) {
            cosmosClient.close();
        }
    }

    public Order createOrder(CreateOrderRequest request) {
        String orderId = UUID.randomUUID().toString();

        double total = 0.0;
        if (request.getItems() != null) {
            for (OrderItem item : request.getItems()) {
                total += item.getQuantity() * item.getUnitPrice();
            }
        }

        Order order = new Order();
        order.setId(orderId);
        order.setOrderId(orderId);
        order.setCustomerId(request.getCustomerId());
        order.setStatus("pending");
        order.setItems(request.getItems());
        order.setTotal(total);
        order.setCreatedAt(Instant.now().toString());
        order.setShippingAddress(request.getShippingAddress());

        container.createItem(order, new PartitionKey(order.getCustomerId()), new CosmosItemRequestOptions());
        return order;
    }

    public Order getOrderById(String orderId) {
        SqlQuerySpec querySpec = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.id = @orderId",
                Arrays.asList(new SqlParameter("@orderId", orderId)));
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        CosmosPagedIterable<Order> results = container.queryItems(querySpec, options, Order.class);
        for (Order order : results) {
            return order;
        }
        return null;
    }

    public List<Order> getOrdersByCustomer(String customerId) {
        SqlQuerySpec querySpec = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.customerId = @customerId",
                Arrays.asList(new SqlParameter("@customerId", customerId)));
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(new PartitionKey(customerId));
        CosmosPagedIterable<Order> results = container.queryItems(querySpec, options, Order.class);
        List<Order> orders = new ArrayList<>();
        for (Order order : results) {
            orders.add(order);
        }
        return orders;
    }

    public List<Order> getOrdersByStatus(String status) {
        SqlQuerySpec querySpec = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.status = @status",
                Arrays.asList(new SqlParameter("@status", status)));
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        CosmosPagedIterable<Order> results = container.queryItems(querySpec, options, Order.class);
        List<Order> orders = new ArrayList<>();
        for (Order order : results) {
            orders.add(order);
        }
        return orders;
    }

    public List<Order> getOrdersByDateRange(String startDate, String endDate) {
        SqlQuerySpec querySpec = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.createdAt >= @startDate AND c.createdAt <= @endDate",
                Arrays.asList(
                        new SqlParameter("@startDate", startDate),
                        new SqlParameter("@endDate", endDate)));
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        CosmosPagedIterable<Order> results = container.queryItems(querySpec, options, Order.class);
        List<Order> orders = new ArrayList<>();
        for (Order order : results) {
            orders.add(order);
        }
        return orders;
    }

    public Order updateOrderStatus(String orderId, String newStatus) {
        Order order = getOrderById(orderId);
        if (order == null) {
            return null;
        }
        order.setStatus(newStatus);
        container.replaceItem(order, order.getId(), new PartitionKey(order.getCustomerId()), new CosmosItemRequestOptions());
        return order;
    }
}
