//! Data loading for Mercato: CSV (source of truth) -> `mercato_core` types.
//!
//! Phase 2. The CSVs in `data/` are English-only today; FR/ES club and
//! nationality names are extracted in Phase 4 (see docs/DATA.md), so the
//! per-language name fields are all filled with the English name for now.

mod csv_load;
mod sqlite;
mod validate;

pub use csv_load::load_corpus;
pub use sqlite::generate_db;
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
