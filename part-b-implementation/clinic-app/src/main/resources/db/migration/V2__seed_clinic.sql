-- V2: seed a small clinic so the dashboard and the simulation have something to work with.
-- Departments follow the project book (general medicine, pediatrics, orthopedics, emergency).
-- Every department has one INACTIVE room so the agent's OPEN_ROOM tool has something to open.
-- The "ord" column keeps ids in list order (insert-select does not guarantee it otherwise).

insert into departments (name, max_queue, warning_threshold, overload_threshold) values
    ('General Medicine', 20, 6, 12),
    ('Pediatrics',       15, 5, 10),
    ('Orthopedics',      12, 4,  8),
    ('Emergency',        25, 5, 10);

insert into treatment_rooms (department_id, name, status)
select d.id, r.name, r.status
from departments d
join (values
    ( 1, 'General Medicine', 'GM-1', 'AVAILABLE'),
    ( 2, 'General Medicine', 'GM-2', 'AVAILABLE'),
    ( 3, 'General Medicine', 'GM-3', 'AVAILABLE'),
    ( 4, 'General Medicine', 'GM-4', 'INACTIVE'),
    ( 5, 'Pediatrics',       'PD-1', 'AVAILABLE'),
    ( 6, 'Pediatrics',       'PD-2', 'AVAILABLE'),
    ( 7, 'Pediatrics',       'PD-3', 'INACTIVE'),
    ( 8, 'Orthopedics',      'OR-1', 'AVAILABLE'),
    ( 9, 'Orthopedics',      'OR-2', 'AVAILABLE'),
    (10, 'Orthopedics',      'OR-3', 'INACTIVE'),
    (11, 'Emergency',        'ER-1', 'AVAILABLE'),
    (12, 'Emergency',        'ER-2', 'AVAILABLE'),
    (13, 'Emergency',        'ER-3', 'AVAILABLE'),
    (14, 'Emergency',        'ER-4', 'INACTIVE')
) as r(ord, department, name, status) on r.department = d.name
order by r.ord;

insert into doctors (department_id, name, available, max_concurrent_patients)
select d.id, x.name, true, x.max_concurrent
from departments d
join (values
    ( 1, 'General Medicine', 'Dr. Levi',     2),
    ( 2, 'General Medicine', 'Dr. Cohen',    2),
    ( 3, 'General Medicine', 'Dr. Mizrahi',  1),
    ( 4, 'Pediatrics',       'Dr. Peretz',   2),
    ( 5, 'Pediatrics',       'Dr. Biton',    1),
    ( 6, 'Orthopedics',      'Dr. Avraham',  1),
    ( 7, 'Orthopedics',      'Dr. Friedman', 1),
    ( 8, 'Emergency',        'Dr. Dahan',    2),
    ( 9, 'Emergency',        'Dr. Shapira',  2),
    (10, 'Emergency',        'Dr. Katz',     2)
) as x(ord, department, name, max_concurrent) on x.department = d.name
order by x.ord;

insert into medications (name, stock, threshold) values
    ('Paracetamol 500mg',   400, 100),
    ('Ibuprofen 400mg',     300,  80),
    ('Amoxicillin 500mg',   150,  50),
    ('Saline 0.9% 500ml',   120,  40),
    ('Adrenaline 1mg',       40,  15),
    ('Insulin (rapid)',      60,  20),
    ('Salbutamol inhaler',   50,  15),
    ('Ondansetron 4mg',      80,  25);

-- Development users, one per role. Password is "<username>123" (bcrypt, cost 10).
-- Rotate or remove these before any shared deployment.
insert into users (username, password_hash, role) values
    ('manager',   '$2y$10$UiV0jWPhR0rff/fL4nHU2eguuLHp3e6zGEQs6w5295u1oh4XS.0EG', 'CLINIC_MANAGER'),
    ('reception', '$2y$10$uUZUn4O3en0ge8NiN8VoXuUzHD47wldTBI7CQr4D.ATjEOyGnS2kS', 'RECEPTION'),
    ('staff',     '$2y$10$s23qhbFmjLDT0dZbXeY/luXAR8dXwfxyjISqYAhB29SUooPblJBuy', 'MEDICAL_STAFF');
