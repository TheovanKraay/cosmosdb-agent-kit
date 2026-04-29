# Iteration 001 - Rust Gaming Leaderboard

## Metadata
- **Date**: 2026-04-28
- **Language/SDK**: Rust (Axum 0.7.9 / reqwest 0.12 / HMAC-SHA256 REST API)
- **Skill Version**: Pre-release (AGENTS.md loaded via issue prompt)
- **Agent**: GitHub Copilot (Claude, automated iteration)
- **Tester**: Automated CI

## ⚠️ Skills Verification

**Were skills loaded before building?** ✅ Yes

**How were skills loaded?**
- [x] Read `skills/cosmosdb-best-practices/AGENTS.md` directly
- [ ] Skills auto-loaded from workspace
- [x] Explicit instruction to follow skills
- [ ] Other

**Verification question asked?** N/A (automated run)

## Prompt Used

```
Read the scenario in testing-v2/scenarios/gaming-leaderboard/SCENARIO.md and
api-contract.yaml. Read the skills in skills/cosmosdb-best-practices/AGENTS.md.
Generate a Rust implementation of the gaming leaderboard API.
```

## What the Agent Produced

### Data Model
- ✅ Three containers: `players` (pk: `/playerId`), `scores` (pk: `/playerId`), `leaderboards` (pk: `/region`)
- ✅ Denormalized leaderboard entries upserted on score submission for O(1) reads
- ✅ Separate document types with `type` discriminator field
- ✅ Integer types (`i64`) for score, bestScore, totalGames; `f64` for averageScore
- ❌ **Missing `schemaVersion` field** on all documents (rule: `model-schema-versioning`)
- ⚠️ Used `_etag` as a document body field for deserialization but properly used HTTP response header ETag for concurrency

### Container Configuration
- ✅ Partition key choices: `/playerId` for players and scores (high cardinality, enables point reads), `/region` for leaderboards (partition-scoped sorted queries)
- ❌ **Missing composite indexes** on leaderboards container — `ORDER BY c.score DESC, c.displayName ASC` requires a composite index (rule: `index-composite`)
- ⚠️ Default indexing policy used (no explicit exclude-unused optimization)
- ⚠️ No throughput configuration specified

### Repository Layer
- ✅ Point reads used for all single-document access (by ID + partition key)
- ✅ Partition-scoped queries (partition key specified in query header)
- ✅ Parameterized queries with `@param` syntax
- ✅ Cross-partition queries avoided
- ❌ `ORDER BY` on multiple fields without composite index causes query failure (500 on leaderboard endpoints)

### SDK Usage
- ✅ Singleton HTTP client (reqwest Client shared via Arc)
- ✅ TLS certificate bypass for emulator (`danger_accept_invalid_certs`)
- ✅ Gateway-compatible REST API approach (HTTP/HTTPS to emulator endpoint)
- ✅ ETag-based optimistic concurrency with retry loop (up to 50 attempts) for player stat updates
- ✅ Proper HMAC-SHA256 auth token generation per REST API spec
- ✅ Upsert operation for leaderboard entries (idempotent writes)
- ⚠️ No diagnostics/logging of RU consumption or request latency

## Build Status
- **Initial Build**: ✅ Succeeded (release mode)
- **CI Startup Issue**: First attempt failed due to Unix-style path in run command (fixed: `.\target\release\gaming-leaderboard.exe`)
- **Runtime Test**: ✅ App started and responded to health check

## Runtime Test Results

### Tests Passed ✅ (38/94)
| Category | Passed | Failed | Notes |
|----------|--------|--------|-------|
| Build & Startup | 2 | 0 | Clean build + health check |
| API Contract | 14 | 31 | Static-path endpoints work; parameterized routes fail |
| Cosmos DB Infrastructure | 10 | 2 | Missing composite index + schema version |
| Data Integrity | 5 | 0 | All data integrity checks pass |
| Robustness | 9 | 22 | Validation logic correct; cascading failures from routing |

### Tests Failed ❌ (55/94)

Root cause analysis reveals **two independent failure clusters**:

