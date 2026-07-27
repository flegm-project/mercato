#!/usr/bin/env bash
# Build everything the iOS app consumes, then generate its Xcode project.
#
# apps/ios/project.yml is the source of truth; the .xcodeproj is generated and
# gitignored, like every other generated artifact in this repo. That keeps the
# project reviewable as a diff instead of an unreadable pbxproj.
#
# Run: ./scripts/gen-ios-project.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

command -v xcodegen >/dev/null 2>&1 || {
  echo "error: xcodegen not found. Install it with: brew install xcodegen" >&2
  exit 1
}

echo "==> generating assets"
node "$ROOT/scripts/gen-design-tokens.mjs"
node "$ROOT/scripts/gen-strings.mjs"

echo "==> building the core for iOS"
"$ROOT/scripts/build-native.sh" ios

echo "==> generating the Xcode project"
( cd "$ROOT/apps/ios" && xcodegen generate )
echo "    -> $ROOT/apps/ios/Mercato.xcodeproj"
