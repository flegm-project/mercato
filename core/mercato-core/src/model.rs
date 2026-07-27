//! Domain types. These mirror the CSV/DB schema (see docs/DATA.md) and the
//! prototype's inline DATA shape (see core/reference/engine.reference.js).

use serde::Deserialize;

/// UI / name language. English is the fallback (see docs/GAME_DESIGN.md).
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Lang {
    En,
    Fr,
    Es,
}

/// Playing position. Matches the `position` CSV column.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum Position {
    Gk,
    Def,
    Mid,
    Fw,
}

impl Position {
    /// Display label per language (used by the Hardcore hint ladder). Only
    /// four values, so the table lives here rather than in the dataset.
    pub fn label(self, lang: Lang) -> &'static str {
        match (self, lang) {
            (Position::Gk, Lang::En) => "Goalkeeper",
            (Position::Gk, Lang::Fr) => "Gardien",
            (Position::Gk, Lang::Es) => "Portero",
            (Position::Def, Lang::En) => "Defender",
            (Position::Def, Lang::Fr) => "Défenseur",
            (Position::Def, Lang::Es) => "Defensa",
            (Position::Mid, Lang::En) => "Midfielder",
            (Position::Mid, Lang::Fr) => "Milieu",
            (Position::Mid, Lang::Es) => "Centrocampista",
            (Position::Fw, Lang::En) => "Forward",
            (Position::Fw, Lang::Fr) => "Attaquant",
            (Position::Fw, Lang::Es) => "Delantero",
        }
    }
}

/// Display names for a nationality. `key` is the exact string used in
/// `players.csv` (e.g. "Kingdom of Denmark"); the per-language names are the
/// user-facing forms ("Denmark" / "Danemark" / "Dinamarca").
#[derive(Debug, Clone, Deserialize)]
pub struct Nationality {
    #[serde(rename = "nationality")]
    pub key: String,
    pub name_en: String,
    pub name_fr: String,
    pub name_es: String,
}

impl Nationality {
    pub fn name(&self, lang: Lang) -> &str {
        match lang {
            Lang::En => &self.name_en,
            Lang::Fr => &self.name_fr,
            Lang::Es => &self.name_es,
        }
    }
}

/// Move type. Matches the `kind` CSV column.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum Kind {
    Transfer,
    Loan,
    Free,
}

/// A footballer. Names are stored per language; player names are identical
/// across languages today, but the field is per-language so extracted FR/ES
/// data can differ later without touching the engine.
#[derive(Debug, Clone, Deserialize)]
pub struct Player {
    pub id: String,
    pub name_en: String,
    pub name_fr: String,
    pub name_es: String,
    #[serde(default)]
    pub aliases: Vec<String>,
    #[serde(default)]
    pub position: Option<Position>,
    #[serde(default)]
    pub nationality: Option<String>,
    #[serde(default)]
    pub birth_year: Option<i32>,
    /// `notoriety` in the schema; the reference engine calls it `l` (links).
    pub notoriety: i64,
}

impl Player {
    /// Canonical name in the requested language.
    pub fn name(&self, lang: Lang) -> &str {
        match lang {
            Lang::En => &self.name_en,
            Lang::Fr => &self.name_fr,
            Lang::Es => &self.name_es,
        }
    }

    /// All name forms: canonical (fr, en, es) followed by aliases, order
    /// preserved and de-duplicated. Mirrors `namesOf(p)` in the reference.
    pub fn names(&self) -> Vec<&str> {
        let mut out: Vec<&str> = Vec::new();
        for n in [
            self.name_fr.as_str(),
            self.name_en.as_str(),
            self.name_es.as_str(),
        ] {
            if !out.contains(&n) {
                out.push(n);
            }
        }
        for a in &self.aliases {
            let a = a.as_str();
            if !out.contains(&a) {
                out.push(a);
            }
        }
        out
    }

    /// Canonical trio (fr, en, es) in the reference's order.
    pub fn canonical(&self) -> [&str; 3] {
        [&self.name_fr, &self.name_en, &self.name_es]
    }
}

#[derive(Debug, Clone, Deserialize)]
pub struct Club {
    pub id: String,
    pub name_en: String,
    pub name_fr: String,
    pub name_es: String,
    pub notoriety: i64,
}

impl Club {
    pub fn name(&self, lang: Lang) -> &str {
        match lang {
            Lang::En => &self.name_en,
            Lang::Fr => &self.name_fr,
            Lang::Es => &self.name_es,
        }
    }
}

#[derive(Debug, Clone, Deserialize)]
pub struct Transfer {
    pub id: i64,
    pub player_id: String,
    pub from_club: String,
    pub to_club: String,
    pub year: i32,
    pub kind: Kind,
    /// 1 mainstream, 2 informed fan, 3 expert.
    pub tier: u8,
}
