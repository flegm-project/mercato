// Generate native localized-string resources for the iOS and Android apps.
//
// design/strings.json is the single source of truth for every UI string,
// keyed by language (top-level "en", "fr", "es" objects, each with the same
// key set). This script validates that JSON, then walks it structurally to
// derive Apple .strings files and Android strings.xml resources: it never
// hardcodes a string value, so editing strings.json and rerunning changes
// the generated output accordingly.
//
// English is the fallback language (see docs/GAME_DESIGN.md: the app reads
// the system language and falls back to English for anything else, there is
// no in-app language picker), so it is required even if fr/es were absent.
//
// Nested values (the "ob" onboarding-slide array and the "cnPoints" bullet
// list) are flattened into dotted keys, e.g. "ob.0.a", "cnPoints.1", so every
// leaf string still gets its own resource entry.
//
// Validation runs first and reports every problem it finds (not just the
// first) before exiting non-zero, so a translator can fix everything in one
// pass:
//   - all three languages must have exactly the same set of (flattened) keys
//   - no empty or whitespace-only values
//   - English must be present, since it is the fallback
//
// Emits:
//   build/strings/ios/en.lproj/Localizable.strings
//   build/strings/ios/fr.lproj/Localizable.strings
//   build/strings/ios/es.lproj/Localizable.strings
//   build/strings/android/values/strings.xml      (English, the default)
//   build/strings/android/values-fr/strings.xml
//   build/strings/android/values-es/strings.xml
//
// Output is generated and gitignored (see /build/ in .gitignore), the same
// convention scripts/build-native.sh and scripts/gen-design-tokens.mjs use
// for the UniFFI bindings and the design tokens.
//
// Run: node scripts/gen-strings.mjs
import fs from "fs";
import path from "path";

const ROOT = new URL("..", import.meta.url).pathname;
const STRINGS_PATH = path.join(ROOT, "design/strings.json");
const OUT_DIR = path.join(ROOT, "build/strings");

const LANGUAGES = ["en", "fr", "es"];
const FALLBACK_LANGUAGE = "en";

const die = (msg) => {
  console.error(`error: ${msg}`);
  process.exit(1);
};

let raw;
try {
  raw = fs.readFileSync(STRINGS_PATH, "utf8");
} catch (e) {
  die(`cannot read ${STRINGS_PATH}: ${e.message}`);
}
let data;
try {
  data = JSON.parse(raw);
} catch (e) {
  die(`invalid JSON in ${STRINGS_PATH}: ${e.message}`);
}
if (data === null || typeof data !== "object" || Array.isArray(data)) {
  die(`${STRINGS_PATH} must contain a top-level JSON object keyed by language`);
}

// --- flatten each language's string tree into "dotted.key" -> value --------
// Strings can be nested inside objects (none currently) or arrays of objects
// (the "ob" onboarding slides) or arrays of strings (the "cnPoints" bullet
// list). Flattening both validates structural shape (mismatched shapes
// across languages surface as mismatched flattened key sets, see below) and
// gives every emitter a single flat key -> string map to work from.
function flattenValue(value, key, out) {
  if (typeof value === "string") {
    out.set(key, value);
    return;
  }
  if (Array.isArray(value)) {
    value.forEach((item, i) => flattenValue(item, `${key}.${i}`, out));
    return;
  }
  if (value !== null && typeof value === "object") {
    for (const [k, v] of Object.entries(value)) {
      flattenValue(v, `${key}.${k}`, out);
    }
    return;
  }
  // Non-string scalar (number, boolean, null): keep the key so it still
  // shows up in key-set comparisons, but flag it as an invalid type below.
  out.set(key, value);
}
function flattenLanguage(langObj) {
  const out = new Map();
  for (const [k, v] of Object.entries(langObj)) {
    flattenValue(v, k, out);
  }
  return out;
}

// --- validate ----------------------------------------------------------------
const errors = [];
const flattened = {}; // lang -> Map(key -> value)

for (const lang of LANGUAGES) {
  if (!(lang in data)) {
    errors.push(
      `language "${lang}" is missing entirely from ${path.relative(ROOT, STRINGS_PATH)}` +
        (lang === FALLBACK_LANGUAGE ? " (this is the fallback language and is required)" : "")
    );
    continue;
  }
  const langObj = data[lang];
  if (langObj === null || typeof langObj !== "object" || Array.isArray(langObj)) {
    errors.push(`"${lang}" must be an object of string keys, got ${Array.isArray(langObj) ? "array" : typeof langObj}`);
    continue;
  }
  flattened[lang] = flattenLanguage(langObj);
}

const presentLanguages = LANGUAGES.filter((l) => flattened[l]);

// Type and emptiness checks, per language.
for (const lang of presentLanguages) {
  for (const [key, value] of flattened[lang].entries()) {
    if (typeof value !== "string") {
      errors.push(`${lang}.${key}: expected a string, got ${value === null ? "null" : typeof value} (${JSON.stringify(value)})`);
    } else if (value.trim().length === 0) {
      errors.push(`${lang}.${key}: empty or whitespace-only value`);
    }
  }
}

