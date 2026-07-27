//! CLI: validate the CSV dataset and generate the bundled SQLite DB.
//!
//! Usage:
//!   gen-db <data-dir> <out.db>    load + validate + write the DB
//!   gen-db <data-dir> --check     load + validate only (CI integrity gate)

use std::path::Path;
use std::process::ExitCode;

fn main() -> ExitCode {
    let args: Vec<String> = std::env::args().skip(1).collect();
    let (dir, out) = match args.as_slice() {
        [dir, out] => (Path::new(dir), out.as_str()),
        _ => {
            eprintln!("usage: gen-db <data-dir> <out.db | --check>");
            return ExitCode::from(2);
        }
    };

    let corpus = match mercato_data::load_corpus(dir) {
        Ok(c) => c,
        Err(e) => {
            eprintln!("dataset invalid:\n{e}");
            return ExitCode::FAILURE;
        }
    };
    let aliases: usize = corpus.players.iter().map(|p| p.aliases.len()).sum();
    println!(
        "dataset OK: {} clubs, {} players, {} aliases, {} transfers",
        corpus.clubs.len(),
        corpus.players.len(),
        aliases,
        corpus.transfers.len()
    );

    if out == "--check" {
        return ExitCode::SUCCESS;
    }
    match mercato_data::generate_db(&corpus, Path::new(out)) {
        Ok(()) => {
            println!("bundled DB written to {out}");
            ExitCode::SUCCESS
        }
        Err(e) => {
            eprintln!("DB generation failed: {e}");
            ExitCode::FAILURE
        }
    }
}
