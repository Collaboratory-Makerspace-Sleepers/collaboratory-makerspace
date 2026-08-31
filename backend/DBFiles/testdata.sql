-- test data so far
INSERT INTO accounts (username, role_name, membership_name, email, phone, firstName, lastName)
VALUES
('admin1', 'superadmin', 'monthly', 'admin1@example.com', '555-1111', 'Alice', 'Morgan'),
('staff_john', 'staff', 'monthly', 'john.staff@example.com', '555-2222', 'John', 'Reed'),
('inst_sara', 'instructor', 'student', 'sara.inst@example.com', '555-3333', 'Sara', 'Kim'),
('user_mike', 'user', 'daypass', 'mike.user@example.com', '555-4444', 'Mike', 'Lopez'),
('user_jane', 'user', 'monthly', 'jane.user@example.com', '555-5555', 'Jane', 'Turner');
-- can add or change types later
INSERT INTO user_logs (user_id, action)
VALUES
(1, 'Logged in'),
(2, 'Checked equipment inventory'),
(3, 'Scheduled class'),
(4, 'Booked equipment'),
(5, 'Updated profile');

INSERT INTO classes (class_time, instructor_id)
VALUES
('2026-09-01 10:00:00', 3),
('2026-09-02 14:00:00', 3),
('2026-09-03 09:00:00', 3);

INSERT INTO equipment (name, category, purchase_date, status, calibration_date)
VALUES
('Laser Cutter A', 'fabrication', '2025-01-10', 'available', '2026-07-01'),
('3D Printer B', 'printing', '2024-11-05', 'in_use', '2026-06-15'),
('CNC Router C', 'fabrication', '2023-08-20', 'under_maintenance', '2026-05-10');

INSERT INTO rooms (name, category, purchase_date, status)
VALUES
('Woodshop', 'fabrication', '2023-02-15', 'available'),
('Electronics Lab', 'electronics', '2024-03-22', 'in_use'),
('Classroom 1', 'instruction', '2025-06-10', 'available');

-- equip bookings, do not overla
INSERT INTO equipment_bookings (account_id, equipment_id, start_time, end_time)
VALUES
(4, 1, '2026-09-01 10:00:00', '2026-09-01 11:00:00'),
(5, 2, '2026-09-01 12:00:00', '2026-09-01 13:00:00');
-- room bookings, do not overlap
INSERT INTO room_bookings (account_id, room_id, start_time, end_time)
VALUES
(1, 1, '2026-09-02 09:00:00', '2026-09-02 10:00:00'),
(2, 3, '2026-09-02 11:00:00', '2026-09-02 12:00:00');
