#!/usr/bin/env bash
# Build the app and put it on a real phone.
#
#   ./scripts/install-device.sh ios        # the paired iPhone
#   ./scripts/install-device.sh android    # the phone on USB, or the emulator
#   ./scripts/install-device.sh both
#
# iOS needs a signing identity in the login keychain and the device paired
# with this Mac. `security find-identity -v -p codesigning` must list one; if
# it lists the certificate but says "0 valid identities", the Apple WWDR
# intermediate is missing or expired, and installing the current one fixes it:
#
#   curl -O https://www.apple.com/certificateauthority/AppleWWDRCAG3.cer
#   security import AppleWWDRCAG3.cer -k ~/Library/Keychains/login.keychain-db
#
# The iPhone also needs Developer Mode on: Settings > Privacy & Security >
# Developer Mode, then restart the phone. Without it xcodebuild reports
# "Timed out waiting for all destinations", which does not mention the phone.
#
# Android needs USB debugging on and the phone authorised for this Mac.
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WHAT="${1:-both}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
export PATH="$ANDROID_HOME/platform-tools:$PATH"

do_ios() {
  echo "==> iOS"
  if ! security find-identity -v -p codesigning 2>/dev/null | grep -q "Apple Development"; then
    echo "    no valid signing identity, see the header of this script" >&2
    return 1
  fi
  local udid
  udid=$(xcrun devicectl list devices 2>/dev/null | awk '/available/ {print $3; exit}')
  [ -n "$udid" ] || { echo "    no paired iPhone found" >&2; return 1; }

  "$ROOT/scripts/gen-ios-project.sh" >/dev/null || return 1
  xcodebuild \
    -project "$ROOT/apps/ios/Mercato.xcodeproj" \
    -scheme Mercato \
    -configuration Debug \
    -destination "id=$udid" \
    -derivedDataPath "$ROOT/apps/ios/build/DeviceData" \
    -allowProvisioningUpdates \
    build || return 1

  local app="$ROOT/apps/ios/build/DeviceData/Build/Products/Debug-iphoneos/Mercato.app"
  xcrun devicectl device install app --device "$udid" "$app" || return 1
  echo "    installed on $udid"
  echo "    first launch: Settings > General > VPN & Device Management, trust the developer"
}

do_android() {
  echo "==> Android"
  local target
  target=$(adb devices | awk '$2=="device" && $1!~/^emulator/ {print $1; exit}')
  [ -n "$target" ] || target=$(adb devices | awk '$2=="device" {print $1; exit}')
  [ -n "$target" ] || { echo "    no device or emulator" >&2; return 1; }

  ( cd "$ROOT/apps/android" && ./gradlew :app:assembleDebug -q ) || return 1
  adb -s "$target" install -r "$ROOT/apps/android/app/build/outputs/apk/debug/app-debug.apk" || return 1
  echo "    installed on $target"
}

rc=0
case "$WHAT" in
  ios) do_ios || rc=1 ;;
  android) do_android || rc=1 ;;
  both) do_ios || rc=1; do_android || rc=1 ;;
  *) echo "usage: $0 [ios|android|both]" >&2; exit 2 ;;
esac
exit $rc
