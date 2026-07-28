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
  const closeList = () => {
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
    const item = /^[-*]\s+(.*)$/.exec(line);
    if (item) {
      if (!inList) {
        out.push("<ul>");
        inList = true;
      }
      out.push(`<li>${inline(item[1])}</li>`);
      continue;
    }
    if (inList) {
      // A wrapped list item continues the previous one.
      out[out.length - 1] = out[out.length - 1].replace(/<\/li>$/, ` ${inline(line.trim())}</li>`);
      continue;
    }
    const prev = out[out.length - 1];
    if (prev && prev.startsWith("<p>") && prev.endsWith("</p>")) {
      out[out.length - 1] = `${prev.slice(0, -4)} ${inline(line.trim())}</p>`;
    } else {
      out.push(`<p>${inline(line.trim())}</p>`);
    }
  }
  closeList();
  return out.join("\n");
}

const sections = LANGS.map(({ code }) => {
  const md = fs.readFileSync(path.join(ROOT, `docs/legal/privacy-policy.${code}.md`), "utf8");
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
