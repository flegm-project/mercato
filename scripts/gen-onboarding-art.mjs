// Generate the three onboarding illustrations for both platforms.
//
// The intro had three hatched rectangles labelled "ILLUSTRATION · CARTE
// TRANSFERT". A placeholder that says it is a placeholder is honest while a
// screen is being built and embarrassing once it ships, so these replace them.
//
// Each pane draws the moment its copy describes, in the game's own language:
// the transfer card, the answers, the recap. They are built from the design
// tokens and rasterised, like the app icon, so they are reproducible from the
// tokens rather than binaries nobody can regenerate.
//
// They carry no text, and that is a constraint rather than a style choice:
// rsvg resolves fonts through fontconfig and ignores `font-family` for a face
// it only knows through a throwaway config (proved when the app icon rendered
// identically with a font name that does not exist). Wordless also means one
// set of images for all three languages.
//
// Emits (generated, gitignored like every other build artifact):
//   build/art/ios/Onboarding.xcassets/     three imagesets, one 3x each
//   build/art/android/drawable-xxhdpi/     the same three PNGs
//
// Run: node scripts/gen-onboarding-art.mjs
import fs from "fs";
import path from "path";
import { execFileSync } from "child_process";

const ROOT = new URL("..", import.meta.url).pathname;
const OUT = path.join(ROOT, "build/art");
const T = JSON.parse(fs.readFileSync(path.join(ROOT, "design/tokens.json"), "utf8"));
const C = T.color;

// The slot is 200dp tall and as wide as the column allows (408 at most, 370 on
// a 402dp phone), so the art is drawn at 408x200 and the platforms scale it to
// fill. Everything that matters stays inside a 24dp side margin, which is what
// keeps the narrower screens from cropping into it.
const W = 408;
const H = 200;
const SCALE = 3; // one 3x asset per platform; both downscale for lower densities

/** A rounded rectangle with the app's hard ink border and offset drop shadow. */
function raised(x, y, w, h, r, fill, { depth = 5, border = 5, stroke = C.ink } = {}) {
  return `
  <rect x="${x}" y="${y + depth}" width="${w}" height="${h}" rx="${r}" fill="${C.ink}"/>
  <rect x="${x + border / 2}" y="${y + border / 2}" width="${w - border}" height="${h - border}"
        rx="${r - border / 2}" fill="${fill}" stroke="${stroke}" stroke-width="${border}"/>`;
}

/** A plain rounded bar, the shape this app uses wherever text would go. */
const bar = (x, y, w, h, fill) =>
  `<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="${h / 2}" fill="${fill}"/>`;

/**
 * The recap star, same construction as `RecapStar` on both platforms: the
 * sharp pentagram at `waist`, with every corner replaced by a tangent arc.
 * Repeating the geometry here rather than eyeballing it is what keeps the
 * intro's stars and the recap's stars the same star.
 */
function star(cx, cy, outer, fill) {
  const WAIST = 0.392, TIP = 0.0665, VALLEY = 0.031;
  const pts = [...Array(10)].map((_, i) => {
    const r = i % 2 === 0 ? outer : outer * WAIST;
    const a = ((-90 + i * 36) * Math.PI) / 180;
    return [cx + r * Math.cos(a), cy + r * Math.sin(a)];
  });
  let d = "";
  for (let i = 0; i < 10; i++) {
    const p = pts[i], prev = pts[(i + 9) % 10], next = pts[(i + 1) % 10];
    const len = (a, b) => Math.hypot(b[0] - a[0], b[1] - a[1]);
    const unit = (a, b) => { const l = len(a, b); return [(b[0] - a[0]) / l, (b[1] - a[1]) / l]; };
    const u = unit(p, prev), v = unit(p, next);
    const half = Math.acos(Math.max(-1, Math.min(1, u[0] * v[0] + u[1] * v[1]))) / 2;
    const corner = outer * (i % 2 === 0 ? TIP : VALLEY);
    const trim = Math.min(corner / Math.tan(half), Math.min(len(p, prev), len(p, next)) / 2);
    const a = [p[0] + u[0] * trim, p[1] + u[1] * trim];
    const b = [p[0] + v[0] * trim, p[1] + v[1] * trim];
    const k = (4 / 3) * Math.tan((Math.PI - 2 * half) / 4) * Math.tan(half);
    const c1 = [a[0] + (p[0] - a[0]) * k, a[1] + (p[1] - a[1]) * k];
    const c2 = [b[0] + (p[0] - b[0]) * k, b[1] + (p[1] - b[1]) * k];
    d += `${i === 0 ? "M" : "L"}${a} C${c1} ${c2} ${b}`;
  }
  return `<path d="${d}Z" fill="${fill}"/>`;
}

// --- The three panes -------------------------------------------------------

/**
 * "Deux clubs. Une année." A transfer card in miniature: the kind chip and the
 * year on the dark header, then the club left behind, the arrow, and the club
 * joined. The four yellow blocks are the year's four digits.
 */
