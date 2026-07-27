// Enrich the CSV source of truth with EN/FR/ES names.
//
// The committed CSVs were English-only. The prototype's inline DATA
// (reference/web-prototype/mercato.html) already carries FR/ES names for clubs
// and nationalities, so this is an extraction, not a translation (docs/DATA.md).
//
// Verified before writing: both sources hold the same 412 clubs / 513 players /
// 1905 transfers with identical ids, and every English club name already agrees.
//
// Rewrites: clubs.csv, players.csv. Creates: nationalities.csv.
// Leaves alone: transfers.csv, player_aliases.csv.
//
// Run: node scripts/enrich-csv-i18n.mjs
import fs from "fs";

const ROOT = new URL("..", import.meta.url).pathname;
const DATA_DIR = ROOT + "data/";

// --- load inline DATA -------------------------------------------------------
const html = fs.readFileSync(ROOT + "reference/web-prototype/mercato.html", "utf8");
const line = html.split(/\r?\n/).find((l) => l.trimStart().startsWith("const DATA"));
const DATA = JSON.parse(line.slice(line.indexOf("{"), line.lastIndexOf("}") + 1));

// --- tiny CSV helpers -------------------------------------------------------
function readCsv(name) {
  const rows = fs.readFileSync(DATA_DIR + name, "utf8").trim().split(/\r?\n/);
  const head = rows[0].split(",");
  return rows.slice(1).map((r) => {
    const cells = splitCsvLine(r);
    return Object.fromEntries(head.map((h, i) => [h, cells[i]]));
  });
}
function splitCsvLine(line) {
  const out = [];
  let cur = "";
  let quoted = false;
  for (let i = 0; i < line.length; i++) {
    const c = line[i];
    if (quoted) {
      if (c === '"' && line[i + 1] === '"') {
        cur += '"';
        i++;
      } else if (c === '"') quoted = false;
      else cur += c;
    } else if (c === '"') quoted = true;
    else if (c === ",") {
      out.push(cur);
      cur = "";
    } else cur += c;
  }
  out.push(cur);
  return out;
}
const cell = (v) => {
  const s = v == null ? "" : String(v);
  return /[",\n]/.test(s) ? '"' + s.replace(/"/g, '""') + '"' : s;
};
const writeCsv = (name, header, rows) => {
  const body = rows.map((r) => header.map((h) => cell(r[h])).join(",")).join("\n");
  fs.writeFileSync(DATA_DIR + name, header.join(",") + "\n" + body + "\n");
};

// --- guard: sources must agree before we rewrite anything -------------------
const players = readCsv("players.csv");
const clubs = readCsv("clubs.csv");
const dataPlayerIds = new Set(Object.keys(DATA.players));
const dataClubIds = new Set(Object.keys(DATA.clubs));
for (const [rows, ids, what] of [
  [players, dataPlayerIds, "players"],
  [clubs, dataClubIds, "clubs"],
]) {
  const missing = rows.filter((r) => !ids.has(r.id));
  if (missing.length || rows.length !== ids.size) {
    throw new Error(`${what}: CSV and inline DATA disagree (${missing.length} unknown ids)`);
  }
}

// --- nationalities.csv (new translation table) ------------------------------
const nats = Object.entries(DATA.nats)
  .map(([id, n]) => ({ id, name_en: n.en, name_fr: n.fr, name_es: n.es }))
  .sort((a, b) => a.id.localeCompare(b.id));
writeCsv("nationalities.csv", ["id", "name_en", "name_fr", "name_es"], nats);

// Map English nationality label -> Q-id, so players.csv can reference the table.
const natIdByEn = new Map(nats.map((n) => [n.name_en, n.id]));

// --- clubs.csv --------------------------------------------------------------
writeCsv(
  "clubs.csv",
  ["id", "name_en", "name_fr", "name_es", "notoriety"],
  clubs.map((c) => {
    const d = DATA.clubs[c.id];
    return {
      id: c.id,
      name_en: d.en,
      name_fr: d.fr,
      name_es: d.es,
      notoriety: c.notoriety,
    };
  })
);

// --- players.csv ------------------------------------------------------------
// Player names are identical across languages today, but the columns are
// per-language so future divergence needs no schema change.
const unknownNats = new Set();
writeCsv(
  "players.csv",
  ["id", "name_en", "name_fr", "name_es", "position", "nationality", "birth_year", "notoriety"],
  players.map((p) => {
    const d = DATA.players[p.id];
    const natId = d.nat || natIdByEn.get(p.nationality) || "";
    if (!natId && p.nationality) unknownNats.add(p.nationality);
    return {
      id: p.id,
      name_en: d.n.en,
      name_fr: d.n.fr,
      name_es: d.n.es,
      position: p.position,
      nationality: natId,
      birth_year: p.birth_year,
      notoriety: p.notoriety,
    };
  })
);

console.log(
  `clubs=${clubs.length} players=${players.length} nationalities=${nats.length}` +
    (unknownNats.size ? ` WARNING unmapped nationalities: ${[...unknownNats].join(", ")}` : "")
);
