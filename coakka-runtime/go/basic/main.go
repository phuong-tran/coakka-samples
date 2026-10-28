package main

import (
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"time"

	connector "github.com/phuong-tran/coakka-runtime-go/v2"
)

func main() {
	if err := run(); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1) // run has already retired every owned resource.
	}
}

func run() (result error) {
	const target = "samples.runtime.go.echo"
	// Application identity/routes only; Core owns runtime defaults.
	host, err := connector.StartRuntimeHost(connector.ConnectorStartSpec{
		SystemName: "go-runtime-sample",
		NodeID:     "go-runtime-sample-node",
		Routes:     []connector.RouteSpec{connector.LocalRouteDefault(target)},
	}, "")
	if err != nil {
		return fmt.Errorf("start: %w", err)
	}
	defer func() { result = errors.Join(result, host.Close()) }()

	info, err := host.RuntimeInfo()
	if err != nil {
		return err
	}
	startup, err := host.StartupConfig()
	if err != nil {
		return err
	}
	fmt.Printf("coakka_runtime_info abi=%d version=%s git=%s\n", info.AbiVersion, info.RuntimeVersion, info.GitCommit)
	fmt.Printf("coakka_runtime_startup cpuPolicy=%d connectionMode=%d monitorEnabled=%t\n",
		startup.Execution.RequestedPolicy, startup.Connection.Mode, startup.Monitor.Enabled)

	// Exactly one sample ask: this one-result handoff cannot block its callback.
	handlerFailures := make(chan error, 1)
	if err := host.RegisterHandler(target, echoHandler(target, handlerFailures), true); err != nil {
		return err
	}
	response, askErr := host.AskJSON(
		"samples-runtime-go-client", target,
		map[string]any{"message": "hello-runtime-go"},
		connector.NewPayloadIdentity("samples.runtime.go.echo.request.v1", 1, connector.PayloadFormatJSON),
		2*time.Second, "echo", connector.DeliveryHintRouterDefault, nil,
	)
	select {
	case handlerErr := <-handlerFailures:
		return errors.Join(handlerErr, askErr)
	default:
	}
	if askErr != nil {
		return askErr
	}
	if response["echo"] != "hello-runtime-go" {
		return fmt.Errorf("echo payload differs")
	}
	payload, err := json.Marshal(response)
	if err != nil {
		return err
	}
	fmt.Printf("coakka_runtime_response payload=%s\n", payload)

	stats, err := host.Stats()
	if err != nil {
		return err
	}
	client := host.ClientStats()
	if stats.RouteCount != 1 || client.DeliveredRequests != 1 || client.MatchedResponses != 1 {
		return fmt.Errorf("unexpected route/client counters: %+v %+v", stats, client)
	}
	fmt.Printf("coakka_runtime_stats generation=%d routes=%d delivered=%d matchedResponses=%d\n",
		stats.AppliedGeneration, stats.RouteCount, client.DeliveredRequests, client.MatchedResponses)
	return nil
}

func echoHandler(target string, failures chan<- error) connector.HandlerFn {
	return func(request *connector.Envelope) *connector.Envelope {
		reply, err := connector.MakeJSONReplyFromRequestIdentity(request, target,
			map[string]any{"echo": "hello-runtime-go"})
		if err != nil {
			select {
			case failures <- err:
			default:
			}
			return nil
		}
		return reply
	}
}
