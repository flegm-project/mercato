//! Data loading for Mercato: CSV (source of truth) -> [`Corpus`].
//!
//! The CSVs in `data/` are the committed source of truth (see docs/DATA.md).
//! Loading validates referential integrity, so a malformed dataset fails fast
//! at startup rather than producing broken rounds.

use std::collections::HashSet;
use std::fmt;
use std::path::Path;

use serde::Deserialize;

pub use mercato_core::{Club, Corpus, Kind, Nationality, Player, Position, Transfer};

/// Why loading failed.
#[derive(Debug)]
pub enum LoadError {
    /// A file could not be read or parsed.
    Csv { file: String, source: csv::Error },
    /// A row references an id that does not exist.
    DanglingRef {
        file: String,
        row_id: String,
        field: &'static str,
        missing_id: String,
    },
    /// The same id appears twice.
    DuplicateId { file: String, id: String },
}

impl fmt::Display for LoadError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            LoadError::Csv { file, source } => write!(f, "{file}: {source}"),
            LoadError::DanglingRef {
                file,
                row_id,
                field,
                missing_id,
            } => write!(
                f,
                "{file}: row {row_id} references unknown {field} '{missing_id}'"
            ),
            LoadError::DuplicateId { file, id } => write!(f, "{file}: duplicate id '{id}'"),
        }
    }
}

impl std::error::Error for LoadError {
    fn source(&self) -> Option<&(dyn std::error::Error + 'static)> {
        match self {
            LoadError::Csv { source, .. } => Some(source),
            _ => None,
        }
    }
}

// --- CSV row shapes ---------------------------------------------------------
// These mirror the CSV headers exactly; the domain types live in mercato-core.

#[derive(Deserialize)]
struct ClubRow {
    id: String,
    name_en: String,
    name_fr: String,
    name_es: String,
    notoriety: i64,
}

#[derive(Deserialize)]
struct PlayerRow {
    id: String,
    name_en: String,
    name_fr: String,
    name_es: String,
    position: Option<Position>,
    nationality: Option<String>,
    birth_year: Option<i32>,
    notoriety: i64,
}

#[derive(Deserialize)]
struct AliasRow {
    player_id: String,
    alias: String,
}

#[derive(Deserialize)]
struct TransferRow {
    id: i64,
    player_id: String,
    from_club: String,
    to_club: String,
    year: i32,
    kind: Kind,
    tier: u8,
}

#[derive(Deserialize)]
struct NationalityRow {
    id: String,
    name_en: String,
    name_fr: String,
    name_es: String,
}

fn read_rows<T: for<'de> Deserialize<'de>>(dir: &Path, file: &str) -> Result<Vec<T>, LoadError> {
    let path = dir.join(file);
    let mut rdr = csv::Reader::from_path(&path).map_err(|source| LoadError::Csv {
        file: file.to_string(),
        source,
    })?;
    rdr.deserialize()
        .collect::<Result<Vec<T>, _>>()
        .map_err(|source| LoadError::Csv {
            file: file.to_string(),
            source,
        })
}

/// Load the full dataset from a directory of CSVs (typically `data/`).
///
/// Expects `clubs.csv`, `players.csv`, `player_aliases.csv`, `transfers.csv`
/// and `nationalities.csv`. Aliases are joined onto their player, preserving
/// file order so the matching engine's candidate order stays deterministic.
pub fn load_corpus(dir: &Path) -> Result<Corpus, LoadError> {
    let club_rows: Vec<ClubRow> = read_rows(dir, "clubs.csv")?;
    let player_rows: Vec<PlayerRow> = read_rows(dir, "players.csv")?;
    let alias_rows: Vec<AliasRow> = read_rows(dir, "player_aliases.csv")?;
    let transfer_rows: Vec<TransferRow> = read_rows(dir, "transfers.csv")?;
    let nat_rows: Vec<NationalityRow> = read_rows(dir, "nationalities.csv")?;

    let nationalities: Vec<Nationality> = nat_rows
        .into_iter()
        .map(|n| Nationality {
            id: n.id,
            name_en: n.name_en,
            name_fr: n.name_fr,
            name_es: n.name_es,
        })
        .collect();
    check_unique(nationalities.iter().map(|n| &n.id), "nationalities.csv")?;
    let nat_ids: HashSet<&str> = nationalities.iter().map(|n| n.id.as_str()).collect();

    let clubs: Vec<Club> = club_rows
        .into_iter()
        .map(|c| Club {
            id: c.id,
            name_en: c.name_en,
            name_fr: c.name_fr,
            name_es: c.name_es,
            notoriety: c.notoriety,
        })
        .collect();
    check_unique(clubs.iter().map(|c| &c.id), "clubs.csv")?;
    let club_ids: HashSet<&str> = clubs.iter().map(|c| c.id.as_str()).collect();

    let mut players: Vec<Player> = player_rows
        .into_iter()
        .map(|p| Player {
            id: p.id,
            name_en: p.name_en,
            name_fr: p.name_fr,
            name_es: p.name_es,
            aliases: Vec::new(),
            position: p.position,
            nationality: p.nationality.filter(|s| !s.is_empty()),
            birth_year: p.birth_year,
            notoriety: p.notoriety,
        })
        .collect();
    check_unique(players.iter().map(|p| &p.id), "players.csv")?;

    // Index players by id for the alias join and transfer validation.
    let index: std::collections::HashMap<String, usize> = players
        .iter()
        .enumerate()
        .map(|(i, p)| (p.id.clone(), i))
        .collect();

    for p in &players {
        if let Some(nat) = &p.nationality {
            if !nat_ids.contains(nat.as_str()) {
                return Err(LoadError::DanglingRef {
                    file: "players.csv".into(),
                    row_id: p.id.clone(),
                    field: "nationality",
                    missing_id: nat.clone(),
                });
            }
        }
    }

    for a in alias_rows {
        match index.get(&a.player_id) {
            Some(&i) => players[i].aliases.push(a.alias),
            None => {
                return Err(LoadError::DanglingRef {
                    file: "player_aliases.csv".into(),
                    row_id: a.player_id.clone(),
                    field: "player_id",
                    missing_id: a.player_id,
                })
            }
        }
    }

    let mut transfers = Vec::with_capacity(transfer_rows.len());
    for t in transfer_rows {
        for (field, id) in [("from_club", &t.from_club), ("to_club", &t.to_club)] {
            if !club_ids.contains(id.as_str()) {
                return Err(LoadError::DanglingRef {
                    file: "transfers.csv".into(),
                    row_id: t.id.to_string(),
                    field,
                    missing_id: id.clone(),
                });
            }
        }
        if !index.contains_key(&t.player_id) {
            return Err(LoadError::DanglingRef {
                file: "transfers.csv".into(),
                row_id: t.id.to_string(),
                field: "player_id",
                missing_id: t.player_id.clone(),
            });
        }
        transfers.push(Transfer {
            id: t.id,
            player_id: t.player_id,
            from_club: t.from_club,
            to_club: t.to_club,
            year: t.year,
            kind: t.kind,
            tier: t.tier,
        });
    }

    Ok(Corpus::new(players, clubs, transfers, nationalities))
}

fn check_unique<'a>(ids: impl Iterator<Item = &'a String>, file: &str) -> Result<(), LoadError> {
    let mut seen = HashSet::new();
    for id in ids {
        if !seen.insert(id.as_str()) {
            return Err(LoadError::DuplicateId {
                file: file.to_string(),
                id: id.clone(),
            });
        }
    }
    Ok(())
}
