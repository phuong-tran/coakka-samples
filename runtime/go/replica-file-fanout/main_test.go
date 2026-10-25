package main

import (
	"errors"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"
	"testing"

	connector "github.com/phuong-tran/coakka-runtime-go"
)

func sampleRoot(t *testing.T) string {
	t.Helper()
	work := os.Getenv("COAKKA_SAMPLE_WORK_ROOT")
	if !filepath.IsAbs(work) {
		t.Fatal("explicit external test work root required")
	}
	root, err := os.MkdirTemp(work, "coakka-file-test-")
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() {
		if err := os.RemoveAll(root); err != nil {
			t.Error(err)
		}
	})
	return root
}

type sourceWitness struct{ count, maximum int }

func (w *sourceWitness) Write(data []byte) (int, error) {
	w.maximum = max(w.maximum, len(data))
	for index, value := range data {
		if value != byte((w.count+index)*31+17) {
			return 0, errors.New("fixture pattern differs")
		}
	}
	w.count += len(data)
	return len(data), nil
}

type refusedWriter struct{ failure error }

func (w refusedWriter) Write(data []byte) (int, error) { return len(data) - 1, w.failure }

func TestSourceBoundPatternAndShortIO(t *testing.T) {
	var witness sourceWitness
	if err := writeSource(&witness); err != nil || witness.count != 9437915 || witness.maximum != 64*1024 {
		t.Fatalf("source: %+v %v", witness, err)
	}
	if err := writeSource(refusedWriter{}); !errors.Is(err, io.ErrShortWrite) {
		t.Fatal(err)
	}
	failure := errors.New("source write refused")
	if err := writeSource(refusedWriter{failure}); !errors.Is(err, failure) {
		t.Fatal(err)
	}
}

type fakeRecord struct {
	sequence uint64
	terminal bool
}

type fakeLane struct {
	mode         string
	injected     error
	owner        string
	records      map[string]*fakeRecord
	closed       int
	forgets      int
	cancels      int
	submits      int
	waits        int
	destinations []string
}

func (l *fakeLane) PrepareReceiveGrant(spec connector.FileReceiveSpec) (connector.FileReceiveGrant, error) {
	if l.mode == "prepare" {
		return connector.FileReceiveGrant{}, l.injected
	}
	l.records[spec.TransferID] = &fakeRecord{}
	l.destinations = append(l.destinations, spec.DestinationPath)
	return connector.FileReceiveGrant{
		Owner:      connector.LaneOwnerEndpoint{OwnerInstanceID: l.owner, AdvertisedHost: "127.0.0.1", Port: 12345},
		TransferID: spec.TransferID, AuthorizationToken: spec.AuthorizationToken,
		ExpectedSize: spec.ExpectedSize, ExpectedSHA256: spec.ExpectedSHA256,
	}, nil
}

func (l *fakeLane) SubmitSend(spec connector.FileSendSpec) error {
	l.submits++
	if strings.Contains(spec.TransferID, "replica-file-2-") && l.mode == "send" {
		return l.injected
	}
	l.records[spec.TransferID] = &fakeRecord{}
	return nil
}

func (l *fakeLane) WaitTransfer(id string, direction connector.FileTransferDirection, sequence uint64, timeout uint32) (connector.FileTransferSnapshot, error) {
	l.waits++
	if timeout != 30000 {
		return connector.FileTransferSnapshot{}, errors.New("native wait timeout changed")
	}
	record, found := l.records[id]
	if !found {
		return connector.FileTransferSnapshot{}, errors.New("wait on unaccepted record")
	}
	if strings.Contains(id, "replica-file-2-") && l.mode == "wait" {
		return connector.FileTransferSnapshot{}, l.injected
	}
	if l.mode == "never-terminal" {
		if sequence != record.sequence {
			return connector.FileTransferSnapshot{}, errors.New("wait did not use issued sequence")
		}
		record.sequence++
		return connector.FileTransferSnapshot{UpdateSequence: record.sequence}, nil
	}
	if !record.terminal && sequence == 0 {
		record.sequence = 9
		return connector.FileTransferSnapshot{UpdateSequence: 9}, nil
	}
	if !record.terminal && sequence != record.sequence {
		return connector.FileTransferSnapshot{}, errors.New("wait did not use issued sequence")
	}
	record.terminal = true
	snapshot := connector.FileTransferSnapshot{Direction: direction, State: connector.FileTransferCompleted,
		Result: connector.FileResultOK, UpdateSequence: record.sequence, TerminalMonoNS: 1}
	if strings.Contains(id, "replica-file-2-") && l.mode == "terminal" {
		snapshot.State, snapshot.Result = connector.FileTransferFailed, connector.FileResultStorageIO
	}
	return snapshot, nil
}

func (l *fakeLane) Cancel(id string, _ connector.FileTransferDirection) error {
	l.cancels++
	if l.mode == "cancel" {
		return l.injected
	}
	if record := l.records[id]; record != nil {
		record.terminal = true
	}
	return nil
}

func (l *fakeLane) Forget(id string, _ connector.FileTransferDirection) error {
	record := l.records[id]
	if record == nil || !record.terminal {
		return errors.New("forget without terminal witness")
	}
	if l.mode == "forget" {
		return l.injected
	}
	l.forgets++
	delete(l.records, id)
	return nil
}

func (l *fakeLane) Close() error {
	l.closed++
	if l.mode == "close" {
		return l.injected
	}
	clear(l.records)
	return nil
}

