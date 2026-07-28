import SwiftUI

/// The two-tab bar shared by Home and Profile, matching the Android
/// `MercatoTabBar`: an ink rounded bar with the selected tab filled yellow.
struct MercatoTabBar: View {
    let tabs: [String]
    let selected: Int
    let onSelect: (Int) -> Void

    /// The inset between the bar's edge and a tab.
    private let inset: CGFloat = 6

    /// The outer corners follow the bar's own, pulled in by the inset; only the
    /// corners facing the other tab take the small radius. At a flat small
    /// radius against the bar's, the yellow left a crescent of ink in each
    /// outer corner and read as the wrong shape inside the border.
    private func shape(_ index: Int) -> UnevenRoundedRectangle {
        let outer = DesignTokens.Radius.card - inset
        let small = DesignTokens.Radius.small
        return UnevenRoundedRectangle(
            topLeadingRadius: index == 0 ? outer : small,
            bottomLeadingRadius: index == 0 ? outer : small,
            bottomTrailingRadius: index == tabs.count - 1 ? outer : small,
            topTrailingRadius: index == tabs.count - 1 ? outer : small,
            style: .continuous
        )
    }

    var body: some View {
        HStack(spacing: 6) {
            ForEach(Array(tabs.enumerated()), id: \.offset) { index, label in
                Button { onSelect(index) } label: {
                    Text(label)
                        .typeStyle(TypeToken.tabLabel)
                        .foregroundStyle(
                            index == selected
                                ? DesignTokens.Color.ink
                                : DesignTokens.Color.ivory.opacity(DesignTokens.Opacity.textMuted)
                        )
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .background(index == selected ? DesignTokens.Color.yellow : Color.clear)
                        .clipShape(shape(index))
                        // The whole cell is the target, not just the label.
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
        }
        .padding(inset)
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
                    .typeStyle(TypeToken.panelTitle)
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
                        .typeStyle(TypeToken.linkTitle)
                        .foregroundStyle(DesignTokens.Color.ink)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 16)
                        .background(DesignTokens.Color.ivory)
                        .solidRaised(radius: DesignTokens.Radius.medium, border: 4, depth: 6)
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
                .typeStyle(TypeToken.sectionTitle)
                .foregroundStyle(DesignTokens.Color.ivory)
                .multilineTextAlignment(.center)
            Text(L("statsEmptyBody"))
                .typeStyle(TypeToken.bodyMid)
                // muted is far too dark on this ink card; a soft ivory keeps the
                // body clearly legible while staying secondary to the title.
                .foregroundStyle(DesignTokens.Color.ivory.opacity(DesignTokens.Opacity.textSubtle))
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity)
        .padding(.horizontal, 18)
        .padding(.vertical, 34)
        .background(DesignTokens.Color.ink.opacity(DesignTokens.Opacity.row))
        .clipShape(RoundedRectangle(cornerRadius: DesignTokens.Radius.row, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: DesignTokens.Radius.row, style: .continuous)
                .strokeBorder(Color.white.opacity(DesignTokens.Opacity.borderFaint), lineWidth: 2)
        )
    }

    private func statRow(_ label: String, _ value: String) -> some View {
        HStack(spacing: 12) {
            Text(label)
                .typeStyle(TypeToken.bodyStrong)
                .foregroundStyle(DesignTokens.Color.ivory)
            Spacer(minLength: 0)
            Text(value)
                .typeStyle(TypeToken.statValue)
                .foregroundStyle(DesignTokens.Color.yellow)
        }
        .padding(.horizontal, 18)
        .padding(.vertical, 20)
        .background(DesignTokens.Color.ink.opacity(DesignTokens.Opacity.row))
        .clipShape(RoundedRectangle(cornerRadius: DesignTokens.Radius.row, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: DesignTokens.Radius.row, style: .continuous)
                .strokeBorder(Color.white.opacity(DesignTokens.Opacity.borderFaint), lineWidth: 2)
        )
    }
}
