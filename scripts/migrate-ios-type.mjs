// Rebuild design/tokens.json's `type` group from the iOS sources, then move the
// iOS call sites onto it.
//
// iOS is the reference, so this is lossless for iOS by construction: every
// role carries the exact font, size, weight and tracking a call site already
// states. Nothing is snapped, nothing is rounded. What it does remove is the
// hardcoding, and with it the reason the two apps could drift apart.
//
// Three existing tokens turn out to state values iOS never uses. Android was
// inheriting them, which is why the parity pass had to override year and
// club-from by hand at the call site:
//
//   year        42/900/0.01em  ->  32/900, no tracking   (the transfer card)
//   club-from   19/800/-0.04em ->  16/800/-0.04em        (the transfer card)
//   label       10.5/900/0.2em ->  12.5/900/0.16em       (the PTS caption)
//
// Run: node scripts/migrate-ios-type.mjs [--dry]

import fs from "fs";
import path from "path";

const ROOT = new URL("..", import.meta.url).pathname;
const IOS = path.join(ROOT, "apps/ios/Sources");
const TOKENS = path.join(ROOT, "design/tokens.json");
const DRY = process.argv.includes("--dry");

// key = "font/size/weight/tracking", value = kebab-case role name.
// Names describe the job, not the metrics, so a later consolidation can move a
// value without renaming every call site.
const ROLES = {
  // display
  "display/74/900/-0.06": "logo",
  "display/64/900/-": "score-hero",
  "display/32/900/-": "year",
  "display/30/900/-0.05": "screen-title",
  "display/30/900/-": "answer-reveal",
  "display/28/900/-0.05": "recap-title",
  "display/26/900/-": "stat-value",
  "display/26/900/-0.05": "offline-title",
  "display/24/900/-": "card-title",
  "display/24/900/-0.05": "consent-title",
  "display/22/900/-": "score-pill",
  "display/22/900/-0.03": "cta-large",
  "display/22/900/-0.05": "dialog-title",
  "display/20/900/-0.04": "panel-title",
  "display/20/800/-": "toast-title",
  "display/18/900/-0.03": "section-title",
  "display/18/900/-0.04": "purchase-title",
  "display/18/900/0.1": "masked-name",
  "display/18/800/-0.045": "answer",
  "display/17/900/-0.03": "cta-medium",
  "display/16/900/-0.03": "link-title",
  "display/16/800/-0.04": "club-from",
  "display/16/800/-0.03": "cta-small",
  "display/15/900/-": "verdict-chip",
  "display/15/900/-0.03": "badge-value",
  "display/15/800/-0.03": "cta-compact",
  "display/14/900/-0.02": "tab-label",
  "display/14/900/-": "bullet-glyph",
  "display/13.5/800/-0.02": "hint-chip",
  "display/12/800/0.08": "owned-badge",
  "display/11/800/0.14": "card-kind",
  // ui
  "ui/16/700/-": "body-large",
  "ui/15/800/-": "body-strong",
  "ui/15/700/-": "body",
  "ui/15/600/-": "body-soft",
  "ui/14.5/800/-": "row-value",
  "ui/14.5/600/-": "consent-body",
  "ui/14/800/-": "lab-label",
  "ui/14/700/-": "body-mid",
  "ui/13.5/700/-": "lab-line",
  "ui/13/900/0.06": "skip-label",
  "ui/13/800/-": "body-small-strong",
  "ui/13/700/-": "body-small",
  "ui/13/600/-": "lab-note",
  "ui/12.5/900/0.16": "label",
  "ui/12.5/600/-": "lab-caption",
  "ui/12/600/-": "lab-fine",
  "ui/11.5/900/-": "tile-label",
  "ui/11.5/700/-": "footnote",
  // mono
  "mono/12/500/-": "mono-value",
  "mono/11/500/-": "mono-plain",
  "mono/11/500/0.18": "technical",
  "mono/10.5/500/0.18": "ad-label",
  "mono/10.5/500/0.14": "lab-caps",
};

// club-to states the same metrics as screen-title. Keeping both lets the card
// and a heading diverge later without touching either call site.
const ALIASES = { "club-to": "screen-title" };

