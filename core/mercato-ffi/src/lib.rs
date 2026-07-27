//! UniFFI surface for Mercato: the `Game` facade native apps call.
//!
//! Both modes are wired. Easy is 4-option multiple choice
//! (`submit_guess`); Hardcore is free-text (`submit_text_guess`) with 3
//! attempts and the free hint ladder nationality -> position -> initial +
//! letter count (per docs/GAME_DESIGN.md and the web prototype's
//! `paintHints`). Ambiguous surnames are refused without consuming an
//! attempt (engine spec, "Ambiguity").

use std::path::Path;
use std::sync::Mutex;

use mercato_core::decoy::distractors_for;
use mercato_core::round::{pick_index, pool};
use mercato_core::scoring::Score;
use mercato_core::{AdsConfig, AdsGate, Consent, Kind, Lang, Mode, Mulberry32, Placement};

uniffi::setup_scaffolding!();

#[derive(uniffi::Enum, Debug, Clone, Copy, PartialEq, Eq)]
pub enum FfiLang {
    En,
    Fr,
    Es,
}

impl From<FfiLang> for Lang {
    fn from(l: FfiLang) -> Self {
        match l {
            FfiLang::En => Lang::En,
            FfiLang::Fr => Lang::Fr,
            FfiLang::Es => Lang::Es,
        }
    }
}

#[derive(uniffi::Enum, Debug, Clone, Copy, PartialEq, Eq)]
pub enum FfiMode {
    Easy,
    Hardcore,
}

impl From<FfiMode> for Mode {
    fn from(m: FfiMode) -> Self {
        match m {
            FfiMode::Easy => Mode::Easy,
            FfiMode::Hardcore => Mode::Hardcore,
        }
    }
}

#[derive(uniffi::Enum, Debug, Clone, Copy, PartialEq, Eq)]
pub enum FfiPlacement {
    Banner,
    SponsorBoard,
    Interstitial,
    Rectangle,
}

impl From<FfiPlacement> for Placement {
    fn from(p: FfiPlacement) -> Self {
        match p {
            FfiPlacement::Banner => Placement::Banner,
            FfiPlacement::SponsorBoard => Placement::SponsorBoard,
            FfiPlacement::Interstitial => Placement::Interstitial,
            FfiPlacement::Rectangle => Placement::Rectangle,
        }
    }
}

#[derive(uniffi::Enum, Debug, Clone, Copy, PartialEq, Eq)]
pub enum FfiConsent {
    Unknown,
    Personalized,
    NonPersonalized,
}

impl From<FfiConsent> for Consent {
    fn from(c: FfiConsent) -> Self {
        match c {
            FfiConsent::Unknown => Consent::Unknown,
            FfiConsent::Personalized => Consent::Personalized,
            FfiConsent::NonPersonalized => Consent::NonPersonalized,
        }
    }
}

fn kind_str(k: Kind) -> &'static str {
    match k {
        Kind::Transfer => "transfer",
        Kind::Loan => "loan",
        Kind::Free => "free",
    }
}

#[derive(uniffi::Record, Debug, Clone)]
pub struct OptionView {
    pub player_id: String,
    pub name: String,
}

#[derive(uniffi::Record, Debug, Clone)]
pub struct RoundView {
    /// Increments on every `start_round`; guess submissions are rejected if
    /// it does not match the round currently in play (guards stale UI state).
    pub round_id: u64,
    pub transfer_id: i64,
    pub kind: String,
    pub year: i32,
    pub from_club: String,
    pub to_club: String,
    /// 4 options in randomized (seeded) order. Empty in Hardcore mode.
    pub options: Vec<OptionView>,
    /// Guesses left before the round is lost. Always 3 at round start in
    /// Hardcore; 1 in Easy (a single tap decides the round).
    pub attempts_left: u8,
}

/// One unlocked hint, already localized for the round's language.
#[derive(uniffi::Enum, Debug, Clone, PartialEq, Eq)]
pub enum Hint {
    /// Localized nationality display name (empty if unknown for the player).
    Nationality { text: String },
    /// Localized position label (empty if unknown for the player).
    Position { text: String },
    /// Surname initial (uppercased) and surname letter count, mirroring the
    /// prototype's final hint chips.
    InitialAndLength { initial: String, letters: u32 },
}

