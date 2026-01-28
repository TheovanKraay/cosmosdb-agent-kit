# Improvements Log

This document tracks all skill improvements made as a result of testing iterations. Each entry links the improvement back to the test scenario and iteration that identified the need.

---

## Format

Each improvement entry should include:
- **Date**: When the improvement was made
- **Scenario**: Which test scenario identified the need
- **Iteration**: Which iteration discovered the issue
- **Issue**: What problem was observed
- **Improvement**: What was changed in the skills
- **Files Modified**: List of skill files changed

---

## Improvements

### [Unreleased]

_Improvements pending next release_

#### 2026-01-28: Iteration 002 - Skills Successfully Applied (ecommerce-order-api)

- **Scenario**: ecommerce-order-api
- **Iteration**: 002-dotnet (WITH skills)
- **Result**: ✅ **SUCCESS** - All tests passed, including the critical enum serialization test
- **Improvement Validated**: Rule 4.10 (enum serialization) was applied correctly, fixing the bug from iteration-001
- **Score**: 9.1/10 (vs 6/10 in baseline iteration-001)
- **Key Rules Applied**:
  - 4.10: JsonStringEnumConverter configured ✅
  - 4.1: Singleton CosmosClient ✅
  - 4.4: Direct connection mode ✅
  - 1.1: Embedded OrderItems ✅
  - 2.1/2.4: High-cardinality partition key (customerId) ✅
  - 3.1/3.2: Single-partition queries with projections ✅
  - 5.2: Composite indexes ✅
- **Observations**: 
  - The skill kit successfully prevented the enum serialization bug
  - Agent applied 18+ rules from the skill kit
  - Cross-partition queries (status/date) are unavoidable given partition key choice - could add guidance for Change Feed patterns

#### 2026-01-27: Testing Framework - Skills Must Be Loaded First

- **Scenario**: ecommerce-order-api
- **Iteration**: 001-dotnet
- **Issue**: Iteration 001 was run WITHOUT loading the skill kit first, making it a baseline test rather than a skill kit test
- **Improvement**: Updated testing framework documentation to emphasize skills MUST be loaded before each iteration
- **Files Modified**: 
  - `testing/README.md` - Added "CRITICAL: Install Skills FIRST" section
  - `testing/scenarios/_iteration-template.md` - Added "Skills Verification" section
  - `iteration-001-dotnet/ITERATION.md` - Marked as baseline (no skills)

#### 2026-01-27: Enum Serialization Mismatch Bug (ecommerce-order-api)

- **Scenario**: ecommerce-order-api
- **Iteration**: 001-dotnet
- **Issue**: Agent generated code where Cosmos SDK stored enums as integers but queries searched for strings, causing status queries to return empty results
- **Improvement**: ✅ Added new rule `sdk-serialization-enums.md` with guidance on consistent enum serialization
- **Priority**: HIGH
- **Files Modified**: 
  - `rules/sdk-serialization-enums.md` (new)
  - `AGENTS.md` (added section 4.10)

#### 2026-01-27: Missing Pagination Pattern (ecommerce-order-api)

- **Scenario**: ecommerce-order-api
- **Iteration**: 001-dotnet
- **Issue**: List queries returned all results without pagination, which would fail at scale
- **Analysis**: Rule `query-pagination.md` already exists with good content - the agent simply didn't apply it
- **Action**: No rule change needed, but highlights that agents may not always apply existing rules
- **Priority**: HIGH (for observation)
- **Files Modified**: None - rule already exists

#### 2026-01-27: Missing RU Diagnostics Logging (ecommerce-order-api)

- **Scenario**: ecommerce-order-api
- **Iteration**: 001-dotnet
- **Issue**: No logging of CosmosDiagnostics or RU consumption for debugging/monitoring
- **Analysis**: Rule `monitoring-ru-consumption.md` already exists with comprehensive SDK examples - agent didn't apply it
- **Action**: No rule change needed - rule has .NET examples with logging, telemetry, and middleware patterns
- **Priority**: MEDIUM (for observation)
- **Files Modified**: None - rule already exists

---

## Release History

### v1.0.0 (Initial Release)

- Initial set of 48 rules covering:
  - Data modeling (6 rules)
  - Partition key design (6 rules)
  - Query optimization (6 rules)
  - SDK best practices (9 rules)
  - Indexing strategies (5 rules)
  - Throughput & scaling (5 rules)
  - Global distribution (6 rules)
  - Monitoring & diagnostics (5 rules)
