ALTER TABLE room_types DROP COLUMN IF EXISTS ceiling_measurement_required;
ALTER TABLE room_types DROP COLUMN IF EXISTS wall_measurement_required;
ALTER TABLE room_types DROP COLUMN IF EXISTS floor_measurement_required;

DROP TABLE IF EXISTS room_type_work_items;
