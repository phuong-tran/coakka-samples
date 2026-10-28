//! Native Mac menu intents; runtime teardown stays with the application owner.
use tauri::menu::{Menu, MenuItem, PredefinedMenuItem as Native, Submenu, WINDOW_SUBMENU_ID};
use tauri::AppHandle;

pub const QUIT: &str = "coakka-sample-quit";

pub fn menu(app: &AppHandle) -> tauri::Result<Menu<tauri::Wry>> {
    // A native predefined Quit terminates Cocoa directly. An application menu
    // event instead joins accepted work through the same owner as window close.
    let quit = MenuItem::with_id(
        app,
        QUIT,
        "Quit CoAkka Tauri Intent",
        true,
        Some("CmdOrCtrl+Q"),
    )?;
    let application = Submenu::with_items(
        app,
        "CoAkka Tauri Intent",
        true,
        &[
            &Native::about(app, None, None)?,
            &Native::separator(app)?,
            &Native::services(app, None)?,
            &Native::separator(app)?,
            &Native::hide(app, None)?,
            &Native::hide_others(app, None)?,
            &Native::show_all(app, None)?,
            &Native::separator(app)?,
            &quit,
        ],
    )?;
    let edit = Submenu::with_items(
        app,
        "Edit",
        true,
        &[
            &Native::undo(app, None)?,
            &Native::redo(app, None)?,
            &Native::separator(app)?,
            &Native::cut(app, None)?,
            &Native::copy(app, None)?,
            &Native::paste(app, None)?,
            &Native::select_all(app, None)?,
        ],
    )?;
    let window = Submenu::with_id_and_items(
        app,
        WINDOW_SUBMENU_ID,
        "Window",
        true,
        &[
            &Native::minimize(app, None)?,
            &Native::maximize(app, None)?,
            &Native::fullscreen(app, None)?,
            &Native::separator(app)?,
            &Native::close_window(app, None)?,
        ],
    )?;
    Menu::with_items(app, &[&application, &edit, &window])
}
