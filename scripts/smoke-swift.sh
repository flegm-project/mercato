#!/usr/bin/env bash
# End-to-end smoke test of the Rust core through its Swift bindings.
#
# Typechecking proves the generated Swift is valid; this proves the whole chain
# actually runs: Rust logic -> UniFFI scaffolding -> Swift bindings -> a real
# round played against the shipped dataset. It builds for the host (macOS
# arm64) because that can be executed directly; the iOS slices are covered by
# `build-native.sh ios`, which builds the same crate for the device and
# simulator triples.
#
# Run: ./scripts/smoke-swift.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CORE="$ROOT/core"
WORK="$ROOT/build/smoke-swift"

command -v swiftc >/dev/null 2>&1 || {
  echo "error: swiftc not found (install Xcode or the Command Line Tools)" >&2
  exit 1
}

echo "==> building the core for the host"
( cd "$CORE" && cargo build -p mercato-ffi )

LIB_DIR="$CORE/target/debug"
mkdir -p "$WORK"

echo "==> generating Swift bindings"
( cd "$CORE" && cargo run -q --bin uniffi-bindgen -- \
    generate --library "$LIB_DIR/libmercato_ffi.dylib" \
    --language swift --out-dir "$WORK/bindings" )

# UniFFI names the module after the header; the generated modulemap expects the
# header next to it, which it already is.
cat > "$WORK/main.swift" <<'SWIFT'
import Foundation

// Plays one full Easy round and one Hardcore question, printing enough to see
// that the core is really driving the game and not returning placeholders.
let dataDir = CommandLine.arguments.count > 1
    ? CommandLine.arguments[1]
    : FileManager.default.currentDirectoryPath + "/data"

let game: Game
do {
    game = try Game(dataDir: dataDir)
} catch {
    FileHandle.standardError.write("failed to load dataset: \(error)\n".data(using: .utf8)!)
    exit(1)
}

print("locale fr-FR resolves to \(languageForLocale(tag: "fr-FR"))")
print("locale de-DE resolves to \(languageForLocale(tag: "de-DE")) (fallback)")

game.startRound(lang: .fr, mode: .easy, seed: 2024)
var asked = 0
while let q = game.nextQuestion() {
    asked += 1
    if asked == 1 {
        print("Q\(q.index)/\(q.total): \(q.fromClub) -> \(q.toClub) (\(q.year), \(q.kind))")
        print("  options: \(q.options.joined(separator: " | "))")
    }
    guard q.options.count == 4 else {
        FileHandle.standardError.write("expected 4 options, got \(q.options.count)\n".data(using: .utf8)!)
        exit(1)
    }
    _ = try? game.submitChoice(index: 0)
}
let score = game.score()
print("easy round: \(asked) questions, \(score.points) points, best streak \(score.bestStreak), \(game.missed().count) missed")

guard asked == Int(game.questionsPerRound()) else {
    FileHandle.standardError.write("expected \(game.questionsPerRound()) questions, played \(asked)\n".data(using: .utf8)!)
    exit(1)
}

// Hardcore, per-round lives: the hint ladder answers while the question is
// open (nationality first), one wrong guess settles the question, costs one
// of the round's lives, and reveals the name.
game.startRound(lang: .en, mode: .hardcore, seed: 99)
guard let hq = game.nextQuestion() else {
    FileHandle.standardError.write("hardcore produced no question\n".data(using: .utf8)!)
    exit(1)
}
print("hardcore: \(hq.fromClub) -> \(hq.toClub) (\(hq.year)), \(hq.attemptsLeft) lives")
if let hint = game.nextHint(), let nat = hint.nationality {
    print("  first hint (nationality): \(nat)")
} else {
    FileHandle.standardError.write("first hint was not a nationality\n".data(using: .utf8)!)
    exit(1)
}
let wrong = try game.submitGuess(text: "zzzzzzzzzz")
print("  wrong guess -> correct=\(wrong.correct) finished=\(wrong.finished) livesLeft=\(wrong.attemptsLeft)")
guard !wrong.correct, wrong.finished, wrong.attemptsLeft == hq.attemptsLeft - 1,
      wrong.revealedName != nil else {
    FileHandle.standardError.write("a wrong guess must settle the question, cost one life and reveal the name\n".data(using: .utf8)!)
    exit(1)
}

print("OK: Rust core drives the game through the Swift bindings")
SWIFT

echo "==> compiling the Swift smoke test"
swiftc -O \
  -o "$WORK/smoke" \
  -I "$WORK/bindings" \
  -L "$LIB_DIR" -lmercato_ffi \
  -Xcc -fmodule-map-file="$WORK/bindings/mercato_ffiFFI.modulemap" \
  "$WORK/bindings/mercato_ffi.swift" "$WORK/main.swift"

echo "==> running"
"$WORK/smoke" "$ROOT/data"
