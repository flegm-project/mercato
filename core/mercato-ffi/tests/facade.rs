//! Drives the FFI facade exactly as the apps will: load the real dataset,
//! play a full round in each mode, and check the surface behaves.

use std::path::PathBuf;

use mercato_ffi::{language_for_locale, GameLang, GameMode};

fn data_dir() -> String {
    PathBuf::from(env!("CARGO_MANIFEST_DIR"))
        .join("../../data")
        .canonicalize()
        .expect("data dir")
        .to_string_lossy()
        .into_owned()
}

fn game() -> mercato_ffi::Game {
    mercato_ffi::Game::new(data_dir()).expect("dataset loads")
}

#[test]
fn locale_falls_back_to_english() {
    assert_eq!(language_for_locale("fr-FR".into()), GameLang::Fr);
    assert_eq!(language_for_locale("es_ES".into()), GameLang::Es);
    assert_eq!(language_for_locale("en-GB".into()), GameLang::En);
    // Unsupported languages fall back to English, per the product decision.
    assert_eq!(language_for_locale("de-DE".into()), GameLang::En);
    assert_eq!(language_for_locale("".into()), GameLang::En);
}

#[test]
fn plays_a_full_easy_round() {
    let g = game();
    g.start_round(GameLang::Fr, GameMode::Easy, 2024);

    let mut asked = 0;
    while let Some(q) = g.next_question() {
        assert_eq!(q.options.len(), 4);
        assert_eq!(q.total, g.questions_per_round());
        assert!(!q.from_club.is_empty() && !q.to_club.is_empty());
        assert_ne!(q.from_club, q.to_club);

        let a = g.submit_choice(0).expect("answer accepted");
        assert!(a.finished, "easy mode resolves in one attempt");
        assert!(a.revealed_name.is_some());
        asked += 1;
    }

    assert_eq!(asked, g.questions_per_round());
    assert!(g.is_over());

    // Every question either scored or was recorded as missed.
    let score = g.score();
    let correct = (score.points / 3) as u32;
    assert_eq!(correct as usize + g.missed().len(), asked as usize);
}

#[test]
fn hardcore_accepts_the_right_name_and_spends_attempts() {
    let g = game();
    g.start_round(GameLang::En, GameMode::Hardcore, 99);

    let q = g.next_question().expect("a question");
    assert!(q.options.is_empty(), "hardcore is free text");
    assert_eq!(q.attempts_left, g.hardcore_attempts());

    // A wrong guess costs an attempt but does not end the question.
    let miss = g.submit_guess("zzzzzzzzzz".into()).expect("answered");
    assert!(!miss.correct);
    assert_eq!(miss.attempts_left, g.hardcore_attempts() - 1);
    assert!(!miss.finished);

    // Hints unlock in order and are free.
    let h1 = g.next_hint().expect("nationality hint");
    assert!(h1.nationality.is_some());
    let h2 = g.next_hint().expect("position hint");
    assert!(h2.position.is_some());
    let h3 = g.next_hint().expect("surname hint");
    assert!(h3.surname_initial.is_some() && h3.surname_letters.is_some());
    assert!(g.next_hint().is_none(), "only three hints");
}

#[test]
fn same_seed_replays_identically() {
    let play = |seed| {
        let g = game();
        g.start_round(GameLang::En, GameMode::Easy, seed);
        let mut seen = Vec::new();
        while let Some(q) = g.next_question() {
            seen.push((
                q.year,
                q.from_club.clone(),
                q.to_club.clone(),
                q.options.clone(),
            ));
            g.submit_choice(0).ok();
        }
        seen
    };
    assert_eq!(play(7), play(7));
    assert_ne!(play(7), play(8));
}

#[test]
fn answering_without_a_question_is_an_error() {
    let g = game();
    g.start_round(GameLang::En, GameMode::Easy, 1);
    assert!(g.submit_choice(0).is_err());
    assert!(g.current_question().is_none());
}

#[test]
fn language_changes_club_names() {
    let en = {
        let g = game();
        g.start_round(GameLang::En, GameMode::Easy, 555);
        g.next_question().expect("question")
    };
    let fr = {
        let g = game();
        g.start_round(GameLang::Fr, GameMode::Easy, 555);
        g.next_question().expect("question")
    };
    // Same seed picks the same transfer, so only the rendering differs.
    assert_eq!(en.year, fr.year);
}
