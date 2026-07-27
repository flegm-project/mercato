//! UniFFI surface for Mercato: the `Game` facade native apps call.
//!
//! Scope for v1 (Phase 3, "first playable"): Easy mode only (4-option
//! multiple choice). Hardcore's free-text matching engine already exists in
//! `mercato_core::Matcher` but is not wired here yet - it needs hint-ladder
//! and attempt-tracking design that belongs with the Phase 5 screens, not
//! this minimal facade. Requesting Hardcore returns `GameError::NotSupported`
//! rather than a half-working guess path.

use std::path::Path;
use std::sync::Mutex;

use mercato_core::decoy::distractors_for;
use mercato_core::round::{pick_index, pool};
use mercato_core::scoring::Score;
use mercato_core::{Kind, Lang, Mode, Mulberry32};

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
    /// Increments on every `start_round`; `submit_guess` is rejected if it
    /// does not match the round currently in play (guards stale UI state).
    pub round_id: u64,
    pub transfer_id: i64,
    pub kind: String,
    pub year: i32,
    pub from_club: String,
    pub to_club: String,
    /// 4 options in randomized (seeded) order, Easy mode only.
    pub options: Vec<OptionView>,
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
}

struct GameState {
    rng: Mulberry32,
    score: Score,
    next_round_id: u64,
    current: Option<CurrentRound>,
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
    #[uniffi::constructor]
    pub fn new(db_path: String, seed: u64) -> Result<Self, GameError> {
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
            }),
        })
    }

    /// Pick a transfer for `mode` and build its round view. Easy mode only
    /// for now (see module docs); Hardcore returns `NotSupported`.
    pub fn start_round(&self, mode: FfiMode, lang: FfiLang) -> Result<RoundView, GameError> {
        if mode == FfiMode::Hardcore {
            return Err(GameError::NotSupported {
                what: "hardcore mode (free-text matching)".into(),
            });
        }
        let lang: Lang = lang.into();
        let mut st = self.state.lock().expect("game state mutex poisoned");

        let candidates = pool(&self.corpus.transfers, mode.into());
        let idx_in_pool = pick_index(candidates.len(), &mut st.rng).ok_or(GameError::EmptyPool)?;
        let transfer = candidates[idx_in_pool];
        let target_player_idx = self
            .corpus
            .players
            .iter()
            .position(|p| p.id == transfer.player_id)
            .expect("transfer.player_id must reference a real player");

        let decoy_idxs = distractors_for(&self.corpus.players, target_player_idx, &mut st.rng);
        let mut option_idxs: Vec<usize> = decoy_idxs;
        option_idxs.push(target_player_idx);
        shuffle(&mut option_idxs, &mut st.rng);

        let options = option_idxs
            .into_iter()
            .map(|idx| {
                let p = &self.corpus.players[idx];
                OptionView {
                    player_id: p.id.clone(),
                    name: p.name(lang).to_string(),
                }
            })
            .collect();

        let round_id = st.next_round_id;
        st.next_round_id += 1;
        st.current = Some(CurrentRound {
            round_id,
            target_player_idx,
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
        })
    }

    /// Submit an MCQ answer: `player_id` must be one of the last
    /// `start_round`'s options. Scores against the round currently open;
    /// `round_id` (from `RoundView`) guards against a stale/duplicate submit.
    pub fn submit_guess(
        &self,
        round_id: u64,
        player_id: String,
    ) -> Result<AnswerResult, GameError> {
        let mut st = self.state.lock().expect("game state mutex poisoned");
        let current = st.current.as_ref().ok_or(GameError::NoRoundActive)?;
        if current.round_id != round_id {
            return Err(GameError::StaleRound);
        }
        let target_player_idx = current.target_player_idx;
        let target = &self.corpus.players[target_player_idx];

        let guessed_idx = self
            .corpus
            .players
            .iter()
            .position(|p| p.id == player_id)
            .ok_or_else(|| GameError::UnknownOption {
                player_id: player_id.clone(),
            })?;

        let correct = guessed_idx == target_player_idx;
        let points_gained = st.score.answer(correct);
        st.current = None;

        Ok(AnswerResult {
            correct,
            points_gained,
            total_points: st.score.points,
            streak: st.score.streak,
            best_streak: st.score.best_streak,
            correct_player_id: target.id.clone(),
            correct_player_name: target.name_en.clone(),
        })
    }

    pub fn score(&self) -> ScoreView {
        let st = self.state.lock().expect("game state mutex poisoned");
        ScoreView {
            points: st.score.points,
            streak: st.score.streak,
            best_streak: st.score.best_streak,
        }
    }
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

        let players: Vec<Player> = (0..6)
            .map(|i| player(&format!("p{i}"), &format!("Player {i}")))
            .collect();
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
        let corpus = Corpus::new(players, clubs, transfers);
        mercato_data::generate_db(&corpus, &db_path).expect("generate_db");

        (dir, db_path.to_string_lossy().into_owned())
    }

    #[test]
    fn plays_a_full_round() {
        let (_dir, db_path) = seed_db();
        let game = Game::new(db_path, 42).expect("game loads");

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
    fn hardcore_is_not_supported_yet() {
        let (_dir, db_path) = seed_db();
        let game = Game::new(db_path, 1).unwrap();
        assert!(matches!(
            game.start_round(FfiMode::Hardcore, FfiLang::En),
            Err(GameError::NotSupported { .. })
        ));
    }
}
