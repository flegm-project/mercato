//! UniFFI surface: the `Game` facade the iOS and Android apps call.
//!
//! This layer is deliberately thin. All rules live in `mercato-core`
//! (see `session.rs`); here we only own the corpus, guard it behind a lock,
//! and convert core types into FFI-friendly records.
//!
//! The facade is coarse-grained on purpose: a handful of methods, so binding
//! calls stay cheap and the generated Swift/Kotlin stays small.

use std::sync::{Arc, Mutex};

use std::collections::BTreeMap;

use mercato_core::distance::{threshold_for, BASE};
use mercato_core::matching::surname_variants;
use mercato_core::normalize::normalize;
use mercato_core::session::{Hint, Rejection, Session, HARDCORE_ATTEMPTS, QUESTIONS_PER_ROUND};
use mercato_core::{
    AdsConfig, AdsGate, Consent, Corpus, Kind, Lang, Mode, Placement, Position, Route,
};

uniffi::setup_scaffolding!();

// --- enums exposed to the apps ---------------------------------------------

#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum GameLang {
    En,
    Fr,
    Es,
}

impl From<GameLang> for Lang {
    fn from(l: GameLang) -> Self {
        match l {
            GameLang::En => Lang::En,
            GameLang::Fr => Lang::Fr,
            GameLang::Es => Lang::Es,
        }
    }
}

