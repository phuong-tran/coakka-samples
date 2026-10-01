// Command fixed-server serves one identical 32-byte response through CoAkka,
// Chi, or Gin. There is intentionally no net/http-only benchmark lane.
package main

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

	"github.com/gin-gonic/gin"
	"github.com/go-chi/chi/v5"
	coakkahttp "github.com/phuong-tran/coakka-http-runtime-go"
)

var body = []byte("0123456789abcdef0123456789abcdef")

func main() {
	if len(os.Args) != 3 {
		fatal(fmt.Errorf("usage: fixed-server <coakka|chi|gin> <port>"))
	}
	port, err := strconv.Atoi(os.Args[2])
	if err != nil || port < 1 || port > 65535 {
		fatal(fmt.Errorf("invalid port"))
	}
	if os.Args[1] == "coakka" {
		runCoakka(uint16(port))
		return
	}
	runFramework(os.Args[1], port)
}

func runCoakka(port uint16) {
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
		Concurrency(1).
		Get("/fixed", func(*coakkahttp.Request) (coakkahttp.Response, error) {
			return response, nil
		}).Start()
	if err != nil {
		fatal(err)
	}
	waitForStop()
	if err = service.Close(); err != nil {
		fatal(err)
	}
}

func runFramework(mode string, port int) {
	var handler http.Handler
	switch mode {
	case "chi":
		router := chi.NewRouter()
		router.Get("/fixed", fixed)
		handler = router
	case "gin":
		gin.SetMode(gin.ReleaseMode)
		router := gin.New()
		router.GET("/fixed", func(context *gin.Context) {
			context.Data(http.StatusOK, "application/octet-stream", body)
		})
		handler = router
	default:
		fatal(fmt.Errorf("unsupported Go lane %q", mode))
	}
	server := &http.Server{
		Addr:              fmt.Sprintf("127.0.0.1:%d", port),
		Handler:           handler,
		ReadHeaderTimeout: 5 * time.Second,
	}
	failed := make(chan error, 1)
	go func() { failed <- server.ListenAndServe() }()
	stopping := make(chan os.Signal, 1)
	signal.Notify(stopping, os.Interrupt, syscall.SIGTERM)
	select {
	case err := <-failed:
		signal.Stop(stopping)
		fatal(fmt.Errorf("framework listener stopped before signal: %w", err))
	case <-stopping:
		signal.Stop(stopping)
		ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		if err := server.Shutdown(ctx); err != nil {
			fatal(err)
		}
		select {
		case err := <-failed:
			if !errors.Is(err, http.ErrServerClosed) {
				fatal(err)
			}
		case <-time.After(5 * time.Second):
			fatal(fmt.Errorf("framework server did not stop before the deadline"))
		}
	}
}

func fixed(writer http.ResponseWriter, _ *http.Request) {
	writer.Header().Set("Content-Type", "application/octet-stream")
	writer.Header().Set("Content-Length", strconv.Itoa(len(body)))
	writer.WriteHeader(http.StatusOK)
	_, _ = writer.Write(body)
}

func waitForStop() {
	stopping := make(chan os.Signal, 1)
	signal.Notify(stopping, os.Interrupt, syscall.SIGTERM)
	<-stopping
	signal.Stop(stopping)
}

func fatal(err error) {
	fmt.Fprintln(os.Stderr, err)
	os.Exit(1)
}
