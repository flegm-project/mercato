//! Decoy (distractor) selection for Easy mode, ported verbatim from
//! `distractorsFor`. Deterministic given a seeded [`crate::rng::Mulberry32`].
//!
//! Scoring per candidate:
//! ```text
//! +3    same position
//! +2    |birth_year gap| <= 6
//! +1    same nationality
//! +2  * notoriety / max_notoriety
//! -1.5 * |notoriety - target| / max_notoriety
//! + rng()*0.9
//! ```
//! Then take the top 3 (stable sort, descending).

use crate::model::Player;
use crate::rng::Mulberry32;

pub fn max_notoriety(players: &[Player]) -> i64 {
    players.iter().map(|p| p.notoriety).max().unwrap_or(0)
}

fn score(p: &Player, tg: &Player, max: f64) -> f64 {
    let mut s = 0.0f64;
    if p.position == tg.position {
        s += 3.0;
    }
    if let (Some(a), Some(b)) = (p.birth_year, tg.birth_year) {
        if (a - b).abs() <= 6 {
            s += 2.0;
        }
    }
    if p.nationality == tg.nationality {
        s += 1.0;
    }
    let l = p.notoriety as f64;
    let tgl = tg.notoriety as f64;
    s += 2.0 * l / max;
    s -= 1.5 * (l - tgl).abs() / max;
    s
}

/// Returns the indices of the 3 decoys for the target at index `pid`.
/// Iterates players in corpus order (matching JS `Object.keys` insertion
/// order) so the RNG is consumed in the same sequence.
pub fn distractors_for(players: &[Player], pid: usize, rng: &mut Mulberry32) -> Vec<usize> {
    let tg = &players[pid];
    let max = max_notoriety(players) as f64;

    let mut scored: Vec<(usize, f64)> = Vec::with_capacity(players.len().saturating_sub(1));
    for (idx, p) in players.iter().enumerate() {
        if idx == pid {
            continue;
        }
        let s = score(p, tg, max) + rng.next_f64() * 0.9;
        scored.push((idx, s));
    }
    // Stable sort, descending by score (ties keep corpus order).
    scored.sort_by(|a, b| b.1.partial_cmp(&a.1).unwrap_or(std::cmp::Ordering::Equal));
    scored.into_iter().take(3).map(|(idx, _)| idx).collect()
}
