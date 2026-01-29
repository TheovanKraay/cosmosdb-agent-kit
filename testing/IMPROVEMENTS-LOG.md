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

#### 2026-01-29: Iteration 003 - IoT Telemetry Scenario (Python / FastAPI)

- **Scenario**: iot-device-telemetry
- **Iteration**: 003-python (WITH skills)
- **Result**: ✅ **SUCCESSFUL** - End-to-end tested, database and containers created in emulator
- **Score**: 9/10
- **Key Achievement**: **VALIDATED Rule 4.9** and successfully tested complete application with database creation
- **Rules Applied**: 30+ rules including hierarchical partition keys (production), TTL, autoscale, composite indexes

**Issues Encountered & Resolved**:

1. **Pydantic Dependency Issue** (Python 3.13 compatibility)
   - Problem: pydantic 2.5.3 doesn't have prebuilt wheels for Python 3.13
   - Error: "Failed building wheel for pydantic-core"
   - Solution: Updated to pydantic 2.10.0 and pydantic-core 2.27.0
   - Status: ✅ RESOLVED

2. **Environment Variable Override** (**Rule 4.9 Validated**)
   - Problem: System `COSMOS_ENDPOINT` environment variable overrode `.env` file
   - Error: "Local Authorization is disabled. Use an AAD token"
   - Solution: Added `python-dotenv` with `load_dotenv(override=True)` before pydantic-settings
   - Status: ✅ RESOLVED - Rule 4.9 accurately predicted this scenario

3. **ThroughputProperties API Usage**
   - Problem: Python SDK doesn't accept dict for autoscale throughput
   - Error: "TypeError: offer_throughput must be int or an instance of ThroughputProperties"
   - Solution: Changed from `{'maxThroughput': 1000}` to `ThroughputProperties(auto_scale_max_throughput=1000)`
   - Status: ✅ RESOLVED

4. **Emulator Hierarchical Partition Key Limitation**
   - Problem: Local emulator doesn't support `kind='MultiHash'` for hierarchical partition keys
   - Error: "The 'kind' value 'MultiHash' specified in the partition key definition is invalid"
   - Solution: Used single partition key `/deviceId` for emulator, documented production recommendation
   - Status: ✅ RESOLVED with documentation for production

**Test Results**:

✅ Database `iot-telemetry-db` created in emulator
✅ Container `devices` created with partition key `/id` (autoscale 1000 RU/s)
✅ Container `telemetry` created with partition key `/deviceId` (autoscale 4000 RU/s, TTL enabled)
✅ Application started successfully on http://0.0.0.0:8000
✅ Gateway connection mode working with emulator
✅ Environment variables loaded correctly from .env file

**Lessons Learned**:

1. Always test end-to-end with database creation verification (not just code review)
2. Python 3.13 is new - check package compatibility before using
3. Use `python-dotenv` with `override=True` to prevent system env var issues
4. Python SDK API differs from other SDKs (ThroughputProperties class vs dict)
5. Local emulator has limitations (hierarchical partition keys not supported)

**Rule Enhancement Recommendations**:

None - Rule 4.9 worked exactly as designed and helped identify/resolve the environment configuration issue.
Potential future rules:
- Python SDK-specific rule about ThroughputProperties class usage
- Emulator limitations documentation (hierarchical keys, etc.)
  - Solution: Recommend logging endpoint at startup (already in rule) + manual .env loading if override needed
  - Priority: HIGH - affects all FastAPI/Pydantic v2 projects

**Python SDK Observations**:
- ✅ Hierarchical partition key requires dict format: `{'paths': [...], 'kind': 'MultiHash', 'version': 2}`
- ⚠️ No built-in bulk executor (unlike .NET/Java)
- ⚠️ Python SDK is not truly async (HTTP is synchronous under the hood)

