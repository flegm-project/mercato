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

pub use ads::{AdsConfig, AdsGate, Consent, Placement};
pub use matching::{MatchResult, Matcher, Route};
pub use model::{Club, Kind, Lang, Nationality, Player, Position, Transfer};
pub use rng::Mulberry32;
pub use round::Mode;

/// The full dataset in memory. `mercato-data` builds this from the CSVs.
#[derive(Debug, Clone, Default)]
pub struct Corpus {
    pub players: Vec<Player>,
    pub clubs: Vec<Club>,
    pub transfers: Vec<Transfer>,
    /// Display-name table keyed by the exact `players.csv` nationality
    /// string. May be empty in tests; lookups fall back to the raw key.
    pub nationalities: Vec<Nationality>,
}

impl Corpus {
    pub fn new(players: Vec<Player>, clubs: Vec<Club>, transfers: Vec<Transfer>) -> Self {
        Self {
            players,
            clubs,
            transfers,
            nationalities: Vec::new(),
        }
    }

    /// User-facing name for a nationality key, falling back to the key
    /// itself when no translation row exists.
    pub fn nationality_label<'a>(&'a self, key: &'a str, lang: Lang) -> &'a str {
        self.nationalities
            .iter()
            .find(|n| n.key == key)
            .map(|n| n.name(lang))
            .unwrap_or(key)
    }

    /// Build a [`Matcher`] over this corpus's players.
    pub fn matcher(&self) -> Matcher<'_> {
        Matcher::new(&self.players)
    }
}
