//! Game session: drives a round of questions over a corpus.
//!
//! Pure and deterministic given a seed. The FFI layer is a thin shell over
//! this; all rules live here so both platforms behave identically.
//! See docs/GAME_DESIGN.md.

use std::sync::Arc;

use crate::decoy::distractors_for;
use crate::distance::BASE;
use crate::matching::Route;
use crate::model::{Kind, Lang, Position, Transfer};
use crate::rng::Mulberry32;
use crate::round::{pick_index, pool_indices, Mode};
use crate::scoring::Score;
use crate::Corpus;

/// Questions per round (10 progress pips in the UI spec).
pub const QUESTIONS_PER_ROUND: usize = 10;
/// Attempts per question in Hardcore.
pub const HARDCORE_ATTEMPTS: u8 = 3;

/// A question as the UI needs it, with names already resolved to the language.
#[derive(Debug, Clone, PartialEq)]
pub struct Question {
    pub index: usize,
    pub total: usize,
    pub kind: Kind,
    pub year: i32,
    pub from_club: String,
    pub to_club: String,
    /// Easy only: the four shuffled options (one is correct).
    pub options: Vec<String>,
    /// Hardcore only: attempts still available.
    pub attempts_left: u8,
}

/// What a hint reveals, in unlock order.
#[derive(Debug, Clone, PartialEq)]
pub enum Hint {
    Nationality(String),
    Position(Position),
    /// First letter of the surname and its length.
    SurnameShape {
        initial: char,
        letters: usize,
    },
}

/// Why an answer was rejected, when it was.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Rejection {
    /// Surname shared by several players: ask for the first name. Per the spec
    /// this does **not** consume an attempt.
    AmbiguousSurname,
    /// Plain miss.
    Wrong,
}

/// Result of submitting an answer.
#[derive(Debug, Clone, PartialEq)]
pub struct Answer {
    pub correct: bool,
    pub rejection: Option<Rejection>,
    pub points_gained: i64,
    pub attempts_left: u8,
    /// Revealed once the question is over (correct, or attempts exhausted).
    pub revealed_name: Option<String>,
    /// True when the question is finished and the UI should advance.
    pub finished: bool,
}

/// One question's mutable state.
#[derive(Debug, Clone)]
struct Current {
    transfer_index: usize,
    player_index: usize,
    /// Easy: option player indices in display order.
    option_indices: Vec<usize>,
    attempts_left: u8,
    hints_used: usize,
    finished: bool,
}

pub struct Session {
    corpus: Arc<Corpus>,
    lang: Lang,
    mode: Mode,
    rng: Mulberry32,
    pool: Vec<usize>,
    asked: usize,
    current: Option<Current>,
    score: Score,
    /// Transfers the player failed, for the recap screen.
    missed: Vec<usize>,
}

impl Session {
    /// Start a session. `seed` makes the whole session reproducible.
    pub fn new(corpus: Arc<Corpus>, lang: Lang, mode: Mode, seed: u32) -> Self {
        Self {
            pool: pool_indices(&corpus.transfers, mode),
            corpus,
            lang,
            mode,
            rng: Mulberry32::new(seed),
            asked: 0,
            current: None,
            score: Score::new(),
            missed: Vec::new(),
        }
    }

    pub fn score(&self) -> &Score {
        &self.score
    }

    /// The language names are rendered in.
    pub fn lang(&self) -> Lang {
        self.lang
    }

    /// The mode this session is playing.
    pub fn mode(&self) -> Mode {
        self.mode
    }

    pub fn missed_transfers(&self) -> Vec<&Transfer> {
        self.missed
            .iter()
            .map(|&i| &self.corpus.transfers[i])
            .collect()
    }

    pub fn is_over(&self) -> bool {
        self.asked >= QUESTIONS_PER_ROUND && self.current.as_ref().is_none_or(|c| c.finished)
    }

    /// Advance to the next question, or `None` when the round is over.
    pub fn next_question(&mut self) -> Option<Question> {
        if self.asked >= QUESTIONS_PER_ROUND || self.pool.is_empty() {
            self.current = None;
            return None;
        }

        let pick = pick_index(self.pool.len(), &mut self.rng)?;
        let transfer_index = self.pool[pick];
        let transfer = &self.corpus.transfers[transfer_index];
        let player_index = self.corpus.player_index(&transfer.player_id)?;

        let option_indices = match self.mode {
            Mode::Easy => {
                let mut opts = distractors_for(&self.corpus.players, player_index, &mut self.rng);
                opts.push(player_index);
                shuffle(&mut opts, &mut self.rng);
                opts
            }
            Mode::Hardcore => Vec::new(),
        };

        self.current = Some(Current {
            transfer_index,
            player_index,
            option_indices,
            attempts_left: match self.mode {
                Mode::Easy => 1,
                Mode::Hardcore => HARDCORE_ATTEMPTS,
            },
            hints_used: 0,
            finished: false,
        });
        self.asked += 1;

        Some(self.view())
    }

