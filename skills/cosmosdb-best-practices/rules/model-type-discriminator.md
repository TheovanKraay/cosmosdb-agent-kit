---
title: Use Type Discriminators for Polymorphic Data
impact: HIGH
impactDescription: enables efficient single-container design and future extensibility
tags: model, polymorphism, type-discriminator, design
---

## Use Type Discriminators for Polymorphic Data

**Always include a `type` field in every Cosmos DB document**, even when a container currently holds only one entity type. Adding it from day one is low cost — retrofitting it across millions of existing documents is expensive. This is mandatory for:
1. Future extensibility — containers often evolve to store multiple types (future engineers or AI agents inheriting this code will need it)
2. Self-documenting data — makes queries and debugging clearer
3. Efficient filtering — the indexed `type` field enables fast entity-type queries

Accepted field names (tests check for any of these): `type`, `_type`, `documentType`, `entityType`, `discriminator`. Prefer **`type`**.

Set the value to a clear lowercase string (e.g., `"order"`, `"customer"`, `"product"`).

**Incorrect (no type field — even for a single entity type):**

```java
// BAD: No type field — container will be opaque and hard to evolve
public class Order {
    private String id;
    private String customerId;
    private String status;
    private List<OrderItem> items;
    private double total;
    private String createdAt;
    // No "type" field!
}
```

```python
# BAD: No type field
order_doc = {
    "id": order_id,
    "customerId": customer_id,
    "status": "pending",
    "items": items,
    "total": total,
    "createdAt": now_iso
    # Missing "type"!
}
```

**Correct (explicit type field on every document):**

```java
// GOOD: type field on every document
public class Order {
    private String id;
    private String customerId;
    private String status;
    private List<OrderItem> items;
    private double total;
    private String createdAt;
    private String type = "order";  // Always include; mirrors container intent
}
```

```python
# GOOD: type field included
order_doc = {
    "id": order_id,
    "customerId": customer_id,
    "status": "pending",
    "items": items,
    "total": total,
    "createdAt": now_iso,
    "type": "order"  # Always include on every document
}
```

Use a single Cosmos DB container to co-locate related parent/child or different entity types when:
- similar entities are written and read together, share a natural or business partition key, require a simple transactional boundary, and do not exceed Cosmos DB partition key limits.

When storing multiple entity types in the same container, include a type discriminator field for efficient filtering and deserialization.

**Multi-entity-type example (correct):**

```csharp
// Multiple types in same container without clear identification
public class Order { public string Id { get; set; } /* ... */ }
public class Customer { public string Id { get; set; } /* ... */ }
public class Product { public string Id { get; set; } /* ... */ }

// How do you query just orders? Full scan!
var allItems = await container.GetItemQueryIterator<dynamic>("SELECT * FROM c").ReadNextAsync();
var orders = allItems.Where(x => x.orderDate != null);  // Brittle, inefficient
```

**Correct (explicit type discriminator):**

```csharp
// Base class with type discriminator
public abstract class BaseEntity
{
    [JsonPropertyName("id")]
    public string Id { get; set; }
    
    [JsonPropertyName("type")]
    public abstract string Type { get; }
    
    [JsonPropertyName("partitionKey")]
    public string PartitionKey { get; set; }
}

public class Order : BaseEntity
{
    public override string Type => "order";
    public DateTime OrderDate { get; set; }
    public List<OrderItem> Items { get; set; }
}

public class Customer : BaseEntity
{
    public override string Type => "customer";
    public string Email { get; set; }
    public string Name { get; set; }
}

public class Product : BaseEntity
{
    public override string Type => "product";
    public string Name { get; set; }
    public decimal Price { get; set; }
}

// Efficient queries by type - uses index!
var ordersQuery = new QueryDefinition(
    "SELECT * FROM c WHERE c.type = @type AND c.partitionKey = @pk")
    .WithParameter("@type", "order")
    .WithParameter("@pk", customerId);

// Polymorphic deserialization
public static BaseEntity DeserializeEntity(JsonDocument doc)
{
    var type = doc.RootElement.GetProperty("type").GetString();
    return type switch
    {
        "order" => doc.Deserialize<Order>(),
        "customer" => doc.Deserialize<Customer>(),
        "product" => doc.Deserialize<Product>(),
        _ => throw new InvalidOperationException($"Unknown type: {type}")
    };
}
```

Benefits:
- Efficient filtering with indexed `type` field
- Clear deserialization logic
- Self-documenting data structure

**When NOT to Use Multi-Entity Containers** :
   - Independent throughput requirements → Use separate containers
   - Different scaling patterns → Use separate containers
   - Different indexing needs → Use separate containers
   - Distinct change feed processing requirements → Use separate containers
   - Low access correlation (<20%) → Use separate containers

**Single-Container Anti-Patterns** :
   - "Everything container" → Complex filtering → Difficult analytics
   - One throughput allocation for all entity types
   - One change feed with mixed events requiring filtering
   - Difficult to maintain and onboard new developers

Reference: [Model data in Cosmos DB](https://learn.microsoft.com/azure/cosmos-db/nosql/modeling-data)
