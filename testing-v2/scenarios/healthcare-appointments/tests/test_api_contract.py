"""
API Contract Tests for Healthcare Appointments
================================================

These tests validate that the generated application conforms to the
API contract defined in api-contract.yaml. They test:
- Correct HTTP methods and paths
- Expected request/response schemas
- Correct status codes
- Required fields present in responses
- Correct data types
- Business logic (status transitions, slot availability, filtering)

Each test is designed to produce a clear, actionable failure message
that helps identify whether the issue is:
- A missing/wrong endpoint path
- A missing required field
- A wrong data type
- A business logic error
"""

import pytest


# ===================================================================
# HEALTH CHECK
# ===================================================================

class TestHealth:
    """Verify the health endpoint exists and responds."""

    def test_health_returns_200(self, api):
        resp = api.request("GET", "/health")
        assert resp.status_code == 200, (
            "Health endpoint must return 200. "
            "Ensure your app exposes GET /health"
        )


# ===================================================================
# PATIENT MANAGEMENT
# ===================================================================

class TestCreatePatient:
    """POST /api/patients — Register a new patient."""

    def test_create_patient_returns_201(self, api):
        resp = api.request("POST", "/api/patients", json={
            "patientId": "test-create-patient-001",
            "firstName": "Test",
            "lastName": "Patient",
            "email": "test.patient@example.com",
            "phone": "555-9901",
            "dateOfBirth": "1995-06-15",
        })
        assert resp.status_code == 201, (
            f"POST /api/patients should return 201, got {resp.status_code}. "
            f"Response: {resp.text[:500]}"
        )

    def test_create_patient_response_has_required_fields(self, api):
        resp = api.request("POST", "/api/patients", json={
            "patientId": "test-create-patient-002",
            "firstName": "Field",
            "lastName": "Check",
            "email": "field.check@example.com",
            "phone": "555-9902",
            "dateOfBirth": "1988-12-01",
        })
        assert resp.status_code == 201
        body = resp.json()

        required = ["patientId", "firstName", "lastName", "email", "phone", "dateOfBirth"]
        missing = [f for f in required if f not in body]
        assert not missing, (
            f"Response missing required fields: {missing}. "
            f"Got: {list(body.keys())}. "
            f"See api-contract.yaml create_patient.response.body.required"
        )

    def test_create_patient_returns_correct_data(self, api):
        resp = api.request("POST", "/api/patients", json={
            "patientId": "test-create-patient-003",
            "firstName": "Data",
            "lastName": "Echo",
            "email": "data.echo@example.com",
            "phone": "555-9903",
            "dateOfBirth": "2001-04-20",
        })
        assert resp.status_code == 201
        body = resp.json()

        assert body["patientId"] == "test-create-patient-003"
        assert body["firstName"] == "Data"
        assert body["lastName"] == "Echo"
        assert body["email"] == "data.echo@example.com"

    def test_create_duplicate_patient_returns_409(self, api):
        patient = {
            "patientId": "test-create-patient-dup",
            "firstName": "Dup",
            "lastName": "Test",
            "email": "dup@example.com",
            "phone": "555-9999",
            "dateOfBirth": "1990-01-01",
        }
        resp1 = api.request("POST", "/api/patients", json=patient)
        assert resp1.status_code == 201

        resp2 = api.request("POST", "/api/patients", json=patient)
        assert resp2.status_code == 409, (
            f"Creating duplicate patient should return 409, got {resp2.status_code}"
        )


