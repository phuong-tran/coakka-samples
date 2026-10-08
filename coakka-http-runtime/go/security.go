package main

import (
	"crypto/tls"
	"crypto/x509"
	"errors"
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
			Protocol:    coakkahttp.ProtocolHTTP11,
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
			return errors.Join(fmt.Errorf("read %s port: %w", sample.name, portErr), closeService(service, sample.name))
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
				return errors.Join(fmt.Errorf("mutual TLS accepted a client without an identity"), closeService(service, sample.name))
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
		var received []byte
		if requestErr == nil {
			received, requestErr = io.ReadAll(io.LimitReader(response.Body, 4097))
			requestErr = errors.Join(requestErr, response.Body.Close())
		}
		client.CloseIdleConnections()
		// Exercise the runtime-owned client lane too; the Go TLS client above
		// is an independent inbound verifier, not the outbound implementation.
		var outboundErr error
		if requestErr == nil {
			outboundErr = demonstrateSecureOutbound(directory, caBytes, port, sample.security, sample.body)
		}
		closeErr := closeService(service, sample.name)
		if requestErr != nil || response == nil || response.StatusCode != http.StatusOK ||
			string(received) != sample.body {
			return errors.Join(fmt.Errorf("%s request failed: status=%v body=%q error=%v", sample.name,
				func() int {
					if response == nil {
						return 0
					}
					return response.StatusCode
				}(),
				received, requestErr), closeErr)
		}
		if err := errors.Join(outboundErr, closeErr); err != nil {
			return fmt.Errorf("%s outbound/close: %w", sample.name, err)
		}
	}
	fmt.Println("go-security-smoke=pass")
	return nil
}

// demonstrateSecureOutbound gives Core immutable, generation-keyed trust and
// optional client identity. It connects to a numeric address but verifies the
// certificate against localhost; neither the connector nor this sample disables
// peer verification or reimplements TLS. Fixture keys are test-only and are
// never printed in diagnostics. The outbound owner closes before its target.
func demonstrateSecureOutbound(directory string, caPEM []byte, port uint16,
	security coakkahttp.TransportSecurity, expectedBody string) (result error) {
	endpoint := coakkahttp.OutboundEndpoint{
		NodeID: "secure-loopback", ConnectHost: "127.0.0.1", ConnectPort: port,
		HTTPAuthority: fmt.Sprintf("localhost:%d", port), Security: security,
		TLSPeerIdentity: "localhost", TLSTrustGeneration: 11,
	}
	builder := coakkahttp.NewBuilder().
		Get("/health", textHandler(http.StatusOK, "ready")).
		OutboundTrust(coakkahttp.OutboundTrust{Generation: 11, CAPEM: caPEM})
	if security == coakkahttp.SecurityMutualTLS {
		certificate, err := os.ReadFile(filepath.Join(directory, "client.pem"))
		if err != nil {
			return err
		}
		key, err := os.ReadFile(filepath.Join(directory, "client.key"))
		if err != nil {
			return err
		}
		endpoint.TLSClientIdentityGeneration = 12
		builder.OutboundIdentity(coakkahttp.OutboundIdentity{
			Generation: 12, CertificateChainPEM: certificate, PrivateKeyPEM: key,
		})
	}
	owner, err := builder.OutboundTarget(coakkahttp.OutboundTarget{
		Name: "sample.secure", Generation: 3, Endpoints: []coakkahttp.OutboundEndpoint{endpoint},
	}).Start()
	if err != nil {
		return fmt.Errorf("start secure outbound owner: %w", err)
	}
	defer func() { result = errors.Join(result, closeService(owner, "secure outbound")) }()
	call, err := owner.SubmitOutbound(coakkahttp.ClientRequest{
		TimeoutMillis: 3000, LogicalTarget: "sample.secure", Method: http.MethodGet, Target: "/secure",
	})
	if err != nil {
		return err
	}
	terminal, err := owner.TakeOutbound(5 * time.Second)
	if err != nil {
		return err
	}
	// Match the complete received response and Core-selected target facts.
	// No transport cause is inferred from status or diagnostic text.
	if terminal == nil || terminal.Call != call || terminal.Reason != coakkahttp.OutboundResponse || terminal.ResponseStatus != http.StatusOK ||
		terminal.TargetGeneration != 3 || terminal.SelectedNodeID != "secure-loopback" ||
		terminal.LogicalTarget != "sample.secure" || string(terminal.ResponseBody) != expectedBody {
		return errors.New("secure outbound did not return the expected response and target identity")
	}
	if extra, err := owner.TakeOutbound(0); err != nil || extra != nil {
		return errors.Join(errors.New("secure outbound terminal was not consumed exactly once"), err)
	}
	return nil
}
