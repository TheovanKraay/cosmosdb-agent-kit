---
title: Keep Leaderboard Entries Consistent When Player Profiles Change
impact: HIGH
impactDescription: prevents stale region data and orphaned players in leaderboards after updates and deletions
tags:
  - leaderboard
  - consistency
  - update
  - delete
  - denormalization
  - pattern
---

## Rule

When a player's profile is updated (region, displayName) or deleted, the corresponding leaderboard entry in the leaderboard container **must also be updated or deleted in the same request handler**. Leaderboard entries are denormalized copies of player data; failing to propagate changes leaves stale or orphaned entries that break regional leaderboards and rank calculations.

## Why

- The leaderboard container stores a denormalized snapshot of each player's data (region, displayName, bestScore). If a player moves from "US" to "EU" and the leaderboard entry is not updated, they continue to appear in the US regional leaderboard and are invisible to the EU one.
- Deleted players whose leaderboard entry is not removed continue to rank in the global and regional leaderboards, inflating ranks for other players and failing consistency tests.
- Cosmos DB has no built-in cascade-delete or foreign-key enforcement. The application must explicitly maintain consistency across containers.

## How

### On player profile update (PATCH /api/players/{playerId})

```python
def update_player(player_id, updates):
    # 1. Update the player document
    player = players_container.read_item(item=player_id, partition_key=player_id)
    player.update({k: v for k, v in updates.items() if k in ("displayName", "region")})
    players_container.upsert_item(player)

    # 2. Propagate changes to the leaderboard entry (if one exists)
    try:
        entry = leaderboard_container.read_item(item=player_id, partition_key="global")
        changed = False
        if "displayName" in updates:
            entry["displayName"] = updates["displayName"]
            changed = True
        if "region" in updates:
            entry["region"] = updates["region"]  # critical for regional leaderboard
            changed = True
        if changed:
            leaderboard_container.upsert_item(entry)
    except exceptions.CosmosResourceNotFoundError:
        pass  # Player has no scores yet, no leaderboard entry to update

    return player
```

### On player deletion (DELETE /api/players/{playerId})

```python
def delete_player(player_id):
    # 1. Delete the player document
    players_container.delete_item(item=player_id, partition_key=player_id)

    # 2. Delete all score documents for this player
    scores = list(scores_container.query_items(
        query="SELECT c.id FROM c WHERE c.playerId = @pid",
        parameters=[{"name": "@pid", "value": player_id}],
        enable_cross_partition_query=True,
    ))
    for score in scores:
        scores_container.delete_item(item=score["id"], partition_key=score["id"])

    # 3. Delete the leaderboard entry (player must not appear in any leaderboard)
    try:
        leaderboard_container.delete_item(item=player_id, partition_key="global")
    except exceptions.CosmosResourceNotFoundError:
        pass  # No leaderboard entry exists (player had no scores) — that's fine
```

### Example (Good) — **Correct** pattern

```python
# PATCH /api/players/{playerId} — region change propagated to leaderboard
updates = {"region": "EU"}
player.update(updates)
players_container.upsert_item(player)          # update player doc
entry["region"] = "EU"
leaderboard_container.upsert_item(entry)       # keep leaderboard in sync
# Result: player now appears in EU regional leaderboard, not US
```

### Example (Bad) — **Incorrect** anti-patterns

```python
# PATCH: only updates player doc — leaderboard entry keeps stale region
def update_player(player_id, updates):
    player = players_container.read_item(item=player_id, partition_key=player_id)
    player.update(updates)
    return players_container.upsert_item(player)
    # BUG: player moved US→EU but still shows up in US leaderboard

# DELETE: only deletes player — orphan leaderboard entry lingers
def delete_player(player_id):
    players_container.delete_item(item=player_id, partition_key=player_id)
    # BUG: player is gone but still ranked in the global leaderboard
```

## References

- [Cosmos DB data modeling: managing relationships](https://learn.microsoft.com/azure/cosmos-db/nosql/modeling-data)
- [Denormalization patterns](https://learn.microsoft.com/azure/cosmos-db/nosql/modeling-data#denormalization)
