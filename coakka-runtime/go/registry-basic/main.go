package main

import (
	"errors"
	"fmt"
	"os"
	"time"

	coakka "github.com/phuong-tran/coakka-runtime-go/v3"
)

func run() (err error) {
	target := "samples.echo"
	host, err := coakka.StartRuntimeHost(coakka.ConnectorStartSpec{
		SystemName: "go-first-run", NodeID: "go-first-run-node",
		Routes: []coakka.RouteSpec{coakka.LocalRouteDefault(target)},
	}, "")
	if err != nil {
		return err
	}
	defer func() { err = errors.Join(err, host.Close()) }()
	if err := host.RegisterPayloadHandler(target, func(input []byte) []byte {
		return append([]byte("accepted-"), input...)
	}); err != nil {
		return err
	}
	reply, err := host.AskPayload("first-run", target, []byte("hello"),
		2*time.Second, coakka.DeliveryHintRouterDefault)
	if err != nil {
		return err
	}
	if string(reply) != "accepted-hello" {
		return fmt.Errorf("unexpected reply: %q", reply)
	}
	fmt.Println(string(reply))
	return nil
}

func main() {
	if err := run(); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}
