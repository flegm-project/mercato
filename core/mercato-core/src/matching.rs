//! Answer matching, ported verbatim from `core/reference/engine.reference.js`.
//! Do not approximate. Behavior is locked by parity tests (see
//! `tests/parity.rs`). Two safety rules are mandatory:
//!   1. exact-beats-fuzzy (`claimed_by_other`)
//!   2. ambiguous-surname rejection (does not consume an attempt)

use std::collections::{HashMap, HashSet};

use serde::Deserialize;

use crate::distance::{levenshtein, threshold_for};
use crate::model::Player;
use crate::normalize::normalize;

/// Which route accepted (or rejected) the guess.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum Route {
    Exact,
    Alias,
    Fuzzy,
    Surname,
    None,
}

/// Outcome of matching a guess against a specific target player.
#[derive(Debug, Clone, PartialEq)]
pub struct MatchResult {
    pub ok: bool,
    pub route: Route,
    pub dist: Option<usize>,
    pub matched: Option<String>,
    pub ambiguous: bool,
}

impl MatchResult {
    fn none_empty() -> Self {
        Self {
            ok: false,
            route: Route::None,
            dist: None,
            matched: None,
            ambiguous: false,
        }
    }
}

/// Name particles kept with the surname (never eaten into the first name).
/// Verbatim from the reference `PARTICLES`.
const PARTICLES: &[&str] = &[
    "van", "von", "de", "del", "della", "di", "da", "do", "dos", "das", "du", "des", "le", "la",
    "el", "al", "bin", "ibn", "ter", "ten", "st", "mc", "mac", "o", "van't", "der", "den",
];

fn is_particle(tok: &str) -> bool {
    PARTICLES.contains(&tok)
}

/// Surname variants for a name: the full surname (with leading particles) and
/// the bare last token. Mirrors `surnameVariants`.
pub fn surname_variants(name: &str) -> Vec<String> {
    let norm = normalize(name);
    let tk: Vec<&str> = norm.split(' ').filter(|s| !s.is_empty()).collect();
    if tk.len() < 2 {
        return vec![tk.first().map(|s| s.to_string()).unwrap_or_default()];
    }
    // Walk left over leading particles, never past index 1 (keep the first name).
    let mut i = tk.len() - 1;
    while i >= 2 && is_particle(tk[i - 1]) {
        i -= 1;
    }
    let full = tk[i..].join(" ");
    let bare = tk[tk.len() - 1].to_string();
    if full == bare {
        vec![bare]
    } else {
        vec![full, bare]
    }
}

/// Matcher over a fixed corpus. Builds the EXACT and SURNAME indexes at
/// construction, exactly as the reference does at module load.
///
/// The indexes are owned (they store player *indices*, not references), so a
/// `Matcher` can live alongside the players it describes -- e.g. inside
/// [`crate::Corpus`] -- without a self-referential borrow. Query methods take
/// the player slice back.
#[derive(Debug, Clone, Default)]
pub struct Matcher {
    by_id: HashMap<String, usize>,
    /// normalized form -> player indices that own it (full names + surnames).
    exact_index: HashMap<String, Vec<usize>>,
    /// normalized surname -> player indices (canonical names only).
    surname_index: HashMap<String, Vec<usize>>,
}

fn push_unique(map: &mut HashMap<String, Vec<usize>>, key: String, idx: usize) {
    if key.is_empty() {
        return;
    }
    let v = map.entry(key).or_default();
    if !v.contains(&idx) {
        v.push(idx);
    }
}

impl Matcher {
    pub fn new(players: &[Player]) -> Self {
        let mut by_id = HashMap::with_capacity(players.len());
        let mut exact_index: HashMap<String, Vec<usize>> = HashMap::new();
        let mut surname_index: HashMap<String, Vec<usize>> = HashMap::new();

        for (idx, p) in players.iter().enumerate() {
            by_id.insert(p.id.clone(), idx);

            // EXACT_INDEX: every normalized name form and every surname variant.
            for nm in p.names() {
                push_unique(&mut exact_index, normalize(nm), idx);
                for v in surname_variants(nm) {
                    push_unique(&mut exact_index, v, idx);
                }
            }
            // SURNAME_INDEX: canonical names only (fr, en, es).
            for nm in p.canonical() {
                for v in surname_variants(nm) {
                    push_unique(&mut surname_index, v, idx);
                }
            }
        }

        Self {
            by_id,
            exact_index,
            surname_index,
        }
    }

