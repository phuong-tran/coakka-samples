package main

import (
	"crypto/rand"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"os"
	"path/filepath"

	connector "github.com/phuong-tran/coakka-runtime-go"
)

const sourceBytes = 9*1024*1024 + 731

// This seam belongs to this application's cleanup/failure tests, not a new
// connector abstraction. Core still owns every state transition and I/O policy.
type fileLane interface {
	PrepareReceiveGrant(connector.FileReceiveSpec) (connector.FileReceiveGrant, error)
	SubmitSend(connector.FileSendSpec) error
	WaitTransfer(string, connector.FileTransferDirection, uint64, uint32) (connector.FileTransferSnapshot, error)
	Cancel(string, connector.FileTransferDirection) error
	Forget(string, connector.FileTransferDirection) error
	Close() error
}

type fileOperations struct {
	open    func(*connector.LaneOwnerConfig) (fileLane, error)
	digest  func(string) (connector.FileDigest, error)
	handoff func(connector.FileReceiveGrant) (connector.FileReceiveGrant, error)
}

type ownedReceive struct {
	owner, id, destination string
	lane                   fileLane
	grant                  connector.FileReceiveGrant
	// Confirmed admissions are cleanup obligations, not locally inferred states.
	receiveAccepted, sendAccepted bool
	outcome                       error
}

func main() {
	if err := run(); err != nil {
		fmt.Fprintln(os.Stderr, "coakka_replica_file_fanout:", err)
		os.Exit(1) // All deferred owner cleanup has finished before process exit.
	}
}

func run() error {
	work := os.Getenv("COAKKA_SAMPLE_WORK_ROOT")
	if !filepath.IsAbs(work) {
		return errors.New("set an existing absolute COAKKA_SAMPLE_WORK_ROOT")
	}
	info, err := os.Lstat(work)
	if err != nil {
		return err
	}
	if !info.IsDir() || info.Mode()&os.ModeSymlink != 0 {
		return errors.New("sample work root must be a real directory")
	}
	root, err := os.MkdirTemp(work, "coakka-file-fanout-")
	if err != nil {
		return err
	}
	return runFanout(root, nativeOperations())
}

func nativeOperations() fileOperations {
	return fileOperations{
		open: func(owner *connector.LaneOwnerConfig) (fileLane, error) {
			config := connector.DefaultFileLaneConfig()
			if owner == nil {
				config.Flags = connector.FileLaneSender
				lane, err := connector.OpenFileLane(config, "")
				if err != nil {
					return nil, err
				}
				return lane, nil
			}
			config.Flags = connector.FileLaneReceiver
			lane, err := connector.OpenOwnedFileLane(config, *owner, "")
			if err != nil {
				return nil, err
			}
			return lane, nil
		},
		digest:  func(path string) (connector.FileDigest, error) { return connector.FileSHA256(path, "") },
		handoff: handoffGrant,
	}
}

