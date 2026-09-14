package main

import (
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"os/signal"
	"sync"
	"syscall"
	"time"

	coakkahttp "github.com/phuong-tran/coakka-http-runtime-go"
)

var body = []byte("0123456789abcdef0123456789abcdef")

func main() {
	if len(os.Args) != 2 {
		fatal(errors.New("usage: coakka-go-core <platform-default|io-uring>"))
	}
	backend, backendName := parseBackend(os.Args[1])
	certificate := requiredEnvironment("COAKKA_BENCHMARK_TLS_CERT")
	privateKey := requiredEnvironment("COAKKA_BENCHMARK_TLS_KEY")
	hostRuntime, err := coakkahttp.InspectRuntime()
	if err != nil {
		fatal(err)
	}
	if backend == coakkahttp.IOUring && !hostRuntime.IOUringSupported {
		fatal(fmt.Errorf(
			"io_uring benchmark stopped: Core reports unsupported (compiled=%t probe_error=%d)",
			hostRuntime.IOUringCompiled, hostRuntime.IOUringProbeError,
		))
	}
	response, err := coakkahttp.Bytes(200, body)
	if err != nil {
		fatal(err)
	}
	core, err := coakkahttp.Open(coakkahttp.Config{
		Listeners: []coakkahttp.Listener{{
			ID: 1, Protocol: coakkahttp.ProtocolHTTP2,
			BindAddress: "127.0.0.1", Security: coakkahttp.SecurityTLS,
			CredentialGeneration: 1, CredentialID: "benchmark-server",
			CertificateChainFile: certificate, PrivateKeyFile: privateKey,
		}},
		Routes: []coakkahttp.Route{{
			ID: 1, HandlerBindingID: 1, Method: "GET", Pattern: "/fixed",
			BodyDelivery: coakkahttp.BodyInline,
		}},
		IOBackend: backend,
	})
	if err != nil {
		fatal(err)
	}
	defer func() { _ = core.Close() }()
	if err = core.Start(); err != nil {
		fatal(err)
	}
	effectiveRuntime, err := core.RuntimeInfo()
	if err != nil {
		fatal(err)
	}
	active := effectiveRuntime.EffectiveIOBackend == coakkahttp.IOUring
	if effectiveRuntime.EffectiveIOBackend != backend {
		fatal(fmt.Errorf(
			"backend benchmark stopped: requested=%s effective=%d fallback_reason=%d",
			backendName, effectiveRuntime.EffectiveIOBackend, effectiveRuntime.FallbackReason,
		))
	}

	stopping := make(chan struct{})
	errorsFound := make(chan error, 1)
	var reader sync.WaitGroup
	reader.Add(1)
	go func() {
		defer reader.Done()
		for {
			event, takeErr := core.TakeEvent(100 * time.Millisecond)
			if takeErr != nil {
				var coreErr *coakkahttp.Error
				if errors.As(takeErr, &coreErr) && (coreErr.Code == coakkahttp.ResultTimeout || coreErr.Code == coakkahttp.ResultClosed || coreErr.Code == coakkahttp.ResultCancelled) {
					select {
					case <-stopping:
						return
					default:
						continue
					}
				}
				select {
				case errorsFound <- takeErr:
				default:
				}
				return
			}
			if event != nil && event.Kind == coakkahttp.EventRequest {
				if respondErr := core.Respond(event.Exchange, response); respondErr != nil {
					select {
					case errorsFound <- respondErr:
					default:
					}
					return
				}
			}
		}
	}()

	port, err := core.BoundPort()
	if err != nil {
		fatal(err)
	}
	printJSON(map[string]any{
		"ready": true, "bound_port": port, "application_path": "normal",
		"application_protocol": 2, "transport_security_mode": 2,
		"io_backend": backendName, "io_uring_active": active,
		"io_uring_supported":         hostRuntime.IOUringSupported,
		"requested_io_backend":       effectiveRuntime.RequestedIOBackend,
		"effective_io_backend":       effectiveRuntime.EffectiveIOBackend,
		"io_backend_fallback_reason": effectiveRuntime.FallbackReason,
	})

	signals := make(chan os.Signal, 1)
	signal.Notify(signals, syscall.SIGINT, syscall.SIGTERM)
	select {
	case <-signals:
	case err = <-errorsFound:
		fmt.Fprintln(os.Stderr, err)
	}
	signal.Stop(signals)
	close(stopping)
	if drainErr := core.Drain(); drainErr != nil && err == nil {
		err = drainErr
	}
	_ = core.Interrupt()
	reader.Wait()
	if stopErr := core.Stop(); stopErr != nil && err == nil {
		err = stopErr
	}
	if closeErr := core.Close(); closeErr != nil && err == nil {
		err = closeErr
	}
	printJSON(map[string]any{"stopped": true, "handler_errors": boolInt(err != nil)})
	if err != nil {
		os.Exit(1)
	}
}

func parseBackend(value string) (coakkahttp.IOBackend, string) {
	switch value {
	case "platform-default":
		return coakkahttp.IOPlatformDefault, value
	case "io-uring":
		return coakkahttp.IOUring, value
	default:
		fatal(fmt.Errorf("unsupported I/O backend: %s", value))
		return 0, ""
	}
}

func requiredEnvironment(name string) string {
	value := os.Getenv(name)
	if value == "" {
		fatal(fmt.Errorf("missing %s", name))
	}
	return value
}

func boolInt(value bool) int {
	if value {
		return 1
	}
	return 0
}

func printJSON(value any) {
	if err := json.NewEncoder(os.Stdout).Encode(value); err != nil {
		fatal(err)
	}
}

func fatal(err error) {
	fmt.Fprintln(os.Stderr, err)
	os.Exit(1)
}