class TestGetPatient:
    """GET /api/patients/{patientId} — Get patient profile."""

    def test_get_existing_patient(self, api, seeded_data):
        resp = api.request("GET", "/api/patients/patient-001")
        assert resp.status_code == 200, (
            f"GET /api/patients/patient-001 should return 200, got {resp.status_code}"
        )

    def test_get_patient_has_required_fields(self, api, seeded_data):
        resp = api.request("GET", "/api/patients/patient-001")
        assert resp.status_code == 200
        body = resp.json()

        required = ["patientId", "firstName", "lastName", "email", "phone", "dateOfBirth"]
        missing = [f for f in required if f not in body]
        assert not missing, (
            f"GET patient response missing required fields: {missing}. "
            f"Got: {list(body.keys())}"
        )

    def test_get_patient_returns_correct_data(self, api, seeded_data):
        resp = api.request("GET", "/api/patients/patient-001")
        assert resp.status_code == 200
        body = resp.json()

        assert body["patientId"] == "patient-001"
        assert body["firstName"] == "Alice"
        assert body["lastName"] == "Smith"

    def test_get_nonexistent_patient_returns_404(self, api):
        resp = api.request("GET", "/api/patients/nonexistent-patient-xyz")
        assert resp.status_code == 404, (
            f"GET /api/patients/nonexistent-patient-xyz should return 404, "
            f"got {resp.status_code}"
        )


# ===================================================================
# PROVIDER MANAGEMENT
# ===================================================================

class TestCreateProvider:
    """POST /api/providers — Create a new provider profile."""

    def test_create_provider_returns_201(self, api):
        resp = api.request("POST", "/api/providers", json={
            "providerId": "test-create-provider-001",
            "name": "Dr. Test Provider",
            "specialty": "neurology",
            "clinicId": "clinic-Z",
        })
        assert resp.status_code == 201, (
            f"POST /api/providers should return 201, got {resp.status_code}. "
            f"Response: {resp.text[:500]}"
        )

    def test_create_provider_response_has_required_fields(self, api):
        resp = api.request("POST", "/api/providers", json={
            "providerId": "test-create-provider-002",
            "name": "Dr. Field Check",
            "specialty": "orthopedics",
            "clinicId": "clinic-Z",
        })
        assert resp.status_code == 201
        body = resp.json()

        required = ["providerId", "name", "specialty", "clinicId"]
        missing = [f for f in required if f not in body]
        assert not missing, (
            f"Response missing required fields: {missing}. "
            f"Got: {list(body.keys())}. "
            f"See api-contract.yaml create_provider.response.body.required"
        )

    def test_create_provider_returns_correct_data(self, api):
        resp = api.request("POST", "/api/providers", json={
            "providerId": "test-create-provider-003",
            "name": "Dr. Data Echo",
            "specialty": "pediatrics",
            "clinicId": "clinic-Z",
        })
        assert resp.status_code == 201
        body = resp.json()

        assert body["providerId"] == "test-create-provider-003"
        assert body["name"] == "Dr. Data Echo"
        assert body["specialty"] == "pediatrics"
        assert body["clinicId"] == "clinic-Z"

    def test_create_duplicate_provider_returns_409(self, api):
        provider = {
            "providerId": "test-create-provider-dup",
            "name": "Dr. Dup",
            "specialty": "cardiology",
            "clinicId": "clinic-Z",
        }
        resp1 = api.request("POST", "/api/providers", json=provider)
        assert resp1.status_code == 201

        resp2 = api.request("POST", "/api/providers", json=provider)
        assert resp2.status_code == 409, (
            f"Creating duplicate provider should return 409, got {resp2.status_code}"
        )


class TestGetProvider:
    """GET /api/providers/{providerId} — Get provider profile."""

    def test_get_existing_provider(self, api, seeded_data):
        resp = api.request("GET", "/api/providers/provider-001")
        assert resp.status_code == 200, (
            f"GET /api/providers/provider-001 should return 200, got {resp.status_code}"
        )

    def test_get_provider_has_required_fields(self, api, seeded_data):
        resp = api.request("GET", "/api/providers/provider-001")
        assert resp.status_code == 200
        body = resp.json()

        required = ["providerId", "name", "specialty", "clinicId"]
        missing = [f for f in required if f not in body]
        assert not missing, (
            f"GET provider response missing required fields: {missing}. "
            f"Got: {list(body.keys())}"
        )

    def test_get_provider_returns_correct_data(self, api, seeded_data):
        resp = api.request("GET", "/api/providers/provider-001")
        assert resp.status_code == 200
        body = resp.json()

        assert body["providerId"] == "provider-001"
        assert body["name"] == "Dr. Emily Carter"
        assert body["specialty"] == "cardiology"
        assert body["clinicId"] == "clinic-A"

    def test_get_nonexistent_provider_returns_404(self, api):
        resp = api.request("GET", "/api/providers/nonexistent-provider-xyz")
        assert resp.status_code == 404, (
            f"GET /api/providers/nonexistent-provider-xyz should return 404, "
            f"got {resp.status_code}"
        )


