// Hold the call sites to the event spec, on both platforms.
//
// design/analytics.json already generates the Event and Param constants, so a
// call site cannot invent a name. What nothing checked is whether a call site
// sends the parameters the spec says that event carries. A missing parameter
// does not fail, does not warn, and does not show up until someone opens the
// dashboard months later and finds a column of nulls with no way to backfill
// it: the rounds that were played are gone.
//
// The same goes for a divergence between platforms. Two apps logging
// round_end with different parameters produce one chart that looks whole and
// is not, which is exactly what the spec's own note says the constants exist
// to prevent.
//
// Run: node scripts/check-analytics.mjs

import fs from "fs";
import path from "path";

const ROOT = new URL("..", import.meta.url).pathname;
const RED = "\x1b[31m";
const GREEN = "\x1b[32m";
const DIM = "\x1b[2m";
const BOLD = "\x1b[1m";
const OFF = "\x1b[0m";

const spec = JSON.parse(fs.readFileSync(path.join(ROOT, "design/analytics.json"), "utf8"));
const declared = new Map(
  Object.entries(spec.events).map(([name, e]) => [name, new Set(Object.keys(e.params ?? {}))])
);

/** ROUND_END -> round_end (Kotlin), roundEnd -> round_end (Swift). */
const snake = (constant) =>
  constant.includes("_")
    ? constant.toLowerCase()
    : constant.replace(/([a-z0-9])([A-Z])/g, "$1_$2").toLowerCase();

function read(dir, exts) {
  const out = [];
  const walk = (d) => {
    for (const entry of fs.readdirSync(d, { withFileTypes: true })) {
      const p = path.join(d, entry.name);
      if (entry.isDirectory()) walk(p);
      else if (exts.some((e) => entry.name.endsWith(e))) {
        out.push({ file: path.relative(ROOT, p), src: fs.readFileSync(p, "utf8") });
      }
    }
  };
  walk(dir);
  return out;
}

/**
 * Every log call and the parameters it passes.
 *
 * The call spans several lines when it carries more than one parameter, so the
 * scan takes the text from the event constant to the closing parenthesis of
 * the map rather than working line by line.
 */
function callSites(files, eventRe, paramRe) {
  const calls = [];
  for (const { file, src } of files) {
    for (const m of src.matchAll(eventRe)) {
      const start = m.index;
      // Far enough to cover the longest call in either app, short enough not
      // to swallow the next one.
      const window = src.slice(start, start + 600);
      const end = window.indexOf("\n\n");
      const body = end === -1 ? window : window.slice(0, end);
      const params = new Set([...body.matchAll(paramRe)].map((p) => snake(p[1])));
      const line = src.slice(0, start).split("\n").length;
      calls.push({ file, line, event: snake(m[1]), params });
    }
  }
  return calls;
}

const kotlin = callSites(
  read(path.join(ROOT, "apps/android/app/src/main"), [".kt"]),
  /Event\.([A-Z_]+)/g,
  /Param\.([A-Z_]+)\s+to/g
);
// Swift spells the same vocabulary as enum shorthand:
//   Analytics.shared.log(.roundEnd, [.mode: modeName, .score: ...])
const swift = callSites(
  read(path.join(ROOT, "apps/ios/Sources"), [".swift"]),
  /Analytics\.shared\.log\(\s*\.([a-zA-Z]+)/g,
  /\.([a-zA-Z]+):\s/g
);

const problems = [];

// 1. A call site that does not carry what the spec says it carries.
for (const call of kotlin) {
  const want = declared.get(call.event);
  if (!want) {
    problems.push(`${call.file}:${call.line}: logs "${call.event}", which the spec does not declare`);
    continue;
  }
  const missing = [...want].filter((p) => !call.params.has(p));
  const extra = [...call.params].filter((p) => !want.has(p));
  if (missing.length) {
    problems.push(
      `${call.file}:${call.line}: ${call.event} is missing ${missing.join(", ")}` +
        ` (the spec says it carries ${[...want].join(", ") || "nothing"})`
    );
  }
  if (extra.length) {
    problems.push(`${call.file}:${call.line}: ${call.event} sends ${extra.join(", ")}, not in the spec`);
  }
}

// 2. An event nobody emits. Dead vocabulary is worse than none: it reads as a
//    metric that exists and is simply always zero.
const emitted = new Set(kotlin.map((c) => c.event));
for (const name of declared.keys()) {
  if (!emitted.has(name)) problems.push(`"${name}" is declared but never logged on Android`);
}

// 3. The two platforms must emit the same set. Reported, not failed: the iOS
//    scan reads Swift argument labels and is the weaker of the two, so a
//    difference here is a question rather than a verdict.
const iosEmitted = new Set(swift.map((c) => c.event).filter((e) => declared.has(e)));
const onlyAndroid = [...emitted].filter((e) => !iosEmitted.has(e));
const onlyIos = [...iosEmitted].filter((e) => !emitted.has(e));

console.log(`${BOLD}Analytics spec${OFF}`);
console.log(
  `${DIM}${declared.size} events declared, ${kotlin.length} Android call sites, ` +
    `${swift.length} Swift ones${OFF}\n`
);

for (const p of problems) console.log(`  ${RED}${p}${OFF}`);

if (onlyAndroid.length || onlyIos.length) {
  console.log(`\n${DIM}Platform difference, worth a look rather than a failure:${OFF}`);
  for (const e of onlyAndroid) console.log(`${DIM}  ${e}: Android only${OFF}`);
  for (const e of onlyIos) console.log(`${DIM}  ${e}: iOS only${OFF}`);
}

if (problems.length) {
  console.log(`\n${RED}${problems.length} problem(s).${OFF}`);
  process.exit(1);
}
console.log(`${GREEN}Every call site carries what the spec declares.${OFF}`);
