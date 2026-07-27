//! Referential-integrity checks over a loaded [`Corpus`].

use std::collections::HashSet;

use mercato_core::Corpus;

/// Reasonable bounds for a transfer year; the dataset spans the modern game.
const YEAR_MIN: i32 = 1950;
const YEAR_MAX: i32 = 2100;

/// Check the dataset's internal consistency. Returns every violation found
/// (empty vec = valid) so CI can report them all in one run.
pub fn validate(corpus: &Corpus) -> Vec<String> {
    let mut errors = Vec::new();

    let mut player_ids: HashSet<&str> = HashSet::new();
    for p in &corpus.players {
        if !player_ids.insert(&p.id) {
            errors.push(format!("players: duplicate id '{}'", p.id));
        }
        if p.name_en.trim().is_empty() {
            errors.push(format!("players: '{}' has an empty name", p.id));
        }
    }

    let mut club_ids: HashSet<&str> = HashSet::new();
    for c in &corpus.clubs {
        if !club_ids.insert(&c.id) {
            errors.push(format!("clubs: duplicate id '{}'", c.id));
        }
        if c.name_en.trim().is_empty() {
            errors.push(format!("clubs: '{}' has an empty name", c.id));
        }
    }

    let mut transfer_ids: HashSet<i64> = HashSet::new();
    for t in &corpus.transfers {
        let ctx = format!("transfers: id {}", t.id);
        if !transfer_ids.insert(t.id) {
            errors.push(format!("{ctx}: duplicate id"));
        }
        if !player_ids.contains(t.player_id.as_str()) {
            errors.push(format!("{ctx}: unknown player '{}'", t.player_id));
        }
        if !club_ids.contains(t.from_club.as_str()) {
            errors.push(format!("{ctx}: unknown from_club '{}'", t.from_club));
        }
        if !club_ids.contains(t.to_club.as_str()) {
            errors.push(format!("{ctx}: unknown to_club '{}'", t.to_club));
        }
        if t.from_club == t.to_club {
            errors.push(format!(
                "{ctx}: from_club equals to_club ('{}')",
                t.from_club
            ));
        }
        if !(1..=3).contains(&t.tier) {
            errors.push(format!("{ctx}: tier {} out of range 1..=3", t.tier));
        }
        if !(YEAR_MIN..=YEAR_MAX).contains(&t.year) {
            errors.push(format!("{ctx}: year {} out of range", t.year));
        }
    }

    errors
}
