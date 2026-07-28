// Inventory every hardcoded text style in the iOS sources.
//
// iOS states its typography literally at each call site (DS.unbounded(30,
// weight: 900) followed by .tracking(-0.05 * 30)) while Android reads
// design/tokens.json. That asymmetry is why the two drifted, and why the
// parity checker has to guess.
//
// This lists every literal, groups identical ones, and proposes a token name
// derived from the surrounding code, so the move to tokens can be reviewed as
// data before a single Swift line changes.
//
// Run: node scripts/audit-ios-type.mjs [--json]

import fs from "fs";
import path from "path";

const ROOT = new URL("..", import.meta.url).pathname;
const DIR = path.join(ROOT, "apps/ios/Sources");
const AS_JSON = process.argv.includes("--json");

const FONT = { unbounded: "display", figtree: "ui", mono: "mono" };
// DS.unbounded and DS.figtree default their weight; DS.mono has one face.
const DEFAULT_WEIGHT = { unbounded: 900, figtree: 700, mono: 500 };

/** Nearest enclosing struct, and the nearest string key above the call. */
function context(lines, i) {
  let struct = null;
  for (let j = i; j >= 0 && !struct; j--) {
    const m = lines[j].match(/^\s*(?:private\s+)?struct\s+(\w+)/);
    if (m) struct = m[1];
  }
  let key = null;
  for (let j = i; j >= Math.max(0, i - 3) && !key; j--) {
    const m = lines[j].match(/L\("([\w.]+)"\)|Text\("([^"]{1,16})"\)/);
    if (m) key = m[1] ?? m[2];
  }
  let fn = null;
  for (let j = i; j >= 0 && !fn; j--) {
    const m = lines[j].match(/^\s*(?:private\s+)?(?:var|func)\s+(\w+)/);
    if (m) fn = m[1];
  }
  return { struct, fn, key };
}

const sites = [];
for (const file of fs.readdirSync(DIR).filter((f) => f.endsWith(".swift"))) {
  const lines = fs.readFileSync(path.join(DIR, file), "utf8").split("\n");
  lines.forEach((line, i) => {
    const m = line.match(/DS\.(unbounded|figtree|mono)\(\s*([\d.]+)(?:\s*,\s*weight:\s*(\d{3}))?\s*\)/);
    if (!m) return;
    const [, fam, size, weight] = m;
    // .tracking() sits on the next line in every current call site, but allow
    // it on the same line too.
    const near = line + "\n" + (lines[i + 1] ?? "");
    const t = near.match(/\.tracking\(\s*(-?[\d.]+)\s*\*\s*[\d.]+\s*\)/);
    sites.push({
      file,
      line: i + 1,
      font: FONT[fam],
      size: +size,
      weight: weight ? +weight : DEFAULT_WEIGHT[fam],
      tracking: t ? +t[1] : null,
      ...context(lines, i),
    });
  });
}

const keyOf = (s) => `${s.font}/${s.size}/${s.weight}/${s.tracking ?? "-"}`;
const groups = new Map();
for (const s of sites) {
  if (!groups.has(keyOf(s))) groups.set(keyOf(s), []);
  groups.get(keyOf(s)).push(s);
}

/** kebab-case name from the most telling context in the group. */
function propose(members) {
  const kebab = (s) =>
    s
      .replace(/([a-z0-9])([A-Z])/g, "$1-$2")
      .replace(/[^A-Za-z0-9]+/g, "-")
      .toLowerCase()
      .replace(/^-|-$/g, "");
  const withKey = members.find((m) => m.key);
  const base = withKey?.key ?? members[0].fn ?? members[0].struct ?? "text";
  return kebab(base).slice(0, 28);
}

const rows = [...groups.entries()]
  .map(([key, members]) => ({
    key,
    uses: members.length,
    proposed: propose(members),
    ...members[0],
    where: members.map((m) => `${m.file}:${m.line}`),
  }))
  .sort((a, b) => b.uses - a.uses || b.size - a.size);

if (AS_JSON) {
  console.log(JSON.stringify(rows, null, 2));
  process.exit(0);
}

console.log(`${sites.length} hardcoded text styles, ${rows.length} distinct combinations\n`);
console.log("uses  font     size  wt   track   proposed name          first use");
for (const r of rows) {
  console.log(
    String(r.uses).padStart(4) +
      "  " + r.font.padEnd(8) +
      String(r.size).padStart(5) + "  " +
      String(r.weight).padEnd(4) +
      String(r.tracking ?? "-").padStart(6) + "   " +
      r.proposed.padEnd(22) +
      r.where[0]
  );
}

