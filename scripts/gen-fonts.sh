#!/usr/bin/env bash
# Regenerate the bundled static font instances from the upstream variable fonts.
#
# Unlike everything else generated in this repo, the output IS committed (see
# design/fonts/): the apps need the files at build time, upstream ships only
# variable fonts, and cutting instances needs a Python toolchain that no app
# build should depend on. Rerun this only when the design system changes which
# weights it uses.
#
# Run: ./scripts/gen-fonts.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$ROOT/design/fonts"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

UPSTREAM="https://github.com/google/fonts/raw/main/ofl"

echo "==> fetching upstream variable fonts"
curl -sSL -o "$WORK/Unbounded.ttf" "$UPSTREAM/unbounded/Unbounded%5Bwght%5D.ttf"
curl -sSL -o "$WORK/Figtree.ttf" "$UPSTREAM/figtree/Figtree%5Bwght%5D.ttf"
curl -sSL -o "$OUT/OFL-Unbounded.txt" "$UPSTREAM/unbounded/OFL.txt"
curl -sSL -o "$OUT/OFL-Figtree.txt" "$UPSTREAM/figtree/OFL.txt"

# IBM Plex Mono ships as static files upstream, so it is copied, not cut.
curl -sSL -o "$OUT/IBMPlexMono-Medium.ttf" "$UPSTREAM/ibmplexmono/IBMPlexMono-Medium.ttf"
curl -sSL -o "$OUT/OFL-IBMPlexMono.txt" "$UPSTREAM/ibmplexmono/OFL.txt"

# fonttools is not a project dependency, so it lives in a throwaway venv.
echo "==> preparing fonttools"
python3 -m venv "$WORK/venv"
"$WORK/venv/bin/pip" install --quiet fonttools

# --update-name-table is what makes each cut register as its own family. Without
# it every instance keeps the variable font's name and iOS registers only one.
cut_instance() {
  local src="$1" weight="$2" name="$3"
  "$WORK/venv/bin/fonttools" varLib.instancer "$src" "wght=$weight" \
    -o "$OUT/$name" --update-name-table >/dev/null
  echo "    $name (weight $weight)"
}

echo "==> cutting static instances"
cut_instance "$WORK/Unbounded.ttf" 800 "Unbounded-ExtraBold.ttf"
cut_instance "$WORK/Unbounded.ttf" 900 "Unbounded-Black.ttf"
cut_instance "$WORK/Figtree.ttf" 500 "Figtree-Medium.ttf"
cut_instance "$WORK/Figtree.ttf" 600 "Figtree-SemiBold.ttf"
cut_instance "$WORK/Figtree.ttf" 700 "Figtree-Bold.ttf"
cut_instance "$WORK/Figtree.ttf" 800 "Figtree-ExtraBold.ttf"
cut_instance "$WORK/Figtree.ttf" 900 "Figtree-Black.ttf"

echo "    -> $OUT"
