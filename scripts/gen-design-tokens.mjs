// Generate native design-token constants for the iOS and Android apps.
//
// design/tokens.json is the single source of truth for colors, typography,
// and game constants (see its own "meta" block for version/notes). This
// script walks that JSON structurally and derives Swift and Kotlin
// constants from it: it never hardcodes a token value, so editing
// tokens.json and rerunning changes the generated output accordingly.
// Shapes that have no native equivalent (CSS gradients, box-shadow strings,
// em-based letter-spacing) are still emitted, as String constants with a
// comment explaining why, so nothing silently disappears when the schema
// grows.
//
// Emits:
//   build/tokens/DesignTokens.swift  (SwiftUI)
//   build/tokens/DesignTokens.kt     (Jetpack Compose, package com.mercato.design)
//
// Output is generated and gitignored (see /build/ in .gitignore), the same
// convention scripts/build-native.sh uses for the UniFFI bindings.
//
// Run: node scripts/gen-design-tokens.mjs
import fs from "fs";
import path from "path";

const ROOT = new URL("..", import.meta.url).pathname;
const TOKENS_PATH = path.join(ROOT, "design/tokens.json");
const OUT_DIR = path.join(ROOT, "build/tokens");

const die = (msg) => {
  console.error(`error: ${msg}`);
  process.exit(1);
};

let raw;
try {
  raw = fs.readFileSync(TOKENS_PATH, "utf8");
} catch (e) {
  die(`cannot read ${TOKENS_PATH}: ${e.message}`);
}
let tokens;
try {
  tokens = JSON.parse(raw);
} catch (e) {
  die(`invalid JSON in ${TOKENS_PATH}: ${e.message}`);
}

// --- naming: kebab-case (and bare numeric) keys -> camelCase -----------------
// "blue-night" -> "blueNight". design/tokens.json's game.stars uses the keys
// "1"/"2"/"3", which cannot be bare identifiers in either language, so a
// purely numeric key borrows its parent key (singularized) as a prefix:
// stars.1 -> star1.
function toCamel(key) {
  return key
    .split("-")
    .map((part, i) => (i === 0 ? part : part.charAt(0).toUpperCase() + part.slice(1)))
    .join("");
}
function pascal(key) {
  const c = toCamel(key);
  return c.charAt(0).toUpperCase() + c.slice(1);
}

