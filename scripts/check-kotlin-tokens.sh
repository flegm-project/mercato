#!/usr/bin/env bash
# Compile the generated Kotlin design tokens to catch syntax and typing errors.
#
# The Swift side is covered by smoke-swift.sh and a swiftc typecheck. Kotlin had
# no equivalent, which is how a reserved-name bug slipped into the generated
# Swift once already (a syntax-only check would not have caught it).
#
# DesignTokens.kt imports Compose types, and there is no Android app with a
# Gradle setup yet, so it is compiled against minimal stand-ins for exactly the
# five Compose symbols it uses. This is a stopgap: once the Android app exists,
# its own Gradle build compiles the real file against real Compose and this
# script can go.
#
# Run: ./scripts/check-kotlin-tokens.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TOKENS="$ROOT/build/tokens/DesignTokens.kt"
WORK="$ROOT/build/kotlin-check"

command -v kotlinc >/dev/null 2>&1 || {
  echo "error: kotlinc not found. Install it with: brew install kotlin" >&2
  exit 1
}

[ -f "$TOKENS" ] || node "$ROOT/scripts/gen-design-tokens.mjs"

rm -rf "$WORK"
mkdir -p "$WORK"

cat > "$WORK/compose-stubs.kt" <<'KT'
// Stand-ins for the only Compose symbols DesignTokens.kt imports. Signatures
// match the real ones closely enough that a mismatch in the generated file
// still fails to compile.
package androidx.compose.ui.graphics

class Color(val value: Long)
KT

cat > "$WORK/unit-stubs.kt" <<'KT'
package androidx.compose.ui.unit

class Dp(val value: Float)
class TextUnit(val value: Float)

val Int.dp: Dp get() = Dp(this.toFloat())
val Double.dp: Dp get() = Dp(this.toFloat())
val Int.sp: TextUnit get() = TextUnit(this.toFloat())
val Double.sp: TextUnit get() = TextUnit(this.toFloat())
KT

echo "==> compiling generated Kotlin tokens"
kotlinc "$WORK/compose-stubs.kt" "$WORK/unit-stubs.kt" "$TOKENS" -d "$WORK/out"

classes=$(find "$WORK/out" -name '*.class' | wc -l | tr -d ' ')
[ "$classes" -gt 0 ] || {
  echo "error: kotlinc produced no classes" >&2
  exit 1
}
echo "    ok, $classes classes"
