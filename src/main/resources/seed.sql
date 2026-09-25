INSERT INTO users (first_name, last_name, email, password, role) VALUES
    ('Jada', 'Nguyen', 'jada.nguyen@example.com', 'password123', 'CUSTOMER'),
    ('Alex', 'Rivera', 'alex.rivera@example.com', 'password123', 'CUSTOMER'),
    ('Morgan', 'Lee', 'morgan.lee@example.com', 'password123', 'ADMIN');

INSERT INTO providers (name, email, phone) VALUES
    ('Downtown Auto Care', 'service@downtownautocare.example.com', '408-555-0101'),
    ('Westside Tire and Inspection', 'hello@westsidetire.example.com', '408-555-0112');

INSERT INTO services (name, description, duration_minutes) VALUES
    ('Oil Change', 'Engine oil and filter replacement with basic fluid check.', 45),
    ('Vehicle Inspection', 'Safety and maintenance inspection for common vehicle systems.', 60),
    ('Vehicle Diagnostics', 'Diagnostic scan and technician review for warning lights or performance issues.', 90),
    ('Tire Service', 'Tire rotation, pressure check, and tread inspection.', 45);

INSERT INTO availability_slots (provider_id, service_id, start_time, end_time, is_available) VALUES
    (1, 1, '2026-10-05 09:00:00', '2026-10-05 09:45:00', TRUE),
    (1, 2, '2026-10-05 10:30:00', '2026-10-05 11:30:00', TRUE),
    (1, 3, '2026-10-06 13:00:00', '2026-10-06 14:30:00', TRUE),
    (2, 4, '2026-10-07 09:30:00', '2026-10-07 10:15:00', TRUE),
    (2, 2, '2026-10-07 11:00:00', '2026-10-07 12:00:00', TRUE),
    (2, 1, '2026-10-08 15:00:00', '2026-10-08 15:45:00', FALSE);

INSERT INTO appointments (user_id, provider_id, service_id, slot_id, status, created_at) VALUES
    (1, 2, 1, 6, 'BOOKED', '2026-09-24 20:30:00');
