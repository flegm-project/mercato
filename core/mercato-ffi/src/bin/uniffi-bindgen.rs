//! Binding generator entry point. Generates the Swift and Kotlin sources the
//! native apps compile against:
//!
//! ```sh
//! cargo run --bin uniffi-bindgen -- generate --library <lib> --language swift --out-dir <dir>
//! ```
fn main() {
    uniffi::uniffi_bindgen_main()
}
