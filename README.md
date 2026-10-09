# AutoBook

CMPE 172 Term Project
Automotive Service Appointment System

## Overview

AutoBook is a Spring Boot web application for booking automotive service appointments. Customers browse open
appointment slots from repair shops, book a slot, and manage or cancel their appointments. Providers (repair shops)
manage their availability and see who booked with them. Admins manage provider information and review all activity.

Milestone 1 delivered the database design and a layered JDBC skeleton. Milestone 2 adds authentication, role-based
access control, the booking workflow with transactional double-booking protection, cancellation and history, provider
availability management, a Thymeleaf/Bootstrap website, and an integration test suite.

## Milestone 2 Features

- **Authentication:** email/password login backed by the `users` table, BCrypt password hashes, session-based
  login/logout, CSRF protection on every state-changing request.
- **Role-based access control:** `CUSTOMER`, `PROVIDER`, and `ADMIN` areas. API requests get JSON `401`/`403`
  responses; browser pages redirect to the login page or show an access-denied page.
- **Ownership checks:** the current user is always resolved from the security session. Customers can only see or
  cancel their own appointments; providers can only change their own slots.
- **Slot search:** filter open future slots by provider, service, and date, sorted by start time, paginated with SQL
  `LIMIT`/`OFFSET`.
- **Booking:** review → confirm → confirmation page. Booking runs in one transaction with a row lock, a conditional
  update, and a database uniqueness constraint (see [Concurrency strategy](#concurrency-strategy)).
- **Cancellation and history:** customers cancel their own future bookings; the slot is reopened, and the cancelled
  appointment is kept as history. Upcoming vs. past/cancelled/completed appointments are listed separately.
- **Provider availability:** add slots (validated time range, future start, valid service, minimum service length,
  no overlaps), remove open slots, view bookings, mark past appointments completed.
- **Admin:** dashboard of providers, users, and all appointments; edit provider contact details.
- **Frontend:** Thymeleaf + Bootstrap 5 pages for every workflow, responsive down to phone width.
- **Error handling:** centralized JSON errors for the API (`400`, `401`, `403`, `404`, `409`, `500`) and friendly error
  pages for the website. SQL and stack traces are never shown to users.

## Technologies

- Java 17, Spring Boot 4.1.1, Maven Wrapper
- Spring Web MVC, Spring Security, Spring JDBC (`JdbcTemplate`), Bean Validation
- H2 in-memory database
- Thymeleaf, Bootstrap 5.3 (bundled as a WebJar, so the site works offline)
- JUnit 5, Spring Boot Test, MockMvc, Spring Security Test

**JDBC only.** No JPA, Hibernate, or other ORM is used. All SQL is in the repository classes.

## Requirements

- JDK 17 or newer (`java -version`)
- No separate database install; H2 runs in memory. Maven is provided by the wrapper (`./mvnw`).

## How to Run

```bash
cd "/Users/JadaNguyen/Documents/CMPE 172 -AUTOBOOK"
./mvnw spring-boot:run
```

Open <http://localhost:8080>. The database is created from `schema.sql` and `seed.sql` on every start, so restarting
the application resets all data. Seed slot times are relative to the current date, so there are always upcoming slots.

If port 8080 is in use, check what is using it with `lsof -nP -iTCP:8080 -sTCP:LISTEN`, or start AutoBook on another
port: `./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=8081`.

## How to Test

```bash
./mvnw clean test
```

The suite has 85 integration tests in 10 test classes. All of them pass. See [Automated tests](#automated-tests).

## Test Login Credentials

All demo accounts use the development-only password `password123`. Never use it for a real deployment.

| Role     | Email                                    | Notes                                        |
|----------|------------------------------------------|----------------------------------------------|
| Customer | `jada.nguyen@example.com`                | Has upcoming, completed, and cancelled bookings |
| Customer | `alex.rivera@example.com`                | Has one upcoming booking                     |
| Provider | `service@downtownautocare.example.com`   | Downtown Auto Care                           |
| Provider | `hello@westsidetire.example.com`         | Westside Tire and Inspection                 |
| Admin    | `morgan.lee@example.com`                 |                                              |

## Customer Workflow

1. Open the home page and click **VIEW AVAILABLE SLOTS** (or go to `/browse`).
2. Filter by repair shop, service, and date; page through results.
3. Click **Book this slot**. You are asked to sign in if you are not signed in yet.
4. Review the details and click **Confirm booking**.
5. The confirmation page shows a confirmation number (for example `AB-00005`).
6. **My appointments** lists upcoming appointments and history. Click **Cancel**, then confirm, to cancel.

If another customer books the slot first, you see "Sorry, this appointment slot has already been booked." and are
returned to the slot list.

## Provider Workflow

1. Sign in with a provider account. You land on the provider dashboard (open, booked, completed counts and upcoming
   bookings).
2. **Availability**: add a slot (service, date, start, end; the end time is suggested from the service length) or
   remove an open slot. Booked slots cannot be removed. A slot that has cancelled-appointment history is closed
   instead of deleted so the history stays intact.
3. **Bookings**: all appointments booked with you; past booked appointments can be marked completed.

## Admin Functionality

Sign in as the admin to see all providers, user accounts, and appointments, and to edit provider names and phone
numbers. Admins cannot book appointments or change a provider's schedule directly.

## Database Schema

Five tables (see `src/main/resources/schema.sql` and `docs/diagrams/`):

- `users(user_id, first_name, last_name, email UNIQUE, password, role)`, with role in `CUSTOMER`/`PROVIDER`/`ADMIN`
- `providers(provider_id, user_id UNIQUE → users, name, email UNIQUE, phone)`. **New in M2:** `user_id` links a
  provider business to its login account.
- `services(service_id, name UNIQUE, description, duration_minutes)`
- `availability_slots(slot_id, provider_id, service_id, start_time, end_time, is_available)`, with `CHECK(end_time > start_time)`
  and `UNIQUE(provider_id, start_time, end_time)`
- `appointments(appointment_id, user_id, provider_id, service_id, slot_id, status, created_at, active_slot_id)`

**Changed in M2:** Milestone 1 used `UNIQUE(slot_id)` on `appointments`, which made it impossible to rebook a slot
after a cancellation without deleting history. It is replaced by a generated column plus a unique constraint:

```sql
active_slot_id BIGINT GENERATED ALWAYS AS (
    CASE WHEN status IN ('BOOKED', 'COMPLETED') THEN slot_id END
),
CONSTRAINT uq_appointments_active_slot UNIQUE (active_slot_id)
```

Cancelled rows have `active_slot_id = NULL` (a unique constraint allows many `NULL`s), so history is preserved, while
the database still rejects two active appointments for the same slot.

## Booking Transaction Logic

`BookingService.book()` runs in one `@Transactional(isolation = READ_COMMITTED)` transaction:

1. `SELECT ... FROM availability_slots WHERE slot_id = ? FOR UPDATE`: lock the slot row (404 if missing).
2. Validate: service matches the slot (400), slot starts in the future (400), slot is still available (409).
3. `UPDATE availability_slots SET is_available = FALSE WHERE slot_id = ? AND is_available = TRUE AND start_time > ?`:
   must change exactly one row, otherwise 409.
4. `INSERT INTO appointments (...) VALUES (..., 'BOOKED', ...)`. A duplicate active booking would violate
   `uq_appointments_active_slot`; the service turns that into 409.
5. Commit. Any exception rolls back every step, so the slot flag and the appointment table always agree.

Cancellation (`AppointmentService.cancel()`) locks the slot row and then the appointment row, in the same order as
booking so the two cannot deadlock. It sets the status to `CANCELLED` with a conditional update and reopens the slot,
all in one transaction.

## Concurrency Strategy

Double booking is prevented by three layers:

1. **Pessimistic row lock** (`SELECT ... FOR UPDATE`): concurrent bookings of the same slot are serialized. The
   second transaction waits (up to H2's `LOCK_TIMEOUT=10000` ms) until the first commits, then sees
   `is_available = FALSE` and gets a 409.
2. **Atomic conditional update**: `UPDATE ... WHERE is_available = TRUE` succeeds for exactly one transaction.
3. **Database uniqueness constraint** on `active_slot_id`: a final guarantee even if application logic were bypassed.

No `synchronized` blocks are used. The protection lives in the database, so it also holds across several application
instances. `ConcurrentBookingIntegrationTests` proves it:

- Two customers, each logged in with a real HTTP session, `POST /api/appointments` for the same slot at the same
  moment → exactly one `201`, exactly one `409`, one active appointment, slot marked unavailable.
- Twelve threads book the same slot through the service → exactly one success, eleven conflicts.
- A cancellation and a rebooking of the same slot race → the final state is always consistent.

## API Endpoints

| Method | Path | Access | Purpose |
|---|---|---|---|
| GET | `/` | Public | M1 JSON summary (browsers requesting HTML get the home page) |
| GET | `/slots` | Public | M1 list of open future slots |
| GET | `/api/slots?providerId&serviceId&date&page&size` | Public | Filtered, paginated slot search |
| GET | `/api/slots/{id}` | Public | Slot details |
| GET | `/api/providers`, `/api/services` | Public | Filter options |
| GET | `/api/csrf` | Public | CSRF token for API clients |
| GET | `/api/me` | Signed in | Current user and roles |
| POST | `/api/appointments` `{slotId, serviceId?}` | Customer | Book (201 / 400 / 404 / 409) |
| GET | `/api/appointments/me` | Customer | `{upcoming, history}` |
| GET | `/api/appointments/{id}` | Customer (owner) | One appointment (403 if not owner) |
| POST | `/api/appointments/{id}/cancel` | Customer (owner) | Cancel |
| GET | `/api/provider/profile` | Provider | Own provider record |
| GET / POST | `/api/provider/slots` | Provider | List / create own slots |
| DELETE | `/api/provider/slots/{id}` | Provider (owner) | Remove an open slot |
| GET | `/api/provider/appointments` | Provider | Bookings with this provider |
| POST | `/api/provider/appointments/{id}/complete` | Provider (owner) | Mark a past booking completed |
| GET | `/api/admin/providers`, `/api/admin/users`, `/api/admin/appointments` | Admin | Review data |
| PUT | `/api/admin/providers/{id}` `{name, phone}` | Admin | Edit provider contact info |

Example API session with curl (CSRF is required for `POST`/`PUT`/`DELETE`):

```bash
B=http://localhost:8080
T=$(curl -s -c c.txt -b c.txt $B/api/csrf | python3 -c "import sys,json;print(json.load(sys.stdin)['token'])")
curl -s -c c.txt -b c.txt -d "username=jada.nguyen@example.com&password=password123&_csrf=$T" $B/login -o /dev/null
T=$(curl -s -c c.txt -b c.txt $B/api/csrf | python3 -c "import sys,json;print(json.load(sys.stdin)['token'])")
curl -s -c c.txt -b c.txt -H "X-CSRF-TOKEN: $T" -H "Content-Type: application/json" -d '{"slotId":2}' $B/api/appointments
```

## Website Pages

Public: `/` home, `/login`, `/browse`. Customer: `/customer/dashboard`, `/customer/book/{slotId}`,
`/customer/appointments/{id}/confirmation`, `/customer/appointments`, `/customer/appointments/{id}/cancel`.
Provider: `/provider/dashboard`, `/provider/availability`, `/provider/appointments`. Admin: `/admin/dashboard`,
`/admin/providers/{id}/edit`.

Screenshots of every page, taken from the running application, are in `docs/screenshots/`.

## Automated Tests

| Test class | Tests | Covers |
|---|---|---|
| `AutobookApplicationTests` | 5 | M1 context, seed data, `/`, `/slots`, DB duplicate rejection |
| `SecurityIntegrationTests` | 17 | Login per role, bad password, 401/403, role isolation, CSRF, logout |
| `SlotSearchIntegrationTests` | 7 | Filters, pagination, past/booked exclusion, validation |
| `BookingIntegrationTests` | 11 | Success, 404/409/400 cases, duplicate, impersonation, CSRF |
| `ConcurrentBookingIntegrationTests` | 3 | Two-customer HTTP race, 12-thread race, cancel vs. rebook |
| `CancellationIntegrationTests` | 9 | History, ownership, status rules, rebooking, DB constraint |
| `ProviderAvailabilityIntegrationTests` | 12 | Create, validation, overlap, removal, ownership, completion |
| `CustomerWebPageIntegrationTests` | 10 | Website booking/cancel flow, friendly errors, access control |
| `ProviderWebPageIntegrationTests` | 6 | Provider pages and form validation |
| `AdminIntegrationTests` | 5 | Admin pages and API, provider editing, access control |

## Project Structure

```text
src/main/java/com/autobook/
  config/            SecurityConfig, TimeConfig
  controller/        JSON controllers (Home, Slot, SlotApi, AppointmentApi, ProviderApi, AdminApi, Auth)
  controller/web/    Thymeleaf page controllers and web error handling
  dto/               Request/response records
  exception/         Domain exceptions and ApiExceptionHandler
  model/             AppUser, AppointmentStatus, UserRole, SlotLock
  repository/        JDBC repositories (all SQL lives here)
  service/           Business logic and transactions
src/main/resources/
  schema.sql, seed.sql, application.properties
  templates/         Thymeleaf pages
  static/css, static/js
docs/
  AutoBook_Milestone_2_Report.pdf (+ .html source), Milestone_2_Code_Walkthrough_Script.md, screenshots/
```

## H2 Console

The H2 console is enabled at `/h2-console` (JDBC URL `jdbc:h2:mem:autobook`, user `sa`, empty password), but Spring
Security requires a login and blocks its frames, so it is effectively unavailable while security is on. Use the API
or the admin dashboard to inspect data.

## Known Limitations

- The database is in memory, so all bookings are lost on restart (by design for the course demo).
- There are no automatic retries: a customer who loses a booking race gets a clear 409 and picks another slot. If a
  lock cannot be acquired within 10 seconds, the request also fails with 409 and can be retried.
- A slot with cancelled-appointment history is closed rather than deleted, so a provider cannot re-add a slot with
  exactly the same start and end time (`UNIQUE(provider_id, start_time, end_time)`).
- Times are local server time; there is no time-zone handling.
- No self-service registration, password reset, email notifications, or AI/RAG features (planned for later milestones).
