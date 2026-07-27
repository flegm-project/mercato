//! Levenshtein distance and the adaptive threshold, ported verbatim.
//!
//! After [`crate::normalize`], strings are ASCII `[a-z0-9 ]`, so operating on
//! bytes is equivalent to the reference's per-UTF-16-unit comparison.

/// Classic Levenshtein edit distance (two-row DP), matching the reference.
pub fn levenshtein(a: &str, b: &str) -> usize {
    if a == b {
        return 0;
    }
    let a = a.as_bytes();
    let b = b.as_bytes();
    if a.is_empty() {
        return b.len();
    }
    if b.is_empty() {
        return a.len();
    }

    let mut prev: Vec<usize> = (0..=b.len()).collect();
    let mut cur = vec![0usize; b.len() + 1];
    for i in 1..=a.len() {
        cur[0] = i;
        for j in 1..=b.len() {
            let cost = if a[i - 1] == b[j - 1] { 0 } else { 1 };
            cur[j] = (prev[j] + 1).min(cur[j - 1] + 1).min(prev[j - 1] + cost);
        }
        std::mem::swap(&mut prev, &mut cur);
    }
    prev[b.len()]
}

/// Adaptive max distance by target length: `<=4 -> 0`, `5..=6 -> min(1, base)`,
/// `>6 -> base`. Reference uses `base = 2`.
pub fn threshold_for(target: &str, base: usize) -> usize {
    let len = target.len(); // ASCII post-normalize: byte len == char len
    if len <= 4 {
        0
    } else if len <= 6 {
        1.min(base)
    } else {
        base
    }
}

/// The reference's default base.
pub const BASE: usize = 2;

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn distance_basics() {
        assert_eq!(levenshtein("kane", "kante"), 1);
        assert_eq!(levenshtein("mbappe", "mbappe"), 0);
        assert_eq!(levenshtein("", "abc"), 3);
    }

    #[test]
    fn thresholds() {
        assert_eq!(threshold_for("kane", BASE), 0); // len 4
        assert_eq!(threshold_for("kante", BASE), 1); // len 5
        assert_eq!(threshold_for("ronaldo", BASE), 2); // len 7
        assert_eq!(threshold_for("kante", 0), 0); // min(1, base)
    }
}
