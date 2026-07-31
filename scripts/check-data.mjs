// Data quality checks for the CSV source of truth.
//
// The dataset is derived from Wikidata labels, which are crowd-edited, so it
// carries occasional corruption that is invisible to the eye: a digit standing
// in for a letter, a Greek capital alpha instead of a Latin A, a doubled space.
// Those matter twice over: the bad spelling is what the player sees, and it is
// also what the matching engine indexes, since normalize() strips characters
// outside [a-z0-9 ] (so "Arda" beginning with a Greek alpha normalizes to
// "rda").
//
// This checks the mechanical, unarguable defects only. Judgement calls (a
// nickname used as a name, a name order that differs across languages) are
// listed by --report for a human to work through, and are not failures.
//
// Run: node scripts/check-data.mjs [--report]
import fs from "fs";
import path from "path";

const ROOT = new URL("..", import.meta.url).pathname;
const DATA = path.join(ROOT, "data");
const REPORT = process.argv.includes("--report");

function readCsv(name) {
  const rows = fs.readFileSync(path.join(DATA, name), "utf8").trim().split(/\r?\n/);
  const head = rows[0].split(",");
  return rows.slice(1).map((line) => {
    const cells = [];
    let cur = "";
    let quoted = false;
    for (let i = 0; i < line.length; i++) {
      const c = line[i];
      if (quoted) {
        if (c === '"' && line[i + 1] === '"') { cur += '"'; i++; }
        else if (c === '"') quoted = false;
        else cur += c;
      } else if (c === '"') quoted = true;
      else if (c === ",") { cells.push(cur); cur = ""; }
      else cur += c;
    }
    cells.push(cur);
    return Object.fromEntries(head.map((h, i) => [h, cells[i]]));
  });
}

const LANGS = ["name_en", "name_fr", "name_es"];
const problems = [];

// A letter outside the Latin script inside an otherwise-Latin name is almost
// always a copy-paste artefact, and normalize() would silently drop it.
const isSuspiciousLetter = (ch) => {
  if (!/\p{L}/u.test(ch)) return false;
  return !/\p{Script=Latin}/u.test(ch);
};

// Digits are legitimate in club names (Hannover 96, TSG 1899 Hoffenheim) and in
// aliases (CR7, LM10), but never in a person's name.
function checkNames(rows, file, { extra = [], allowDigits = false } = {}) {
  for (const row of rows) {
    for (const field of [...LANGS, ...extra]) {
      const value = row[field];
      if (value === undefined) continue;
      const where = `${file} ${row.id ?? row.player_id} ${field}`;

      if (value.trim() !== value) problems.push(`${where}: leading or trailing space in ${JSON.stringify(value)}`);
      if (/\s{2,}/.test(value)) problems.push(`${where}: repeated space in ${JSON.stringify(value)}`);
      if (!allowDigits && /\d/.test(value)) {
        problems.push(`${where}: digit inside a name: ${JSON.stringify(value)}`);
      }
      // ". ." and friends: a stray separator left behind when a name component
      // was stripped, e.g. "1. . Nuremberg".
      if (/[.,]\s*[.,]/.test(value)) {
        problems.push(`${where}: repeated punctuation in ${JSON.stringify(value)}`);
      }
      for (const ch of value) {
        if (isSuspiciousLetter(ch)) {
          problems.push(`${where}: non-Latin letter ${JSON.stringify(ch)} in ${JSON.stringify(value)}`);
          break;
        }
      }
      if (value === "") problems.push(`${where}: empty name`);
    }
  }
}

const players = readCsv("players.csv");
const clubs = readCsv("clubs.csv");
const nationalities = readCsv("nationalities.csv");
const aliases = readCsv("player_aliases.csv");

checkNames(players, "players.csv");
checkNames(clubs, "clubs.csv", { allowDigits: true });
checkNames(nationalities, "nationalities.csv");
checkNames(aliases, "player_aliases.csv", { extra: ["alias"], allowDigits: true });

