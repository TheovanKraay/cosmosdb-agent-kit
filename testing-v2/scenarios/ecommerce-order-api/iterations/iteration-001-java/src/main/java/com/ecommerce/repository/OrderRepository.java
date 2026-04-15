package com.ecommerce.repository;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.azure.cosmos.util.CosmosPagedIterable;
import com.ecommerce.config.CosmosDbConfiguration;
import com.ecommerce.model.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Repository
public class OrderRepository {

    private static final Logger logger = LoggerFactory.getLogger(OrderRepository.class);

    private final CosmosDbConfiguration cosmosConfig;

    public OrderRepository(CosmosDbConfiguration cosmosConfig) {
        this.cosmosConfig = cosmosConfig;
    }

    private CosmosContainer getContainer() {
        CosmosContainer container = cosmosConfig.getContainer();
        if (container == null) {
            throw new IllegalStateException("Cosmos DB container not initialized");
        }
        return container;
    }

    /**
     * Create a new order in the container.
     * Uses partition key = customerId for efficient writes.
     */
    public Order createOrder(Order order) {
        CosmosItemResponse<Order> response = getContainer().createItem(
            order,
            new PartitionKey(order.getCustomerId()),
            new CosmosItemRequestOptions()
        );
        return response.getItem();
    }

    /**
     * Get an order by its orderId.
     * Since orderId is the document id and we don't know the customerId,
     * we use a cross-partition query. For known customerId, use point read.
     */
    public Optional<Order> getOrderById(String orderId) {
        // Use parameterized query (Rule 3.6)
        SqlQuerySpec query = new SqlQuerySpec(
            "SELECT * FROM c WHERE c.id = @orderId AND c.type = 'order'",
            Arrays.asList(new SqlParameter("@orderId", orderId))
        );

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        CosmosPagedIterable<Order> results = getContainer().queryItems(query, options, Order.class);

        for (Order order : results) {
            return Optional.of(order);
        }
        return Optional.empty();
    }

    /**
     * Get all orders for a customer.
     * Uses partition key for efficient single-partition query (Rule 3.1).
     */
    public List<Order> getOrdersByCustomerId(String customerId) {
        SqlQuerySpec query = new SqlQuerySpec(
            "SELECT * FROM c WHERE c.customerId = @customerId AND c.type = 'order' ORDER BY c.createdAt DESC",
            Arrays.asList(new SqlParameter("@customerId", customerId))
        );

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(new PartitionKey(customerId));

        List<Order> orders = new ArrayList<>();
        CosmosPagedIterable<Order> results = getContainer().queryItems(query, options, Order.class);
        results.forEach(orders::add);
        return orders;
    }

    /**
     * Query orders by status (admin query, cross-partition).
     */
    public List<Order> getOrdersByStatus(String status) {
        SqlQuerySpec query = new SqlQuerySpec(
            "SELECT * FROM c WHERE c.status = @status AND c.type = 'order'",
            Arrays.asList(new SqlParameter("@status", status))
        );

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        List<Order> orders = new ArrayList<>();
        CosmosPagedIterable<Order> results = getContainer().queryItems(query, options, Order.class);
        results.forEach(orders::add);
        return orders;
    }

    /**
     * Query orders by date range (admin query, cross-partition).
     */
    public List<Order> getOrdersByDateRange(String startDate, String endDate) {
        SqlQuerySpec query = new SqlQuerySpec(
            "SELECT * FROM c WHERE c.createdAt >= @startDate AND c.createdAt <= @endDate AND c.type = 'order' ORDER BY c.createdAt DESC",
            Arrays.asList(
                new SqlParameter("@startDate", startDate),
                new SqlParameter("@endDate", endDate + "T23:59:59.999Z")
            )
        );

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        List<Order> orders = new ArrayList<>();
        CosmosPagedIterable<Order> results = getContainer().queryItems(query, options, Order.class);
        results.forEach(orders::add);
        return orders;
    }

    /**
     * Replace (update) an existing order.
     * Uses partition key for efficient point write.
     */
    public Order replaceOrder(Order order) {
        CosmosItemResponse<Order> response = getContainer().replaceItem(
            order,
            order.getId(),
            new PartitionKey(order.getCustomerId()),
            new CosmosItemRequestOptions()
        );
        return response.getItem();
    }

    /**
     * Delete an order by id and partition key.
     */
    public void deleteOrder(String orderId, String customerId) {
        getContainer().deleteItem(
            orderId,
            new PartitionKey(customerId),
            new CosmosItemRequestOptions()
        );
    }
}
