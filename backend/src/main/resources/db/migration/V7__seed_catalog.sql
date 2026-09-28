-- Seed the catalog: equipment, bookable classes, and membership plans.
-- Each insert is guarded (WHERE NOT EXISTS / ON CONFLICT) so re-runs are no-ops.

-- Equipment to rent
INSERT INTO equipment (name, description, category, image_url, status)
SELECT 'Laser Cutter', 'CO2 laser cutter for acrylic, wood and leather', 'LASER',
       'https://picsum.photos/seed/laser/480/360', 'AVAILABLE'
WHERE NOT EXISTS (SELECT 1 FROM equipment WHERE name = 'Laser Cutter');

INSERT INTO equipment (name, description, category, image_url, status)
SELECT '3D Printer', 'FDM filament printer, 0.4mm nozzle', '3D',
       'https://picsum.photos/seed/3d-printer/480/360', 'AVAILABLE'
WHERE NOT EXISTS (SELECT 1 FROM equipment WHERE name = '3D Printer');

INSERT INTO equipment (name, description, category, image_url, status)
SELECT 'CNC Router', 'Large-format CNC routing table', 'CNC',
       'https://picsum.photos/seed/cnc/480/360', 'AVAILABLE'
WHERE NOT EXISTS (SELECT 1 FROM equipment WHERE name = 'CNC Router');

INSERT INTO equipment (name, description, category, image_url, status)
SELECT 'Soldering Station', 'Hot air and soldering iron station', 'ELECTRONICS',
       'https://picsum.photos/seed/soldering/480/360', 'AVAILABLE'
WHERE NOT EXISTS (SELECT 1 FROM equipment WHERE name = 'Soldering Station');

INSERT INTO equipment (name, description, category, image_url, status)
SELECT 'Table Saw', 'Woodshop table saw with safety guard', 'WOODSHOP',
       'https://picsum.photos/seed/table-saw/480/360', 'AVAILABLE'
WHERE NOT EXISTS (SELECT 1 FROM equipment WHERE name = 'Table Saw');

INSERT INTO equipment (name, description, category, image_url, status)
SELECT 'Sewing Machine', 'Basic sewing machine', 'SEWING',
       'https://picsum.photos/seed/sewing/480/360', 'AVAILABLE'
WHERE NOT EXISTS (SELECT 1 FROM equipment WHERE name = 'Sewing Machine');

-- Bookable classes (equipment rows with category CLASS)
INSERT INTO equipment (name, description, category, image_url, status)
SELECT 'Laser Cutting Intro', 'Learn the basics of safe laser cutting', 'CLASS',
       'https://picsum.photos/seed/class-laser/480/360', 'AVAILABLE'
WHERE NOT EXISTS (SELECT 1 FROM equipment WHERE name = 'Laser Cutting Intro');

INSERT INTO equipment (name, description, category, image_url, status)
SELECT '3D Printing Basics', 'Learn slicers and filament printing', 'CLASS',
       'https://picsum.photos/seed/class-3d/480/360', 'AVAILABLE'
WHERE NOT EXISTS (SELECT 1 FROM equipment WHERE name = '3D Printing Basics');

INSERT INTO equipment (name, description, category, image_url, status)
SELECT 'Woodshop Safety', 'Required safety certification for the woodshop', 'CLASS',
       'https://picsum.photos/seed/class-wood/480/360', 'AVAILABLE'
WHERE NOT EXISTS (SELECT 1 FROM equipment WHERE name = 'Woodshop Safety');

-- Membership plans. Stripe hooks are placeholders until billing is wired up.
INSERT INTO membership_plan
    (code, display_name, stripe_price_id, stripe_product_id, billing_interval, amount_cents, currency, grants_role, active)
VALUES
    ('MONTHLY',  'Monthly',  'price_demo_monthly',  'prod_demo_membership', 'MONTH', 2500,  'usd', 'MEMBER',  TRUE),
    ('ANNUAL',   'Annual',   'price_demo_annual',   'prod_demo_membership', 'YEAR',  25000, 'usd', 'MEMBER',  TRUE),
    ('STUDENT',  'Student',  'price_demo_student',  'prod_demo_membership', 'MONTH', 1500,  'usd', 'STUDENT', TRUE),
    ('DAY_PASS', 'Day Pass', 'price_demo_day_pass', 'prod_demo_membership', NULL,    1000,  'usd', 'RENTEE',  TRUE)
ON CONFLICT (code) DO NOTHING;