class TestSearchProviders:
    """GET /api/providers?specialty=X — Search providers by specialty."""

    def test_search_providers_returns_200(self, api, seeded_data):
        resp = api.request("GET", "/api/providers?specialty=cardiology")
        assert resp.status_code == 200, (
            f"GET /api/providers?specialty=cardiology should return 200, "
            f"got {resp.status_code}"
        )

    def test_search_providers_returns_array(self, api, seeded_data):
        resp = api.request("GET", "/api/providers?specialty=cardiology")
        assert resp.status_code == 200
        body = resp.json()
        assert isinstance(body, list), (
            f"Provider search should return an array, got {type(body).__name__}"
        )

    def test_search_providers_filters_by_specialty(self, api, seeded_data):
        """Cardiology search should return provider-001 and provider-002."""
        resp = api.request("GET", "/api/providers?specialty=cardiology")
        body = resp.json()

        provider_ids = {p["providerId"] for p in body}
        assert "provider-001" in provider_ids, (
            "provider-001 is a cardiologist, should appear in cardiology search"
        )
        assert "provider-002" in provider_ids, (
            "provider-002 is a cardiologist, should appear in cardiology search"
        )
        assert "provider-003" not in provider_ids, (
            "provider-003 is a dermatologist, should NOT appear in cardiology search"
        )

    def test_search_providers_entries_have_required_fields(self, api, seeded_data):
        resp = api.request("GET", "/api/providers?specialty=cardiology")
        body = resp.json()
        assert len(body) > 0, "Cardiology search should not be empty"

        entry = body[0]
        required = ["providerId", "name", "specialty", "clinicId"]
        missing = [f for f in required if f not in entry]
        assert not missing, (
            f"Provider search entry missing required fields: {missing}. "
            f"Got: {list(entry.keys())}"
        )

    def test_search_providers_no_results_returns_empty_array(self, api, seeded_data):
        resp = api.request("GET", "/api/providers?specialty=nonexistent-specialty")
        assert resp.status_code == 200
        body = resp.json()
        assert isinstance(body, list) and len(body) == 0, (
            f"Search for nonexistent specialty should return empty array, "
            f"got {body}"
        )


# ===================================================================
# AVAILABLE SLOTS
# ===================================================================

