-- initializes enum types before the schema
CREATE TYPE user_role AS ENUM ('superadmin', 'staff', 'instructor', 'user');
CREATE TYPE membership AS ENUM ('monthly', 'daypass', 'student', 'none');
CREATE TYPE current_status AS ENUM ('available', 'in_use', 'under_maintenance');