#[derive(uniffi::Enum, Debug, Clone, Copy, PartialEq, Eq)]
pub enum TextVerdict {
    /// Right player: the round is closed and `result` is set.
    Correct,
    /// Wrong guess: an attempt was consumed and the next hint unlocked.
    Wrong,
    /// Shared surname: ask for a first name. No attempt consumed.
    Ambiguous,
    /// That wrong guess was the last attempt: round closed, `result` set.
    RoundLost,
}

#[derive(uniffi::Record, Debug, Clone)]
pub struct TextGuessResult {
    pub verdict: TextVerdict,
    pub attempts_left: u8,
    /// Hint unlocked by this failed attempt, if any remained.
    pub new_hint: Option<Hint>,
    /// Set when the round ended (`Correct` or `RoundLost`).
    pub result: Option<AnswerResult>,
}

#[derive(uniffi::Record, Debug, Clone)]
pub struct AnswerResult {
    pub correct: bool,
    pub points_gained: i64,
    pub total_points: i64,
    pub streak: u32,
    pub best_streak: u32,
    pub correct_player_id: String,
    pub correct_player_name: String,
}

#[derive(uniffi::Record, Debug, Clone)]
pub struct ScoreView {
    pub points: i64,
    pub streak: u32,
    pub best_streak: u32,
}

#[derive(uniffi::Error, Debug, Clone)]
pub enum GameError {
    Load { message: String },
    EmptyPool,
    NoRoundActive,
    StaleRound,
    UnknownOption { player_id: String },
    NotSupported { what: String },
}

impl std::fmt::Display for GameError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            GameError::Load { message } => write!(f, "failed to load dataset: {message}"),
            GameError::EmptyPool => write!(f, "no transfers available for this mode"),
            GameError::NoRoundActive => write!(f, "no round in progress; call start_round first"),
            GameError::StaleRound => write!(f, "guess does not match the current round"),
            GameError::UnknownOption { player_id } => {
                write!(f, "'{player_id}' is not one of the current round's options")
            }
            GameError::NotSupported { what } => write!(f, "not supported yet: {what}"),
        }
    }
}

impl std::error::Error for GameError {}

struct CurrentRound {
    round_id: u64,
    target_player_idx: usize,
    mode: Mode,
    lang: Lang,
    attempts_left: u8,
    /// 0..=3, Hardcore only: how far down the hint ladder this round is.
    hints_unlocked: u8,
}

struct GameState {
    rng: Mulberry32,
    score: Score,
    next_round_id: u64,
    current: Option<CurrentRound>,
    ads: AdsGate,
}

/// Fisher-Yates shuffle driven by the game's seeded RNG, so option order is
/// reproducible for a given seed (useful for tests and support requests).
fn shuffle<T>(items: &mut [T], rng: &mut Mulberry32) {
    for i in (1..items.len()).rev() {
        let j = (rng.next_f64() * (i as f64 + 1.0)) as usize;
        items.swap(i, j);
    }
}

#[derive(uniffi::Object)]
pub struct Game {
    corpus: mercato_core::Corpus,
    state: Mutex<GameState>,
}

#[uniffi::export]
impl Game {
    /// Load the bundled dataset from `db_path` (the SQLite artifact produced
    /// by `mercato_data::generate_db`) and start a fresh session. `seed`
    /// drives round selection, decoys, and option order; pass a
    /// time-derived value from the native side for a real session.
    /// `ads_removed` is the store entitlement known at launch (StoreKit 2 /
    /// Play Billing); it can be flipped later with `set_ads_removed`.
    #[uniffi::constructor]
    pub fn new(db_path: String, seed: u64, ads_removed: bool) -> Result<Self, GameError> {
        let corpus =
            mercato_data::load_from_db(Path::new(&db_path)).map_err(|e| GameError::Load {
                message: e.to_string(),
            })?;
        Ok(Self {
            corpus,
            state: Mutex::new(GameState {
                rng: Mulberry32::new(seed as u32),
                score: Score::new(),
                next_round_id: 1,
                current: None,
                ads: AdsGate::new(AdsConfig::default(), ads_removed),
            }),
        })
    }

