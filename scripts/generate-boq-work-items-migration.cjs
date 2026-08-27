/**
 * Generates Flyway migration SQL from Docs/boq_data.json
 * Usage: node scripts/generate-boq-work-items-migration.js
 */
const fs = require("fs");
const path = require("path");

const ROOT = path.resolve(__dirname, "..", "..");
const JSON_PATH = path.join(ROOT, "Docs", "boq_data.json");
const OUT_PATH = path.join(
  __dirname,
  "..",
  "src",
  "main",
  "resources",
  "db",
  "migration",
  "V32__seed_boq_work_items.sql"
);

const VALID_ID = /^[A-K]\.\d+$/;

function sqlEscape(value) {
  if (value == null) return "NULL";
  return `'${String(value).replace(/'/g, "''")}'`;
}

function parseRate(value) {
  if (value == null) return null;
  const raw = String(value).trim();
  if (!raw || raw === "-" || /^rate only$/i.test(raw)) return null;
  const n = Number(raw.replace(/,/g, ""));
  return Number.isFinite(n) ? n : null;
}

function mapUnit(unit, quantity) {
  const q = String(quantity || "").trim().toUpperCase();
  if (q === "PS") return "LOT";

  const u = String(unit || "").trim().toLowerCase();
  if (!u) return "LOT";
  if (u === "no." || u === "no" || u === "nos" || u === "nr") return "PCS";
  if (u === "m" || u === "rm" || u === "rmt" || u === "r.m") return "RMT";
  if (u === "sq.m" || u === "sqm" || u === "m2" || u === "sq m") return "SQM";
  if (u === "sq.ft" || u === "sqft" || u === "sft") return "SQFT";
  if (u === "set") return "SET";
  if (u === "lot" || u === "ls" || u === "l.s." || u === "l/s") return "LOT";
  if (u === "kg") return "KG";
  if (u === "bag") return "BAG";
  if (u === "box") return "BOX";
  return "LOT";
}

function truncate(value, max) {
  const text = String(value || "").trim();
  if (text.length <= max) return text;
  return text.slice(0, max - 3) + "...";
}

function buildRows(data) {
  const masters = [];
  const items = [];
  const codeUsage = new Map();

  function uniqueCode(baseCode) {
    const count = (codeUsage.get(baseCode) || 0) + 1;
    codeUsage.set(baseCode, count);
    return count === 1 ? baseCode : `${baseCode}-${count}`;
  }

  for (const category of data) {
    const masterCode = category.category_id;
    masters.push({
      code: masterCode,
      name: category.category_name,
    });

    for (const item of category.items || []) {
      const description = String(item.description || "").trim();
      const id = String(item.id || "").trim();
      const hasValidId = VALID_ID.test(id);

      if (!description && !(item.sub_items || []).length) continue;
      if (!hasValidId) continue;

      const workItemCode = uniqueCode(id);
      items.push({
        masterCode,
        workItemCode,
        workItemName: truncate(description, 200),
        description,
        unitType: mapUnit(item.unit, item.quantity),
        defaultRate: parseRate(item.rate),
      });

      for (const sub of item.sub_items || []) {
        const subDesc = String(sub.description || "").trim();
        if (!subDesc) continue;
        const subId = String(sub.id || "").trim();
        const subCode = uniqueCode(`${workItemCode}.${subId || "x"}`);
        items.push({
          masterCode,
          workItemCode: subCode,
          workItemName: truncate(subDesc, 200),
          description: description ? `${description} — ${subDesc}` : subDesc,
          unitType: mapUnit(sub.unit, sub.quantity),
          defaultRate: parseRate(sub.rate),
        });
      }
    }
  }

  return { masters, items };
}

function renderSql({ masters, items }) {
  const masterValues = masters
    .map((m) => `    (${sqlEscape(m.name)}, ${sqlEscape(m.code)})`)
    .join(",\n");

  const itemValues = items
    .map((item) => {
      const rate =
        item.defaultRate == null ? "NULL" : item.defaultRate.toFixed(2);
      return [
        "    (",
        `${sqlEscape(item.masterCode)},`,
        `${sqlEscape(item.workItemCode)},`,
        `${sqlEscape(item.workItemName)},`,
        `${sqlEscape(item.description)},`,
        `${sqlEscape(item.unitType)},`,
        rate,
        ",",
        rate,
        ")",
      ].join(" ");
    })
    .join(",\n");

  return `-- Seed BOQ work item masters and catalog from Docs/boq_data.json (idempotent per company).

INSERT INTO work_item_masters (company_id, name, code)
SELECT c.uuid, v.name, v.code
FROM companies c
CROSS JOIN (VALUES
${masterValues}
) AS v(name, code)
ON CONFLICT (company_id, code) DO NOTHING;

INSERT INTO work_items (
    company_id,
    work_item_master_id,
    work_item_name,
    work_item_code,
    description,
    unit_type,
    default_rate,
    cost_price,
    selling_price_override,
    cost_price_override,
    quantity_formula_type,
    ceiling_applicable,
    wall_applicable,
    floor_applicable,
    active,
    deleted
)
SELECT
    c.uuid,
    wim.id,
    v.work_item_name,
    v.work_item_code,
    v.description,
    v.unit_type,
    v.default_rate,
    v.cost_price,
    FALSE,
    CASE WHEN v.default_rate IS NULL THEN FALSE ELSE TRUE END,
    'MANUAL',
    FALSE,
    FALSE,
    FALSE,
    TRUE,
    FALSE
FROM companies c
CROSS JOIN (VALUES
${itemValues}
) AS v(
    master_code,
    work_item_code,
    work_item_name,
    description,
    unit_type,
    default_rate,
    cost_price
)
JOIN work_item_masters wim
  ON wim.company_id = c.uuid
 AND wim.code = v.master_code
ON CONFLICT (company_id, work_item_code) DO NOTHING;
`;
}

const data = JSON.parse(fs.readFileSync(JSON_PATH, "utf8"));
const rows = buildRows(data);
const sql = renderSql(rows);
fs.writeFileSync(OUT_PATH, sql, "utf8");

console.log(`Wrote ${OUT_PATH}`);
console.log(`Masters: ${rows.masters.length}, Work items: ${rows.items.length}`);
