// Package fixture owns only comparator application startup/shutdown. It imports
// no CoAkka code and does not define a net/http-only comparison lane.
package fixture

import (
	"context"
	"errors"
	"fmt"
	"net/http"
	"os"
	"os/signal"
	"strconv"
	"syscall"
	"time"
)

// Body is the fixed response shared read-only by requests; never mutate it.
var Body = []byte("0123456789abcdef0123456789abcdef")

// ContentType is identical for every benchmark implementation.
const ContentType = "application/octet-stream"

// Port validates the lane identity, preventing a mislabeled executable run.
func Port(lane string) int {
	if len(os.Args) != 3 || os.Args[1] != lane {
		Fatal(fmt.Errorf("usage: %s-server %s <port>", lane, lane))
	}
	port, err := strconv.Atoi(os.Args[2])
	if err != nil || port < 1 || port > 65535 {
		Fatal(fmt.Errorf("invalid port"))
	}
	return port
}

// Serve waits for normal HTTP drain and propagates close failures to the driver.
func Serve(handler http.Handler, port int) {
	server := &http.Server{Addr: fmt.Sprintf("127.0.0.1:%d", port), Handler: handler,
		ReadHeaderTimeout: 5 * time.Second}
	failed := make(chan error, 1)
	stopping := make(chan os.Signal, 1)
	signal.Notify(stopping, os.Interrupt, syscall.SIGTERM)
	defer signal.Stop(stopping)
	go func() { failed <- server.ListenAndServe() }()
	select {
	case err := <-failed:
		Fatal(fmt.Errorf("framework listener stopped before signal: %w", err))
	case <-stopping:
		ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		if err := server.Shutdown(ctx); err != nil {
			Fatal(err)
		}
		select {
		case err := <-failed:
			if !errors.Is(err, http.ErrServerClosed) {
				Fatal(err)
			}
		case <-time.After(5 * time.Second):
			Fatal(fmt.Errorf("framework server did not stop before deadline"))
		}
	}
	fmt.Println("benchmark-shutdown=pass")
}

// Fatal terminates the measured process on a startup or lifecycle failure.
func Fatal(err error) {
	fmt.Fprintln(os.Stderr, err)
	os.Exit(1)
}