    /// Pick a transfer for `mode` and build its round view.
    pub fn start_round(&self, mode: FfiMode, lang: FfiLang) -> Result<RoundView, GameError> {
        let mode: Mode = mode.into();
        let lang: Lang = lang.into();
        let mut st = self.state.lock().expect("game state mutex poisoned");

        let candidates = pool(&self.corpus.transfers, mode);
        let idx_in_pool = pick_index(candidates.len(), &mut st.rng).ok_or(GameError::EmptyPool)?;
        let transfer = candidates[idx_in_pool];
        let target_player_idx = self
            .corpus
            .players
            .iter()
            .position(|p| p.id == transfer.player_id)
            .expect("transfer.player_id must reference a real player");

        let options = if mode == Mode::Easy {
            let decoy_idxs = distractors_for(&self.corpus.players, target_player_idx, &mut st.rng);
            let mut option_idxs: Vec<usize> = decoy_idxs;
            option_idxs.push(target_player_idx);
            shuffle(&mut option_idxs, &mut st.rng);
            option_idxs
                .into_iter()
                .map(|idx| {
                    let p = &self.corpus.players[idx];
                    OptionView {
                        player_id: p.id.clone(),
                        name: p.name(lang).to_string(),
                    }
                })
                .collect()
        } else {
            Vec::new()
        };

        let attempts_left = match mode {
            Mode::Easy => 1,
            Mode::Hardcore => 3,
        };
        let round_id = st.next_round_id;
        st.next_round_id += 1;
        st.current = Some(CurrentRound {
            round_id,
            target_player_idx,
            mode,
            lang,
            attempts_left,
            hints_unlocked: 0,
        });

        let from_club = self.club_name(&transfer.from_club, lang);
        let to_club = self.club_name(&transfer.to_club, lang);

        Ok(RoundView {
            round_id,
            transfer_id: transfer.id,
            kind: kind_str(transfer.kind).to_string(),
            year: transfer.year,
            from_club,
            to_club,
            options,
            attempts_left,
        })
    }

    /// Submit an MCQ answer (Easy mode): `player_id` must be one of the last
    /// `start_round`'s options. Scores against the round currently open;
    /// `round_id` (from `RoundView`) guards against a stale/duplicate submit.
    pub fn submit_guess(
        &self,
        round_id: u64,
        player_id: String,
    ) -> Result<AnswerResult, GameError> {
        let mut st = self.state.lock().expect("game state mutex poisoned");
        let current = current_round(&st, round_id, Mode::Easy)?;
        let target_player_idx = current.target_player_idx;

        let guessed_idx = self
            .corpus
            .players
            .iter()
            .position(|p| p.id == player_id)
            .ok_or_else(|| GameError::UnknownOption {
                player_id: player_id.clone(),
            })?;

        let correct = guessed_idx == target_player_idx;
        Ok(self.close_round(&mut st, target_player_idx, correct))
    }

