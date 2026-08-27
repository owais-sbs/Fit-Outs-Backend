/**
 * Appends BOQ categories K–O into Docs/boq_data.json and generates:
 * - V33__seed_boq_work_items_k_to_o.sql (work item masters + items)
 * - V34__seed_boq_purchase_materials.sql (materials from Purchases / supply lines)
 *
 * Usage: node scripts/seed-boq-k-to-o.cjs
 */
const fs = require("fs");
const path = require("path");

const ROOT = path.resolve(__dirname, "..", "..");
const JSON_PATH = path.join(ROOT, "Docs", "boq_data.json");
const MIG_DIR = path.join(__dirname, "..", "src", "main", "resources", "db", "migration");

const NEW_CATEGORIES = [
  {
    category_id: "K",
    category_name: "JOINERY WORKS",
    items: [
      {
        id: "K.0.1",
        description:
          "Relocation of existing door frame and installation of new architrave made of MDF in approved PU paint finish at Master Bathroom",
        quantity: "",
        unit: "No.",
        rate: "1100",
        cost: "850",
        total: "-",
        sub_items: [],
      },
      {
        id: "K.0.2",
        description:
          "Cutting the bottom of door leaves of internal doors (including paint touch ups at bottom only) to adjust to the new floor level",
        quantity: "",
        unit: "No.",
        rate: "360",
        cost: "300",
        total: "-",
        sub_items: [],
      },
      {
        id: "K.1",
        description:
          "Repainting of existing wooden staircase handrail including required sanding. NOTE: It is assumed that aluminum railing will be retained, if modification or repainting is required, it will be quoted later",
        quantity: "",
        unit: "m",
        rate: "180",
        cost: "150",
        total: "-",
        sub_items: [],
      },
      {
        id: "K.1.1",
        description:
          "Repainting of existing wooden staircase handrail including required sanding including spray painting of existing aluminum railing at staircase area",
        quantity: "",
        unit: "m",
        rate: "600",
        cost: "500",
        total: "-",
        sub_items: [],
      },
      {
        id: "K.2",
        description:
          "Spray painting of existing door leafs at factory. Including transportation, cutting of bottom part to adjust to new floor level (if required) & reinstallation of repainted door leafs by retaining the existing ironmongeries. Hand painting of door frames & architraves at site (if possible). NOTE: If existing doors are in laminate finish, it cannot be repainted.",
        quantity: "",
        unit: "",
        rate: "",
        cost: "",
        total: "-",
        sub_items: [
          { id: "a", description: "Internal Doors", quantity: "0", unit: "No.", rate: "1600", cost: "1350", total: "-" },
          { id: "b", description: "Utility Room Doors", quantity: "0", unit: "No.", rate: "1310", cost: "1100", total: "-" },
          { id: "c", description: "Main Door leaf", quantity: "0", unit: "No.", rate: "2140", cost: "1800", total: "-" },
        ],
      },
      {
        id: "K.3",
        description:
          "Spray painting of existing wardrobe shutters at factory. Including transportation and reinstallation of repainted shutters and existing ironmongeries. Hand painting of architraves at site (if possible). NOTE: If existing doors are in laminate finish, it cannot be repainted",
        quantity: "",
        unit: "",
        rate: "",
        cost: "",
        total: "",
        sub_items: [
          { id: "a", description: "Bedroom 1", quantity: "", unit: "No.", rate: "800", cost: "675", total: "-" },
          { id: "b", description: "Bedroom 2", quantity: "", unit: "No.", rate: "800", cost: "675", total: "-" },
          { id: "c", description: "Master Bedroom", quantity: "", unit: "No.", rate: "800", cost: "675", total: "-" },
        ],
      },
      {
        id: "K.4",
        description:
          "Supply and installation of new architrave (one side only) in paint finish of internal face of powder room, bathroom & master bathroom doors.",
        quantity: "",
        unit: "No.",
        rate: "360",
        cost: "300",
        total: "-",
        sub_items: [],
      },
      {
        id: "K.5",
        description:
          "Fabrication, supply and installation of the following custom made joinery as per the illustrations and material specifications stated at the Appendices. Size: L x D x H. NOTE: JCT will provide the samples from our Material Library + maximum of 2 samples for each required finish (e.g. veneer / laminate / PU paint finish) and it will be given free of cost. If the Client will require additional samples, then it will be 150dhs per sample",
        quantity: "",
        unit: "",
        rate: "",
        cost: "",
        total: "-",
        sub_items: [
          {
            id: "a",
            description:
              "Provisional amount for Kitchen Cabinets: Base Cabinet Size: (2.25+1.85+2.45) x 0.6 x 0.90m; Top Cabinet Size: (2.25+1.85+3.25) x 0.35 x 0.90m; Full Height Cabinet Size: 1.20 x 0.60 x 2.40m; Island Size: 2.00m x 0.60m x 0.90m; Accessories: Waste Bin and Corner Unit",
            quantity: "",
            unit: "No.",
            rate: "3800",
            cost: "3150",
            total: "-",
          },
          {
            id: "b.1",
            description: "Vanity Unit at Powder Room (shutters). Size: 1.0m x 0.55m x 0.50m",
            quantity: "",
            unit: "No.",
            rate: "0",
            cost: "0",
            total: "-",
          },
          {
            id: "b.1b",
            description: "Vanity Unit at Guest Bathroom (shutters). Size: 1.0m x 0.55m x 0.50m",
            quantity: "",
            unit: "No.",
            rate: "0",
            cost: "0",
            total: "-",
          },
          {
            id: "b.2",
            description: "Vanity Unit at Common Bathroom (shutters). Size: 1.0m x 0.55m x 0.50m",
            quantity: "",
            unit: "No.",
            rate: "0",
            cost: "0",
            total: "-",
          },
          {
            id: "b.2b",
            description: "Vanity Unit at Bathroom 1 (shutters). Size: 1.0m x 0.55m x 0.50m",
            quantity: "",
            unit: "No.",
            rate: "0",
            cost: "0",
            total: "-",
          },
          {
            id: "b.3",
            description: "Vanity Unit at Master Bathroom (shutters). Size: 1.80m x 0.55m x 0.50m",
            quantity: "",
            unit: "No.",
            rate: "0",
            cost: "0",
            total: "-",
          },
          {
            id: "c",
            description: "Plain Frameless Vanity Mirror. Size: 0.6m x 0.90m",
            quantity: "0",
            unit: "No.",
            rate: "1370",
            cost: "1150",
            total: "-",
          },
          {
            id: "c2",
            description: "Backlit Frameless Vanity Mirror. Size: 0.6m x 0.90m",
            quantity: "",
            unit: "No.",
            rate: "1780",
            cost: "1500",
            total: "-",
          },
          {
            id: "c3",
            description: "Vanity Mirror with MDF frame in approve PU paint finish. Size: 0.60 x 0.90m",
            quantity: "",
            unit: "No.",
            rate: "1900",
            cost: "1600",
            total: "-",
          },
          {
            id: "g",
            description:
              "Provisional amount for new Main Door - made of Solid Core in approved Veneer finish with solid wood frame and architrave. OA Size: 1.60m x 2.20m",
            quantity: "PS",
            unit: "PS",
            rate: "12900",
            cost: "10000",
            total: "-",
          },
        ],
      },
      {
        id: "K.6",
        description: "2D Shop drawing for above Joinery items (2 rev)",
        quantity: "1",
        unit: "No.",
        rate: "500",
        cost: "300",
        total: "500.00",
        sub_items: [],
      },
      {
        id: "K.7",
        description: "Optional Joinery",
        quantity: "",
        unit: "",
        rate: "",
        cost: "",
        total: "-",
        sub_items: [
          {
            id: "a",
            description: "New Internal Door (Non-FR). OA Size: 0.90-1.0m x 2.10m",
            quantity: "",
            unit: "No.",
            rate: "3450",
            cost: "3000",
            total: "RATE ONLY",
          },
          {
            id: "b",
            description: "New Wardrobe. OA Size: 2.0m x 0.60m x 2.70m",
            quantity: "",
            unit: "No.",
            rate: "0",
            cost: "1850",
            total: "RATE ONLY",
          },
        ],
      },
    ],
  },
  {
    category_id: "L",
    category_name: "COUNTER TOP & MARBLE WORKS",
    items: [
      {
        id: "L.1",
        description:
          "Provisional amount for supply and installation of proposed 20mm thk Thasos White - KOZO Quartz Counter Top with 4cm fascia at: Kitchen",
        quantity: "",
        unit: "No.",
        rate: "0",
        cost: "0",
        total: "-",
        sub_items: [],
      },
      {
        id: "L.2",
        description:
          "Provisional amount for supply and installation of proposed 20mm thk Thasos White - KOZO Quartz Vanity Counter Top with 4cm fascia. NOTE: Pricing is based on the following locations. Should the size and number of locations change, the price will be adjusted accordingly",
        quantity: "",
        unit: "",
        rate: "",
        cost: "",
        total: "-",
        sub_items: [
          { id: "a", description: "Powder Room: Size: 1.00m x 0.55m", quantity: "0", unit: "No.", rate: "0", cost: "0", total: "-" },
          { id: "b", description: "Guest Bathroom: Size: 1.00m x 0.55m", quantity: "0", unit: "No.", rate: "0", cost: "0", total: "-" },
          { id: "c", description: "Common Bathroom: Size: 1.00m x 0.55m", quantity: "0", unit: "No.", rate: "0", cost: "0", total: "-" },
          { id: "d", description: "Bathroom 1: Size: 1.80m x 0.55m", quantity: "0", unit: "No.", rate: "0", cost: "0", total: "-" },
          { id: "e", description: "Master Bathroom: Size: 1.80m x 0.55m", quantity: "0", unit: "No.", rate: "0", cost: "0", total: "-" },
        ],
      },
      {
        id: "L.4",
        description: "Repolishing of existing marble riser, step and landing at Staircase area",
        quantity: "",
        unit: "No.",
        rate: "220",
        cost: "180",
        total: "-",
        sub_items: [],
      },
    ],
  },
  {
    category_id: "M",
    category_name: "ALUMINUM AND GLASS WORKS",
    items: [
      {
        id: "M.0.1",
        description:
          "Supply and installation of film and closing of the existing window opening (0.60m x 1.30m) with regular (RG) gypsum board at Living area",
        quantity: "",
        unit: "No.",
        rate: "",
        cost: "300",
        total: "-",
        sub_items: [],
      },
      {
        id: "M.1",
        description:
          "Supply and installation of shower glass fixed panel made of 10mm thick clear normal tempered glass supported by aluminum in chrome finish U channel at Guest bathroom, Common bathroom and Master Bathroom. Size: 1.0m x 2.4m",
        quantity: "",
        unit: "No.",
        rate: "0",
        cost: "0",
        total: "-",
        sub_items: [],
      },
      {
        id: "M.1.1",
        description:
          "Supply and installation of shower glass swing door made of 10mm thick clear normal tempered glass including chrome finish hinges and normal handle at Master Bathroom. Size: 0.70m x 2.1m",
        quantity: "",
        unit: "No.",
        rate: "0",
        cost: "0",
        total: "-",
        sub_items: [],
      },
      {
        id: "M.1.2",
        description:
          "Supply and installation of shower glass fixed panel + swing door made of 10mm thick clear normal tempered glass supported by aluminum in chrome finish U channel at Master Bathroom. Size: (0.8+0.90)m x 2.4m",
        quantity: "",
        unit: "No.",
        rate: "0",
        cost: "0",
        total: "-",
        sub_items: [],
      },
      {
        id: "M.2",
        description:
          "Supply and installation of glass balustrade (side mounted) made of 17.52mm thick clear tempered laminated glass with cap and stainless steel exposed accessories at Staircase area",
        quantity: "",
        unit: "m",
        rate: "3680",
        cost: "3100",
        total: "-",
        sub_items: [],
      },
      {
        id: "M.2.1",
        description:
          "Supply and installation of rectangular Glass Door made of Stainless Steel custom made Slim Profile Powder Coating Finish, 8Mm Ribbed Glass including standard accessories as per provided design. Size: 1.0 x 2.40m",
        quantity: "",
        unit: "No.",
        rate: "0",
        cost: "0",
        total: "-",
        sub_items: [],
      },
      {
        id: "M.2.2",
        description:
          "Supply and installation of rectangular Glass Door made of Stainless Steel custom made Slim Profile Powder Coating Finish, 10mm clear tempered normal Glass including standard accessories as per provided design. Size: 1.0 x 2.40m",
        quantity: "",
        unit: "No.",
        rate: "0",
        cost: "0",
        total: "-",
        sub_items: [],
      },
      {
        id: "M.3",
        description:
          "Supply and installation of new plain RAL color Powder coated MAQ105 Slim series profile heavy duty with 6mm Clear Glass (Guardian) + 12mm Air Gap + 6mm Clear Glass (Guardian) (24mm Double Glazed) tempered glass",
        quantity: "",
        unit: "",
        rate: "",
        cost: "",
        total: "",
        sub_items: [
          {
            id: "a",
            description: "Living room - 3 panels. Size: 3.60 x 2.15m",
            quantity: "",
            unit: "No.",
            rate: "0",
            cost: "0",
            total: "-",
          },
        ],
      },
      {
        id: "M.3.1",
        description:
          "Supply and installation of new plain RAL color Powder coated HT70 Folding Series profile / frame (door/window) with 6mm Clear Glass (Guardian) + 12mm Air Gap + 6mm Clear Glass (Guardian) (24mm Double Glazed) tempered glass",
        quantity: "",
        unit: "",
        rate: "",
        cost: "",
        total: "",
        sub_items: [
          {
            id: "a",
            description: "Living room - 4 panels. Size: 3.80 x 2.20m",
            quantity: "",
            unit: "No.",
            rate: "0",
            cost: "0",
            total: "-",
          },
        ],
      },
      {
        id: "M.3.2",
        description:
          "Supply and installation of new plain RAL color Powder coated 10.5mm Al Ghurair Series aluminum profile / frame (door/window) with 6mm Clear Glass (Guardian) + 12mm Air Gap + 6mm Clear Glass (Guardian) (24mm Double Glazed) tempered glass",
        quantity: "",
        unit: "",
        rate: "",
        cost: "",
        total: "",
        sub_items: [
          { id: "a", description: "Living room (sliding door). Size: 1.40m x 2.20m", quantity: "", unit: "No.", rate: "0", cost: "0", total: "-" },
          { id: "b", description: "Maid's room extension (sliding door). Size: 1.00m x 1.10m", quantity: "", unit: "No.", rate: "0", cost: "0", total: "-" },
          { id: "c", description: "Living area (fixed window)", quantity: "", unit: "No.", rate: "0", cost: "0", total: "-" },
        ],
      },
      {
        id: "M.3.3",
        description: "Supply and installation of FLYSCREEN",
        quantity: "",
        unit: "No.",
        rate: "0",
        cost: "0",
        total: "RATE ONLY",
        sub_items: [],
      },
      {
        id: "M.4",
        description:
          "Supply and installation of (powder coated) MAQS Main Door series profile heavy duty aluminum with MAQS stainless steel and aluminum accessories. Material: Aluminum; Surface Finished: Powder Coated; Glass: Clear Glass 6mm + 20mm Air Gap + 6mm Clear Glass (Guardian) (32mm Double Glazed) All Glass tempered as per ASTM. Size: 2000mm x 2350mm (Hinged aluminum main door with side fixed glass)",
        quantity: "PS",
        unit: "PS",
        rate: "24950",
        cost: "21620",
        total: "-",
        sub_items: [],
      },
      {
        id: "M.4.1",
        description:
          "Provisional amount for supply and installation of Main Door. OA Size: 1.85m x 2.10m; Door Model: 1010CC; Construction: Premium CarbonCore Level; Handle outside: 32384 Handle ANGULAR180 NERO; Handle inside: 60554 Door handle ANGLAR NERO with square rosette; Cylinder type: ARMO 1 R6 Security rosette cylinder with FL function; Clear Glass: Safety glass inside only: VSG with triple glazing. NOTE: Final Amount will be quoted based on final design and actual site requirement",
        quantity: "PS",
        unit: "PS",
        rate: "43150",
        cost: "37422",
        total: "-",
        sub_items: [],
      },
    ],
  },
  {
    category_id: "N",
    category_name: "PURCHASES",
    items: [
      {
        id: "N.1",
        description: "Supply of IP65 WHITE frame LED spotlight (non-dimmable) for bathrooms and kitchen",
        quantity: "0",
        unit: "No.",
        rate: "135",
        cost: "115",
        total: "-",
        sub_items: [],
      },
      {
        id: "N.2",
        description: "Supply of IP20 WHITE frame LED spotlight (non-dimmable) for item I.1",
        quantity: "0",
        unit: "No.",
        rate: "50",
        cost: "45",
        total: "-",
        sub_items: [],
      },
      {
        id: "N.3",
        description: "Supply of IP20 BLACK frame LED spotlight (non-dimmable) for item I.1",
        quantity: "0",
        unit: "No.",
        rate: "150",
        cost: "130",
        total: "-",
        sub_items: [],
      },
      {
        id: "N.4",
        description: "Provisional Amount for new face plate for switches and socket - 90pcs. Brand: Legrand Mallia Senses (White)",
        quantity: "",
        unit: "No.",
        rate: "5000",
        cost: "5000",
        total: "-",
        sub_items: [],
      },
      {
        id: "N.5",
        description: "Provisional amount for supply of internal door and utility door handles (chrome finish)",
        quantity: "0",
        unit: "No.",
        rate: "650",
        cost: "550",
        total: "-",
        sub_items: [],
      },
      {
        id: "N.6",
        description: "Provisional amount for supply of Main Door handle (chrome finish)",
        quantity: "0",
        unit: "No.",
        rate: "850",
        cost: "750",
        total: "-",
        sub_items: [],
      },
      {
        id: "N.7",
        description: "Provisional Amount for Sanitary Fixtures for Maid's Bathroom Brand: Kludi/RAK",
        quantity: "PS",
        unit: "PS",
        rate: "2500",
        cost: "2000",
        total: "-",
        sub_items: [],
      },
      {
        id: "N.9",
        description: "Tiles Supply (RAK Ceramics) - 65dhs/m²",
        quantity: "",
        unit: "",
        rate: "",
        cost: "",
        total: "",
        sub_items: [
          { id: "a", description: "60x60cm Powder Room Floor and Wall Tiles", quantity: "", unit: "PS", rate: "0", cost: "", total: "RATE ONLY" },
          { id: "b", description: "60x60cm Maid's Bathroom Floor and Wall Tiles", quantity: "", unit: "PS", rate: "0", cost: "", total: "RATE ONLY" },
          { id: "b2", description: "60x60cm Guest Bathroom Floor and Wall Tiles", quantity: "", unit: "PS", rate: "0", cost: "", total: "RATE ONLY" },
          { id: "b3", description: "60x60cm Common Bathroom Floor and Wall Tiles", quantity: "", unit: "PS", rate: "0", cost: "", total: "RATE ONLY" },
          { id: "b4", description: "60x60cm Bathroom 1 Floor and Wall Tiles", quantity: "", unit: "PS", rate: "0", cost: "", total: "RATE ONLY" },
          { id: "c", description: "60x60cm Master Bathroom Floor and Wall Tiles", quantity: "", unit: "PS", rate: "0", cost: "", total: "RATE ONLY" },
          { id: "e", description: "60x60cm Kitchen Backsplash", quantity: "", unit: "PS", rate: "0", cost: "", total: "RATE ONLY" },
        ],
      },
      {
        id: "N.10",
        description: "Provisional Amount for Sanitary Fixtures. Brand: BAGNO DESIGN",
        quantity: "",
        unit: "",
        rate: "",
        cost: "",
        total: "",
        sub_items: [
          { id: "a", description: "Powder Room", quantity: "", unit: "PS", rate: "3250", cost: "3250", total: "RATE ONLY" },
          { id: "a2", description: "Guest Bathroom (shower)", quantity: "", unit: "PS", rate: "4875", cost: "4875", total: "RATE ONLY" },
          { id: "b", description: "Common Bathroom (bathtub)", quantity: "", unit: "PS", rate: "6375", cost: "6375", total: "RATE ONLY" },
          { id: "b2", description: "Common Bathroom (shower)", quantity: "", unit: "PS", rate: "4875", cost: "4875", total: "RATE ONLY" },
          { id: "d", description: "Master Bathroom (shower & single sink)", quantity: "", unit: "PS", rate: "4875", cost: "4875", total: "RATE ONLY" },
          { id: "c", description: "Master Bathroom (shower & double sink)", quantity: "", unit: "PS", rate: "5800", cost: "5800", total: "RATE ONLY" },
          { id: "c2", description: "Master Bathroom (shower + inset bathtub)", quantity: "", unit: "PS", rate: "5500", cost: "5500", total: "RATE ONLY" },
          { id: "c3", description: "Master Bathroom (shower + stand alone bathtub)", quantity: "", unit: "PS", rate: "17500", cost: "17500", total: "RATE ONLY" },
        ],
      },
      {
        id: "N.11",
        description: "Provisional Amount for Sanitary Fixtures. Brand: Kludi/RAK (stand alone)",
        quantity: "",
        unit: "",
        rate: "",
        cost: "",
        total: "",
        sub_items: [
          { id: "a", description: "Powder Room", quantity: "", unit: "PS", rate: "2500", cost: "2500", total: "RATE ONLY" },
          { id: "b", description: "Common Bathroom (bathtub)", quantity: "", unit: "PS", rate: "3850", cost: "3850", total: "RATE ONLY" },
          { id: "c", description: "Common Bathroom (shower)", quantity: "", unit: "PS", rate: "4350", cost: "4350", total: "RATE ONLY" },
          { id: "d", description: "Master Bathroom (shower & single sink)", quantity: "", unit: "PS", rate: "3400", cost: "3400", total: "RATE ONLY" },
          { id: "e", description: "Master Bathroom (shower & double sink)", quantity: "", unit: "PS", rate: "4050", cost: "4050", total: "RATE ONLY" },
          { id: "f", description: "Master Bathroom (shower + inset bathtub)", quantity: "", unit: "PS", rate: "5200", cost: "5200", total: "RATE ONLY" },
          { id: "g", description: "Master Bathroom (shower + stand alone bathtub)", quantity: "", unit: "PS", rate: "17000", cost: "17000", total: "RATE ONLY" },
        ],
      },
      {
        id: "N.12",
        description: "Provisional Amount for Kitchen Sink and Mixer. Brand: BAGNO DESIGN",
        quantity: "",
        unit: "PS",
        rate: "2200",
        cost: "2200",
        total: "RATE ONLY",
        sub_items: [],
      },
    ],
  },
  {
    category_id: "O",
    category_name: "EXCLUSIONS",
    items: [
      { id: "O.1", description: "Gas Works (remove if gas works is quoted)", quantity: "", unit: "", rate: "", cost: "", total: "", sub_items: [] },
      { id: "O.2", description: "External and Landscaping Works", quantity: "", unit: "", rate: "", cost: "", total: "", sub_items: [] },
      { id: "O.3", description: "Home Automation Works", quantity: "", unit: "", rate: "", cost: "", total: "", sub_items: [] },
      { id: "O.4", description: "Fire Fighting and Fire Alarm Works", quantity: "", unit: "", rate: "", cost: "", total: "", sub_items: [] },
      { id: "O.5", description: "Supply of Sanitary Fixtures & Tiles (remove if purchases is included)", quantity: "", unit: "", rate: "", cost: "", total: "", sub_items: [] },
      { id: "O.6", description: "Supply of Kitchen Appliances, Sink and Mixer (remove if purchases is included)", quantity: "", unit: "", rate: "", cost: "", total: "", sub_items: [] },
      { id: "O.7", description: "Supply of Hanging, Ceiling Mounted or Wall Lights", quantity: "", unit: "", rate: "", cost: "", total: "", sub_items: [] },
      { id: "O.8", description: "Any item not specification mentioned above", quantity: "", unit: "", rate: "", cost: "", total: "", sub_items: [] },
    ],
  },
];

