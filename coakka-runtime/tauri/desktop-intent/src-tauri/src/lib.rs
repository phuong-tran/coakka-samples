//! Desktop application policy; Core and the intent connector stay authoritative.
mod admission;
#[cfg(target_os = "windows")]
mod browser;
#[cfg(target_os = "windows")]
mod browser_gate;
#[cfg(target_os = "macos")]
mod mac_menu;

use admission::Owner;
use coakka_runtime::{ConnectorStartSpec, RouteSpec};
use coakka_tauri_intents::{
    coakka_ask_intent_command, IntentEnvelope, IntentError, IntentResult, RuntimeIntentDispatcher,
};
use serde_json::{json, Value};
use std::error::Error;
use std::path::PathBuf;
use std::sync::{
    atomic::{AtomicBool, Ordering},
    Arc,
};
use tauri::{Manager, RunEvent, State, WebviewUrl, WebviewWindowBuilder};

const TARGET: &str = "samples.tauri.desktop.echo";

struct Shutdown {
    started: AtomicBool,
    retired: AtomicBool,
    failed: AtomicBool,
}

impl Shutdown {
    fn is_retired(&self) -> bool {
        self.retired.load(Ordering::Acquire)
    }
}

struct AppState {
    owner: Arc<Owner<RuntimeIntentDispatcher>>,
    shutdown: Arc<Shutdown>,
    facts: Value,
}

#[derive(Default)]
struct Resources {
    // Absence uses the normal app cache. The optional launch-time choice owns
    // browser data only, never Core/native selection or capability discovery.
    browser_data_dir: Option<PathBuf>,
}

fn resources() -> Result<Resources, Box<dyn Error>> {
    let mut args = std::env::args_os().skip(1);
    let Some(option) = args.next() else {
        return Ok(Resources::default());
    };
    if option != "--browser-data-dir" {
        return Err("unknown application argument".into());
    }
    let directory = PathBuf::from(args.next().ok_or("missing browser data directory")?);
    if !directory.is_absolute() || args.next().is_some() {
        return Err("one absolute browser data directory required".into());
    }
    Ok(Resources {
        browser_data_dir: Some(directory),
    })
}

struct AppLog;
static APP_LOG: AppLog = AppLog;
impl log::Log for AppLog {
    fn enabled(&self, metadata: &log::Metadata<'_>) -> bool {
        metadata.level() <= log::Level::Warn
    }
    fn log(&self, record: &log::Record<'_>) {
        if self.enabled(record.metadata()) {
            eprintln!(
                "coakka sample framework {} {} {}",
                record.level(),
                record.target(),
                record.args()
            );
        }
    }
    fn flush(&self) {}
}

#[tauri::command]
async fn coakka_ask_intent(
    state: State<'_, AppState>,
    intent: IntentEnvelope,
) -> Result<IntentResult, IntentError> {
    // Framework ACL authorizes before this body. Application capacity is not
    // a mirrored Core queue/default and refusal never submits native work.
    let lease = state.owner.admit().map_err(|reason| {
        IntentError::application(format!("application admission: {reason:?}"), None)
    })?;
    tauri::async_runtime::spawn_blocking(move || coakka_ask_intent_command(lease.value(), intent))
        .await
        .map_err(|error| IntentError::application(format!("application executor: {error}"), None))?
}

#[tauri::command]
fn coakka_runtime_facts(state: State<'_, AppState>) -> Value {
    state.facts.clone()
}

fn create_state() -> Result<AppState, Box<dyn Error>> {
    let mut intent = ConnectorStartSpec::new("tauri-desktop-sample", "tauri-desktop-node");
    intent.routes = vec![RouteSpec::single_local(TARGET, "local", 0)];
    #[cfg(target_os = "macos")]
    let dispatcher = {
        // The app packager owns this bundle location. Any present override,
        // including empty/non-Unicode input, stays entirely SDK-authoritative.
        let path =
            macos_runtime_library_intent(std::env::var_os("COAKKA_RUNTIME_LIB").is_some(), || {
                Ok(std::env::current_exe()?.canonicalize()?)
            })?;
        let runtime = match path {
            None => coakka_runtime::RuntimeHost::start(intent)?,
            Some(path) => coakka_runtime::RuntimeHost::start_with_runtime_lib(intent, path)?,
        };
        RuntimeIntentDispatcher::new(runtime)
    };
    #[cfg(not(target_os = "macos"))]
    let dispatcher = RuntimeIntentDispatcher::start(intent)?;
    dispatcher.register_json_handler(TARGET, |request| {
        Ok(json!({"handledBy": "tauri-rust-host", "operation": request.operation, "echo": request.payload}))
    })?;
    let info = dispatcher.runtime_info()?;
    let startup = dispatcher.runtime_startup_config()?;
    let delivery = dispatcher.runtime_delivery_config()?;
    let facts = json!({"runtime": info.runtime_version, "commit": info.git_commit, "abi": info.abi_version,
        "requestedCpuPolicy": startup.execution.requested_policy, "connectionMode": startup.connection.mode.0,
        "monitorEnabled": startup.monitor.enabled, "requestMode": delivery.request_mode,
        "responseMode": delivery.response_mode, "defaultedGroups": startup.defaulted_groups.to_string()});
    Ok(AppState {
        owner: Owner::new(dispatcher, 8),
        facts,
        shutdown: Arc::new(Shutdown {
            started: AtomicBool::new(false),
            retired: AtomicBool::new(false),
            failed: AtomicBool::new(false),
        }),
    })
}

