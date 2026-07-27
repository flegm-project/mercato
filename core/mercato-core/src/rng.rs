//! Seedable RNG, ported verbatim from the reference (`mulberry32`, `hashStr`).
//! Bit-exact with the JS so decoy selection is reproducible and parity-testable.
//!
//! ```js
//! function mulberry32(seed){ return function(){
//!   seed|=0; seed=seed+0x6D2B79F5|0;
//!   let t=Math.imul(seed^seed>>>15,1|seed);
//!   t=t+Math.imul(t^t>>>7,61|t)^t;
//!   return ((t^t>>>14)>>>0)/4294967296; }; }
//! ```

/// `Math.imul`: 32-bit wrapping multiply.
#[inline]
fn imul(a: u32, b: u32) -> u32 {
    a.wrapping_mul(b)
}

#[derive(Debug, Clone)]
pub struct Mulberry32 {
    state: u32,
}

impl Mulberry32 {
    pub fn new(seed: u32) -> Self {
        Self { state: seed }
    }

    /// Next float in [0, 1), matching the JS generator exactly.
    pub fn next_f64(&mut self) -> f64 {
        // seed = seed + 0x6D2B79F5 | 0
        self.state = self.state.wrapping_add(0x6D2B_79F5);
        let seed = self.state;
        // t = imul(seed ^ seed>>>15, 1|seed)
        let mut t = imul(seed ^ (seed >> 15), 1 | seed);
        // t = t + imul(t ^ t>>>7, 61|t) ^ t
        t = (t.wrapping_add(imul(t ^ (t >> 7), 61 | t))) ^ t;
        // ((t ^ t>>>14) >>> 0) / 4294967296
        ((t ^ (t >> 14)) as f64) / 4_294_967_296.0
    }
}

/// FNV-1a-style 32-bit string hash, ported verbatim (`hashStr`).
///
/// ```js
/// let h=2166136261; for(...) { h^=charCodeAt(i); h=Math.imul(h,16777619); } h>>>0
/// ```
/// Uses UTF-16 code units to match `charCodeAt`.
pub fn hash_str(s: &str) -> u32 {
    let mut h: u32 = 2166136261;
    for u in s.encode_utf16() {
        h ^= u as u32;
        h = imul(h, 16777619);
    }
    h
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn deterministic_sequence() {
        // Regression values; also re-checked against JS in the parity fixture.
        let mut r = Mulberry32::new(42);
        let a = r.next_f64();
        let b = r.next_f64();
        assert!((0.0..1.0).contains(&a));
        assert_ne!(a, b);

        let mut r2 = Mulberry32::new(42);
        assert_eq!(a, r2.next_f64());
    }

    #[test]
    fn hash_is_stable() {
        assert_eq!(hash_str("2026-07-27"), hash_str("2026-07-27"));
        assert_ne!(hash_str("a"), hash_str("b"));
    }
}
