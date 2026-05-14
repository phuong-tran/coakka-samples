package main

import (
	"encoding/json"
	"fmt"
	"os"
	"time"

	connector "github.com/phuong-tran/coakka-runtime-go"
)

func main() {
	target := "samples.runtime.go.echo"
	// Minimal single-process runtime configuration.
	//
	// SystemName groups diagnostics for one logical runtime participant.
	// NodeID identifies this concrete process in logs and runtime snapshots.
	// QueueCapacity=128 is bounded but roomy enough for a sample.
	// StrictNoDrop=true makes overload visible instead of silently dropping messages.
	// EnableMonitor=true exposes runtime snapshots for diagnostics.
	// The delivered-request lane is enabled by default for request/reply hosts.
	// Generation=1 is the first route-table version; increment it for new route snapshots.
	// EndpointFlagLocal means the target handler is registered in this process.
	runtimeHost, err := connector.StartRuntimeHost(connector.ConnectorStartSpec{
		SystemName:                   "go-runtime-sample",
		NodeID:                       "go-runtime-sample-node",
		StrictNoDrop:                 true,
		QueueCapacity:                128,
		EnableMonitor:                true,
		Generation:                   1,
		Routes: []connector.RouteSpec{{
			Target: target,
			Endpoints: []connector.EndpointSpec{{
				Host:  "127.0.0.1",
				Port:  19331,
				Flags: uint32(connector.EndpointFlagLocal),
			}},
		}},
	}, "")
	if err != nil {
		fail("StartRuntimeHost failed: %v", err)
	}
	defer func() {
		if err := runtimeHost.Close(); err != nil {
			fail("Close failed: %v", err)
		}
	}()

	info := runtimeHost.RuntimeInfo()
	fmt.Printf(
		"coakka_runtime_info abi=%d version=%s git=%s\n",
		info.AbiVersion,
		info.RuntimeVersion,
		info.GitCommit,
	)

	if err := runtimeHost.RegisterHandler(target, func(request *connector.Envelope) *connector.Envelope {
		reply, err := connector.MakeJSONReplyFromRequestIdentity(request, target, map[string]any{
			"echo": "hello-runtime-go",
		})
		if err != nil {
			fail("MakeJSONReplyFromRequestIdentity failed: %v", err)
		}
		return reply
	}, true); err != nil {
		fail("RegisterHandler failed: %v", err)
	}

	response, err := runtimeHost.AskJSON(
		"samples-runtime-go-client",
		target,
		map[string]any{"message": "hello-runtime-go"},
		connector.NewPayloadIdentity("samples.runtime.go.echo.request.v1", 1, connector.PayloadFormatJSON),
		2*time.Second,
		"echo",
		connector.DeliveryHintRouterDefault,
		nil,
	)
	if err != nil {
		fail("AskJSON failed: %v", err)
	}

	payload, err := json.Marshal(response)
	if err != nil {
		fail("json.Marshal response failed: %v", err)
	}
	fmt.Printf("coakka_runtime_response payload=%s\n", payload)

	stats := runtimeHost.Stats()
	clientStats := runtimeHost.ClientStats()
	fmt.Printf(
		"coakka_runtime_stats generation=%d routes=%d delivered=%d matchedResponses=%d\n",
		stats.AppliedGeneration,
		stats.RouteCount,
		clientStats.DeliveredRequests,
		clientStats.MatchedResponses,
	)
}

func fail(format string, args ...any) {
	fmt.Fprintf(os.Stderr, format+"\n", args...)
	os.Exit(1)
}