const PURCHASE_MATERIALS = [
  // cat_code, name, code, unit, cost, selling, supplier, sku, description
  ["ELEC", "IP65 WHITE frame LED spotlight (non-dimmable)", "PUR-LED-IP65-W", "PCS", 115, 135, "JCT Purchases", "N.1", "Bathrooms and kitchen"],
  ["ELEC", "IP20 WHITE frame LED spotlight (non-dimmable)", "PUR-LED-IP20-W", "PCS", 45, 50, "JCT Purchases", "N.2", "For item I.1"],
  ["ELEC", "IP20 BLACK frame LED spotlight (non-dimmable)", "PUR-LED-IP20-B", "PCS", 130, 150, "JCT Purchases", "N.3", "For item I.1"],
  ["ELEC", "Legrand Mallia Senses White face plates (90pcs provisional)", "PUR-FACE-MALLIA", "LOT", 5000, 5000, "Legrand", "N.4", "Switches and sockets face plates"],
  ["FIX", "Internal / utility door handle chrome finish", "PUR-HND-INT-CHR", "PCS", 550, 650, "JCT Purchases", "N.5", "Provisional door handles"],
  ["FIX", "Main Door handle chrome finish", "PUR-HND-MAIN-CHR", "PCS", 750, 850, "JCT Purchases", "N.6", "Provisional main door handle"],
  ["PLUMB", "Sanitary Fixtures Maid Bathroom Kludi/RAK", "PUR-SAN-MAID-KR", "LOT", 2000, 2500, "Kludi/RAK", "N.7", "Provisional sanitary package"],
  ["FLOOR", "RAK Ceramics Tile 60x60cm (supply)", "PUR-TILE-RAK-6060", "SQM", 65, 65, "RAK Ceramics", "N.9", "Floor and wall tiles supply rate"],
  ["PLUMB", "Sanitary Fixtures Powder Room BAGNO DESIGN", "PUR-SAN-PR-BAGNO", "LOT", 3250, 3250, "BAGNO DESIGN", "N.10.a", "Provisional sanitary package"],
  ["PLUMB", "Sanitary Fixtures Guest Bathroom shower BAGNO", "PUR-SAN-GB-SH-BAGNO", "LOT", 4875, 4875, "BAGNO DESIGN", "N.10.a2", "Provisional sanitary package"],
  ["PLUMB", "Sanitary Fixtures Common Bathroom bathtub BAGNO", "PUR-SAN-CB-BT-BAGNO", "LOT", 6375, 6375, "BAGNO DESIGN", "N.10.b", "Provisional sanitary package"],
  ["PLUMB", "Sanitary Fixtures Common Bathroom shower BAGNO", "PUR-SAN-CB-SH-BAGNO", "LOT", 4875, 4875, "BAGNO DESIGN", "N.10.b2", "Provisional sanitary package"],
  ["PLUMB", "Sanitary Fixtures Master Bathroom single sink BAGNO", "PUR-SAN-MB-SS-BAGNO", "LOT", 4875, 4875, "BAGNO DESIGN", "N.10.d", "Provisional sanitary package"],
  ["PLUMB", "Sanitary Fixtures Master Bathroom double sink BAGNO", "PUR-SAN-MB-DS-BAGNO", "LOT", 5800, 5800, "BAGNO DESIGN", "N.10.c", "Provisional sanitary package"],
  ["PLUMB", "Sanitary Fixtures Master Bathroom inset bathtub BAGNO", "PUR-SAN-MB-IB-BAGNO", "LOT", 5500, 5500, "BAGNO DESIGN", "N.10.c2", "Provisional sanitary package"],
  ["PLUMB", "Sanitary Fixtures Master Bathroom stand alone bathtub BAGNO", "PUR-SAN-MB-SA-BAGNO", "LOT", 17500, 17500, "BAGNO DESIGN", "N.10.c3", "Provisional sanitary package"],
  ["PLUMB", "Sanitary Fixtures Powder Room Kludi/RAK", "PUR-SAN-PR-KR", "LOT", 2500, 2500, "Kludi/RAK", "N.11.a", "Stand alone sanitary package"],
  ["PLUMB", "Sanitary Fixtures Common Bathroom bathtub Kludi/RAK", "PUR-SAN-CB-BT-KR", "LOT", 3850, 3850, "Kludi/RAK", "N.11.b", "Stand alone sanitary package"],
  ["PLUMB", "Sanitary Fixtures Common Bathroom shower Kludi/RAK", "PUR-SAN-CB-SH-KR", "LOT", 4350, 4350, "Kludi/RAK", "N.11.c", "Stand alone sanitary package"],
  ["PLUMB", "Sanitary Fixtures Master Bathroom single sink Kludi/RAK", "PUR-SAN-MB-SS-KR", "LOT", 3400, 3400, "Kludi/RAK", "N.11.d", "Stand alone sanitary package"],
  ["PLUMB", "Sanitary Fixtures Master Bathroom double sink Kludi/RAK", "PUR-SAN-MB-DS-KR", "LOT", 4050, 4050, "Kludi/RAK", "N.11.e", "Stand alone sanitary package"],
  ["PLUMB", "Sanitary Fixtures Master Bathroom inset bathtub Kludi/RAK", "PUR-SAN-MB-IB-KR", "LOT", 5200, 5200, "Kludi/RAK", "N.11.f", "Stand alone sanitary package"],
  ["PLUMB", "Sanitary Fixtures Master Bathroom stand alone bathtub Kludi/RAK", "PUR-SAN-MB-SA-KR", "LOT", 17000, 17000, "Kludi/RAK", "N.11.g", "Stand alone sanitary package"],
  ["FIX", "Kitchen Sink and Mixer BAGNO DESIGN", "PUR-SINK-MIX-BAGNO", "LOT", 2200, 2200, "BAGNO DESIGN", "N.12", "Provisional kitchen sink and mixer"],
  ["STONE", "Thasos White KOZO Quartz 20mm countertop", "PUR-QTZ-THASOS-20", "SQM", 0, 0, "KOZO", "L.1", "20mm thk with 4cm fascia"],
  ["JOIN", "MDF Architrave PU paint finish", "PUR-ARCH-MDF-PU", "PCS", 300, 360, "JCT Joinery", "K.4", "One side architrave paint finish"],
  ["GLASS", "Shower glass fixed panel 10mm tempered 1.0x2.4m", "PUR-GLS-SHW-FIX", "PCS", 0, 0, "JCT Glass", "M.1", "Chrome U channel"],
  ["GLASS", "Glass balustrade laminated 17.52mm side mounted", "PUR-GLS-BAL-1752", "RMT", 3100, 3680, "JCT Glass", "M.2", "SS exposed accessories"],
];

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
  if (!u || u === "ps") return "LOT";
  if (u === "no." || u === "no" || u === "nos" || u === "nr") return "PCS";
  if (u === "m" || u === "rm" || u === "rmt" || u === "r.m") return "RMT";
  if (u === "sq.m" || u === "sqm" || u === "m2" || u === "sq m") return "SQM";
  if (u === "set") return "SET";
  if (u === "lot" || u === "ls") return "LOT";
  return "LOT";
}

