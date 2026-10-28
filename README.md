# AutoBook

CMPE 172 Term Project
Automotive Service Appointment System

## Overview

AutoBook is a Spring Boot web application for booking automotive service appointments. Customers browse open
appointment slots from repair shops, book a slot, and manage or cancel their appointments. Providers (repair shops)
manage their availability and see who booked with them. Admins manage provider information and review all activity.

Milestone 1 delivered the database design and a layered JDBC skeleton. Milestone 2 added authentication, role-based
access control, the booking workflow with transactional double-booking protection, cancellation and history, provider
availability management, a Thymeleaf/Bootstrap website, and an integration test suite. Milestone 3 moves AutoBook to
Java 21 and a persistent PostgreSQL database, adds a distribution boundary to an external notification service
(commit-then-notify with timeouts, retries, idempotency, and fallback), health checks and metrics, Docker, and
GitHub Actions CI.

## Milestone 3 Features

- **Java 21** for the build, the Docker image, and CI.
- **Persistent PostgreSQL** with **Flyway** migrations. The schema is created once and kept; bookings survive
  application and container restarts (Docker named volume `autobook-db`).
- **Notification distribution boundary:** booking and cancellation confirmations are sent to a mock external
  notification service through `NotificationGateway`, using one coarse-grained `NotificationRequest` per message.
- **Commit-then-notify** with a **transactional outbox**, plus **timeouts, bounded retries with backoff,
  idempotency keys, and fallback**. A notification failure can never undo a committed booking.
- **Observability:** INFO/WARN/ERROR logging, public `GET /health` with a database check, and booking and
  notification metrics at `/metrics` (admin only).
- **Docker and CI:** `Dockerfile`, `docker-compose.yml`, `.env.example`, and a GitHub Actions workflow that runs the
  full test suite and a Docker Compose smoke test on every push.

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

- Java 21, Spring Boot 4.1.1, Maven Wrapper
- Spring Web MVC, Spring Security, Spring JDBC (`JdbcTemplate`), Bean Validation
- PostgreSQL 16, Flyway database migrations
- Spring Boot Actuator and Micrometer (health and metrics)
- Thymeleaf, Bootstrap 5.3 (bundled as a WebJar, so the site works offline)
- Docker, Docker Compose, GitHub Actions
- JUnit 5, Spring Boot Test, MockMvc, Spring Security Test, Testcontainers (PostgreSQL)

**JDBC only.** No JPA, Hibernate, or other ORM is used. All SQL is in the repository classes and the Flyway scripts.

## Requirements

- **Docker Desktop** (or Docker Engine with Compose v2). Required to run the app with Docker Compose and to run the
  tests, which start a throwaway PostgreSQL container.
- **JDK 21** (`java -version` must report 21) to build or run AutoBook outside Docker. Maven is provided by the
  wrapper (`./mvnw`, or `mvnw.cmd` on Windows).
- Git.

## How to Run

All commands run from the folder where you cloned the repository.

```bash
git clone https://github.com/yadaltmn/CMPE172-AutoBook.git
cd CMPE172-AutoBook
```

### Option A: Docker Compose (recommended)

```bash
cp .env.example .env        # then edit .env and set AUTOBOOK_DB_PASSWORD
docker compose up --build   # add -d to run in the background
```

Open <http://localhost:8080>. Compose starts PostgreSQL 16 and AutoBook. On the first start Flyway creates the schema
and the demo data; on every later start the existing data is kept.

| Task | Command |
|---|---|
| Stop, keep data | `docker compose down` (or `docker compose stop`) |
| Restart the app only | `docker compose restart app` |
| View logs | `docker compose logs -f app` |
| Delete all data and start fresh | `docker compose down -v` |
| End-to-end check (book, restart, verify) | `./scripts/compose-smoke-test.sh` |

To use another port, set `AUTOBOOK_PORT` in `.env`.

### Option B: Maven with your own PostgreSQL

Create an empty PostgreSQL database and user, then export the connection settings (names as in `.env.example`):

```bash
export AUTOBOOK_DB_URL=jdbc:postgresql://localhost:5432/autobook
export AUTOBOOK_DB_USER=autobook
export AUTOBOOK_DB_PASSWORD=your-password
./mvnw spring-boot:run
```

Seed slot times are relative to the day the database is first created, so the demo has upcoming slots on that day.
After a few days, providers add new slots from the Availability page, or run `docker compose down -v` to recreate the
demo data.

## How to Test