    /// The current question as the UI sees it.
    fn view(&self) -> Question {
        let c = self.current.as_ref().expect("current question");
        let t = &self.corpus.transfers[c.transfer_index];
        Question {
            index: self.asked,
            total: QUESTIONS_PER_ROUND,
            kind: t.kind,
            year: t.year,
            from_club: self.club_name(&t.from_club),
            to_club: self.club_name(&t.to_club),
            options: c
                .option_indices
                .iter()
                .map(|&i| self.corpus.players[i].name(self.lang).to_string())
                .collect(),
            attempts_left: c.attempts_left,
        }
    }

    pub fn current_question(&self) -> Option<Question> {
        self.current.as_ref().map(|_| self.view())
    }

    fn club_name(&self, id: &str) -> String {
        self.corpus
            .club(id)
            .map(|c| c.name(self.lang).to_string())
            .unwrap_or_else(|| id.to_string())
    }

    fn answer_name(&self) -> String {
        let c = self.current.as_ref().expect("current question");
        self.corpus.players[c.player_index]
            .name(self.lang)
            .to_string()
    }

    /// Easy mode: pick option `index`.
    pub fn submit_choice(&mut self, index: usize) -> Option<Answer> {
        let c = self.current.as_ref()?;
        if c.finished {
            return None;
        }
        let correct = c.option_indices.get(index) == Some(&c.player_index);
        Some(self.settle(correct, None))
    }

    /// Hardcore mode: free-text guess, run through the matching engine.
    pub fn submit_guess(&mut self, guess: &str) -> Option<Answer> {
        let c = self.current.as_ref()?;
        if c.finished {
            return None;
        }
        let r = self.corpus.match_answer(guess, c.player_index, BASE);

        // An ambiguous surname asks for the first name and costs nothing.
        if r.ambiguous {
            let attempts_left = self.current.as_ref()?.attempts_left;
            return Some(Answer {
                correct: false,
                rejection: Some(Rejection::AmbiguousSurname),
                points_gained: 0,
                attempts_left,
                revealed_name: None,
                finished: false,
            });
        }

        let correct = r.ok && r.route != Route::None;
        Some(self.settle(correct, Some(Rejection::Wrong)))
    }

    /// Apply an answer to the session state.
    fn settle(&mut self, correct: bool, rejection: Option<Rejection>) -> Answer {
        let revealed = self.answer_name();
        let transfer_index = self.current.as_ref().expect("current").transfer_index;
        let c = self.current.as_mut().expect("current");

        if correct {
            c.finished = true;
        } else {
            c.attempts_left = c.attempts_left.saturating_sub(1);
            if c.attempts_left == 0 {
                c.finished = true;
            }
        }
        let finished = c.finished;
        let attempts_left = c.attempts_left;

        let mut points_gained = 0;
        if finished {
            points_gained = self.score.answer(correct);
            if !correct {
                self.missed.push(transfer_index);
            }
        }

        Answer {
            correct,
            rejection: if correct { None } else { rejection },
            points_gained,
            attempts_left,
            revealed_name: finished.then_some(revealed),
            finished,
        }
    }

    /// Unlock the next hint (Hardcore). Hints are free gameplay: there is no
    /// shop and no currency (see docs/MONETIZATION.md).
    pub fn next_hint(&mut self) -> Option<Hint> {
        let c = self.current.as_ref()?;
        if c.finished {
            return None;
        }
        let player = &self.corpus.players[c.player_index];
        let hint = match c.hints_used {
            0 => {
                let nat = player.nationality.as_ref()?;
                let name = self
                    .corpus
                    .nationality(nat)
                    .map(|n| n.name(self.lang).to_string())
                    .unwrap_or_else(|| nat.clone());
                Hint::Nationality(name)
            }
            1 => Hint::Position(player.position?),
            2 => {
                let surname = crate::matching::surname_variants(player.name(self.lang))
                    .pop()
                    .unwrap_or_default();
                let initial = surname.chars().next()?.to_ascii_uppercase();
                Hint::SurnameShape {
                    initial,
                    letters: surname.chars().filter(|c| !c.is_whitespace()).count(),
                }
            }
            _ => return None,
        };
        self.current.as_mut()?.hints_used += 1;
        Some(hint)
    }
}

