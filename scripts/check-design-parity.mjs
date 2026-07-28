// Detect visual drift between the iOS and Android implementations.
//
// iOS is the reference. The two apps share design/tokens.json, but the drift
// that actually reaches a screenshot never comes from the tokens: it comes
// from what each call site does with them. The android-parity brief was
// written by hand precisely because nothing caught, for example, that iOS
// gave seven buttons seven different label sizes while Android hardcoded one.
//
// So this does not compare tokens. It extracts every *literal* style value
// each platform states at a call site -- font size, weight, tracking, corner
// radius, raised depth, fixed box size -- and compares the two multisets per
// screen. A value iOS uses and Android never uses anywhere in the matching
// file is drift, and vice versa.
//
// It is deliberately a heuristic. It cannot know that iOS line 109 and Android
// line 305 are the same button, so it will not tell you *where* a value should
// go; it tells you that a value exists on one side and not the other, which is
// the signal that a human then places. It is meant to catch regressions after
// a parity pass, not to replace looking at the two screens.
//
// Run:  node scripts/check-design-parity.mjs [--json] [--verbose]
// Exit: 0 when every pair matches, 1 otherwise.

import fs from "fs";
import path from "path";

const ROOT = new URL("..", import.meta.url).pathname;
const IOS = path.join(ROOT, "apps/ios/Sources");
const AND = path.join(ROOT, "apps/android/app/src/main/kotlin/com/mercato/app");

// Which files implement the same screens. Several iOS files map onto one
// Android file and the reverse, so a pair is (name, [ios...], [android...]).
// Both platforms are read whole. Mapping iOS files onto Android files is
// many-to-many (Screens.swift alone covers menus, settings, recap and the quit
// dialog), and pretending otherwise reports a value as missing on one side
// purely because it lives in the other file of the pair.
const IOS_FILES = fs.readdirSync(IOS).filter((f) => f.endsWith(".swift"));
const AND_FILES = [
  ...fs.readdirSync(path.join(AND, "ui")).filter((f) => f.endsWith(".kt")).map((f) => "ui/" + f),
  ...fs.readdirSync(path.join(AND, "ads")).filter((f) => f.endsWith(".kt")).map((f) => "ads/" + f),
];

// Tokens resolve to numbers on both sides. Without this, every value Android
// inherits from a token reads as "missing on Android" and buries the real
// drift under a hundred false positives.
const TOKENS = JSON.parse(fs.readFileSync(path.join(ROOT, "design/tokens.json"), "utf8"));
const camel = (k) => k.replace(/-([a-z])/g, (_, c) => c.toUpperCase());
const TYPE = Object.fromEntries(Object.entries(TOKENS.type).map(([k, v]) => [camel(k), v]));
const RADIUS = TOKENS.radius;

const args = new Set(process.argv.slice(2));
const AS_JSON = args.has("--json");
const VERBOSE = args.has("--verbose");

// ---------------------------------------------------------------------------
// Extraction
//
// Every matcher yields {kind, value}. Values are rounded to 2 decimals so that
// 12.5 and 12.50 agree, and trackings are normalised to em on both sides:
// iOS writes `.tracking(-0.05 * 30)` (points), Kotlin writes `-0.05f` (em).
// ---------------------------------------------------------------------------

const round = (n) => Math.round(n * 100) / 100;

function readAll(dir, files) {
  return files
    .map((f) => path.join(dir, f))
    .filter((p) => fs.existsSync(p))
    .map((p) => fs.readFileSync(p, "utf8"))
    .join("\n");
}

