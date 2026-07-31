#!/usr/bin/env bash
# Create the Android upload key, and wire the build to it.
#
# This is the one secret in the project that no tool and no assistant should
# ever hold: the key is what lets Mercato be updated for the rest of its life,
# and a password that has passed through a transcript is not a password. So the
# script never takes one as an argument, never echoes one, and never writes one
# anywhere except the file the build reads, which git ignores.
#
# It exists rather than a command in a document because the key's parameters
# are worth recording: alias, algorithm, size and validity are choices, and in
# ten years the only place they will still be written down is here.
#
# Run: ./scripts/make-upload-key.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
KEYSTORE="${MERCATO_KEYSTORE:-$HOME/mercato-upload.jks}"
ALIAS="mercato-upload"
PROPS="$ROOT/apps/android/keystore.properties"

# Never silently replace a keystore. Overwriting the one the store already
# knows is the single mistake from which there is no way back.
if [ -e "$KEYSTORE" ]; then
  echo "error: $KEYSTORE already exists." >&2
  echo "       Refusing to touch it. If you really want a new key, move that" >&2
  echo "       file somewhere safe first, and remember the Play Store only" >&2
  echo "       accepts uploads signed by the key it already has." >&2
  exit 1
fi

echo "==> Creating the upload key at $KEYSTORE"
echo "    keytool will ask for a password twice. Choose one you can find again:"
echo "    it cannot be recovered, and it is what stands between you and every"
echo "    future update of the app."
echo

# 4096-bit RSA and 10000 days: Google requires the key to outlive the app, and
# a key that expires before the listing does is a listing that cannot be
# updated. The distinguished name carries the studio, not a person.
keytool -genkeypair -v \
  -keystore "$KEYSTORE" \
  -alias "$ALIAS" \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=Flegm, O=Flegm, C=FR"

echo
echo "==> Writing $PROPS"
echo "    This file is git-ignored. It is the only place the password is"
echo "    stored, and it never leaves this machine."

# Read it here rather than reuse keytool's: the prompt is the user's, and this
# script has no way to see what was typed into another process.
printf "    Repeat the same password so the build can open the keystore: "
read -rs PASSWORD
echo

umask 077
cat > "$PROPS" <<PROPSEOF
storeFile=$KEYSTORE
storePassword=$PASSWORD
keyAlias=$ALIAS
keyPassword=$PASSWORD
PROPSEOF
unset PASSWORD

echo
echo "==> Done."
echo "    Verify with: cd apps/android && ./gradlew bundleRelease"
echo
echo "    Two things left that this script cannot do for you:"
echo "      1. Back up $KEYSTORE somewhere that is not this Mac."
echo "      2. Enrol in Play App Signing at the first upload, so Google holds"
echo "         the app signing key and this one is only the upload key. That is"
echo "         the arrangement where losing it stays recoverable."
