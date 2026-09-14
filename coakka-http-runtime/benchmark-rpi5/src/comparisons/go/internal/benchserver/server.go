package benchserver

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"net"
	"net/http"
	"os"
	"os/signal"
	"sync/atomic"
	"syscall"
	"time"
)

var Payload = []byte("0123456789abcdef0123456789abcdef")
var handlerErrors atomic.Uint64

func Run(handler http.Handler) error {
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		return err
	}
	server := &http.Server{
		Handler:           handler,
		ReadHeaderTimeout: 30 * time.Second,
		IdleTimeout:       30 * time.Second,
		MaxHeaderBytes:    4 * 1024,
	}
	serveResult := make(chan error, 1)
	go func() {
		serveResult <- server.Serve(listener)
	}()

	port := listener.Addr().(*net.TCPAddr).Port
	if err := json.NewEncoder(os.Stdout).Encode(map[string]any{
		"ready": true, "bound_port": port,
	}); err != nil {
		_ = server.Close()
		return err
	}

	stopping := make(chan os.Signal, 1)
	signal.Notify(stopping, syscall.SIGINT, syscall.SIGTERM)
	select {
	case <-stopping:
	case err = <-serveResult:
		return fmt.Errorf("HTTP server stopped before shutdown signal: %w", err)
	}
	signal.Stop(stopping)

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	if err := server.Shutdown(ctx); err != nil {
		return err
	}
	if err = <-serveResult; !errors.Is(err, http.ErrServerClosed) {
		return err
	}
	return json.NewEncoder(os.Stdout).Encode(map[string]any{
		"stopped": true, "handler_errors": handlerErrors.Load(),
	})
}

func WriteFixed(response http.ResponseWriter) {
	response.Header().Set("Content-Length", "32")
	response.WriteHeader(http.StatusOK)
	if _, err := response.Write(Payload); err != nil {
		handlerErrors.Add(1)
		fmt.Fprintln(os.Stderr, err)
	}
}
