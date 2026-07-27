//! Ads gating: the single source of truth for "should an ad be shown?".
//!
//! The SDK calls (AdMob, StoreKit 2, Play Billing) stay native; this module
//! owns the decision rule and its parameters so iOS and Android cannot drift.
//! See docs/MONETIZATION.md and docs/specs/ads.md.
//!
//! Terminology: a *round* is one question (`Game::start_round` /
//! `submit_guess`). Interstitial opportunities occur at natural breaks (the
//! recap after a game over); the frequency cap is expressed in completed
//! rounds between two interstitials, per MONETIZATION.md.

/// The ad slots the app may render. No rewarded ads in v1 (the prototype's
/// rewarded video only doubled a currency that no longer exists).
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Placement {
    /// 320x50 above the tab bar on menu screens. Never during a question.
    Banner,
    /// Static full-width board below the transfer card, in game.
    SponsorBoard,
    /// Full screen, between the last question and the recap.
    Interstitial,
    /// 300x250 on the recap screen.
    Rectangle,
}

/// GDPR/UMP consent outcome, as reported by the native consent flow.
/// Refusing personalisation yields non-personalised ads, not fewer slots,
/// so consent never changes [`AdsGate::should_show`]; it only tells the
/// native layer which request flag (npa) to send to AdMob.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Default)]
pub enum Consent {
    #[default]
    Unknown,
    Personalized,
    NonPersonalized,
}

/// Tunable frequency parameters. One place to change, both platforms follow.
#[derive(Debug, Clone, Copy)]
pub struct AdsConfig {
    /// Rounds a fresh session must complete before the first interstitial.
    pub warmup_rounds: u32,
    /// Minimum completed rounds between two interstitials.
    pub min_rounds_between_interstitials: u32,
}

impl Default for AdsConfig {
    fn default() -> Self {
        Self {
            warmup_rounds: 2,
            min_rounds_between_interstitials: 3,
        }
    }
}

/// Session-scoped gate. Owns the `ads_removed` entitlement flag (set natively
/// from the store on purchase/restore) and the interstitial frequency state.
#[derive(Debug, Clone, Default)]
pub struct AdsGate {
    config: AdsConfig,
    ads_removed: bool,
    consent: Consent,
    rounds_completed: u32,
    /// `rounds_completed` value when the last interstitial was shown. Also
    /// enforces "one interstitial per round maximum".
    last_interstitial_at: Option<u32>,
}

impl AdsGate {
    pub fn new(config: AdsConfig, ads_removed: bool) -> Self {
        Self {
            config,
            ads_removed,
            ..Self::default()
        }
    }

    /// The one rule: ads not removed AND the placement's frequency cap is
    /// satisfied. Banner, sponsor board and rectangle have no cap; their
    /// "where" rules are layout facts owned by the screens.
    pub fn should_show(&self, placement: Placement) -> bool {
        if self.ads_removed {
            return false;
        }
        match placement {
            Placement::Banner | Placement::SponsorBoard | Placement::Rectangle => true,
            Placement::Interstitial => self.interstitial_allowed(),
        }
    }

    fn interstitial_allowed(&self) -> bool {
        if self.rounds_completed < self.config.warmup_rounds {
            return false;
        }
        match self.last_interstitial_at {
            None => true,
            Some(at) => {
                self.rounds_completed.saturating_sub(at)
                    >= self.config.min_rounds_between_interstitials.max(1)
            }
        }
    }

    /// Call after every answered question.
    pub fn record_round_completed(&mut self) {
        self.rounds_completed += 1;
    }

    /// Call when the native side actually presented an interstitial.
    pub fn record_interstitial_shown(&mut self) {
        self.last_interstitial_at = Some(self.rounds_completed);
    }

    /// Set from the store entitlement (purchase, restore, launch check).
    /// Once true, every slot goes dark, including the interstitial.
    pub fn set_ads_removed(&mut self, removed: bool) {
        self.ads_removed = removed;
    }

    pub fn ads_removed(&self) -> bool {
        self.ads_removed
    }

    pub fn set_consent(&mut self, consent: Consent) {
        self.consent = consent;
    }

    /// Whether ad requests may be personalised (npa flag stays off). Unknown
    /// consent is treated as non-personalised, the safe default pre-flow.
    pub fn personalization_allowed(&self) -> bool {
        self.consent == Consent::Personalized
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn gate() -> AdsGate {
        AdsGate::new(AdsConfig::default(), false)
    }

    #[test]
    fn remove_ads_kills_every_slot() {
        let mut g = gate();
        for _ in 0..10 {
            g.record_round_completed();
        }
        assert!(g.should_show(Placement::Interstitial));
        g.set_ads_removed(true);
        assert!(!g.should_show(Placement::Banner));
        assert!(!g.should_show(Placement::SponsorBoard));
        assert!(!g.should_show(Placement::Interstitial));
        assert!(!g.should_show(Placement::Rectangle));
    }

    #[test]
    fn free_user_sees_static_slots() {
        let g = gate();
        assert!(g.should_show(Placement::Banner));
        assert!(g.should_show(Placement::SponsorBoard));
        assert!(g.should_show(Placement::Rectangle));
    }

    #[test]
    fn interstitial_respects_warmup() {
        let mut g = gate();
        assert!(!g.should_show(Placement::Interstitial));
        g.record_round_completed();
        assert!(!g.should_show(Placement::Interstitial));
        g.record_round_completed();
        assert!(g.should_show(Placement::Interstitial));
    }

    #[test]
    fn interstitial_frequency_cap() {
        let mut g = gate();
        for _ in 0..2 {
            g.record_round_completed();
        }
        assert!(g.should_show(Placement::Interstitial));
        g.record_interstitial_shown();
        // One per round max: no second interstitial without a new round.
        assert!(!g.should_show(Placement::Interstitial));
        g.record_round_completed();
        g.record_round_completed();
        assert!(!g.should_show(Placement::Interstitial));
        g.record_round_completed();
        assert!(g.should_show(Placement::Interstitial));
    }

    #[test]
    fn zero_config_cap_still_blocks_same_round_repeat() {
        let mut g = AdsGate::new(
            AdsConfig {
                warmup_rounds: 0,
                min_rounds_between_interstitials: 0,
            },
            false,
        );
        assert!(g.should_show(Placement::Interstitial));
        g.record_interstitial_shown();
        assert!(!g.should_show(Placement::Interstitial));
        g.record_round_completed();
        assert!(g.should_show(Placement::Interstitial));
    }

    #[test]
    fn consent_changes_personalisation_not_slots() {
        let mut g = gate();
        assert!(!g.personalization_allowed());
        assert!(g.should_show(Placement::Banner));
        g.set_consent(Consent::NonPersonalized);
        assert!(!g.personalization_allowed());
        assert!(g.should_show(Placement::Banner));
        g.set_consent(Consent::Personalized);
        assert!(g.personalization_allowed());
    }
}
