package main

import (
	"errors"
	"fmt"
	"io"
	"log"
	"net/http"
	"time"

	coakkahttp "github.com/phuong-tran/coakka-http-runtime-go"
)

func run() (err error) {
	service, err := coakkahttp.NewBuilder().Get("/hello",
		func(*coakkahttp.Request) (coakkahttp.Response, error) {
			return coakkahttp.Text(200, "hello")
		}).Start()
	if err != nil {
		return err
	}
	defer func() { err = errors.Join(err, service.Close()) }()

	port, err := service.Port()
	if err != nil {
		return err
	}
	client := &http.Client{Timeout: 5 * time.Second}
	reply, err := client.Get(fmt.Sprintf("http://127.0.0.1:%d/hello", port))
	if err != nil {
		return err
	}
	defer reply.Body.Close()
	body, err := io.ReadAll(io.LimitReader(reply.Body, 6))
	if err != nil {
		return err
	}
	if reply.StatusCode != http.StatusOK || string(body) != "hello" {
		return fmt.Errorf("unexpected HTTP response: %s %q", reply.Status, body)
	}
	return nil
}

func main() {
	if err := run(); err != nil {
		log.Fatal(err)
	}
	fmt.Println("HTTP 200 hello")
}