function truncate(value, max) {
  const text = String(value || "").trim();
  if (text.length <= max) return text;
  return text.slice(0, max - 3) + "...";
}

function buildWorkItemRows(categories) {
  const masters = [];
  const items = [];
  const codeUsage = new Map();

  function uniqueCode(baseCode) {
    const count = (codeUsage.get(baseCode) || 0) + 1;
    codeUsage.set(baseCode, count);
    return count === 1 ? baseCode : `${baseCode}-${count}`;
  }

  for (const category of categories) {
    masters.push({ code: category.category_id, name: category.category_name });
    for (const item of category.items || []) {
      const description = String(item.description || "").trim();
      if (!description) continue;
      const workItemCode = uniqueCode(String(item.id || `${category.category_id}.X`).trim());
      const selling = parseRate(item.rate);
      const cost = parseRate(item.cost) ?? selling;
      items.push({
        masterCode: category.category_id,
        workItemCode,
        workItemName: truncate(description, 200),
        description,
        unitType: mapUnit(item.unit, item.quantity),
        defaultRate: selling,
        costPrice: cost,
      });
      for (const sub of item.sub_items || []) {
        const subDesc = String(sub.description || "").trim();
        if (!subDesc) continue;
        const subCode = uniqueCode(`${workItemCode}.${sub.id || "x"}`);
        const subSell = parseRate(sub.rate);
        const subCost = parseRate(sub.cost) ?? subSell;
        items.push({
          masterCode: category.category_id,
          workItemCode: subCode,
          workItemName: truncate(subDesc, 200),
          description: `${description} — ${subDesc}`,
          unitType: mapUnit(sub.unit, sub.quantity),
          defaultRate: subSell,
          costPrice: subCost,
        });
      }
    }
  }
  return { masters, items };
}

