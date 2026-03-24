---
title: Use count-based or cached rank approaches instead of full partition scans for ranking
impact: HIGH
impactDescription: reduces rank lookups from O(N) partition scans to O(1) or O(log N) operations
tags: pattern, ranking, leaderboard, performance, query-optimization
---

## Efficient Ranking in Cosmos DB

When implementing leaderboards or rankings, avoid scanning an entire partition to determine a single player's rank. Full partition scans for rank lookups are an anti-pattern that becomes unsustainable at scale.

**Problem: Full partition scan to find rank**

```csharp
// Anti-pattern: Reads ALL entries in a partition to find one player's rank
// At 500K players, this consumes thousands of RU and takes seconds
public async Task<int> GetPlayerRankAsync(string leaderboardKey, string playerId)
{
    var query = new QueryDefinition(
        "SELECT c.playerId, c.bestScore FROM c WHERE c.type = @type ORDER BY c.bestScore DESC"
    ).WithParameter("@type", "leaderboardEntry");

    var allEntries = new List<LeaderboardEntry>();
    using var iterator = _container.GetItemQueryIterator<LeaderboardEntry>(
        query, requestOptions: new QueryRequestOptions { PartitionKey = new PartitionKey(leaderboardKey) });

    while (iterator.HasMoreResults)
    {
        var response = await iterator.ReadNextAsync();
        allEntries.AddRange(response); // Loading ALL entries into memory!
    }

    // O(N) scan to find player
    return allEntries.FindIndex(e => e.PlayerId == playerId) + 1;
}
```

This approach:
- Reads every document in the partition (potentially 500K+ documents)
- Consumes thousands of RU per request
- Has multi-second latency
- Loads all entries into memory

**Solution 1: COUNT-based rank query (simplest)**

```csharp
// Count players with higher scores to determine rank
// Single query, ~3-5 RU regardless of partition size
public async Task<int> GetPlayerRankAsync(string leaderboardKey, string playerId, int playerScore)
{
    var countQuery = new QueryDefinition(
        "SELECT VALUE COUNT(1) FROM c WHERE c.type = @type AND c.bestScore > @score"
    )
    .WithParameter("@type", "leaderboardEntry")
    .WithParameter("@score", playerScore);

    using var iterator = _container.GetItemQueryIterator<int>(
        countQuery, requestOptions: new QueryRequestOptions { PartitionKey = new PartitionKey(leaderboardKey) });

    var response = await iterator.ReadNextAsync();
    return response.Resource.FirstOrDefault() + 1; // Rank = count of players above + 1
}
```

**Solution 2: Cached rank offsets with Change Feed**

For extremely high-volume leaderboard reads, pre-compute and cache rank data:

```csharp
// Maintain a rank cache that is periodically updated
// Leaderboard entry includes pre-computed rank
public class RankedLeaderboardEntry
{
    [JsonPropertyName("id")]
    public string Id { get; set; }  // playerId

    [JsonPropertyName("leaderboardKey")]
    public string LeaderboardKey { get; set; }

    [JsonPropertyName("rank")]
    public int Rank { get; set; }  // Pre-computed rank

    [JsonPropertyName("bestScore")]
    public int BestScore { get; set; }

    [JsonPropertyName("displayName")]
    public string DisplayName { get; set; }
}

// Change Feed processor periodically recomputes ranks
// Run on a schedule (e.g., every 30 seconds) for near-real-time rankings
public async Task RecomputeRanksAsync(string leaderboardKey)
{
    var query = new QueryDefinition(
        "SELECT c.id, c.playerId, c.bestScore, c.displayName FROM c " +
        "WHERE c.type = @type ORDER BY c.bestScore DESC"
    ).WithParameter("@type", "leaderboardEntry");

    int rank = 0;
    using var iterator = _container.GetItemQueryIterator<LeaderboardEntry>(
        query, requestOptions: new QueryRequestOptions { PartitionKey = new PartitionKey(leaderboardKey) });

    while (iterator.HasMoreResults)
    {
        var batch = await iterator.ReadNextAsync();
        foreach (var entry in batch)
        {
            rank++;
            entry.Rank = rank;
            await _container.UpsertItemAsync(entry,
                new PartitionKey(leaderboardKey));
        }
    }
}

// Then rank lookup is a simple point read: O(1), 1 RU
public async Task<int> GetPlayerRankAsync(string leaderboardKey, string playerId)
{
    var response = await _container.ReadItemAsync<RankedLeaderboardEntry>(
        playerId, new PartitionKey(leaderboardKey));
    return response.Resource.Rank;
}
```