// Sizes reached by more than one weight or tracking are where the drift lives.
const bySize = new Map();
for (const r of rows) {
  if (!bySize.has(r.size)) bySize.set(r.size, []);
  bySize.get(r.size).push(r);
}
const crowded = [...bySize.entries()].filter(([, v]) => v.length > 1).sort((a, b) => b[1].length - a[1].length);
if (crowded.length) {
  console.log(`\n${crowded.length} sizes carry more than one weight or tracking:`);
  for (const [size, v] of crowded) {
    console.log(
      `  ${String(size).padStart(5)}  ` +
        v.map((r) => `${r.weight}/${r.tracking ?? "-"}`).join("  ")
    );
  }
}

// ---------------------------------------------------------------------------
// --propose: cluster the combinations into a scale a human would defend.
//
// Two call sites belong together when they use the same font family and the
// same weight band, and their sizes are within one point. The cluster keeps
// the most-used size, so the busiest screens stay pixel-identical and the
// one-off outliers move.
// ---------------------------------------------------------------------------
if (process.argv.includes("--propose")) {
  const band = (w) => (w >= 900 ? 900 : w >= 800 ? 800 : w >= 700 ? 700 : 500);
  // Tracking is not a free parameter: positive tracking means letterspaced
  // caps, negative means tight display text, absent means neither. Merging
  // across those turns a caps label into a heading, so they stay apart, and
  // even inside a class the gap is capped.
  const cls = (t) => (t == null ? 0 : t > 0 ? 1 : -1);
  const clusters = [];
  for (const r of [...rows].sort((a, b) => b.uses - a.uses || b.size - a.size)) {
    const hit = clusters.find(
      (c) =>
        c.font === r.font &&
        band(c.weight) === band(r.weight) &&
        Math.abs(c.size - r.size) <= 1 &&
        cls(c.tracking) === cls(r.tracking) &&
        Math.abs((c.tracking ?? 0) - (r.tracking ?? 0)) <= 0.02
    );
    if (hit) {
      hit.members.push(r);
      hit.uses += r.uses;
    } else {
      clusters.push({ font: r.font, size: r.size, weight: r.weight, tracking: r.tracking, uses: r.uses, members: [r] });
    }
  }

  let same = 0, moved = 0;
  const deltas = [];
  for (const c of clusters) {
    for (const m of c.members) {
      const dSize = +(c.size - m.size).toFixed(2);
      const dWeight = c.weight - m.weight;
      const dTrack = +((c.tracking ?? 0) - (m.tracking ?? 0)).toFixed(3);
      if (!dSize && !dWeight && !dTrack) same += m.uses;
      else {
        moved += m.uses;
        deltas.push({ ...m, toSize: c.size, toWeight: c.weight, toTracking: c.tracking, dSize, dWeight, dTrack });
      }
    }
  }

  console.log(`\n\nProposed scale: ${clusters.length} roles for ${sites.length} call sites`);
  console.log(`  ${same} sites unchanged, ${moved} sites shift\n`);
  console.log("uses  font     size  wt   track   covers");
  for (const c of clusters.sort((a, b) => b.uses - a.uses)) {
    console.log(
      String(c.uses).padStart(4) + "  " + c.font.padEnd(8) +
      String(c.size).padStart(5) + "  " + String(c.weight).padEnd(4) +
      String(c.tracking ?? "-").padStart(6) + "   " +
      c.members.map((m) => `${m.size}/${m.weight}/${m.tracking ?? "-"}`).join("  ")
    );
  }

  if (deltas.length) {
    console.log(`\nSites that shift (iOS is the reference, so each is a design change):`);
    for (const d of deltas.sort((a, b) => Math.abs(b.dSize) - Math.abs(a.dSize))) {
      const bits = [];
      if (d.dSize) bits.push(`size ${d.size} -> ${d.toSize}`);
      if (d.dWeight) bits.push(`weight ${d.weight} -> ${d.toWeight}`);
      if (d.dTrack) bits.push(`tracking ${d.tracking ?? "none"} -> ${d.toTracking ?? "none"}`);
      console.log(`  ${d.where[0].padEnd(26)} ${bits.join(", ")}`);
    }
  }
}
