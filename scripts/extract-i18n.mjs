// Phase 4: extract the curated FR/ES club and nationality names from the web
// prototype's inline DATA (the validated reference) into the CSVs.
//
// Rewrites:
//   data/clubs.csv          -> id,name_en,name_fr,name_es,notoriety
//   data/nationalities.csv  -> nationality,name_en,name_fr,name_es
//     (`nationality` is the exact key used by players.csv, i.e. the English
//      label; the prototype keys its map by Wikidata Q-id, so it is re-keyed)
//
// The script fails loudly on any coverage gap (club id or player nationality
// missing from the prototype) instead of silently falling back.
//
// Usage: node scripts/extract-i18n.mjs

import { readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");
const HTML = join(ROOT, "reference", "web-prototype", "mercato.html");
const CLUBS_CSV = join(ROOT, "data", "clubs.csv");
const PLAYERS_CSV = join(ROOT, "data", "players.csv");
const NATS_CSV = join(ROOT, "data", "nationalities.csv");

function extractData(html) {
  const marker = "const DATA = ";
  const start = html.indexOf(marker) + marker.length;
  let depth = 0;
  for (let i = start; i < html.length; i++) {
    if (html[i] === "{") depth++;
    else if (html[i] === "}" && --depth === 0) {
      return JSON.parse(html.slice(start, i + 1));
    }
  }
  throw new Error("DATA object not found in prototype");
}

function parseCsv(text) {
  const rows = [];
  let row = [], field = "", inQuotes = false;
  for (let i = 0; i < text.length; i++) {
    const c = text[i];
    if (inQuotes) {
      if (c === '"' && text[i + 1] === '"') { field += '"'; i++; }
      else if (c === '"') inQuotes = false;
      else field += c;
    } else if (c === '"') inQuotes = true;
    else if (c === ",") { row.push(field); field = ""; }
    else if (c === "\n") { row.push(field); rows.push(row); row = []; field = ""; }
    else if (c !== "\r") field += c;
  }
  if (field !== "" || row.length) { row.push(field); rows.push(row); }
  return rows;
}

const esc = (v) => (/[",\n]/.test(v) ? `"${v.replaceAll('"', '""')}"` : v);

// Display overrides: the prototype carries Wikidata's official long forms for
// two states; hints should use the common short names. Keys are players.csv
// nationality strings; values replace the extracted en/fr/es display names.
const SHORT_FORMS = {
  "Kingdom of Denmark": { en: "Denmark", fr: "Danemark", es: "Dinamarca" },
  "Kingdom of the Netherlands": { en: "Netherlands", fr: "Pays-Bas", es: "Países Bajos" },
};

const DATA = extractData(readFileSync(HTML, "utf8"));

// Clubs: keep CSV row order (the engine relies on stable ordering).
const clubRows = parseCsv(readFileSync(CLUBS_CSV, "utf8"));
const clubHeader = clubRows.shift();
if (clubHeader[1] !== "name" && clubHeader[1] !== "name_en") {
  throw new Error(`unexpected clubs.csv header: ${clubHeader.join(",")}`);
}
const notorietyCol = clubHeader.length - 1;
const missingClubs = [];
const outClubs = clubRows.map((r) => {
  const proto = DATA.clubs[r[0]];
  if (!proto) { missingClubs.push(r[0]); return ""; }
  return [r[0], proto.en, proto.fr, proto.es, r[notorietyCol]].map(esc).join(",");
});
if (missingClubs.length) {
  throw new Error(`clubs missing from prototype DATA: ${missingClubs.join(", ")}`);
}

// Nationalities: re-key the prototype map (Q-id keyed) by its English label,
// which is exactly what players.csv stores.
const byEn = new Map(Object.values(DATA.nats).map((n) => [n.en, n]));
const playerRows = parseCsv(readFileSync(PLAYERS_CSV, "utf8"));
playerRows.shift();
const natKeys = [...new Set(playerRows.map((r) => r[3]).filter(Boolean))].sort();
const missingNats = natKeys.filter((k) => !byEn.has(k));
if (missingNats.length) {
  throw new Error(`player nationalities missing from prototype DATA: ${missingNats.join(", ")}`);
}
const outNats = natKeys.map((k) => {
  const n = SHORT_FORMS[k] ?? byEn.get(k);
  return [k, n.en, n.fr, n.es].map(esc).join(",");
});

writeFileSync(CLUBS_CSV, ["id,name_en,name_fr,name_es,notoriety", ...outClubs].join("\n") + "\n");
writeFileSync(NATS_CSV, ["nationality,name_en,name_fr,name_es", ...outNats].join("\n") + "\n");
console.log(`clubs: ${outClubs.length} rows, nationalities: ${outNats.length} rows`);
