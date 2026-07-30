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
# The project references the three .wav files by name, so they have to exist
# before xcodegen runs or a fresh clone generates a project that cannot build.
node "$ROOT/scripts/gen-sounds.mjs"
# The event vocabulary, shared with Android and compiled into the app.
node "$ROOT/scripts/gen-analytics.mjs"
# The project references build/icons/ios/Assets.xcassets, and build/ is
# gitignored: without this, a fresh clone cannot even generate the project.
# Android has run this as a Gradle task for a while; iOS never did.
node "$ROOT/scripts/gen-app-icon.mjs"
# Same reason: the project references build/art/OnboardingScenes.swift.
node "$ROOT/scripts/gen-onboarding-scenes.mjs"

echo "==> building the core for iOS"
"$ROOT/scripts/build-native.sh" ios

echo "==> generating the Xcode project"
( cd "$ROOT/apps/ios" && xcodegen generate )
echo "    -> $ROOT/apps/ios/Mercato.xcodeproj"