**Lessons Learned**:
- Rule 4.9 is critical and accurately predicts real-world issues
- Logging endpoint at startup is essential for catching config issues
- Pydantic v2 environment variable handling differs from python-dotenv
- Python 3.13 requires newer dependency versions

**FILES TO MODIFY** (Proposed):
- `rules/sdk-local-dev-config.md` (enhance with pydantic-settings example - HIGH priority)
- `rules/partition-hierarchical.md` (add Python dict format example - MEDIUM priority)

---

#### 2026-01-29: Iteration 002 - IoT Telemetry Scenario (Java / Spring Boot)

- **Scenario**: iot-device-telemetry
- **Iteration**: 002-java (WITH skills)
- **Result**: ✅ **BUILD SUCCESS** - Complete Spring Boot 3 implementation after resolving compatibility issues
- **Score**: 8/10
- **Key Achievement**: Validated Cosmos DB best practices in Java ecosystem, identified framework version requirements gap
- **Rules Applied**: 26+ rules from AGENTS.md including:
  - 1.2: Denormalized device info in telemetry readings (deviceName, location)
  - 1.3: Embedded device summary for read efficiency
  - 1.5: Schema versioning (schemaVersion field)
  - 1.6: Type discriminators (type="device", type="telemetry")
  - 2.3: Hierarchical partition key (deviceId + yearMonth)
  - 2.5: Partition key aligned with query patterns
  - 3.1: All queries single-partition
  - 3.5: Parameterized queries throughout
  - 4.1: Async APIs with Project Reactor (Mono/Flux)
  - 4.13: Singleton CosmosClient
  - 5.1: Composite indexes for time-range queries
  - 6.1: Autoscale throughput for variable IoT workload
  - TTL: 30-day automatic expiration at container level

**Issues Encountered and Resolved**:

1. **Java Version Mismatch** (CRITICAL - NEW RULE NEEDED)
   - Problem: Spring Boot 3.2.1 requires Java 17+, pom.xml initially had Java 11
   - Error: "bad class file...has wrong version 61.0, should be 55.0"
   - Solution: Updated `<java.version>` to 17, set JAVA_HOME to Java 17 installation
   - Impact: Build blocker - developers would fail immediately without this knowledge
   - **GAP IDENTIFIED**: AGENTS.md doesn't document Java/Spring Boot version requirements

2. **SDK API Evolution** (MEDIUM impact)
   - Problem: `setMaxItemCount()` method not public in azure-cosmos 4.52.0
   - Solution: Removed explicit page size settings (SDK uses sensible defaults)
   - Impact: Code compiles after removal, no functionality loss

3. **Hierarchical Partition Key API** (HIGH - DOCUMENTATION NEEDED)
   - Problem: Constructor signature changed in current SDK version
   - Old (non-working): `new CosmosContainerProperties(name, List<String>)`
   - New (working):
   ```java
   PartitionKeyDefinition partitionKeyDef = new PartitionKeyDefinition();
   partitionKeyDef.setPaths(Arrays.asList("/deviceId", "/yearMonth"));
   partitionKeyDef.setKind(PartitionKind.MULTI_HASH);
   partitionKeyDef.setVersion(PartitionKeyDefinitionVersion.V2);
   ```
   - Impact: Build failure without proper API usage
   - **GAP IDENTIFIED**: Rule 2.3 mentions hierarchical keys but lacks Java SDK-specific API example

**Proposed Skill Improvements**:

1. **NEW RULE: `sdk-java-spring-boot-versions.md`** (CRITICAL impact)
   - Category: SDK Configuration (Section 4)
   - Problem: Framework version requirements not documented in skills
   - Content: Document Spring Boot 3.x → Java 17+, Spring Boot 2.7.x → Java 11/17, SDK compatibility matrix
   - Rationale: Prevents immediate build failures, critical for developer onboarding
   - Priority: HIGH - foundational requirement

