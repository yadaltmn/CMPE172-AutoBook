# AutoBook Milestone 2: Code Walkthrough Video Script

Target length: about 7 minutes (minimum 5). This is a **code walkthrough**: most of the time is spent in VS Code
explaining the actual implementation. The website is shown briefly to connect the code to what the user sees.

No video has been recorded yet; this is the script to record from.

## Before recording

1. Open the project in VS Code: `/Users/JadaNguyen/Documents/CMPE 172 -AUTOBOOK`.
2. In the VS Code terminal run `./mvnw clean test` once so dependencies are cached (the run takes about 15 seconds).
3. Start the app in a second terminal: `./mvnw spring-boot:run`, then open <http://localhost:8080>.
4. Have these files open in tabs, in this order:
   `schema.sql`, `SecurityConfig.java`, `CurrentUserService.java`, `SlotRepository.java`, `BookingService.java`,
   `AppointmentService.java`, `ProviderService.java`, `ApiExceptionHandler.java`, `ConcurrentBookingIntegrationTests.java`.

---

## 1. Introduction and architecture (0:00 – 0:45)

**Show:** the project tree in the VS Code Explorer.

> "This is AutoBook, my CMPE 172 automotive service booking system, built with Spring Boot and plain JDBC: no JPA and
> no Hibernate. Milestone 2 adds login and roles, the booking workflow with protection against double booking,
> cancellation, provider availability management, and a Thymeleaf website.
>
> The code follows the layered architecture from Milestone 1. The `controller` package has the JSON API,
> `controller/web` has the page controllers, both call the same `service` classes where the transactions live, and
> all SQL is in the `repository` classes using `JdbcTemplate`."

## 2. Database changes (0:45 – 1:30)

**Show:** `src/main/resources/schema.sql`, the `providers` and `appointments` tables.

> "Two schema changes. First, `providers.user_id` links each repair shop to a login account, so the provider is always
> derived from whoever is signed in.
>
> Second, in Milestone 1 `appointments.slot_id` was UNIQUE. That stops double booking, but a cancelled appointment
> would block the slot forever unless I deleted the history. So I replaced it with this generated column,
> `active_slot_id`. It equals `slot_id` when the status is BOOKED or COMPLETED and NULL when it is CANCELLED, and
> there's a UNIQUE constraint on it. A unique constraint allows many NULLs, so cancelled rows stay as history, but
> the database itself still refuses two active appointments for one slot."

## 3. Authentication and role-based access (1:30 – 2:30)

**Show:** `config/SecurityConfig.java`, the `authorizeHttpRequests` block, then `exceptionHandling`.

> "Login uses Spring Security form login. `DatabaseUserDetailsService` loads the user by email with a JDBC query, and
> passwords are BCrypt hashes. Here are the role rules: `/customer/**` and `/api/appointments/**` need CUSTOMER,
> `/provider/**` needs PROVIDER, `/admin/**` needs ADMIN. Browsing slots is public.
>
> For the API I register separate handlers: an unauthenticated `/api` request gets a JSON 401 and a wrong role gets a
> JSON 403, while browser pages redirect to the login page. CSRF protection stays on for every POST, PUT, and DELETE."

**Show:** `service/CurrentUserService.java`.

> "Roles aren't enough on their own; a customer must not touch another customer's data. No controller ever reads a
> user id from the request. `CurrentUserService` takes the authenticated session and loads the user from the
> database, and the services compare ownership. For example, `requireOwner` in `AppointmentService` throws a 403.
> There's a test that sends a fake `userId` in the booking body and checks it's ignored."

## 4. Searching slots (2:30 – 3:00)

**Show:** `repository/SlotRepository.java`, `searchAvailableSlots` and `availableSlotFilter`.

> "The browse page calls this search. It only returns slots that are available and start in the future, adds optional
> provider, service, and date filters, sorts by start time, and pages with `LIMIT ? OFFSET ?`. Every value is a bound
> parameter. Only fixed SQL fragments are concatenated, so there's no SQL injection."

## 5. Booking transaction and concurrency (3:00 – 4:45), the core of the milestone

**Show:** `service/BookingService.java`, the `book` method.

> "This is the booking transaction. The race condition we must prevent: two customers both read that a slot is
> available, and both insert an appointment. The critical section is from reading availability to inserting the
> appointment.
>
> The whole method is `@Transactional` with READ COMMITTED isolation, so it runs on one connection and either
> everything commits or everything rolls back.
>
> Step one: `lockById` runs `SELECT ... FOR UPDATE` on the slot row."

**Show:** `SlotRepository.lockById` and `claimIfAvailable`, then back to `BookingService`.