/// Resolve the system language, falling back to English (docs/GAME_DESIGN.md).
/// Apps pass their platform locale tag (e.g. "fr-FR"); there is no in-app picker.
#[uniffi::export]
pub fn language_for_locale(tag: String) -> GameLang {
    match tag
        .split(['-', '_'])
        .next()
        .unwrap_or("")
        .to_lowercase()
        .as_str()
    {
        "fr" => GameLang::Fr,
        "es" => GameLang::Es,
        _ => GameLang::En,
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum GameMode {
    /// Four options over mainstream transfers.
    Easy,
    /// Free text over the full dataset, three attempts.
    Hardcore,
}

impl From<GameMode> for Mode {
    fn from(m: GameMode) -> Self {
        match m {
            GameMode::Easy => Mode::Easy,
            GameMode::Hardcore => Mode::Hardcore,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum MoveKind {
    Transfer,
    Loan,
}

impl From<Kind> for MoveKind {
    fn from(k: Kind) -> Self {
        match k {
            Kind::Transfer => MoveKind::Transfer,
            Kind::Loan => MoveKind::Loan,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum PlayerPosition {
    Gk,
    Def,
    Mid,
    Fw,
}

impl From<Position> for PlayerPosition {
    fn from(p: Position) -> Self {
        match p {
            Position::Gk => PlayerPosition::Gk,
            Position::Def => PlayerPosition::Def,
            Position::Mid => PlayerPosition::Mid,
            Position::Fw => PlayerPosition::Fw,
        }
    }
}

/// Why an answer was refused.
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum RejectionReason {
    /// Shared surname: ask for the first name. Costs no attempt.
    AmbiguousSurname,
    Wrong,
}

impl From<Rejection> for RejectionReason {
    fn from(r: Rejection) -> Self {
        match r {
            Rejection::AmbiguousSurname => RejectionReason::AmbiguousSurname,
            Rejection::Wrong => RejectionReason::Wrong,
        }
    }
}

/// An ad slot the app may render. See mercato_core::ads.
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum AdPlacement {
    /// 320x50 on menu screens, never during a question.
    Banner,
    /// Static full-width board below the transfer card, in game.
    SponsorBoard,
    /// Full screen, between the last question and the recap.
    Interstitial,
    /// 300x250 on the recap screen.
    Rectangle,
}

impl From<AdPlacement> for Placement {
    fn from(p: AdPlacement) -> Self {
        match p {
            AdPlacement::Banner => Placement::Banner,
            AdPlacement::SponsorBoard => Placement::SponsorBoard,
            AdPlacement::Interstitial => Placement::Interstitial,
            AdPlacement::Rectangle => Placement::Rectangle,
        }
    }
}

/// Consent outcome from the native flow. Refusing personalisation gives
/// non-personalised ads, never fewer slots.
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum AdConsent {
    Unknown,
    Personalized,
    NonPersonalized,
}

impl From<AdConsent> for Consent {
    fn from(c: AdConsent) -> Self {
        match c {
            AdConsent::Unknown => Consent::Unknown,
            AdConsent::Personalized => Consent::Personalized,
            AdConsent::NonPersonalized => Consent::NonPersonalized,
        }
    }
}

// --- records ----------------------------------------------------------------

/// A question, with club names already resolved to the session language.
#[derive(Debug, Clone, uniffi::Record)]
pub struct QuestionView {
    /// 1-based position in the round.
    pub index: u32,
    pub total: u32,
    pub kind: MoveKind,
    pub year: i32,
    pub from_club: String,
    pub to_club: String,
    /// Easy: four shuffled options. Hardcore: empty.
    pub options: Vec<String>,
    pub attempts_left: u8,
    /// Hardcore only: the answer as dots, one per letter.
    pub masked_name: String,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct AnswerView {
    pub correct: bool,
    pub rejection: Option<RejectionReason>,
    pub points_gained: i64,
    pub attempts_left: u8,
    /// Set once the question is over.
    pub revealed_name: Option<String>,
    /// True when the UI should advance to the next question.
    pub finished: bool,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct ScoreView {
    pub points: i64,
    pub streak: u32,
    pub best_streak: u32,
    /// Colour hint for the score pill; `None` before the first answer.
    pub last_correct: Option<bool>,
}

/// A hint. Exactly one field is set, matching the unlock order.
#[derive(Debug, Clone, uniffi::Record)]
pub struct HintView {
    pub nationality: Option<String>,
    pub position: Option<PlayerPosition>,
    pub surname_initial: Option<String>,
    pub surname_letters: Option<u32>,
}

/// A transfer the player got wrong, for the recap screen.
#[derive(Debug, Clone, uniffi::Record)]
pub struct MissedView {
    pub year: i32,
    pub kind: MoveKind,
    pub from_club: String,
    pub to_club: String,
    pub player_name: String,
}

#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum GameError {
    // Field deliberately not named "message": UniFFI maps errors to
    // Throwable subclasses in Kotlin, where a `message` field collides
    // with Throwable.message and breaks compilation.
    #[error("could not load the dataset: {reason}")]
    DataLoad { reason: String },
    #[error("no question in progress")]
    NoQuestion,
}

// --- matching lab (dev-only screens) ----------------------------------------
// Read-only diagnostics over the matching engine, shared by the iOS and
// Android lab screens so both platforms show the exact same verdicts. The
// engine itself (matching.rs) is reused as-is; nothing here re-implements
// the rule.

#[derive(Debug, Clone, uniffi::Record)]
pub struct LabStats {
    pub players: u32,
    pub clubs: u32,
    pub transfers: u32,
    pub aliases: u32,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum LabVerdict {
    Accept,
    Reject,
    /// Shared surname: the engine asks for a first name (costs no attempt).
    Ambiguous,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct LabOutcome {
    pub verdict: LabVerdict,
    /// The name form the engine matched against, when any.
    pub best_match: Option<String>,
    /// Levenshtein distance to the best match, when one was computed.
    pub distance: Option<u32>,
    /// Distance threshold applied for the evaluated form.
    pub threshold: u32,
    /// Human-readable evaluation steps, top to bottom.
    pub trace: Vec<String>,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct LabPlayer {
    pub id: String,
    pub name: String,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct LabCollision {
    pub surname: String,
    pub players: Vec<String>,
}

// --- the facade -------------------------------------------------------------

/// Owns the dataset and the running session.
///
/// The corpus is parsed once at construction and shared with each session via
/// `Arc`; starting a round is cheap. All mutation goes through the lock, so the
/// object is safe to call from any thread the apps use.
#[derive(uniffi::Object)]
pub struct Game {
    corpus: Arc<Corpus>,
    session: Mutex<Session>,
    /// App-lifetime ads decision rule; see mercato_core::ads. Survives
    /// rounds: frequency state must not reset with the session.
    ads: Mutex<AdsGate>,
}

#[uniffi::export]
impl Game {
    /// Load the dataset from a directory of CSVs (the app bundles `data/`).
    #[uniffi::constructor]
    pub fn new(data_dir: String) -> Result<Self, GameError> {
        let corpus = mercato_data::load_corpus(std::path::Path::new(&data_dir)).map_err(|e| {
            GameError::DataLoad {
                reason: e.to_string(),
            }
        })?;
        let corpus = Arc::new(corpus);
        let session = Session::new(corpus.clone(), Lang::En, Mode::Easy, 0);
        Ok(Self {
            corpus,
            session: Mutex::new(session),
            ads: Mutex::new(AdsGate::new(AdsConfig::default(), false)),
        })
    }

    /// Begin a fresh round. `seed` makes it reproducible.
    pub fn start_round(&self, lang: GameLang, mode: GameMode, seed: u32) {
        *self.session.lock().expect("session lock") =
            Session::new(self.corpus.clone(), lang.into(), mode.into(), seed);
    }

    /// Advance to the next question, or `None` when the round is over.
    pub fn next_question(&self) -> Option<QuestionView> {
        self.session
            .lock()
            .expect("session lock")
            .next_question()
            .map(question_view)
    }

    /// The question currently on screen, if any.
    pub fn current_question(&self) -> Option<QuestionView> {
        self.session
            .lock()
            .expect("session lock")
            .current_question()
            .map(question_view)
    }

    /// Easy mode: choose an option by index.
    pub fn submit_choice(&self, index: u32) -> Result<AnswerView, GameError> {
        let answer = self
            .session
            .lock()
            .expect("session lock")
            .submit_choice(index as usize)
            .map(answer_view)
            .ok_or(GameError::NoQuestion)?;
        if answer.finished {
            self.ads
                .lock()
                .expect("ads lock")
                .record_question_completed();
        }
        Ok(answer)
    }

    /// Hardcore mode: submit a typed guess.
    pub fn submit_guess(&self, text: String) -> Result<AnswerView, GameError> {
        let answer = self
            .session
            .lock()
            .expect("session lock")
            .submit_guess(&text)
            .map(answer_view)
            .ok_or(GameError::NoQuestion)?;
        if answer.finished {
            self.ads
                .lock()
                .expect("ads lock")
                .record_question_completed();
        }
        Ok(answer)
    }

    /// Unlock the next hint (Hardcore). Hints are free: there is no shop.
    pub fn next_hint(&self) -> Option<HintView> {
        self.session
            .lock()
            .expect("session lock")
            .next_hint()
            .map(hint_view)
    }

    pub fn score(&self) -> ScoreView {
        let s = self.session.lock().expect("session lock");
        let sc = s.score();
        ScoreView {
            points: sc.points,
            streak: sc.streak,
            best_streak: sc.best_streak,
            last_correct: sc.last_correct,
        }
    }

    pub fn is_over(&self) -> bool {
        self.session.lock().expect("session lock").is_over()
    }

    /// Transfers missed this round, for the recap screen.
    pub fn missed(&self) -> Vec<MissedView> {
        let s = self.session.lock().expect("session lock");
        let lang = s.lang();
        s.missed_transfers()
            .into_iter()
            .map(|t| MissedView {
                year: t.year,
                kind: t.kind.into(),
                from_club: self.club_name(&t.from_club, lang),
                to_club: self.club_name(&t.to_club, lang),
                player_name: self
                    .corpus
                    .player(&t.player_id)
                    .map(|p| p.name(lang).to_string())
                    .unwrap_or_default(),
            })
            .collect()
    }

    /// Questions in a round.
    pub fn questions_per_round(&self) -> u32 {
        QUESTIONS_PER_ROUND as u32
    }

    /// Attempts per question in Hardcore.
    pub fn hardcore_attempts(&self) -> u8 {
        HARDCORE_ATTEMPTS
    }

    // --- matching lab (dev-only) --------------------------------------------

    /// Dataset counters for the lab's stats panel.
    pub fn lab_stats(&self) -> LabStats {
        LabStats {
            players: self.corpus.players.len() as u32,
            clubs: self.corpus.clubs.len() as u32,
            transfers: self.corpus.transfers.len() as u32,
            aliases: self
                .corpus
                .players
                .iter()
                .map(|p| p.aliases.len())
                .sum::<usize>() as u32,
        }
    }

    /// Every player (dataset order), to pick the lab's expected answer.
    pub fn lab_players(&self) -> Vec<LabPlayer> {
        self.corpus
            .players
            .iter()
            .map(|p| LabPlayer {
                id: p.id.clone(),
                name: p.name_en.clone(),
            })
            .collect()
    }

    /// Run one guess against one target through the real engine and report
    /// route, distance, threshold and a readable trace.
    pub fn lab_evaluate(&self, target_id: String, guess: String) -> LabOutcome {
        let Some(target) = self.corpus.player(&target_id) else {
            return LabOutcome {
                verdict: LabVerdict::Reject,
                best_match: None,
                distance: None,
                threshold: 0,
                trace: vec![format!("unknown target id: {target_id}")],
            };
        };
        let result = self
            .corpus
            .matcher()
            .match_by_id(&self.corpus.players, &guess, &target_id, BASE)
            .expect("target id resolved above");

        // Threshold of the form actually evaluated: the matched candidate
        // when there is one, the target's canonical name otherwise.
        let evaluated = result
            .matched
            .clone()
            .unwrap_or_else(|| target.name_en.clone());
        let threshold = threshold_for(&normalize(&evaluated), BASE) as u32;

        let route = match result.route {
            Route::Exact => "exact",
            Route::Alias => "alias",
            Route::Fuzzy => "fuzzy",
            Route::Surname => "surname",
            Route::None => "none",
        };
        let verdict = if result.ambiguous {
            LabVerdict::Ambiguous
        } else if result.ok {
            LabVerdict::Accept
        } else {
            LabVerdict::Reject
        };

        let mut trace = vec![
            format!("normalized input: {}", normalize(&guess)),
            format!(
                "known aliases: {}",
                if target.aliases.is_empty() {
                    "-".to_string()
                } else {
                    target.aliases.join(", ")
                }
            ),
            format!("route: {route}"),
            format!("best match: {}", result.matched.as_deref().unwrap_or("-")),
            format!(
                "distance: {}",
                result.dist.map_or("-".to_string(), |d| d.to_string())
            ),
            format!("threshold: {threshold}"),
        ];
        trace.push(match verdict {
            LabVerdict::Accept => "verdict: accepted".to_string(),
            LabVerdict::Reject => "verdict: rejected".to_string(),
            LabVerdict::Ambiguous => "verdict: ambiguous, ask for the first name".to_string(),
        });

        LabOutcome {
            verdict,
            best_match: result.matched,
            distance: result.dist.map(|d| d as u32),
            threshold,
            trace,
        }
    }

    /// Surnames shared by several players (the cases Hardcore must refuse
    /// as a bare surname), with the players carrying each of them.
    pub fn lab_collisions(&self) -> Vec<LabCollision> {
        // Same construction rule as the engine's SURNAME_INDEX: surname
        // variants of the canonical names only. BTreeMap keeps the output
        // ordered and stable for the UI.
        let mut by_surname: BTreeMap<String, Vec<String>> = BTreeMap::new();
        for p in &self.corpus.players {
            for nm in p.canonical() {
                for v in surname_variants(nm) {
                    let entry = by_surname.entry(v).or_default();
                    if !entry.contains(&p.name_en) {
                        entry.push(p.name_en.clone());
                    }
                }
            }
        }
        by_surname
            .into_iter()
            .filter(|(_, players)| players.len() > 1)
            .map(|(surname, players)| LabCollision { surname, players })
            .collect()
    }

    // --- ads gate (see mercato_core::ads) -----------------------------------
    // The SDK calls (AdMob, StoreKit, Play Billing) stay native; the decision
    // rule lives here so both apps stay consistent.

    /// Ask right before rendering a slot or presenting an interstitial.
    pub fn should_show_ad(&self, placement: AdPlacement) -> bool {
        self.ads
            .lock()
            .expect("ads lock")
            .should_show(placement.into())
    }

    /// Call once an interstitial was actually presented, so the frequency
    /// cap starts counting from here.
    pub fn record_interstitial_shown(&self) {
        self.ads
            .lock()
            .expect("ads lock")
            .record_interstitial_shown();
    }

    /// Flip the remove-ads entitlement from the store (launch check,
    /// purchase, restore).
    pub fn set_ads_removed(&self, removed: bool) {
        self.ads.lock().expect("ads lock").set_ads_removed(removed);
    }

    pub fn ads_removed(&self) -> bool {
        self.ads.lock().expect("ads lock").ads_removed()
    }

    /// Report the consent outcome so ad requests carry the right flag.
    pub fn set_ad_consent(&self, consent: AdConsent) {
        self.ads
            .lock()
            .expect("ads lock")
            .set_consent(consent.into());
    }

    /// False means send non-personalised requests (npa) to the ads SDK.
    pub fn ad_personalization_allowed(&self) -> bool {
        self.ads.lock().expect("ads lock").personalization_allowed()
    }
}

impl Game {
    fn club_name(&self, id: &str, lang: Lang) -> String {
        self.corpus
            .club(id)
            .map(|c| c.name(lang).to_string())
            .unwrap_or_else(|| id.to_string())
    }
}

// --- conversions ------------------------------------------------------------

fn question_view(q: mercato_core::Question) -> QuestionView {
    QuestionView {
        index: q.index as u32,
        total: q.total as u32,
        kind: q.kind.into(),
        year: q.year,
        from_club: q.from_club,
        to_club: q.to_club,
        options: q.options,
        attempts_left: q.attempts_left,
        masked_name: q.masked_name,
    }
}

fn answer_view(a: mercato_core::Answer) -> AnswerView {
    AnswerView {
        correct: a.correct,
        rejection: a.rejection.map(Into::into),
        points_gained: a.points_gained,
        attempts_left: a.attempts_left,
        revealed_name: a.revealed_name,
        finished: a.finished,
    }
}

fn hint_view(h: Hint) -> HintView {
    match h {
        Hint::Nationality(n) => HintView {
            nationality: Some(n),
            position: None,
            surname_initial: None,
            surname_letters: None,
        },
        Hint::Position(p) => HintView {
            nationality: None,
            position: Some(p.into()),
            surname_initial: None,
            surname_letters: None,
        },
        Hint::SurnameShape { initial, letters } => HintView {
            nationality: None,
            position: None,
            surname_initial: Some(initial.to_string()),
            surname_letters: Some(letters as u32),
        },
    }
}
