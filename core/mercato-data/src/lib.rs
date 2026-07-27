//! Data loading for Mercato: CSV (source of truth) -> `mercato_core` types.
//!
//! Phase 2. The CSVs in `data/` are English-only today; FR/ES club and
//! nationality names are to be extracted from the prototype's inline DATA
//! (see docs/DATA.md). Until the loader lands, this crate only re-exports the
//! core types so downstream crates can depend on a stable surface.

pub use mercato_core::{Club, Corpus, Kind, Player, Transfer};

/// Placeholder for the CSV loader (Phase 2).
///
/// Planned signature:
/// ```ignore
/// pub fn load_corpus(dir: &std::path::Path) -> std::io::Result<Corpus>;
/// ```
/// It will parse `clubs.csv`, `players.csv`, `player_aliases.csv`, and
/// `transfers.csv`, join aliases onto players, and validate referential
/// integrity (every transfer references existing player/club ids).
pub fn load_corpus_todo() -> Corpus {
    Corpus::default()
}
