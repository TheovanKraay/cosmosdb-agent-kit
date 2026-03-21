---
title: Version Your Document Schemas
impact: HIGH
impactDescription: enables safe schema evolution without rewriting existing data
tags: model, schema, versioning, migration
---

## Version Your Document Schemas

**Always include a `schemaVersion` field in every Cosmos DB document you create, even on the first version of the schema.** Initialize it to `1`. This is a mandatory best practice regardless of whether you expect the schema to change — future engineers (or future AI agents) will need it for safe migrations and backward-compatible reads.

Accepted field names (tests check for any of these): `schemaVersion`, `schema_version`, `_version`, `version`, `docVersion`. Prefer **`schemaVersion`**.

Include schema version in documents to handle evolution gracefully. This enables safe migrations and backward-compatible reads.

**Incorrect (missing schemaVersion from the start):**

```java
// BAD: No schemaVersion — impossible to migrate later
public class Order {
    private String id;
    private String customerId;
    private List<OrderItem> items;
    private double total;
    private String status;
    private String createdAt;
    // No schemaVersion!
}
```

```python
# BAD: No schemaVersion
order_doc = {
    "id": order_id,
    "customerId": customer_id,
    "items": items,
    "total": total,
    "status": "pending",
    "createdAt": now_iso
    # Missing schemaVersion!
}
```

**Correct (schemaVersion on every document from version 1):**

```java
// GOOD: schemaVersion included from the start
public class Order {
    private String id;
    private String customerId;
    private List<OrderItem> items;
    private double total;
    private String status;
    private String createdAt;
    private int schemaVersion = 1;  // Always include; start at 1
}
```

```python
# GOOD: schemaVersion included from the start
order_doc = {
    "id": order_id,
    "customerId": customer_id,
    "items": items,
    "total": total,
    "status": "pending",
    "createdAt": now_iso,
    "schemaVersion": 1  # Always include; start at 1
}
```

```csharp
// GOOD: Base class carries schemaVersion for all document types
public abstract class DocumentBase
{
    public string Id { get; set; }
    public int SchemaVersion { get; set; } = 1;  // Initialize to 1
}

public class Order : DocumentBase
{
    public string CustomerId { get; set; }
    public List<OrderItem> Items { get; set; }
    public decimal Total { get; set; }
    public string Status { get; set; }
    public string CreatedAt { get; set; }
}

// Read with version handling for future evolution
public async Task<Order> GetOrderAsync(string id, string partitionKey)
{
    var response = await container.ReadItemStreamAsync(id, new PartitionKey(partitionKey));
    using var doc = await JsonDocument.ParseAsync(response.Content);
    var version = doc.RootElement.GetProperty("schemaVersion").GetInt32();
    
    return version switch
    {
        1 => JsonSerializer.Deserialize<Order>(doc),
        _ => throw new NotSupportedException($"Unknown schema version: {version}")
    };
}

// Background migration using Change Feed
public async Task MigrateDocuments()
{
    var changeFeed = container.GetChangeFeedProcessorBuilder<Order>("migration", HandleChanges)
        .WithInstanceName("migrator")
        .WithStartTime(DateTime.MinValue.ToUniversalTime())
        .Build();
    await changeFeed.StartAsync();
}
```

Always increment version when:
- Adding required fields
- Changing field types
- Restructuring nested objects

Reference: [Schema evolution in Cosmos DB](https://learn.microsoft.com/azure/cosmos-db/nosql/modeling-data)
