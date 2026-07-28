// Generate the app icon for both platforms from the design tokens.
//
// The mark is the wordmark's first letter: yellow on the blue radial
// background, over the hard ink offset shadow that every raised surface in the
// app carries. Drawn as SVG and rasterised, so it is reproducible from the
// tokens rather than a binary nobody can regenerate.
//
// Outputs (generated, gitignored like every other build artifact):
//   build/icons/ios/Assets.xcassets/      AppIcon set Xcode derives every size from
//   build/icons/android/                  adaptive foreground/background + legacy densities
//
// Run: node scripts/gen-app-icon.mjs
import fs from "fs";
import path from "path";
import { execFileSync } from "child_process";

const ROOT = new URL("..", import.meta.url).pathname;
const OUT = path.join(ROOT, "build/icons");
const T = JSON.parse(fs.readFileSync(path.join(ROOT, "design/tokens.json"), "utf8")).color;

const FONT = path.join(ROOT, "design/fonts/Unbounded-Black.ttf");
if (!fs.existsSync(FONT)) {
  console.error(`error: ${FONT} is missing; run scripts/gen-fonts.sh first`);
  process.exit(1);
}

/**
 * The background, as its own layer so Android can animate it independently of
 * the mark. iOS gets the two flattened together.
 */
function background(size) {
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 512 512">
  <defs>
    <radialGradient id="bg" cx="50%" cy="0%" r="130%">
      <stop offset="0%" stop-color="${T["blue-top"]}"/>
      <stop offset="44%" stop-color="${T.blue}"/>
      <stop offset="100%" stop-color="${T["blue-deep"]}"/>
    </radialGradient>
  </defs>
  <rect width="512" height="512" fill="url(#bg)"/>
</svg>`;
}

/**
 * The mark. `inset` keeps it inside Android's adaptive safe zone: only the
 * middle 66 of 108 units is guaranteed visible once the launcher masks it.
 */
function mark(size, { inset = 1, transparent = true } = {}) {
  const bg = transparent ? "" : `<rect width="512" height="512" fill="url(#bg)"/>`;
  const defs = transparent
    ? ""
    : `<defs><radialGradient id="bg" cx="50%" cy="0%" r="130%">
        <stop offset="0%" stop-color="${T["blue-top"]}"/>
        <stop offset="44%" stop-color="${T.blue}"/>
        <stop offset="100%" stop-color="${T["blue-deep"]}"/>
      </radialGradient></defs>`;
  const scale = inset;
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 512 512">
  ${defs}${bg}
  <g transform="translate(256 256) scale(${scale}) translate(-256 -256)">
    <text x="256" y="256" text-anchor="middle" dominant-baseline="central"
          font-family="Unbounded" font-weight="900" font-size="340"
          letter-spacing="-20" fill="${T.ink}" transform="translate(14 16)">M</text>
    <text x="256" y="256" text-anchor="middle" dominant-baseline="central"
          font-family="Unbounded" font-weight="900" font-size="340"
          letter-spacing="-20" fill="${T.yellow}">M</text>
  </g>
</svg>`;
}

// rsvg resolves fonts through fontconfig and has no flag to load one
// directly, so a throwaway config pointing at design/fonts is built once and
// handed to it. Nothing is installed into the user's font library.
const FC_DIR = path.join(OUT, ".fontconfig");
function setupFontconfig() {
  fs.mkdirSync(FC_DIR, { recursive: true });
  fs.writeFileSync(
    path.join(FC_DIR, "fonts.conf"),
    `<?xml version="1.0"?>
<!DOCTYPE fontconfig SYSTEM "fonts.dtd">
<fontconfig>
  <dir>${path.join(ROOT, "design/fonts")}</dir>
  <cachedir>${path.join(FC_DIR, "cache")}</cachedir>
</fontconfig>
`
  );
}

function render(svg, out, size) {
  const tmp = path.join(OUT, ".tmp.svg");
  fs.writeFileSync(tmp, svg);
  execFileSync("rsvg-convert", ["-w", String(size), "-h", String(size), "-o", out, tmp], {
    env: { ...process.env, FONTCONFIG_PATH: FC_DIR, FONTCONFIG_FILE: path.join(FC_DIR, "fonts.conf") },
  });
  fs.rmSync(tmp);
}

fs.rmSync(OUT, { recursive: true, force: true });
fs.mkdirSync(OUT, { recursive: true });
setupFontconfig();

// --- iOS: one 1024 marketing icon, which Xcode 14+ derives every size from ---
const iosAssets = path.join(OUT, "ios/Assets.xcassets");
const iosDir = path.join(iosAssets, "AppIcon.appiconset");
fs.mkdirSync(iosDir, { recursive: true });
fs.writeFileSync(
  path.join(iosAssets, "Contents.json"),
  JSON.stringify({ info: { author: "mercato", version: 1 } }, null, 2) + "\n"
);
render(mark(1024, { inset: 0.78, transparent: false }), path.join(iosDir, "icon-1024.png"), 1024);
fs.writeFileSync(
  path.join(iosDir, "Contents.json"),
  JSON.stringify(
    {
      images: [{ filename: "icon-1024.png", idiom: "universal", platform: "ios", size: "1024x1024" }],
      info: { author: "mercato", version: 1 },
    },
    null,
    2
  ) + "\n"
);

// --- Android: adaptive foreground and background, plus legacy densities ---
const andRes = path.join(OUT, "android/res");
const densities = { mdpi: 48, hdpi: 72, xhdpi: 96, xxhdpi: 144, xxxhdpi: 192 };
for (const [name, px] of Object.entries(densities)) {
  const dir = path.join(andRes, `mipmap-${name}`);
  fs.mkdirSync(dir, { recursive: true });
  // Adaptive layers are drawn at 108/72 of the nominal size, and the mark is
  // inset so the launcher mask cannot clip it.
  const layer = Math.round(px * 1.5);
  render(background(layer), path.join(dir, "ic_launcher_background.png"), layer);
  render(mark(layer, { inset: 0.52 }), path.join(dir, "ic_launcher_foreground.png"), layer);
  // Legacy square icon for pre-26 launchers.
  render(mark(px, { inset: 0.78, transparent: false }), path.join(dir, "ic_launcher.png"), px);
}
const anyDpi = path.join(andRes, "mipmap-anydpi-v26");
fs.mkdirSync(anyDpi, { recursive: true });
const adaptive = `<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@mipmap/ic_launcher_background"/>
    <foreground android:drawable="@mipmap/ic_launcher_foreground"/>
</adaptive-icon>
`;
fs.writeFileSync(path.join(anyDpi, "ic_launcher.xml"), adaptive);
fs.writeFileSync(path.join(anyDpi, "ic_launcher_round.xml"), adaptive);

fs.rmSync(FC_DIR, { recursive: true, force: true });

console.log("icons generated:");
console.log(`  ios     ${path.relative(ROOT, iosAssets)}`);
console.log(`  android ${path.relative(ROOT, andRes)} (${Object.keys(densities).length} densities + adaptive)`);
