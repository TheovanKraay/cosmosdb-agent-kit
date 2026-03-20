"""
Cosmos DB Infrastructure & SDK Behavior Tests — Healthcare Appointments
========================================================================

These tests go BELOW the HTTP API surface to verify that the agent
applied Cosmos DB best practices at the SDK and container level.

Test categories:
  1. INFRASTRUCTURE — verify container partition keys, indexing policies,
     throughput mode, composite indexes directly via Cosmos DB Python SDK.
  2. SDK BEHAVIORS — verify that SDK-specific patterns (enum serialization,
     ETag for concurrency, content-response-on-write) are correct.
  3. CROSS-BOUNDARY — write data through the HTTP API, then read it
     directly from Cosmos DB to catch serialization mismatches.

These tests are the ones most likely to FAIL without skills loaded.
"""

import pytest


# ============================================================================
# 1. INFRASTRUCTURE TESTS — Container Configuration
# ============================================================================

class TestContainerDesign:
    """
    Rules: partition-high-cardinality, partition-query-patterns

    A healthcare appointment system has multiple access patterns:
    - Patient lookups (by patientId)
    - Provider lookups (by providerId)
    - Appointment queries (by patientId or providerId)
    - Provider search (by specialty)

    The system should use multiple containers or synthetic partition keys.
    """

    def test_has_multiple_containers_or_synthetic_keys(self, cosmos_containers):
        """
        Healthcare systems need separate access patterns:
        - Patient profiles (keyed by patientId)
        - Provider profiles (keyed by providerId or specialty)
        - Appointments (keyed by patientId or providerId)

        Either use multiple containers or synthetic partition keys.
        """
        if len(cosmos_containers) >= 2:
            return  # Multiple containers — likely correct design

        # Single container — check for synthetic partition key patterns
        for c in cosmos_containers:
            paths = c.get("partitionKey", {}).get("paths", [])
            for p in paths:
                if any(kw in p.lower() for kw in ("partition", "composite", "type")):
                    return

        pytest.fail(
            "Only one container with a simple partition key. "
            "Healthcare appointment systems need different access patterns: "
            "patient lookup (by patientId), provider search (by specialty), "
            "and appointment queries (by patientId or providerId). "
            "Use multiple containers or synthetic partition keys. "
            "(Rules: partition-high-cardinality, partition-query-patterns)"
        )

    def test_appointment_container_partition_key(self, cosmos_containers):
        """
        Appointment containers should be partitioned by patientId or providerId
        for efficient queries scoped to a single patient or provider.
        """
        appt_containers = [
            c for c in cosmos_containers
            if any(kw in c["id"].lower() for kw in ("appointment", "appt", "booking"))
        ]

        if not appt_containers:
            pytest.skip("No appointment-specific container found")

        for c in appt_containers:
            paths = c.get("partitionKey", {}).get("paths", [])
            has_entity_key = any(
                "patient" in p.lower() or "provider" in p.lower()
                for p in paths
            )
            assert has_entity_key, (
                f"Appointment container '{c['id']}' doesn't use patientId or "
                f"providerId as partition key (has: {paths}). "
                f"Most appointment queries are scoped to a single patient or provider — "
                f"partition key should match for efficient point reads. "
                f"(Rule: partition-query-patterns)"
            )

    def test_no_id_only_partition_key(self, cosmos_containers):
        """No container should use /id as its sole partition key."""
        for c in cosmos_containers:
            paths = c.get("partitionKey", {}).get("paths", [])
            assert paths != ["/id"], (
                f"Container '{c['id']}' uses /id as sole partition key. "
                f"This is an anti-pattern — it prevents efficient cross-document "
                f"queries within a partition. "
                f"(Rule: partition-high-cardinality)"
            )


class TestAppointmentIndexing:
    """
    Rule: index-composite

    Appointment queries need composite indexes for efficient ORDER BY
    on (dateTime DESC) and filtering by status within a partition.
    """

    def test_has_composite_indexes(self, cosmos_containers):
        """At least one container should have composite indexes for appointment queries."""
        has_composite = False
        for c in cosmos_containers:
            policy = c.get("indexingPolicy", {})
            composites = policy.get("compositeIndexes", [])
            if composites:
                has_composite = True
                break

        assert has_composite, (
            "No container has composite indexes. "
            "Appointment queries need composite indexes on (dateTime DESC, status) "
            "for efficient ORDER BY and filtering within a partition. Without them, "
            "the query engine must sort in memory, increasing RU cost. "
            "(Rule: index-composite)"
        )

    def test_has_custom_indexing_policy(self, cosmos_containers):
        """At least one container should exclude unused paths from indexing."""
        has_custom = False
        for c in cosmos_containers:
            policy = c.get("indexingPolicy", {})
            excluded = policy.get("excludedPaths", [])
            non_default = [
                p for p in excluded
                if p.get("path") not in ("/_etag/?", '"/_etag"/?', "/*")
            ]
            if non_default:
                has_custom = True
                break

        assert has_custom, (
            "All containers use default indexing (index everything). "
            "Exclude paths that are never queried (e.g., phone, reason) "
            "to reduce write RU cost. "
            "(Rule: index-exclude-unused)"
        )


