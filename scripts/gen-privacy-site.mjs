// Build the publishable privacy policy page from docs/legal/*.md.
//
// Both stores require a reachable privacy policy URL for an app that shows ads
// and sells an in-app purchase, and the app links to it from Settings. The
// page carries all three languages and picks one from the browser, so a single
// URL serves every locale.
//
// Output: docs/privacy/index.html, committed so GitHub Pages can serve it from
// the docs/ folder. This is the one generated file that is not gitignored:
// nothing can publish it otherwise.
//
// Run: node scripts/gen-privacy-site.mjs
import fs from "fs";
import path from "path";

const ROOT = new URL("..", import.meta.url).pathname;
const OUT = path.join(ROOT, "docs/privacy");

// The contact address lives in one file rather than three times in three
// languages, and the build refuses to publish the placeholder. A privacy
// policy is the one page whose whole job is to be read by a stranger, and the
// address on it is printed on both store listings: a personal one puts a real
// name in front of every player, which is exactly what the studio namespace
// exists to avoid.
const CONTACT_FILE = path.join(ROOT, "docs/legal/contact.txt");
const PLACEHOLDER = "REPLACE-ME@example.invalid";
let contact;
try {
  contact = fs.readFileSync(CONTACT_FILE, "utf8").trim();
} catch {
  console.error(`error: ${CONTACT_FILE} is missing; it holds the address the policy prints`);
  process.exit(1);
}
if (!contact || contact === PLACEHOLDER) {
  console.error(
    "error: docs/legal/contact.txt still holds the placeholder.\n" +
      "       Put a neutral address there before publishing: the three policies\n" +
      "       print it, and both stores show it on the listing."
  );
  process.exit(1);
}
const T = JSON.parse(fs.readFileSync(path.join(ROOT, "design/tokens.json"), "utf8")).color;

const LANGS = [
  { code: "en", label: "English" },
  { code: "fr", label: "Français" },
  { code: "es", label: "Español" },
];

const escapeHtml = (s) =>
  s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");

/** Minimal markdown: headings, lists, paragraphs, bold, links, inline code. */
function toHtml(md) {
  const inline = (s) =>
    escapeHtml(s)
      .replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2">$1</a>')
      .replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>")
      .replace(/`([^`]+)`/g, "<code>$1</code>");

  const out = [];
  let inList = false;
  // A block (paragraph or list item) is buffered raw and rendered once it is
  // complete, so inline markup that wraps across source lines, a **bold** span
  // whose markers land on two lines, is not split into two unmatched halves.
  let pending = null;
  const flush = () => {
    if (!pending) return;
    const html = inline(pending.text);
    out.push(pending.type === "li" ? `<li>${html}</li>` : `<p>${html}</p>`);
    pending = null;
  };
  const closeList = () => {
    flush();
    if (inList) {
      out.push("</ul>");
      inList = false;
    }
  };

  for (const raw of md.split(/\r?\n/)) {
    const line = raw.trimEnd();
    if (!line.trim()) {
      closeList();
      continue;
    }
    const heading = /^(#{1,4})\s+(.*)$/.exec(line);
    if (heading) {
      closeList();
      const level = heading[1].length;
      out.push(`<h${level}>${inline(heading[2])}</h${level}>`);
      continue;
    }
    if (/^(-{3,}|\*{3,}|_{3,})$/.test(line.trim())) {
      closeList();
      out.push("<hr>");
      continue;
    }
    const item = /^[-*]\s+(.*)$/.exec(line);
    if (item) {
      flush();
      if (!inList) {
        out.push("<ul>");
        inList = true;
      }
      pending = { type: "li", text: item[1] };
      continue;
    }
    // A wrapped line continues the current block, whichever kind it is.
    if (pending) {
      pending.text += ` ${line.trim()}`;
    } else {
      pending = { type: "p", text: line.trim() };
    }
  }
  closeList();
  return out.join("\n");
}

const sections = LANGS.map(({ code }) => {
  const md = fs
    .readFileSync(path.join(ROOT, `docs/legal/privacy-policy.${code}.md`), "utf8")
    .replaceAll("{{contact}}", contact);
  return `<section data-lang="${code}" hidden>\n${toHtml(md)}\n</section>`;
}).join("\n");

const nav = LANGS.map(
  ({ code, label }) => `<button type="button" data-pick="${code}">${label}</button>`
).join("");

const html = `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Mercato privacy policy</title>
<style>
  :root { color-scheme: dark; }
  body {
    margin: 0; padding: 2rem 1.25rem 4rem;
    background: radial-gradient(130% 85% at 50% 0%, ${T["blue-top"]} 0%, ${T.blue} 44%, ${T["blue-deep"]} 100%);
    background-attachment: fixed;
    color: ${T.ivory};
    font: 16px/1.6 -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
  }
  main { max-width: 46rem; margin: 0 auto; }
  h1 { font-size: 1.9rem; line-height: 1.15; margin: 0 0 1.5rem; }
  h2 { font-size: 1.25rem; margin: 2rem 0 .5rem; }
  h3 { font-size: 1.05rem; margin: 1.5rem 0 .4rem; }
  p, li { color: rgba(251, 250, 244, .82); }
  a { color: ${T.yellow}; }
  code { background: rgba(11,16,48,.5); padding: .1em .35em; border-radius: .25rem; font-size: .9em; }
  ul { padding-left: 1.2rem; }
  nav { display: flex; gap: .5rem; margin-bottom: 2rem; flex-wrap: wrap; }
  nav button {
    font: inherit; font-weight: 700; cursor: pointer;
    padding: .45rem .9rem; border-radius: 999px;
    border: 2px solid ${T.ink}; background: rgba(11,16,48,.45); color: ${T.ivory};
  }
  nav button[aria-current="true"] { background: ${T.yellow}; color: ${T.ink}; }
</style>
</head>
<body>
<main>
<nav aria-label="Language">${nav}</nav>
${sections}
</main>
<script>
  var sections = document.querySelectorAll("section[data-lang]");
  var buttons = document.querySelectorAll("nav button[data-pick]");
  function show(code) {
    sections.forEach(function (s) { s.hidden = s.dataset.lang !== code; });
    buttons.forEach(function (b) { b.setAttribute("aria-current", String(b.dataset.pick === code)); });
    document.documentElement.lang = code;
  }
  // The app opens this without a language hint, so follow the browser and fall
  // back to English, matching the app's own rule.
  var preferred = (navigator.languages || [navigator.language || "en"])
    .map(function (l) { return String(l).slice(0, 2).toLowerCase(); })
    .find(function (l) { return ["en", "fr", "es"].indexOf(l) !== -1; }) || "en";
  buttons.forEach(function (b) { b.addEventListener("click", function () { show(b.dataset.pick); }); });
  show(preferred);
</script>
</body>
</html>
`;

fs.mkdirSync(OUT, { recursive: true });
fs.writeFileSync(path.join(OUT, "index.html"), html);
console.log(`privacy page written to ${path.relative(ROOT, path.join(OUT, "index.html"))} (${LANGS.length} languages)`);
