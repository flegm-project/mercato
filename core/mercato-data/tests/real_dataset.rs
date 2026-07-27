//! Integration tests over the real CSVs in `data/` (source of truth).

use std::path::{Path, PathBuf};

fn data_dir() -> PathBuf {
    Path::new(env!("CARGO_MANIFEST_DIR")).join("../../data")
}

#[test]
fn loads_real_dataset_with_expected_counts() {
    let corpus = mercato_data::load_corpus(&data_dir()).expect("dataset must load");
    assert_eq!(corpus.clubs.len(), 412);
    assert_eq!(corpus.players.len(), 513);
    assert_eq!(corpus.transfers.len(), 1905);
    let aliases: usize = corpus.players.iter().map(|p| p.aliases.len()).sum();
    assert_eq!(aliases, 945);
}

#[test]
fn real_dataset_has_no_integrity_violations() {
    let corpus = mercato_data::load_corpus(&data_dir()).expect("dataset must load");
    assert!(mercato_data::validate(&corpus).is_empty());
}

#[test]
fn corpus_feeds_the_matcher() {
    // Exit criterion of Phase 2: the core can work over the real dataset.
    let corpus = mercato_data::load_corpus(&data_dir()).expect("dataset must load");
    let matcher = corpus.matcher();
    // Exact name, then the committed alias "Cole Jermaine Palmer" (Q99760796).
    let exact = matcher.match_by_id("Cole Palmer", "Q99760796", 0).unwrap();
    assert!(exact.ok);
    let alias = matcher
        .match_by_id("Cole Jermaine Palmer", "Q99760796", 0)
        .unwrap();
    assert!(alias.ok);
}

#[test]
fn generates_a_valid_bundled_db() {
    let corpus = mercato_data::load_corpus(&data_dir()).expect("dataset must load");
    let tmp = std::env::temp_dir().join("mercato-test-bundle.db");
    mercato_data::generate_db(&corpus, &tmp).expect("db generation must succeed");

    let conn = rusqlite::Connection::open(&tmp).expect("generated db must open");
    let count = |sql: &str| -> i64 { conn.query_row(sql, [], |r| r.get(0)).unwrap() };
    assert_eq!(count("SELECT COUNT(*) FROM club"), 412);
    assert_eq!(count("SELECT COUNT(*) FROM player"), 513);
    assert_eq!(count("SELECT COUNT(*) FROM player_alias"), 945);
    assert_eq!(count("SELECT COUNT(*) FROM transfer"), 1905);

    // FK integrity of the artifact itself.
    let violations: i64 = conn
        .query_row("SELECT COUNT(*) FROM pragma_foreign_key_check", [], |r| {
            r.get(0)
        })
        .unwrap();
    assert_eq!(violations, 0);

    std::fs::remove_file(tmp).ok();
}