// Referential integrity is enforced again in the Rust loader; checking here
// too means a bad edit fails before anyone waits on a Rust build.
const clubIds = new Set(clubs.map((c) => c.id));
const playerIds = new Set(players.map((p) => p.id));
const natIds = new Set(nationalities.map((n) => n.id));
// The dataset carries permanent moves and loans, and nothing else. A free
// transfer is a permanent move that cost no fee, so it belongs under
// `transfer` rather than in a kind of its own. The core's `Kind` has no
// fallback arm either, so a stray kind fails the load; this catches it here,
// where the fix is a one-character edit rather than a crash on a phone.
const KINDS = new Set(["transfer", "loan"]);
for (const t of readCsv("transfers.csv")) {
  for (const field of ["from_club", "to_club"]) {
    if (!clubIds.has(t[field])) problems.push(`transfers.csv ${t.id}: unknown ${field} ${t[field]}`);
  }
  if (!playerIds.has(t.player_id)) problems.push(`transfers.csv ${t.id}: unknown player ${t.player_id}`);
  if (t.from_club === t.to_club) problems.push(`transfers.csv ${t.id}: club moves to itself`);
  if (!KINDS.has(t.kind)) {
    problems.push(
      `transfers.csv ${t.id}: kind "${t.kind}" is neither transfer nor loan`
    );
  }
}
for (const p of players) {
  if (p.nationality && !natIds.has(p.nationality)) {
    problems.push(`players.csv ${p.id}: unknown nationality ${p.nationality}`);
  }
}
for (const a of aliases) {
  if (!playerIds.has(a.player_id)) problems.push(`player_aliases.csv: unknown player ${a.player_id}`);
}

// Names the transfer card cannot hold on one line. The Spanish column had
// silently taken the registered corporate name where the other two took the
// usual one ("Le Havre Athletic Club Football Association" against "Le Havre"),
// which wrapped the card onto a second line and pushed the Hardcore controls
// off the bottom of the screen. The bug looked like a layout bug for hours.
//
// 21 is where the card starts wrapping at its display size; a genuine name
// longer than that (Wolverhampton Wanderers) belongs in the allowlist below,
// with the point being that adding to it is a decision someone has to make.
const NAME_MAX = 21;
const LONG_ON_PURPOSE = new Set([
  "Bosnia and Herzegovina", // the country's actual name in English
]);
for (const [file, rows] of [["clubs.csv", clubs], ["nationalities.csv", nationalities]]) {
  for (const r of rows) {
    for (const col of ["name_en", "name_fr", "name_es"]) {
      const v = r[col] ?? "";
      if (v.length > NAME_MAX && !LONG_ON_PURPOSE.has(v)) {
        problems.push(
          `${file}: ${r.id} ${col} is ${v.length} chars, over ${NAME_MAX}: ${JSON.stringify(v)}` +
            ` (use the name people say, or add it to LONG_ON_PURPOSE)`
        );
      }
    }
  }
}

if (problems.length) {
  console.error(`data check failed: ${problems.length} problem(s)\n`);
  for (const p of problems) console.error(`  - ${p}`);
  process.exit(1);
}

console.log(
  `data ok: ${players.length} players, ${clubs.length} clubs, ${nationalities.length} nationalities, ${aliases.length} aliases`
);

// Not a failure: the spec says player names are identical across the three
// languages, but the data has legitimate exceptions (transliterations, accents,
// short names versus full names). Listing them makes the genuinely wrong ones
// findable without blocking the build on the many correct ones.
if (REPORT) {
  const diverging = players.filter((p) => !(p.name_en === p.name_fr && p.name_fr === p.name_es));
  console.log(`\n${diverging.length} players whose name differs across languages (review, not a failure):`);
  for (const p of diverging) {
    console.log(`  ${p.id}  en=${JSON.stringify(p.name_en)} fr=${JSON.stringify(p.name_fr)} es=${JSON.stringify(p.name_es)}`);
  }
}