class TestGetAvailableSlots:
    """GET /api/providers/{providerId}/slots?date=YYYY-MM-DD — Available time slots."""

    def test_get_slots_returns_200(self, api, seeded_data):
        # 2026-06-01 is a Monday, provider-001 works mondays
        resp = api.request("GET", "/api/providers/provider-001/slots?date=2026-06-01")
        assert resp.status_code == 200, (
            f"GET /api/providers/provider-001/slots?date=2026-06-01 should return 200, "
            f"got {resp.status_code}"
        )

    def test_get_slots_returns_array(self, api, seeded_data):
        resp = api.request("GET", "/api/providers/provider-001/slots?date=2026-06-01")
        assert resp.status_code == 200
        body = resp.json()
        assert isinstance(body, list), (
            f"Slots endpoint should return an array, got {type(body).__name__}"
        )

    def test_slots_have_required_fields(self, api, seeded_data):
        resp = api.request("GET", "/api/providers/provider-001/slots?date=2026-06-01")
        body = resp.json()
        assert len(body) > 0, (
            "provider-001 works Mondays 09:00-17:00, slots should not be empty "
            "on 2026-06-01 (Monday)"
        )

        entry = body[0]
        required = ["startTime", "endTime"]
        missing = [f for f in required if f not in entry]
        assert not missing, (
            f"Slot entry missing required fields: {missing}. "
            f"Got: {list(entry.keys())}. "
            f"See api-contract.yaml get_available_slots.response"
        )

    def test_slots_exclude_booked_times(self, api, seeded_data):
        """
        appt-001 is at 2026-06-01T09:00:00 with provider-001.
        The 09:00 slot should NOT appear in available slots.
        """
        resp = api.request("GET", "/api/providers/provider-001/slots?date=2026-06-01")
        body = resp.json()

        start_times = [slot["startTime"] for slot in body]
        booked_starts = [t for t in start_times if "09:00" in t]
        assert len(booked_starts) == 0, (
            f"09:00 slot is booked (appt-001), should not appear in available slots. "
            f"Got start times: {start_times[:10]}"
        )

    def test_slots_sorted_by_time(self, api, seeded_data):
        resp = api.request("GET", "/api/providers/provider-001/slots?date=2026-06-01")
        body = resp.json()
        start_times = [slot["startTime"] for slot in body]
        assert start_times == sorted(start_times), (
            f"Slots should be sorted by time ascending. Got: {start_times[:10]}"
        )

    def test_slots_for_nonexistent_provider_returns_404(self, api):
        resp = api.request("GET", "/api/providers/nonexistent-xyz/slots?date=2026-06-01")
        assert resp.status_code == 404, (
            f"Slots for nonexistent provider should return 404, got {resp.status_code}"
        )

    def test_no_slots_on_day_off(self, api, seeded_data):
        """
        provider-001 does not work Tuesdays.
        Slots on a Tuesday (2026-06-02) should be empty.
        """
        resp = api.request("GET", "/api/providers/provider-001/slots?date=2026-06-02")
        assert resp.status_code == 200
        body = resp.json()
        assert len(body) == 0, (
            f"provider-001 does not work Tuesdays, slots on 2026-06-02 should be empty. "
            f"Got {len(body)} slots."
        )


# ===================================================================
# APPOINTMENT MANAGEMENT
# ===================================================================

class TestCreateAppointment:
    """POST /api/appointments — Book a new appointment."""

    def test_create_appointment_returns_201(self, api, seeded_data):
        resp = api.request("POST", "/api/appointments", json={
            "appointmentId": "test-appt-001",
            "patientId": "patient-001",
            "providerId": "provider-001",
            "dateTime": "2026-06-08T09:00:00",
            "reason": "Test appointment",
        })
        assert resp.status_code == 201, (
            f"POST /api/appointments should return 201, got {resp.status_code}. "
            f"Response: {resp.text[:500]}"
        )

    def test_create_appointment_response_has_required_fields(self, api, seeded_data):
        resp = api.request("POST", "/api/appointments", json={
            "appointmentId": "test-appt-002",
            "patientId": "patient-002",
            "providerId": "provider-001",
            "dateTime": "2026-06-08T10:00:00",
        })
        assert resp.status_code == 201
        body = resp.json()

        required = ["appointmentId", "patientId", "providerId", "dateTime", "status"]
        missing = [f for f in required if f not in body]
        assert not missing, (
            f"Response missing required fields: {missing}. "
            f"Got: {list(body.keys())}. "
            f"See api-contract.yaml create_appointment.response.body.required"
        )

    def test_new_appointment_has_scheduled_status(self, api, seeded_data):
        resp = api.request("POST", "/api/appointments", json={
            "appointmentId": "test-appt-003",
            "patientId": "patient-001",
            "providerId": "provider-003",
            "dateTime": "2026-06-08T11:00:00",
        })
        assert resp.status_code == 201
        body = resp.json()

        assert body.get("status") == "scheduled", (
            f"New appointment status should be 'scheduled', "
            f"got '{body.get('status')}'"
        )

    def test_create_appointment_returns_correct_data(self, api, seeded_data):
        resp = api.request("POST", "/api/appointments", json={
            "appointmentId": "test-appt-004",
            "patientId": "patient-003",
            "providerId": "provider-002",
            "dateTime": "2026-06-09T08:00:00",
            "reason": "Consultation",
        })
        assert resp.status_code == 201
        body = resp.json()

        assert body["appointmentId"] == "test-appt-004"
        assert body["patientId"] == "patient-003"
        assert body["providerId"] == "provider-002"