const FONT = { unbounded: "display", figtree: "ui", mono: "mono" };
const DEFAULT_WEIGHT = { unbounded: 900, figtree: 700, mono: 500 };
const camel = (k) => k.replace(/-([a-z])/g, (_, c) => c.toUpperCase());
const num = (s) => (Number.isInteger(+s) ? +s : +(+s).toFixed(2));
// Tracking needs three decimals: -0.045em is a real value and rounding it to
// -0.04 silently loses the `answer` role.
const trk = (s) => (Number.isInteger(+s) ? +s : +(+s).toFixed(3));

// --- 1. rebuild the type group -------------------------------------------
const tokens = JSON.parse(fs.readFileSync(TOKENS, "utf8"));
const before = { ...tokens.type };
const type = {};
for (const [key, name] of Object.entries(ROLES)) {
  const [font, size, weight, tracking] = key.split("/");
  const entry = { font, weight: +weight, size: num(size) };
  if (tracking !== "-") entry.tracking = `${tracking}em`;
  // carry over leading/transform a previous role declared
  const old = before[name];
  if (old?.leading != null) entry.leading = old.leading;
  if (old?.transform != null) entry.transform = old.transform;
  type[name] = entry;
}
for (const [alias, target] of Object.entries(ALIASES)) type[alias] = { ...type[target] };
tokens.type = type;

const changed = Object.keys(type).filter((k) => JSON.stringify(type[k]) !== JSON.stringify(before[k]));
const added = Object.keys(type).filter((k) => !(k in before));
const removed = Object.keys(before).filter((k) => !(k in type));

console.log(`type roles: ${Object.keys(before).length} -> ${Object.keys(type).length}`);
console.log(`  added ${added.length}, retargeted ${changed.length - added.length}, removed ${removed.length}`);
for (const k of changed.filter((k) => !added.includes(k))) {
  const f = (v) => v && `${v.font}/${v.size}/${v.weight}/${v.tracking ?? "-"}`;
  console.log(`  retargeted  ${k.padEnd(14)} ${f(before[k])}  ->  ${f(type[k])}`);
}
if (removed.length) console.log(`  removed     ${removed.join(", ")}`);

// --- 2. move the iOS call sites onto the tokens ---------------------------
let rewritten = 0, skipped = [];
for (const file of fs.readdirSync(IOS).filter((f) => f.endsWith(".swift"))) {
  const p = path.join(IOS, file);
  const lines = fs.readFileSync(p, "utf8").split("\n");
  const out = [];
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    const m = line.match(/DS\.(unbounded|figtree|mono)\(\s*([\d.]+)(?:\s*,\s*weight:\s*(\d{3}))?\s*\)/);
    if (!m) { out.push(line); continue; }
    const font = FONT[m[1]];
    const size = num(m[2]);
    const weight = m[3] ? +m[3] : DEFAULT_WEIGHT[m[1]];
    const next = lines[i + 1] ?? "";
    const t = next.match(/^\s*\.tracking\(\s*(-?[\d.]+)\s*\*\s*[\d.]+\s*\)\s*$/);
    const key = `${font}/${size}/${weight}/${t ? trk(t[1]) : "-"}`;
    const role = ROLES[key];
    if (!role) { skipped.push(`${file}:${i + 1}  ${key}`); out.push(line); continue; }
    // `Type` is a Swift metatype keyword; DesignSystem.swift aliases the
    // generated namespace to TypeToken so call sites stay readable.
    const token = `TypeToken.${camel(role)}`;

    // `.font(DS.x(...))` as a modifier takes the whole style, tracking too.
    // `font: DS.x(...)` is a parameter, so only the Font can be substituted.
    if (/\.font\(\s*DS\./.test(line)) {
      out.push(line.replace(/\.font\(\s*DS\.[^)]*\)\s*\)?/, `.typeStyle(${token})`).replace(/\)\s*$/, ")"));
      if (t) i++; // the tracking modifier is folded into typeStyle
      rewritten++;
      continue;
    }
    out.push(line.replace(/DS\.(?:unbounded|figtree|mono)\([^)]*\)/, `DS.font(${token})`));
    rewritten++;
  }
  if (!DRY) fs.writeFileSync(p, out.join("\n"));
}

console.log(`\ncall sites rewritten: ${rewritten}`);
if (skipped.length) {
  console.log(`left alone (no role, check by hand): ${skipped.length}`);
  for (const s of skipped) console.log(`  ${s}`);
}
if (!DRY) fs.writeFileSync(TOKENS, JSON.stringify(tokens, null, 2) + "\n");
else console.log("\n--dry: nothing written");
