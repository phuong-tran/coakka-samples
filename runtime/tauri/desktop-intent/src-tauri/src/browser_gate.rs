//! Join actual browser notifications with completed application-owner retirement.
use std::sync::Mutex;

#[derive(Default)]
struct State {
    attached: u8,
    retired: u8,
    owner_retired: bool,
    finished: bool,
}

pub struct Gate {
    state: Mutex<State>,
    expected: u8,
}

impl Gate {
    pub fn new(windows: usize) -> Self {
        // One main window; qualification may add one untrusted ACL window.
        // This is an app resource bound, never a native runtime capability.
        assert!((1..=2).contains(&windows));
        Self {
            state: Mutex::new(State::default()),
            expected: (1 << windows) - 1,
        }
    }

    fn change(&self, event: usize) -> bool {
        let mut state = self.state.lock().expect("browser lifetime gate");
        assert!(!state.finished);
        match event {
            0 | 1 => {
                let bit = 1 << event;
                assert_ne!(self.expected & bit, 0);
                assert_eq!(state.attached & bit, 0);
                state.attached |= bit;
            }
            2 | 3 => {
                let bit = 1 << (event - 2);
                assert_ne!(state.attached & bit, 0);
                assert_eq!(state.retired & bit, 0);
                state.retired |= bit;
            }
            4 => {
                assert!(!state.owner_retired);
                state.owner_retired = true;
            }
            _ => panic!("invalid browser lifetime event"),
        }
        state.finished = state.attached == self.expected
            && state.retired == self.expected
            && state.owner_retired;
        state.finished
    }

    pub fn attached(&self, role: usize) -> bool {
        assert!(role < 2);
        self.change(role)
    }

    pub fn retired(&self, role: usize) -> bool {
        assert!(role < 2);
        self.change(role + 2)
    }

    /// Called after actual accepted-work join, never inferred from UI closure.
    pub fn owner_retired(&self) -> bool {
        self.change(4)
    }

    pub fn finished(&self) -> bool {
        self.state.lock().expect("browser lifetime gate").finished
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::{sync::Arc, thread};

    fn permutations(order: &mut [usize], at: usize, visit: &mut impl FnMut(&[usize])) {
        if at == order.len() {
            visit(order);
            return;
        }
        for index in at..order.len() {
            order.swap(at, index);
            permutations(order, at + 1, visit);
            order.swap(at, index);
        }
    }

    #[test]
    fn every_legal_one_and_two_window_order_has_one_exit() {
        for windows in 1..=2 {
            let mut order = if windows == 1 {
                vec![0, 2, 4]
            } else {
                vec![0, 1, 2, 3, 4]
            };
            let mut legal = 0;
            permutations(&mut order, 0, &mut |order| {
                if (0..windows).any(|role| {
                    order.iter().position(|event| *event == role).unwrap()
                        > order.iter().position(|event| *event == role + 2).unwrap()
                }) {
                    return;
                }
                legal += 1;
                let gate = Gate::new(windows);
                for (index, event) in order.iter().enumerate() {
                    assert_eq!(gate.change(*event), index == order.len() - 1);
                    assert_eq!(gate.finished(), index == order.len() - 1);
                }
                assert!(std::panic::catch_unwind(|| gate.owner_retired()).is_err());
            });
            assert_eq!(legal, if windows == 1 { 3 } else { 30 });
        }
    }

    #[test]
    fn invalid_duplicate_and_early_notifications_refuse() {
        for windows in 1..=2 {
            for role in 0..windows {
                assert!(std::panic::catch_unwind(|| Gate::new(windows).retired(role)).is_err());
                let gate = Gate::new(windows);
                assert!(!gate.attached(role));
                assert!(std::panic::catch_unwind(|| gate.attached(role)).is_err());
                let gate = Gate::new(windows);
                gate.attached(role);
                assert!(!gate.retired(role));
                assert!(std::panic::catch_unwind(|| gate.retired(role)).is_err());
            }
            let gate = Gate::new(windows);
            assert!(!gate.owner_retired());
            assert!(std::panic::catch_unwind(|| gate.owner_retired()).is_err());
        }
        assert!(std::panic::catch_unwind(|| Gate::new(0)).is_err());
        assert!(std::panic::catch_unwind(|| Gate::new(3)).is_err());
        assert!(std::panic::catch_unwind(|| Gate::new(1).attached(1)).is_err());
        assert!(std::panic::catch_unwind(|| Gate::new(2).attached(2)).is_err());
        assert!(std::panic::catch_unwind(|| Gate::new(2).retired(2)).is_err());
    }

    #[test]
    fn concurrent_final_notifications_choose_one_exit_owner() {
        for windows in 1..=2 {
            let gate = Arc::new(Gate::new(windows));
            for role in 0..windows {
                assert!(!gate.attached(role));
            }
            let jobs: Vec<_> = (0..=windows)
                .map(|role| {
                    let gate = gate.clone();
                    thread::spawn(move || {
                        if role == windows {
                            gate.owner_retired()
                        } else {
                            gate.retired(role)
                        }
                    })
                })
                .collect();
            assert_eq!(
                jobs.into_iter()
                    .map(|job| job.join().unwrap())
                    .filter(|exit| *exit)
                    .count(),
                1
            );
            assert!(gate.finished());
        }
    }
}
