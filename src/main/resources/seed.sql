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

-- Slot times are relative to the day the application starts so the demo always has upcoming slots.
INSERT INTO availability_slots (provider_id, service_id, start_time, end_time, is_available) VALUES
    (1, 1, DATEADD('MINUTE', 540, DATEADD('DAY', 1, CAST(CURRENT_DATE AS TIMESTAMP))),
           DATEADD('MINUTE', 585, DATEADD('DAY', 1, CAST(CURRENT_DATE AS TIMESTAMP))), TRUE),
    (1, 2, DATEADD('MINUTE', 630, DATEADD('DAY', 1, CAST(CURRENT_DATE AS TIMESTAMP))),
           DATEADD('MINUTE', 690, DATEADD('DAY', 1, CAST(CURRENT_DATE AS TIMESTAMP))), TRUE),
    (1, 3, DATEADD('MINUTE', 780, DATEADD('DAY', 2, CAST(CURRENT_DATE AS TIMESTAMP))),
           DATEADD('MINUTE', 870, DATEADD('DAY', 2, CAST(CURRENT_DATE AS TIMESTAMP))), TRUE),
    (2, 4, DATEADD('MINUTE', 570, DATEADD('DAY', 3, CAST(CURRENT_DATE AS TIMESTAMP))),
           DATEADD('MINUTE', 615, DATEADD('DAY', 3, CAST(CURRENT_DATE AS TIMESTAMP))), TRUE),
    (2, 2, DATEADD('MINUTE', 660, DATEADD('DAY', 3, CAST(CURRENT_DATE AS TIMESTAMP))),
           DATEADD('MINUTE', 720, DATEADD('DAY', 3, CAST(CURRENT_DATE AS TIMESTAMP))), TRUE),
    (2, 1, DATEADD('MINUTE', 900, DATEADD('DAY', 4, CAST(CURRENT_DATE AS TIMESTAMP))),
           DATEADD('MINUTE', 945, DATEADD('DAY', 4, CAST(CURRENT_DATE AS TIMESTAMP))), FALSE),
    -- Past slots that hold appointment history.
    (1, 1, DATEADD('MINUTE', 600, DATEADD('DAY', -10, CAST(CURRENT_DATE AS TIMESTAMP))),
           DATEADD('MINUTE', 645, DATEADD('DAY', -10, CAST(CURRENT_DATE AS TIMESTAMP))), FALSE),
    (2, 4, DATEADD('MINUTE', 840, DATEADD('DAY', -5, CAST(CURRENT_DATE AS TIMESTAMP))),
           DATEADD('MINUTE', 885, DATEADD('DAY', -5, CAST(CURRENT_DATE AS TIMESTAMP))), FALSE),
    -- Upcoming slot already booked by a second customer.
    (1, 2, DATEADD('MINUTE', 600, DATEADD('DAY', 5, CAST(CURRENT_DATE AS TIMESTAMP))),
           DATEADD('MINUTE', 660, DATEADD('DAY', 5, CAST(CURRENT_DATE AS TIMESTAMP))), FALSE);

INSERT INTO appointments (user_id, provider_id, service_id, slot_id, status, created_at) VALUES
    (1, 2, 1, 6, 'BOOKED', DATEADD('DAY', -1, CURRENT_TIMESTAMP)),
    (1, 1, 1, 7, 'COMPLETED', DATEADD('DAY', -14, CURRENT_TIMESTAMP)),
    (1, 2, 4, 8, 'CANCELLED', DATEADD('DAY', -8, CURRENT_TIMESTAMP)),
    (2, 1, 2, 9, 'BOOKED', DATEADD('DAY', -2, CURRENT_TIMESTAMP));