> "That's a pessimistic row lock. If a second customer is booking the same slot, their SELECT FOR UPDATE waits here
> until my transaction commits, so the two bookings run one after another instead of interleaving.
>
> Then I validate: the service matches, the slot is in the future (otherwise 400), and it's still available
> (otherwise 409).
>
> Step two: `claimIfAvailable` is a conditional UPDATE, `SET is_available = FALSE WHERE is_available = TRUE`. It
> must change exactly one row. If it changes zero, someone else won, and that's a 409.
>
> Step three: insert the appointment. If anything got past the first two layers, the unique constraint on
> `active_slot_id` rejects the duplicate. I catch `DuplicateKeyException` and also `ConcurrencyFailureException`,
> which is what a lock timeout becomes, and turn them into a 409 Conflict.
>
> So there are three layers: row lock, atomic conditional update, and a database constraint. I don't use Java
> `synchronized`, because that only protects one JVM and doesn't roll anything back.
>
> Why READ COMMITTED and not SERIALIZABLE? READ COMMITTED prevents dirty reads, and the explicit row lock removes
> the lost-update problem, so I get the guarantee I need without the extra aborts of SERIALIZABLE. I chose
> pessimistic locking over an optimistic version column, because for a popular slot most optimistic attempts would
> fail and need retries.
>
> There's no automatic retry. Losing the race isn't a temporary error: the slot really is gone, so the customer gets
> 409 and the website sends them back to pick another time. H2's lock timeout is 10 seconds, set in
> `application.properties`."

**Show:** `service/AppointmentService.java`, `cancel`.

> "Cancellation uses the same pattern. It checks ownership, locks the slot row and then the appointment row, in the
> same order as booking so they can't deadlock. Then it sets the status to CANCELLED with a conditional update and
> reopens the slot. The cancelled row stays as history."

**Show briefly:** `service/ProviderService.java`, `createSlot`.

> "For providers, checking for overlapping slots and then inserting is also a race, a phantom read. So I lock the
> provider's row first, which serializes schedule changes for that provider."

## 6. The concurrency test (4:45 – 6:00)

**Show:** `src/test/java/com/autobook/ConcurrentBookingIntegrationTests.java`.

> "This test proves it. It starts the real application on a random port with its own in-memory database, so it can
> commit real transactions.
>
> `loggedInClient` logs Jada and Alex in through the real login form with CSRF tokens. Each one gets an HTTP client
> with its own session cookie. `runSimultaneously` starts a thread per request, waits until all are ready, then
> releases them together with a `CountDownLatch`. Every wait has a timeout, and there's a 30-second `@Timeout`, so a
> deadlock can't hang the build.
>
> The assertions: exactly one 201, exactly one 409, exactly one BOOKED appointment in the database, and the slot is
> marked unavailable. The second test does the same with twelve threads calling the service directly: one success,
> eleven conflicts. The third races a cancellation against a rebooking and checks the final state is consistent
> either way."

**Do:** in the terminal run

```bash
./mvnw test -Dtest=ConcurrentBookingIntegrationTests
```

> "All three pass." *(Point at `Tests run: 3, Failures: 0, Errors: 0`.)*

Then run the full suite:

```bash
./mvnw clean test
```

> "The whole suite is 85 integration tests across 10 classes: security, search, booking, concurrency, cancellation,
> provider, the web pages, and admin. All 85 pass, including the five original Milestone 1 tests."

## 7. Error handling and quick website demo (6:00 – 7:00)

**Show:** `exception/ApiExceptionHandler.java`.

> "Errors are handled centrally: 400 for validation, 403 for ownership, 404, 409 for conflicts, and a generic 500.
> The body never includes SQL or stack traces."

**Show the browser (keep it short):**

1. Home page, then **VIEW AVAILABLE SLOTS**, then filter by service.
2. Sign in as `jada.nguyen@example.com` / `password123`, then **Book this slot**, **Confirm booking**, and the confirmation page.
3. **My appointments**, then **Cancel**, then confirm.
4. Optional: sign in as `service@downtownautocare.example.com` and show **Availability**.

> "That page flow is just a Thymeleaf form posting to `CustomerPageController`, which calls the same
> `BookingService.book` method we just walked through.
>
> To sum up: AutoBook Milestone 2 adds role-based security, a transactional booking flow that prevents double booking
> with a row lock, an atomic update, and a uniqueness constraint, cancellation with history, provider schedule
> management, and a website, all on Spring Boot with plain JDBC. Thanks for watching."

---

## Timing checklist

| Section | Time |
|---|---|
| Intro and architecture | 0:45 |
| Database changes | 0:45 |
| Security and ownership | 1:00 |
| Slot search | 0:30 |
| Booking transaction and concurrency | 1:45 |
| Concurrency test and test run | 1:15 |
| Error handling and website | 1:00 |
| **Total** | **about 7:00** |
