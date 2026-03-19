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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Repository
public class OrderRepository {

    private static final Logger log = LoggerFactory.getLogger(OrderRepository.class);

    private final CosmosContainer container;

    public OrderRepository(CosmosContainer container) {
        this.container = container;
    }

    public Order save(Order order) {
        try {
            CosmosItemResponse<Order> response = container.createItem(
                    order,
                    new PartitionKey(order.getCustomerId()),
                    new CosmosItemRequestOptions());
            return response.getItem();
        } catch (CosmosException e) {
            log.error("Failed to save order: {}", e.getMessage());
            throw e;
        }
    }

    public Order replace(Order order) {
        try {
            CosmosItemResponse<Order> response = container.replaceItem(
                    order,
                    order.getId(),
                    new PartitionKey(order.getCustomerId()),
                    new CosmosItemRequestOptions());
            return response.getItem();
        } catch (CosmosException e) {
            log.error("Failed to replace order: {}", e.getMessage());
            throw e;
        }
    }

    public Optional<Order> findById(String orderId) {
        try {
            SqlQuerySpec query = new SqlQuerySpec(
                    "SELECT * FROM c WHERE c.id = @id",
                    Arrays.asList(new SqlParameter("@id", orderId)));

            CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
            CosmosPagedIterable<Order> results = container.queryItems(query, options, Order.class);

            for (Order order : results) {
                return Optional.of(order);
            }
            return Optional.empty();
        } catch (CosmosException e) {
            log.error("Failed to find order by id: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public List<Order> findByCustomerId(String customerId) {
        try {
            SqlQuerySpec query = new SqlQuerySpec(
                    "SELECT * FROM c WHERE c.customerId = @customerId",
                    Arrays.asList(new SqlParameter("@customerId", customerId)));

            CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
            options.setPartitionKey(new PartitionKey(customerId));

            List<Order> orders = new ArrayList<>();
            container.queryItems(query, options, Order.class)
                    .forEach(orders::add);
            return orders;
        } catch (CosmosException e) {
            log.error("Failed to find orders for customer: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    public List<Order> findByStatus(String status) {
        try {
            SqlQuerySpec query = new SqlQuerySpec(
                    "SELECT * FROM c WHERE c.status = @status",
                    Arrays.asList(new SqlParameter("@status", status)));

            CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
            List<Order> orders = new ArrayList<>();
            container.queryItems(query, options, Order.class)
                    .forEach(orders::add);
            return orders;
        } catch (CosmosException e) {
            log.error("Failed to find orders by status: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    public List<Order> findByDateRange(String startDate, String endDate) {
        try {
            SqlQuerySpec query = new SqlQuerySpec(
                    "SELECT * FROM c WHERE c.createdAt >= @startDate AND c.createdAt <= @endDate",
                    Arrays.asList(
                            new SqlParameter("@startDate", startDate),
                            new SqlParameter("@endDate", endDate)));

            CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
            List<Order> orders = new ArrayList<>();
            container.queryItems(query, options, Order.class)
                    .forEach(orders::add);
            return orders;
        } catch (CosmosException e) {
            log.error("Failed to find orders by date range: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    public void delete(String orderId, String customerId) {
        try {
            container.deleteItem(orderId, new PartitionKey(customerId), new CosmosItemRequestOptions());
        } catch (CosmosException e) {
            log.error("Failed to delete order: {}", e.getMessage());
            throw e;
        }
    }
}