    pub fn player_index(&self, id: &str) -> Option<usize> {
        self.by_id.get(id).copied()
    }

    /// A guess is "claimed by another" if its normalized form is an exact
    /// name/surname of a different player. Guards fuzzy and distant-surname
    /// matches (the cardinal rule: "kane" must not pass for Kanté).
    fn claimed_by_other(&self, g: &str, pid: usize) -> bool {
        match self.exact_index.get(g) {
            Some(owners) => !owners.contains(&pid),
            None => false,
        }
    }

    /// Match `guess` against the player at index `pid`. Ported from
    /// `matchAnswer`; iteration orders are preserved deliberately.
    pub fn match_answer(
        &self,
        players: &[Player],
        guess: &str,
        pid: usize,
        base: usize,
    ) -> MatchResult {
        let p = &players[pid];
        let g = normalize(guess);
        if g.is_empty() {
            return MatchResult::none_empty();
        }

        let cands = p.names(); // fr, en, es, then aliases (deduped)
        let canon: HashSet<String> = p.canonical().iter().map(|c| normalize(c)).collect();

        // 1) exact / alias
        for c in &cands {
            if g == normalize(c) {
                let route = if canon.contains(&normalize(c)) {
                    Route::Exact
                } else {
                    Route::Alias
                };
                return MatchResult {
                    ok: true,
                    route,
                    dist: Some(0),
                    matched: Some((*c).to_string()),
                    ambiguous: false,
                };
            }
        }

        // 2) fuzzy against full names (best by distance, first on ties)
        let mut best_d = usize::MAX;
        let mut best_c: Option<&str> = None;
        for c in &cands {
            let d = levenshtein(&g, &normalize(c));
            if d < best_d {
                best_d = d;
                best_c = Some(c);
            }
        }
        if let Some(bc) = best_c {
            if best_d <= threshold_for(&normalize(bc), base) && !self.claimed_by_other(&g, pid) {
                return MatchResult {
                    ok: true,
                    route: Route::Fuzzy,
                    dist: Some(best_d),
                    matched: Some(bc.to_string()),
                    ambiguous: false,
                };
            }
        }

        // 3) surname variants (reference iterates en, fr, es here)
        let mut vars: Vec<String> = Vec::new();
        for nm in [&p.name_en, &p.name_fr, &p.name_es] {
            for v in surname_variants(nm) {
                if !vars.contains(&v) {
                    vars.push(v);
                }
            }
        }
        let mut hit: Option<(String, usize)> = None;
        for sn in &vars {
            if sn.is_empty() {
                continue;
            }
            let d = levenshtein(&g, sn);
            if d <= threshold_for(sn, base) && hit.as_ref().is_none_or(|(_, hd)| d < *hd) {
                hit = Some((sn.clone(), d));
            }
        }
        if let Some((sn, d)) = hit {
            let mut out = MatchResult {
                ok: false,
                route: Route::Surname,
                dist: Some(d),
                matched: Some(sn.clone()),
                ambiguous: false,
            };
            if d > 0 && self.claimed_by_other(&g, pid) {
                out.route = Route::None;
                return out;
            }
            if self.surname_index.get(&sn).map_or(0, |v| v.len()) > 1 {
                out.ambiguous = true;
                out.ok = false;
            } else {
                out.ok = true;
            }
            return out;
        }

        // 4) none (carries the best fuzzy distance, as the reference does)
        MatchResult {
            ok: false,
            route: Route::None,
            dist: Some(best_d),
            matched: best_c.map(|c| c.to_string()),
            ambiguous: false,
        }
    }

    /// Convenience: match by player id.
    pub fn match_by_id(
        &self,
        players: &[Player],
        guess: &str,
        player_id: &str,
        base: usize,
    ) -> Option<MatchResult> {
        let pid = self.player_index(player_id)?;
        Some(self.match_answer(players, guess, pid, base))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn surname_particles() {
        assert_eq!(
            surname_variants("Virgil van Dijk"),
            vec!["van dijk", "dijk"]
        );
        assert_eq!(
            surname_variants("Kevin De Bruyne"),
            vec!["de bruyne", "bruyne"]
        );
        assert_eq!(surname_variants("Neymar"), vec!["neymar"]);
    }
}
