#!/usr/bin/env bash
# Capture every screen on both platforms, at the same logical size.
#
# Both apps carry a debug route affordance (iOS: -MercatoRoute <name>, Android:
# --es route <name>) so a screen can be reached directly, without walking the
# flow and without the UMP consent form landing on the capture. That is what
# makes these comparable at all: navigating by hand gave a different question,
# a different device size and an occasional ad form.
#
# The Android emulator is resized to the iPhone's logical size (402x874) rather
# than its own 411x914, so a difference in the output is a difference in the
# layout rather than in the canvas.
#
# Run: ./scripts/capture-parity.sh [route ...]

set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$ROOT/build/parity-shots"
IOS_SIM="${IOS_SIM:-29FF0D80-629D-4A35-95F1-FC1E5EF7CDEB}"   # iPhone 17 Pro
AND_PKG="com.nicogaray.mercato"
AND_ACT="com.mercato.app.MainActivity"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
export PATH="$ANDROID_HOME/platform-tools:$PATH"

ROUTES=("$@")
if [ ${#ROUTES[@]} -eq 0 ]; then
  ROUTES=(onboarding consent home easy hardcore recap recaplose profile settings lab offline)
fi

mkdir -p "$OUT/ios" "$OUT/android"

# Screenshot once the screen has stopped changing, rather than after a fixed
# wait. A cold start here can take well over the 5s this used to sleep: the
# core has to load the dataset and build the round before the screen has
# anything on it, and a capture taken too early is a bare background. That then
# reads as a screen with no bands at all, which is indistinguishable from a
# real layout difference. Screens that settle sooner also stop costing 5s each.
#
# $1 shot path, $2 a command that writes a capture to $1.
settle() {
  local out="$1" grab="$2" prev="" cur="" i
  for i in $(seq 1 20); do
    sleep 1
    eval "$grab" >/dev/null 2>&1
    cur=$(md5 -q "$out" 2>/dev/null || echo none)
    # Two identical frames, and never before 3s: an animation can hold still
    # between two of its own frames.
    if [ "$i" -ge 3 ] && [ "$cur" = "$prev" ]; then return 0; fi
    prev="$cur"
  done
  return 1
}

echo "==> iOS"
APP="$ROOT/apps/ios/build/DerivedData/Build/Products/Debug-iphonesimulator/Mercato.app"
BID=$(/usr/libexec/PlistBuddy -c "Print :CFBundleIdentifier" "$APP/Info.plist")
xcrun simctl boot "$IOS_SIM" 2>/dev/null
xcrun simctl bootstatus "$IOS_SIM" >/dev/null 2>&1
xcrun simctl install "$IOS_SIM" "$APP"
for r in "${ROUTES[@]}"; do
  xcrun simctl terminate "$IOS_SIM" "$BID" >/dev/null 2>&1
  # A fresh container per route keeps a stored streak or consent choice from
  # one capture leaking into the next.
  xcrun simctl launch "$IOS_SIM" "$BID" -MercatoRoute "$r" >/dev/null 2>&1
  settle "$OUT/ios/$r.png" \
    "xcrun simctl io '$IOS_SIM' screenshot '$OUT/ios/$r.png'" && ok=ok || ok=UNSETTLED
  printf '    %-12s %s\n' "$r" "$([ -f "$OUT/ios/$r.png" ] && echo "$ok" || echo FAILED)"
done

echo "==> Android"
# Install first: capturing against a stale build is how a whole run came back
# showing the same screen for every route.
APK="$ROOT/apps/android/app/build/outputs/apk/debug/app-debug.apk"
[ -f "$APK" ] || { echo "    no debug apk, run ./gradlew :app:assembleDebug" >&2; exit 1; }
adb install -r "$APK" >/dev/null 2>&1 || { echo "    install failed" >&2; exit 1; }
# Match the iPhone's logical size, not the emulator's own.
adb shell wm size 1055x2294 >/dev/null 2>&1
adb shell wm density 420 >/dev/null 2>&1
# The first launch after an install pays for dexopt on top of everything else,
# and the very first route otherwise comes back showing the system splash.
adb shell am start -n "$AND_PKG/$AND_ACT" --es route home >/dev/null 2>&1
sleep 10
for r in "${ROUTES[@]}"; do
  adb shell am force-stop "$AND_PKG" >/dev/null 2>&1
  adb shell am start -n "$AND_PKG/$AND_ACT" --es route "$r" >/dev/null 2>&1
  settle "$OUT/android/$r.png" \
    "adb exec-out screencap -p > '$OUT/android/$r.png'" && ok=ok || ok=UNSETTLED
  size=$(stat -f%z "$OUT/android/$r.png" 2>/dev/null || echo 0)
  printf '    %-12s %s\n' "$r" "$([ "$size" -gt 10000 ] && echo "$ok" || echo FAILED)"
done

echo "==> side by side"
"$ROOT/scripts/side-by-side" "$OUT" "${ROUTES[@]}" 2>/dev/null || \
  echo "    (build scripts/side-by-side first)"
echo "    -> $OUT"
