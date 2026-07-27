//! Bundled-DB generation: [`Corpus`] -> read-only SQLite artifact.
//!
//! Schema follows docs/DATA.md (single English name per entity until Phase 4
//! adds per-language columns). The DB is a build artifact, never committed.

use std::collections::HashMap;
use std::path::Path;

use mercato_core::{Club, Corpus, Kind, Player, Position, Transfer};
use rusqlite::{params, Connection};

use crate::DataError;

const SCHEMA: &str = "
CREATE TABLE club (
    id        TEXT PRIMARY KEY,
    name      TEXT NOT NULL,
    notoriety INTEGER NOT NULL
) STRICT;

CREATE TABLE player (
    id          TEXT PRIMARY KEY,
    name        TEXT NOT NULL,
    position    TEXT CHECK (position IN ('gk', 'def', 'mid', 'fw')),
    nationality TEXT,
    birth_year  INTEGER,
    notoriety   INTEGER NOT NULL
) STRICT;

CREATE TABLE player_alias (
    player_id TEXT NOT NULL REFERENCES player(id),
    alias     TEXT NOT NULL
) STRICT;

CREATE TABLE transfer (
    id        INTEGER PRIMARY KEY,
    player_id TEXT NOT NULL REFERENCES player(id),
    from_club TEXT NOT NULL REFERENCES club(id),
    to_club   TEXT NOT NULL REFERENCES club(id),
    year      INTEGER NOT NULL,
    kind      TEXT NOT NULL CHECK (kind IN ('transfer', 'loan', 'free')),
    tier      INTEGER NOT NULL CHECK (tier IN (1, 2, 3)),
    CHECK (from_club <> to_club)
) STRICT;

CREATE INDEX idx_transfer_tier      ON transfer(tier);
CREATE INDEX idx_transfer_player_id ON transfer(player_id);
CREATE INDEX idx_alias_player_id    ON player_alias(player_id);
";

fn position_str(p: Position) -> &'static str {
    match p {
        Position::Gk => "gk",
        Position::Def => "def",
        Position::Mid => "mid",
        Position::Fw => "fw",
    }
}

fn kind_str(k: Kind) -> &'static str {
    match k {
        Kind::Transfer => "transfer",
        Kind::Loan => "loan",
        Kind::Free => "free",
    }
}

fn str_to_position(s: &str) -> Option<Position> {
    match s {
        "gk" => Some(Position::Gk),
        "def" => Some(Position::Def),
        "mid" => Some(Position::Mid),
        "fw" => Some(Position::Fw),
        _ => None,
    }
}

fn str_to_kind(s: &str) -> Option<Kind> {
    match s {
        "transfer" => Some(Kind::Transfer),
        "loan" => Some(Kind::Loan),
        "free" => Some(Kind::Free),
        _ => None,
    }
}

/// Write the bundled SQLite DB for `corpus` at `out`, replacing any existing
/// file. Foreign keys are enforced during the build so a corrupt corpus fails
/// loudly instead of producing a broken artifact.
pub fn generate_db(corpus: &Corpus, out: &Path) -> Result<(), DataError> {
    if out.exists() {
        std::fs::remove_file(out)?;
    }
    let mut conn = Connection::open(out)?;
    conn.pragma_update(None, "foreign_keys", "ON")?;
    conn.execute_batch(SCHEMA)?;

    let tx = conn.transaction()?;
    {
        let mut ins = tx.prepare("INSERT INTO club (id, name, notoriety) VALUES (?1, ?2, ?3)")?;
        for c in &corpus.clubs {
            ins.execute(params![c.id, c.name_en, c.notoriety])?;
        }

        let mut ins = tx.prepare(
            "INSERT INTO player (id, name, position, nationality, birth_year, notoriety)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6)",
        )?;
        for p in &corpus.players {
            ins.execute(params![
                p.id,
                p.name_en,
                p.position.map(position_str),
                p.nationality,
                p.birth_year,
                p.notoriety
            ])?;
        }

        let mut ins = tx.prepare("INSERT INTO player_alias (player_id, alias) VALUES (?1, ?2)")?;
        for p in &corpus.players {
            for a in &p.aliases {
                ins.execute(params![p.id, a])?;
            }
        }

        let mut ins = tx.prepare(
            "INSERT INTO transfer (id, player_id, from_club, to_club, year, kind, tier)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7)",
        )?;
        for t in &corpus.transfers {
            ins.execute(params![
                t.id,
                t.player_id,
                t.from_club,
                t.to_club,
                t.year,
                kind_str(t.kind),
                t.tier
            ])?;
        }
    }
    tx.commit()?;

    // Compact, analyzed, read-optimized artifact.
    conn.execute_batch("ANALYZE; VACUUM;")?;
    Ok(())
}

