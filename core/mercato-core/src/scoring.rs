//! Scoring: +3 correct, +0 wrong, streak resets on a miss. Session-only (no
//! persistence in v1). See docs/GAME_DESIGN.md.

pub const POINTS_CORRECT: i64 = 3;

#[derive(Debug, Clone, Default)]
pub struct Score {
    pub points: i64,
    pub streak: u32,
    pub best_streak: u32,
    /// Colour hint for the UI: whether the last answer was correct.
    pub last_correct: Option<bool>,
}

impl Score {
    pub fn new() -> Self {
        Self::default()
    }

    /// Register an answer. Returns the points gained (3 or 0).
    pub fn answer(&mut self, correct: bool) -> i64 {
        self.last_correct = Some(correct);
        if correct {
            self.points += POINTS_CORRECT;
            self.streak += 1;
            self.best_streak = self.best_streak.max(self.streak);
            POINTS_CORRECT
        } else {
            self.streak = 0;
            0
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn streaks_and_points() {
        let mut s = Score::new();
        assert_eq!(s.answer(true), 3);
        assert_eq!(s.answer(true), 3);
        assert_eq!(s.streak, 2);
        assert_eq!(s.answer(false), 0);
        assert_eq!(s.streak, 0);
        assert_eq!(s.best_streak, 2);
        assert_eq!(s.points, 6);
    }
}
