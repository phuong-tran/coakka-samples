//! App-owned Windows browser subscriptions. COM borrows stay on their UI thread.
use crate::browser_gate::Gate;
use std::cell::RefCell;
use tauri::{AppHandle, Manager, WebviewWindow};
use webview2_com::{BrowserProcessExitedEventHandler, Microsoft::Web::WebView2::Win32::*};
use windows_core::Interface;

struct Subscription {
    environment: ICoreWebView2Environment5,
    token: i64,
    pid: u32,
}

thread_local! {
    static SUBSCRIPTIONS: RefCell<[Option<Subscription>; 2]> = const { RefCell::new([None, None]) };
}

fn finish(app: &AppHandle, ready: bool) {
    if ready {
        app.exit(crate::exit_code(app));
    }
}

pub fn owner_retired(app: &AppHandle) {
    finish(app, app.state::<Gate>().owner_retired());
}

fn retire(app: &AppHandle, role: usize, outcome: Result<(i32, u32), String>) {
    let subscription = SUBSCRIPTIONS.with(|slots| slots.borrow_mut()[role].take());
    let Some(subscription) = subscription else {
        crate::app_failed(app, "duplicate browser retirement");
        return;
    };
    // This later UI turn follows the active Invoke return. No callback may
    // destroy its own registration or move an environment to another thread.
    let removed = unsafe {
        subscription
            .environment
            .remove_BrowserProcessExited(subscription.token)
    };
    let expected_pid = subscription.pid;
    drop(subscription);
    if let Err(error) = removed {
        crate::app_failed(app, error);
        return;
    }
    match outcome {
        Ok((kind, pid))
            if kind == COREWEBVIEW2_BROWSER_PROCESS_EXIT_KIND_NORMAL.0 && pid == expected_pid =>
        {
            finish(app, app.state::<Gate>().retired(role));
        }
        Ok(_) => crate::app_failed(app, "abnormal or mismatched browser retirement"),
        Err(error) => crate::app_failed(app, error),
    }
}

pub fn attach(app: AppHandle, window: &WebviewWindow, role: usize) -> tauri::Result<()> {
    assert!(role < 2);
    window.with_webview(move |view| {
        let registered = (|| -> Result<(), Box<dyn std::error::Error>> {
            assert!(SUBSCRIPTIONS.with(|slots| slots.borrow()[role].is_none()));
            let environment: ICoreWebView2Environment5 = view.environment().cast()?;
            let mut pid = 0;
            // The borrowed controller and initialized native output remain in
            // this UI closure; only an owned environment survives registration.
            unsafe {
                view.controller()
                    .CoreWebView2()?
                    .BrowserProcessId(&mut pid)?
            };
            if pid == 0 {
                return Err("browser returned no process identity".into());
            }
            let callback_app = app.clone();
            let callback =
                BrowserProcessExitedEventHandler::create(Box::new(move |_sender, args| {
                    let outcome = (|| -> Result<(i32, u32), String> {
                        let args = args.ok_or_else(|| "missing browser exit data".to_string())?;
                        let mut kind = COREWEBVIEW2_BROWSER_PROCESS_EXIT_KIND_NORMAL;
                        let mut actual_pid = 0;
                        // Outputs are copied before this invocation returns. No
                        // event-argument/COM reference crosses the executor handoff.
                        unsafe {
                            args.BrowserProcessExitKind(&mut kind)
                                .map_err(|error| error.to_string())?;
                            args.BrowserProcessId(&mut actual_pid)
                                .map_err(|error| error.to_string())?;
                        }
                        Ok((kind.0, actual_pid))
                    })();
                    let app = callback_app.clone();
                    // Spawn never polls synchronously. Its worker queues a later
                    // UI turn, fencing callback lifetime before unregister/drop.
                    tauri::async_runtime::spawn(async move {
                        let retire_app = app.clone();
                        if let Err(error) =
                            app.run_on_main_thread(move || retire(&retire_app, role, outcome))
                        {
                            crate::app_failed(&app, error);
                        }
                    });
                    Ok(())
                }));
            let mut token = 0;
            // Failure publishes no subscription. Successful registration and
            // its owned token are installed before any deferred retirement.
            unsafe { environment.add_BrowserProcessExited(&callback, &mut token)? };
            SUBSCRIPTIONS.with(|slots| {
                slots.borrow_mut()[role] = Some(Subscription {
                    environment,
                    token,
                    pid,
                });
            });
            assert!(!app.state::<Gate>().attached(role));
            Ok(())
        })();
        if let Err(error) = registered {
            crate::app_failed(&app, error);
        }
    })
}