2. **ENHANCE RULE 2.3**: Add Java SDK-specific hierarchical partition key code
   - Current state: Conceptual explanation only
   - Enhancement: Add code example showing `PartitionKeyDefinition` with `MULTI_HASH` and `V2`
   - Rationale: API has evolved, developers need current syntax
   - Priority: MEDIUM - affects hierarchical partition key adoption

**Observations**:
- ✅ All Cosmos DB best practices successfully applied in Java/Spring Boot ecosystem
- ✅ Async/reactive programming with Project Reactor properly implemented
- ✅ Build artifacts generated correctly (iot-telemetry-api-1.0.0.jar - 18.4 MB)
- ⚠️ Framework version requirements are external to Cosmos DB but critical for successful implementation
- ⚠️ SDK API documentation should be version-specific

**Lessons Learned**:
- Spring Boot version dictates minimum Java version (non-negotiable dependency)
- Azure Cosmos SDK API evolves between versions - check current documentation
- Skills should include framework compatibility matrices for complete guidance
- Hierarchical partition key API requires explicit V2 definition in Java SDK

**FILES TO MODIFY** (Proposed):
- `rules/sdk-java-spring-boot-versions.md` (new - CRITICAL)
- `rules/partition-hierarchical.md` (enhance with Java API example - MEDIUM)
- `rules/_sections.md` (if new rule added)
- `SKILL.md` (update quick reference)
- `AGENTS.md` (regenerate after rule changes)

---

#### 2026-01-29: Iteration 001 - IoT Telemetry Scenario (.NET)

- **Scenario**: iot-device-telemetry
- **Iteration**: 001-dotnet (WITH skills)
- **Result**: ✅ **SUCCESS** - Excellent implementation with all best practices applied
- **Score**: 9.5/10
- **Key Achievement**: Demonstrated comprehensive application of best practices for time-series IoT data
- **Rules Applied**: 30+ rules from AGENTS.md including:
  - 1.2: Denormalized `latestReading` in Device (avoids joins)
  - 1.3: Embedded `TelemetrySummary` for related data
  - 1.6: Type discriminators (`type` field)
  - 2.1: Hierarchical partition key prevents 20GB limit
  - 2.2: Synthetic key distributes writes (no hot partition)
  - 2.3: Hierarchical partition key (`deviceId` + `yearMonth`)
  - 2.5: Partition key aligned with query patterns
  - 2.6: Synthetic partition key for time-series
  - 3.1: All queries single-partition
  - 3.5: Parameterized queries throughout
  - 4.1: Async APIs throughout
  - 4.4/4.6: Correct mode per environment (Gateway for emulator, Direct for production)
  - 4.11: 429 retry configuration
  - 4.13: Singleton CosmosClient
  - 5.1: Composite indexes for time-range queries
  - 5.2: Excluded unused paths (temperature, humidity, batteryLevel)
  - 6.1: Autoscale for variable IoT workload
  - TTL: 30-day automatic expiration
- **Observations**:
  - ✅ All best practices from AGENTS.md were successfully applied
  - ✅ Hierarchical partition key perfectly suited for time-series data
  - ✅ Database initialized successfully with optimal container configurations
  - ✅ Clean build with no errors
  - ✅ Comprehensive documentation linking every decision to best practices
- **No Issues Found**: The iteration ran smoothly with no bugs or gaps requiring new rules
- **Lessons Learned**:
  - Indexing policy must include "/" path - learned to use `/*` with exclusions rather than overly specific inclusions
  - TCP connection settings only apply to Direct mode (not Gateway for emulator)
  - The skill kit is comprehensive for IoT time-series scenarios

**NO NEW RULES NEEDED**: All patterns and issues were covered by existing rules. This validates the skill kit's completeness for time-series IoT workloads.

**FILES MODIFIED**: None - no skill improvements needed

---

#### 2026-01-28: Cross-Iteration Review - New Rules from Lessons Learned

Based on reviewing all iteration ITERATION.md files and identifying patterns that could become rules:

**NEW RULES CREATED:**

