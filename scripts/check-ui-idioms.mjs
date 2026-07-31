// Static checks on the Compose sources for mistakes that compile, run, and
// are only visible to someone looking at the screen.
//
// Every rule here exists because the mistake it catches actually shipped.
// A rule with no history behind it is a rule that will one day be wrong for
// a good reason and get switched off, taking the useful ones with it.
//
// Run: node scripts/check-ui-idioms.mjs

import fs from "fs";
import path from "path";

const ROOT = new URL("..", import.meta.url).pathname;
const UI = path.join(ROOT, "apps/android/app/src/main/kotlin/com/mercato/app/ui");

const RED = "\x1b[31m";
const GREEN = "\x1b[32m";
const DIM = "\x1b[2m";
const OFF = "\x1b[0m";

const files = fs
  .readdirSync(UI)
  .filter((f) => f.endsWith(".kt"))
  .map((f) => ({ name: f, src: fs.readFileSync(path.join(UI, f), "utf8") }));

const findings = [];

/**
 * Track which container opened each brace block, so a call can be attributed
 * to the layout it actually sits in rather than to whatever happens to be
 * above it in the file. Indentation is not reliable here: a Row with named
 * arguments spans several lines before its brace, which is exactly the shape
 * the Settings header had when its gap went missing.
 */
function containerStack(src) {
  const stack = [];
  const at = [];
  let pending = null;
  let line = 1;
  for (let i = 0; i < src.length; i++) {
    if (src[i] === "\n") line++;
    const before = i === 0 ? "" : src[i - 1];
    const boundary = !/[\w.]/.test(before);
    const m = /^(Row|Column|Box|LazyColumn|LazyRow)\s*[({]/.exec(src.slice(i, i + 20));
    if (m && boundary) pending = m[1];
    if (src[i] === "{") {
      stack.push(pending ?? "?");
      pending = null;
    } else if (src[i] === "}") {
      stack.pop();
    }
    at.push({ i, line, top: stack[stack.length - 1] ?? null });
  }
  return at;
}

// Rule 1: Gap() is a height-only spacer. In a Row it sets no width and so
// does nothing at all, with no error and no warning. That is how the Settings
// title came to sit flush against its back arrow, unnoticed across commits.
for (const { name, src } of files) {
  const at = containerStack(src);
  for (let i = 0; i < src.length; i++) {
    const before = i === 0 ? "" : src[i - 1];
    if (!/^Gap\s*\(/.test(src.slice(i, i + 8))) continue;
    if (/[\w_]/.test(before)) continue;
    if (at[i].top === "Row") {
      findings.push({
        file: name,
        line: at[i].line,
        rule: "gap-in-row",
        text: "Gap() inside a Row sets a height and no width, so it spaces nothing.",
        fix: "Use Arrangement.spacedBy on the Row, or GapW for a one-off horizontal gap.",
      });
    }
  }
}

// Rule 2: a fill painted across a shape with the border stroked on top leaves
// the outermost pixel shared between two antialiased edges, and neither wins:
// the pale fill shows through as a hairline all the way round. solidFill lays
// the outline down first and insets the fill instead.
for (const { name, src } of files) {
  const lines = src.split("\n");
  for (let i = 0; i < lines.length; i++) {
    if (!/\.background\(/.test(lines[i])) continue;
    const next = lines.slice(i + 1, i + 3).join(" ");
    if (!/\.border\(/.test(next)) continue;
    // A dimmed or translucent fill cannot be inset the same way, and those
    // call sites are deliberate.
    if (/\.dim\(|Color\.Transparent/.test(lines[i] + next)) continue;
    findings.push({
      file: name,
      line: i + 1,
      rule: "background-then-border",
      text: "An opaque fill stroked with a border traces a hairline of the fill colour.",
      fix: "Use Modifier.solidFill(radius, fill, border, outline).",
    });
  }
}

console.log("\x1b[1mCompose idioms\x1b[0m");
console.log(`${DIM}${files.length} UI files, ${findings.length ? "" : "nothing to report"}${OFF}\n`);

for (const f of findings) {
  console.log(`  ${RED}${f.rule}${OFF}  ${f.file}:${f.line}`);
  console.log(`    ${f.text}`);
  console.log(`    ${DIM}${f.fix}${OFF}\n`);
}

if (findings.length) {
  console.log(`${RED}${findings.length} finding${findings.length > 1 ? "s" : ""}.${OFF}`);
  process.exit(1);
}
console.log(`${GREEN}No finding.${OFF}`);
