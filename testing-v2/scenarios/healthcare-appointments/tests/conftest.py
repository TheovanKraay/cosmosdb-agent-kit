"""
Scenario-level conftest for healthcare-appointments tests.

Imports shared harness fixtures and adds scenario-specific helpers.
"""

import sys
from pathlib import Path

# Add harness to path so shared fixtures are importable
harness_dir = Path(__file__).resolve().parent.parent.parent.parent / "harness"
sys.path.insert(0, str(harness_dir))

from conftest_base import *  # noqa: F401,F403 — re-export all shared fixtures

import pytest


# ---------------------------------------------------------------------------
# Scenario-specific fixtures
# ---------------------------------------------------------------------------

@pytest.fixture(scope="session")
def test_patients():
    """Standard set of test patients used across tests."""
    return [
        {
            "patientId": "patient-001",
            "firstName": "Alice",
            "lastName": "Smith",
            "email": "alice.smith@example.com",
            "phone": "555-0101",
            "dateOfBirth": "1985-03-15",
        },
        {
            "patientId": "patient-002",
            "firstName": "Bob",
            "lastName": "Johnson",
            "email": "bob.johnson@example.com",
            "phone": "555-0102",
            "dateOfBirth": "1990-07-22",
        },
        {
            "patientId": "patient-003",
            "firstName": "Charlie",
            "lastName": "Williams",
            "email": "charlie.williams@example.com",
            "phone": "555-0103",
            "dateOfBirth": "1978-11-05",
        },
        {
            "patientId": "patient-004",
            "firstName": "Diana",
            "lastName": "Brown",
            "email": "diana.brown@example.com",
            "phone": "555-0104",
            "dateOfBirth": "2000-01-30",
        },
    ]


@pytest.fixture(scope="session")
def test_providers():
    """
    Standard set of test providers used across tests.
    Two cardiologists and one dermatologist for search testing.
    Provider-001 and provider-002 are at clinic-A, provider-003 at clinic-B.
    """
    return [
        {
            "providerId": "provider-001",
            "name": "Dr. Emily Carter",
            "specialty": "cardiology",
            "clinicId": "clinic-A",
            "schedule": {
                "monday": {"start": "09:00", "end": "17:00"},
                "wednesday": {"start": "09:00", "end": "17:00"},
                "friday": {"start": "09:00", "end": "13:00"},
            },
        },
        {
            "providerId": "provider-002",
            "name": "Dr. Frank Miller",
            "specialty": "cardiology",
            "clinicId": "clinic-A",
            "schedule": {
                "tuesday": {"start": "08:00", "end": "16:00"},
                "thursday": {"start": "08:00", "end": "16:00"},
            },
        },
        {
            "providerId": "provider-003",
            "name": "Dr. Grace Lee",
            "specialty": "dermatology",
            "clinicId": "clinic-B",
            "schedule": {
                "monday": {"start": "10:00", "end": "18:00"},
                "tuesday": {"start": "10:00", "end": "18:00"},
                "wednesday": {"start": "10:00", "end": "18:00"},
                "thursday": {"start": "10:00", "end": "18:00"},
                "friday": {"start": "10:00", "end": "14:00"},
            },
        },
    ]


@pytest.fixture(scope="session")
def test_appointments():
    """
    Standard set of test appointments used across tests.
    Designed for deterministic filtering and status transition tests.

    Appointment dates use a Monday (2026-06-01) so provider-001's monday
    schedule (09:00-17:00) applies.

    Status distribution:
      - appt-001: scheduled (patient-001 with provider-001)
      - appt-002: scheduled (patient-002 with provider-001)
      - appt-003: scheduled (patient-001 with provider-003, different provider)
      - appt-004: scheduled (patient-003 with provider-002, tuesday 2026-06-02)
    """
    return [
        {
            "appointmentId": "appt-001",
            "patientId": "patient-001",
            "providerId": "provider-001",
            "dateTime": "2026-06-01T09:00:00",
            "reason": "Annual checkup",
        },
        {
            "appointmentId": "appt-002",
            "patientId": "patient-002",
            "providerId": "provider-001",
            "dateTime": "2026-06-01T10:00:00",
            "reason": "Follow-up visit",
        },
        {
            "appointmentId": "appt-003",
            "patientId": "patient-001",
            "providerId": "provider-003",
            "dateTime": "2026-06-01T11:00:00",
            "reason": "Skin consultation",
        },
        {
            "appointmentId": "appt-004",
            "patientId": "patient-003",
            "providerId": "provider-002",
            "dateTime": "2026-06-02T09:00:00",
            "reason": "Heart palpitations",
        },
    ]


@pytest.fixture(scope="session")
def seeded_data(api, test_patients, test_providers, test_appointments):
    """
    Create all test patients, providers, and appointments via the API.
    Returns a dict with the created data for reference.
    Called once per session before any tests that need data.
    """
    created_patients = []
    for patient in test_patients:
        resp = api.request("POST", "/api/patients", json=patient)
        assert resp.status_code == 201, (
            f"Failed to create patient {patient['patientId']}: "
            f"{resp.status_code} {resp.text}"
        )
        created_patients.append(resp.json())

    created_providers = []
    for provider in test_providers:
        resp = api.request("POST", "/api/providers", json=provider)
        assert resp.status_code == 201, (
            f"Failed to create provider {provider['providerId']}: "
            f"{resp.status_code} {resp.text}"
        )
        created_providers.append(resp.json())

    created_appointments = []
    for appt in test_appointments:
        resp = api.request("POST", "/api/appointments", json=appt)
        assert resp.status_code == 201, (
            f"Failed to create appointment {appt['appointmentId']}: "
            f"{resp.status_code} {resp.text}"
        )
        created_appointments.append(resp.json())

    return {
        "patients": created_patients,
        "providers": created_providers,
        "appointments": created_appointments,
    }
