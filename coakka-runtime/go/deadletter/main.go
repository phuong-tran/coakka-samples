package main

import (
	"context"
	"errors"
	"fmt"
	"os"
	"time"

	connector "github.com/phuong-tran/coakka-runtime-go/v3"
)

func main() {
	if err := run(); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1) // run has already canceled observation and closed its host.
	}
}

func run() (result error) {
	const liveTarget = "samples.runtime.go.deadletter.live"
	const missingTarget = "samples.runtime.go.deadletter.missing"
	host, err := connector.StartRuntimeHost(connector.ConnectorStartSpec{
		SystemName: "go-deadletter-sample",
		NodeID:     "go-deadletter-sample-node",
		Routes:     []connector.RouteSpec{connector.LocalRouteDefault(liveTarget)},
	}, "")
	if err != nil {
		return err
	}
	defer func() { result = errors.Join(result, host.Close()) }()

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel() // Retire the bounded observer before the host close defer.
	observedDeadletters, err := host.Deadletters(ctx, 1)
	if err != nil {
		return err
	}
	_, err = host.AskJSON(
		"samples-runtime-go-deadletter-client", missingTarget,
		map[string]any{"message": "route-miss"},
		connector.NewPayloadIdentity("samples.runtime.go.deadletter.request.v1", 1, connector.PayloadFormatJSON),
		2*time.Second, "route-miss", connector.DeliveryHintRouterDefault, nil,
	)
	var terminal *connector.DeadletterError
	if !errors.As(err, &terminal) || terminal == nil {
		return fmt.Errorf("expected typed route-miss deadletter, got %T: %v", err, err)
	}
	if err := validateDeadletter(terminal.Deadletter, missingTarget); err != nil {
		return err
	}
	deadletter := terminal.Deadletter
	stats, err := host.Stats()
	if err != nil {
		return err
	}
	client := host.ClientStats()
	if stats.RouteMissCount != 1 || stats.DeadletterCount != 1 || client.MatchedDeadletters != 1 {
		return fmt.Errorf("unexpected terminal counters: %+v %+v", stats, client)
	}
	var observed connector.ObservedDeadletter
	select {
	case value, open := <-observedDeadletters:
		if !open {
			return fmt.Errorf("deadletter observation closed before the event")
		}
		observed = value
	case <-time.After(time.Second):
		return fmt.Errorf("timed out waiting for observed deadletter")
	}
	if !observed.MatchedPendingRequest {
		return fmt.Errorf("expected observed deadletter to match pending request")
	}
	if err := validateDeadletter(observed.Deadletter, missingTarget); err != nil {
		return err
	}
	fmt.Printf("coakka_runtime_deadletter reason=%s target=%s generation=%d\n",
		deadletter.Reason.String(), deadletter.OriginalEnvelope.Target, deadletter.ActiveGeneration)
	fmt.Printf("coakka_runtime_deadletter_observed matchedPending=%t target=%s\n",
		observed.MatchedPendingRequest, observed.Deadletter.OriginalEnvelope.Target)
	fmt.Printf("coakka_runtime_stats routeMisses=%d deadletters=%d matchedDeadletters=%d\n",
		stats.RouteMissCount, stats.DeadletterCount, client.MatchedDeadletters)
	return nil
}

func validateDeadletter(value *connector.Deadletter, target string) error {
	if value == nil || value.Reason != connector.DeadletterReasonRouteMiss {
		return fmt.Errorf("expected route-miss deadletter, got %+v", value)
	}
	if value.OriginalEnvelope == nil || value.OriginalEnvelope.Target != target {
		return fmt.Errorf("expected original envelope for target %q", target)
	}
	return nil
}
