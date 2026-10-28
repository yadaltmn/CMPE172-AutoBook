-- Demo data for AutoBook (PostgreSQL). Flyway applies it once, right after the schema.
-- All seeded passwords are the development-only password "password123" (BCrypt hashed).
INSERT INTO users (first_name, last_name, email, password, role)
VALUES
    ('Jada', 'Nguyen', 'jada.nguyen@example.com',
     '$2a$10$d8A/7t3fhv0uxuSlwJ5N8uUcD3SjUW/2HbuzMwG2Da70N6MZcr5dG', 'CUSTOMER'),

    ('Alex', 'Rivera', 'alex.rivera@example.com',
     '$2a$10$d8A/7t3fhv0uxuSlwJ5N8uUcD3SjUW/2HbuzMwG2Da70N6MZcr5dG', 'CUSTOMER'),

    ('Morgan', 'Lee', 'morgan.lee@example.com',
     '$2a$10$d8A/7t3fhv0uxuSlwJ5N8uUcD3SjUW/2HbuzMwG2Da70N6MZcr5dG', 'ADMIN'),

    ('Sam', 'Patel', 'service@downtownautocare.example.com',
     '$2a$10$d8A/7t3fhv0uxuSlwJ5N8uUcD3SjUW/2HbuzMwG2Da70N6MZcr5dG', 'PROVIDER'),

    ('Riley', 'Chen', 'hello@westsidetire.example.com',
     '$2a$10$d8A/7t3fhv0uxuSlwJ5N8uUcD3SjUW/2HbuzMwG2Da70N6MZcr5dG', 'PROVIDER');

INSERT INTO providers (user_id, name, email, phone) VALUES
    (4, 'Downtown Auto Care', 'service@downtownautocare.example.com', '408-555-0101'),
    (5, 'Westside Tire and Inspection', 'hello@westsidetire.example.com', '408-555-0112');

INSERT INTO services (name, description, duration_minutes) VALUES
    ('Oil Change', 'Engine oil and filter replacement with basic fluid check.', 45),
    ('Vehicle Inspection', 'Safety and maintenance inspection for common vehicle systems.', 60),
    ('Vehicle Diagnostics', 'Diagnostic scan and technician review for warning lights or performance issues.', 90),
    ('Tire Service', 'Tire rotation, pressure check, and tread inspection.', 45);

-- Slot times are relative to the day this migration first runs (the first start against an empty database).
-- Later restarts keep the existing data instead of re-seeding it.
INSERT INTO availability_slots (provider_id, service_id, start_time, end_time, is_available) VALUES
    (1, 1, CAST(CURRENT_DATE AS TIMESTAMP) + INTERVAL '1 days 540 minutes',
           CAST(CURRENT_DATE AS TIMESTAMP) + INTERVAL '1 days 585 minutes', TRUE),
    (1, 2, CAST(CURRENT_DATE AS TIMESTAMP) + INTERVAL '1 days 630 minutes',
           CAST(CURRENT_DATE AS TIMESTAMP) + INTERVAL '1 days 690 minutes', TRUE),
    (1, 3, CAST(CURRENT_DATE AS TIMESTAMP) + INTERVAL '2 days 780 minutes',
           CAST(CURRENT_DATE AS TIMESTAMP) + INTERVAL '2 days 870 minutes', TRUE),
    (2, 4, CAST(CURRENT_DATE AS TIMESTAMP) + INTERVAL '3 days 570 minutes',
           CAST(CURRENT_DATE AS TIMESTAMP) + INTERVAL '3 days 615 minutes', TRUE),
    (2, 2, CAST(CURRENT_DATE AS TIMESTAMP) + INTERVAL '3 days 660 minutes',
           CAST(CURRENT_DATE AS TIMESTAMP) + INTERVAL '3 days 720 minutes', TRUE),
    (2, 1, CAST(CURRENT_DATE AS TIMESTAMP) + INTERVAL '4 days 900 minutes',
           CAST(CURRENT_DATE AS TIMESTAMP) + INTERVAL '4 days 945 minutes', FALSE),
    -- Past slots that hold appointment history.
    (1, 1, CAST(CURRENT_DATE AS TIMESTAMP) + INTERVAL '-10 days 600 minutes',
           CAST(CURRENT_DATE AS TIMESTAMP) + INTERVAL '-10 days 645 minutes', FALSE),
    (2, 4, CAST(CURRENT_DATE AS TIMESTAMP) + INTERVAL '-5 days 840 minutes',
           CAST(CURRENT_DATE AS TIMESTAMP) + INTERVAL '-5 days 885 minutes', FALSE),
    -- Upcoming slot already booked by a second customer.
    (1, 2, CAST(CURRENT_DATE AS TIMESTAMP) + INTERVAL '5 days 600 minutes',
           CAST(CURRENT_DATE AS TIMESTAMP) + INTERVAL '5 days 660 minutes', FALSE);

INSERT INTO appointments (user_id, provider_id, service_id, slot_id, status, created_at) VALUES
    (1, 2, 1, 6, 'BOOKED', LOCALTIMESTAMP + INTERVAL '-1 days'),
    (1, 1, 1, 7, 'COMPLETED', LOCALTIMESTAMP + INTERVAL '-14 days'),
    (1, 2, 4, 8, 'CANCELLED', LOCALTIMESTAMP + INTERVAL '-8 days'),
    (2, 1, 2, 9, 'BOOKED', LOCALTIMESTAMP + INTERVAL '-2 days');