const pane1 = () => {
  const x = 74, y = 22, w = 260, h = 148, r = 18;
  const head = 46;
  return `
  ${raised(x, y, w, h, r, C.ivory, { depth: 6 })}
  <clipPath id="card"><rect x="${x + 5}" y="${y + 5}" width="${w - 10}" height="${h - 10}" rx="${r - 3}"/></clipPath>
  <g clip-path="url(#card)">
    <rect x="${x}" y="${y}" width="${w}" height="${head}" fill="${C.ink}"/>
    ${bar(x + 20, y + 17, 74, 18, C["club-grey"])}
    ${[0, 1, 2, 3].map((i) => `<rect x="${x + w - 102 + i * 21}" y="${y + 11}" width="15" height="24" rx="3" fill="${C.yellow}"/>`).join("")}
    ${bar(x + w / 2 - 46, y + head + 22, 92, 11, C["club-grey"])}
    <path d="M${x + w / 2 - 8} ${y + head + 45} L${x + w / 2 + 8} ${y + head + 45} L${x + w / 2} ${y + head + 57} Z" fill="${C["club-grey"]}"/>
    ${bar(x + w / 2 - 78, y + head + 66, 156, 20, C.ink)}
  </g>`;
};

/**
 * "Trouve le joueur." Four answers, the right one turned green with the tick.
 * Four bars rather than four words: the shape of the choice is the point, and
 * it reads the same in every language.
 */
const pane2 = () => {
  const w = 250, h = 27, gap = 13, x = (W - w) / 2;
  const top = (H - (4 * h + 3 * gap)) / 2 - 3;
  return [0, 1, 2, 3].map((i) => {
    const y = top + i * (h + gap);
    const right = i === 1;
    const face = raised(x, y, w, h, h / 2, right ? C.green : C.ivory, { depth: 4, border: 4 });
    const inner = right
      ? `<path d="M${x + w / 2 - 14} ${y + h / 2 + 1} l8 9 l19 -20" fill="none" stroke="${C.ink}"
             stroke-width="6" stroke-linecap="round" stroke-linejoin="round"/>`
      : bar(x + w / 2 - 55, y + h / 2 - 4, 110, 8, C["club-grey"]);
    return face + inner;
  }).join("");
};

/**
 * "+3 par bonne réponse." The recap: three stars, two of them earned, over the
 * round's ten answers. Seven green and three coral is a two-star round, which
 * is exactly what the two lit stars above say.
 */
const pane3 = () => {
  const R = 33, pitch = 82, cy = 72;
  const stars = [0, 1, 2].map((i) => {
    const cx = W / 2 + (i - 1) * pitch;
    const fill = i < 2 ? C.yellow : "#FFFFFF";
    const op = i < 2 ? 1 : T.opacity["star-off"];
    return `<g opacity="${op}">${star(cx + 4, cy + 4, R, C.ink)}${star(cx, cy, R, fill)}</g>`;
  }).join("");
  const results = [1, 1, 0, 1, 1, 1, 0, 1, 1, 0];
  const pw = 26, pgap = 8, py = 152;
  const px = (W - (results.length * pw + (results.length - 1) * pgap)) / 2;
  const pips = results.map((ok, i) =>
    bar(px + i * (pw + pgap), py, pw, 10, ok ? C.green : C.coral)).join("");
  return stars + pips;
};

// --- Rasterise -------------------------------------------------------------

const PANES = [pane1, pane2, pane3];

// The ground is the app background rather than the ivory the slot used to
// paint: every one of these is a picture of the game, and the game is on blue.
const svg = (body) => `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}">
  <rect width="${W}" height="${H}" fill="${C["blue-deep"]}"/>
  ${body}
</svg>`;

const iosDir = path.join(OUT, "ios/Onboarding.xcassets");
const andDir = path.join(OUT, "android/drawable-xxhdpi");
fs.rmSync(OUT, { recursive: true, force: true });
fs.mkdirSync(andDir, { recursive: true });
fs.mkdirSync(iosDir, { recursive: true });
fs.writeFileSync(
  path.join(iosDir, "Contents.json"),
  JSON.stringify({ info: { author: "xcode", version: 1 } }, null, 2) + "\n"
);

for (const [i, pane] of PANES.entries()) {
  const name = `onboarding_${i + 1}`;
  const tmp = path.join(OUT, `${name}.svg`);
  fs.writeFileSync(tmp, svg(pane()));

  const png = path.join(andDir, `${name}.png`);
  execFileSync("rsvg-convert", [
    "-w", String(W * SCALE), "-h", String(H * SCALE), "-o", png, tmp,
  ], { stdio: "inherit" });
  fs.rmSync(tmp);

  // One 3x entry rather than three sizes: at this scale the downsample is
  // invisible and three copies of the same picture is three things to forget
  // to regenerate.
  const set = path.join(iosDir, `${name}.imageset`);
  fs.mkdirSync(set, { recursive: true });
  fs.copyFileSync(png, path.join(set, `${name}.png`));
  fs.writeFileSync(
    path.join(set, "Contents.json"),
    JSON.stringify({
      images: [{ idiom: "universal", filename: `${name}.png`, scale: "3x" }],
      info: { author: "xcode", version: 1 },
    }, null, 2) + "\n"
  );
  console.log(`  ${name}.png  ${W * SCALE}x${H * SCALE}`);
}
console.log("onboarding art written to build/art");
