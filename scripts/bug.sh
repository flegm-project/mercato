#!/usr/bin/env bash
# Capture what a bug report needs, from whichever phone is plugged in.
#
#   ./scripts/bug.sh "play again does nothing after a lost round"
#   ./scripts/bug.sh ios "the score pill overlaps the pips"
#   ./scripts/bug.sh android "settings scrolls under the banner"
#
# Writes build/bugs/<timestamp>/ with a screenshot, the device and app
# versions, the commit the build came from, and the last few seconds of log.
# report.md is written to be pasted as-is: the context is the part that is
# gone by the time anyone asks for it.
#
# With no platform argument it takes the Android phone if one is attached,
# otherwise the iPhone. The emulator is used only when nothing else is.
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
export PATH="$ANDROID_HOME/platform-tools:$PATH"
AND_PKG="com.flegm.mercato"
IOS_BID="com.flegm.mercato"

PLATFORM=""
case "${1:-}" in
  ios|android) PLATFORM="$1"; shift ;;
esac
SUMMARY="${*:-}"
[ -n "$SUMMARY" ] || { echo "usage: $0 [ios|android] \"what went wrong\"" >&2; exit 2; }

pick_android() {
  adb devices | awk '$2=="device" && $1!~/^emulator/ {print $1; exit}'
}
pick_emulator() {
  adb devices | awk '$2=="device" {print $1; exit}'
}
pick_ios() {
  xcrun devicectl list devices 2>/dev/null | awk '/available/ {print $3; exit}'
}

if [ -z "$PLATFORM" ]; then
  if [ -n "$(pick_android)" ]; then PLATFORM=android
  elif [ -n "$(pick_ios)" ]; then PLATFORM=ios
  elif [ -n "$(pick_emulator)" ]; then PLATFORM=android
  else echo "no phone attached and no emulator running" >&2; exit 1
  fi
fi

STAMP=$(date +%Y%m%d-%H%M%S)
DIR="$ROOT/build/bugs/$STAMP"
mkdir -p "$DIR"

COMMIT=$(git -C "$ROOT" rev-parse --short HEAD 2>/dev/null || echo unknown)
DIRTY=$(git -C "$ROOT" status --porcelain 2>/dev/null | head -1)
[ -n "$DIRTY" ] && COMMIT="$COMMIT (uncommitted changes)"

case "$PLATFORM" in
  android)
    DEV=$(pick_android); [ -n "$DEV" ] || DEV=$(pick_emulator)
    [ -n "$DEV" ] || { echo "no Android device" >&2; exit 1; }
    MODEL=$(adb -s "$DEV" shell getprop ro.product.model | tr -d '\r')
    OSVER=$(adb -s "$DEV" shell getprop ro.build.version.release | tr -d '\r')
    APIVER=$(adb -s "$DEV" shell getprop ro.build.version.sdk | tr -d '\r')
    # "Override size" when the phone is not running at its native resolution,
    # which a Samsung lets you pick and which changes the layout.
    SIZE=$(adb -s "$DEV" shell wm size | tr -d '\r' | tail -1 | sed 's/.*: //')
    DENS=$(adb -s "$DEV" shell wm density | tr -d '\r' | tail -1 | sed 's/.*: //')
    APPVER=$(adb -s "$DEV" shell dumpsys package "$AND_PKG" \
             | awk -F= '/versionName/ {print $2; exit}' | tr -d '\r')
    SCREEN=$(adb -s "$DEV" shell dumpsys window \
             | awk -F/ '/mCurrentFocus/ {print $NF}' | tr -d '\r}' | head -1)
    adb -s "$DEV" exec-out screencap -p > "$DIR/screen.png" 2>/dev/null
    # Only this app, only what is still in the buffer: a full logcat is noise.
    PID=$(adb -s "$DEV" shell pidof "$AND_PKG" | tr -d '\r')
    if [ -n "$PID" ]; then
      adb -s "$DEV" logcat -d --pid="$PID" -t 800 > "$DIR/log.txt" 2>/dev/null
      # A Samsung narrates every surface it draws, so the report carries only
      # warnings and worse. The full buffer stays in log.txt.
      adb -s "$DEV" logcat -d --pid="$PID" -t 800 '*:W' > "$DIR/log-warn.txt" 2>/dev/null
    else
      adb -s "$DEV" logcat -d -t 800 > "$DIR/log.txt" 2>/dev/null
      grep -iE "mercato|FATAL|AndroidRuntime" "$DIR/log.txt" > "$DIR/log-warn.txt" 2>/dev/null
    fi
    DEVICE_LINE="$MODEL, Android $OSVER (API $APIVER), $SIZE, $DENS"
    ;;
  ios)
    DEV=$(pick_ios)
    [ -n "$DEV" ] || { echo "no paired iPhone" >&2; exit 1; }
    INFO=$(xcrun devicectl list devices 2>/dev/null | grep "$DEV")
    MODEL=$(echo "$INFO" | sed -E 's/.*available \(paired\) +//')
    xcrun devicectl device info details --device "$DEV" > "$DIR/device.txt" 2>/dev/null
    OSVER=$(awk -F: '/osVersionNumber/ {gsub(/ /,"",$2); print $2; exit}' "$DIR/device.txt")
    APPVER=$(/usr/libexec/PlistBuddy -c "Print :CFBundleShortVersionString" \
             "$ROOT/apps/ios/build/DeviceData/Build/Products/Debug-iphoneos/Mercato.app/Info.plist" \
             2>/dev/null || echo unknown)
    # There is no screencap for a device over devicectl, so this is the one
    # thing the reporter has to supply: the phone's own screenshot.
    echo "add the screenshot yourself: take one on the phone and drop it here" \
      > "$DIR/screen-README.txt"
    xcrun devicectl device info processes --device "$DEV" 2>/dev/null \
      | grep -i mercato > "$DIR/log.txt"
    SCREEN="(iOS: name the screen yourself)"
    DEVICE_LINE="$MODEL, iOS $OSVER"
    ;;
esac

{
  echo "## $SUMMARY"
  echo
  echo "| | |"
  echo "| --- | --- |"
  echo "| platform | $PLATFORM |"
  echo "| device | $DEVICE_LINE |"
  echo "| app | ${APPVER:-unknown} |"
  echo "| build | $COMMIT |"
  echo "| screen | ${SCREEN:-unknown} |"
  echo "| when | $(date '+%Y-%m-%d %H:%M:%S %Z') |"
  echo
  echo "### What I did"
  echo
  echo "1. "
  echo
  echo "### What I expected"
  echo
  echo "### What happened"
  echo
  if [ -s "$DIR/log-warn.txt" ]; then
    echo "### Log (warnings and worse; the full buffer is in log.txt)"
    echo
    echo '```'
    tail -30 "$DIR/log-warn.txt"
    echo '```'
  fi
} > "$DIR/report.md"

echo "==> $DIR"
[ -f "$DIR/screen.png" ] && echo "    screen.png"
[ -s "$DIR/log.txt" ] && echo "    log.txt  ($(wc -l < "$DIR/log.txt" | tr -d ' ') lines)"
echo "    report.md   <- fill in the three sections and paste it"
command -v pbcopy >/dev/null 2>&1 && pbcopy < "$DIR/report.md" && \
  echo "    (report.md is on the clipboard)"
