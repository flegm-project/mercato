// Export the players whose name differs across EN/FR/ES, for human review.
//
// The spec says player names are identical in all three languages, so every
// difference is either a legitimate variant (an accent, a transliteration, a
// short name versus a full one) or a defect inherited from the Wikidata
// labels. Telling those apart needs judgement about real people, so this only
// gathers the evidence and sorts it: it never edits the dataset.
//
// Each row carries the Wikidata id so a claim can be checked at the source,
// plus the position, nationality and birth year, which are usually enough to
// identify who is meant.
//
// Emits build/review/player-name-review.csv (and .md for reading).
// Run: node scripts/export-name-review.mjs
import fs from "fs";
import path from "path";

const ROOT = new URL("..", import.meta.url).pathname;
const DATA = path.join(ROOT, "data");
const OUT = path.join(ROOT, "build/review");

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

const players = readCsv("players.csv");
const nationalities = new Map(readCsv("nationalities.csv").map((n) => [n.id, n.name_en]));
const aliases = new Map();
for (const a of readCsv("player_aliases.csv")) {
  if (!aliases.has(a.player_id)) aliases.set(a.player_id, []);
  aliases.get(a.player_id).push(a.alias);
}

// Same normalisation the matching engine uses, so "differs only by accent" here
// means the same thing it means to the game.
const fold = (s) =>
  s.normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .replace(/[ØøŁłÐðÞþ]/g, (m) => ({ "Ø": "O", ø: "o", "Ł": "L", ł: "l", "Ð": "D", ð: "d", "Þ": "T", þ: "t" }[m]))
    .toLowerCase()
    .replace(/[.'’`\-_]/g, " ")
    .replace(/[^a-z0-9 ]/g, "")
    .replace(/\s+/g, " ")
    .trim();

const words = (s) => new Set(fold(s).split(" ").filter(Boolean));
const isSubset = (a, b) => [...a].every((w) => b.has(w));

/// A first pass so the reviewer can start with the rows most likely wrong.
/// These are hints, not verdicts.
function classify(en, fr, es) {
  const forms = [en, fr, es];
  const folded = forms.map(fold);
  if (new Set(folded).size === 1) return "accents only";

  const sets = forms.map(words);
  // One form contains all the words of another: a short name versus a full one.
  const nested = sets.every((s) => isSubset(s, sets[0]) || isSubset(sets[0], s))
    || sets.every((s) => isSubset(s, sets[1]) || isSubset(sets[1], s))
    || sets.every((s) => isSubset(s, sets[2]) || isSubset(sets[2], s));
  if (nested) return "short vs full name";

  // Same surname, differently spelled first names: usually a transliteration.
  const lastWords = new Set(folded.map((f) => f.split(" ").pop()));
  if (lastWords.size === 1) return "transliteration";

  // Nothing links the forms: most likely a genuine defect.
  return "REVIEW: forms disagree";
}

const diverging = players
  .filter((p) => !(p.name_en === p.name_fr && p.name_fr === p.name_es))
  .map((p) => ({
    id: p.id,
    wikidata: `https://www.wikidata.org/wiki/${p.id}`,
    name_en: p.name_en,
    name_fr: p.name_fr,
    name_es: p.name_es,
    position: p.position,
    nationality: nationalities.get(p.nationality) ?? p.nationality,
    birth_year: p.birth_year,
    aliases: (aliases.get(p.id) ?? []).join(" | "),
    hint: classify(p.name_en, p.name_fr, p.name_es),
  }));

// Most likely defects first.
const order = { "REVIEW: forms disagree": 0, "short vs full name": 1, transliteration: 2, "accents only": 3 };
diverging.sort((a, b) => order[a.hint] - order[b.hint] || a.name_en.localeCompare(b.name_en));

fs.mkdirSync(OUT, { recursive: true });

const cols = ["id", "wikidata", "name_en", "name_fr", "name_es", "position", "nationality", "birth_year", "aliases", "hint"];
const cell = (v) => {
  const s = v == null ? "" : String(v);
  return /[",\n]/.test(s) ? '"' + s.replace(/"/g, '""') + '"' : s;
};
fs.writeFileSync(
  path.join(OUT, "player-name-review.csv"),
  cols.join(",") + "\n" + diverging.map((r) => cols.map((c) => cell(r[c])).join(",")).join("\n") + "\n"
);

const counts = diverging.reduce((acc, r) => ((acc[r.hint] = (acc[r.hint] ?? 0) + 1), acc), {});
const md = [
  "# Player names that differ across EN / FR / ES",
  "",
  "The spec says a player's name is the same in all three languages, so every row",
  "here is either a legitimate variant or a defect inherited from the Wikidata",
  "labels. The `hint` column is a mechanical first pass, not a verdict.",
  "",
  "Deciding needs judgement about real people, so nothing here has been changed.",
  "Check a claim against the linked Wikidata entry before acting on it.",
  "",
  `Total: ${diverging.length} of ${players.length} players.`,
  "",
  ...Object.entries(counts).map(([k, v]) => `- ${v} ${k}`),
  "",
  "| id | EN | FR | ES | position | nationality | born | hint |",
  "| --- | --- | --- | --- | --- | --- | --- | --- |",
  ...diverging.map(
    (r) =>
      `| [${r.id}](${r.wikidata}) | ${r.name_en} | ${r.name_fr} | ${r.name_es} | ${r.position} | ${r.nationality} | ${r.birth_year} | ${r.hint} |`
  ),
  "",
].join("\n");
fs.writeFileSync(path.join(OUT, "player-name-review.md"), md);

console.log(`${diverging.length} diverging players written to build/review/`);
for (const [k, v] of Object.entries(counts)) console.log(`  ${String(v).padStart(3)}  ${k}`);
