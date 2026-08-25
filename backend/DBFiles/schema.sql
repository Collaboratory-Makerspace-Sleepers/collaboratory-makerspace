CREATE EXTENSION IF NOT EXISTS btree_gist;
-- some fixes - Sarina
-- fixed type error with gist
-- changes
-- no need for dedicated user rn
-- changed check names so they do not overlap
-- changed to tsrange from tstzrange because of an immutable error inside the gist index
-- changed auto_increment to SERIAL 
 
CREATE SCHEMA IF NOT EXISTS makerspace;


-- id: unique user id 
-- username the login username for the user
-- passHash the password hash stored here, never plain text
-- roleID the role of the user
--  Guest: users with no account yet
--  SuperAdmin: highest level admin
--  Staff: staff admin
--  Instructor: instructor
--  Generaluser: basic account with no administrative access, membership will be another var
-- Membership

-- moved to a file to create seperately init.sql
/*
CREATE TYPE user_role AS ENUM ('superadmin', 'staff', 'instructor', 'user');
CREATE TYPE membership AS ENUM ('monthly', 'daypass', 'student', 'none');
CREATE TYPE current_status AS ENUM ('available', 'in_use', 'under_maintenance');
*/

-- table for all accounts including login details, their role and membership
-- notes: use enums
-- for user logs, do not cascade, soft delete
-- add deleted on by default null timestamp, do not cascade delete user
-- 
    -- ACCOUNTS TABLE
    CREATE TABLE IF NOT EXISTS accounts (
        id SERIAL PRIMARY KEY,
        username VARCHAR(50) UNIQUE NOT NULL,
        role_name user_role NOT NULL,
        membership_name membership NOT NULL,
        email VARCHAR(100),
        phone VARCHAR(20),
        firstName VARCHAR(50),
        lastName VARCHAR(50),
        created_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        deleted_on TIMESTAMP DEFAULT NULL
    );

    -- USER LOGS
    CREATE TABLE IF NOT EXISTS user_logs (
        log_id SERIAL PRIMARY KEY,
        user_id INT NOT NULL REFERENCES accounts(id),
        action VARCHAR(100) NOT NULL,
        log_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );

-- CLASSES
    CREATE TABLE IF NOT EXISTS classes (
        class_id SERIAL PRIMARY KEY,
        class_time TIMESTAMP,
        instructor_id INT REFERENCES accounts(id) ON DELETE SET NULL
    );

    -- equipment
    CREATE TABLE IF NOT EXISTS equipment(
        equipment_id SERIAL PRIMARY KEY,
        name VARCHAR(100) NOT NULL,
        category VARCHAR(50), --adds a way for users to search for avail equip by category
        purchase_date DATE,
        status current_status NOT NULL DEFAULT 'available',
        calibration_date DATE
    );
    -- rooms
    CREATE TABLE IF NOT EXISTS rooms (
        room_id SERIAL PRIMARY KEY,
        name VARCHAR(100) NOT NULL,
        category VARCHAR(50), --adds a way for users to search for avail rooms by category like size, might change this later
        purchase_date DATE,
        status current_status NOT NULL DEFAULT 'available' 
    );
   -- Equipment Bookings table: manages scheduling and usage of equipment
    CREATE TABLE IF NOT EXISTS equipment_bookings (
        booking_id SERIAL PRIMARY KEY,
        account_id INT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
        equipment_id INT NOT NULL REFERENCES equipment(equipment_id) ON DELETE CASCADE,
        start_time TIMESTAMP NOT NULL,
        end_time TIMESTAMP NOT NULL,
        CONSTRAINT booking_time_check CHECK (end_time > start_time),
        -- prevents overlap 
        -- The EXCLUDE constraint ensures that certain combinations of column values do not conflict according to specified operators.
        -- USING gist specifies the index type. GiST (Generalized Search Tree)
        -- equipment_id WITH =: Ensures that the constraint is applied separately for each piece of equipment. 
        -- tsrange(start_time, end_time) WITH &&: Uses PostgreSQL's range type (timestamp) to represent the booking period. 
        -- the && operator checks for overlap between ranges.
        CONSTRAINT booking_overlap_check_equip EXCLUDE USING gist (
            equipment_id WITH =,
            tsrange(start_time, end_time) WITH &&
        )
);
   -- Equipment Bookings table: manages scheduling and usage of equipment
    CREATE TABLE IF NOT EXISTS room_bookings (
        booking_id SERIAL PRIMARY KEY,
        account_id INT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
        room_id INT NOT NULL REFERENCES rooms(room_id) ON DELETE CASCADE,
        start_time TIMESTAMP NOT NULL,
        end_time TIMESTAMP NOT NULL,
        CONSTRAINT booking_time_check CHECK (end_time > start_time),
        CONSTRAINT booking_overlap_check_room EXCLUDE USING gist (
            room_id WITH =,
            tsrange(start_time, end_time) WITH && 
        ) 
);