class TestGetAppointment:
    """GET /api/appointments/{appointmentId} — Get appointment details."""

    def test_get_existing_appointment(self, api, seeded_data):
        resp = api.request("GET", "/api/appointments/appt-001")
        assert resp.status_code == 200, (
            f"GET /api/appointments/appt-001 should return 200, got {resp.status_code}"
        )

    def test_get_appointment_has_required_fields(self, api, seeded_data):
        resp = api.request("GET", "/api/appointments/appt-001")
        assert resp.status_code == 200
        body = resp.json()

        required = ["appointmentId", "patientId", "providerId", "dateTime", "status"]
        missing = [f for f in required if f not in body]
        assert not missing, (
            f"GET appointment response missing required fields: {missing}. "
            f"Got: {list(body.keys())}"
        )

    def test_get_appointment_returns_correct_data(self, api, seeded_data):
        resp = api.request("GET", "/api/appointments/appt-001")
        assert resp.status_code == 200
        body = resp.json()

        assert body["appointmentId"] == "appt-001"
        assert body["patientId"] == "patient-001"
        assert body["providerId"] == "provider-001"
        assert body["status"] == "scheduled"

    def test_get_nonexistent_appointment_returns_404(self, api):
        resp = api.request("GET", "/api/appointments/nonexistent-appt-xyz")
        assert resp.status_code == 404, (
            f"GET /api/appointments/nonexistent-appt-xyz should return 404, "
            f"got {resp.status_code}"
        )


# ===================================================================
# APPOINTMENT STATUS UPDATES
# ===================================================================

class TestUpdateAppointmentStatus:
    """PATCH /api/appointments/{appointmentId}/status — Update appointment status."""

    def test_update_status_returns_200(self, api, seeded_data):
        # Create a disposable appointment for status update
        api.request("POST", "/api/appointments", json={
            "appointmentId": "test-status-001",
            "patientId": "patient-001",
            "providerId": "provider-001",
            "dateTime": "2026-06-15T09:00:00",
        })
        resp = api.request("PATCH", "/api/appointments/test-status-001/status", json={
            "status": "completed",
        })
        assert resp.status_code == 200, (
            f"PATCH status should return 200, got {resp.status_code}. "
            f"Response: {resp.text[:500]}"
        )

    def test_update_status_response_has_required_fields(self, api, seeded_data):
        api.request("POST", "/api/appointments", json={
            "appointmentId": "test-status-002",
            "patientId": "patient-002",
            "providerId": "provider-001",
            "dateTime": "2026-06-15T10:00:00",
        })
        resp = api.request("PATCH", "/api/appointments/test-status-002/status", json={
            "status": "cancelled",
        })
        assert resp.status_code == 200
        body = resp.json()

        required = ["appointmentId", "patientId", "providerId", "dateTime", "status"]
        missing = [f for f in required if f not in body]
        assert not missing, (
            f"Status update response missing required fields: {missing}. "
            f"Got: {list(body.keys())}"
        )

    def test_update_status_reflects_new_status(self, api, seeded_data):
        api.request("POST", "/api/appointments", json={
            "appointmentId": "test-status-003",
            "patientId": "patient-003",
            "providerId": "provider-002",
            "dateTime": "2026-06-16T08:00:00",
        })
        resp = api.request("PATCH", "/api/appointments/test-status-003/status", json={
            "status": "no-show",
        })
        assert resp.status_code == 200
        body = resp.json()
        assert body["status"] == "no-show", (
            f"Status should be 'no-show' after update, got '{body['status']}'"
        )

        # Verify GET also returns the updated status
        get_resp = api.request("GET", "/api/appointments/test-status-003")
        assert get_resp.status_code == 200
        assert get_resp.json()["status"] == "no-show"

    def test_cancelled_appointment_cannot_be_updated(self, api, seeded_data):
        """Cancelled is a terminal state — further updates should return 409."""
        api.request("POST", "/api/appointments", json={
            "appointmentId": "test-status-terminal-001",
            "patientId": "patient-001",
            "providerId": "provider-001",
            "dateTime": "2026-06-22T09:00:00",
        })
        api.request("PATCH", "/api/appointments/test-status-terminal-001/status", json={
            "status": "cancelled",
        })
        resp = api.request("PATCH", "/api/appointments/test-status-terminal-001/status", json={
            "status": "scheduled",
        })
        assert resp.status_code == 409, (
            f"Updating a cancelled appointment should return 409, "
            f"got {resp.status_code}"
        )

    def test_completed_appointment_cannot_be_updated(self, api, seeded_data):
        """Completed is a terminal state — further updates should return 409."""
        api.request("POST", "/api/appointments", json={
            "appointmentId": "test-status-terminal-002",
            "patientId": "patient-002",
            "providerId": "provider-001",
            "dateTime": "2026-06-22T10:00:00",
        })
        api.request("PATCH", "/api/appointments/test-status-terminal-002/status", json={
            "status": "completed",
        })
        resp = api.request("PATCH", "/api/appointments/test-status-terminal-002/status", json={
            "status": "cancelled",
        })
        assert resp.status_code == 409, (
            f"Updating a completed appointment should return 409, "
            f"got {resp.status_code}"
        )

    def test_update_nonexistent_appointment_returns_404(self, api):
        resp = api.request("PATCH", "/api/appointments/nonexistent-xyz/status", json={
            "status": "cancelled",
        })
        assert resp.status_code == 404, (
            f"Updating nonexistent appointment should return 404, "
            f"got {resp.status_code}"
        )


