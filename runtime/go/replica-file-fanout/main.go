package main

import (
	"crypto/rand"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"

	connector "github.com/phuong-tran/coakka-runtime-go"
)

type ownedReceive struct {
	id          string
	destination string
	lane        *connector.FileLane
	grant       connector.FileReceiveGrant
}

func main() {
	runtime := os.Getenv("COAKKA_FILE_LANE_RUNTIME_LIB")
	root, err := os.MkdirTemp("", "coakka-replica-file-fanout-")
	require(err)
	defer os.RemoveAll(root)

	source := filepath.Join(root, "source.bin")
	payload := make([]byte, 9*1024*1024+731)
	for index := range payload {
		payload[index] = byte(index*31 + 17)
	}
	require(os.WriteFile(source, payload, 0o600))
	digest, err := connector.FileSHA256(source, runtime)
	require(err)

	senderConfig := connector.DefaultFileLaneConfig()
	senderConfig.Flags = connector.FileLaneSender
	sender, err := connector.OpenFileLane(senderConfig, runtime)
	require(err)
	defer sender.Close()

	owners := []string{"billing-1", "billing-2", "billing-3"}
	receivers := make([]ownedReceive, 0, len(owners))
	for _, ownerID := range owners {
		receivers = append(receivers, prepareOwner(root, ownerID, digest, runtime))
	}
	defer func() {
		for index := range receivers {
			receivers[index].lane.Close()
		}
	}()

	for index := range receivers {
		require(sender.SubmitSend(receivers[index].grant.ToSendSpec(source, 0)))
	}
	for index := range receivers {
		item := &receivers[index]
		sent := waitTerminal(sender, item.id, connector.FileTransferSend)
		received := waitTerminal(item.lane, item.id, connector.FileTransferReceive)
		if !sent.Succeeded() || !received.Succeeded() {
			fail("owner %s failed: send=%v receive=%v", item.grant.Owner.OwnerInstanceID, sent, received)
		}
		actual, err := connector.FileSHA256(item.destination, runtime)
		require(err)
		if actual != digest {
			fail("owner %s destination identity mismatch", item.grant.Owner.OwnerInstanceID)
		}
		require(sender.Forget(item.id, connector.FileTransferSend))
		require(item.lane.Forget(item.id, connector.FileTransferReceive))
	}

	fmt.Printf("coakka_replica_file_fanout owners=%d bytes_per_owner=%d ok\n", len(owners), digest.Size)
}

func prepareOwner(root, ownerID string, digest connector.FileDigest, runtime string) ownedReceive {
	config := connector.DefaultFileLaneConfig()
	config.Flags = connector.FileLaneReceiver
	lane, err := connector.OpenOwnedFileLane(config, connector.LaneOwnerConfig{
		OwnerInstanceID: ownerID,
		AdvertisedHost:  "127.0.0.1",
	}, runtime)
	require(err)

	id := "replica-file-" + ownerID
	destination := filepath.Join(root, ownerID+".bin")
	grant, err := lane.PrepareReceiveGrant(connector.FileReceiveSpec{
		TransferID:         id,
		AuthorizationToken: freshToken(),
		DestinationPath:    destination,
		ExpectedSize:       digest.Size,
		ExpectedSHA256:     digest.SHA256,
	})
	require(err)

	// This JSON roundtrip stands in for the application's authenticated control API.
	wire, err := json.Marshal(grant)
	require(err)
	var receivedGrant connector.FileReceiveGrant
	require(json.Unmarshal(wire, &receivedGrant))
	if receivedGrant.Owner.OwnerInstanceID != ownerID {
		fail("requested owner %s returned grant for %s", ownerID, receivedGrant.Owner.OwnerInstanceID)
	}
	return ownedReceive{id: id, destination: destination, lane: lane, grant: receivedGrant}
}

func waitTerminal(lane *connector.FileLane, id string, direction connector.FileTransferDirection) connector.FileTransferSnapshot {
	var sequence uint64
	for range 64 {
		snapshot, err := lane.WaitTransfer(id, direction, sequence, 30_000)
		require(err)
		if snapshot.Terminal() {
			return snapshot
		}
		sequence = snapshot.UpdateSequence
	}
	fail("transfer %s did not reach terminal state", id)
	return connector.FileTransferSnapshot{}
}

func freshToken() string {
	value := make([]byte, 32)
	requireValue(rand.Read(value))
	return base64.RawURLEncoding.EncodeToString(value)
}

func require(err error) {
	if err != nil {
		fail("%v", err)
	}
}

func requireValue(_ int, err error) {
	require(err)
}

func fail(format string, arguments ...any) {
	fmt.Fprintf(os.Stderr, format+"\n", arguments...)
	os.Exit(1)
}
