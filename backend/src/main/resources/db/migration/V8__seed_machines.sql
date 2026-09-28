-- Named machine inventory for the Collaboratory Makerspace.
-- Guarded with WHERE NOT EXISTS so re-runs are no-ops.

-- 3D Printers
INSERT INTO equipment (name, description, category, image_url, status)
SELECT 'Rachel', 'FDM 3D printer', '3D_PRINTER', 'https://picsum.photos/seed/rachel/480/360', 'AVAILABLE'
WHERE NOT EXISTS (SELECT 1 FROM equipment WHERE name = 'Rachel');

INSERT INTO equipment (name, description, category, image_url, status)
SELECT 'Phoebe', 'FDM 3D printer', '3D_PRINTER', 'https://picsum.photos/seed/phoebe/480/360', 'AVAILABLE'
WHERE NOT EXISTS (SELECT 1 FROM equipment WHERE name = 'Phoebe');

INSERT INTO equipment (name, description, category, image_url, status)
SELECT 'Monica', 'FDM 3D printer', '3D_PRINTER', 'https://picsum.photos/seed/monica/480/360', 'AVAILABLE'
WHERE NOT EXISTS (SELECT 1 FROM equipment WHERE name = 'Monica');

-- Laser engraver
INSERT INTO equipment (name, description, category, image_url, status)
SELECT 'Darth Vader', 'CO2 laser engraver', 'LASER', 'https://picsum.photos/seed/darthvader/480/360', 'AVAILABLE'
WHERE NOT EXISTS (SELECT 1 FROM equipment WHERE name = 'Darth Vader');

-- Metal lathe
INSERT INTO equipment (name, description, category, image_url, status)
SELECT 'Kirk', 'Metal lathe', 'METAL_LATHE', 'https://picsum.photos/seed/kirk/480/360', 'AVAILABLE'
WHERE NOT EXISTS (SELECT 1 FROM equipment WHERE name = 'Kirk');

-- Wood lathe
INSERT INTO equipment (name, description, category, image_url, status)
SELECT 'Spock', 'Wood lathe', 'WOOD_LATHE', 'https://picsum.photos/seed/spock/480/360', 'AVAILABLE'
WHERE NOT EXISTS (SELECT 1 FROM equipment WHERE name = 'Spock');

-- CNCs
INSERT INTO equipment (name, description, category, image_url, status)
SELECT 'Joey', 'Large-format CNC router', 'CNC_LARGE', 'https://picsum.photos/seed/joey/480/360', 'AVAILABLE'
WHERE NOT EXISTS (SELECT 1 FROM equipment WHERE name = 'Joey');

INSERT INTO equipment (name, description, category, image_url, status)
SELECT 'Ross', 'Small CNC router', 'CNC_SMALL', 'https://picsum.photos/seed/ross/480/360', 'AVAILABLE'
WHERE NOT EXISTS (SELECT 1 FROM equipment WHERE name = 'Ross');

INSERT INTO equipment (name, description, category, image_url, status)
SELECT 'Chandler', 'Small CNC router', 'CNC_SMALL', 'https://picsum.photos/seed/chandler/480/360', 'AVAILABLE'
WHERE NOT EXISTS (SELECT 1 FROM equipment WHERE name = 'Chandler');

-- Small maker studios
INSERT INTO equipment (name, description, category, image_url, status)
SELECT 'Harkonnen', 'Small maker studio', 'STUDIO_SMALL', 'https://picsum.photos/seed/harkonnen/480/360', 'AVAILABLE'
WHERE NOT EXISTS (SELECT 1 FROM equipment WHERE name = 'Harkonnen');

INSERT INTO equipment (name, description, category, image_url, status)
SELECT 'Atreides', 'Small maker studio', 'STUDIO_SMALL', 'https://picsum.photos/seed/atreides/480/360', 'AVAILABLE'
WHERE NOT EXISTS (SELECT 1 FROM equipment WHERE name = 'Atreides');

INSERT INTO equipment (name, description, category, image_url, status)
SELECT 'Corrine', 'Small maker studio', 'STUDIO_SMALL', 'https://picsum.photos/seed/corrine/480/360', 'AVAILABLE'
WHERE NOT EXISTS (SELECT 1 FROM equipment WHERE name = 'Corrine');

-- Medium maker studios
INSERT INTO equipment (name, description, category, image_url, status)
SELECT 'Coruscant', 'Medium maker studio', 'STUDIO_MED', 'https://picsum.photos/seed/coruscant/480/360', 'AVAILABLE'
WHERE NOT EXISTS (SELECT 1 FROM equipment WHERE name = 'Coruscant');

INSERT INTO equipment (name, description, category, image_url, status)
SELECT 'Alderaan', 'Medium maker studio', 'STUDIO_MED', 'https://picsum.photos/seed/alderaan/480/360', 'AVAILABLE'
WHERE NOT EXISTS (SELECT 1 FROM equipment WHERE name = 'Alderaan');

INSERT INTO equipment (name, description, category, image_url, status)
SELECT 'Corellia', 'Medium maker studio', 'STUDIO_MED', 'https://picsum.photos/seed/corellia/480/360', 'AVAILABLE'
WHERE NOT EXISTS (SELECT 1 FROM equipment WHERE name = 'Corellia');

-- Large maker studios
INSERT INTO equipment (name, description, category, image_url, status)
SELECT 'Paris', 'Large maker studio', 'STUDIO_LARGE', 'https://picsum.photos/seed/paris/480/360', 'AVAILABLE'
WHERE NOT EXISTS (SELECT 1 FROM equipment WHERE name = 'Paris');

INSERT INTO equipment (name, description, category, image_url, status)
SELECT 'Big Apple', 'Large maker studio', 'STUDIO_LARGE', 'https://picsum.photos/seed/bigapple/480/360', 'AVAILABLE'
WHERE NOT EXISTS (SELECT 1 FROM equipment WHERE name = 'Big Apple');