class TestThroughputConfiguration:
    """Verify throughput is explicitly configured."""

    def test_throughput_is_configured(self, cosmos_database, cosmos_containers):
        """At least one container or the database should have throughput set."""
        has_throughput = False

        try:
            offer = cosmos_database.read_offer()
            if offer is not None:
                has_throughput = True
        except Exception:
            pass

        if not has_throughput:
            for c in cosmos_containers:
                try:
                    container = cosmos_database.get_container_client(c["id"])
                    offer = container.read_offer()
                    if offer is not None:
                        has_throughput = True
                        break
                except Exception:
                    pass

        assert has_throughput, (
            "No throughput configuration found. "
            "Explicitly configure throughput (autoscale preferred for variable workloads). "
            "(Rule: throughput-autoscale)"
        )


# ============================================================================
# 2. SDK BEHAVIOR TESTS
# ============================================================================

class TestAppointmentSerialization:
    """
    Rules: model-json-serialization, sdk-etag-concurrency

    Verify that appointment status and dates are serialized correctly
    in Cosmos DB.
    """

    def test_status_stored_as_string(self, api, seeded_data, cosmos_container_map):
        """Appointment status should be stored as a string, not an integer."""
        for name, container in cosmos_container_map.items():
            try:
                items = list(container.query_items(
                    query="SELECT TOP 5 * FROM c WHERE IS_DEFINED(c.status)",
                    enable_cross_partition_query=True,
                ))
            except Exception:
                continue

            for doc in items:
                status = doc.get("status")
                if status is not None:
                    assert isinstance(status, str), (
                        f"Status stored as {type(status).__name__} ({status!r}). "
                        f"Status must be a string (e.g., 'scheduled', 'completed') "
                        f"for query predicates and readability. "
                        f"Integer enums are fragile and break cross-service contracts. "
                        f"(Rule: model-enum-strings)"
                    )
                    return

        pytest.skip("Could not find documents with status field in Cosmos DB")

    def test_etag_present_on_appointment_documents(self, api, seeded_data, cosmos_container_map):
        """
        Appointment documents should carry _etag (Cosmos DB provides this automatically).
        The app should use it for optimistic concurrency on status updates.
        """
        for name, container in cosmos_container_map.items():
            try:
                items = list(container.query_items(
                    query=(
                        "SELECT TOP 1 * FROM c WHERE "
                        "IS_DEFINED(c.appointmentId) OR IS_DEFINED(c.appointment_id)"
                    ),
                    enable_cross_partition_query=True,
                ))
            except Exception:
                continue

            if items:
                doc = items[0]
                etag = doc.get("_etag")
                assert etag is not None, (
                    "Appointment document has no _etag. Cosmos DB should always include "
                    "_etag — if it's missing, the SDK may be stripping system properties."
                )
                return

        pytest.skip("Could not find appointment documents in Cosmos DB")

    def test_datetime_stored_as_string(self, api, seeded_data, cosmos_container_map):
        """dateTime fields should be stored as ISO-8601 strings, not epoch numbers."""
        for name, container in cosmos_container_map.items():
            try:
                items = list(container.query_items(
                    query="SELECT TOP 5 * FROM c WHERE IS_DEFINED(c.dateTime)",
                    enable_cross_partition_query=True,
                ))
            except Exception:
                continue

            for doc in items:
                dt = doc.get("dateTime")
                if dt is not None:
                    assert isinstance(dt, str), (
                        f"dateTime stored as {type(dt).__name__} ({dt!r}). "
                        f"Dates must be ISO-8601 strings for range queries and readability. "
                        f"Epoch numbers are harder to debug and sort lexicographically wrong. "
                        f"(Rule: model-json-serialization)"
                    )
                    return

        pytest.skip("Could not find documents with dateTime field in Cosmos DB")