// Key-set parity across languages.
const keySets = {};
for (const lang of presentLanguages) keySets[lang] = new Set(flattened[lang].keys());
const unionKeys = new Set();
for (const lang of presentLanguages) for (const k of keySets[lang]) unionKeys.add(k);

for (const lang of presentLanguages) {
  const missing = [...unionKeys].filter((k) => !keySets[lang].has(k)).sort();
  if (missing.length > 0) {
    errors.push(`"${lang}" is missing ${missing.length} key(s) present in other languages: ${missing.join(", ")}`);
  }
}
for (const key of [...unionKeys].sort()) {
  const presentIn = presentLanguages.filter((lang) => keySets[lang].has(key));
  if (presentIn.length === 1) {
    errors.push(`key "${key}" is present only in "${presentIn[0]}"`);
  }
}

if (errors.length > 0) {
  console.error(`validation failed for ${path.relative(ROOT, STRINGS_PATH)}: ${errors.length} problem(s) found\n`);
  for (const e of errors) console.error(`  - ${e}`);
  process.exit(1);
}

// --- escaping ------------------------------------------------------------------
// Apple .strings: backslashes and double quotes must be escaped; a real
// newline would break the single-line "key" = "value"; grammar so it is
// escaped too, even though none of the current values contain one.
function appleEscape(s) {
  return s.replace(/\\/g, "\\\\").replace(/"/g, '\\"').replace(/\n/g, "\\n");
}

// Android string resources: XML-escape &, < and > (text content, so quotes
// don't need escaping), and apply Android's own apostrophe rule: a literal
// ' must be written \' or the resource fails to compile. Escape the
// backslash itself first so the apostrophe/newline escapes below don't get
// double-escaped.
function androidEscape(s) {
  return s
    .replace(/\\/g, "\\\\")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/'/g, "\\'")
    .replace(/\n/g, "\\n");
}

// Android resource names may not contain ".", only letters/digits/underscore
// (and may not start with a digit); flattened keys like "ob.0.a" become
// "ob_0_a". Flattened keys never start with a digit (the first segment is
// always an object key), so this is the only transform needed.
function androidResourceName(key) {
  return key.replace(/\./g, "_");
}

const GENERATED_NOTE = [
  "GENERATED FILE. DO NOT EDIT BY HAND.",
  `Derived from ${path.relative(ROOT, STRINGS_PATH)}. Regenerate with:`,
  "  node scripts/gen-strings.mjs",
];

// --- emit: Apple .strings -----------------------------------------------------
const canonicalKeyOrder = [...flattened[FALLBACK_LANGUAGE].keys()];

function writeAppleStrings(lang) {
  const dir = path.join(OUT_DIR, "ios", `${lang}.lproj`);
  fs.mkdirSync(dir, { recursive: true });
  const header = `/*\n${GENERATED_NOTE.map((l) => ` * ${l}`).join("\n")}\n */\n`;
  const lines = canonicalKeyOrder.map((key) => {
    const value = flattened[lang].get(key);
    return `"${appleEscape(key)}" = "${appleEscape(value)}";`;
  });
  fs.writeFileSync(path.join(dir, "Localizable.strings"), `${header}\n${lines.join("\n")}\n`);
  return path.join(dir, "Localizable.strings");
}

// --- emit: Android strings.xml -------------------------------------------------
function writeAndroidStrings(lang, valuesDir) {
  const dir = path.join(OUT_DIR, "android", valuesDir);
  fs.mkdirSync(dir, { recursive: true });
  const header = `<!--\n${GENERATED_NOTE.map((l) => `  ${l}`).join("\n")}\n-->\n`;
  const lines = canonicalKeyOrder.map((key) => {
    const value = flattened[lang].get(key);
    return `    <string name="${androidResourceName(key)}">${androidEscape(value)}</string>`;
  });
  const xml = `<?xml version="1.0" encoding="utf-8"?>\n${header}<resources>\n${lines.join("\n")}\n</resources>\n`;
  fs.writeFileSync(path.join(dir, "strings.xml"), xml);
  return path.join(dir, "strings.xml");
}

const written = [];
written.push(writeAppleStrings("en"));
written.push(writeAppleStrings("fr"));
written.push(writeAppleStrings("es"));
written.push(writeAndroidStrings("en", "values"));
written.push(writeAndroidStrings("fr", "values-fr"));
written.push(writeAndroidStrings("es", "values-es"));

// --- summary -------------------------------------------------------------------
console.log(
  `generated ${written.length} localized-string files under ${path.relative(ROOT, OUT_DIR)}/:\n` +
    written.map((f) => `  - ${path.relative(ROOT, f)}`).join("\n")
);
console.log(
  `\n${canonicalKeyOrder.length} keys x ${presentLanguages.length} languages (${presentLanguages.join(", ")}); ` +
    `key sets match across all languages.`
);
