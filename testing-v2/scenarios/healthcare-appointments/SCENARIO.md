# Scenario: Healthcare Appointments

> **Important**: This file defines the fixed requirements for this test scenario. 
> Do NOT modify this file between iterations - the point is to measure improvement 
> with the same requirements.

## Overview

Build a REST API for a healthcare appointment booking system. Patients can register, search for healthcare providers by specialty, view available time slots, and book appointments. Patients can also cancel appointments or view their appointment history. Providers have profiles with specialties and weekly schedules. The system should track appointment status (scheduled, completed, cancelled, no-show) and support querying appointments by date range and status. Each provider belongs to a single clinic location.

## Language Suitability

| Language | Suitability | Notes |
|----------|-------------|-------|
| .NET | ✅ Recommended | Excellent async support, strong healthcare industry adoption |
| Java | ✅ Recommended | Popular for enterprise healthcare systems, good async capabilities |
| Python | ⚠️ Suitable | Good for rapid prototyping, FastAPI provides async support |
| Node.js | ✅ Recommended | Great for high-concurrency appointment booking scenarios |
| Go | ✅ Recommended | Excellent performance for high-throughput read-heavy workloads |
| Rust | 🔬 Experimental | High performance, but SDK is in preview |

## Requirements

### Functional Requirements

1. Patients can register with personal information
2. Healthcare providers can be created with specialty and clinic assignment
3. Providers have weekly schedules defining their available hours
4. Patients can search for providers by medical specialty
5. Patients can view available 30-minute time slots for a provider on a date
6. Patients can book appointments with providers
7. Appointment status can be updated (scheduled → completed, cancelled, or no-show)
8. Cancelled and completed appointments are terminal states (no further changes)
9. Patients can view their appointment history with filtering by status and date range
10. Providers can view their appointments for a given date

### Technical Requirements

- **Language/Framework**: Any supported Cosmos DB SDK language
  - .NET 8 (ASP.NET Core)
  - Java 17+ (Spring Boot 3)
  - Python 3.10+ (FastAPI)
  - Node.js 18+ (Express.js)
  - Go 1.21+ (Gin)
  - Rust (Axum) - experimental
- **Cosmos DB API**: NoSQL
- **Authentication**: Connection string (for simplicity in testing)
- **Deployment Target**: Local development only

### Data Model

The system should handle:
- **Patients**: Patient profiles with personal information
- **Providers**: Healthcare provider profiles with specialty, clinic, and weekly schedule
- **Appointments**: Appointment records with status tracking

Expected volume:
- ~50,000 patients
- ~2,000 providers across 200 clinic locations
- ~10,000 appointments per day
- High read volume on provider search and slot availability
- Most queries are scoped to a single patient or provider

### Expected Operations

- [x] Register a patient
- [x] Create a provider with schedule
- [x] Search providers by specialty
- [x] View available time slots for a provider on a date
- [x] Book an appointment
- [x] Update appointment status (scheduled, completed, cancelled, no-show)
- [x] View patient appointment history (with status and date range filters)
- [x] View provider appointments for a date
- [ ] Bulk operations (not required)
- [ ] Transactions (optional for booking with slot validation)

## API Contract (V2)

This scenario has a **fixed API contract** defined in [`api-contract.yaml`](api-contract.yaml).
Automated tests in the [`tests/`](tests/) directory validate implementations against this contract.

**The agent MUST implement these exact endpoints:**

| Method | Path | Description |
|--------|------|-------------|
| GET | `/health` | Health check (returns 200 when ready) |
| POST | `/api/patients` | Register a patient |
| GET | `/api/patients/{patientId}` | Get patient profile |
| POST | `/api/providers` | Create a provider profile |
| GET | `/api/providers/{providerId}` | Get provider profile |
| GET | `/api/providers?specialty=X` | Search providers by specialty |
| GET | `/api/providers/{providerId}/slots?date=YYYY-MM-DD` | Get available time slots |
| POST | `/api/appointments` | Book an appointment |
| GET | `/api/appointments/{appointmentId}` | Get appointment details |
| PATCH | `/api/appointments/{appointmentId}/status` | Update appointment status |
| GET | `/api/patients/{patientId}/appointments?status=X&from=Y&to=Z` | Patient appointment history |
| GET | `/api/providers/{providerId}/appointments?date=YYYY-MM-DD` | Provider appointments for a date |