// Some token keys pascal-case into names the target languages reserve, and the
// compiler rejects them outright: tokens.json's `type` group becomes `Type`,
// which Swift refuses because it collides with the `foo.Type` metatype
// expression. Both languages accept an escaped identifier, so escape rather
// than rename: the token name stays traceable to the JSON key.
const SWIFT_RESERVED_TYPE_NAMES = new Set([
  "Type",
  "Protocol",
  "Self",
  "Any",
  "AnyObject",
]);
const KOTLIN_RESERVED = new Set([
  "object",
  "class",
  "fun",
  "val",
  "var",
  "typealias",
  "when",
  "is",
  "in",
  "as",
]);
function swiftIdent(name) {
  return SWIFT_RESERVED_TYPE_NAMES.has(name) ? `\`${name}\`` : name;
}
function kotlinIdent(name) {
  return KOTLIN_RESERVED.has(name) ? `\`${name}\`` : name;
}
function singular(word) {
  return word.endsWith("s") ? word.slice(0, -1) : word;
}
function identFor(key, parentKey) {
  if (/^\d+$/.test(key)) return toCamel(singular(parentKey)) + key;
  return toCamel(key);
}
function escStr(s) {
  return s.replace(/\\/g, "\\\\").replace(/"/g, '\\"');
}

// --- shape classification -----------------------------------------------------
const HEX_RE = /^#[0-9a-fA-F]{6}$/;
const isPlainObject = (v) => v !== null && typeof v === "object" && !Array.isArray(v);
const isHexColor = (v) => typeof v === "string" && HEX_RE.test(v);

// Which bare numeric leaves are physical dimensions (need a dp/sp unit)
// rather than plain counts, weights, or millisecond durations. This is a
// structural fact about the token families themselves (what unit a design
// token is measured in), not a hardcoded value: the numbers still flow
// straight from tokens.json untouched.
const DIMENSION_GROUPS = new Set(["radius", "space", "depth"]);
const DIMENSION_KEYS = new Set([
  "hairline",
  "standard",
  "heavy",
  "column-max",
  "tap-min",
  "compact-height",
]);

function isDimension(tokenPath) {
  if (DIMENSION_GROUPS.has(tokenPath[0])) return true;
  if (DIMENSION_KEYS.has(tokenPath[tokenPath.length - 1])) return true;
  if (tokenPath[0] === "type" && tokenPath[tokenPath.length - 1] === "size") return true;
  return false;
}
function isFontSize(tokenPath) {
  return tokenPath[0] === "type" && tokenPath[tokenPath.length - 1] === "size";
}

// A node qualifies as a "struct group" (type.*) when it has at least two
// children that are all flat, leaf-only objects: that shape is a natural fit
// for one shared struct/data class rather than N unrelated namespaces.
// Detected from the data, not from the key name, so any future group with
// the same shape gets the same treatment.
function isHomogeneousObjectMap(node) {
  const values = Object.values(node);
  if (values.length < 2) return false;
  if (!values.every(isPlainObject)) return false;
  return values.every((v) => Object.values(v).every((x) => !isPlainObject(x)));
}

function noteForNumber(tokenPath) {
  if (tokenPath[0] === "motion") return "milliseconds";
  if (tokenPath[tokenPath.length - 1] === "leading") return "unitless line-height multiplier";
  return null;
}
function noteForString(tokenPath, value) {
  const last = tokenPath[tokenPath.length - 1];
  if (tokenPath[0] === "gradient") {
    return "raw CSS gradient string; no native gradient type matches it, build one from the stops manually";
  }
  if (tokenPath[0] === "shadow") {
    return "raw CSS box-shadow string; apply offset/blur/color natively at the call site";
  }
  if (tokenPath[0] === "font") {
    return "raw CSS font-family stack; look up a native font by family name";
  }
  if (tokenPath[0] === "motion") {
    return "raw CSS transition/transform value; parse duration/easing/offset at the call site";
  }
  if (tokenPath[0] === "type" && last === "tracking") {
    return "letter-spacing in em units, relative to this style's own font size; multiply at the call site";
  }
  if (tokenPath[0] === "type" && last === "transform") {
    return "CSS text-transform value; apply manually (e.g. uppercased)";
  }
  if (tokenPath[0] === "type" && last === "font") {
    return `font family key, see Font.${toCamel(value)}`;
  }
  if (tokenPath[0] === "game" && tokenPath.includes("stars")) {
    return "display text; formats are inconsistent across levels ('> 0%' vs '>= 90% correct'), do not parse numerically";
  }
  return null;
}

// Classifies a single leaf value into everything both renderers need: the
// Swift/Kotlin type name, a ready-to-emit literal for each, an optional
// explanatory comment, and which summary counter it belongs to.
function classifyLeaf(tokenPath, value) {
  if (isHexColor(value)) {
    const hex6 = value.slice(1).toUpperCase();
    return {
      swiftType: "SwiftUI.Color",
      swiftLiteral: `SwiftUI.Color(hex: "${value}")`,
      kotlinType: "ComposeColor",
      kotlinLiteral: `ComposeColor(0xFF${hex6})`,
      note: null,
      counterKey: "colors",
    };
  }
  if (typeof value === "number") {
    if (isDimension(tokenPath)) {
      const fontSize = isFontSize(tokenPath);
      return {
        swiftType: "CGFloat",
        swiftLiteral: `${value}`,
        kotlinType: fontSize ? "TextUnit" : "Dp",
        kotlinLiteral: `${value}${fontSize ? ".sp" : ".dp"}`,
        note: null,
        counterKey: "dimensions",
      };
    }
    const isFloat = !Number.isInteger(value);
    return {
      swiftType: isFloat ? "Double" : "Int",
      swiftLiteral: `${value}`,
      kotlinType: isFloat ? "Double" : "Int",
      kotlinLiteral: `${value}`,
      note: noteForNumber(tokenPath),
      counterKey: "numbers",
    };
  }
  return {
    swiftType: "String",
    swiftLiteral: `"${escStr(value)}"`,
    kotlinType: "String",
    kotlinLiteral: `"${escStr(value)}"`,
    note: noteForString(tokenPath, value),
    counterKey: "strings",
  };
}

function unionKeysInOrder(objs) {
  const seen = new Set();
  const order = [];
  for (const o of objs) {
    for (const k of Object.keys(o)) {
      if (!seen.has(k)) {
        seen.add(k);
        order.push(k);
      }
    }
  }
  return order;
}

// A Kotlin `const val` requires a compile-time-constant type (String, Int,
// Double, ...); Compose's Color/Dp/TextUnit are value classes and don't
// qualify, so those fall back to plain `val`.
const KOTLIN_CONST_TYPES = new Set(["String", "Int", "Double"]);

const counts = {};
const bump = (key) => {
  counts[key] = (counts[key] || 0) + 1;
};

function renderLeaf(swiftOut, kotlinOut, indent, key, parentKey, value, tokenPath) {
  const ident = identFor(key, parentKey);
  const c = classifyLeaf(tokenPath, value);
  const sIndent = "    ".repeat(indent);
  const comment = c.note ? `  // ${c.note}` : "";
  swiftOut.push(`${sIndent}public static let ${ident}: ${c.swiftType} = ${c.swiftLiteral}${comment}`);
  const kw = KOTLIN_CONST_TYPES.has(c.kotlinType) ? "const val" : "val";
  const kotlinType = KOTLIN_CONST_TYPES.has(c.kotlinType) ? `: ${c.kotlinType}` : "";
  kotlinOut.push(`${sIndent}${kw} ${ident}${kotlinType} = ${c.kotlinLiteral}${comment}`);
  bump(c.counterKey);
}

// Emits one shared struct (Swift) / data class (Kotlin) for a homogeneous
// map of style objects (type.*), plus a namespace of named instances built
// from it. Fields not present on every style become optional.
function renderStructGroup(swiftOut, kotlinOut, indent, key, node, tokenPath) {
  const groupName = pascal(key);
  const structName = pascal(singular(key)) + "Style";
  const styleNames = Object.keys(node);
  const fieldOrder = unionKeysInOrder(Object.values(node));
  const sIndent = "    ".repeat(indent);

  const fieldMeta = fieldOrder.map((f) => {
    const owner = styleNames.find((s) => f in node[s]);
    const c = classifyLeaf([...tokenPath, owner, f], node[owner][f]);
    const optional = !styleNames.every((s) => f in node[s]);
    return { field: identFor(f, key), jsonKey: f, swiftType: c.swiftType, kotlinType: c.kotlinType, optional };
  });

  swiftOut.push(`${sIndent}public struct ${structName} {`);
  for (const f of fieldMeta) {
    swiftOut.push(`${sIndent}    public let ${f.field}: ${f.swiftType}${f.optional ? "?" : ""}`);
  }
  swiftOut.push(`${sIndent}}`);

  kotlinOut.push(`${sIndent}data class ${structName}(`);
  fieldMeta.forEach((f, i) => {
    const comma = i < fieldMeta.length - 1 ? "," : "";
    kotlinOut.push(`${sIndent}    val ${f.field}: ${f.kotlinType}${f.optional ? "?" : ""}${comma}`);
  });
  kotlinOut.push(`${sIndent})`);

  swiftOut.push(`${sIndent}public enum ${swiftIdent(groupName)} {`);
  kotlinOut.push(`${sIndent}object ${kotlinIdent(groupName)} {`);
  for (const styleName of styleNames) {
    const styleObj = node[styleName];
    const ident = identFor(styleName, key);
    const swiftArgs = [];
    const kotlinArgs = [];
    for (const f of fieldMeta) {
      const has = f.jsonKey in styleObj;
      if (!has) {
        swiftArgs.push(`${f.field}: nil`);
        kotlinArgs.push(`${f.field} = null`);
        continue;
      }
      const c = classifyLeaf([...tokenPath, styleName, f.jsonKey], styleObj[f.jsonKey]);
      swiftArgs.push(`${f.field}: ${c.swiftLiteral}`);
      kotlinArgs.push(`${f.field} = ${c.kotlinLiteral}`);
    }
    swiftOut.push(`${sIndent}    public static let ${ident} = ${structName}(${swiftArgs.join(", ")})`);
    kotlinOut.push(`${sIndent}    val ${ident} = ${structName}(${kotlinArgs.join(", ")})`);
    bump("typeStyles");
  }
  swiftOut.push(`${sIndent}}`);
  kotlinOut.push(`${sIndent}}`);
}

// Generic recursive namespace renderer. Direct leaves become constants at
// this level; nested objects recurse into their own namespace, unless the
// whole node is a homogeneous map of style objects, in which case it is
// rendered as a shared struct group instead (see isHomogeneousObjectMap).
// New keys added to tokens.json fall into one of these two buckets
// automatically, so nothing needs to be added here to keep them visible.
function renderNode(swiftOut, kotlinOut, indent, key, node, tokenPath) {
  const entries = Object.entries(node);
  const leafEntries = entries.filter(([, v]) => !isPlainObject(v));
  const objEntries = entries.filter(([, v]) => isPlainObject(v));

  if (objEntries.length > 0 && objEntries.length === entries.length && isHomogeneousObjectMap(node)) {
    renderStructGroup(swiftOut, kotlinOut, indent, key, node, tokenPath);
    return;
  }

  const name = pascal(key);
  const sIndent = "    ".repeat(indent);
  swiftOut.push(`${sIndent}public enum ${swiftIdent(name)} {`);
  kotlinOut.push(`${sIndent}object ${kotlinIdent(name)} {`);
  for (const [k, v] of leafEntries) {
    renderLeaf(swiftOut, kotlinOut, indent + 1, k, key, v, [...tokenPath, k]);
  }
  for (const [k, v] of objEntries) {
    renderNode(swiftOut, kotlinOut, indent + 1, k, v, [...tokenPath, k]);
  }
  swiftOut.push(`${sIndent}}`);
  kotlinOut.push(`${sIndent}}`);
}

// --- drive the whole file over every top-level group except "meta" ----------
const swiftBody = [];
const kotlinBody = [];
for (const [key, node] of Object.entries(tokens)) {
  if (key === "meta") continue;
  renderNode(swiftBody, kotlinBody, 1, key, node, [key]);
}

const meta = tokens.meta || {};
const header = (lines) => lines.map((l) => (l === "" ? "//" : `// ${l}`)).join("\n");
const headerLines = [
  "GENERATED FILE. DO NOT EDIT BY HAND.",
  "",
  `Derived from design/tokens.json (${meta.name || "Mercato"} v${meta.version || "?"}, ` +
    `updated ${meta.updated || "unknown"}). Regenerate with:`,
  "  node scripts/gen-design-tokens.mjs",
  "",
  "Output lives under build/ and is gitignored: it is never committed, the",
  "same convention scripts/build-native.sh uses for the UniFFI bindings.",
  "",
  "Shapes with no native equivalent (CSS gradients, box-shadow strings,",
  "em-based letter-spacing) are kept as String constants with a comment",
  "rather than dropped.",
];

// SwiftUI.Color has no built-in hex initializer; the generated file needs to
// stand alone, so a small private one is emitted alongside the tokens
// instead of asking every consumer to bring their own.
const SWIFT_COLOR_HEX_INIT = `private extension SwiftUI.Color {
    init(hex: String) {
        let digits = hex.hasPrefix("#") ? String(hex.dropFirst()) : hex
        var value: UInt64 = 0
        Scanner(string: digits).scanHexInt64(&value)
        let r = Double((value & 0xFF0000) >> 16) / 255
        let g = Double((value & 0x00FF00) >> 8) / 255
        let b = Double(value & 0x0000FF) / 255
        self.init(red: r, green: g, blue: b)
    }
}`;

const swiftFile = `${header(headerLines)}

import SwiftUI

${SWIFT_COLOR_HEX_INIT}

public enum DesignTokens {
${swiftBody.join("\n")}
}
`;

const kotlinFile = `${header(headerLines)}

package com.mercato.design

import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object DesignTokens {
${kotlinBody.join("\n")}
}
`;

fs.mkdirSync(OUT_DIR, { recursive: true });
fs.writeFileSync(path.join(OUT_DIR, "DesignTokens.swift"), swiftFile);
fs.writeFileSync(path.join(OUT_DIR, "DesignTokens.kt"), kotlinFile);

// The Android launch theme is XML, and XML cannot read a Kotlin object: the
// window is painted before any of this app's code runs. iOS states the same
// colour as a colour set in the asset catalogue (project.yml, LaunchBackground)
// so the hand-off to the splash is invisible instead of a flash of white.
const ANDROID_RES = path.join(ROOT, "build/tokens/android/values");
fs.mkdirSync(ANDROID_RES, { recursive: true });
fs.writeFileSync(
  path.join(ANDROID_RES, "colors.xml"),
  `<?xml version="1.0" encoding="utf-8"?>
<!-- Generated by scripts/gen-design-tokens.mjs from design/tokens.json.
     Only the colours the launch theme needs: everything drawn by the app
     itself reads DesignTokens.kt. -->
<resources>
  <color name="launch_background">${tokens.color["blue-deep"]}</color>
  <color name="ink">${tokens.color.ink}</color>
</resources>
`
);

console.log(
  `generated build/tokens/DesignTokens.{swift,kt}: ` +
    `${counts.colors || 0} colors, ${counts.typeStyles || 0} type styles, ` +
    `${counts.dimensions || 0} dimension constants, ${counts.numbers || 0} plain numeric constants, ` +
    `${counts.strings || 0} string constants (css/display-text fallbacks included)`
);