#### Cluster 1: Route Matching Failure (≈45 tests)
All endpoints with path parameters (`/api/players/:playerId`, `/api/players/:playerId/scores`, `/api/players/:playerId/rank`, `/api/leaderboards/regional/:region`) returned bare 404 with empty body — indicating Axum's default "no route matched" response, not our handler's 404.

**Root cause**: Used `{param}` syntax for path parameters in Axum route definitions. While matchit 0.7.3 documents support for `{param}`, the combination with Axum 0.7.9 on Windows CI did not resolve these routes. Switching to `:param` syntax fixes the issue.

**Classification**: Code bug (framework quirk) — not a Cosmos DB skill gap.

#### Cluster 2: Leaderboard Query Failure (≈8 tests)
`GET /api/leaderboards/global` returned 500. The query `ORDER BY c.score DESC, c.displayName ASC` requires a composite index on the container.

**Root cause**: Container created without indexing policy. Cosmos DB requires composite indexes for multi-field ORDER BY clauses.

**Classification**: Cosmos DB anti-pattern — existing rule `index-composite` applies but was not followed.

#### Cluster 3: Infrastructure Checks (2 tests)
- Missing composite indexes on leaderboards container
- Missing `schemaVersion` field on documents

**Classification**: Existing rules `index-composite` and `model-schema-versioning` apply.

### Bugs Found 🐛
1. **Axum path parameter syntax**: `{param}` style didn't work; `:param` is required
2. **Duplicate `.route()` calls**: Same path registered multiple times (merged in 0.7.9 but pattern still questionable)
3. **Missing composite index**: Multi-field ORDER BY fails without it
4. **Edge case `top=0`**: Query parameter edge case caused 500 instead of returning empty array

## Gaps Identified

### Critical Gaps (functionality issues)
1. **Route parameter syntax** — Framework-specific issue causing total failure of parameterized endpoints (45+ test failures)
2. **Missing composite index** — Leaderboard queries return 500 without proper indexing

### Best Practice Gaps (suboptimal but works)
1. **No schema versioning** — Documents lack `schemaVersion` field for future migration support
2. **No custom indexing policy** — Default "index everything" used instead of targeted indexes
3. **No RU consumption logging** — No observability into query cost

### Knowledge Gaps (agent didn't know/mention)
1. **Axum `:param` vs `{param}` syntax** — Framework-specific knowledge gap (not Cosmos DB related)
2. **Composite index requirement for multi-field ORDER BY** — Rule exists but wasn't applied

## Recommendations for Skill Improvements

### High Priority
1. **Clarify `index-composite` rule** — Add explicit guidance: "Any query with ORDER BY on multiple fields REQUIRES a composite index defined at container creation time. Without it, the query will fail with an error." Add a container-creation example showing the composite index in the indexing policy JSON.

### Medium Priority
1. **Add `sdk-rust-rest-api` rule** — Since there is no stable Rust SDK for Cosmos DB, agents using Rust must implement REST API calls directly. Guidance on auth token generation, required headers, and partition key header format would help.

### Low Priority
1. **Strengthen `model-schema-versioning` rule** — Add language-specific examples showing the field added to every document type struct/class definition.

## Score Summary

| Category | Score | Notes |
|----------|-------|-------|
| Data Model | 7/10 | Good denormalization, proper partition keys, missing schema version |
| Partition Key | 9/10 | High cardinality for entities, region-scoped for leaderboards |
| Indexing | 3/10 | Missing composite indexes caused functional failures |
| SDK Usage | 7/10 | Proper REST API implementation, ETag concurrency, but no diagnostics |
| Query Patterns | 7/10 | Point reads, partition-scoped queries, parameterized — but ORDER BY failed |
| **Overall** | **5/10** | **App functional but routing bug + missing composite index caused 60% test failure. Core Cosmos DB patterns (denormalization, point reads, ETags) correctly applied.** |

## Next Steps
1. ✅ Fix route parameter syntax (`:param` instead of `{param}`)
2. ✅ Add composite index to leaderboards container creation
3. ✅ Add `schemaVersion` field to all document types
4. ✅ Handle `top=0` edge case
5. Consider updating `index-composite` rule to emphasize container-creation-time requirement
6. Consider adding Rust-specific SDK guidance rule
