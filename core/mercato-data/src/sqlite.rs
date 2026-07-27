//! Bundled-DB generation: [`Corpus`] -> read-only SQLite artifact.
//!
//! Schema follows docs/DATA.md (single English name per entity until Phase 4
//! adds per-language columns). The DB is a build artifact, never committed.

use std::path::Path;

use mercato_core::{Corpus, Kind, Position};
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
