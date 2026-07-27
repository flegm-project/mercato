//! Data loading for Mercato: CSV (source of truth) -> `mercato_core` types.
//!
//! Phases 2 and 4. Clubs carry per-language names (EN/FR/ES, extracted from
//! Wikidata labels, see scripts/fetch-club-names.mjs) and nationalities have
//! a display-name table (data/nationalities.csv). Player names are still a
//! single value copied into the per-language fields (see docs/DATA.md).

mod csv_load;
mod sqlite;
mod validate;

pub use csv_load::load_corpus;
pub use sqlite::{generate_db, load_from_db};
pub use validate::validate;

pub use mercato_core::{Club, Corpus, Kind, Player, Transfer};

use std::fmt;

/// Everything that can go wrong while loading or bundling the dataset.
#[derive(Debug)]
pub enum DataError {
    /// I/O failure (missing file, unreadable directory...).
    Io(std::io::Error),
    /// Malformed CSV (wrong header, bad enum value, bad number...).
    Csv { file: String, source: csv::Error },
    /// The CSVs parsed but the dataset is inconsistent. Every violation is
    /// listed so CI can print them all at once.
    Integrity(Vec<String>),
    /// SQLite bundling failure.
    Sqlite(rusqlite::Error),
}

impl fmt::Display for DataError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            DataError::Io(e) => write!(f, "i/o error: {e}"),
            DataError::Csv { file, source } => write!(f, "{file}: {source}"),
            DataError::Integrity(errs) => {
                writeln!(f, "{} integrity violation(s):", errs.len())?;
                for e in errs {
                    writeln!(f, "  - {e}")?;
                }
                Ok(())
            }
            DataError::Sqlite(e) => write!(f, "sqlite error: {e}"),
        }
    }
}

impl std::error::Error for DataError {}

impl From<std::io::Error> for DataError {
    fn from(e: std::io::Error) -> Self {
        DataError::Io(e)
    }
}

impl From<rusqlite::Error> for DataError {
    fn from(e: rusqlite::Error) -> Self {
        DataError::Sqlite(e)
    }
}
