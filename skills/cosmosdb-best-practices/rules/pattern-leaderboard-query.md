---
title: Implement Leaderboards with a Shared-Partition Leaderboard Container
impact: HIGH
impactDescription: eliminates 500 errors on leaderboard endpoints and enables efficient single-partition ORDER BY queries
tags:
  - leaderboard
  - partition
  - query
  - ranking
  - pattern
  - denormalization
---

## Rule

For leaderboard scenarios, maintain a dedicated leaderboard container where all entries share a **single partition key value** (e.g., `"global"`). Each player has exactly **one entry** representing their best score. When a new score is submitted, upsert the leaderboard entry only if the new score exceeds the player's current best. Use a composite index on `(bestScore DESC, displayName ASC)` so Cosmos DB can return sorted, paginated results with a single efficient query.

## Why

- Cosmos DB `ORDER BY` across multiple partitions requires scatter-gather fan-out: results from every physical partition are sorted independently and then merged. This is expensive and slow for a global leaderboard. Placing all entries in one partition (partitioned by `"global"`) keeps the query single-partition and cheap.
- Inserting a new document for every score submission creates duplicate entries per player. The leaderboard must show each player **once** with their best score. Upsert (not insert) is the correct operation.
- Tiebreaking on a second field (e.g., `displayName ASC` when scores are equal) requires a composite index. Without one, Cosmos DB will either fail the query or return non-deterministic ordering.
- Returning sequential ranks (1, 2, 3, …) is the responsibility of the application layer — assign rank after reading the sorted list.

## How

### Container design

```python
# Leaderboard container: partition key = /leaderboardKey
# One document per player (id = playerId)
# Value "global" co-locates all entries for cheap single-partition queries
leaderboard_entry = {
    "id": player_id,               # one entry per player
    "leaderboardKey": "global",    # partition key — all entries share this value
    "playerId": player_id,
    "displayName": player["displayName"],
    "region": player["region"],    # needed for regional leaderboard queries
    "bestScore": new_score,
}
container.upsert_item(leaderboard_entry)
```

### Composite index for tiebreaking

Define this on the leaderboard container. Without it, `ORDER BY bestScore DESC, displayName ASC` fails or returns unpredictable order.

```json
{
  "compositeIndexes": [
    [
      { "path": "/bestScore", "order": "descending" },
      { "path": "/displayName", "order": "ascending" }
    ],
    [
      { "path": "/region", "order": "ascending" },
      { "path": "/bestScore", "order": "descending" },
      { "path": "/displayName", "order": "ascending" }
    ]
  ]
}
```

### On score submission — upsert only on new best

```python
def submit_score(player_id, score):
    # 1. Store the score document
    score_doc = {"id": str(uuid4()), "playerId": player_id, "score": score, ...}
    scores_container.create_item(score_doc)

    # 2. Update player stats
    player = players_container.read_item(item=player_id, partition_key=player_id)
    player["totalGames"] = player.get("totalGames", 0) + 1
    player["bestScore"] = max(player.get("bestScore", 0), score)
    # update averageScore ...
    players_container.upsert_item(player)

    # 3. Upsert leaderboard entry only if new best score
    try:
        entry = leaderboard_container.read_item(item=player_id, partition_key="global")
        if score > entry["bestScore"]:
            entry["bestScore"] = score
            leaderboard_container.upsert_item(entry)
    except exceptions.CosmosResourceNotFoundError:
        # First score for this player — create leaderboard entry
        leaderboard_container.upsert_item({
            "id": player_id,
            "leaderboardKey": "global",
            "playerId": player_id,
            "displayName": player["displayName"],
            "region": player["region"],
            "bestScore": score,
        })
```

### Global leaderboard query

```python
def get_global_leaderboard(top=100):
    top = max(0, min(int(top), 100))
    if top == 0:
        return []

    query = """
        SELECT c.playerId, c.displayName, c.bestScore AS score
        FROM c
        ORDER BY c.bestScore DESC, c.displayName ASC
        OFFSET 0 LIMIT @top
    """
    items = list(leaderboard_container.query_items(
        query=query,
        parameters=[{"name": "@top", "value": top}],
        partition_key="global",   # single-partition — no cross-partition fan-out
    ))

    # Assign sequential 1-based ranks in application code
    for i, item in enumerate(items):
        item["rank"] = i + 1   # integer, not string

    return items  # returns [] if no entries — never raise an error for empty
```

### Regional leaderboard query

```python
def get_regional_leaderboard(region, top=100):
    top = max(0, min(int(top), 100))
    if top == 0:
        return []

    query = """
        SELECT c.playerId, c.displayName, c.bestScore AS score
        FROM c
        WHERE c.region = @region
        ORDER BY c.bestScore DESC, c.displayName ASC
        OFFSET 0 LIMIT @top
    """
    items = list(leaderboard_container.query_items(
        query=query,
        parameters=[
            {"name": "@region", "value": region},
            {"name": "@top", "value": top},
        ],
        partition_key="global",
    ))

    for i, item in enumerate(items):
        item["rank"] = i + 1

    return items  # returns [] for unknown/empty regions — never a 404 or 500
```

### Example (Good) — **Correct** pattern

```python
# One entry per player, upserted on new best — no duplicates
{"id": "p001", "leaderboardKey": "global", "playerId": "p001",
 "displayName": "Alice", "region": "US", "bestScore": 8200}

# Single-partition query with composite-indexed ORDER BY
query = ("SELECT c.playerId, c.displayName, c.bestScore AS score FROM c "
         "ORDER BY c.bestScore DESC, c.displayName ASC OFFSET 0 LIMIT @top")
items = list(container.query_items(query=query,
             parameters=[{"name": "@top", "value": 10}],
             partition_key="global"))
# Assign ranks after reading
for i, e in enumerate(items):
    e["rank"] = i + 1
```

### Example (Bad) — **Incorrect** anti-patterns

```python
# Anti-pattern 1: insert per score → duplicate player entries
container.create_item({"id": str(uuid4()), "leaderboardKey": "global",
                        "playerId": "p001", "score": 8200})  # player-001 has N entries!

# Anti-pattern 2: cross-partition query — expensive and slow
items = list(container.query_items(
    "SELECT * FROM c ORDER BY c.bestScore DESC",
    enable_cross_partition_query=True))  # scatter-gather fan-out

# Anti-pattern 3: returning string for rank — causes type assertion failures
item["rank"] = str(i + 1)  # rank must be an integer, not "1", "2", "3"
```

## References

- [Cosmos DB OFFSET LIMIT clause](https://learn.microsoft.com/azure/cosmos-db/nosql/query/offset-limit)
- [Composite indexes for ORDER BY](https://learn.microsoft.com/azure/cosmos-db/index-policy#composite-indexes)
- [Single-partition queries](https://learn.microsoft.com/azure/cosmos-db/nosql/query/getting-started)
