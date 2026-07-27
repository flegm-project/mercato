//! UniFFI surface: the `Game` facade the iOS and Android apps call.
//!
//! This layer is deliberately thin. All rules live in `mercato-core`
//! (see `session.rs`); here we only own the corpus, guard it behind a lock,
//! and convert core types into FFI-friendly records.
//!
//! The facade is coarse-grained on purpose: a handful of methods, so binding
//! calls stay cheap and the generated Swift/Kotlin stays small.

use std::sync::{Arc, Mutex};

use mercato_core::session::{Hint, Rejection, Session, HARDCORE_ATTEMPTS, QUESTIONS_PER_ROUND};
use mercato_core::{Corpus, Kind, Lang, Mode, Position};

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
    Free,
}

impl From<Kind> for MoveKind {
    fn from(k: Kind) -> Self {
        match k {
            Kind::Transfer => MoveKind::Transfer,
            Kind::Loan => MoveKind::Loan,
            Kind::Free => MoveKind::Free,
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
    #[error("could not load the dataset: {message}")]
    DataLoad { message: String },
    #[error("no question in progress")]
    NoQuestion,
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
}

#[uniffi::export]
impl Game {
    /// Load the dataset from a directory of CSVs (the app bundles `data/`).
    #[uniffi::constructor]
    pub fn new(data_dir: String) -> Result<Self, GameError> {
        let corpus = mercato_data::load_corpus(std::path::Path::new(&data_dir)).map_err(|e| {
            GameError::DataLoad {
                message: e.to_string(),
            }
        })?;
        let corpus = Arc::new(corpus);
        let session = Session::new(corpus.clone(), Lang::En, Mode::Easy, 0);
        Ok(Self {
            corpus,
            session: Mutex::new(session),
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
        self.session
            .lock()
            .expect("session lock")
            .submit_choice(index as usize)
            .map(answer_view)
            .ok_or(GameError::NoQuestion)
    }

    /// Hardcore mode: submit a typed guess.
    pub fn submit_guess(&self, text: String) -> Result<AnswerView, GameError> {
        self.session
            .lock()
            .expect("session lock")
            .submit_guess(&text)
            .map(answer_view)
            .ok_or(GameError::NoQuestion)
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
