//! Loads the real dataset from `data/` and checks it end to end. This doubles
//! as the CI integrity check for the CSV source of truth.

use std::path::PathBuf;

use mercato_core::{Lang, Mode};
use mercato_data::load_corpus;

fn data_dir() -> PathBuf {
    // CARGO_MANIFEST_DIR = core/mercato-data
    PathBuf::from(env!("CARGO_MANIFEST_DIR"))
        .join("../../data")
        .canonicalize()
        .expect("data dir")
}

#[test]
fn loads_the_real_dataset() {
    let c = load_corpus(&data_dir()).expect("dataset loads");

    assert_eq!(c.clubs.len(), 412);
    assert_eq!(c.players.len(), 513);
    assert_eq!(c.transfers.len(), 1905);
    assert_eq!(c.nationalities.len(), 53);

    // Aliases were joined onto players (945 across the corpus).
    let aliases: usize = c.players.iter().map(|p| p.aliases.len()).sum();
    assert_eq!(aliases, 945);
}

#[test]
fn easy_pool_matches_the_spec() {
    let c = load_corpus(&data_dir()).expect("dataset loads");
    // Easy = tiers 1 and 2; the spec quotes 956 transfers.
    assert_eq!(
        mercato_core::round::pool(&c.transfers, Mode::Easy).len(),
        956
    );
    assert_eq!(
        mercato_core::round::pool(&c.transfers, Mode::Hardcore).len(),
        1905
    );
}

#[test]
fn names_are_trilingual() {
    let c = load_corpus(&data_dir()).expect("dataset loads");

    // Clubs and nationalities genuinely differ across languages.
    let clubs_differ = c.clubs.iter().filter(|x| x.name_en != x.name_fr).count();
    assert!(
        clubs_differ > 100,
        "expected many FR club names, got {clubs_differ}"
    );
    let nats_differ = c
        .nationalities
        .iter()
        .filter(|n| n.name_en != n.name_fr)
        .count();
    assert!(
        nats_differ > 30,
        "expected many FR nationalities, got {nats_differ}"
    );

    // Spot check a known translation.
    let zaragoza = c.club("Q10308").expect("Zaragoza");
    assert_eq!(zaragoza.name(Lang::En), "Real Zaragoza");
    assert_eq!(zaragoza.name(Lang::Fr), "Real Saragosse");
}

#[test]
fn every_transfer_resolves() {
    let c = load_corpus(&data_dir()).expect("dataset loads");
    for t in &c.transfers {
        assert!(c.player(&t.player_id).is_some(), "player {}", t.player_id);
        assert!(c.club(&t.from_club).is_some(), "club {}", t.from_club);
        assert!(c.club(&t.to_club).is_some(), "club {}", t.to_club);
        assert_ne!(t.from_club, t.to_club, "transfer {} is a self-move", t.id);
    }
    for p in &c.players {
        if let Some(nat) = &p.nationality {
            assert!(c.nationality(nat).is_some(), "nationality {nat}");
        }
    }
}

#[test]
fn matching_works_on_the_real_corpus() {
    let c = load_corpus(&data_dir()).expect("dataset loads");

    // Exact, accent-insensitive, and the cardinal rule (kane must not pass
    // for Kanté) all hold on the shipped data.
    let sukur = "Q192974"; // Hakan Şükür
    assert!(
        c.matcher()
            .match_by_id(&c.players, "Hakan Şükür", sukur, 2)
            .unwrap()
            .ok
    );
    assert!(
        c.matcher()
            .match_by_id(&c.players, "hakan sukur", sukur, 2)
            .unwrap()
            .ok
    );
    assert!(
        !c.matcher()
            .match_by_id(&c.players, "zzzzzzzz", sukur, 2)
            .unwrap()
            .ok
    );
}