#[cfg(target_os = "macos")]
fn macos_runtime_library_intent(
    override_present: bool,
    executable: impl FnOnce() -> Result<PathBuf, Box<dyn Error>>,
) -> Result<Option<String>, Box<dyn Error>> {
    if override_present {
        return Ok(None);
    }
    Ok(Some(macos_bundle_runtime_path(&executable()?)?))
}

#[cfg(target_os = "macos")]
fn macos_bundle_runtime_path(executable: &std::path::Path) -> Result<String, Box<dyn Error>> {
    let directory = executable.parent().ok_or("missing application directory")?;
    let contents = directory.parent().ok_or("missing application contents")?;
    let bundle = contents.parent().ok_or("missing application bundle")?;
    if directory.file_name() != Some(std::ffi::OsStr::new("MacOS"))
        || contents.file_name() != Some(std::ffi::OsStr::new("Contents"))
        || bundle.extension() != Some(std::ffi::OsStr::new("app"))
    {
        return Err("expected the packaged application bundle layout".into());
    }
    Ok(contents
        .join("Frameworks/libcoakka_runtime_v2.dylib")
        .to_str()
        .ok_or("application bundle path is not valid text")?
        .to_owned())
}

pub(crate) fn exit_code(app: &tauri::AppHandle) -> i32 {
    i32::from(
        app.state::<AppState>()
            .shutdown
            .failed
            .load(Ordering::Acquire),
    )
}

pub(crate) fn app_failed(app: &tauri::AppHandle, error: impl std::fmt::Display) {
    // A framework/resource failure remains an app failure, never a Core status.
    eprintln!("coakka sample application failure: {error}");
    app.state::<AppState>()
        .shutdown
        .failed
        .store(true, Ordering::Release);
    if app.state::<AppState>().shutdown.is_retired() {
        // No accepted work remains to join after a late browser/close failure.
        app.exit(1);
    } else {
        begin_shutdown(app.clone());
    }
}

fn begin_shutdown(app: tauri::AppHandle) {
    let state = app.state::<AppState>();
    if state.shutdown.started.swap(true, Ordering::AcqRel) {
        return;
    }
    let owner = state.owner.clone();
    let shutdown = state.shutdown.clone();
    tauri::async_runtime::spawn_blocking(move || {
        // Close joins every accepted lease before the final dispatcher Drop.
        // A vanished UI future does not cancel an accepted Core operation.
        retire_application_work(&owner, &shutdown, || {
            println!("coakka sample owned work retired");
        });
        #[cfg(target_os = "windows")]
        browser::owner_retired(&app);
        let close_app = app.clone();
        if let Err(error) = app.run_on_main_thread(move || {
            if let Some(window) = close_app.get_webview_window("main") {
                if let Err(error) = window.close() {
                    app_failed(&close_app, error);
                }
            } else {
                #[cfg(not(target_os = "windows"))]
                close_app.exit(exit_code(&close_app));
                #[cfg(target_os = "windows")]
                if exit_code(&close_app) != 0 {
                    close_app.exit(1);
                }
            }
        }) {
            app_failed(&app, error);
        }
    });
}

fn retire_application_work<T: Clone>(owner: &Owner<T>, shutdown: &Shutdown, record: impl FnOnce()) {
    owner.close();
    record();
    // Owner.closed precedes this continuation. Only this later publication
    // admits framework exit, so a repeated close cannot cut cleanup short.
    shutdown.retired.store(true, Ordering::Release);
}

