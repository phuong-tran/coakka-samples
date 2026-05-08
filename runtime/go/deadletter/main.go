package main

import (
	"context"
	"errors"
	"fmt"
	"os"
	"time"

	connector "github.com/phuong-tran/coakka-runtime-go"
	coakkav2 "github.com/phuong-tran/coakka-runtime-go/coakka/v2"
)

func main() {
	liveTarget := "samples.runtime.go.deadletter.live"
	missingTarget := "samples.runtime.go.deadletter.missing"
	runtimeHost, err := connector.StartRuntimeHost(connector.ConnectorStartSpec{
		SystemName:                   "go-deadletter-sample",
		NodeID:                       "go-deadletter-sample-node",
		StrictNoDrop:                 true,
		QueueCapacity:                128,
		EnableMonitor:                true,
		SeparateDeliveredRequestLane: true,
		Generation:                   1,
		Routes: []connector.RouteSpec{{
			Target: liveTarget,
			Endpoints: []connector.EndpointSpec{{
				Host:  "127.0.0.1",
				Port:  19431,
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

	deadletterCtx, cancelDeadletterObserve := context.WithCancel(context.Background())
	defer cancelDeadletterObserve()
	observedDeadletters := runtimeHost.Deadletters(deadletterCtx, 1)

	_, err = runtimeHost.AskJSON(
		"samples-runtime-go-deadletter-client",
		missingTarget,
		map[string]any{"message": "route-miss"},
		connector.NewPayloadIdentity("samples.runtime.go.deadletter.request.v1", 1, connector.PayloadFormatJSON),
		2*time.Second,
		"route-miss",
		connector.DeliveryHintRouterDefault,
		nil,
	)
	if err == nil {
		fail("expected route miss deadletter")
	}

	var deadletterErr *connector.DeadletterError
	if !errors.As(err, &deadletterErr) {
		fail("expected DeadletterError, got %T: %v", err, err)
	}
	deadletter := deadletterErr.Deadletter

	stats := runtimeHost.Stats()
	clientStats := runtimeHost.ClientStats()

	if deadletter.GetReason() != coakkav2.DeadletterReason_DEADLETTER_REASON_ROUTE_MISS {
		fail("expected route miss reason, got %s", deadletter.GetReason().String())
	}
	if deadletter.GetOriginalEnvelope().GetTarget() != missingTarget {
		fail("expected target=%s, got %s", missingTarget, deadletter.GetOriginalEnvelope().GetTarget())
	}
	if stats.RouteMissCount != 1 || stats.DeadletterCount != 1 {
		fail("expected routeMissCount=1 deadletterCount=1, got %+v", stats)
	}
	if clientStats.MatchedDeadletters != 1 {
		fail("expected matchedDeadletters=1, got %d", clientStats.MatchedDeadletters)
	}
	var observed connector.ObservedDeadletter
	select {
	case observed = <-observedDeadletters:
	case <-time.After(time.Second):
		fail("timed out waiting for observed deadletter")
	}
	if !observed.MatchedPendingRequest {
		fail("expected observed deadletter to match pending request")
	}
	if observed.Deadletter == nil || observed.Deadletter.GetOriginalEnvelope().GetTarget() != missingTarget {
		fail("expected observed target=%s, got %+v", missingTarget, observed.Deadletter)
	}

	fmt.Printf(
		"coakka_runtime_deadletter reason=%s target=%s generation=%d\n",
		deadletter.GetReason().String(),
		deadletter.GetOriginalEnvelope().GetTarget(),
		deadletter.GetActiveGeneration(),
	)
	fmt.Printf(
		"coakka_runtime_deadletter_observed matchedPending=%t target=%s\n",
		observed.MatchedPendingRequest,
		observed.Deadletter.GetOriginalEnvelope().GetTarget(),
	)
	fmt.Printf(
		"coakka_runtime_stats routeMisses=%d deadletters=%d matchedDeadletters=%d\n",
		stats.RouteMissCount,
		stats.DeadletterCount,
		clientStats.MatchedDeadletters,
	)
}

func fail(format string, args ...any) {
	fmt.Fprintf(os.Stderr, format+"\n", args...)
	os.Exit(1)
}
