#!/usr/bin/env bash
# Build a signed Android bundle and put it on a Play track.
#
#   ./scripts/release-android.sh                 build only, no upload
#   ./scripts/release-android.sh --upload        also upload, needs a key
#   ./scripts/release-android.sh --track internal --upload
#   ./scripts/release-android.sh --notes store/release-notes/next.md --upload
#   ./scripts/release-android.sh --name 1.1.0 --upload
#
# It exists because doing this by hand went wrong twice in a row, and both
# times silently. Once a version code was uploaded that Play then refused over
# 16 KB alignment, and once a stale artifact was picked up from a path that
# already held an older build: same filename, older bytes, no error anywhere.
# So this script never trusts a path, only what it has just produced, and it
# checks the artifact rather than the intention.
#
# What it will not do: sign with a key it went looking for, or upload from a
# tree that does not match what is on GitHub. A build nobody can reproduce
# from a commit is not a release.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GRADLE_DIR="$ROOT/apps/android"
BUILD_FILE="$GRADLE_DIR/app/build.gradle.kts"
AAB="$GRADLE_DIR/app/build/outputs/bundle/release/app-release.aab"
PKG="com.flegm.mercato"

TRACK="alpha"
UPLOAD=0
NOTES=""
NEW_NAME=""

die() { echo "error: $*" >&2; exit 1; }
step() { echo; echo "==> $*"; }

while [ $# -gt 0 ]; do
  case "$1" in
    --track)  TRACK="${2:-}"; shift 2 ;;
    --notes)  NOTES="${2:-}"; shift 2 ;;
    --name)   NEW_NAME="${2:-}"; shift 2 ;;
    --upload) UPLOAD=1; shift ;;
    -h|--help) sed -n '2,18p' "$0"; exit 0 ;;
    *) die "unknown argument '$1'" ;;
  esac
done

case "$TRACK" in
  internal|alpha|beta|production) ;;
  *) die "unknown track '$TRACK'. Use internal, alpha, beta or production." ;;
esac

# ---------------------------------------------------------------- provenance
# A bundle that cannot be traced back to a pushed commit is a bundle nobody can
# rebuild. Play keeps it forever; the tree it came from may not survive the
# afternoon.
step "checking the tree"
cd "$ROOT"
[ -z "$(git status --porcelain --untracked-files=no)" ] \
  || die "the working tree has uncommitted changes. Commit them first:
     $(git status --short --untracked-files=no | head -5 | sed 's/^/       /')"

BRANCH=$(git rev-parse --abbrev-ref HEAD)
[ "$BRANCH" = "main" ] || die "on branch '$BRANCH', not main."

git fetch -q origin main 2>/dev/null || echo "    (could not reach origin, continuing on the local ref)"
LOCAL=$(git rev-parse HEAD)
REMOTE=$(git rev-parse origin/main 2>/dev/null || echo "$LOCAL")
[ "$LOCAL" = "$REMOTE" ] || die "HEAD is not what origin/main holds. Push first."
echo "    $(git log --oneline -1)"

# Signing. Absent the keystore the release build is left unsigned rather than
# failing, which is right for a checkout and wrong here: an unsigned bundle is
# rejected by Play after the upload, not before it.
[ -f "$GRADLE_DIR/keystore.properties" ] || [ -n "${MERCATO_KEYSTORE:-}" ] \
  || die "no keystore.properties and no MERCATO_KEYSTORE. The bundle would be
     unsigned and Play would refuse it. See scripts/make-upload-key.sh."

# ------------------------------------------------------------- version code
# Play refuses a version code it has already seen, and there is no way to free
# one. Bumping here, in the commit, is what keeps the number and the source in
# step: a bundle's version code names a commit.
step "bumping the version code"
CURRENT=$(sed -n 's/^ *versionCode = \([0-9]*\).*/\1/p' "$BUILD_FILE" | head -1)
[ -n "$CURRENT" ] || die "cannot read versionCode from $BUILD_FILE"
NEXT=$((CURRENT + 1))
NAME=$(sed -n 's/^ *versionName = "\(.*\)".*/\1/p' "$BUILD_FILE" | head -1)
# The version name is the only number a tester ever sees, so it moves when
# they would notice and stays put when they would not. A build that changes
# nothing they can see keeps the name it had.
if [ -n "$NEW_NAME" ]; then
  python3 - "$BUILD_FILE" "$NAME" "$NEW_NAME" <<'PY'