fn allow_navigation(url: &tauri::Url) -> bool {
    ((url.scheme() == "tauri" && url.host_str() == Some("localhost"))
        || (url.scheme() == "http" && url.host_str() == Some("tauri.localhost")))
        // The pinned framework maps index.html to tauri://localhost (empty
        // path) on Mac/Linux, and to the HTTP root on Windows.
        && matches!(url.path(), "" | "/" | "/index.html")
        && url.username().is_empty()
        && url.password().is_none()
        && url.port().is_none()
        && url.query().is_none()
        && url.fragment().is_none()
}

fn initialize(app: &mut tauri::App, resources: Resources) -> Result<(), Box<dyn Error>> {
    // Browser storage is an app resource, separate from immutable installed
    // code/native images. This is not a Core capability or native cache probe.
    let storage = match resources.browser_data_dir {
        Some(directory) => directory,
        None => app.path().app_cache_dir()?.join("webview"),
    };
    std::fs::create_dir_all(&storage)?;
    let window = WebviewWindowBuilder::new(app, "main", WebviewUrl::App("index.html".into()));
    // The pinned browser default adds a SmartScreen-disabling argument.
    // Supply no extra arguments; no browser/OS security bypass is permitted.
    #[cfg(target_os = "windows")]
    let window = window.additional_browser_args("");
    let window = window
        .title("CoAkka Tauri Intent")
        .inner_size(900.0, 680.0)
        .visible(true)
        .devtools(false)
        .incognito(true)
        .data_directory(storage)
        .on_navigation(allow_navigation)
        .on_new_window(|_, _| tauri::webview::NewWindowResponse::Deny)
        .build()?;
    #[cfg(target_os = "windows")]
    browser::attach(app.handle().clone(), &window, 0)?;
    #[cfg(not(target_os = "windows"))]
    let _ = window;
    Ok(())
}

#[cfg(test)]
mod navigation_tests {
    #[test]
    fn only_the_framework_local_entrypoint_is_admitted() {
        for entry in [
            "tauri://localhost",
            "tauri://localhost/",
            "tauri://localhost/index.html",
            "http://tauri.localhost",
            "http://tauri.localhost/index.html",
        ] {
            assert!(
                super::allow_navigation(&tauri::Url::parse(entry).unwrap()),
                "{entry}"
            );
        }
        for entry in [
            "https://tauri.localhost/",
            "http://localhost/",
            "tauri://elsewhere/",
            "tauri://localhost/other.html",
            "tauri://localhost/?query",
            "tauri://localhost/#fragment",
            "tauri://user@localhost/",
            "tauri://localhost:123/",
            "https://example.com/",
        ] {
            assert!(
                !super::allow_navigation(&tauri::Url::parse(entry).unwrap()),
                "{entry}"
            );
        }
    }
}

/// Starts the foreground application. The framework compiler graph is app-owned.
pub fn run() -> Result<(), Box<dyn Error>> {
    let resources = resources()?;
    log::set_logger(&APP_LOG).map_err(|error| format!("application logger: {error}"))?;
    log::set_max_level(log::LevelFilter::Warn);
    let state = create_state()?;
    // Retain only the app's exit facts across framework teardown, not Core or
    // a dispatcher. Some platform event loops return zero despite exit(1).
    let shutdown = state.shutdown.clone();
    let builder = tauri::Builder::default().manage(state);
    #[cfg(target_os = "macos")]
    let builder = builder.menu(mac_menu::menu).on_menu_event(|app, event| {
        if event.id().as_ref() == mac_menu::QUIT {
            begin_shutdown(app.clone());
        }
    });
    #[cfg(target_os = "windows")]
    let builder = builder.manage(browser_gate::Gate::new(1));
    let app = builder
        .invoke_handler(tauri::generate_handler![
            coakka_ask_intent,
            coakka_runtime_facts
        ])
        .on_window_event(|window, event| {
            if window.label() == "main" && matches!(event, tauri::WindowEvent::Destroyed) {
                let app = window.app_handle();
                begin_shutdown(app.clone());
                if app.state::<AppState>().shutdown.is_retired() {
                    #[cfg(not(target_os = "windows"))]
                    app.exit(exit_code(app));
                    #[cfg(target_os = "windows")]
                    if exit_code(app) != 0 {
                        app.exit(1);
                    }
                }
            }
        })
        .setup(move |app| {
            // Returning a setup error makes the pinned framework panic across
            // its event callback. Retire accepted work through our app owner
            // instead; this is never reclassified as a Core startup outcome.
            if let Err(error) = initialize(app, resources) {
                app_failed(app.handle(), error);
            }
            Ok(())
        })
        .build(tauri::generate_context!())?;
    let framework_code = app.run_return(|app, event| {
        if let RunEvent::ExitRequested { api, code, .. } = event {
            if code.is_some_and(|value| value != 0) {
                app.state::<AppState>()
                    .shutdown
                    .failed
                    .store(true, Ordering::Release);
            }
            if !app.state::<AppState>().shutdown.is_retired() {
                api.prevent_exit();
                begin_shutdown(app.clone());
            }
            #[cfg(target_os = "windows")]
            if exit_code(app) == 0 && !app.state::<browser_gate::Gate>().finished() {
                api.prevent_exit();
            }
        }
    });
    application_result(&shutdown, framework_code)
}