    /// Submit a free-text guess (Hardcore mode). Wrong guesses consume an
    /// attempt and unlock the next hint; ambiguous surnames consume nothing.
    pub fn submit_text_guess(
        &self,
        round_id: u64,
        guess: String,
    ) -> Result<TextGuessResult, GameError> {
        let mut st = self.state.lock().expect("game state mutex poisoned");
        let current = current_round(&st, round_id, Mode::Hardcore)?;
        let target_player_idx = current.target_player_idx;
        let lang = current.lang;

        let outcome = self.corpus.matcher().match_answer(
            &guess,
            target_player_idx,
            mercato_core::matching::BASE_DISTANCE,
        );

        if outcome.ambiguous {
            return Ok(TextGuessResult {
                verdict: TextVerdict::Ambiguous,
                attempts_left: current.attempts_left,
                new_hint: None,
                result: None,
            });
        }
        if outcome.ok {
            let result = self.close_round(&mut st, target_player_idx, true);
            return Ok(TextGuessResult {
                verdict: TextVerdict::Correct,
                attempts_left: 0,
                new_hint: None,
                result: Some(result),
            });
        }

        let current = st.current.as_mut().expect("checked above");
        current.attempts_left -= 1;
        let attempts_left = current.attempts_left;
        let new_hint = if current.hints_unlocked < 3 {
            current.hints_unlocked += 1;
            let n = current.hints_unlocked;
            Some(self.hint(target_player_idx, lang, n))
        } else {
            None
        };

        if attempts_left == 0 {
            let result = self.close_round(&mut st, target_player_idx, false);
            return Ok(TextGuessResult {
                verdict: TextVerdict::RoundLost,
                attempts_left: 0,
                new_hint,
                result: Some(result),
            });
        }
        Ok(TextGuessResult {
            verdict: TextVerdict::Wrong,
            attempts_left,
            new_hint,
            result: None,
        })
    }

    /// Voluntarily unlock the next hint (Hardcore; hints are free gameplay,
    /// never a product). Returns `None` once all three are unlocked.
    pub fn request_hint(&self, round_id: u64) -> Result<Option<Hint>, GameError> {
        let mut st = self.state.lock().expect("game state mutex poisoned");
        let current = current_round(&st, round_id, Mode::Hardcore)?;
        let (target_player_idx, lang) = (current.target_player_idx, current.lang);
        let current = st.current.as_mut().expect("checked above");
        if current.hints_unlocked >= 3 {
            return Ok(None);
        }
        current.hints_unlocked += 1;
        let n = current.hints_unlocked;
        Ok(Some(self.hint(target_player_idx, lang, n)))
    }

    /// Every hint unlocked so far for the current round, ladder order.
    pub fn current_hints(&self, round_id: u64) -> Result<Vec<Hint>, GameError> {
        let st = self.state.lock().expect("game state mutex poisoned");
        let current = current_round(&st, round_id, Mode::Hardcore)?;
        Ok((1..=current.hints_unlocked)
            .map(|n| self.hint(current.target_player_idx, current.lang, n))
            .collect())
    }

    pub fn score(&self) -> ScoreView {
        let st = self.state.lock().expect("game state mutex poisoned");
        ScoreView {
            points: st.score.points,
            streak: st.score.streak,
            best_streak: st.score.best_streak,
        }
    }

    // Ads gating (see mercato_core::ads). The AdMob/StoreKit/Billing SDK
    // calls stay native; the decision rule lives here, shared by both apps.

    /// Ask right before rendering a slot or presenting an interstitial.
    pub fn should_show_ad(&self, placement: FfiPlacement) -> bool {
        let st = self.state.lock().expect("game state mutex poisoned");
        st.ads.should_show(placement.into())
    }

    /// Call once an interstitial was actually presented, so the frequency
    /// cap starts counting from this round.
    pub fn record_interstitial_shown(&self) {
        let mut st = self.state.lock().expect("game state mutex poisoned");
        st.ads.record_interstitial_shown();
    }

    /// Flip the remove-ads entitlement after a purchase or restore.
    pub fn set_ads_removed(&self, removed: bool) {
        let mut st = self.state.lock().expect("game state mutex poisoned");
        st.ads.set_ads_removed(removed);
    }

    pub fn ads_removed(&self) -> bool {
        let st = self.state.lock().expect("game state mutex poisoned");
        st.ads.ads_removed()
    }

    /// Report the UMP consent outcome so ad requests carry the right flag.
    pub fn set_ad_consent(&self, consent: FfiConsent) {
        let mut st = self.state.lock().expect("game state mutex poisoned");
        st.ads.set_consent(consent.into());
    }

    /// False means send non-personalised requests (npa=1) to AdMob.
    pub fn ad_personalization_allowed(&self) -> bool {
        let st = self.state.lock().expect("game state mutex poisoned");
        st.ads.personalization_allowed()
    }
}