Docker must be running; the tests start PostgreSQL 16 containers with Testcontainers.

```bash
./mvnw clean verify
```

The suite has 114 tests in 15 test classes (see [Automated tests](#automated-tests)); all of them pass in GitHub
Actions on Java 21 and PostgreSQL 16. CI runs the same command plus the Docker Compose smoke test on every push
(`.github/workflows/ci.yml`).

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

Five booking tables plus the notification outbox (see `src/main/resources/db/migration/` and `docs/diagrams/`):

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

**Changed in M3:** the schema lives in Flyway migrations instead of `schema.sql`/`seed.sql`:

| Migration | Contents |
|---|---|
| `V1__create_booking_schema.sql` | The five tables above, unchanged except for PostgreSQL syntax (`GENERATED ALWAYS AS (...) STORED`). No `DROP TABLE`. |
| `V2__seed_demo_data.sql` | The Milestone 2 demo users, providers, services, slots, and appointments. |
| `V3__create_notification_outbox.sql` | `notification_outbox` (see [Notifications](#notifications-and-the-distribution-boundary)). |

Flyway records each applied script in `flyway_schema_history` and never runs it again, so restarting AutoBook keeps all
bookings. Future schema changes are added as new `V4__...` scripts.

## Booking Transaction Logic

`BookingService.book()` runs in one `@Transactional(isolation = READ_COMMITTED)` transaction:

1. `SELECT ... FROM availability_slots WHERE slot_id = ? FOR UPDATE`: lock the slot row (404 if missing).
2. Validate: service matches the slot (400), slot starts in the future (400), slot is still available (409).
3. `UPDATE availability_slots SET is_available = FALSE WHERE slot_id = ? AND is_available = TRUE AND start_time > ?`:
   must change exactly one row, otherwise 409.
4. `INSERT INTO appointments (...) VALUES (..., 'BOOKED', ...)`. A duplicate active booking would violate
   `uq_appointments_active_slot`; the service turns that into 409.
5. Write the confirmation notification to `notification_outbox` (same transaction).
6. Commit. Any exception rolls back every step, so the slot flag, the appointment table, and the outbox always agree.
   The notification is sent only after the commit.

Cancellation (`AppointmentService.cancel()`) locks the slot row and then the appointment row, in the same order as
booking so the two cannot deadlock. It sets the status to `CANCELLED` with a conditional update and reopens the slot,
all in one transaction.

## Concurrency Strategy

Double booking is prevented by three layers:

1. **Pessimistic row lock** (`SELECT ... FOR UPDATE`): concurrent bookings of the same slot are serialized. The
   second transaction waits (up to PostgreSQL's `lock_timeout` of 10 s, set for every pooled connection) until the
   first commits, then sees
   `is_available = FALSE` and gets a 409.
2. **Atomic conditional update**: `UPDATE ... WHERE is_available = TRUE` succeeds for exactly one transaction.
3. **Database uniqueness constraint** on `active_slot_id`: a final guarantee even if application logic were bypassed.

No `synchronized` blocks are used. The protection lives in the database, so it also holds across several application
instances. `ConcurrentBookingIntegrationTests` proves it:

- Two customers, each logged in with a real HTTP session, `POST /api/appointments` for the same slot at the same
  moment → exactly one `201`, exactly one `409`, one active appointment, slot marked unavailable.
- Twelve threads book the same slot through the service → exactly one success, eleven conflicts.
- A cancellation and a rebooking of the same slot race → the final state is always consistent.

## Notifications and the Distribution Boundary

```text
Browser / API client ──HTTP──▶ AutoBook (Appointment Scheduler) ──HTTP──▶ Notification Service (mock)
                                  │  BookingService / AppointmentService       POST /mock-notifications/api/notifications
                                  │  notification_outbox (PostgreSQL)
                                  └─ NotificationDispatcher → ResilientNotificationSender → NotificationGateway
```

- **Coarse-grained DTO:** `NotificationRequest` carries the idempotency key, type (`BOOKING_CONFIRMED` or
  `BOOKING_CANCELLED`), recipient email and name, subject, the finished message, the appointment id, and the
  confirmation number. One message is one remote call.
- **Gateway:** `NotificationGateway` is the only way AutoBook reaches the service. `HttpNotificationGateway` posts the
  DTO with a connect and read timeout and maps timeouts, connection errors, and 5xx to retryable failures.
- **Mock external service:** `MockNotificationController` (package `mocknotification`) runs in the same process for
  the demo but is only reached over HTTP. It delivers each idempotency key once and can simulate an outage, a number
  of failures, or slow responses (`PUT /mock-notifications/api/faults`, admin only).
- **Commit-then-notify:** `BookingNotifications` (`Propagation.MANDATORY`) writes the outbox row inside the booking
  or cancellation transaction. `NotificationDispatcher` reacts only `AFTER_COMMIT` and sends on a background thread, so
  the customer's request never waits for the notification service and a rolled-back booking sends nothing.
- **Timeout and retries:** each call is limited by `autobook.notifications.timeout` (2 s); one delivery round makes at
  most `max-attempts` (3) calls with exponential backoff (200 ms, 400 ms, capped at 2 s).
- **Idempotency:** the key `BOOKING_CONFIRMED-appointment-<id>` is unique in `notification_outbox` and is sent with
  every attempt. If a call times out after the service already delivered it, the retry is acknowledged as a duplicate
  and the customer is notified once.
- **Fallback:** if a round fails, the booking stays committed, the outbox row stays `PENDING` with a later
  `next_attempt_at` (30 s × round), and a scheduled job retries every 15 s, including after a restart. After
  `max-delivery-rounds` (5) the row becomes `FAILED` and an ERROR is logged for manual follow-up. The customer still
  sees the confirmation page and the booking under **My appointments**.

All settings are under `autobook.notifications.*` in `application.properties`.

## Logging, Health, and Metrics

- **Logging:** INFO for bookings, cancellations, and delivered notifications; WARN for booking conflicts found by the
  database, retried notification attempts, and notifications kept for a later round; ERROR for notifications that
  failed for good or an outbox that cannot be processed. Logs identify appointments and outbox rows by id and never
  contain passwords, session ids, or message bodies.
- **Health:** `GET /health` (public) returns `UP`/`DOWN` with component status for the PostgreSQL connection (`db`),
  disk space, and the notification outbox (`notificationOutbox`). Admins also see details (database product,
  pending and failed notification counts). `GET /health/liveness` and `/health/readiness` are available for
  containers; the Docker image's `HEALTHCHECK` uses `/health`.
- **Metrics** (admin only, `GET /metrics/<name>`): `autobook.bookings.created`, `autobook.bookings.conflicts`,
  `autobook.bookings.cancelled`, `autobook.booking.duration` (tag `outcome`), `autobook.notifications.rounds`
  (tag `outcome`), `autobook.notifications.retries`, and `autobook.notifications.outbox` (tag `status`).

## Failure Scenario and Recovery

**The notification service is down while customers are booking.**

1. A customer books a slot. The booking transaction commits (appointment `BOOKED`, slot taken, outbox row `PENDING`)
   and the customer immediately sees the confirmation page.
2. After the commit, the dispatcher calls the service: three attempts fail with 503 or time out. Each retry is
   logged as WARN; then the row is kept in the outbox with `next_attempt_at` 30 s later (WARN).
3. `/health` stays `UP` (bookings still work); admins see `notificationOutbox.details.pending` rise, and
   `autobook.notifications.rounds{outcome=retry_scheduled}` increases.
4. **Recovery:** when the service is back, the retry job sends every due notification. If AutoBook itself restarts in
   the meantime, the outbox is in PostgreSQL, so nothing is lost. Repeated attempts carry the same idempotency key, so
   no customer gets a duplicate.
5. If the outage lasts longer than five rounds, the notification becomes `FAILED` with an ERROR log; an admin finds it
   at `GET /api/admin/notifications` and contacts the customer. The appointment is unaffected.

To demonstrate it with curl (admin session; see the API example below for how the CSRF token works):

```bash
B=http://localhost:8080
T=$(curl -s -c a.txt -b a.txt $B/api/csrf | python3 -c "import sys,json;print(json.load(sys.stdin)['token'])")
curl -s -c a.txt -b a.txt -d "username=morgan.lee@example.com&password=password123&_csrf=$T" $B/login -o /dev/null
T=$(curl -s -c a.txt -b a.txt $B/api/csrf | python3 -c "import sys,json;print(json.load(sys.stdin)['token'])")
# 1. Take the notification service down
curl -s -b a.txt -X PUT -H "X-CSRF-TOKEN: $T" -H "Content-Type: application/json" \
     -d '{"unavailable":true,"failNextRequests":0,"delayMillis":0}' $B/mock-notifications/api/faults
# 2. Book an appointment as a customer (website or API): it succeeds.
# 3. The notification waits in the outbox (status PENDING, lastError mentions 503)
curl -s -b a.txt $B/api/admin/notifications
# 4. Bring the service back; within about a minute the retry job marks it SENT
curl -s -b a.txt -X PUT -H "X-CSRF-TOKEN: $T" -H "Content-Type: application/json" \
     -d '{"unavailable":false,"failNextRequests":0,"delayMillis":0}' $B/mock-notifications/api/faults
```

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
| GET | `/api/admin/notifications` | Admin | Notification outbox (sent, pending, failed) |
| GET | `/health`, `/health/liveness`, `/health/readiness`, `/info` | Public | Health and app info |
| GET | `/metrics`, `/metrics/{name}` | Admin | Metrics |
| POST | `/mock-notifications/api/notifications` | Gateway (no session) | Mock external notification service |
| GET | `/mock-notifications/api/notifications` | Admin | Notifications the mock service delivered |
| GET / PUT | `/mock-notifications/api/faults` | Admin | Simulate outage, failures, or slow responses |

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
| `MockNotificationServiceIntegrationTests` | 8 | Mock service delivery, idempotency, outage, validation, admin-only views |
| `NotificationGatewayIntegrationTests` | 4 | Gateway over real HTTP: success, duplicate, outage, timeout |
| `NotificationOutboxIntegrationTests` | 10 | Commit-then-notify, outage fallback and recovery, retries, timeout + idempotency, FAILED, rollback |
| `HealthAndMetricsIntegrationTests` | 6 | `/health` with database check, probes, metrics access and booking counters |
| `ApplicationRestartPersistenceIntegrationTests` | 1 | Bookings, history, and outbox survive an application restart |

All tests run against PostgreSQL 16 (Testcontainers). Classes that commit data (concurrency, notifications, restart)
use their own database so the seed data other classes rely on stays unchanged.

## Project Structure

```text
src/main/java/com/autobook/
  config/            SecurityConfig, TimeConfig
  controller/        JSON controllers (Home, Slot, SlotApi, AppointmentApi, ProviderApi, AdminApi,
                     AdminNotificationApi, Auth)
  controller/web/    Thymeleaf page controllers and web error handling
  dto/               Request/response records
  exception/         Domain exceptions and ApiExceptionHandler
  model/             AppUser, AppointmentStatus, UserRole, SlotLock
  notification/      NotificationRequest, NotificationGateway, outbox, dispatcher, retry sender
  mocknotification/  Mock external notification service (controller, service, fault switches)
  observability/     Booking and notification metrics, outbox health indicator
  repository/        JDBC repositories (all SQL lives here)
  service/           Business logic and transactions
src/main/resources/
  application.properties
  db/migration/      Flyway scripts V1 (schema), V2 (demo data), V3 (notification outbox)
  templates/         Thymeleaf pages
  static/css, static/js
src/test/            Integration tests and test configuration (Testcontainers PostgreSQL)
scripts/             compose-smoke-test.sh
Dockerfile, docker-compose.yml, .env.example, .github/workflows/ci.yml
docs/
  AutoBook_Milestone_3_Report.pdf (+ .html source), diagrams/distribution-diagram.svg/.png,
  Milestone_3_Code_Walkthrough_Script.md, and the Milestone 1 and 2 reports, diagrams, and screenshots
```

## Inspecting the Database

With Docker Compose running: `docker compose exec db psql -U autobook -d autobook`, then for example
`SELECT appointment_id, status FROM appointments;` or `SELECT outbox_id, notification_type, status, attempts FROM
notification_outbox;`. The admin dashboard and `/api/admin/*` endpoints show the same data.

## Known Limitations

- The notification service is a mock that runs in the same process and "delivers" by recording messages in memory
  (cleared on restart); no real email or SMS is sent. Its admin endpoints exist for demos and testing.
- A customer who loses a booking race gets a clear 409 and picks another slot; bookings are not retried
  automatically. If a lock cannot be acquired within 10 seconds, the request also fails with 409.
- A slot with cancelled-appointment history is closed rather than deleted, so a provider cannot re-add a slot with
  exactly the same start and end time (`UNIQUE(provider_id, start_time, end_time)`).
- Times are local times in the configured time zone (`AUTOBOOK_TIMEZONE`); there is no per-user time-zone handling.
- Demo slot times are relative to the day the database was created; they become past slots over time.
- No self-service registration or password reset.
