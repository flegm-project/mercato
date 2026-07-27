//! String normalization, ported verbatim from the reference engine.
//!
//! Reference (`core/reference/engine.reference.js`):
//! ```js
//! s.normalize("NFD").replace(/[̀-ͯ]/g,"")
//!  .replace(/[ØøŁłÐðÞþ]/g, map).toLowerCase()
//!  .replace(/[.'’`\-_]/g," ").replace(/[^a-z0-9 ]/g,"")
//!  .replace(/\s+/g," ").trim();
//! ```
//! Steps, in order: NFD decompose, drop combining marks (U+0300..U+036F),
//! map special latin letters, lowercase, turn a fixed punctuation set into
//! spaces, drop everything outside `[a-z0-9 ]`, collapse whitespace, trim.

use unicode_normalization::UnicodeNormalization;

/// Map the special letters the reference handles explicitly (they have no
/// canonical NFD decomposition, so stripping combining marks does not touch
/// them). Applied for both cases before lowercasing, exactly as in JS.
fn map_special(c: char) -> char {
    match c {
        'Ø' => 'O',
        'ø' => 'o',
        'Ł' => 'L',
        'ł' => 'l',
        'Ð' => 'D',
        'ð' => 'd',
        'Þ' => 'T',
        'þ' => 't',
        other => other,
    }
}

pub fn normalize(s: &str) -> String {
    // NFD + strip combining marks + map special letters.
    let stripped: String = s
        .nfd()
        .filter(|c| !('\u{300}'..='\u{36f}').contains(c))
        .map(map_special)
        .collect();

    // Lowercase, then punctuation -> space and keep only [a-z0-9 ].
    let mut buf = String::with_capacity(stripped.len());
    for c in stripped.to_lowercase().chars() {
        let c = match c {
            '.' | '\'' | '\u{2019}' | '`' | '-' | '_' => ' ',
            other => other,
        };
        if c == ' ' || c.is_ascii_lowercase() || c.is_ascii_digit() {
            buf.push(c);
        }
        // anything else is dropped, matching /[^a-z0-9 ]/g -> ""
    }

    // Collapse runs of whitespace and trim.
    buf.split_whitespace().collect::<Vec<_>>().join(" ")
}

#[cfg(test)]
mod tests {
    use super::normalize;

    #[test]
    fn diacritics_and_case() {
        assert_eq!(normalize("Hakan Şükür"), "hakan sukur");
        assert_eq!(normalize("N'Golo Kanté"), "n golo kante");
        assert_eq!(normalize("Virgil van Dijk"), "virgil van dijk");
    }

    #[test]
    fn special_letters() {
        assert_eq!(normalize("Kristoffer Ajer Ø"), "kristoffer ajer o");
        assert_eq!(normalize("Robert Lewandowski Ł"), "robert lewandowski l");
    }

    #[test]
    fn punctuation_and_whitespace() {
        assert_eq!(normalize("  O'Brien-Smith_jr  "), "o brien smith jr");
        assert_eq!(normalize("A.C.  Milan"), "a c milan");
    }
}
