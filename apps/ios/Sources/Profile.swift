import SwiftUI

/// The two-tab bar shared by Home and Profile, matching the Android
/// `MercatoTabBar`: an ink rounded bar with the selected tab filled yellow.
struct MercatoTabBar: View {
    let tabs: [String]
    let selected: Int
    let onSelect: (Int) -> Void

    var body: some View {
        HStack(spacing: 6) {
            ForEach(Array(tabs.enumerated()), id: \.offset) { index, label in
                Button { onSelect(index) } label: {
                    Text(label)
                        .font(DS.unbounded(14, weight: 900))
                        .tracking(-0.02 * 14)
                        .foregroundStyle(
                            index == selected
                                ? DesignTokens.Color.ink
                                : DesignTokens.Color.ivory.opacity(0.7)
                        )
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .background(index == selected ? DesignTokens.Color.yellow : Color.clear)
                        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                }
                .buttonStyle(.plain)
            }
        }
        .padding(6)
        .frame(height: 58)
        .background(DesignTokens.Color.ink)
        .clipShape(RoundedRectangle(cornerRadius: DesignTokens.Radius.card, style: .continuous))
    }
}

// MARK: - Profile

/// Screen 10: four lifetime stats and the door to Settings, over the shared
/// tab bar (docs/specs/screens.md). The stats persist in `AppStats`; the score
/// itself never does.
struct ProfileView: View {
    @ObservedObject var stats: AppStats
    let game: Game
    let onPlayTab: () -> Void
    let onSettings: () -> Void

    var body: some View {
        ZStack {
            DS.appBackground
            VStack(spacing: 0) {
                Text(L("profile"))
                    .font(DS.unbounded(20, weight: 900))
                    .tracking(-0.04 * 20)
                    .foregroundStyle(DesignTokens.Color.ivory)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.top, 8)

                // Kept at the top, away from the tab bar and the Settings
                // button, so it cannot be mis-tapped.
                BannerSlot(game: game)
                    .frame(maxWidth: .infinity)
                    .padding(.top, 16)

                Spacer(minLength: 0)

                // Before the first round every stat is zero, and a column of
                // zeros reads as broken. Show an inviting empty state instead;
                // the real stats replace it as soon as one round is played.
                if stats.roundsPlayed == 0 {
                    emptyStats
                } else {
                    VStack(spacing: 12) {
                        statRow(L("stPlayed"), "\(stats.roundsPlayed)")
                        statRow(L("stBest"), "\(stats.bestScore)")
                        statRow(L("stStreak"), "\(stats.bestStreak)")
                        statRow(L("stAcc"), stats.accuracy)
                    }
                }

                Button(action: onSettings) {
                    Text(L("settings"))
                        .font(DS.unbounded(16, weight: 900))
                        .tracking(-0.03 * 16)
                        .foregroundStyle(DesignTokens.Color.ink)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 16)
                        .background(DesignTokens.Color.ivory)
                        .solidRaised(radius: 18, border: 4, depth: 6)
                }
                .buttonStyle(.plain)
                .padding(.top, 20)

                Spacer(minLength: 0)

                MercatoTabBar(tabs: [L("tPlay"), L("tProfile")], selected: 1) { index in
                    if index == 0 { onPlayTab() }
                }
            }
            .padding(.horizontal, DesignTokens.Space.gutter)
            .padding(.vertical, DesignTokens.Space.gutter)
            .frame(maxWidth: DesignTokens.Layout.columnMax)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }

    /// Shown on the Profile before any round has been played, in place of the
    /// four zeroed stat rows.
    private var emptyStats: some View {
        VStack(spacing: 10) {
            Text(L("statsEmptyTitle"))
                .font(DS.unbounded(18, weight: 900))
                .tracking(-0.03 * 18)
                .foregroundStyle(DesignTokens.Color.ivory)
                .multilineTextAlignment(.center)
            Text(L("statsEmptyBody"))
                .font(DS.figtree(14, weight: 700))
                .foregroundStyle(DesignTokens.Color.muted)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity)
        .padding(.horizontal, 18)
        .padding(.vertical, 34)
        .background(DesignTokens.Color.ink.opacity(0.35))
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .strokeBorder(Color.white.opacity(0.12), lineWidth: 2)
        )
    }

    private func statRow(_ label: String, _ value: String) -> some View {
        HStack(spacing: 12) {
            Text(label)
                .font(DS.figtree(15, weight: 800))
                .foregroundStyle(DesignTokens.Color.ivory)
            Spacer(minLength: 0)
            Text(value)
                .font(DS.unbounded(26, weight: 900))
                .foregroundStyle(DesignTokens.Color.yellow)
        }
        .padding(.horizontal, 18)
        .padding(.vertical, 20)
        .background(DesignTokens.Color.ink.opacity(0.35))
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .strokeBorder(Color.white.opacity(0.12), lineWidth: 2)
        )
    }
}