# ===================================================================
# PATIENT APPOINTMENTS (HISTORY / QUERY)
# ===================================================================

class TestGetPatientAppointments:
    """GET /api/patients/{patientId}/appointments — Patient appointment history."""

    def test_patient_appointments_returns_200(self, api, seeded_data):
        resp = api.request("GET", "/api/patients/patient-001/appointments")
        assert resp.status_code == 200, (
            f"GET /api/patients/patient-001/appointments should return 200, "
            f"got {resp.status_code}"
        )

    def test_patient_appointments_returns_array(self, api, seeded_data):
        resp = api.request("GET", "/api/patients/patient-001/appointments")
        assert resp.status_code == 200
        body = resp.json()
        assert isinstance(body, list), (
            f"Patient appointments should return an array, got {type(body).__name__}"
        )

    def test_patient_appointments_entries_have_required_fields(self, api, seeded_data):
        resp = api.request("GET", "/api/patients/patient-001/appointments")
        body = resp.json()
        assert len(body) > 0, "patient-001 has appointments, result should not be empty"

        entry = body[0]
        required = ["appointmentId", "patientId", "providerId", "dateTime", "status"]
        missing = [f for f in required if f not in entry]
        assert not missing, (
            f"Patient appointment entry missing required fields: {missing}. "
            f"Got: {list(entry.keys())}"
        )

    def test_patient_appointments_only_shows_own(self, api, seeded_data):
        """patient-002 has only appt-002. Results should only contain their appointments."""
        resp = api.request("GET", "/api/patients/patient-002/appointments")
        body = resp.json()

        for entry in body:
            assert entry["patientId"] == "patient-002", (
                f"Patient-002's appointment list contains appointment for "
                f"{entry['patientId']}. Must only contain own appointments."
            )

    def test_patient_appointments_filter_by_status(self, api, seeded_data):
        """Filter by status=scheduled should only return scheduled appointments."""
        resp = api.request("GET", "/api/patients/patient-001/appointments?status=scheduled")
        assert resp.status_code == 200
        body = resp.json()

        for entry in body:
            assert entry["status"] == "scheduled", (
                f"Filtered by status=scheduled but got status '{entry['status']}'"
            )

    def test_patient_appointments_filter_by_date_range(self, api, seeded_data):
        """Filter by from/to date range."""
        resp = api.request(
            "GET",
            "/api/patients/patient-001/appointments?from=2026-06-01&to=2026-06-01"
        )
        assert resp.status_code == 200
        body = resp.json()
        assert len(body) >= 1, (
            "patient-001 has appointments on 2026-06-01, filtered results should not be empty"
        )

        for entry in body:
            assert "2026-06-01" in entry["dateTime"], (
                f"Date range filter 2026-06-01 to 2026-06-01 returned appointment "
                f"outside range: {entry['dateTime']}"
            )

    def test_patient_appointments_sorted_by_datetime_desc(self, api, seeded_data):
        """Appointments should be sorted by dateTime descending (most recent first)."""
        resp = api.request("GET", "/api/patients/patient-001/appointments")
        body = resp.json()

        if len(body) >= 2:
            datetimes = [entry["dateTime"] for entry in body]
            assert datetimes == sorted(datetimes, reverse=True), (
                f"Patient appointments should be sorted by dateTime descending. "
                f"Got: {datetimes[:5]}"
            )

    def test_nonexistent_patient_appointments_returns_404(self, api):
        resp = api.request("GET", "/api/patients/nonexistent-xyz/appointments")
        assert resp.status_code == 404, (
            f"Appointments for nonexistent patient should return 404, "
            f"got {resp.status_code}"
        )


