-- Site visit category/room fields + JCT Renovation Checklist seed from prototype PDF

ALTER TABLE checklist_template_items
    ADD COLUMN IF NOT EXISTS room_name VARCHAR(120);

ALTER TABLE site_visits
    ADD COLUMN IF NOT EXISTS checklist_template_uuid UUID,
    ADD COLUMN IF NOT EXISTS categories TEXT,
    ADD COLUMN IF NOT EXISTS rooms TEXT;

DO $$
DECLARE
    v_company_id UUID;
    v_template_id UUID;
    v_ord INTEGER;
BEGIN
    FOR v_company_id IN SELECT uuid FROM companies LOOP
        SELECT uuid INTO v_template_id
        FROM checklist_templates
        WHERE company_id = v_company_id
          AND name = 'JCT Renovation Checklist'
        LIMIT 1;

        IF v_template_id IS NULL THEN
            INSERT INTO checklist_templates (uuid, name, description, created_by, created_at, updated_at, company_id)
            VALUES (
                gen_random_uuid(),
                'JCT Renovation Checklist',
                'Seeded from JCT Renovation Prototype checklist (scope sections A–M).',
                NULL,
                NOW(),
                NOW(),
                v_company_id
            )
            RETURNING uuid INTO v_template_id;
        END IF;

        -- Skip item seed if template already has items
        IF EXISTS (SELECT 1 FROM checklist_template_items WHERE template_uuid = v_template_id) THEN
            CONTINUE;
        END IF;

        v_ord := 0;

        -- Helper via temporary insert of all items
        INSERT INTO checklist_template_items (
            uuid, template_uuid, section_name, room_name, question, type, is_required, display_order, created_at, updated_at
        )
        SELECT
            gen_random_uuid(),
            v_template_id,
            v.section_name,
            v.room_name,
            v.question,
            v.item_type,
            v.is_required,
            v.display_order,
            NOW(),
            NOW()
        FROM (VALUES
            -- A Flooring & Skirting — Ground Floor
            ('Flooring & Skirting', 'Ground Floor', 'Flooring: Retain / No Modification', 'CHECKBOX', FALSE, 10),
            ('Flooring & Skirting', 'Ground Floor', 'Flooring: Tile (size: _______)', 'CHECKBOX', FALSE, 11),
            ('Flooring & Skirting', 'Ground Floor', 'Flooring: SPC flooring', 'CHECKBOX', FALSE, 12),
            ('Flooring & Skirting', 'Ground Floor', 'Flooring: Tile on top', 'CHECKBOX', FALSE, 13),
            ('Flooring & Skirting', 'Ground Floor', 'Flooring: Removal of Existing', 'CHECKBOX', FALSE, 14),
            ('Flooring & Skirting', 'Ground Floor', 'Flooring: Others / Specific Item', 'TEXT', FALSE, 15),
            ('Flooring & Skirting', 'Ground Floor', 'Skirting: Retain / No Modification', 'CHECKBOX', FALSE, 16),
            ('Flooring & Skirting', 'Ground Floor', 'Skirting: MDF Skirting', 'CHECKBOX', FALSE, 17),
            ('Flooring & Skirting', 'Ground Floor', 'Skirting: Tile Skirting', 'CHECKBOX', FALSE, 18),
            ('Flooring & Skirting', 'Ground Floor', 'Skirting: Client to Supplied', 'CHECKBOX', FALSE, 19),
            ('Flooring & Skirting', 'Ground Floor', 'Skirting: Removal and Repair Only', 'CHECKBOX', FALSE, 20),
            ('Flooring & Skirting', 'Ground Floor', 'Skirting: Others (Marble, Engineered wood, Microcement, etc.)', 'TEXT', FALSE, 21),
            -- A Flooring & Skirting — First Floor
            ('Flooring & Skirting', 'First Floor', 'Flooring: Retain / No Modification', 'CHECKBOX', FALSE, 30),
            ('Flooring & Skirting', 'First Floor', 'Flooring: Tile (size: _______)', 'CHECKBOX', FALSE, 31),
            ('Flooring & Skirting', 'First Floor', 'Flooring: SPC flooring', 'CHECKBOX', FALSE, 32),
            ('Flooring & Skirting', 'First Floor', 'Flooring: Tile on top', 'CHECKBOX', FALSE, 33),
            ('Flooring & Skirting', 'First Floor', 'Flooring: Removal of Existing', 'CHECKBOX', FALSE, 34),
            ('Flooring & Skirting', 'First Floor', 'Flooring: Others / Specific Item', 'TEXT', FALSE, 35),
            ('Flooring & Skirting', 'First Floor', 'Skirting: Retain / No Modification', 'CHECKBOX', FALSE, 36),
            ('Flooring & Skirting', 'First Floor', 'Skirting: MDF Skirting', 'CHECKBOX', FALSE, 37),
            ('Flooring & Skirting', 'First Floor', 'Skirting: Tile Skirting', 'CHECKBOX', FALSE, 38),
            ('Flooring & Skirting', 'First Floor', 'Skirting: Client to Supplied', 'CHECKBOX', FALSE, 39),
            ('Flooring & Skirting', 'First Floor', 'Skirting: Removal and Repair Only', 'CHECKBOX', FALSE, 40),
            ('Flooring & Skirting', 'First Floor', 'Skirting: Others (Marble, Engineered wood, Microcement, etc.)', 'TEXT', FALSE, 41),
            -- B Staircase
            ('Staircase Flooring and Balustrade', 'General', 'Retain Existing Flooring', 'CHECKBOX', FALSE, 50),
            ('Staircase Flooring and Balustrade', 'General', 'New Flooring', 'CHECKBOX', FALSE, 51),
            ('Staircase Flooring and Balustrade', 'General', 'Polishing of Existing Flooring', 'CHECKBOX', FALSE, 52),
            ('Staircase Flooring and Balustrade', 'General', 'Retain Existing Balustrade', 'CHECKBOX', FALSE, 53),
            ('Staircase Flooring and Balustrade', 'General', 'Repainting of Existing Balustrade', 'CHECKBOX', FALSE, 54),
            ('Staircase Flooring and Balustrade', 'General', 'New Balustrade / Specify Type', 'TEXT', FALSE, 55),
            -- C Ceiling
            ('Ceiling Works', 'General', 'Retain / No Modification', 'CHECKBOX', FALSE, 60),
            ('Ceiling Works', 'General', 'New Ceiling Everywhere', 'CHECKBOX', FALSE, 61),
            ('Ceiling Works', 'General', 'Ceiling with Cove Lights', 'CHECKBOX', FALSE, 62),
            ('Ceiling Works', 'General', 'Ceiling with Moulding / Cornice', 'CHECKBOX', FALSE, 63),
            ('Ceiling Works', 'General', 'Regular Ceiling', 'CHECKBOX', FALSE, 64),
            ('Ceiling Works', 'General', 'Renovated Areas Only', 'CHECKBOX', FALSE, 65),
            ('Ceiling Works', 'General', 'Others / Specific Item', 'TEXT', FALSE, 66),
            -- D Painting
            ('Painting Works', 'General', 'Internal', 'CHECKBOX', FALSE, 70),
            ('Painting Works', 'General', 'External', 'CHECKBOX', FALSE, 71),
            ('Painting Works', 'General', 'Others / Please Specify Area', 'TEXT', FALSE, 72),
            -- E Plumbing rooms
            ('Plumbing Works', 'Powder Room', 'Concealed WC', 'CHECKBOX', FALSE, 80),
            ('Plumbing Works', 'Powder Room', 'Concealed Mixers', 'CHECKBOX', FALSE, 81),
            ('Plumbing Works', 'Powder Room', 'Pre-existing Layout', 'CHECKBOX', FALSE, 82),
            ('Plumbing Works', 'Powder Room', 'Others', 'TEXT', FALSE, 83),
            ('Plumbing Works', 'Maid''s Bathroom', 'Concealed WC', 'CHECKBOX', FALSE, 90),
            ('Plumbing Works', 'Maid''s Bathroom', 'Concealed Mixers', 'CHECKBOX', FALSE, 91),
            ('Plumbing Works', 'Maid''s Bathroom', 'Pre-existing Layout', 'CHECKBOX', FALSE, 92),
            ('Plumbing Works', 'Maid''s Bathroom', 'Others', 'TEXT', FALSE, 93),
            ('Plumbing Works', 'Bathroom 1', 'Concealed WC', 'CHECKBOX', FALSE, 100),
            ('Plumbing Works', 'Bathroom 1', 'Concealed Mixers', 'CHECKBOX', FALSE, 101),
            ('Plumbing Works', 'Bathroom 1', 'Pre-existing Layout', 'CHECKBOX', FALSE, 102),
            ('Plumbing Works', 'Bathroom 1', 'Others', 'TEXT', FALSE, 103),
            ('Plumbing Works', 'Bathroom 2', 'Concealed WC', 'CHECKBOX', FALSE, 110),
            ('Plumbing Works', 'Bathroom 2', 'Concealed Mixers', 'CHECKBOX', FALSE, 111),
            ('Plumbing Works', 'Bathroom 2', 'Pre-existing Layout', 'CHECKBOX', FALSE, 112),
            ('Plumbing Works', 'Bathroom 2', 'Others', 'TEXT', FALSE, 113),
            ('Plumbing Works', 'Master Bathroom', 'Concealed WC', 'CHECKBOX', FALSE, 120),
            ('Plumbing Works', 'Master Bathroom', 'Concealed Mixers', 'CHECKBOX', FALSE, 121),
            ('Plumbing Works', 'Master Bathroom', 'Pre-existing Layout', 'CHECKBOX', FALSE, 122),
            ('Plumbing Works', 'Master Bathroom', 'Others', 'TEXT', FALSE, 123),
            ('Plumbing Works', 'Water Heater', 'Retain Existing', 'CHECKBOX', FALSE, 130),
            ('Plumbing Works', 'Water Heater', 'New Normal Waterheater', 'CHECKBOX', FALSE, 131),
            ('Plumbing Works', 'Water Heater', 'Solar Waterheater', 'CHECKBOX', FALSE, 132),
            ('Plumbing Works', 'Water Heater', 'Others', 'TEXT', FALSE, 133),
            -- F Electrical
            ('Electrical Works', 'General', 'New Switches & Sockets', 'CHECKBOX', FALSE, 140),
            ('Electrical Works', 'General', 'Others', 'TEXT', FALSE, 141),
            -- G AC
            ('AC Works', 'General', 'New AC Unit', 'CHECKBOX', FALSE, 150),
            ('AC Works', 'General', 'New AC Grills', 'CHECKBOX', FALSE, 151),
            ('AC Works', 'General', 'New Thermostats', 'CHECKBOX', FALSE, 152),
            ('AC Works', 'General', 'Deep Cleaning of ACU and Ducts', 'CHECKBOX', FALSE, 153),
            -- H Joinery
            ('Joinery Works', 'General', 'New Kitchen Laminated', 'CHECKBOX', FALSE, 160),
            ('Joinery Works', 'General', 'Kitchen Accessories / Specify', 'TEXT', FALSE, 161),
            ('Joinery Works', 'General', 'Wardrobes', 'CHECKBOX', FALSE, 162),
            ('Joinery Works', 'General', 'Vanity Units Laminated', 'CHECKBOX', FALSE, 163),
            ('Joinery Works', 'General', 'Vanity Units Shutters', 'CHECKBOX', FALSE, 164),
            ('Joinery Works', 'General', 'Vanity Units Drawers', 'CHECKBOX', FALSE, 165),
            ('Joinery Works', 'General', 'New Kitchen Painted', 'CHECKBOX', FALSE, 166),
            ('Joinery Works', 'General', 'Vanity Units Painted', 'CHECKBOX', FALSE, 167),
            ('Joinery Works', 'General', 'TV Unit', 'CHECKBOX', FALSE, 168),
            ('Joinery Works', 'General', 'Internal Doors Repainting', 'CHECKBOX', FALSE, 169),
            ('Joinery Works', 'General', 'Wardrobe Shutters Repainting', 'CHECKBOX', FALSE, 170),
            ('Joinery Works', 'General', 'New Ironmongeries and Door Handle', 'CHECKBOX', FALSE, 171),
            -- I Aluminum
            ('Aluminum Works', 'General', 'Retain Existing', 'CHECKBOX', FALSE, 180),
            ('Aluminum Works', 'General', 'New Windows and Doors', 'CHECKBOX', FALSE, 181),
            ('Aluminum Works', 'General', 'Others', 'TEXT', FALSE, 182),
            -- J Patio
            ('Patio Enclosure', 'General', 'No', 'CHECKBOX', FALSE, 190),
            ('Patio Enclosure', 'General', 'Yes / Specify Location', 'TEXT', FALSE, 191),
            ('Patio Enclosure', 'General', 'Others', 'TEXT', FALSE, 192),
            -- K Extension
            ('Extension Work', 'General', 'No', 'CHECKBOX', FALSE, 200),
            ('Extension Work', 'General', 'Yes / Specify Location', 'TEXT', FALSE, 201),
            ('Extension Work', 'General', 'Others', 'TEXT', FALSE, 202),
            -- L Balcony / Terrace
            ('Balcony / Terrace Works', 'General', 'No', 'CHECKBOX', FALSE, 210),
            ('Balcony / Terrace Works', 'General', 'Yes / Specify Location', 'TEXT', FALSE, 211),
            ('Balcony / Terrace Works', 'General', 'Others', 'TEXT', FALSE, 212),
            -- M External
            ('External Works', 'General', 'Landscaping', 'CHECKBOX', FALSE, 220),
            ('External Works', 'General', 'Specify works', 'TEXT', FALSE, 221),
            -- Other Information
            ('Other Information', 'General', 'Pictures / Attachment notes', 'TEXT', FALSE, 230),
            ('Other Information', 'General', 'As-Built Drawings notes', 'TEXT', FALSE, 231),
            ('Other Information', 'General', 'Designer', 'TEXT', FALSE, 232),
            ('Other Information', 'General', 'Recommended by', 'TEXT', FALSE, 233),
            ('Other Information', 'General', 'Referral Commission (%)', 'TEXT', FALSE, 234)
        ) AS v(section_name, room_name, question, item_type, is_required, display_order);
    END LOOP;
END $$;
