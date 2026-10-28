//! Application blocking-work leases; never Core queue or cancellation policy.
use std::sync::{Arc, Condvar, Mutex};

pub struct Owner<T> {
    state: Mutex<State<T>>,
    changed: Condvar,
    limit: usize,
}

struct State<T> {
    value: Option<T>,
    active: usize,
    completed: u64,
    closing: bool,
    closed: bool,
}

#[derive(Debug, PartialEq, Eq)]
pub enum Refusal {
    Busy,
    Closed,
}

pub struct Lease<T> {
    owner: Arc<Owner<T>>,
    value: Option<T>,
}

impl<T: Clone> Owner<T> {
    pub fn new(value: T, limit: usize) -> Arc<Self> {
        assert!(limit > 0 && limit <= 8);
        Arc::new(Self {
            state: Mutex::new(State {
                value: Some(value),
                active: 0,
                completed: 0,
                closing: false,
                closed: false,
            }),
            changed: Condvar::new(),
            limit,
        })
    }

    pub fn admit(self: &Arc<Self>) -> Result<Lease<T>, Refusal> {
        let mut state = self.state.lock().expect("application admission lock");
        if state.closing {
            return Err(Refusal::Closed);
        }
        if state.active == self.limit {
            return Err(Refusal::Busy);
        }
        let value = state
            .value
            .as_ref()
            .expect("open application owner")
            .clone();
        state.active += 1;
        Ok(Lease {
            owner: self.clone(),
            value: Some(value),
        })
    }

    #[cfg(test)]
    pub fn snapshot(&self) -> (usize, u64, bool, bool) {
        let state = self.state.lock().expect("application admission lock");
        (state.active, state.completed, state.closing, state.closed)
    }

    #[cfg(test)]
    fn wait_closing(&self) {
        let mut state = self.state.lock().expect("application admission lock");
        while !state.closing {
            state = self
                .changed
                .wait(state)
                .expect("application seal observation");
        }
    }

    /// Blocking-executor only. Never call while holding a lease being joined.
    pub fn close(&self) {
        let mut state = self.state.lock().expect("application admission lock");
        if state.closing {
            while !state.closed {
                state = self.changed.wait(state).expect("application close wait");
            }
            return;
        }
        state.closing = true;
        self.changed.notify_all();
        while state.active != 0 {
            state = self.changed.wait(state).expect("application work wait");
        }
        let value = state.value.take();
        drop(state);
        // Last dispatcher Drop may join native work. It must finish outside
        // the admission lock and before the closed notification is published.
        drop(value);
        let mut state = self.state.lock().expect("application admission lock");
        state.closed = true;
        self.changed.notify_all();
    }
}

impl<T> Lease<T> {
    pub fn value(&self) -> &T {
        self.value.as_ref().expect("active application lease")
    }
}

impl<T> Drop for Lease<T> {
    fn drop(&mut self) {
        // Retire this runtime reference before another thread sees completion.
        drop(self.value.take());
        let mut state = self.owner.state.lock().expect("application admission lock");
        assert!(state.active > 0);
        state.active -= 1;
        state.completed = state
            .completed
            .checked_add(1)
            .expect("application count bound");
        self.owner.changed.notify_all();
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::{sync::mpsc, thread};

    #[test]
    fn finite_slots_retire_owner_before_closed() {
        let value = Arc::new(17);
        let weak = Arc::downgrade(&value);
        let owner = Owner::new(value, 2);
        let first = owner.admit().unwrap();
        assert_eq!(**first.value(), 17);
        let second = owner.admit().unwrap();
        assert!(matches!(owner.admit(), Err(Refusal::Busy)));
        drop(first);
        drop(owner.admit().unwrap());
        drop(second);
        assert_eq!(owner.snapshot(), (0, 3, false, false));
        owner.close();
        assert_eq!(owner.snapshot(), (0, 3, true, true));
        assert!(weak.upgrade().is_none());
        assert!(matches!(owner.admit(), Err(Refusal::Closed)));
        owner.close();
    }

    #[test]
    fn held_work_and_repeated_close_join_the_same_retirement() {
        let value = Arc::new(17);
        let weak = Arc::downgrade(&value);
        let owner = Owner::new(value, 1);
        let lease = owner.admit().unwrap();
        let (done, completed) = mpsc::channel();
        let closer = owner.clone();
        let worker = thread::spawn(move || {
            closer.close();
            done.send(()).unwrap();
        });
        owner.wait_closing();
        assert_eq!(owner.snapshot(), (1, 0, true, false));
        assert!(matches!(owner.admit(), Err(Refusal::Closed)));
        assert!(completed.try_recv().is_err());
        assert!(weak.upgrade().is_some());
        let repeated = owner.clone();
        let other = thread::spawn(move || repeated.close());
        drop(lease);
        completed.recv().unwrap();
        worker.join().unwrap();
        other.join().unwrap();
        assert!(weak.upgrade().is_none());
        assert_eq!(owner.snapshot(), (0, 1, true, true));
    }

    #[test]
    fn app_unwind_returns_its_slot() {
        let owner = Owner::new(Arc::new(17), 1);
        let lease = owner.admit().unwrap();
        assert!(thread::spawn(move || {
            let _lease = lease;
            panic!("controlled app unwind");
        })
        .join()
        .is_err());
        assert_eq!(owner.snapshot(), (0, 1, false, false));
        drop(owner.admit().unwrap());
        owner.close();
    }
}
