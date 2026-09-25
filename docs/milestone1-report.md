# AutoBook Milestone 1 Report Notes

## 1. Project Title

AutoBook: Automotive Service Appointment System

## 2. Project Overview

AutoBook is a web-based appointment scheduling system for automotive services. The goal is to help customers find available service times and help automotive providers manage availability and booked appointments. Milestone 1 focuses on the requirements, database design, and a working Spring Boot skeleton that reads from the database.

## 3. Chosen Scenario

The chosen scenario is an automotive service appointment system. Customers will eventually be able to browse services and book appointments, while providers or administrators will eventually manage available time slots and view appointments booked with them.

## 4. User Roles

- Customer: browses available service slots, books appointments, views appointments, and cancels appointments in future milestones.
- Provider/Admin: creates or removes availability slots and views appointments booked with the provider in future milestones.

## 5. Core Features

Milestone 1 implements only the database-backed skeleton. The full proposed system features are:

1. Customer login - future milestone work.
2. Browse, filter, and paginate available slots by provider, service, and date using SQL `LIMIT`/`OFFSET` - future milestone work.
3. Book a slot for a chosen service - future milestone work.
4. View my appointments, including upcoming appointments and history - future milestone work.
5. Cancel my appointment with owner-only access - future milestone work.
6. Provider login - future milestone work.
7. Provider creates and removes availability slots tied to a service - future milestone work.
8. Provider views appointments booked with them - future milestone work.
9. Confirmation through a mock external service when booking or cancelling - future milestone work.
10. Logging, a health endpoint, and at least one metric - future milestone work.
11. AI agents, including a conversational booking agent and an autonomous agent - future milestone work.
12. Grounded Q&A/RAG over a small knowledge base - future milestone work.

## 6. Main Entities

The five main entities are `users`, `providers`, `services`, `availability_slots`, and `appointments`.

## 7. Technology Statement

AutoBook uses Java + Spring Boot + SQL via JDBC (no ORM). Spring Boot provides the modern Java enterprise application framework, while REST endpoints expose distributed application behavior. In the project architecture, Spring Boot realizes the modern equivalent of J2EE-style enterprise application development, and REST realizes the modern equivalent of CORBA/distributed object communication patterns.

## 8. Architecture and Block Diagram Explanation

The application uses the required layered architecture:

Web Client -> Spring Boot -> Controller -> Service -> Repository -> JDBC -> H2 Database

The controller receives HTTP requests. The service layer coordinates application logic. The repository layer owns SQL queries and database access. JDBC is the database access technology, and H2 is the Milestone 1 database.

## 9. Database Design

The database stores users, service providers, service types, provider availability slots, and appointments. Availability slots connect a provider to a service during a time range. Appointments connect a user to a provider, service, and availability slot.

## 10. Primary Keys

- `users.user_id`
- `providers.provider_id`
- `services.service_id`
- `availability_slots.slot_id`
- `appointments.appointment_id`

## 11. Foreign Keys

- `availability_slots.provider_id` references `providers.provider_id`
- `availability_slots.service_id` references `services.service_id`
- `appointments.user_id` references `users.user_id`
- `appointments.provider_id` references `providers.provider_id`
- `appointments.service_id` references `services.service_id`
- `appointments.slot_id` references `availability_slots.slot_id`

## 12. Relationship Explanations

A provider can offer many availability slots. A service can appear in many availability slots. A customer can have many appointments. Each appointment is connected to exactly one user, provider, service, and availability slot.

## 13. Cardinality

- One provider to many availability slots.
- One service to many availability slots.
- One user to many appointments.
- One provider to many appointments.
- One service to many appointments.
- One availability slot to zero or one appointment.

## 14. Weak Entities

`appointments` depends on other entities because an appointment has no useful meaning without a user, provider, service, and slot. `availability_slots` also depends on provider and service records.

## 15. Constraints

The schema uses primary keys, foreign keys, `NOT NULL` constraints, unique email constraints, role/status check constraints, positive service duration checks, and an `end_time > start_time` check for availability slots.

## 16. Double-Booking Protection

The `appointments` table has `UNIQUE(slot_id)`. This prevents the same availability slot from being booked by more than one appointment, even if two requests try to use the same slot.

## 17. Request Flow Through Layers

For `GET /slots`, the request enters `SlotController`, which calls `SlotService`. The service calls `SlotRepository`, and the repository runs a JDBC query joining `availability_slots`, `providers`, and `services`. The result is mapped into `AvailabilitySlotDto` records and returned as JSON.

For `GET /`, the request enters `HomeController`, which calls `HomeService`. The service calls `HomeRepository`, and the repository reads database counts for providers, services, and available slots. The result is returned as a `HomeDto`.

## 18. Page Controller vs Front Controller

Spring Boot uses the Front Controller pattern through Spring MVC's central dispatcher. Incoming HTTP requests first go through the framework dispatcher, which routes each request to the matching controller method. The project's controller classes act like page or endpoint controllers for specific routes such as `/` and `/slots`, while the framework-level dispatcher provides the overall front controller behavior.

## 19. Milestone 1 Implementation Scope

Milestone 1 implements the project skeleton, database schema, seed data, JDBC repositories, services, DTOs, controllers, two database-backed read endpoints, tests, and documentation. It does not implement authentication, complete booking workflows, provider management workflows, notification integration, AI agents, or RAG.

## 20. Future Milestone Features

Future milestones will add customer and provider authentication, slot browsing filters and pagination, booking, cancellation, appointment history, provider slot management, confirmation notifications through a mock external service, operational logging/health/metrics, AI booking support, autonomous agent behavior, and grounded Q&A over a small knowledge base.
