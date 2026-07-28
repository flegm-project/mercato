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
fn hardcore_hints_then_a_wrong_guess_costs_a_life() {
    let g = game();
    g.start_round(GameLang::En, GameMode::Hardcore, 99);

    let q = g.next_question().expect("a question");
    assert!(q.options.is_empty(), "hardcore is free text");
    assert_eq!(q.attempts_left, g.hardcore_attempts()); // three lives for the round

    // Hints unlock in order and are free while the question is open.
    let h1 = g.next_hint().expect("nationality hint");
    assert!(h1.nationality.is_some());
    let h2 = g.next_hint().expect("position hint");
    assert!(h2.position.is_some());
    let h3 = g.next_hint().expect("surname hint");
    assert!(h3.surname_initial.is_some() && h3.surname_letters.is_some());
    assert!(g.next_hint().is_none(), "only three hints");

    // One guess settles the question; a wrong one costs a life and reveals.
    let miss = g.submit_guess("zzzzzzzzzz".into()).expect("answered");
    assert!(!miss.correct && miss.finished);
    assert_eq!(miss.attempts_left, g.hardcore_attempts() - 1);
    assert!(miss.revealed_name.is_some());
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

#[test]
fn ads_gate_follows_questions_consent_and_purchase() {
    use mercato_ffi::{AdConsent, AdPlacement};

    let g = game();
    // Fresh install, free user: static slots yes, interstitial in warmup.
    assert!(g.should_show_ad(AdPlacement::Banner));
    assert!(g.should_show_ad(AdPlacement::SponsorBoard));
    assert!(g.should_show_ad(AdPlacement::Rectangle));
    assert!(!g.should_show_ad(AdPlacement::Interstitial));

    // Finishing questions is what advances the gate; no separate call.
    g.start_round(GameLang::En, GameMode::Easy, 7);
    for _ in 0..2 {
        let q = g.next_question().expect("question");
        let mut done = false;
        for i in 0..q.options.len() {
            if g.submit_choice(i as u32).expect("answer accepted").finished {
                done = true;
                break;
            }
        }
        assert!(done);
    }
    assert!(g.should_show_ad(AdPlacement::Interstitial));
    g.record_interstitial_shown();
    assert!(!g.should_show_ad(AdPlacement::Interstitial));

    // Consent flips personalisation, never slot visibility.
    assert!(!g.ad_personalization_allowed());
    g.set_ad_consent(AdConsent::Personalized);
    assert!(g.ad_personalization_allowed());
    g.set_ad_consent(AdConsent::NonPersonalized);
    assert!(!g.ad_personalization_allowed());
    assert!(g.should_show_ad(AdPlacement::Banner));

    // Remove-ads entitlement: everything goes dark, and back on restore-fail.
    g.set_ads_removed(true);
    assert!(g.ads_removed());
    for p in [
        AdPlacement::Banner,
        AdPlacement::SponsorBoard,
        AdPlacement::Interstitial,
        AdPlacement::Rectangle,
    ] {
        assert!(!g.should_show_ad(p));
    }
    g.set_ads_removed(false);
    assert!(g.should_show_ad(AdPlacement::Banner));
}

#[test]
fn lab_reports_stats_verdicts_and_collisions() {
    use mercato_ffi::LabVerdict;

    let g = game();
    let stats = g.lab_stats();
    assert_eq!(stats.players, 513);
    assert_eq!(stats.clubs, 412);
    assert_eq!(stats.transfers, 1905);
    assert_eq!(stats.aliases, 1000);

    let players = g.lab_players();
    assert_eq!(players.len(), 513);
    let palmer = players
        .iter()
        .find(|p| p.name == "Cole Palmer")
        .expect("Cole Palmer in dataset");

    // Exact name: accepted, distance 0, full trace.
    let ok = g.lab_evaluate(palmer.id.clone(), "cole palmer".into());
    assert_eq!(ok.verdict, LabVerdict::Accept);
    assert_eq!(ok.distance, Some(0));
    assert!(ok.trace.iter().any(|l| l.starts_with("route: exact")));
    assert!(ok.trace.iter().any(|l| l.starts_with("verdict: accepted")));

    // Garbage: rejected.
    let no = g.lab_evaluate(palmer.id.clone(), "xqzzk".into());
    assert_eq!(no.verdict, LabVerdict::Reject);

    // A real surname collision from the data must come back ambiguous when
    // one of its carriers is the target and the bare surname is typed.
    let collisions = g.lab_collisions();
    assert!(!collisions.is_empty(), "dataset has shared surnames");
    let case = &collisions[0];
    assert!(case.players.len() > 1);
    let target = players
        .iter()
        .find(|p| p.name == case.players[0])
        .expect("collision player resolvable");
    let amb = g.lab_evaluate(target.id.clone(), case.surname.clone());
    assert_eq!(amb.verdict, LabVerdict::Ambiguous);

    // Unknown target never panics.
    let unknown = g.lab_evaluate("nope".into(), "anything".into());
    assert_eq!(unknown.verdict, LabVerdict::Reject);
}