func runFanout(root string, operations fileOperations) (result error) {
	var sender fileLane
	var receivers [3]ownedReceive
	defer func() {
		joined := true
		for index := range receivers {
			item := &receivers[index]
			if item.sendAccepted {
				item.outcome = errors.Join(item.outcome, retire(sender, item.id, connector.FileTransferSend))
			}
			if item.receiveAccepted {
				item.outcome = errors.Join(item.outcome, retire(item.lane, item.id, connector.FileTransferReceive))
			}
			if item.lane != nil {
				err := item.lane.Close()
				joined = joined && err == nil
				item.outcome = errors.Join(item.outcome, err)
			}
			if item.outcome != nil {
				result = errors.Join(result, fmt.Errorf("owner %s: %w", item.owner, item.outcome))
			}
		}
		if sender != nil {
			err := sender.Close()
			joined = joined && err == nil
			result = errors.Join(result, err)
		}
		// A refused stop is not proof that a worker released the source/destination.
		// Retain this app-owned directory rather than deleting beneath native I/O.
		if joined {
			result = errors.Join(result, os.RemoveAll(root))
		} else {
			result = errors.Join(result, fmt.Errorf("unconfirmed lane stop; staging retained at %s", root))
		}
		if result == nil {
			fmt.Printf("coakka_replica_file_fanout owners=3 bytes_per_owner=%d ok\n", sourceBytes)
		}
	}()
	source := filepath.Join(root, "source.bin")
	file, err := os.OpenFile(source, os.O_CREATE|os.O_EXCL|os.O_WRONLY, 0600)
	if err != nil {
		return err
	}
	if err := errors.Join(writeSource(file), file.Close()); err != nil {
		return err
	}
	digest, err := operations.digest(source)
	if err != nil {
		return err
	}
	sender, err = operations.open(nil)
	if err != nil {
		return err
	}
	incarnation, err := freshToken()
	if err != nil {
		return err
	}
	for index := range receivers {
		item := &receivers[index]
		item.owner = fmt.Sprintf("billing-%d-%s", index+1, incarnation)
		item.id = fmt.Sprintf("replica-file-%d-%s", index+1, incarnation)
		item.destination = filepath.Join(root, fmt.Sprintf("billing-%d.bin", index+1))
		item.lane, item.outcome = operations.open(&connector.LaneOwnerConfig{
			OwnerInstanceID: item.owner, AdvertisedHost: "127.0.0.1",
		})
		if item.outcome != nil {
			continue
		}
		token, err := freshToken()
		if err != nil {
			item.outcome = err
			continue
		}
		grant, err := item.lane.PrepareReceiveGrant(connector.FileReceiveSpec{
			TransferID: item.id, AuthorizationToken: token, DestinationPath: item.destination,
			ExpectedSize: digest.Size, ExpectedSHA256: digest.SHA256,
		})
		if err != nil {
			item.outcome = err
			continue
		}
		item.receiveAccepted = true
		item.grant, item.outcome = operations.handoff(grant)
		// Compare all Core-issued fields, including exact bound endpoint and token.
		// Do not reconstruct an endpoint from topology or log the secret grant.
		if item.outcome == nil && (item.grant != grant || grant.Owner.OwnerInstanceID != item.owner ||
			grant.TransferID != item.id || grant.AuthorizationToken != token ||
			grant.ExpectedSize != digest.Size || grant.ExpectedSHA256 != digest.SHA256) {
			item.outcome = errors.New("control handoff changed owner/transfer facts")
		}
	}
	for index := range receivers {
		item := &receivers[index]
		if item.outcome == nil {
			item.outcome = sender.SubmitSend(item.grant.ToSendSpec(source, 0))
			item.sendAccepted = item.outcome == nil
		}
	}
	for index := range receivers {
		item := &receivers[index]
		if !item.sendAccepted {
			continue
		}
		sent, err := waitTerminal(sender, item.id, connector.FileTransferSend)
		if err != nil {
			item.outcome = err
			continue
		}
		if !sent.Succeeded() {
			item.outcome = fmt.Errorf("send terminal state=%d result=%d", sent.State, sent.Result)
			continue // Cleanup cancels the peer rather than waiting on a prepared receive.
		}
		received, err := waitTerminal(item.lane, item.id, connector.FileTransferReceive)
		if err != nil {
			item.outcome = err
			continue
		}
		if !received.Succeeded() {
			item.outcome = fmt.Errorf("receive terminal state=%d result=%d", received.State, received.Result)
			continue
		}
		actual, err := operations.digest(item.destination)
		item.outcome = err
		if err == nil && actual != digest {
			item.outcome = errors.New("destination identity mismatch")
		}
	}
	return nil // Deferred cleanup joins every independent operation/retirement outcome.
}

func waitTerminal(lane fileLane, id string, direction connector.FileTransferDirection) (connector.FileTransferSnapshot, error) {
	var sequence uint64
	for range 64 {
		snapshot, err := lane.WaitTransfer(id, direction, sequence, 30_000)
		if err != nil {
			return connector.FileTransferSnapshot{}, err
		}
		if snapshot.Terminal() {
			return snapshot, nil
		}
		sequence = snapshot.UpdateSequence
	}
	return connector.FileTransferSnapshot{}, errors.New("transfer did not reach terminal state within 64 updates")
}

func retire(lane fileLane, id string, direction connector.FileTransferDirection) error {
	// Cancel is idempotent for retained terminal records. A concurrent completion
	// remains Core's completion, never a fabricated local CANCELED state.
	cancelErr := lane.Cancel(id, direction)
	_, waitErr := waitTerminal(lane, id, direction)
	if waitErr != nil {
		return errors.Join(cancelErr, waitErr) // No Forget without a terminal witness.
	}
	return errors.Join(cancelErr, lane.Forget(id, direction))
}

func writeSource(writer io.Writer) error {
	buffer := make([]byte, 64*1024)
	for offset := 0; offset < sourceBytes; {
		count := min(len(buffer), sourceBytes-offset)
		for index := range buffer[:count] {
			buffer[index] = byte((offset+index)*31 + 17)
		}
		n, err := writer.Write(buffer[:count])
		if err != nil {
			return err
		}
		if n != count {
			return io.ErrShortWrite
		}
		offset += count
	}
	return nil
}

func handoffGrant(grant connector.FileReceiveGrant) (connector.FileReceiveGrant, error) {
	// Local JSON roundtrip only: the deployment must authenticate its real control
	// plane and keep this fresh per-owner token private. This is not TLS evidence.
	wire, err := json.Marshal(grant)
	if err != nil {
		return connector.FileReceiveGrant{}, err
	}
	var received connector.FileReceiveGrant
	err = json.Unmarshal(wire, &received)
	return received, err
}

func freshToken() (string, error) {
	var value [32]byte
	if _, err := rand.Read(value[:]); err != nil {
		return "", err
	}
	return base64.RawURLEncoding.EncodeToString(value[:]), nil
}