/// Read the bundled SQLite DB at `path` back into a [`Corpus`]. The inverse
/// of [`generate_db`]. Per-language name fields are all filled with the
/// single `name` column stored in the DB (see module docs).
pub fn load_from_db(path: &Path) -> Result<Corpus, DataError> {
    let conn = Connection::open(path)?;

    let mut clubs = Vec::new();
    {
        let mut stmt = conn.prepare("SELECT id, name, notoriety FROM club")?;
        let mut rows = stmt.query([])?;
        while let Some(row) = rows.next()? {
            let id: String = row.get(0)?;
            let name: String = row.get(1)?;
            let notoriety: i64 = row.get(2)?;
            clubs.push(Club {
                id,
                name_en: name.clone(),
                name_fr: name.clone(),
                name_es: name,
                notoriety,
            });
        }
    }

    let mut aliases_by_player: HashMap<String, Vec<String>> = HashMap::new();
    {
        let mut stmt =
            conn.prepare("SELECT player_id, alias FROM player_alias ORDER BY player_id")?;
        let mut rows = stmt.query([])?;
        while let Some(row) = rows.next()? {
            let player_id: String = row.get(0)?;
            let alias: String = row.get(1)?;
            aliases_by_player.entry(player_id).or_default().push(alias);
        }
    }

    let mut players = Vec::new();
    {
        let mut stmt = conn
            .prepare("SELECT id, name, position, nationality, birth_year, notoriety FROM player")?;
        let mut rows = stmt.query([])?;
        while let Some(row) = rows.next()? {
            let id: String = row.get(0)?;
            let name: String = row.get(1)?;
            let position: Option<String> = row.get(2)?;
            let nationality: Option<String> = row.get(3)?;
            let birth_year: Option<i32> = row.get(4)?;
            let notoriety: i64 = row.get(5)?;
            let aliases = aliases_by_player.remove(&id).unwrap_or_default();
            players.push(Player {
                id,
                name_en: name.clone(),
                name_fr: name.clone(),
                name_es: name,
                aliases,
                position: position.map(|p| {
                    str_to_position(&p).unwrap_or_else(|| panic!("unknown position {p:?} in db"))
                }),
                nationality,
                birth_year,
                notoriety,
            });
        }
    }

    let mut transfers = Vec::new();
    {
        let mut stmt = conn
            .prepare("SELECT id, player_id, from_club, to_club, year, kind, tier FROM transfer")?;
        let mut rows = stmt.query([])?;
        while let Some(row) = rows.next()? {
            let id: i64 = row.get(0)?;
            let player_id: String = row.get(1)?;
            let from_club: String = row.get(2)?;
            let to_club: String = row.get(3)?;
            let year: i32 = row.get(4)?;
            let kind: String = row.get(5)?;
            let tier: u8 = row.get(6)?;
            transfers.push(Transfer {
                id,
                player_id,
                from_club,
                to_club,
                year,
                kind: str_to_kind(&kind).unwrap_or_else(|| panic!("unknown kind {kind:?} in db")),
                tier,
            });
        }
    }

    Ok(Corpus::new(players, clubs, transfers))
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::sync::atomic::{AtomicU32, Ordering};

    fn tmp_db_path() -> std::path::PathBuf {
        static COUNTER: AtomicU32 = AtomicU32::new(0);
        let n = COUNTER.fetch_add(1, Ordering::SeqCst);
        std::env::temp_dir().join(format!(
            "mercato_data_sqlite_roundtrip_{}_{n}.sqlite",
            std::process::id()
        ))
    }

    fn sample_corpus() -> Corpus {
        let clubs = vec![
            Club {
                id: "psg".into(),
                name_en: "Paris Saint-Germain".into(),
                name_fr: "Paris Saint-Germain".into(),
                name_es: "Paris Saint-Germain".into(),
                notoriety: 90,
            },
            Club {
                id: "fcb".into(),
                name_en: "FC Barcelona".into(),
                name_fr: "FC Barcelona".into(),
                name_es: "FC Barcelona".into(),
                notoriety: 95,
            },
            Club {
                id: "mci".into(),
                name_en: "Manchester City".into(),
                name_fr: "Manchester City".into(),
                name_es: "Manchester City".into(),
                notoriety: 92,
            },
        ];

        let players = vec![
            Player {
                id: "mbappe".into(),
                name_en: "Kylian Mbappe".into(),
                name_fr: "Kylian Mbappe".into(),
                name_es: "Kylian Mbappe".into(),
                aliases: vec!["Donatello".into(), "Kyky".into()],
                position: Some(Position::Fw),
                nationality: Some("FR".into()),
                birth_year: Some(1998),
                notoriety: 99,
            },
            Player {
                id: "messi".into(),
                name_en: "Lionel Messi".into(),
                name_fr: "Lionel Messi".into(),
                name_es: "Lionel Messi".into(),
                aliases: vec!["La Pulga".into()],
                position: Some(Position::Fw),
                nationality: Some("AR".into()),
                birth_year: Some(1987),
                notoriety: 100,
            },
            Player {
                id: "goalie".into(),
                name_en: "Some Keeper".into(),
                name_fr: "Some Keeper".into(),
                name_es: "Some Keeper".into(),
                aliases: vec![],
                position: Some(Position::Gk),
                nationality: None,
                birth_year: None,
                notoriety: 40,
            },
        ];

        let transfers = vec![
            Transfer {
                id: 1,
                player_id: "mbappe".into(),
                from_club: "psg".into(),
                to_club: "fcb".into(),
                year: 2024,
                kind: Kind::Transfer,
                tier: 1,
            },
            Transfer {
                id: 2,
                player_id: "messi".into(),
                from_club: "fcb".into(),
                to_club: "mci".into(),
                year: 2021,
                kind: Kind::Free,
                tier: 2,
            },
        ];

        Corpus::new(players, clubs, transfers)
    }

    #[test]
    fn round_trips_corpus_through_sqlite() {
        let corpus = sample_corpus();
        let path = tmp_db_path();

        generate_db(&corpus, &path).expect("generate_db should succeed");
        let loaded = load_from_db(&path).expect("load_from_db should succeed");

        std::fs::remove_file(&path).ok();

        assert_eq!(loaded.clubs.len(), corpus.clubs.len());
        assert_eq!(loaded.players.len(), corpus.players.len());
        assert_eq!(loaded.transfers.len(), corpus.transfers.len());

        let mbappe = loaded
            .players
            .iter()
            .find(|p| p.id == "mbappe")
            .expect("mbappe should be present");
        assert_eq!(mbappe.name_en, "Kylian Mbappe");
        assert_eq!(mbappe.name_fr, "Kylian Mbappe");
        assert_eq!(mbappe.name_es, "Kylian Mbappe");
        assert_eq!(mbappe.position, Some(Position::Fw));
        assert_eq!(mbappe.nationality.as_deref(), Some("FR"));
        assert_eq!(mbappe.birth_year, Some(1998));
        assert_eq!(mbappe.notoriety, 99);
        let mut aliases = mbappe.aliases.clone();
        aliases.sort();
        assert_eq!(aliases, vec!["Donatello".to_string(), "Kyky".to_string()]);

        let goalie = loaded
            .players
            .iter()
            .find(|p| p.id == "goalie")
            .expect("goalie should be present");
        assert!(goalie.aliases.is_empty());
        assert_eq!(goalie.nationality, None);
        assert_eq!(goalie.birth_year, None);

        let transfer = loaded
            .transfers
            .iter()
            .find(|t| t.id == 1)
            .expect("transfer 1 should be present");
        assert_eq!(transfer.player_id, "mbappe");
        assert_eq!(transfer.from_club, "psg");
        assert_eq!(transfer.to_club, "fcb");
        assert_eq!(transfer.year, 2024);
        assert_eq!(transfer.kind, Kind::Transfer);
        assert_eq!(transfer.tier, 1);

        let free_transfer = loaded
            .transfers
            .iter()
            .find(|t| t.id == 2)
            .expect("transfer 2 should be present");
        assert_eq!(free_transfer.kind, Kind::Free);
    }
}
