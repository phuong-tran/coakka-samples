package main

import (
	"context"
	"errors"
	"fmt"
	"net/http"
	"os"
	"os/signal"
	"path/filepath"
	"syscall"

	coakkahttp "github.com/phuong-tran/coakka-http-runtime-go"
)

// runProtocolServer changes only the public listener declaration. Core owns
// protocol negotiation, sockets, TLS and shutdown; handlers stay ordinary Go.
// Certificates are generated test identities, never production credentials.
func runProtocolServer(name, fixtures string) (result error) {
	var protocol coakkahttp.ListenerProtocol
	switch name {
	case "http2":
		protocol = coakkahttp.ProtocolHTTP2
	case "http3":
		protocol = coakkahttp.ProtocolHTTP3
	default:
		return fmt.Errorf("unknown protocol sample %q; choose http2 or http3", name)
	}
	if fixtures == "" {
		return errors.New("protocol sample requires a test certificate directory")
	}
	service, err := coakkahttp.NewBuilder().Listener(coakkahttp.Listener{
		Protocol: protocol, BindAddress: "127.0.0.1", Security: coakkahttp.SecurityTLS,
		CredentialGeneration: 1, CredentialID: "go-protocol-sample",
		CertificateChainFile: filepath.Join(fixtures, "server.pem"),
		PrivateKeyFile:       filepath.Join(fixtures, "server.key"),
	}).Get("/protocol", func(*coakkahttp.Request) (coakkahttp.Response, error) {
		// A diagnostic admission marker contains no user request or credential.
		fmt.Println("go-protocol-requested")
		return coakkahttp.Text(http.StatusOK, "protocol-ready")
	}).Start()
	if err != nil {
		return err
	}
	defer func() { result = errors.Join(result, closeService(service, "protocol sample")) }()
	port, err := service.Port()
	if err != nil {
		return err
	}
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()
	fmt.Printf("go-protocol=%s https://localhost:%d/protocol\n", name, port)
	<-ctx.Done()
	// Keep protocol clients progressing during graceful drain. If a peer
	// disappears and Core reports expiry, preserve that close failure.
	return nil
}