func TestAllOwnersIndependentAndJoinedOnEveryBoundary(t *testing.T) {
	for _, mode := range []string{"success", "open", "prepare", "handoff", "mismatch", "send", "send-wait", "send-terminal", "receive-wait", "receive-terminal", "digest", "cancel", "forget", "close", "sender-close"} {
		t.Run(mode, func(t *testing.T) {
			root := sampleRoot(t)
			injected := fmt.Errorf("injected %s", mode)
			sender := &fakeLane{records: make(map[string]*fakeRecord), injected: injected}
			var lanes []*fakeLane
			if mode == "send" || mode == "sender-close" || mode == "send-wait" || mode == "send-terminal" {
				sender.mode = strings.TrimPrefix(mode, "send-")
				if mode == "sender-close" {
					sender.mode = "close"
				}
			}
			opens, verified := 0, 0
			digest := connector.FileDigest{Size: sourceBytes, SHA256: [32]byte{17}}
			operations := fileOperations{
				open: func(owner *connector.LaneOwnerConfig) (fileLane, error) {
					if owner == nil {
						return sender, nil
					}
					opens++
					if owner.AdvertisedHost != "127.0.0.1" {
						t.Fatal("changed direct-owner topology")
					}
					if opens == 2 && mode == "open" {
						return nil, injected
					}
					lane := &fakeLane{owner: owner.OwnerInstanceID, injected: injected, records: make(map[string]*fakeRecord)}
					if opens == 2 {
						lane.mode = strings.TrimPrefix(mode, "receive-")
					}
					lanes = append(lanes, lane)
					return lane, nil
				},
				digest: func(path string) (connector.FileDigest, error) {
					if filepath.Base(path) != "source.bin" {
						verified++
					}
					if filepath.Base(path) == "billing-2.bin" && mode == "digest" {
						return connector.FileDigest{}, injected
					}
					return digest, nil
				},
				handoff: func(grant connector.FileReceiveGrant) (connector.FileReceiveGrant, error) {
					if strings.Contains(grant.TransferID, "replica-file-2-") {
						if mode == "handoff" {
							return connector.FileReceiveGrant{}, injected
						}
						if mode == "mismatch" {
							grant.Owner.Port++
						}
					}
					return handoffGrant(grant)
				},
			}
			err := runFanout(root, operations)
			if (mode == "success") != (err == nil) {
				t.Fatalf("%s: %v", mode, err)
			}
			if mode != "success" && mode != "mismatch" && !strings.HasSuffix(mode, "terminal") && !errors.Is(err, injected) {
				t.Fatalf("operation cause lost: %v", err)
			}
			if opens != 3 || verified < 2 || sender.closed != 1 {
				t.Fatalf("early abort: opens=%d verified=%d close=%d", opens, verified, sender.closed)
			}
			for _, lane := range lanes {
				if lane.closed != 1 || (lane.mode != "close" && len(lane.records) != 0) {
					t.Fatalf("receiver not retired: %+v", lane)
				}
			}
			_, statErr := os.Stat(root)
			retained := mode == "close" || mode == "sender-close"
			if retained && statErr != nil || !retained && !os.IsNotExist(statErr) {
				t.Fatalf("staging retention: retained=%t err=%v", retained, statErr)
			}
		})
	}
}

func TestWaitBoundAndNoForgetWithoutTerminal(t *testing.T) {
	lane := &fakeLane{mode: "never-terminal", records: map[string]*fakeRecord{"record": {}}}
	if _, err := waitTerminal(lane, "record", connector.FileTransferReceive); err == nil || lane.waits != 64 {
		t.Fatalf("normal wait bound: calls=%d err=%v", lane.waits, err)
	}
	lane.records["record"].sequence = 0
	if err := retire(lane, "record", connector.FileTransferReceive); err == nil || lane.forgets != 0 || lane.cancels != 1 || lane.waits != 128 {
		t.Fatalf("unobserved retirement: calls=%d err=%v", lane.waits, err)
	}
}

func TestNativePartialHandoffRetiresAllOwners(t *testing.T) {
	root := sampleRoot(t)
	operations := nativeOperations()
	handoff := operations.handoff
	digest := operations.digest
	count, verified := 0, 0
	operations.digest = func(path string) (connector.FileDigest, error) {
		value, err := digest(path)
		if err == nil && filepath.Base(path) != "source.bin" {
			verified++
		}
		return value, err
	}
	injected := errors.New("authenticated control plane refused second owner")
	operations.handoff = func(grant connector.FileReceiveGrant) (connector.FileReceiveGrant, error) {
		count++
		if count == 2 {
			return connector.FileReceiveGrant{}, injected
		}
		return handoff(grant)
	}
	if err := runFanout(root, operations); !errors.Is(err, injected) {
		t.Fatalf("partial failure lost: %v", err)
	}
	if count != 3 || verified != 2 {
		t.Fatalf("independent native owners: handoffs=%d verified=%d", count, verified)
	}
	if _, err := os.Stat(root); !os.IsNotExist(err) {
		t.Fatal("joined native cleanup left staging")
	}
}

func TestEarlyFailureStillRemovesAppOwnedSource(t *testing.T) {
	for _, failDigest := range []bool{false, true} {
		root := sampleRoot(t)
		injected := errors.New("source admission refused")
		operations := fileOperations{
			digest: func(string) (connector.FileDigest, error) {
				if failDigest {
					return connector.FileDigest{}, injected
				}
				return connector.FileDigest{Size: sourceBytes}, nil
			},
			open: func(*connector.LaneOwnerConfig) (fileLane, error) { return nil, injected },
		}
		if err := runFanout(root, operations); !errors.Is(err, injected) {
			t.Fatal(err)
		}
		if _, err := os.Stat(root); !os.IsNotExist(err) {
			t.Fatal("early failure left app source")
		}
	}
}