**The agent MUST also create `iteration-config.yaml`** in the iteration folder.
See `testing-v2/scenarios/_iteration-config-template.yaml` for the template.

## Prompt to Give Agent

> Copy the appropriate prompt for the language being tested.
> Each prompt includes the API contract requirements that the agent must follow.

### .NET Prompt
```
I need to build a .NET 8 Web API for a healthcare appointment booking system using Azure Cosmos DB (NoSQL API).

Requirements:
1. Patients can register with personal information (name, email, phone, date of birth)
2. Healthcare providers have profiles with specialty, clinic assignment, and weekly schedules
3. Patients can search for providers by medical specialty
4. Patients can view available 30-minute time slots for a provider on a given date
5. Patients can book, cancel, or complete appointments
6. Track appointment status: scheduled, completed, cancelled, no-show
7. Cancelled and completed are terminal states (cannot be changed)
8. Query patient appointment history by status and date range
9. Query provider appointments for a specific date

Expected scale:
- ~50,000 patients
- ~2,000 providers across 200 clinic locations
- ~10,000 appointments per day
- High read volume on provider search and slot availability
- Most queries scoped to a single patient or provider

Please create:
1. The data model optimized for appointment queries
2. The Cosmos DB container configuration with appropriate partition keys
3. A repository layer for data access
4. REST API endpoints for all required operations

Use best practices for Cosmos DB throughout. Consider partition key design for efficient queries scoped to patients and providers.

---
**CRITICAL: API Contract Requirements**
Your API MUST implement these EXACT endpoints with these EXACT paths and field names.
Automated tests will validate conformance — any deviation will cause test failures.

Endpoints:
- GET  /health                                          → Returns 200 when app is ready
- POST /api/patients                                    → Body: {patientId, firstName, lastName, email, phone, dateOfBirth} → 201 with same fields. 409 if duplicate.
- GET  /api/patients/{patientId}                        → 200 with {patientId, firstName, lastName, email, phone, dateOfBirth} or 404
- POST /api/providers                                   → Body: {providerId, name, specialty, clinicId, schedule?} → 201 with {providerId, name, specialty, clinicId, schedule}. 409 if duplicate.
- GET  /api/providers/{providerId}                      → 200 with {providerId, name, specialty, clinicId, schedule} or 404
- GET  /api/providers?specialty=X                       → 200 with array of {providerId, name, specialty, clinicId} filtered by specialty (case-insensitive)
- GET  /api/providers/{providerId}/slots?date=YYYY-MM-DD → 200 with array of {startTime, endTime} for available 30-min slots, sorted by time ascending. 404 if provider not found.
- POST /api/appointments                                → Body: {appointmentId, patientId, providerId, dateTime, reason?} → 201 with {appointmentId, patientId, providerId, dateTime, status, reason}. Status defaults to "scheduled". 409 if duplicate.
- GET  /api/appointments/{appointmentId}                → 200 with {appointmentId, patientId, providerId, dateTime, status, reason} or 404
- PATCH /api/appointments/{appointmentId}/status        → Body: {status} → 200 with updated appointment. 404 if not found. 409 if terminal state (cancelled/completed cannot change).
- GET  /api/patients/{patientId}/appointments?status=X&from=YYYY-MM-DD&to=YYYY-MM-DD → 200 with array of appointments filtered by status and/or date range, sorted by dateTime descending. 404 if patient not found.
- GET  /api/providers/{providerId}/appointments?date=YYYY-MM-DD → 200 with array of appointments for that date, sorted by dateTime ascending. 404 if provider not found.

Field naming: use camelCase (patientId, firstName, lastName, dateOfBirth, providerId, clinicId, appointmentId, dateTime, startTime, endTime).
New appointments must have status="scheduled".
Provider schedule is an object with day names as keys (monday, tuesday, etc.) and {start, end} in HH:MM 24-hour format.
Available slots are 30-minute intervals within the provider's schedule for that day, excluding already-booked times.
Cancelled and completed appointments cannot have their status changed further (return 409).

**You MUST also create a file called `iteration-config.yaml`** in your iteration folder with:
```yaml
language: dotnet
database: healthcare-appointments
port: 5000
health: /health
build: dotnet build
run: dotnet run
```

The Cosmos DB connection uses environment variables:
- COSMOS_ENDPOINT (default: https://localhost:8081)
- COSMOS_KEY (default: the standard emulator key)

Do NOT hardcode connection strings. Read them from environment variables or configuration.
```

