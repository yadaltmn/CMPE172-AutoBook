# AutoBook Relational Schema

## `users`

| Column | Type | Key/Constraint |
| --- | --- | --- |
| `user_id` | `BIGINT` | Primary key |
| `first_name` | `VARCHAR(100)` | `NOT NULL` |
| `last_name` | `VARCHAR(100)` | `NOT NULL` |
| `email` | `VARCHAR(255)` | `NOT NULL`, `UNIQUE` |
| `password` | `VARCHAR(255)` | `NOT NULL` |
| `role` | `VARCHAR(30)` | `NOT NULL`, check: `CUSTOMER`, `PROVIDER`, `ADMIN` |

## `providers`

| Column | Type | Key/Constraint |
| --- | --- | --- |
| `provider_id` | `BIGINT` | Primary key |
| `name` | `VARCHAR(150)` | `NOT NULL` |
| `email` | `VARCHAR(255)` | `NOT NULL`, `UNIQUE` |
| `phone` | `VARCHAR(30)` | Optional |

## `services`

| Column | Type | Key/Constraint |
| --- | --- | --- |
| `service_id` | `BIGINT` | Primary key |
| `name` | `VARCHAR(120)` | `NOT NULL`, `UNIQUE` |
| `description` | `VARCHAR(500)` | Optional |
| `duration_minutes` | `INT` | `NOT NULL`, check: greater than `0` |

## `availability_slots`

| Column | Type | Key/Constraint |
| --- | --- | --- |
| `slot_id` | `BIGINT` | Primary key |
| `provider_id` | `BIGINT` | Foreign key to `providers(provider_id)`, `NOT NULL` |
| `service_id` | `BIGINT` | Foreign key to `services(service_id)`, `NOT NULL` |
| `start_time` | `TIMESTAMP` | `NOT NULL` |
| `end_time` | `TIMESTAMP` | `NOT NULL`, check: `end_time > start_time` |
| `is_available` | `BOOLEAN` | `NOT NULL`, default `TRUE` |

Important constraint: `UNIQUE(provider_id, start_time, end_time)` prevents a provider from publishing duplicate slot times.

## `appointments`

| Column | Type | Key/Constraint |
| --- | --- | --- |
| `appointment_id` | `BIGINT` | Primary key |
| `user_id` | `BIGINT` | Foreign key to `users(user_id)`, `NOT NULL` |
| `provider_id` | `BIGINT` | Foreign key to `providers(provider_id)`, `NOT NULL` |
| `service_id` | `BIGINT` | Foreign key to `services(service_id)`, `NOT NULL` |
| `slot_id` | `BIGINT` | Foreign key to `availability_slots(slot_id)`, `NOT NULL`, `UNIQUE` |
| `status` | `VARCHAR(30)` | `NOT NULL`, check: `BOOKED`, `CANCELLED`, `COMPLETED` |
| `created_at` | `TIMESTAMP` | `NOT NULL`, default `CURRENT_TIMESTAMP` |

Important constraint: `UNIQUE(slot_id)` prevents multiple appointments from booking the same availability slot.
