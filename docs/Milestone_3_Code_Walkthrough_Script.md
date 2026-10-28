# AutoBook Milestone 3: Code Walkthrough Video Script

Target length: about 7–8 minutes (minimum 5). Like Milestone 2, this is a **code walkthrough**: most of the time is
spent in VS Code on the real implementation, with short demos in the browser and terminal to connect the code to
running behavior.

No video has been recorded yet; this is the script to record from. Line numbers refer to the `milestone-3` tag.
In VS Code, **Cmd+P** opens a file by name and **Cmd+F** searches inside it (Ctrl on Windows); **Cmd+Shift+F**
searches the whole project. Each section lists the exact search term to type.

## Before recording

1. Open your clone of the repository in VS Code (File → Open Folder → the `CMPE172-AutoBook` folder you cloned).
2. Start Docker Desktop and wait until it says it is running.
3. In the VS Code terminal (Terminal → New Terminal):
   ```bash
   cp .env.example .env          # only the first time; then set AUTOBOOK_DB_PASSWORD in .env
   docker compose up -d --build  # first build takes a few minutes
   docker compose ps             # both services should show "healthy"
   ```
4. Open <http://localhost:8080> and <http://localhost:8080/health> in the browser.
5. Open the GitHub Actions page for the repository (github.com/yadaltmn/CMPE172-AutoBook → **Actions**) and click
   the latest green run on `main`.
6. Open these files in tabs, in this order (Cmd+P, then type the name):
   `pom.xml`, `V1__create_booking_schema.sql`, `application.properties`, `NotificationRequest.java`,
   `NotificationGateway.java`, `HttpNotificationGateway.java`, `BookingService.java`, `BookingNotifications.java`,
   `V3__create_notification_outbox.sql`, `NotificationDispatcher.java`, `ResilientNotificationSender.java`,
   `MockNotificationService.java`, `BookingMetrics.java`, `docker-compose.yml`, `ci.yml`,
   `NotificationOutboxIntegrationTests.java`, `ApplicationRestartPersistenceIntegrationTests.java`.
7. Close Slack, email, and anything else that could show a notification on screen.

---

## 1. Introduction (0:00 – 0:40)

**Show:** the distribution diagram, `docs/diagrams/distribution-diagram.png` (open it from the Explorer).

> "This is AutoBook, my CMPE 172 automotive service booking system. In Milestone 3 I moved it to Java 21 and a
> persistent PostgreSQL database, added a distribution boundary to an external notification service, made that
> boundary resilient with timeouts, retries, idempotency, and a fallback, and added health checks, metrics, Docker,
> and GitHub Actions.
>
> Here is the distribution: the client talks only to the Appointment Scheduler, which is my Spring Boot app. The
> scheduler talks to the notification service only through one gateway, over HTTP. The key rule is that the booking
> commits first, and the notification is sent afterwards."

## 2. Java 21 and PostgreSQL with Flyway (0:40 – 1:50)

**Show:** `pom.xml`. **Search:** `java.version` (line 30), then `flyway`.

> "First, the professor's corrections. The build now targets Java 21, and H2 is replaced by PostgreSQL with Flyway
> and the PostgreSQL driver."

**Show:** `V1__create_booking_schema.sql`. **Highlight:** the first comment block (lines 1–4), then search `STORED`
(line 62).

> "In Milestone 2, `schema.sql` started with DROP TABLE, so every restart erased all bookings. Now the same five
> tables live in Flyway migration V1. There is no DROP: Flyway runs each script once and records it in
> `flyway_schema_history`, so on restart the data stays. The schema is the same as Milestone 2. The only change here
> is the keyword STORED, which PostgreSQL needs for the generated `active_slot_id` column that lets a cancelled slot
> be rebooked while keeping the history."

**Show:** `application.properties`, lines 1–16. **Highlight:** `${AUTOBOOK_DB_URL...}` and `lock_timeout`.

> "The connection comes from environment variables, so there is no password in the repository and no personal file
> path. And the 10-second lock wait from my Milestone 2 concurrency design is now a PostgreSQL lock_timeout, so the
> row lock, conditional update, and unique constraint work exactly as before."

## 3. The notification DTO and gateway (1:50 – 2:50)

**Show:** `NotificationRequest.java`. **Highlight:** the record fields (lines 19–28) and the Javadoc above them.

> "This is the coarse-grained DTO. One request carries everything the notification service needs: the recipient, the
> type, the subject, the finished message, the appointment, and the confirmation number. One notification is one
> remote call, so there is only one call to protect with a timeout and retries. The idempotency key identifies the
> business event, like BOOKING_CONFIRMED-appointment-42."

**Show:** `NotificationGateway.java`, line 9 to the end.

> "The gateway is the boundary. It has one method, send, and the rest of AutoBook never sees HTTP or JSON."

**Show:** `HttpNotificationGateway.java`. **Search:** `setReadTimeout` (line 40), then `catch (HttpServerErrorException`
(line 60).

> "The implementation uses RestClient with a connect and read timeout from configuration, 2 seconds by default. Then
> it classifies failures: a 5xx, a timeout, or a connection error is retryable; a 4xx means the request is invalid,
> so retrying cannot help."

## 4. Commit-then-notify with the transactional outbox (2:50 – 4:20)

**Show:** `BookingService.java`. **Search:** `lockById` (line 87), then scroll to `bookingNotifications.bookingConfirmed`
(line 108).