function renderWorkItemsSql({ masters, items }) {
  const masterValues = masters.map((m) => `    (${sqlEscape(m.name)}, ${sqlEscape(m.code)})`).join(",\n");
  const itemValues = items
    .map((item) => {
      const rate = item.defaultRate == null ? "NULL" : Number(item.defaultRate).toFixed(2);
      const cost = item.costPrice == null ? "NULL" : Number(item.costPrice).toFixed(2);
      return `    ( ${sqlEscape(item.masterCode)}, ${sqlEscape(item.workItemCode)}, ${sqlEscape(item.workItemName)}, ${sqlEscape(item.description)}, ${sqlEscape(item.unitType)}, ${rate} , ${cost} )`;
    })
    .join(",\n");

  return `-- Seed BOQ categories K–O work item masters and catalog (idempotent).

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
    CASE WHEN v.default_rate IS NULL THEN FALSE ELSE TRUE END,
    CASE WHEN v.cost_price IS NULL THEN FALSE ELSE TRUE END,
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

function renderMaterialsSql(materials) {
  const values = materials
    .map(
      ([cat, name, code, unit, cost, sell, supplier, sku, desc]) =>
        `    (${sqlEscape(cat)}, ${sqlEscape(name)}, ${sqlEscape(code)}, ${sqlEscape(unit)}, ${Number(cost).toFixed(2)}, ${Number(sell).toFixed(2)}, ${sqlEscape(supplier)}, ${sqlEscape(sku)}, ${sqlEscape(desc)})`
    )
    .join(",\n");

  return `-- Seed BOQ purchase / supply materials from categories K–N (idempotent).

INSERT INTO material_categories (company_id, name, code)
SELECT c.uuid, v.name, v.code
FROM companies c
CROSS JOIN (VALUES
    ('Purchases', 'PURCH')
) AS v(name, code)
ON CONFLICT (company_id, code) DO NOTHING;

INSERT INTO materials (
    company_id,
    material_category_id,
    material_name,
    material_code,
    unit_type,
    cost_price,
    selling_price,
    supplier_name,
    sku,
    description
)
SELECT
    c.uuid,
    mc.id,
    v.material_name,
    v.material_code,
    v.unit_type,
    v.cost_price,
    v.selling_price,
    v.supplier_name,
    v.sku,
    v.description
FROM companies c
CROSS JOIN (VALUES
${values}
) AS v(
    cat_code,
    material_name,
    material_code,
    unit_type,
    cost_price,
    selling_price,
    supplier_name,
    sku,
    description
)
JOIN material_categories mc
  ON mc.company_id = c.uuid
 AND mc.code = v.cat_code
 AND mc.deleted = FALSE
ON CONFLICT (company_id, material_code) DO NOTHING;

INSERT INTO material_stock (company_id, material_id, quantity_on_hand)
SELECT m.company_id, m.id, 0
FROM materials m
WHERE m.deleted = FALSE
  AND m.material_code LIKE 'PUR-%'
  AND NOT EXISTS (
      SELECT 1 FROM material_stock ms WHERE ms.material_id = m.id
  );
`;
}

// --- patch JSON ---
const existing = JSON.parse(fs.readFileSync(JSON_PATH, "utf8"));
const byId = new Map(existing.map((c) => [c.category_id, c]));
for (const cat of NEW_CATEGORIES) {
  byId.set(cat.category_id, cat);
}
const order = ["A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O"];
const merged = order.map((id) => byId.get(id)).filter(Boolean);
for (const cat of existing) {
  if (!order.includes(cat.category_id)) merged.push(cat);
}
fs.writeFileSync(JSON_PATH, JSON.stringify(merged, null, 4) + "\n", "utf8");

const rows = buildWorkItemRows(NEW_CATEGORIES);
const workSql = renderWorkItemsSql(rows);
const matSql = renderMaterialsSql(PURCHASE_MATERIALS);

const workPath = path.join(MIG_DIR, "V33__seed_boq_work_items_k_to_o.sql");
const matPath = path.join(MIG_DIR, "V34__seed_boq_purchase_materials.sql");
fs.writeFileSync(workPath, workSql, "utf8");
fs.writeFileSync(matPath, matSql, "utf8");

console.log(`Updated ${JSON_PATH}`);
console.log(`Wrote ${workPath} (${rows.masters.length} masters, ${rows.items.length} work items)`);
console.log(`Wrote ${matPath} (${PURCHASE_MATERIALS.length} materials)`);
