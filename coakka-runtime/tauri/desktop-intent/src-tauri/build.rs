fn main() {
    tauri_build::try_build(tauri_build::Attributes::new().app_manifest(
        tauri_build::AppManifest::new().commands(&["coakka_ask_intent", "coakka_runtime_facts"]),
    ))
    .expect("build explicit sample command permissions");
    let target = std::env::var("CARGO_CFG_TARGET_ENV").expect("compiler target environment");
    let profile = std::env::var("PROFILE").expect("compiler profile");
    if target == "msvc" && profile == "release" {
        // Rust's platform linker arguments can re-enable CodeView after the
        // strip profile. Harden this app target at link time, never by editing
        // its image. Keep the exact address map only in the private build cache.
        let output =
            std::path::PathBuf::from(std::env::var_os("OUT_DIR").expect("app symbol output"));
        println!("cargo:rustc-link-arg-bin=coakka-sample-tauri-desktop-intent=/DEBUG:NONE");
        println!(
            "cargo:rustc-link-arg-bin=coakka-sample-tauri-desktop-intent=/MAP:{}",
            output.join("coakka-desktop.map").display()
        );
    }
}