> "This is the same booking transaction from Milestone 2: lock the slot row with SELECT FOR UPDATE, check it, claim it
> with a conditional update, insert the appointment. The new line is here: inside the same transaction, I write the
> confirmation into an outbox table. I do not call the notification service here, because a slow service would hold
> the row lock and block other customers, and if the booking rolled back afterwards, the customer would already have
> a confirmation for a booking that doesn't exist."

**Show:** `BookingNotifications.java`. **Search:** `Propagation.MANDATORY` (line 37), then `private void enqueue`
(line 64).

> "MANDATORY means this method refuses to run without an active transaction, so the outbox row always commits or
> rolls back together with the appointment. The insert is idempotent, and it publishes an event."

**Show:** `V3__create_notification_outbox.sql`, lines 1–26. **Highlight:** `uq_notification_outbox_key`.

> "This is the outbox table: one row per business event, enforced by the unique idempotency key, with the status,
> number of attempts, last error, and the next attempt time."

**Show:** `NotificationDispatcher.java`. **Search:** `AFTER_COMMIT` (line 64).

> "And this is commit-then-notify. The listener runs only in the AFTER_COMMIT phase. If the booking rolls back, Spring
> drops the event, so nothing is ever sent. After a commit, it hands the work to a background thread, so the customer
> gets the confirmation page immediately. So a failed notification can never make a committed appointment disappear."

## 5. Timeout, retries, idempotency, fallback (4:20 – 5:40)

**Show:** `ResilientNotificationSender.java`. **Search:** `for (int attempt = 1` (line 37).

> "Each delivery round makes at most three attempts. Every attempt is limited by the 2-second timeout, and the wait
> between attempts grows from 200 milliseconds and is capped at 2 seconds. Non-retryable errors stop immediately."

**Show:** `MockNotificationService.java`. **Search:** `computeIfAbsent` (line 47).

> "Retrying is safe because of idempotency. The mock service delivers each key once. The tricky case is a timeout
> where the service actually got the message but the answer was lost: the retry carries the same key, the service
> says 'duplicate', and the customer is notified only once."

**Show:** `NotificationDispatcher.java`. **Search:** `private DeliveryOutcome fallback` (line 129), then `@Scheduled`
(line 81).

> "If all three attempts fail, this is the fallback. The booking is not touched. The outbox row stays PENDING with a
> later retry time, and this scheduled job retries due notifications every 15 seconds. The outbox is in PostgreSQL, so
> this also works after a restart. After five rounds the row becomes FAILED and an ERROR is logged so an admin can
> follow up."

## 6. Demo: notification service outage (5:40 – 6:30)

**Show:** the browser and the VS Code terminal side by side. Use the commands from the README section
"Failure Scenario and Recovery".

1. Run the admin login lines and the first `curl ... /mock-notifications/api/faults` command with `"unavailable":true`.
2. In the browser, sign in as `alex.rivera@example.com` / `password123`, book any slot, and show the confirmation page.
3. In the terminal: `docker compose logs app --tail=20`. Point at the WARN lines ("attempt 1/3 failed", "Kept in
   outbox").
4. Run `curl -s -b a.txt http://localhost:8080/api/admin/notifications` and point at `"status":"PENDING"`.
5. Run the faults command again with `"unavailable":false`, wait about 45 seconds, and repeat step 4: `"status":"SENT"`.

> "The booking succeeded even though the notification service was down. The notification waited in the outbox, and
> when the service came back the retry job delivered it."

## 7. Logging, health, and metrics (6:30 – 7:10)

**Show:** <http://localhost:8080/health> in the browser.

> "GET /health is public. It shows UP and the status of each component, including db, which is a live PostgreSQL
> connectivity check, and my notificationOutbox indicator."

**Show:** `BookingMetrics.java`, lines 28–33. **Search:** `autobook.bookings.created`.

> "These are the booking metrics: bookings created, conflicts, cancellations, and the time spent in the booking
> transaction. Admins can read them at /metrics. The logs use INFO for normal events, WARN for retries and conflicts,
> ERROR for notifications that failed for good, and they never contain passwords or message bodies."

## 8. Docker, CI, and tests (7:10 – 8:00)

**Show:** `docker-compose.yml`. **Highlight:** `autobook-db:/var/lib/postgresql/data` and `condition: service_healthy`.

> "Docker Compose runs PostgreSQL 16 with a named volume, so the data survives restarts, and starts AutoBook only after
> the database is healthy."

**Show:** `.github/workflows/ci.yml`, then the green GitHub Actions run in the browser (both jobs).

> "GitHub Actions runs on every push. The first job runs all 114 tests on Java 21 against real PostgreSQL containers.
> The second builds the Docker image, starts the stack, books an appointment through the API, restarts the app, and
> checks that the booking is still there."

**Show:** `ApplicationRestartPersistenceIntegrationTests.java`, line 37, and `NotificationOutboxIntegrationTests.java`,
lines 119 and 162.

> "This test starts AutoBook, books and cancels, shuts it down, starts it again on the same database, and checks that
> everything is still there. These two cover booking while the notification service is down, and a timeout followed by
> a retry that's recognized as a duplicate. All Milestone 1 and 2 tests still pass, now on PostgreSQL. Thanks for
> watching."

---

## Checklist after recording

- The video is at least 5 minutes long.
- It shows Java 21, PostgreSQL/Flyway, the DTO and gateway, commit-then-notify, timeout/retry/idempotency/fallback,
  health and metrics, Docker Compose, and the green CI run.
- Stop the containers afterwards with `docker compose down` (keeps data) or `docker compose down -v` (deletes it).
