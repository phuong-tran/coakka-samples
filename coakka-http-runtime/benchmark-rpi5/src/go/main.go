package main

import (
	"context"
	"encoding/json"
	"fmt"
	"os"
	"os/signal"
	"syscall"
	"time"

	coakkahttp "github.com/phuong-tran/coakka-publish/coakka-http-runtime/go"
)

var payload = []byte("0123456789abcdef0123456789abcdef")

func main() {
	response := coakkahttp.Bytes(200, payload)
	service, err := coakkahttp.NewBuilder().
		Listen("127.0.0.1", 0).
		Get("/fixed", func(request *coakkahttp.Request) (coakkahttp.Response, error) {
			return response, nil
		}).
		Start()
	if err != nil {
		fatal(err)
	}
	port, err := service.Port()
	if err != nil {
		ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		_ = service.Close(ctx)
		fatal(err)
	}
	printJSON(map[string]any{
		"ready": true, "bound_port": port, "application_path": "normal",
	})

	stopping := make(chan os.Signal, 1)
	signal.Notify(stopping, syscall.SIGINT, syscall.SIGTERM)
	<-stopping
	signal.Stop(stopping)
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	if err = service.Close(ctx); err != nil {
		fatal(err)
	}
	errors := 0
	for service.PollError() != nil {
		errors++
	}
	printJSON(map[string]any{"stopped": true, "handler_errors": errors})
	if errors != 0 {
		os.Exit(1)
	}
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
