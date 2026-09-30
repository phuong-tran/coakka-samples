package main

import (
	"crypto/tls"
	"crypto/x509"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"time"

	coakkahttp "github.com/phuong-tran/coakka-http-runtime-go"
)

// runSecuritySmoke proves both server-authenticated TLS and mandatory client
// identity. Production deployments should load identities from their normal
// secret manager and advance CredentialGeneration when rotating them.
func runSecuritySmoke(directory string) error {
	caBytes, err := os.ReadFile(filepath.Join(directory, "ca.pem"))
	if err != nil {
		return fmt.Errorf("read sample CA: %w", err)
	}
	trust := x509.NewCertPool()
	if !trust.AppendCertsFromPEM(caBytes) {
		return fmt.Errorf("parse sample CA")
	}
	identity, err := tls.LoadX509KeyPair(
		filepath.Join(directory, "client.pem"),
		filepath.Join(directory, "client.key"),
	)
	if err != nil {
		return fmt.Errorf("load sample client identity: %w", err)
	}

	for _, sample := range []struct {
		name       string
		security   coakkahttp.TransportSecurity
		generation uint64
		body       string
	}{
		{name: "tls", security: coakkahttp.SecurityTLS, generation: 7, body: "tls-ready"},
		{name: "mtls", security: coakkahttp.SecurityMutualTLS, generation: 9, body: "mtls-ready"},
	} {
		listener := coakkahttp.Listener{
			ID: 1, Protocol: coakkahttp.ProtocolHTTP11,
			BindAddress: "127.0.0.1", Security: sample.security,
			CredentialGeneration: sample.generation,
			CredentialID:         "go-sample-server",
			CertificateChainFile: filepath.Join(directory, "server.pem"),
			PrivateKeyFile:       filepath.Join(directory, "server.key"),
		}
		if sample.security == coakkahttp.SecurityMutualTLS {
			listener.TrustRootsFile = filepath.Join(directory, "ca.pem")
		}
		service, startErr := coakkahttp.NewBuilder().Listener(listener).
			Get("/secure", textHandler(http.StatusOK, sample.body)).Start()
		if startErr != nil {
			return fmt.Errorf("start %s service: %w", sample.name, startErr)
		}
		port, portErr := service.Port()
		if portErr != nil {
			_ = service.Close()
			return fmt.Errorf("read %s port: %w", sample.name, portErr)
		}
		configuration := &tls.Config{ // #nosec G402 -- TLS 1.2 is the sample floor.
			MinVersion: tls.VersionTLS12,
			RootCAs:    trust,
			ServerName: "localhost",
		}
		if sample.security == coakkahttp.SecurityMutualTLS {
			unauthenticated := &http.Client{
				Timeout:   5 * time.Second,
				Transport: &http.Transport{TLSClientConfig: configuration.Clone()},
			}
			unexpected, unauthenticatedErr := unauthenticated.Get(
				fmt.Sprintf("https://localhost:%d/secure", port),
			)
			unauthenticated.CloseIdleConnections()
			if unexpected != nil {
				_ = unexpected.Body.Close()
			}
			if unauthenticatedErr == nil {
				_ = service.Close()
				return fmt.Errorf("mutual TLS accepted a client without an identity")
			}
			configuration.Certificates = []tls.Certificate{identity}
		}
		client := &http.Client{
			Timeout:   5 * time.Second,
			Transport: &http.Transport{TLSClientConfig: configuration},
		}
		response, requestErr := client.Get(
			fmt.Sprintf("https://localhost:%d/secure", port),
		)
		if requestErr == nil {
			defer response.Body.Close()
		}
		var received []byte
		if requestErr == nil {
			received, requestErr = io.ReadAll(response.Body)
		}
		client.CloseIdleConnections()
		closeErr := service.Close()
		if requestErr != nil || response == nil || response.StatusCode != http.StatusOK ||
			string(received) != sample.body {
			return fmt.Errorf("%s request failed: status=%v body=%q error=%v", sample.name,
				func() int {
					if response == nil {
						return 0
					}
					return response.StatusCode
				}(),
				received, requestErr)
		}
		if closeErr != nil {
			return fmt.Errorf("close %s service: %w", sample.name, closeErr)
		}
	}
	fmt.Println("go-security-smoke=pass")
	return nil
}