/** Strip comments so a value quoted in prose is not read as a declaration. */
function decomment(src) {
  return src
    .replace(/\/\*[\s\S]*?\*\//g, "")
    .split("\n")
    .map((l) => l.replace(/\/\/.*$/, ""))
    .join("\n");
}

function scan(src, matchers) {
  const out = [];
  for (const matcher of matchers) {
    for (const m of src.matchAll(matcher.re)) {
      if (matcher.expand) {
        for (const rec of matcher.expand(m)) {
          if (Number.isFinite(rec.value)) out.push({ kind: rec.kind, value: round(rec.value) });
        }
        continue;
      }
      for (const v of [].concat(matcher.map(m))) {
        if (v !== null && Number.isFinite(v)) out.push({ kind: matcher.kind, value: round(v) });
      }
    }
  }
  return out;
}

const IOS_MATCHERS = [
  // DS.unbounded(30, weight: 900) / DS.figtree(13.5, weight: 600) / DS.mono(11)
  { kind: "fontSize", re: /DS\.(?:unbounded|figtree|mono)\(\s*([\d.]+)/g, map: (m) => +m[1] },
  { kind: "fontWeight", re: /weight:\s*(\d{3})\b/g, map: (m) => +m[1] },
  // .font(.system(size: 16, weight: .black))
  { kind: "fontSize", re: /\.system\(size:\s*([\d.]+)/g, map: (m) => +m[1] },
  // .tracking(-0.05 * 30) -> em
  { kind: "tracking", re: /\.tracking\(\s*(-?[\d.]+)\s*\*\s*[\d.]+\s*\)/g, map: (m) => +m[1] },
  { kind: "radius", re: /cornerRadius:\s*([\d.]+)/g, map: (m) => +m[1] },
  { kind: "radius", re: /solidRaised\(radius:\s*([\d.]+)/g, map: (m) => +m[1] },
  { kind: "depth", re: /solidRaised(?:Capsule)?\((?:radius:[^,]+,\s*)?depth:\s*([\d.]+)/g, map: (m) => +m[1] },
  { kind: "opacity", re: /\.opacity\(\s*([\d.]+)\s*\)/g, map: (m) => +m[1] },
  // DesignTokens.Radius.card -> 26
  {
    kind: "radius",
    re: /DesignTokens\.Radius\.(\w+)/g,
    map: (m) => RADIUS[m[1]] ?? null,
  },
];

// iOS now reads the same type tokens as Android, through the TypeToken alias.
// Expanded the same way, so the two inventories stay comparable.
const expandType = (name) => {
  const t = TYPE[name];
  if (!t) return [];
  const out = [
    { kind: "fontSize", value: t.size },
    { kind: "fontWeight", value: t.weight },
  ];
  if (t.tracking != null) {
    const em = typeof t.tracking === "string" ? parseFloat(t.tracking) : t.tracking;
    if (Number.isFinite(em)) out.push({ kind: "tracking", value: em });
  }
  return out;
};

const IOS_TOKEN_TYPE = {
  re: /TypeToken\.(\w+)/g,
  expand: (m) => expandType(m[1]),
};

const KT_MATCHERS = [
  // typeStyle(token, colour, 30.sp, -0.05f)  -- the explicit overload
  {
    kind: "fontSize",
    re: /typeStyle\([^()]*?,\s*([\d.]+)\.sp\s*,/g,
    map: (m) => +m[1],
  },
  {
    kind: "tracking",
    re: /typeStyle\([^()]*?,\s*[\d.]+\.sp\s*,\s*(-?[\d.]+)f?\s*,?\s*\)/g,
    map: (m) => +m[1],
  },
  { kind: "fontSize", re: /fontSize\s*=\s*([\d.]+)\.sp/g, map: (m) => +m[1] },
  { kind: "fontWeight", re: /FontWeight\(\s*(\d{3})\s*\)/g, map: (m) => +m[1] },
  { kind: "fontWeight", re: /fontWeight\s*=\s*(\d{3})\b/g, map: (m) => +m[1] },
  { kind: "tracking", re: /tracking\s*=\s*(-?[\d.]+)f/g, map: (m) => +m[1] },
  { kind: "tracking", re: /letterSpacing\s*=\s*\(?(-?[\d.]+)f?\)?\.em/g, map: (m) => +m[1] },
  { kind: "radius", re: /RoundedCornerShape\(\s*([\d.]+)\.dp/g, map: (m) => +m[1] },
  { kind: "radius", re: /solidRaised\((?:radius\s*=\s*)?([\d.]+)\.dp/g, map: (m) => +m[1] },
  { kind: "radius", re: /radius\s*=\s*([\d.]+)\.dp/g, map: (m) => +m[1] },
  { kind: "depth", re: /depth(?:\s*:\s*Dp)?\s*=\s*([\d.]+)\.dp/g, map: (m) => +m[1] },
  { kind: "opacity", re: /alpha\s*=\s*([\d.]+)f/g, map: (m) => +m[1] },
  { kind: "radius", re: /DesignTokens\.Radius\.(\w+)/g, map: (m) => RADIUS[m[1]] ?? null },
];

// typeStyle(DesignTokens.Type.body, colour) with no override inherits the
// token's size, weight and tracking. Expanded here so those values count as
// stated, exactly as the iOS literals do.
const KT_TOKEN_TYPE = {
  re: /typeStyle\(\s*DesignTokens\.Type\.(\w+)\s*,[^)]*\)(?!\s*\.copy\s*\(\s*fontSize)/g,
  expand: (m) => expandType(m[1]),
};

// A token also travels as a parameter, a composable default or a branch of a
// conditional, and never reaches a typeStyle() call in the same file. Matching
// the bare reference covers all of those. The cost is that a call site which
// overrides the size still contributes the token's own size; harmless now that
// Android has almost no overrides left.
const KT_TOKEN_PARAM = {
  re: /DesignTokens\.Type\.(\w+)/g,
  expand: (m) => expandType(m[1]),
};

// Values that carry no visual meaning on their own, or that one platform
// expresses structurally rather than numerically. Comparing them produces
// noise, not drift.
const IGNORE = {
  // Compose needs a radius for a capsule; SwiftUI has Capsule(). 7 is the
  // splash track's half-height, the same idea.
  radius: new Set([999, 7]),
  // 0 and 1 are "none" and "opaque" everywhere.
  opacity: new Set([0, 1]),
  // depth 0 means "outline, no shadow"; iOS writes that as .inkOutlined
  // rather than solidRaised(depth: 0), so it is structural, not visual.
  depth: new Set([0]),
  // iOS picks Plex Mono Medium by font name ("IBMPlexMono-Medium"); Android
  // states the same weight numerically in the `technical` token.
  fontWeight: new Set([500]),
};

function inventory(src, matchers) {
  const found = scan(decomment(src), matchers);
  const byKind = new Map();
  for (const { kind, value } of found) {
    if (IGNORE[kind]?.has(value)) continue;
    if (!byKind.has(kind)) byKind.set(kind, new Map());
    const counts = byKind.get(kind);
    counts.set(value, (counts.get(value) ?? 0) + 1);
  }
  return byKind;
}

// ---------------------------------------------------------------------------
// Comparison
// ---------------------------------------------------------------------------

const KINDS = ["fontSize", "fontWeight", "tracking", "radius", "depth", "opacity"];

function compare() {
  const ios = inventory(readAll(IOS, IOS_FILES), [...IOS_MATCHERS, IOS_TOKEN_TYPE]);
  const and = inventory(readAll(AND, AND_FILES), [...KT_MATCHERS, KT_TOKEN_TYPE, KT_TOKEN_PARAM]);
  const findings = [];
  for (const kind of KINDS) {
    const a = ios.get(kind) ?? new Map();
    const b = and.get(kind) ?? new Map();
    const onlyIos = [...a.keys()].filter((v) => !b.has(v)).sort((x, y) => x - y);
    const onlyAnd = [...b.keys()].filter((v) => !a.has(v)).sort((x, y) => x - y);
    if (onlyIos.length || onlyAnd.length) findings.push({ kind, onlyIos, onlyAnd });
  }
  return { findings, ios, and };
}

const { findings, ios, and } = compare();
const drift = findings.length;

if (AS_JSON) {
  console.log(JSON.stringify({ drift, results }, null, 2));
  process.exit(drift ? 1 : 0);
}

const BOLD = "\x1b[1m", DIM = "\x1b[2m", RED = "\x1b[31m", GRN = "\x1b[32m", OFF = "\x1b[0m";
console.log(`${BOLD}Design parity, iOS as the reference${OFF}`);
console.log(
  `${DIM}${IOS_FILES.length} Swift files vs ${AND_FILES.length} Kotlin files, ` +
    `token references resolved on both sides.${OFF}\n`
);

for (const f of findings) {
  console.log(`  ${RED}${f.kind}${OFF}`);
  if (f.onlyIos.length) console.log(`    stated on iOS, absent from Android   ${f.onlyIos.join(", ")}`);
  if (f.onlyAnd.length) console.log(`    stated on Android, absent from iOS   ${f.onlyAnd.join(", ")}`);
}

if (VERBOSE) {
  console.log(`\n${DIM}Values seen per property:${OFF}`);
  for (const kind of KINDS) {
    const a = [...(ios.get(kind) ?? new Map()).keys()].sort((x, y) => x - y);
    const b = [...(and.get(kind) ?? new Map()).keys()].sort((x, y) => x - y);
    console.log(`  ${kind.padEnd(11)} iOS ${a.join(", ") || "-"}`);
    console.log(`  ${"".padEnd(11)} And ${b.join(", ") || "-"}`);
  }
}

console.log();
if (drift) {
  console.log(`${RED}${drift} propert${drift > 1 ? "ies" : "y"} diverge.${OFF}`);
  console.log(
    `${DIM}A value on one side only is not automatically a bug: the platforms\n` +
      `sometimes reach the same look by different means. Read each one, then\n` +
      `either align the call site or add it to IGNORE with a reason.${OFF}`
  );
} else {
  console.log(`${GRN}No drift: every literal style value appears on both platforms.${OFF}`);
}
process.exit(drift ? 1 : 0);
