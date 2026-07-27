//! Mercato game core: matching engine, decoys, RNG, rounds, scoring.
//!
//! Pure and deterministic (given a seed), no I/O and no platform dependencies.
//! The matching + decoy logic is ported verbatim from the web prototype
//! (`core/reference/engine.reference.js`) and locked by parity tests.

pub mod decoy;
pub mod distance;
pub mod matching;
pub mod model;
pub mod normalize;
pub mod rng;
pub mod round;
pub mod scoring;

pub use matching::{MatchResult, Matcher, Route};
pub use model::{Club, Kind, Lang, Player, Position, Transfer};
pub use rng::Mulberry32;
pub use round::Mode;

/// The full dataset in memory. `mercato-data` builds this from the CSVs.
#[derive(Debug, Clone, Default)]
pub struct Corpus {
    pub players: Vec<Player>,
    pub clubs: Vec<Club>,
    pub transfers: Vec<Transfer>,
}

impl Corpus {
    pub fn new(players: Vec<Player>, clubs: Vec<Club>, transfers: Vec<Transfer>) -> Self {
        Self {
            players,
            clubs,
            transfers,
        }
    }

    /// Build a [`Matcher`] over this corpus's players.
    pub fn matcher(&self) -> Matcher<'_> {
        Matcher::new(&self.players)
    }
}
