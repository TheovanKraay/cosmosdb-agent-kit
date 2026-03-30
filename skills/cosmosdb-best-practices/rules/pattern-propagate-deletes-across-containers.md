---
title: Explicitly propagate deletes to all derived and denormalized containers
impact: HIGH
impactDescription: prevents orphaned documents in derived containers that silently corrupt query results indefinitely
tags:
  - pattern
  - delete
  - materialized-views
  - denormalization
  - data-consistency
---

## Rule

When you maintain derived or denormalized data in a secondary container (materialized views, leaderboard entries, status projections, secondary indexes, etc.), your delete handler for the source entity **must explicitly delete all corresponding documents** in every derived container. Cosmos DB Change Feed does **not** surface deletes, so you cannot rely on a Change Feed processor to clean up derived data when a source document is removed. Failing to propagate deletes leaves orphaned documents in derived containers that will corrupt query results indefinitely.

## Why

Cosmos DB's Change Feed delivers inserts and updates only — deleted documents are never pushed to the feed. Any pattern that uses a secondary container populated from a primary container (leaderboard entries derived from player profiles, order projections by status, product catalog views, etc.) will accumulate stale/orphaned documents if the delete path does not clean them up explicitly. These orphaned documents surface as incorrect query results: deleted entities reappearing in listings, incorrect aggregate counts, and stale secondary index entries — bugs that are difficult to diagnose because the primary container is correct.

## How

1. Identify every secondary container that stores documents derived from the entity being deleted.
2. In the same delete handler (or within the same logical operation), issue delete calls against all derived containers.
3. Use the derived document's known `id` and partition key — do not query for them at delete time (point deletes are cheaper than queries).
4. If any derived-container delete fails, decide on a strategy: re-raise to roll back (transactional), retry, or queue for async cleanup.
5. Document the dependency in code comments so future engineers know which containers must be updated together.

> **Note on Change Feed and deletes:** Even if you use a Change Feed processor to sync creates/updates to a derived container, you still need a separate explicit delete path. Change Feed only delivers changes for documents that exist; a delete event is never emitted.

### Example (Good) — Python

```python
async def delete_player(player_id: str):
    """Delete player and ALL derived documents atomically."""
    # 1. Delete from primary container
    await players_container.delete_item(item=player_id, partition_key=player_id)

    # 2. Delete leaderboard entries in every derived container
    # Partition key in the leaderboard container is a synthetic key (e.g., "global")
    # so the document id is known upfront — use a point delete, not a query
    try:
        await leaderboard_container.delete_item(
            item=player_id,
            partition_key="global"
        )
    except CosmosResourceNotFoundError:
        pass  # Player had no score — no leaderboard entry to remove

    # 3. Delete regional leaderboard entry
    player = ...  # Retrieve region from request context or pass as parameter
    if player_region:
        try:
            await regional_leaderboard_container.delete_item(
                item=player_id,
                partition_key=player_region
            )
        except CosmosResourceNotFoundError:
            pass
```

### Example (Good) — C#

```csharp
public async Task DeleteUserAsync(string userId)
{
    // 1. Delete the source document
    await _usersContainer.DeleteItemAsync<User>(userId, new PartitionKey(userId));

    // 2. Delete all derived documents — do NOT rely on Change Feed for this
    // Change Feed does not emit delete events
    await DeleteDerivedDocumentsAsync(userId);
}

private async Task DeleteDerivedDocumentsAsync(string userId)
{
    // Delete from activity feed container (partitioned by /feedKey)
    try
    {
        await _activityFeedContainer.DeleteItemAsync<ActivityEntry>(
            userId, new PartitionKey("global-feed"));
    }
    catch (CosmosException ex) when (ex.StatusCode == HttpStatusCode.NotFound)
    {
        // No activity entry — nothing to clean up
    }

    // Delete from region projection container
    // If region is unknown at delete time, query only once and delete the results
    var query = new QueryDefinition(
        "SELECT c.id, c.region FROM c WHERE c.userId = @userId")
        .WithParameter("@userId", userId);
    using var iter = _regionProjectionContainer.GetItemQueryIterator<RegionEntry>(query);
    while (iter.HasMoreResults)
    {
        foreach (var entry in await iter.ReadNextAsync())
        {
            await _regionProjectionContainer.DeleteItemAsync<RegionEntry>(
                entry.Id, new PartitionKey(entry.Region));
        }
    }
}
```

### Example (Bad)

```python
# ❌ WRONG — only deletes from the primary container
# Leaderboard entries in the secondary container are orphaned forever
async def delete_player(player_id: str):
    await players_container.delete_item(item=player_id, partition_key=player_id)
    # Leaderboard container still holds the deleted player's entry.
    # GET /leaderboards/global will keep returning this player.
```

```csharp
// ❌ WRONG — assuming Change Feed will clean up derived containers
// Change Feed does NOT emit delete events; orphaned documents accumulate
async Task HandleChangesAsync(IReadOnlyCollection<PlayerProfile> changes, CancellationToken ct)
{
    foreach (var profile in changes)
    {
        // Upsert leaderboard entry for creates/updates — but deleted players
        // never appear here. Their leaderboard entry will remain forever.
        await _leaderboardContainer.UpsertItemAsync(
            new LeaderboardEntry { PlayerId = profile.Id, Score = profile.BestScore },
            new PartitionKey("global"));
    }
}
```

## References

- [Change feed in Azure Cosmos DB — limitations (deletes not captured)](https://learn.microsoft.com/azure/cosmos-db/change-feed#limitations)
- [Implement cross-document transactions using Change Feed](https://learn.microsoft.com/azure/cosmos-db/nosql/change-feed-design-patterns)
