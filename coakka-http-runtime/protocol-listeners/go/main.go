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
		log.Fatal("usage: server http1|http2|http3|multi certificate-directory")
	}
	builder := coakkahttp.NewBuilder()
	certificate := filepath.Join(os.Args[2], "server.pem")
	key := filepath.Join(os.Args[2], "server.key")
	switch os.Args[1] {
	case "http1":
		builder.Listen("127.0.0.1", 8080)
	case "http2", "http3":
		listener := coakkahttp.Listener{
			BindAddress: "127.0.0.1", Port: 8443,
			Protocol: coakkahttp.ProtocolHTTP2, Security: coakkahttp.SecurityTLS,
			CredentialGeneration: 1, CredentialID: "local-example",
			CertificateChainFile: certificate, PrivateKeyFile: key,
		}
		if os.Args[1] == "http3" {
			listener.Protocol = coakkahttp.ProtocolHTTP3
		}
		builder.Listener(listener)
	case "multi":
		builder.MultiProtocolListener(coakkahttp.MultiProtocolListener{
			BindAddress: "127.0.0.1", Port: 8443,
			CredentialGeneration: 1, CredentialID: "local-example",
			CertificateChainFile: certificate, PrivateKeyFile: key,
		})
	default:
		log.Fatal("choose http1, http2, http3 or multi")
	}
	stopped := make(chan os.Signal, 1)
	signal.Notify(stopped, os.Interrupt, syscall.SIGTERM)
	defer signal.Stop(stopped)
	service, err := builder.Get("/hello",
		func(*coakkahttp.Request) (coakkahttp.Response, error) {
			return coakkahttp.Text(200, "hello")
		}).Start()
	if err != nil {
		log.Fatal(err)
	}
	port, err := service.Port()
	if err != nil {
		log.Fatal(err)
	}
	fmt.Printf("listening on 127.0.0.1:%d\n", port)
	<-stopped
	if err := service.Close(); err != nil {
		log.Fatal(err)
	}
}