/// Fisher-Yates using the seeded RNG, matching the reference `shuffle`.
fn shuffle(v: &mut [usize], rng: &mut Mulberry32) {
    if v.is_empty() {
        return;
    }
    for i in (1..v.len()).rev() {
        let j = (rng.next_f64() * (i + 1) as f64) as usize;
        v.swap(i, j);
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::model::{Club, Nationality, Player, Transfer};

    fn player(id: &str, name: &str, not: i64) -> Player {
        Player {
            id: id.into(),
            name_en: name.into(),
            name_fr: name.into(),
            name_es: name.into(),
            aliases: vec![],
            position: Some(Position::Fw),
            nationality: Some("Q1".into()),
            birth_year: Some(1990),
            notoriety: not,
        }
    }
    fn club(id: &str, name: &str) -> Club {
        Club {
            id: id.into(),
            name_en: name.into(),
            name_fr: name.into(),
            name_es: name.into(),
            notoriety: 50,
        }
    }

    fn corpus() -> Arc<Corpus> {
        let players = vec![
            player("P1", "Alpha One", 90),
            player("P2", "Beta Two", 80),
            player("P3", "Gamma Three", 70),
            player("P4", "Delta Four", 60),
            player("P5", "Epsilon Five", 50),
        ];
        let clubs = vec![club("C1", "Club One"), club("C2", "Club Two")];
        let transfers = (1..=12)
            .map(|i| Transfer {
                id: i,
                player_id: format!("P{}", (i % 5) + 1),
                from_club: "C1".into(),
                to_club: "C2".into(),
                year: 2000 + i as i32,
                kind: Kind::Transfer,
                tier: 1,
            })
            .collect();
        let nats = vec![Nationality {
            id: "Q1".into(),
            name_en: "Testland".into(),
            name_fr: "Testland".into(),
            name_es: "Testlandia".into(),
        }];
        Arc::new(Corpus::new(players, clubs, transfers, nats))
    }

    #[test]
    fn easy_round_has_four_options_and_ends_after_ten() {
        let c = corpus();
        let mut s = Session::new(c.clone(), Lang::En, Mode::Easy, 7);
        let mut count = 0;
        while let Some(q) = s.next_question() {
            assert_eq!(q.options.len(), 4);
            assert_eq!(q.total, QUESTIONS_PER_ROUND);
            // Answer the first option every time; correctness varies.
            s.submit_choice(0).expect("answer accepted");
            count += 1;
        }
        assert_eq!(count, QUESTIONS_PER_ROUND);
        assert!(s.is_over());
    }

    #[test]
    fn correct_choice_scores_three() {
        let c = corpus();
        let mut s = Session::new(c.clone(), Lang::En, Mode::Easy, 3);
        let q = s.next_question().expect("question");
        let answer_name = s.answer_name();
        let correct_idx = q
            .options
            .iter()
            .position(|o| *o == answer_name)
            .expect("correct option present");
        let a = s.submit_choice(correct_idx).expect("answered");
        assert!(a.correct);
        assert_eq!(a.points_gained, 3);
        assert!(a.finished);
        assert_eq!(s.score().points, 3);
    }

    #[test]
    fn hardcore_spends_three_attempts_then_reveals() {
        let c = corpus();
        let mut s = Session::new(c.clone(), Lang::En, Mode::Hardcore, 11);
        let q = s.next_question().expect("question");
        assert_eq!(q.attempts_left, HARDCORE_ATTEMPTS);
        assert!(q.options.is_empty());

        let a1 = s.submit_guess("zzzzzzzz").expect("answered");
        assert!(!a1.correct && !a1.finished && a1.attempts_left == 2);
        let a2 = s.submit_guess("zzzzzzzz").expect("answered");
        assert_eq!(a2.attempts_left, 1);
        let a3 = s.submit_guess("zzzzzzzz").expect("answered");
        assert!(a3.finished);
        assert!(a3.revealed_name.is_some());
        assert_eq!(s.score().points, 0);
        assert_eq!(s.missed_transfers().len(), 1);
    }

    #[test]
    fn hints_unlock_in_order() {
        let c = corpus();
        let mut s = Session::new(c.clone(), Lang::En, Mode::Hardcore, 5);
        s.next_question().expect("question");
        assert!(matches!(s.next_hint(), Some(Hint::Nationality(_))));
        assert!(matches!(s.next_hint(), Some(Hint::Position(_))));
        assert!(matches!(s.next_hint(), Some(Hint::SurnameShape { .. })));
        assert!(s.next_hint().is_none());
    }

    #[test]
    fn same_seed_replays_identically() {
        let c = corpus();
        let run = |seed| {
            let mut s = Session::new(c.clone(), Lang::En, Mode::Easy, seed);
            let mut seen = Vec::new();
            while let Some(q) = s.next_question() {
                seen.push((q.year, q.options.clone()));
                s.submit_choice(0);
            }
            seen
        };
        assert_eq!(run(42), run(42));
        assert_ne!(run(42), run(43));
    }
}
