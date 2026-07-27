//! Round pools and selection. Easy = tier 1+2, Hardcore = all (see
//! docs/GAME_DESIGN.md). Selection is seeded for reproducibility.

use crate::model::Transfer;
use crate::rng::Mulberry32;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Mode {
    Easy,
    Hardcore,
}

/// Transfers eligible for a mode. Easy keeps tiers 1 and 2; Hardcore keeps all.
pub fn pool(transfers: &[Transfer], mode: Mode) -> Vec<&Transfer> {
    transfers
        .iter()
        .filter(|t| match mode {
            Mode::Easy => t.tier <= 2,
            Mode::Hardcore => true,
        })
        .collect()
}

/// Pick an index in `[0, len)` from the RNG (`floor(rng()*len)`).
pub fn pick_index(len: usize, rng: &mut Mulberry32) -> Option<usize> {
    if len == 0 {
        return None;
    }
    Some((rng.next_f64() * len as f64) as usize)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::model::Kind;

    fn t(id: i64, tier: u8) -> Transfer {
        Transfer {
            id,
            player_id: "Q1".into(),
            from_club: "C1".into(),
            to_club: "C2".into(),
            year: 2020,
            kind: Kind::Transfer,
            tier,
        }
    }

    #[test]
    fn pools_by_tier() {
        let all = vec![t(1, 1), t(2, 2), t(3, 3)];
        assert_eq!(pool(&all, Mode::Easy).len(), 2);
        assert_eq!(pool(&all, Mode::Hardcore).len(), 3);
    }
}
