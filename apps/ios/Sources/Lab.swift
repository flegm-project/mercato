#if DEBUG
import SwiftUI

/// Screen 13 (dev only): drives the real matching engine through the shared
/// core facade. Pick the expected player, type a guess, and watch the engine's
/// verdict, trace, threshold and best match live, plus dataset stats and the
/// surname collisions Hardcore must refuse. Compiled out of release builds, so
/// the row that opens it only exists behind DEBUG too (docs/specs/screens.md).
struct LabView: View {
    let game: Game
    let onBack: () -> Void

    @State private var players: [LabPlayer] = []
    @State private var stats: LabStats?
    @State private var collisions: [LabCollision] = []
    @State private var targetFilter = ""
    @State private var targetId: String?
    @State private var guess = ""
    @State private var outcome: LabOutcome?

    private var targetName: String? { players.first { $0.id == targetId }?.name }

    var body: some View {
        ZStack {
            DS.appBackground
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    header
                    targetSection
                    guessSection
                    if let outcome { outcomeSection(outcome) }
                    statsSection
                    collisionsSection
                }
                .padding(DesignTokens.Space.gutter)
                .frame(maxWidth: DesignTokens.Layout.columnMax)
                .frame(maxWidth: .infinity)
            }
        }
        .onAppear {
            players = game.labPlayers()
            stats = game.labStats()
            collisions = game.labCollisions()
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 12) {
                Button(action: onBack) {
                    Text("\u{2039}")
                        .font(.system(size: 20, weight: .black))
                        .foregroundStyle(.white)
                        .frame(width: 38, height: 38)
                        .background(DesignTokens.Color.ink.opacity(0.45))
                        .inkOutlined(RoundedRectangle(cornerRadius: 12, style: .continuous))
                }
                .buttonStyle(.plain)
                Text(L("labT"))
                    .typeStyle(TypeToken.panelTitle)
                    .foregroundStyle(DesignTokens.Color.ivory)
            }
            Text(L("labN"))
                .typeStyle(TypeToken.labNote)
                .foregroundStyle(DesignTokens.Color.ivory.opacity(0.7))
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    private var targetSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            caps(L("labTarget"))
            labField(L("labTarget"), text: $targetFilter)
            if let targetName {
                Text(targetName)
                    .typeStyle(TypeToken.labLabel)
                    .foregroundStyle(DesignTokens.Color.yellow)
            }
            let filter = targetFilter.trimmingCharacters(in: .whitespaces)
            if !filter.isEmpty {
                let hits = players.filter { $0.name.range(of: filter, options: .caseInsensitive) != nil }.prefix(6)
                ForEach(Array(hits), id: \.id) { player in
                    Button {
                        targetId = player.id
                        targetFilter = ""
                        evaluate()
                    } label: {
                        Text(player.name)
                            .typeStyle(TypeToken.bodyMid)
                            .foregroundStyle(DesignTokens.Color.ivory)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 10)
                            .background(DesignTokens.Color.ink.opacity(0.3))
                            .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    private var guessSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            caps(L("labGuess"))
            labField(L("ph"), text: $guess)
                .onChange(of: guess) { _, _ in evaluate() }
                .onChange(of: targetId) { _, _ in evaluate() }
        }
    }

    private func outcomeSection(_ o: LabOutcome) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(verdictLabel(o.verdict))
                .typeStyle(TypeToken.verdictChip)
                .foregroundStyle(verdictColor(o.verdict))
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(DesignTokens.Color.ink.opacity(0.4))
                .clipShape(Capsule())
            Text("\(L("labTh")): \(o.threshold)")
                .typeStyle(TypeToken.monoPlain)
                .foregroundStyle(DesignTokens.Color.ivory)
            Text("\(L("kBest")): \(o.bestMatch ?? L("rNoneL"))" + (o.distance.map { " (\($0))" } ?? ""))
                .typeStyle(TypeToken.monoPlain)
                .foregroundStyle(DesignTokens.Color.ivory.opacity(0.9))
            VStack(alignment: .leading, spacing: 3) {
                ForEach(Array(o.trace.enumerated()), id: \.offset) { _, line in
                    Text(line)
                        .typeStyle(TypeToken.monoPlain)
                        .foregroundStyle(DesignTokens.Color.ivory.opacity(0.85))
                }
            }
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(DesignTokens.Color.ink.opacity(0.3))
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        }
    }

    private var statsSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            caps(L("stats"))
            if let stats {
                statLine(L("sPlayers"), stats.players)
                statLine(L("sClubs"), stats.clubs)
                statLine(L("sTr"), stats.transfers)
                statLine(L("kAlias"), stats.aliases)
                statLine(L("sAmb"), UInt32(collisions.count))
            }
        }
    }

    private var collisionsSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            caps(L("ambT"))
            Text(L("ambN"))
                .typeStyle(TypeToken.labCaption)
                .foregroundStyle(DesignTokens.Color.ivory.opacity(0.6))
                .fixedSize(horizontal: false, vertical: true)
            ForEach(Array(collisions.prefix(30).enumerated()), id: \.offset) { _, c in
                VStack(alignment: .leading, spacing: 2) {
                    Text(c.surname)
                        .typeStyle(TypeToken.bodySmallStrong)
                        .foregroundStyle(DesignTokens.Color.yellow)
                    Text(c.players.joined(separator: ", "))
                        .typeStyle(TypeToken.labFine)
                        .foregroundStyle(DesignTokens.Color.ivory.opacity(0.75))
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(10)
                .background(DesignTokens.Color.ink.opacity(0.3))
                .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
            }
        }
    }

    // MARK: - Pieces

    private func evaluate() {
        guard let targetId, !guess.trimmingCharacters(in: .whitespaces).isEmpty else {
            outcome = nil
            return
        }
        outcome = game.labEvaluate(targetId: targetId, guess: guess)
    }

    private func verdictLabel(_ v: LabVerdict) -> String {
        switch v {
        case .accept: return L("yes")
        case .reject: return L("no")
        case .ambiguous: return L("amb")
        }
    }

    private func verdictColor(_ v: LabVerdict) -> Color {
        switch v {
        case .accept: return DesignTokens.Color.green
        case .reject: return DesignTokens.Color.coral
        case .ambiguous: return DesignTokens.Color.yellow
        }
    }

    private func caps(_ text: String) -> some View {
        Text(text.uppercased())
            .typeStyle(TypeToken.labCaps)
            .foregroundStyle(DesignTokens.Color.ivory.opacity(0.55))
    }

    private func labField(_ placeholder: String, text: Binding<String>) -> some View {
        TextField(placeholder, text: text)
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled()
            .typeStyle(TypeToken.body)
            .foregroundStyle(DesignTokens.Color.ink)
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
            .background(DesignTokens.Color.ivory)
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    }

    private func statLine(_ label: String, _ value: UInt32) -> some View {
        HStack {
            Text(label)
                .typeStyle(TypeToken.labLine)
                .foregroundStyle(DesignTokens.Color.ivory)
            Spacer()
            Text("\(value)")
                .typeStyle(TypeToken.monoValue)
                .foregroundStyle(DesignTokens.Color.yellow)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 9)
        .background(DesignTokens.Color.ink.opacity(0.3))
        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
    }
}
#endif