/// Borrow the round `round_id` if it is open and in `mode`.
fn current_round(
    st: &GameState,
    round_id: u64,
    mode: Mode,
) -> Result<&CurrentRound, GameError> {
    let current = st.current.as_ref().ok_or(GameError::NoRoundActive)?;
    if current.round_id != round_id {
        return Err(GameError::StaleRound);
    }
    if current.mode != mode {
        return Err(GameError::NotSupported {
            what: format!("this call is for {:?} rounds", mode),
        });
    }
    Ok(current)
}

impl Game {
    fn club_name(&self, id: &str, lang: Lang) -> String {
        self.corpus
            .clubs
            .iter()
            .find(|c| c.id == id)
            .map(|c| c.name(lang).to_string())
            .unwrap_or_else(|| id.to_string())
    }

    /// Score the answer, advance the ads gate, and clear the round.
    fn close_round(
        &self,
        st: &mut GameState,
        target_player_idx: usize,
        correct: bool,
    ) -> AnswerResult {
        let target = &self.corpus.players[target_player_idx];
        let points_gained = st.score.answer(correct);
        st.current = None;
        st.ads.record_round_completed();
        AnswerResult {
            correct,
            points_gained,
            total_points: st.score.points,
            streak: st.score.streak,
            best_streak: st.score.best_streak,
            correct_player_id: target.id.clone(),
            correct_player_name: target.name_en.clone(),
        }
    }

