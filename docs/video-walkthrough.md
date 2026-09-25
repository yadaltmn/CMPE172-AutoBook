# AutoBook Milestone 1 Video Walkthrough Script

## 0:00-0:30 - AutoBook Introduction

Hi, this is my CMPE 172 Term Project, AutoBook. AutoBook is an automotive service appointment system. The long-term goal is for customers to browse service availability and book appointments, while providers manage slots and view appointments. For Milestone 1, I focused on the required database-backed Spring Boot skeleton rather than implementing the full booking workflow.

## 0:30-1:15 - Database Schema and Five Entities

I will start in `src/main/resources/schema.sql`. The project defines the five required tables: `users`, `providers`, `services`, `availability_slots`, and `appointments`.

The `users` table stores future customer, provider, and admin identities. The `providers` table stores automotive service providers. The `services` table stores service types like oil changes, inspections, diagnostics, and tire service. The `availability_slots` table connects a provider and service to a start and end time. The `appointments` table represents a booked slot for a user.

## 1:15-1:45 - Double-Booking UNIQUE Constraint

The most important scheduling rule is in the `appointments` table. The `slot_id` column is marked `UNIQUE`, so the database itself prevents the same availability slot from being booked twice. This is important because even if application code has a bug, the database still protects the appointment schedule from duplicate bookings.

## 1:45-2:10 - `seed.sql`

Next I will open `src/main/resources/seed.sql`. This file loads sample data when the application starts. It creates sample users, two providers, four services, several availability slots, and one appointment. The seed data is intentionally small but realistic, and it does not violate the unique slot booking constraint.

## 2:10-2:50 - Repository and JDBC

Now I will open `SlotRepository`. This class is marked with `@Repository` and uses `JdbcTemplate`. There is no JPA, no Hibernate, and no ORM. The SQL query joins `availability_slots`, `providers`, and `services`, filters to slots where `is_available` is true, and orders by start time. The row mapper converts each database row into an `AvailabilitySlotDto`.

I will also show `HomeRepository`, which reads counts from the database for the home endpoint. This proves both endpoints read from the database.

## 2:50-3:25 - Service Layer

Next is the service layer. `SlotService` calls `SlotRepository`, and `HomeService` calls `HomeRepository`. For Milestone 1 the services are simple, but they are still important because they keep business workflow logic out of the controllers. Future booking, cancellation, and validation rules would belong in this layer.

## 3:25-4:00 - Controller Layer

Now I will open the controllers. `HomeController` handles `GET /` and returns a database-backed summary DTO. `SlotController` handles `GET /slots` and returns the available slot DTOs from the service layer. This follows the required flow: Controller to Service to Repository to JDBC to Database.

## 4:00-4:30 - DTOs and Endpoints

The DTO package contains `HomeDto` and `AvailabilitySlotDto`. These classes define the JSON response shape without exposing database tables directly. `HomeDto` includes the application name and database counts. `AvailabilitySlotDto` includes slot, provider, service, start time, end time, and availability fields.

## 4:30-5:00 - Run and Test Application

To run the application, I use:

```bash
./mvnw spring-boot:run
```

To run tests, I use:

```bash
./mvnw test
```

The tests check that the Spring context loads, seed data is available, `GET /` works, `GET /slots` works, and the database rejects duplicate appointments for the same slot.

## 5:00-5:30+ - Request Flow and Architecture

The overall architecture is:

Client -> Controller -> Service -> Repository -> JDBC -> Database

For example, when a client calls `GET /slots`, Spring routes the request to `SlotController`. The controller calls `SlotService`, the service calls `SlotRepository`, and the repository executes SQL through `JdbcTemplate`. The database returns rows, the repository maps them into DTOs, and Spring returns the DTOs as JSON.

This completes Milestone 1: a Java Spring Boot Maven application using SQL through JDBC, with no ORM, a five-entity schema, seed data, two database-backed read endpoints, tests, and supporting design documentation.