1. **`pattern-change-feed-materialized-views.md`** (HIGH impact)
   - **Source**: Iterations 001, 002, 003 all noted cross-partition queries for admin endpoints
   - **Issue**: Status/date queries require expensive cross-partition fan-out
   - **Solution**: Document Change Feed pattern to maintain separate container optimized for admin queries
   - **Benefit**: Converts cross-partition queries into single-partition lookups

2. **`sdk-java-content-response.md`** (MEDIUM impact)
   - **Source**: Iteration 003-java discovered createItem returned null
   - **Issue**: Java SDK doesn't return document content after write operations by default
   - **Solution**: Set `contentResponseOnWriteEnabled(true)` at client or request level
   - **Benefit**: Prevents confusion when created items appear null

3. **`sdk-local-dev-config.md`** (MEDIUM impact)
   - **Source**: Iteration 004-python encountered system env vars overriding .env file
   - **Issue**: Developers with Azure CLI configured have COSMOS_ENDPOINT pointing to cloud, not emulator
   - **Solution**: Use `load_dotenv(override=True)`, environment-specific configs, and log endpoint at startup
   - **Benefit**: Prevents accidental connections to production during local development

**EXISTING RULE ENHANCED:**

4. **`sdk-emulator-ssl.md`** - Now covers ALL SDKs
   - **Previous**: Java-only SSL certificate guidance
   - **Enhanced**: Added .NET, Python, Node.js examples for Gateway mode and SSL handling
   - **Benefit**: Single reference for all SDK emulator configuration

**COMPILE SCRIPT UPDATED:**
- Added `pattern-` prefix for Design Patterns category (section 9)

**FILES MODIFIED:**
- `rules/pattern-change-feed-materialized-views.md` (new)
- `rules/sdk-java-content-response.md` (new)
- `rules/sdk-local-dev-config.md` (new)
- `rules/sdk-emulator-ssl.md` (enhanced)
- `rules/_sections.md` (added section 9)
- `SKILL.md` (updated quick reference)
- `scripts/compile.js` (added pattern- prefix)
- `AGENTS.md` (regenerated - now 53 rules)
- `testing/README.md` (added Continuous Improvement / Feedback Loop section)

---

#### 2026-01-28: Iteration 003 - Java SDK Validation (ecommerce-order-api)

- **Scenario**: ecommerce-order-api
- **Iteration**: 003-java (WITH skills)
- **Result**: ✅ **SUCCESS** - Critical enum serialization test passed
- **Improvement Validated**: Rule 4.10 works across both .NET and Java SDKs
- **Score**: 9.0/10
- **Key Rules Applied**:
  - 4.10: `@JsonValue`/`@JsonCreator` for enum serialization ✅
  - 4.1: Singleton CosmosAsyncClient via Spring @Bean ✅
  - 4.2: Async APIs with Project Reactor (Mono<T>) ✅
  - 4.3: ThrottlingRetryOptions configured ✅
  - 1.1: Embedded OrderItems ✅
  - 2.1: High-cardinality partition key (customerId) ✅
  - 3.1/3.5: Single-partition queries with SqlParameter ✅
  - 5.2: Composite indexes ✅
- **Observations**: 
  - The skill kit successfully guided Java-specific enum serialization
  - Gateway mode required for emulator (Direct mode has SSL issues)
  - `contentResponseOnWriteEnabled(true)` needed to get item back after create
- **Tests**: 5/6 passing (date range query has format issue, not enum-related)

**NEW RULE CREATED**: `sdk-emulator-ssl` - Configure SSL for Cosmos DB Emulator in Java
- **Issue**: Java SDK fails with SSL handshake errors when connecting to emulator
- **Solution**: Import emulator certificate into JDK truststore + use Gateway mode
- **Files Modified**: 
  - `rules/sdk-emulator-ssl.md` (new file)
  - `SKILL.md` (added to quick reference)
  - `AGENTS.md` (regenerated)

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