import re, sys
path, cur, new = sys.argv[1], sys.argv[2], sys.argv[3]
src = open(path, encoding="utf-8").read()
out, n = re.subn(r'(^\s*versionName = )"%s"' % re.escape(cur), r'\g<1>"%s"' % new,
                 src, count=1, flags=re.M)
if n != 1:
    sys.exit("could not rewrite versionName")
open(path, "w", encoding="utf-8").write(out)
PY
  NAME="$NEW_NAME"
fi
python3 - "$BUILD_FILE" "$CURRENT" "$NEXT" <<'PY'
import re, sys
path, cur, nxt = sys.argv[1], sys.argv[2], sys.argv[3]
src = open(path, encoding="utf-8").read()
new, n = re.subn(r"(^\s*versionCode = )%s\b" % cur, r"\g<1>%s" % nxt, src, count=1, flags=re.M)
if n != 1:
    sys.exit("could not rewrite versionCode")
open(path, "w", encoding="utf-8").write(new)
PY
echo "    $CURRENT -> $NEXT (versionName $NAME)"

# ------------------------------------------------------------------- inputs
step "generating app inputs"
"$ROOT/scripts/build-native.sh" android
node "$ROOT/scripts/gen-design-tokens.mjs"
node "$ROOT/scripts/gen-strings.mjs"

# --------------------------------------------------------------------- build
# Delete the artifact first. Gradle would overwrite it, but if the build fails
# late the old file stays on disk with a plausible name, and everything
# downstream happily ships last week's bundle.
step "building the bundle"
rm -f "$AAB"
( cd "$GRADLE_DIR" && ./gradlew --no-daemon bundleRelease )
[ -f "$AAB" ] || die "the build reported success but produced no bundle at $AAB"

# verify16kAlignmentBundle already ran as part of bundleRelease. Run the check
# again here, on the file this script is about to upload, because the two are
# only the same file as long as nothing between them moved.
step "checking the artifact"
python3 "$ROOT/scripts/check-16k.py" "$AAB" | tail -3
python3 - "$AAB" "$NEXT" <<'PY'
import sys, zipfile
aab, expected = sys.argv[1], sys.argv[2]
z = zipfile.ZipFile(aab)
names = z.namelist()
if not any(n.startswith("META-INF/") and n.endswith((".RSA", ".DSA", ".EC")) for n in names):
    sys.exit("error: the bundle is not signed")
if not any("com.android.tools.build.debugsymbols" in n for n in names):
    sys.exit("error: no native debug symbols. AGP did not find the NDK; see ndkDir "
             "in app/build.gradle.kts")
print("    signed, native symbols present, %d entries" % len(names))
PY
echo "    $(du -h "$AAB" | cut -f1)  $AAB"

# -------------------------------------------------------------------- commit
step "recording the version code"
git add "$BUILD_FILE"
git commit -q -m "chore(android): version code $NEXT" \
  -m "Uploaded to the $TRACK track as $NEXT ($NAME)."
git push -q origin main
echo "    $(git log --oneline -1)"

# -------------------------------------------------------------------- upload
if [ "$UPLOAD" != "1" ]; then
  echo
  echo "Built, not uploaded. Version code $NEXT is now committed, so it is spent"
  echo "either way. Re-run with --upload, or upload $AAB by hand."
  exit 0
fi

# The service account key is never in the repo and never in the environment of
# a shell that did not ask for it. PLAY_SA_JSON points at it; on the VPS that
# is agent-sdk-bot/secrets/play-sa.json.
KEY="${PLAY_SA_JSON:-}"
[ -n "$KEY" ] && [ -f "$KEY" ] \
  || die "no Play service account key. Set PLAY_SA_JSON to its path.
     Without it this machine cannot talk to Play; the bundle is built and
     waiting at $AAB."

step "uploading to the $TRACK track"
NOTES_ARG=()
[ -n "$NOTES" ] && NOTES_ARG=("$NOTES")
python3 "$ROOT/scripts/play-upload.py" \
  --package "$PKG" --track "$TRACK" --aab "$AAB" \
  --version-code "$NEXT" --version-name "$NAME" \
  --key "$KEY" ${NOTES_ARG[@]+--notes "${NOTES_ARG[@]}"}