**Solution 3: Approximate ranking with score buckets**

For leaderboards where approximate rank is acceptable:

```csharp
// Maintain score distribution buckets for O(1) approximate ranking
// Partition key: /leaderboardKey, id: "bucket-{range}"
public class ScoreBucket
{
    [JsonPropertyName("id")]
    public string Id { get; set; }  // e.g., "bucket-9000-10000"

    [JsonPropertyName("leaderboardKey")]
    public string LeaderboardKey { get; set; }

    [JsonPropertyName("minScore")]
    public int MinScore { get; set; }

    [JsonPropertyName("maxScore")]
    public int MaxScore { get; set; }

    [JsonPropertyName("playerCount")]
    public int PlayerCount { get; set; }
}

// Approximate rank = sum of players in all higher buckets + position within bucket
```

**Key Points:**
- **Never scan an entire partition** to find a single item's rank — this is O(N) and doesn't scale
- **COUNT queries** are the simplest solution and work well for moderate scale (< 1M entries)
- **Pre-computed ranks** via Change Feed are best for high-volume reads with eventual consistency tolerance
- **Score buckets** provide O(1) approximate ranking for very large datasets
- Consider the trade-off: exact real-time rank (more RU) vs. slightly stale rank (less RU)
- For "nearby players ±10", combine a COUNT query with a TOP 21 query centered on the player's score

**Python implementation — rank + neighbors response:**

```python
def get_player_rank(player_id):
    # 1. Get the player's leaderboard entry (their best score)
    try:
        entry = leaderboard_container.read_item(item=player_id, partition_key="global")
    except exceptions.CosmosResourceNotFoundError:
        return None  # 404 — player not found or has no scores

    player_score = entry["bestScore"]

    # 2. Count players with a strictly higher score → rank
    count_result = list(leaderboard_container.query_items(
        query="SELECT VALUE COUNT(1) FROM c WHERE c.bestScore > @score",
        parameters=[{"name": "@score", "value": player_score}],
        partition_key="global",
    ))
    rank = (count_result[0] if count_result else 0) + 1

    # 3. Fetch neighbors: top 21 players around the player's score
    #    OFFSET by (rank - 11) clamped to 0, LIMIT 21 to get ±10
    offset = max(0, rank - 11)
    neighbors_raw = list(leaderboard_container.query_items(
        query=("SELECT c.playerId, c.displayName, c.bestScore AS score FROM c "
               "ORDER BY c.bestScore DESC, c.displayName ASC "
               "OFFSET @offset LIMIT 21"),
        parameters=[{"name": "@offset", "value": offset}],
        partition_key="global",
    ))
    # Assign ranks to neighbors
    neighbors = []
    for i, n in enumerate(neighbors_raw):
        n["rank"] = offset + i + 1
        if n["playerId"] != player_id:   # exclude the player themselves
            neighbors.append(n)

    return {
        "playerId": player_id,
        "rank": rank,           # integer
        "score": player_score,  # integer
        "neighbors": neighbors, # array of {rank, playerId, displayName, score}
    }
```

This pattern:
- Requires the leaderboard container (see `pattern-leaderboard-query.md`) — all entries in one partition
- Uses COUNT for O(1) rank calculation instead of loading all entries
- Reuses the same `ORDER BY bestScore DESC, displayName ASC` composite index for neighbors
- Returns the required response shape: `{playerId, rank, score, neighbors[]}`

Reference: [Cosmos DB query optimization](https://learn.microsoft.com/azure/cosmos-db/nosql/query/getting-started)
