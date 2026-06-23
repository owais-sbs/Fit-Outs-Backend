-- V7 dropped this table; Hibernate ddl-auto=update may recreate it from RoomType.workItems
-- before V8 runs. Use IF NOT EXISTS so migration succeeds either way.

CREATE TABLE IF NOT EXISTS room_type_work_items (
    room_type_id UUID NOT NULL REFERENCES room_types(id) ON DELETE CASCADE,
    work_item_id UUID NOT NULL REFERENCES work_items(id) ON DELETE CASCADE,
    PRIMARY KEY (room_type_id, work_item_id)
);

CREATE INDEX IF NOT EXISTS idx_room_type_work_items_room_type ON room_type_work_items(room_type_id);
CREATE INDEX IF NOT EXISTS idx_room_type_work_items_work_item ON room_type_work_items(work_item_id);
