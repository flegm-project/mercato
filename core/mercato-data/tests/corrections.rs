//! Guards the name corrections applied from the review pass.
//!
//! Unifying a player's name across the three languages sometimes replaced a
//! short name with the full one. The short form is what most players would
//! type, and it is far past the fuzzy threshold from the full name, so each
//! one was preserved as an alias. These tests fail if that is ever undone.

use std::path::PathBuf;

use mercato_data::load_corpus;

fn data_dir() -> PathBuf {
    PathBuf::from(env!("CARGO_MANIFEST_DIR"))
        .join("../../data")
        .canonicalize()
        .expect("data dir")
}

#[test]
fn short_forms_still_match() {
    let c = load_corpus(&data_dir()).expect("dataset loads");

    // (player, what a person would type)
    let cases = [
        ("Q38136", "Marcelo"),
        ("Q179773", "Pedro"),
        ("Q19497", "Oscar"),
        ("Q312772", "Fred"),
        ("Q170452", "Adriano"),
        ("Q46741", "Leonardo"),
        ("Q177686", "Maicon"),
        ("Q182459", "Julio Cesar"),
        ("Q165125", "Javier Hernandez"),
    ];
    for (id, typed) in cases {
        let r = c
            .matcher()
            .match_by_id(&c.players, typed, id, 2)
            .unwrap_or_else(|| panic!("unknown player {id}"));
        assert!(r.ok, "typing {typed:?} should still match {id}, got {r:?}");
    }
}

/// Players whose name carries a middle name or a hyphenated compound first name
/// gained a "first + surname" alias, so the everyday short form is accepted even
/// though it is far past the fuzzy threshold from the full name. A shared
/// surname (Kevin-Prince vs Jerome Boateng) is disambiguated by the first name.
#[test]
fn first_plus_surname_short_forms_match() {
    let c = load_corpus(&data_dir()).expect("dataset loads");
    let cases = [
        ("Q151034", "Kevin Boateng"),   // Kevin-Prince Boateng
        ("Q44977", "Pierre Aubameyang"), // Pierre-Emerick Aubameyang
        ("Q26069", "Klaas Huntelaar"),   // Klaas-Jan Huntelaar
        ("Q13494", "Jean Papin"),        // Jean-Pierre Papin
        ("Q160472", "Marc ter Stegen"),  // Marc-Andre ter Stegen
        ("Q152871", "Karl Rummenigge"),  // Karl-Heinz Rummenigge
    ];
    for (id, typed) in cases {
        let r = c
            .matcher()
            .match_by_id(&c.players, typed, id, 2)
            .unwrap_or_else(|| panic!("unknown player {id}"));
        assert!(r.ok, "typing {typed:?} should match {id}, got {r:?}");
    }
}

/// Every form the review displaced was kept as an alias, so a guess that was
/// valid before the corrections is still valid after them.
#[test]
fn displaced_forms_still_match() {
    let c = load_corpus(&data_dir()).expect("dataset loads");
    let cases = [
        ("Q47526", "Arthur Antunes Coimbra"), // now displayed as Zico
        ("Q342489", "Antonio de Oliveira Filho"), // now Careca
        ("Q80892", "Francisco Roman Alarcon"), // now Isco
        ("Q11576", "Raul Gonzalez Blanco"),   // now Raul
        ("Q42728914", "Rodrygo Goes"),        // now Rodrygo
        ("Q222789", "Luis Enrique Martinez Garcia"),
        ("Q166984", "Mikel John Obi"),         // now John Obi Mikel
        ("Q28967995", "Erling Braut Haaland"), // now Erling Haaland
    ];
    for (id, typed) in cases {
        let r = c
            .matcher()
            .match_by_id(&c.players, typed, id, 2)
            .unwrap_or_else(|| panic!("unknown player {id}"));
        assert!(r.ok, "typing {typed:?} should still match {id}, got {r:?}");
    }
}

#[test]
fn corrected_names_are_unified_across_languages() {
    let c = load_corpus(&data_dir()).expect("dataset loads");
    for id in ["Q208104", "Q215522", "Q11557367", "Q22082505", "Q41533"] {
        let p = c
            .player(id)
            .unwrap_or_else(|| panic!("unknown player {id}"));
        assert_eq!(p.name_en, p.name_fr, "{id} still differs between en and fr");
        assert_eq!(p.name_fr, p.name_es, "{id} still differs between fr and es");
    }
    // The names that were plainly wrong are gone.
    assert_eq!(c.player("Q208104").unwrap().name_fr, "Xabi Alonso");
    assert_eq!(c.player("Q11557367").unwrap().name_en, "Takuma Asano");
    assert_eq!(c.player("Q215522").unwrap().name_es, "Guillermo Ochoa");
}