# ===================================================================
# PROVIDER APPOINTMENTS
# ===================================================================

class TestGetProviderAppointments:
    """GET /api/providers/{providerId}/appointments?date=YYYY-MM-DD — Provider appointments."""

    def test_provider_appointments_returns_200(self, api, seeded_data):
        resp = api.request("GET", "/api/providers/provider-001/appointments?date=2026-06-01")
        assert resp.status_code == 200, (
            f"GET provider appointments should return 200, got {resp.status_code}"
        )

    def test_provider_appointments_returns_array(self, api, seeded_data):
        resp = api.request("GET", "/api/providers/provider-001/appointments?date=2026-06-01")
        assert resp.status_code == 200
        body = resp.json()
        assert isinstance(body, list), (
            f"Provider appointments should return an array, got {type(body).__name__}"
        )

    def test_provider_appointments_entries_have_required_fields(self, api, seeded_data):
        resp = api.request("GET", "/api/providers/provider-001/appointments?date=2026-06-01")
        body = resp.json()
        assert len(body) > 0, (
            "provider-001 has appointments on 2026-06-01, result should not be empty"
        )

        entry = body[0]
        required = ["appointmentId", "patientId", "providerId", "dateTime", "status"]
        missing = [f for f in required if f not in entry]
        assert not missing, (
            f"Provider appointment entry missing required fields: {missing}. "
            f"Got: {list(entry.keys())}"
        )

    def test_provider_appointments_only_shows_own(self, api, seeded_data):
        """provider-001 has appt-001 and appt-002 on 2026-06-01."""
        resp = api.request("GET", "/api/providers/provider-001/appointments?date=2026-06-01")
        body = resp.json()

        for entry in body:
            assert entry["providerId"] == "provider-001", (
                f"Provider-001's appointment list contains appointment for "
                f"provider {entry['providerId']}. Must only contain own appointments."
            )

    def test_provider_appointments_sorted_by_datetime_asc(self, api, seeded_data):
        """Provider appointments should be sorted by dateTime ascending."""
        resp = api.request("GET", "/api/providers/provider-001/appointments?date=2026-06-01")
        body = resp.json()

        if len(body) >= 2:
            datetimes = [entry["dateTime"] for entry in body]
            assert datetimes == sorted(datetimes), (
                f"Provider appointments should be sorted by dateTime ascending. "
                f"Got: {datetimes[:5]}"
            )

    def test_provider_appointments_empty_on_no_appointments(self, api, seeded_data):
        """provider-001 has no appointments on 2026-07-01, should return empty array."""
        resp = api.request("GET", "/api/providers/provider-001/appointments?date=2026-07-01")
        assert resp.status_code == 200
        body = resp.json()
        assert isinstance(body, list) and len(body) == 0, (
            f"Provider with no appointments on date should return empty array, "
            f"got {len(body)} entries"
        )

    def test_nonexistent_provider_appointments_returns_404(self, api):
        resp = api.request("GET", "/api/providers/nonexistent-xyz/appointments?date=2026-06-01")
        assert resp.status_code == 404, (
            f"Appointments for nonexistent provider should return 404, "
            f"got {resp.status_code}"
        )
