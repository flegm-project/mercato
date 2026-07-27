//! Ads gating: the single source of truth for "should an ad be shown?".
//!
//! The SDK calls (AdMob, StoreKit 2, Play Billing, UMP) stay native; this
//! module owns the decision rule and its parameters so iOS and Android
//! cannot drift. See docs/MONETIZATION.md and docs/specs/ads.md.
//!
//! Counting unit: an answered **question** (`Session::submit_choice` /
//! `submit_guess` finishing a question). The interstitial opportunity is the
//! natural break between the last question and the recap; the caps below
//! ("one per break", warmup, minimum questions between two interstitials)
//! are expressed in questions so they hold even if the round length changes.

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

/// Consent outcome, as reported by the native consent flow. Refusing
/// personalisation yields non-personalised ads, not fewer slots, so consent
/// never changes [`AdsGate::should_show`]; it only tells the native layer
/// which request flag (npa) to send to the ads SDK.
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
    /// Questions a fresh install/session must answer before the first
    /// interstitial.
    pub warmup_questions: u32,
    /// Minimum answered questions between two interstitials.
    pub min_questions_between_interstitials: u32,
}

impl Default for AdsConfig {
    fn default() -> Self {
        Self {
            warmup_questions: 2,
            min_questions_between_interstitials: 3,
        }
    }
}

/// App-lifetime gate. Owns the `ads_removed` entitlement flag (set natively
/// from the store on purchase/restore) and the interstitial frequency state.
#[derive(Debug, Clone, Default)]
pub struct AdsGate {
    config: AdsConfig,
    ads_removed: bool,
    consent: Consent,
    questions_completed: u32,
    /// `questions_completed` when the last interstitial was shown. Also
    /// enforces "one interstitial per break maximum".
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
        if self.questions_completed < self.config.warmup_questions {
            return false;
        }
        match self.last_interstitial_at {
            None => true,
            Some(at) => {
                self.questions_completed.saturating_sub(at)
                    >= self.config.min_questions_between_interstitials.max(1)
            }
        }
    }

    /// Call after every finished question (correct, or attempts exhausted).
    pub fn record_question_completed(&mut self) {
        self.questions_completed += 1;
    }

    /// Call when the native side actually presented an interstitial.
    pub fn record_interstitial_shown(&mut self) {
        self.last_interstitial_at = Some(self.questions_completed);
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

    pub fn consent(&self) -> Consent {
        self.consent
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
            g.record_question_completed();
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
        g.record_question_completed();
        assert!(!g.should_show(Placement::Interstitial));
        g.record_question_completed();
        assert!(g.should_show(Placement::Interstitial));
    }

    #[test]
    fn interstitial_frequency_cap() {
        let mut g = gate();
        for _ in 0..2 {
            g.record_question_completed();
        }
        assert!(g.should_show(Placement::Interstitial));
        g.record_interstitial_shown();
        // One per break max: no second interstitial without a new question.
        assert!(!g.should_show(Placement::Interstitial));
        g.record_question_completed();
        g.record_question_completed();
        assert!(!g.should_show(Placement::Interstitial));
        g.record_question_completed();
        assert!(g.should_show(Placement::Interstitial));
    }

    #[test]
    fn zero_config_cap_still_blocks_same_break_repeat() {
        let mut g = AdsGate::new(
            AdsConfig {
                warmup_questions: 0,
                min_questions_between_interstitials: 0,
            },
            false,
        );
        assert!(g.should_show(Placement::Interstitial));
        g.record_interstitial_shown();
        assert!(!g.should_show(Placement::Interstitial));
        g.record_question_completed();
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
