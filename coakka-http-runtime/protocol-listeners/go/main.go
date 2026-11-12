package main

import (
	"fmt"
	"log"
	"os"
	"os/signal"
	"path/filepath"
	"syscall"

	coakkahttp "github.com/phuong-tran/coakka-http-runtime-go/v2"
)

func main() {
	if len(os.Args) != 3 {
		log.Fatal("usage: server http1|http2|http3 certificate-directory")
	}
	listener := coakkahttp.Listener{BindAddress: "127.0.0.1", Port: 8080, Protocol: coakkahttp.ProtocolHTTP11, Security: coakkahttp.SecurityPlaintext}
	switch os.Args[1] {
	case "http1":
	case "http2":
		listener.Protocol = coakkahttp.ProtocolHTTP2
	case "http3":
		listener.Protocol = coakkahttp.ProtocolHTTP3
	default:
		log.Fatal("choose http1, http2 or http3")
	}
	if os.Args[1] != "http1" {
		listener.Port = 8443
		listener.Security = coakkahttp.SecurityTLS
		listener.CredentialGeneration = 1
		listener.CredentialID = "local-example"
		listener.CertificateChainFile = filepath.Join(os.Args[2], "server.pem")
		listener.PrivateKeyFile = filepath.Join(os.Args[2], "server.key")
	}
	stopped := make(chan os.Signal, 1)
	signal.Notify(stopped, os.Interrupt, syscall.SIGTERM)
	defer signal.Stop(stopped)
	service, err := coakkahttp.NewBuilder().Listener(listener).Get("/hello",
		func(*coakkahttp.Request) (coakkahttp.Response, error) {
			return coakkahttp.Text(200, "hello")
		}).Start()
	if err != nil {
		log.Fatal(err)
	}
	fmt.Printf("listening on 127.0.0.1:%d\n", listener.Port)
	<-stopped
	if err := service.Close(); err != nil {
		log.Fatal(err)
	}
}