    /// Hint number `n` (1-based) of the ladder for the round's target,
    /// localized. Order per docs/GAME_DESIGN.md: nationality -> position ->
    /// initial + letter count; surname = last space-separated token, as in
    /// the prototype's `paintHints`.
    fn hint(&self, target_player_idx: usize, lang: Lang, n: u8) -> Hint {
        let p = &self.corpus.players[target_player_idx];
        match n {
            1 => Hint::Nationality {
                text: p
                    .nationality
                    .as_deref()
                    .map(|nat| self.corpus.nationality_label(nat, lang).to_string())
                    .unwrap_or_default(),
            },
            2 => Hint::Position {
                text: p
                    .position
                    .map(|pos| pos.label(lang))
                    .unwrap_or_default()
                    .to_string(),
            },
            3 => {
                let surname = p.name(lang).split(' ').next_back().unwrap_or_default();
                Hint::InitialAndLength {
                    initial: surname
                        .chars()
                        .next()
                        .map(|c| c.to_uppercase().to_string())
                        .unwrap_or_default(),
                    letters: surname.chars().count() as u32,
                }
            }
            _ => unreachable!("hint ladder has exactly 3 steps"),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use mercato_core::{Club, Corpus, Player, Transfer};

    fn player(id: &str, name: &str) -> Player {
        Player {
            id: id.into(),
            name_en: name.into(),
            name_fr: name.into(),
            name_es: name.into(),
            aliases: vec![],
            position: None,
            nationality: None,
            birth_year: None,
            notoriety: 1,
        }
    }

    fn club(id: &str, name: &str) -> Club {
        Club {
            id: id.into(),
            name_en: name.into(),
            name_fr: name.into(),
            name_es: name.into(),
            notoriety: 1,
        }
    }

    fn seed_db() -> (tempfile::TempDir, String) {
        let dir = tempfile::tempdir().expect("tempdir");
        let db_path = dir.path().join("mercato.sqlite");

        // p0/p1 share a surname so the ambiguity path is testable.
        let mut players: Vec<Player> = (0..6)
            .map(|i| player(&format!("p{i}"), &format!("Player {i}")))
            .collect();
        players[0].name_en = "Alan Shearer".into();
        players[0].name_fr = "Alan Shearer".into();
        players[0].name_es = "Alan Shearer".into();
        players[0].nationality = Some("France".into());
        players[0].position = Some(mercato_core::Position::Fw);
        players[1].name_en = "Bob Shearer".into();
        players[1].name_fr = "Bob Shearer".into();
        players[1].name_es = "Bob Shearer".into();

        let clubs = vec![club("c1", "Club One"), club("c2", "Club Two")];
        let transfers = vec![Transfer {
            id: 1,
            player_id: "p0".into(),
            from_club: "c1".into(),
            to_club: "c2".into(),
            year: 2022,
            kind: Kind::Transfer,
            tier: 1,
        }];
        let mut corpus = Corpus::new(players, clubs, transfers);
        corpus.nationalities = vec![mercato_core::Nationality {
            key: "France".into(),
            name_en: "France".into(),
            name_fr: "France".into(),
            name_es: "Francia".into(),
        }];
        mercato_data::generate_db(&corpus, &db_path).expect("generate_db");

        (dir, db_path.to_string_lossy().into_owned())
    }

    #[test]
    fn plays_a_full_round() {
        let (_dir, db_path) = seed_db();
        let game = Game::new(db_path, 42, false).expect("game loads");

        let round = game
            .start_round(FfiMode::Easy, FfiLang::En)
            .expect("round starts");
        assert_eq!(round.transfer_id, 1);
        assert_eq!(round.from_club, "Club One");
        assert_eq!(round.to_club, "Club Two");
        assert_eq!(round.options.len(), 4);
        assert!(round.options.iter().any(|o| o.player_id == "p0"));

        let wrong_id = round
            .options
            .iter()
            .find(|o| o.player_id != "p0")
            .unwrap()
            .player_id
            .clone();
        let miss = game
            .submit_guess(round.round_id, wrong_id)
            .expect("guess accepted");
        assert!(!miss.correct);
        assert_eq!(miss.points_gained, 0);
        assert_eq!(miss.correct_player_id, "p0");

        // Round is now closed; guessing again must fail.
        assert!(matches!(
            game.submit_guess(round.round_id, "p0".into()),
            Err(GameError::NoRoundActive)
        ));

        let round2 = game
            .start_round(FfiMode::Easy, FfiLang::En)
            .expect("second round starts");
        let hit = game
            .submit_guess(round2.round_id, "p0".into())
            .expect("guess accepted");
        assert!(hit.correct);
        assert_eq!(hit.points_gained, 3);
        assert_eq!(hit.total_points, 3);
        assert_eq!(hit.streak, 1);

        let score = game.score();
        assert_eq!(score.points, 3);
        assert_eq!(score.streak, 1);
    }

    #[test]
    fn hardcore_full_round_with_hints() {
        let (_dir, db_path) = seed_db();
        let game = Game::new(db_path, 1, false).unwrap();
        let round = game.start_round(FfiMode::Hardcore, FfiLang::Fr).unwrap();
        assert!(round.options.is_empty());
        assert_eq!(round.attempts_left, 3);

        // MCQ submissions are for Easy rounds only.
        assert!(matches!(
            game.submit_guess(round.round_id, "p0".into()),
            Err(GameError::NotSupported { .. })
        ));

        // Shared surname: refused, nothing consumed, no hint unlocked.
        let amb = game
            .submit_text_guess(round.round_id, "Shearer".into())
            .unwrap();
        assert_eq!(amb.verdict, TextVerdict::Ambiguous);
        assert_eq!(amb.attempts_left, 3);
        assert!(amb.new_hint.is_none());
        assert!(game.current_hints(round.round_id).unwrap().is_empty());

        // Wrong guess: one attempt gone, hint 1 (nationality) unlocks.
        let miss = game
            .submit_text_guess(round.round_id, "Zidane".into())
            .unwrap();
        assert_eq!(miss.verdict, TextVerdict::Wrong);
        assert_eq!(miss.attempts_left, 2);
        assert_eq!(
            miss.new_hint,
            Some(Hint::Nationality {
                text: "France".into()
            })
        );

        // Voluntary hint request: hint 2 (position), localized.
        let h2 = game.request_hint(round.round_id).unwrap();
        assert_eq!(
            h2,
            Some(Hint::Position {
                text: "Attaquant".into()
            })
        );

        // Second wrong guess unlocks the last hint: S, 7 letters.
        let miss = game
            .submit_text_guess(round.round_id, "Ronaldo".into())
            .unwrap();
        assert_eq!(miss.verdict, TextVerdict::Wrong);
        assert_eq!(miss.attempts_left, 1);
        assert_eq!(
            miss.new_hint,
            Some(Hint::InitialAndLength {
                initial: "S".into(),
                letters: 7
            })
        );
        assert!(game.request_hint(round.round_id).unwrap().is_none());
        assert_eq!(game.current_hints(round.round_id).unwrap().len(), 3);

        // Exact full name on the last attempt wins the round.
        let hit = game
            .submit_text_guess(round.round_id, "alan shearer".into())
            .unwrap();
        assert_eq!(hit.verdict, TextVerdict::Correct);
        let result = hit.result.expect("round closed");
        assert!(result.correct);
        assert_eq!(result.points_gained, 3);
        assert_eq!(result.correct_player_id, "p0");
    }

    #[test]
    fn hardcore_round_is_lost_after_three_misses() {
        let (_dir, db_path) = seed_db();
        let game = Game::new(db_path, 1, false).unwrap();
        let round = game.start_round(FfiMode::Hardcore, FfiLang::En).unwrap();
        for _ in 0..2 {
            let r = game
                .submit_text_guess(round.round_id, "Zidane".into())
                .unwrap();
            assert_eq!(r.verdict, TextVerdict::Wrong);
        }
        let last = game
            .submit_text_guess(round.round_id, "Zidane".into())
            .unwrap();
        assert_eq!(last.verdict, TextVerdict::RoundLost);
        let result = last.result.expect("round closed");
        assert!(!result.correct);
        assert_eq!(result.correct_player_name, "Alan Shearer");
        // Round is closed: no further guesses.
        assert!(matches!(
            game.submit_text_guess(round.round_id, "x".into()),
            Err(GameError::NoRoundActive)
        ));
        // The lost round advanced the ads gate like any completed round.
        let easy = game.start_round(FfiMode::Easy, FfiLang::En).unwrap();
        game.submit_guess(easy.round_id, "p0".into()).unwrap();
        assert!(game.should_show_ad(FfiPlacement::Interstitial));
    }

    fn answer_one_round(game: &Game) {
        let round = game.start_round(FfiMode::Easy, FfiLang::En).unwrap();
        game.submit_guess(round.round_id, "p0".into()).unwrap();
    }

    #[test]
    fn ads_gate_follows_rounds_and_purchase() {
        let (_dir, db_path) = seed_db();
        let game = Game::new(db_path, 7, false).unwrap();

        // Free user, fresh session: static slots yes, interstitial in warmup.
        assert!(game.should_show_ad(FfiPlacement::Banner));
        assert!(game.should_show_ad(FfiPlacement::SponsorBoard));
        assert!(!game.should_show_ad(FfiPlacement::Interstitial));

        // Answering rounds is what advances the ads gate; no separate call.
        answer_one_round(&game);
        answer_one_round(&game);
        assert!(game.should_show_ad(FfiPlacement::Interstitial));
        game.record_interstitial_shown();
        assert!(!game.should_show_ad(FfiPlacement::Interstitial));

        // Consent flips personalisation, never slot visibility.
        assert!(!game.ad_personalization_allowed());
        game.set_ad_consent(FfiConsent::Personalized);
        assert!(game.ad_personalization_allowed());
        assert!(game.should_show_ad(FfiPlacement::Banner));

        // Remove-ads purchase mid-session: everything goes dark.
        game.set_ads_removed(true);
        assert!(game.ads_removed());
        assert!(!game.should_show_ad(FfiPlacement::Banner));
        assert!(!game.should_show_ad(FfiPlacement::SponsorBoard));
        assert!(!game.should_show_ad(FfiPlacement::Rectangle));
        assert!(!game.should_show_ad(FfiPlacement::Interstitial));
    }

    #[test]
    fn purchased_at_launch_never_shows_ads() {
        let (_dir, db_path) = seed_db();
        let game = Game::new(db_path, 7, true).unwrap();
        assert!(game.ads_removed());
        assert!(!game.should_show_ad(FfiPlacement::Banner));
    }
}