### Java Prompt
```
I need to build a Spring Boot 3 REST API for a healthcare appointment booking system using Azure Cosmos DB (NoSQL API).

Requirements:
1. Patients can register with personal information (name, email, phone, date of birth)
2. Healthcare providers have profiles with specialty, clinic assignment, and weekly schedules
3. Patients can search for providers by medical specialty
4. Patients can view available 30-minute time slots for a provider on a given date
5. Patients can book, cancel, or complete appointments
6. Track appointment status: scheduled, completed, cancelled, no-show
7. Cancelled and completed are terminal states (cannot be changed)
8. Query patient appointment history by status and date range
9. Query provider appointments for a specific date

Expected scale:
- ~50,000 patients
- ~2,000 providers across 200 clinic locations
- ~10,000 appointments per day
- High read volume on provider search and slot availability
- Most queries scoped to a single patient or provider

Please create:
1. The data model optimized for appointment queries
2. The Cosmos DB container configuration with appropriate partition keys
3. A repository layer for data access
4. REST API endpoints for all required operations

Use best practices for Cosmos DB throughout. Consider partition key design for efficient queries scoped to patients and providers.

---
**CRITICAL: API Contract Requirements**
Your API MUST implement these EXACT endpoints with these EXACT paths and field names.
Automated tests will validate conformance — any deviation will cause test failures.

Endpoints:
- GET  /health                                          → Returns 200 when app is ready
- POST /api/patients                                    → Body: {patientId, firstName, lastName, email, phone, dateOfBirth} → 201 with same fields. 409 if duplicate.
- GET  /api/patients/{patientId}                        → 200 with {patientId, firstName, lastName, email, phone, dateOfBirth} or 404
- POST /api/providers                                   → Body: {providerId, name, specialty, clinicId, schedule?} → 201 with {providerId, name, specialty, clinicId, schedule}. 409 if duplicate.
- GET  /api/providers/{providerId}                      → 200 with {providerId, name, specialty, clinicId, schedule} or 404
- GET  /api/providers?specialty=X                       → 200 with array of {providerId, name, specialty, clinicId} filtered by specialty (case-insensitive)
- GET  /api/providers/{providerId}/slots?date=YYYY-MM-DD → 200 with array of {startTime, endTime} for available 30-min slots, sorted by time ascending. 404 if provider not found.
- POST /api/appointments                                → Body: {appointmentId, patientId, providerId, dateTime, reason?} → 201 with {appointmentId, patientId, providerId, dateTime, status, reason}. Status defaults to "scheduled". 409 if duplicate.
- GET  /api/appointments/{appointmentId}                → 200 with {appointmentId, patientId, providerId, dateTime, status, reason} or 404
- PATCH /api/appointments/{appointmentId}/status        → Body: {status} → 200 with updated appointment. 404 if not found. 409 if terminal state (cancelled/completed cannot change).
- GET  /api/patients/{patientId}/appointments?status=X&from=YYYY-MM-DD&to=YYYY-MM-DD → 200 with array of appointments filtered by status and/or date range, sorted by dateTime descending. 404 if patient not found.
- GET  /api/providers/{providerId}/appointments?date=YYYY-MM-DD → 200 with array of appointments for that date, sorted by dateTime ascending. 404 if provider not found.

Field naming: use camelCase (patientId, firstName, lastName, dateOfBirth, providerId, clinicId, appointmentId, dateTime, startTime, endTime).
New appointments must have status="scheduled".
Provider schedule is an object with day names as keys (monday, tuesday, etc.) and {start, end} in HH:MM 24-hour format.
Available slots are 30-minute intervals within the provider's schedule for that day, excluding already-booked times.
Cancelled and completed appointments cannot have their status changed further (return 409).

**You MUST also create a file called `iteration-config.yaml`** in your iteration folder with:
```yaml
language: java
database: healthcare-appointments
port: 8080
health: /health
build: mvn package -DskipTests
run: java -jar target/*.jar
```

The Cosmos DB connection uses environment variables:
- COSMOS_ENDPOINT (default: https://localhost:8081)
- COSMOS_KEY (default: the standard emulator key)

Do NOT hardcode connection strings. Read them from environment variables or configuration.
```

### Python Prompt
```
I need to build a FastAPI REST API for a healthcare appointment booking system using Azure Cosmos DB (NoSQL API).

Requirements:
1. Patients can register with personal information (name, email, phone, date of birth)
2. Healthcare providers have profiles with specialty, clinic assignment, and weekly schedules
3. Patients can search for providers by medical specialty
4. Patients can view available 30-minute time slots for a provider on a given date
5. Patients can book, cancel, or complete appointments
6. Track appointment status: scheduled, completed, cancelled, no-show
7. Cancelled and completed are terminal states (cannot be changed)
8. Query patient appointment history by status and date range
9. Query provider appointments for a specific date

Expected scale:
- ~50,000 patients
- ~2,000 providers across 200 clinic locations
- ~10,000 appointments per day
- High read volume on provider search and slot availability
- Most queries scoped to a single patient or provider

Please create:
1. The data model optimized for appointment queries
2. The Cosmos DB container configuration with appropriate partition keys
3. A repository layer for data access
4. REST API endpoints for all required operations

Use best practices for Cosmos DB throughout. Consider partition key design for efficient queries scoped to patients and providers.

---
**CRITICAL: API Contract Requirements**
Your API MUST implement these EXACT endpoints with these EXACT paths and field names.
Automated tests will validate conformance — any deviation will cause test failures.

Endpoints:
- GET  /health                                          → Returns 200 when app is ready
- POST /api/patients                                    → Body: {patientId, firstName, lastName, email, phone, dateOfBirth} → 201 with same fields. 409 if duplicate.
- GET  /api/patients/{patientId}                        → 200 with {patientId, firstName, lastName, email, phone, dateOfBirth} or 404
- POST /api/providers                                   → Body: {providerId, name, specialty, clinicId, schedule?} → 201 with {providerId, name, specialty, clinicId, schedule}. 409 if duplicate.
- GET  /api/providers/{providerId}                      → 200 with {providerId, name, specialty, clinicId, schedule} or 404
- GET  /api/providers?specialty=X                       → 200 with array of {providerId, name, specialty, clinicId} filtered by specialty (case-insensitive)
- GET  /api/providers/{providerId}/slots?date=YYYY-MM-DD → 200 with array of {startTime, endTime} for available 30-min slots, sorted by time ascending. 404 if provider not found.
- POST /api/appointments                                → Body: {appointmentId, patientId, providerId, dateTime, reason?} → 201 with {appointmentId, patientId, providerId, dateTime, status, reason}. Status defaults to "scheduled". 409 if duplicate.
- GET  /api/appointments/{appointmentId}                → 200 with {appointmentId, patientId, providerId, dateTime, status, reason} or 404
- PATCH /api/appointments/{appointmentId}/status        → Body: {status} → 200 with updated appointment. 404 if not found. 409 if terminal state (cancelled/completed cannot change).
- GET  /api/patients/{patientId}/appointments?status=X&from=YYYY-MM-DD&to=YYYY-MM-DD → 200 with array of appointments filtered by status and/or date range, sorted by dateTime descending. 404 if patient not found.
- GET  /api/providers/{providerId}/appointments?date=YYYY-MM-DD → 200 with array of appointments for that date, sorted by dateTime ascending. 404 if provider not found.

Field naming: use camelCase (patientId, firstName, lastName, dateOfBirth, providerId, clinicId, appointmentId, dateTime, startTime, endTime).
New appointments must have status="scheduled".
Provider schedule is an object with day names as keys (monday, tuesday, etc.) and {start, end} in HH:MM 24-hour format.
Available slots are 30-minute intervals within the provider's schedule for that day, excluding already-booked times.
Cancelled and completed appointments cannot have their status changed further (return 409).

**You MUST also create a file called `iteration-config.yaml`** in your iteration folder with:
```yaml
language: python
database: healthcare-appointments
port: 8000
health: /health
build: pip install -r requirements.txt
run: uvicorn main:app --host 0.0.0.0 --port 8000
```

The Cosmos DB connection uses environment variables:
- COSMOS_ENDPOINT (default: https://localhost:8081)
- COSMOS_KEY (default: the standard emulator key)

Do NOT hardcode connection strings. Read them from environment variables or configuration.
```

### Node.js Prompt
```
I need to build an Express.js REST API for a healthcare appointment booking system using Azure Cosmos DB (NoSQL API).

Requirements:
1. Patients can register with personal information (name, email, phone, date of birth)
2. Healthcare providers have profiles with specialty, clinic assignment, and weekly schedules
3. Patients can search for providers by medical specialty
4. Patients can view available 30-minute time slots for a provider on a given date
5. Patients can book, cancel, or complete appointments
6. Track appointment status: scheduled, completed, cancelled, no-show
7. Cancelled and completed are terminal states (cannot be changed)
8. Query patient appointment history by status and date range
9. Query provider appointments for a specific date

Expected scale:
- ~50,000 patients
- ~2,000 providers across 200 clinic locations
- ~10,000 appointments per day
- High read volume on provider search and slot availability
- Most queries scoped to a single patient or provider

Please create:
1. The data model optimized for appointment queries
2. The Cosmos DB container configuration with appropriate partition keys
3. A repository layer for data access
4. REST API routes for all required operations

Use best practices for Cosmos DB throughout. Consider partition key design for efficient queries scoped to patients and providers.

---
**CRITICAL: API Contract Requirements**
Your API MUST implement these EXACT endpoints with these EXACT paths and field names.
Automated tests will validate conformance — any deviation will cause test failures.

Endpoints:
- GET  /health                                          → Returns 200 when app is ready
- POST /api/patients                                    → Body: {patientId, firstName, lastName, email, phone, dateOfBirth} → 201 with same fields. 409 if duplicate.
- GET  /api/patients/{patientId}                        → 200 with {patientId, firstName, lastName, email, phone, dateOfBirth} or 404
- POST /api/providers                                   → Body: {providerId, name, specialty, clinicId, schedule?} → 201 with {providerId, name, specialty, clinicId, schedule}. 409 if duplicate.
- GET  /api/providers/{providerId}                      → 200 with {providerId, name, specialty, clinicId, schedule} or 404
- GET  /api/providers?specialty=X                       → 200 with array of {providerId, name, specialty, clinicId} filtered by specialty (case-insensitive)
- GET  /api/providers/{providerId}/slots?date=YYYY-MM-DD → 200 with array of {startTime, endTime} for available 30-min slots, sorted by time ascending. 404 if provider not found.
- POST /api/appointments                                → Body: {appointmentId, patientId, providerId, dateTime, reason?} → 201 with {appointmentId, patientId, providerId, dateTime, status, reason}. Status defaults to "scheduled". 409 if duplicate.
- GET  /api/appointments/{appointmentId}                → 200 with {appointmentId, patientId, providerId, dateTime, status, reason} or 404
- PATCH /api/appointments/{appointmentId}/status        → Body: {status} → 200 with updated appointment. 404 if not found. 409 if terminal state (cancelled/completed cannot change).
- GET  /api/patients/{patientId}/appointments?status=X&from=YYYY-MM-DD&to=YYYY-MM-DD → 200 with array of appointments filtered by status and/or date range, sorted by dateTime descending. 404 if patient not found.
- GET  /api/providers/{providerId}/appointments?date=YYYY-MM-DD → 200 with array of appointments for that date, sorted by dateTime ascending. 404 if provider not found.

Field naming: use camelCase (patientId, firstName, lastName, dateOfBirth, providerId, clinicId, appointmentId, dateTime, startTime, endTime).
New appointments must have status="scheduled".
Provider schedule is an object with day names as keys (monday, tuesday, etc.) and {start, end} in HH:MM 24-hour format.
Available slots are 30-minute intervals within the provider's schedule for that day, excluding already-booked times.
Cancelled and completed appointments cannot have their status changed further (return 409).

**You MUST also create a file called `iteration-config.yaml`** in your iteration folder with:
```yaml
language: nodejs
database: healthcare-appointments
port: 3000
health: /health
build: npm install
run: node server.js
```

The Cosmos DB connection uses environment variables:
- COSMOS_ENDPOINT (default: https://localhost:8081)
- COSMOS_KEY (default: the standard emulator key)

Do NOT hardcode connection strings. Read them from environment variables or configuration.
```

### Go Prompt
```
I need to build a Go REST API (using Gin) for a healthcare appointment booking system using Azure Cosmos DB (NoSQL API).

Requirements:
1. Patients can register with personal information (name, email, phone, date of birth)
2. Healthcare providers have profiles with specialty, clinic assignment, and weekly schedules
3. Patients can search for providers by medical specialty
4. Patients can view available 30-minute time slots for a provider on a given date
5. Patients can book, cancel, or complete appointments
6. Track appointment status: scheduled, completed, cancelled, no-show
7. Cancelled and completed are terminal states (cannot be changed)
8. Query patient appointment history by status and date range
9. Query provider appointments for a specific date

Expected scale:
- ~50,000 patients
- ~2,000 providers across 200 clinic locations
- ~10,000 appointments per day
- High read volume on provider search and slot availability
- Most queries scoped to a single patient or provider

Please create:
1. The data model optimized for appointment queries
2. The Cosmos DB container configuration with appropriate partition keys
3. A repository layer for data access
4. REST API handlers for all required operations

Use best practices for Cosmos DB throughout. Consider partition key design for efficient queries scoped to patients and providers.

---
**CRITICAL: API Contract Requirements**
Your API MUST implement these EXACT endpoints with these EXACT paths and field names.
Automated tests will validate conformance — any deviation will cause test failures.

Endpoints:
- GET  /health                                          → Returns 200 when app is ready
- POST /api/patients                                    → Body: {patientId, firstName, lastName, email, phone, dateOfBirth} → 201 with same fields. 409 if duplicate.
- GET  /api/patients/{patientId}                        → 200 with {patientId, firstName, lastName, email, phone, dateOfBirth} or 404
- POST /api/providers                                   → Body: {providerId, name, specialty, clinicId, schedule?} → 201 with {providerId, name, specialty, clinicId, schedule}. 409 if duplicate.
- GET  /api/providers/{providerId}                      → 200 with {providerId, name, specialty, clinicId, schedule} or 404
- GET  /api/providers?specialty=X                       → 200 with array of {providerId, name, specialty, clinicId} filtered by specialty (case-insensitive)
- GET  /api/providers/{providerId}/slots?date=YYYY-MM-DD → 200 with array of {startTime, endTime} for available 30-min slots, sorted by time ascending. 404 if provider not found.
- POST /api/appointments                                → Body: {appointmentId, patientId, providerId, dateTime, reason?} → 201 with {appointmentId, patientId, providerId, dateTime, status, reason}. Status defaults to "scheduled". 409 if duplicate.
- GET  /api/appointments/{appointmentId}                → 200 with {appointmentId, patientId, providerId, dateTime, status, reason} or 404
- PATCH /api/appointments/{appointmentId}/status        → Body: {status} → 200 with updated appointment. 404 if not found. 409 if terminal state (cancelled/completed cannot change).
- GET  /api/patients/{patientId}/appointments?status=X&from=YYYY-MM-DD&to=YYYY-MM-DD → 200 with array of appointments filtered by status and/or date range, sorted by dateTime descending. 404 if patient not found.
- GET  /api/providers/{providerId}/appointments?date=YYYY-MM-DD → 200 with array of appointments for that date, sorted by dateTime ascending. 404 if provider not found.

Field naming: use camelCase (patientId, firstName, lastName, dateOfBirth, providerId, clinicId, appointmentId, dateTime, startTime, endTime).
New appointments must have status="scheduled".
Provider schedule is an object with day names as keys (monday, tuesday, etc.) and {start, end} in HH:MM 24-hour format.
Available slots are 30-minute intervals within the provider's schedule for that day, excluding already-booked times.
Cancelled and completed appointments cannot have their status changed further (return 409).

**You MUST also create a file called `iteration-config.yaml`** in your iteration folder with:
```yaml
language: go
database: healthcare-appointments
port: 8080
health: /health
build: go build -o server .
run: ./server
```

The Cosmos DB connection uses environment variables:
- COSMOS_ENDPOINT (default: https://localhost:8081)
- COSMOS_KEY (default: the standard emulator key)

Do NOT hardcode connection strings. Read them from environment variables or configuration.
```

## Success Criteria

What does "done" look like for this scenario?

- [ ] API compiles and runs (`iteration-config.yaml` build/run commands succeed)
- [ ] All API contract tests pass (`test_api_contract.py`)
- [ ] All data integrity tests pass (`test_data_integrity.py`)
- [ ] Provider search by specialty is efficient (consider partition key or indexing)
- [ ] Slot availability queries are efficient (avoid scanning all appointments)
- [ ] Patient appointment history queries don't create cross-partition scans
- [ ] Appointment status transitions enforce business rules (terminal states)
- [ ] Date range filtering is handled efficiently with proper indexing

## Notes

- This scenario tests read-heavy patterns with multiple access paths (by patient, by provider, by specialty)
- Common mistakes: using a single container with a poor partition key that causes cross-partition queries for every access pattern
- Tests understanding of composite indexes for ORDER BY with filtering
- Status field as a string enum tests proper serialization practices
- Provider schedule + slot availability tests domain-specific logic alongside Cosmos DB patterns
- Date range queries test proper use of range indexes and ISO-8601 date formatting
