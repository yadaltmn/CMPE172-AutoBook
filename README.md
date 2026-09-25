# AutoBook

CMPE 172 Term Project  
Automotive Service Appointment System

## Overview

AutoBook is a Spring Boot application for scheduling automotive service appointments. Milestone 1 defines the project scope, database design, and a minimal layered application skeleton that reads from an H2 database.

## Technology

- Java 17
- Spring Boot
- Maven
- JDBC
- H2
- REST

No ORM is used. Database access is performed through JDBC with `JdbcTemplate`.

## Architecture

The application follows the required layered flow:

Client -> Controller -> Service -> Repository -> JDBC -> Database

Controllers expose REST endpoints, services hold application workflow logic, repositories execute SQL with JDBC, and H2 stores the Milestone 1 sample data.

## Database

The database contains five main entities:

- `users`: customer, provider, and admin login identities for future milestones.
- `providers`: automotive shops or provider accounts offering service appointments.
- `services`: automotive service types such as oil changes and inspections.
- `availability_slots`: open provider/service appointment times.
- `appointments`: booked service appointments.

Important integrity rules include primary keys, foreign keys, email uniqueness, valid status/role checks, and `UNIQUE(slot_id)` on `appointments`. The `UNIQUE(slot_id)` rule prevents multiple appointments from booking the same availability slot.

## Running the Application

Install a JDK compatible with Java 17 or newer, then run:

```bash
./mvnw spring-boot:run
```

The application starts with an in-memory H2 database initialized from `schema.sql` and `seed.sql`.

## Running Tests

```bash
./mvnw test
```

## Endpoints

### `GET /`

Returns a database-backed project summary.

Example response:

```json
{
  "applicationName": "AutoBook",
  "providerCount": 2,
  "serviceCount": 4,
  "availableSlotCount": 5
}
```

### `GET /slots`

Returns available appointment slots read from the database.

Example response:

```json
[
  {
    "slotId": 1,
    "providerId": 1,
    "providerName": "Downtown Auto Care",
    "serviceId": 1,
    "serviceName": "Oil Change",
    "startTime": "2026-10-05T09:00:00",
    "endTime": "2026-10-05T09:45:00",
    "available": true
  }
]
```

## H2 Database

The in-memory database is configured in `src/main/resources/application.properties`.

- Schema: `src/main/resources/schema.sql`
- Seed data: `src/main/resources/seed.sql`
- H2 console: `http://localhost:8080/h2-console`
- JDBC URL: `jdbc:h2:mem:autobook`
- Username: `sa`
- Password: empty

## Project Structure

```text
src/main/java/com/autobook/
  controller/
  dto/
  repository/
  service/
  AutobookApplication.java
src/main/resources/
  application.properties
  schema.sql
  seed.sql
src/test/java/com/autobook/
  AutobookApplicationTests.java
```

## Milestone 1 Scope

Milestone 1 implements the database-backed layered skeleton only. Authentication, booking workflows, cancellation, provider slot management, notifications, AI agents, and RAG features are intentionally documented as future milestone work and are not implemented yet.