fn application_result(shutdown: &Shutdown, framework_code: i32) -> Result<(), Box<dyn Error>> {
    if shutdown.failed.load(Ordering::Acquire) {
        return Err("desktop application failed".into());
    }
    if framework_code != 0 {
        return Err(format!("desktop event loop exited with code {framework_code}").into());
    }
    if !shutdown.is_retired() {
        return Err("desktop event loop ended before application work retired".into());
    }
    Ok(())
}

#[cfg(test)]
mod application_exit_tests {
    use super::*;

    #[test]
    fn app_failure_survives_zero_framework_exit_and_teardown() {
        let shutdown = Arc::new(Shutdown {
            started: AtomicBool::new(false),
            retired: AtomicBool::new(false),
            failed: AtomicBool::new(false),
        });
        assert!(application_result(&shutdown, 0).is_err());
        shutdown.started.store(true, Ordering::Release);
        shutdown.retired.store(true, Ordering::Release);
        assert!(application_result(&shutdown, 0).is_ok());
        assert!(application_result(&shutdown, 1).is_err());
        assert!(application_result(&shutdown, -1).is_err());
        let retained = shutdown.clone();
        shutdown.failed.store(true, Ordering::Release);
        drop(shutdown);
        assert!(application_result(&retained, 0).is_err());
    }

    #[test]
    fn owner_closed_does_not_admit_exit_before_retirement_continuation() {
        use std::{sync::mpsc, thread, time::Duration};
        let owner = Owner::new(Arc::new(17), 1);
        let shutdown = Arc::new(Shutdown {
            started: AtomicBool::new(true),
            retired: AtomicBool::new(false),
            failed: AtomicBool::new(false),
        });
        let (closed, observed) = mpsc::channel();
        let (resume, proceed) = mpsc::channel();
        let worker_owner = owner.clone();
        let worker_shutdown = shutdown.clone();
        let worker = thread::spawn(move || {
            retire_application_work(&worker_owner, &worker_shutdown, || {
                closed.send(()).unwrap();
                proceed.recv_timeout(Duration::from_secs(2)).unwrap();
            });
        });
        observed.recv_timeout(Duration::from_secs(2)).unwrap();
        assert!(owner.snapshot().3);
        // These are the same admission fact and final-result check used by
        // repeated framework exit, window destruction and failure callbacks.
        assert!(!shutdown.is_retired());
        assert!(application_result(&shutdown, 0).is_err());
        resume.send(()).unwrap();
        worker.join().unwrap();
        assert!(shutdown.is_retired());
        assert!(application_result(&shutdown, 0).is_ok());
    }

    #[cfg(target_os = "macos")]
    #[test]
    fn packaged_runtime_path_is_exact_without_probing_or_fallback() {
        use std::path::Path;
        assert_eq!(
            macos_bundle_runtime_path(Path::new(
                "/app có dấu 测试/Intent.app/Contents/MacOS/application"
            ))
            .unwrap(),
            "/app có dấu 测试/Intent.app/Contents/Frameworks/libcoakka_runtime_v2.dylib"
        );
        for path in [
            "/app/application",
            "/app/Intent.app/Contents/application",
            "/app/Intent/Contents/MacOS/application",
        ] {
            assert!(macos_bundle_runtime_path(Path::new(path)).is_err());
        }
        assert!(
            macos_runtime_library_intent(true, || panic!("override must stay with SDK"))
                .unwrap()
                .is_none()
        );
        assert_eq!(
            macos_runtime_library_intent(false, || Ok(PathBuf::from(
                "/app/Intent.app/Contents/MacOS/application"
            )))
            .unwrap(),
            Some("/app/Intent.app/Contents/Frameworks/libcoakka_runtime_v2.dylib".to_owned())
        );
    }
}
