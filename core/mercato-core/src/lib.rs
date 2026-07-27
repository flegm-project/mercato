//! Mercato game core: matching engine, decoys, RNG, rounds, scoring.
//!
//! Pure and deterministic (given a seed), no I/O and no platform dependencies.
//! The matching + decoy logic is ported verbatim from the web prototype
//! (`core/reference/engine.reference.js`) and locked by parity tests.

pub mod ads;
pub mod decoy;
pub mod distance;
pub mod matching;
pub mod model;
pub mod normalize;
pub mod rng;
pub mod round;
pub mod scoring;
pub mod session;

pub use ads::{AdsConfig, AdsGate, Consent, Placement};
pub use matching::{MatchResult, Matcher, Route};
pub use model::{Club, Kind, Lang, Nationality, Player, Position, Transfer};
pub use rng::Mulberry32;
pub use round::Mode;
pub use session::{Answer, Hint, Question, Rejection, Session};

use std::collections::HashMap;

/// The full dataset in memory, plus the matching indexes built over it.
/// `mercato-data` builds this from the CSVs.
#[derive(Debug, Clone, Default)]
pub struct Corpus {
    pub players: Vec<Player>,
    pub clubs: Vec<Club>,
    pub transfers: Vec<Transfer>,
    pub nationalities: Vec<Nationality>,
    matcher: Matcher,
    club_by_id: HashMap<String, usize>,
    player_by_id: HashMap<String, usize>,
    nationality_by_id: HashMap<String, usize>,
}

impl Corpus {
    pub fn new(
        players: Vec<Player>,
        clubs: Vec<Club>,
        transfers: Vec<Transfer>,
        nationalities: Vec<Nationality>,
    ) -> Self {
        let club_by_id = clubs
            .iter()
            .enumerate()
            .map(|(i, c)| (c.id.clone(), i))
            .collect();
        let player_by_id = players
            .iter()
            .enumerate()
            .map(|(i, p)| (p.id.clone(), i))
            .collect();
        let nationality_by_id = nationalities
            .iter()
            .enumerate()
            .map(|(i, n)| (n.id.clone(), i))
            .collect();
        let matcher = Matcher::new(&players);
        Self {
            players,
            clubs,
            transfers,
            nationalities,
            matcher,
            club_by_id,
            player_by_id,
            nationality_by_id,
        }
    }

    /// The matching engine indexed over this corpus's players.
    pub fn matcher(&self) -> &Matcher {
        &self.matcher
    }

    /// Match a guess against a player, using this corpus's indexes.
    pub fn match_answer(&self, guess: &str, pid: usize, base: usize) -> MatchResult {
        self.matcher.match_answer(&self.players, guess, pid, base)
    }

    pub fn club(&self, id: &str) -> Option<&Club> {
        self.club_by_id.get(id).map(|&i| &self.clubs[i])
    }

    pub fn player(&self, id: &str) -> Option<&Player> {
        self.player_by_id.get(id).map(|&i| &self.players[i])
    }

    pub fn player_index(&self, id: &str) -> Option<usize> {
        self.player_by_id.get(id).copied()
    }

    pub fn nationality(&self, id: &str) -> Option<&Nationality> {
        self.nationality_by_id
            .get(id)
            .map(|&i| &self.nationalities[i])
    }
}
