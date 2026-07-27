//! CSV parsing: `data/*.csv` -> [`Corpus`], aliases joined onto players.

use std::collections::HashMap;
use std::path::Path;

use mercato_core::{Club, Corpus, Kind, Nationality, Player, Position, Transfer};
use serde::Deserialize;

use crate::{validate, DataError};

// Row shapes mirror the CSV headers exactly. Clubs carry per-language names
// (Phase 4); player names are still identical across languages, so the
// per-language fields of the core model are filled from `name`.

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
    name: String,
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

fn read_rows<T: serde::de::DeserializeOwned>(dir: &Path, file: &str) -> Result<Vec<T>, DataError> {
    let path = dir.join(file);
    let mut reader = csv::Reader::from_path(&path).map_err(|source| DataError::Csv {
        file: file.to_string(),
        source,
    })?;
    reader
        .deserialize()
        .collect::<Result<Vec<T>, _>>()
        .map_err(|source| DataError::Csv {
            file: file.to_string(),
            source,
        })
}

/// Parse the five CSVs under `dir`, join aliases onto players, and validate
/// referential integrity. CSV order is preserved (the engine relies on stable
/// ordering for deterministic rounds).
pub fn load_corpus(dir: &Path) -> Result<Corpus, DataError> {
    let clubs: Vec<ClubRow> = read_rows(dir, "clubs.csv")?;
    let players: Vec<PlayerRow> = read_rows(dir, "players.csv")?;
    let aliases: Vec<AliasRow> = read_rows(dir, "player_aliases.csv")?;
    let transfers: Vec<TransferRow> = read_rows(dir, "transfers.csv")?;
    let nationalities: Vec<Nationality> = read_rows(dir, "nationalities.csv")?;

    // Join aliases per player, preserving file order.
    let mut alias_map: HashMap<&str, Vec<String>> = HashMap::new();
    let mut orphan_aliases: Vec<String> = Vec::new();
    let player_ids: std::collections::HashSet<&str> =
        players.iter().map(|p| p.id.as_str()).collect();
    for a in &aliases {
        if player_ids.contains(a.player_id.as_str()) {
            alias_map
                .entry(a.player_id.as_str())
                .or_default()
                .push(a.alias.clone());
        } else {
            orphan_aliases.push(format!(
                "player_aliases.csv: alias '{}' references unknown player '{}'",
                a.alias, a.player_id
            ));
        }
    }
    if !orphan_aliases.is_empty() {
        return Err(DataError::Integrity(orphan_aliases));
    }

    let mut corpus = Corpus::new(
        players
            .into_iter()
            .map(|r| {
                let aliases = alias_map.remove(r.id.as_str()).unwrap_or_default();
                Player {
                    id: r.id,
                    name_en: r.name.clone(),
                    name_fr: r.name.clone(),
                    name_es: r.name,
                    aliases,
                    position: r.position,
                    nationality: r.nationality,
                    birth_year: r.birth_year,
                    notoriety: r.notoriety,
                }
            })
            .collect(),
        clubs
            .into_iter()
            .map(|r| Club {
                id: r.id,
                name_en: r.name_en,
                name_fr: r.name_fr,
                name_es: r.name_es,
                notoriety: r.notoriety,
            })
            .collect(),
        transfers
            .into_iter()
            .map(|r| Transfer {
                id: r.id,
                player_id: r.player_id,
                from_club: r.from_club,
                to_club: r.to_club,
                year: r.year,
                kind: r.kind,
                tier: r.tier,
            })
            .collect(),
    );
    corpus.nationalities = nationalities;

    let errors = validate(&corpus);
    if errors.is_empty() {
        Ok(corpus)
    } else {
        Err(DataError::Integrity(errors))
    }
}