class TestDocumentStructure:
    """Verify document modeling best practices."""

    def test_documents_have_type_discriminator(self, api, seeded_data, cosmos_container_map):
        """Documents should have a 'type' field for polymorphic containers."""
        found_type = False
        for name, container in cosmos_container_map.items():
            try:
                items = list(container.query_items(
                    query="SELECT TOP 3 * FROM c",
                    enable_cross_partition_query=True,
                ))
            except Exception:
                continue

            for doc in items:
                if any(field in doc for field in ("type", "_type", "documentType", "entityType")):
                    found_type = True
                    break
            if found_type:
                break

        assert found_type, (
            "No documents have a type discriminator field. "
            "Use a 'type' field (e.g., 'patient', 'provider', 'appointment') "
            "to distinguish document types within a container. "
            "(Rule: model-type-discriminator)"
        )

    def test_documents_have_schema_version(self, api, seeded_data, cosmos_container_map):
        """Documents should include a schema version for future evolution."""
        for name, container in cosmos_container_map.items():
            try:
                items = list(container.query_items(
                    query="SELECT TOP 3 * FROM c",
                    enable_cross_partition_query=True,
                ))
            except Exception:
                continue

            for doc in items:
                if any(field in doc for field in ("schemaVersion", "schema_version", "_version", "docVersion")):
                    return

        pytest.fail(
            "No documents have a schema version field. "
            "(Rule: model-schema-versioning)"
        )


# ============================================================================
# 3. CROSS-BOUNDARY TESTS — API ↔ Cosmos DB Round-Trip
# ============================================================================

class TestCrossBoundaryConsistency:
    """
    Write through the API, read directly from Cosmos DB to catch
    serialization mismatches invisible to round-trip HTTP tests.
    """

    def test_appointment_stored_correctly(self, api, seeded_data, cosmos_container_map):
        """
        After creating an appointment via the API, verify it is correctly
        stored in Cosmos DB with all expected fields.
        """
        # Get appointment from the API
        resp = api.request("GET", "/api/appointments/appt-001")
        if resp.status_code != 200:
            pytest.skip("Could not get appointment from API")

        api_appt = resp.json()
        api_status = api_appt.get("status")

        # Now read directly from Cosmos DB
        for name, container in cosmos_container_map.items():
            try:
                items = list(container.query_items(
                    query="SELECT * FROM c WHERE c.appointmentId = @aid",
                    parameters=[{"name": "@aid", "value": "appt-001"}],
                    enable_cross_partition_query=True,
                ))
            except Exception:
                continue

            for doc in items:
                stored_status = doc.get("status")
                if stored_status is not None:
                    if api_status is not None:
                        assert stored_status == api_status, (
                            f"Cosmos DB status ({stored_status}) != API status ({api_status}). "
                            f"Status may be transformed incorrectly between storage and API."
                        )
                    return

        pytest.skip("Could not find appointment document in Cosmos DB")

    def test_patient_stored_correctly(self, api, seeded_data, cosmos_container_map):
        """
        After creating a patient via the API, verify the patient document
        exists in Cosmos DB with matching field values.
        """
        resp = api.request("GET", "/api/patients/patient-001")
        if resp.status_code != 200:
            pytest.skip("Could not get patient from API")

        api_patient = resp.json()
        api_email = api_patient.get("email")

        for name, container in cosmos_container_map.items():
            try:
                items = list(container.query_items(
                    query="SELECT * FROM c WHERE c.patientId = @pid",
                    parameters=[{"name": "@pid", "value": "patient-001"}],
                    enable_cross_partition_query=True,
                ))
            except Exception:
                continue

            for doc in items:
                stored_email = doc.get("email")
                if stored_email is not None:
                    if api_email is not None:
                        assert stored_email == api_email, (
                            f"Cosmos DB email ({stored_email}) != API email ({api_email}). "
                            f"Data may be transformed incorrectly between storage and API."
                        )
                    return

        pytest.skip("Could not find patient document in Cosmos DB")

    def test_provider_specialty_stored_correctly(self, api, seeded_data, cosmos_container_map):
        """
        Provider specialty should be stored as a string in Cosmos DB,
        matching what the API returns.
        """
        resp = api.request("GET", "/api/providers/provider-001")
        if resp.status_code != 200:
            pytest.skip("Could not get provider from API")

        api_provider = resp.json()
        api_specialty = api_provider.get("specialty")

        for name, container in cosmos_container_map.items():
            try:
                items = list(container.query_items(
                    query="SELECT * FROM c WHERE c.providerId = @pid",
                    parameters=[{"name": "@pid", "value": "provider-001"}],
                    enable_cross_partition_query=True,
                ))
            except Exception:
                continue

            for doc in items:
                stored_specialty = doc.get("specialty")
                if stored_specialty is not None:
                    assert isinstance(stored_specialty, str), (
                        f"Specialty stored as {type(stored_specialty).__name__}. "
                        f"Should be a string for query filtering."
                    )
                    if api_specialty is not None:
                        assert stored_specialty == api_specialty, (
                            f"Cosmos DB specialty ({stored_specialty}) != "
                            f"API specialty ({api_specialty}). "
                            f"Specialty may be transformed incorrectly."
                        )
                    return

        pytest.skip("Could not find provider document in Cosmos DB")
