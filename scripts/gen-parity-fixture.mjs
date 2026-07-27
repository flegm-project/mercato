// Generate parity fixtures for the Rust engine port.
//
// Oracle = the verbatim engine in core/reference/engine.reference.js run
// against the prototype's inline trilingual DATA (reference/web-prototype/
// mercato.html). Emits:
//   - players.json      corpus in DATA insertion order (Rust Player shape)
//   - cases.json        matchAnswer verdicts for many (guess, player) pairs
//   - decoy_cases.json  distractorsFor outputs for seeded RNGs
//
// Run: node scripts/gen-parity-fixture.mjs
import fs from "fs";

const ROOT = new URL("..", import.meta.url).pathname;
const OUT = ROOT + "core/mercato-core/tests/fixtures/";

// --- load inline DATA -------------------------------------------------------
const html = fs.readFileSync(ROOT + "reference/web-prototype/mercato.html", "utf8");
const dataLine = html.split(/\r?\n/).find((l) => l.trimStart().startsWith("const DATA"));
const DATA = JSON.parse(dataLine.slice(dataLine.indexOf("{"), dataLine.lastIndexOf("}") + 1));
const PLAYERS = DATA.players;

// --- load and evaluate the verbatim engine ---------------------------------
// The engine file references the PLAYERS/CLUBS globals; provide them, then
// expose the functions we need.
const engineSrc = fs.readFileSync(ROOT + "core/reference/engine.reference.js", "utf8");
const harness = `
${engineSrc}
return { normalize, matchAnswer, distractorsFor, mulberry32, hashStr };
`;
const make = new Function("PLAYERS", "CLUBS", harness);
const ENGINE = make(PLAYERS, DATA.clubs);

// --- players.json (insertion order) ----------------------------------------
const ids = Object.keys(PLAYERS);
const posSet = new Set();
const players = ids.map((id) => {
  const p = PLAYERS[id];
  if (p.pos) posSet.add(p.pos);
  return {
    id,
    name_en: p.n.en,
    name_fr: p.n.fr,
    name_es: p.n.es,
    aliases: p.alt || [],
    position: p.pos || null,
    nationality: p.nat || null,
    birth_year: p.b ?? null,
    notoriety: p.l ?? 0,
  };
});
const badPos = [...posSet].filter((x) => !["gk", "def", "mid", "fw"].includes(x));
if (badPos.length) throw new Error("unexpected position values: " + badPos.join(","));

// --- cases.json -------------------------------------------------------------
const BASE = 2;
const norm = ENGINE.normalize;
const bareSurname = (name) => {
  // last token of the normalized name
  const tk = norm(name).split(" ").filter(Boolean);
  return tk[tk.length - 1] || "";
};
const record = (guess, id) => {
  const r = ENGINE.matchAnswer(guess, id, BASE);
  return {
    guess,
    player_id: id,
    route: r.route,
    ok: r.ok,
    dist: r.dist === Infinity ? null : r.dist,
    ambiguous: r.ambiguous,
  };
};

const cases = [];
ids.forEach((id, i) => {
  const p = PLAYERS[id];
  const seen = new Set();
  const push = (g) => {
    if (g == null) return;
    const key = g + "|" + id;
    if (seen.has(key)) return;
    seen.add(key);
    cases.push(record(g, id));
  };
  // exact canonical (all three languages)
  push(p.n.en);
  push(p.n.fr);
  push(p.n.es);
  // pre-normalized input (accent/case-stripped) -> exact
  push(norm(p.n.en));
  // alias -> alias route (when present)
  if (p.alt && p.alt[0]) push(p.alt[0]);
  // bare surname -> surname / ambiguous
  push(bareSurname(p.n.en));
  // one-char deletion typo on the english name -> fuzzy or none
  const e = p.n.en;
  if (e.length > 1) push(e.slice(0, -1));
  // clearly-wrong guess -> none
  push("zzzzzzzz");
  // cross-player surname (exercises the exact-beats-fuzzy cardinal rule)
  const other = PLAYERS[ids[(i + 1) % ids.length]];
  push(bareSurname(other.n.en));
});

// --- decoy_cases.json -------------------------------------------------------
const seeds = [1, 12345, 424242, 4294967295];
const decoyCases = [];
const sampleEvery = 17; // spread across the corpus without exploding the file
ids.forEach((id, i) => {
  if (i % sampleEvery !== 0) return;
  for (const seed of seeds) {
    const rng = ENGINE.mulberry32(seed);
    const expected = ENGINE.distractorsFor(id, rng);
    decoyCases.push({ player_id: id, seed, expected });
  }
});

// --- write ------------------------------------------------------------------
fs.mkdirSync(OUT, { recursive: true });
fs.writeFileSync(OUT + "players.json", JSON.stringify(players));
fs.writeFileSync(OUT + "cases.json", JSON.stringify(cases));
fs.writeFileSync(OUT + "decoy_cases.json", JSON.stringify(decoyCases));

console.log(
  `players=${players.length} cases=${cases.length} decoy_cases=${decoyCases.length} positions=${[...posSet].join("/")}`
);
