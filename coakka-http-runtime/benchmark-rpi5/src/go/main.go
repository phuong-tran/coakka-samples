// Command fixed-server measures the installed CoAkka host-inlined surface.
// Chi and Gin use separate executables so neither links this native runtime.
package main

import (
	"encoding/json"
	"errors"
	"fmt"
	coakkahttp "github.com/phuong-tran/coakka-http-runtime-go"
	"net/http"
	"os"
	"os/signal"
	"strconv"
	"syscall"
)

var body = []byte("0123456789abcdef0123456789abcdef")

func main() {
	if len(os.Args) != 3 || os.Args[1] != "coakka" {
		fatal(fmt.Errorf("usage: fixed-server coakka <port>"))
	}
	port, err := strconv.Atoi(os.Args[2])
	if err != nil || port < 1 || port > 65535 {
		fatal(fmt.Errorf("invalid port"))
	}
	runCoakka(uint16(port))
}

func runCoakka(port uint16) {
	stopping := make(chan os.Signal, 1)
	signal.Notify(stopping, os.Interrupt, syscall.SIGTERM)
	defer signal.Stop(stopping)
	policy := coakkahttp.CPUAuto
	switch os.Getenv("COAKKA_BENCH_CPU_POLICY") {
	case "single":
		policy = coakkahttp.CPUSingle
	case "auto":
	default:
		fatal(fmt.Errorf("explicit benchmark CPU intent required"))
	}
	contentType, err := coakkahttp.NewHeader("content-type", "application/octet-stream")
	if err != nil {
		fatal(err)
	}
	headers, err := coakkahttp.NewHeaders(contentType)
	if err != nil {
		fatal(err)
	}
	response, err := coakkahttp.NewResponse(http.StatusOK, headers, body)
	if err != nil {
		fatal(err)
	}
	service, err := coakkahttp.NewBuilder().Listen("127.0.0.1", port).
		CPU(policy).
		Get("/fixed", func(*coakkahttp.Request) (coakkahttp.Response, error) {
			return response, nil
		}).Start()
	if err != nil {
		fatal(err)
	}
	// This cold record projects Core facts; it never reconstructs its defaults.
	info, err := service.RuntimeInfo()
	if err != nil {
		fatal(errors.Join(err, service.Close()))
	}
	if info.CPU.SelectedCPUCount > 2 {
		fatal(errors.Join(fmt.Errorf("invalid Core CPU observation"), service.Close()))
	}
	if info.EffectiveIOBackend != coakkahttp.IOPlatformDefault && info.EffectiveIOBackend != coakkahttp.IOUring {
		fatal(errors.Join(fmt.Errorf("unknown Core backend observation"), service.Close()))
	}
	record, err := json.Marshal(map[string]any{
		"cpu": map[string]any{"requestedPolicy": info.CPU.RequestedPolicy,
			"placement": info.CPU.Placement, "selectedCpuCount": info.CPU.SelectedCPUCount,
			"selectedCpuIds": info.CPU.SelectedCPUIDs[:info.CPU.SelectedCPUCount]},
		"execution": map[string]any{"observed": info.Execution.Observed,
			"configuredEventLoops": info.Execution.ConfiguredEventLoops,
			"activeEventLoops":     info.Execution.ActiveEventLoops},
		"limits": info.Limits, "effectiveIoBackend": info.EffectiveIOBackend,
		"ioUringEffective":              info.EffectiveIOBackend == coakkahttp.IOUring,
		"requestNotificationBatchSize":  info.RequestNotificationBatchSize,
		"terminalNotificationBatchSize": info.TerminalNotificationBatchSize,
	})
	if err != nil {
		fatal(errors.Join(err, service.Close()))
	}
	fmt.Printf("coakka-runtime-info=%s\n", record)
	<-stopping
	if err = service.Close(); err != nil {
		fatal(err)
	}
	fmt.Println("benchmark-shutdown=pass")
}

func fatal(err error) {
	fmt.Fprintln(os.Stderr, err)
	os.Exit(1)
}
