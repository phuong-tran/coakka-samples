// Command http-sample demonstrates the public Go service API with finite
// resource limits, application routes, streaming, realtime responses, files,
// monitoring, outbound HTTP, and generation-checked handler activation.
package main

import (
	"context"
	"errors"
	"flag"
	"fmt"
	"io"
	"net/http"
	"os"
	"os/signal"
	"path/filepath"
	"strings"
	"syscall"
	"time"

	coakkahttp "github.com/phuong-tran/coakka-http-runtime-go"
)

const outboundTarget = "sample.upstream"

func main() {
	if err := run(); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}

// run owns both services and always closes them in reverse construction order.
func run() error {
	var smoke bool
	var ioUring bool
	var assets string
	var securityFixtures string
	flag.BoolVar(&smoke, "smoke", false, "exercise representative routes and exit")
	flag.BoolVar(&ioUring, "io-uring", false, "request io_uring and allow native fallback to epoll")
	flag.StringVar(&assets, "assets", "../assets", "static and application-file root")
	flag.StringVar(&securityFixtures, "security-smoke", "", "exercise TLS and mutual TLS using this fixture directory")
	flag.Parse()
	if securityFixtures != "" {
		return runSecuritySmoke(securityFixtures)
	}

	root, err := filepath.Abs(assets)
	if err != nil {
		return fmt.Errorf("resolve asset root: %w", err)
	}
	if err = demonstrateRoutePublication(); err != nil {
		return err
	}

	upstream, err := coakkahttp.NewBuilder().
		Get("/source", textHandler(200, "outbound-ready")).
		Start()
	if err != nil {
		return fmt.Errorf("start outbound target: %w", err)
	}
	defer upstream.Close()
	upstreamPort, err := upstream.Port()
	if err != nil {
		return fmt.Errorf("read outbound target port: %w", err)
	}

	service, err := newService(root, upstreamPort, ioUring)
	if err != nil {
		return err
	}
	defer service.Close()

	if err = activateReplacement(service); err != nil {
		return err
	}
	if err = demonstrateOutbound(service); err != nil {
		return err
	}
	if err = printObservability(service); err != nil {
		return err
	}

	port, err := service.Port()
	if err != nil {
		return fmt.Errorf("read service port: %w", err)
	}
	fmt.Printf("go-sample=http://127.0.0.1:%d\n", port)
	if smoke {
		return smokeService(port)
	}

	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()
	<-ctx.Done()
	return nil
}

// newService declares all resources before the native listener is published.
// The filesystem roots and handler functions remain application-owned until
// Close returns.
func newService(root string, upstreamPort uint16, ioUring bool) (*coakkahttp.Service, error) {
	contentType, err := coakkahttp.NewHeader("content-type", "text/plain; charset=utf-8")
	if err != nil {
		return nil, err
	}
	textHeaders, err := coakkahttp.NewHeaders(contentType)
	if err != nil {
		return nil, err
	}

	stream := func(_ *coakkahttp.Request) (coakkahttp.Response, error) {
		return coakkahttp.NewStreamingResponse(200, textHeaders, func(writer *coakkahttp.ResponseStreamWriter) (coakkahttp.Headers, error) {
			if _, writeErr := writer.Write([]byte("stream-")); writeErr != nil {
				return coakkahttp.Headers{}, writeErr
			}
			if _, writeErr := writer.Write([]byte("ready")); writeErr != nil {
				return coakkahttp.Headers{}, writeErr
			}
			trailer, headerErr := coakkahttp.NewHeader("x-stream-end", "done")
			if headerErr != nil {
				return coakkahttp.Headers{}, headerErr
			}
			return coakkahttp.NewHeaders(trailer)
		})
	}

	events := func(_ *coakkahttp.Request) (coakkahttp.Response, error) {
		return coakkahttp.NewSSEResponse(coakkahttp.Headers{}, func(writer *coakkahttp.SSEWriter) error {
			return writer.Write(coakkahttp.SSEEvent{
				Data: []byte("ready"), EventType: "state", ID: "1", RetryMillis: 1500,
				HasEventType: true, HasID: true, HasRetry: true,
			})
		})
	}

	socket := func(_ *coakkahttp.Request) (coakkahttp.Response, error) {
		return coakkahttp.NewWebSocketResponse("coakka.sample", func(session *coakkahttp.WebSocketSession, event coakkahttp.SocketEvent) error {
			switch event.Kind {
			case coakkahttp.WebSocketOpen:
				return session.SendText("welcome")
			case coakkahttp.WebSocketText:
				return session.SendText(string(event.Data))
			default:
				return nil
			}
		})
	}

	endpoint := coakkahttp.OutboundEndpoint{
		NodeID: "loopback", ConnectHost: "127.0.0.1", ConnectPort: upstreamPort,
		HTTPAuthority: fmt.Sprintf("127.0.0.1:%d", upstreamPort),
		Security:      coakkahttp.SecurityPlaintext, ConnectionStrategyGeneration: 1,
	}

	builder := coakkahttp.NewBuilder().
		Concurrency(2).
		Limits(coakkahttp.DefaultLimits()).
		Monitor(coakkahttp.MonitorOptions{
			Collection:    coakkahttp.MonitorAggregatesAndEvents,
			EventCapacity: 32, MaxEventsPerRead: 8,
			AggregateCategories: coakkahttp.MonitorCategoryLifecycle | coakkahttp.MonitorCategoryExchange,
			EventCategories:     coakkahttp.MonitorCategoryLifecycle, SignalReserved: true,
		}).
		StaticMount(coakkahttp.StaticMount{
			ID: 81, URLPrefix: "/app", RootPath: root,
			IndexFile: "index.html", SPAFallbackFile: "index.html",
			HasIndexFile: true, HasSPAFallbackFile: true,
		}).
		FileAuthority(coakkahttp.FileAuthority{ID: 82, RootPath: root, MaxActiveFiles: 2, MaxFileBytes: 1 << 20}).
		OutboundTarget(coakkahttp.OutboundTarget{Name: outboundTarget, Generation: 1, Endpoints: []coakkahttp.OutboundEndpoint{endpoint}}).
		Get("/hello/{name}", func(request *coakkahttp.Request) (coakkahttp.Response, error) {
			name := "world"
			if len(request.PathParameters) != 0 {
				name = request.PathParameters[0].EncodedValue
			}
			title := "hello"
			if len(request.QueryParameters) != 0 && request.QueryParameters[0].EncodedKey == "title" &&
				request.QueryParameters[0].HasValue {
				title = request.QueryParameters[0].EncodedValue
			}
			caller, _ := request.Headers.Get("x-sample-caller")
			return coakkahttp.Text(200, fmt.Sprintf("%s %s from %s", title, name, caller))
		}).
		Post("/echo", func(request *coakkahttp.Request) (coakkahttp.Response, error) {
			return coakkahttp.Bytes(201, request.Bytes())
		}).
		PostStream("/upload", func(request *coakkahttp.Request) (coakkahttp.Response, error) {
			body, readErr := io.ReadAll(request.Body)
			if readErr != nil {
				return coakkahttp.Response{}, readErr
			}
			return coakkahttp.Bytes(200, body)
		}).
		Get("/stream", stream).
		Get("/events", events).
		WebSocket("/socket", socket).
		Get("/download", func(_ *coakkahttp.Request) (coakkahttp.Response, error) {
			return coakkahttp.ServeFile(200, textHeaders, 82, "/sample.txt")
		}).
		Get("/version", textHandler(200, "v1"))
	if ioUring {
		builder.IOBackend(coakkahttp.IOUring)
	}
	return builder.Start()
}

// textHandler is intentionally small: handler errors stay typed and are
// surfaced by Service instead of being converted to an accidental empty body.
func textHandler(status uint32, body string) coakkahttp.Handler {
	return func(_ *coakkahttp.Request) (coakkahttp.Response, error) {
		return coakkahttp.Text(status, body)
	}
}

// activateReplacement demonstrates a handler-only update. The route shape is
// unchanged and any already admitted request keeps its captured v1 binding.
func activateReplacement(service *coakkahttp.Service) error {
	if err := service.PrepareHandler(9, textHandler(201, "v2")); err != nil {
		return fmt.Errorf("prepare replacement: %w", err)
	}
	outcome, err := service.RebindHandler(coakkahttp.RebindRequest{
		ActivationID: 1, ExpectedRouteGeneration: 1, RouteID: 8,
		ExpectedBindingRevision: 1, NewHandlerBindingID: 9,
	}, 2*time.Second)
	if err != nil {
		return fmt.Errorf("activate replacement: %w", err)
	}
	if outcome.Code != coakkahttp.RebindApplied || !outcome.Changed {
		return fmt.Errorf("replacement rejected with code %d", outcome.Code)
	}
	return nil
}

// demonstrateRoutePublication replaces a complete route generation on an
// isolated service. Structural publication is intentionally separate from a
// handler-only rebind because it has different generation and replay guards.
func demonstrateRoutePublication() error {
	limits := coakkahttp.DefaultLimits()
	limits.MaxHandlerBindings = 3
	service, err := coakkahttp.NewBuilder().Limits(limits).
		Get("/old", textHandler(200, "old-generation")).Start()
	if err != nil {
		return fmt.Errorf("start route publication sample: %w", err)
	}
	defer service.Close()
	if err = service.PrepareHandler(2, textHandler(201, "new-generation")); err != nil {
		return fmt.Errorf("prepare published route: %w", err)
	}
	publication := coakkahttp.RoutePublication{
		ActivationID: 1, ExpectedRouteGeneration: 1,
		ExpectedMetadataGeneration: 1, ExpectedBindingChangeSequence: 1,
		Routes: []coakkahttp.Route{{
			ID: 2, HandlerBindingID: 2, Method: http.MethodGet, Pattern: "/published",
			BodyDelivery: coakkahttp.BodyInline,
			BodyPolicy: coakkahttp.BodyPolicy{
				Enabled: true, AcceptAbsent: true, AcceptOther: true,
				MaxBodyBytes: limits.MaxRequestBodyBytes,
			},
		}},
	}
	outcome, err := service.PublishRoutes(publication, 2*time.Second)
	if err != nil {
		return fmt.Errorf("publish route generation: %w", err)
	}
	if outcome.Code != coakkahttp.RoutePublicationApplied || !outcome.Changed {
		return fmt.Errorf("route generation rejected: outcome=%+v", outcome)
	}
	port, err := service.Port()
	if err != nil {
		return err
	}
	response, err := (&http.Client{Timeout: 5 * time.Second}).Get(
		fmt.Sprintf("http://127.0.0.1:%d/published", port),
	)
	if err != nil {
		return fmt.Errorf("request published route: %w", err)
	}
	defer response.Body.Close()
	body, err := io.ReadAll(response.Body)
	if err != nil || response.StatusCode != 201 || string(body) != "new-generation" {
		return fmt.Errorf("published route mismatch: status=%d body=%q error=%v", response.StatusCode, body, err)
	}
	return nil
}

// demonstrateOutbound uses the runtime-owned client lane and consumes the one
// terminal reader synchronously before returning.
func demonstrateOutbound(service *coakkahttp.Service) error {
	call, err := service.SubmitOutbound(coakkahttp.ClientRequest{
		TimeoutMillis: 3000, LogicalTarget: outboundTarget, Method: "GET", Target: "/source",
	})
	if err != nil {
		return fmt.Errorf("submit outbound request: %w", err)
	}
	terminal, err := service.TakeOutbound(5 * time.Second)
	if err != nil {
		return fmt.Errorf("take outbound result: %w", err)
	}
	if terminal == nil || terminal.Call != call || terminal.ResponseStatus != 200 || string(terminal.ResponseBody) != "outbound-ready" {
		return errors.New("outbound result did not match the declared target")
	}
	return nil
}

// printObservability reads pointer-free snapshots; monitoring storage remains
// bounded by the construction-time declaration above.
func printObservability(service *coakkahttp.Service) error {
	health, err := service.ProbeLiveness(time.Second)
	if err != nil {
		return fmt.Errorf("probe liveness: %w", err)
	}
	page, err := service.ReadMonitorEvents(0, 8)
	if err != nil {
		return fmt.Errorf("read monitor events: %w", err)
	}
	info, err := service.RuntimeInfo()
	if err != nil {
		return fmt.Errorf("read runtime information: %w", err)
	}
	fmt.Printf(
		"readiness=%d monitor-latest=%d retained=%d io-uring-requested=%t io-uring-effective=%t\n",
		health.Readiness, page.LatestSequence, len(page.Events),
		info.RequestedIOBackend == coakkahttp.IOUring,
		info.EffectiveIOBackend == coakkahttp.IOUring,
	)
	return nil
}

// smokeService validates representative buffered, streaming, static-file and
// live-handler paths through the loopback listener before graceful shutdown.
func smokeService(port uint16) error {
	client := &http.Client{Timeout: 5 * time.Second}
	cases := []struct {
		method, path, body string
		status             int
		want               string
	}{
		{"GET", "/hello/reader?title=hello", "", 200, "hello reader from smoke"},
		{"POST", "/echo", "payload", 201, "payload"},
		{"POST", "/upload", "streamed", 200, "streamed"},
		{"GET", "/stream", "", 200, "stream-ready"},
		{"GET", "/events", "", 200, "data: ready"},
		{"GET", "/download", "", 200, "confined application file"},
		{"GET", "/app/client/route", "", 200, "CoAkka HTTP Runtime"},
		{"GET", "/version", "", 201, "v2"},
	}
	for _, item := range cases {
		request, err := http.NewRequest(item.method, fmt.Sprintf("http://127.0.0.1:%d%s", port, item.path), strings.NewReader(item.body))
		if err != nil {
			return err
		}
		if item.path == "/app/client/route" {
			request.Header.Set("accept", "text/html")
		}
		if strings.HasPrefix(item.path, "/hello/") {
			request.Header.Set("x-sample-caller", "smoke")
		}
		response, err := client.Do(request)
		if err != nil {
			return fmt.Errorf("%s %s: %w", item.method, item.path, err)
		}
		body, readErr := io.ReadAll(response.Body)
		response.Body.Close()
		if readErr != nil || response.StatusCode != item.status || !strings.Contains(string(body), item.want) {
			return fmt.Errorf("%s %s: status=%d body=%q", item.method, item.path, response.StatusCode, body)
		}
	}
	fmt.Println("go-smoke=pass")
	return nil
}
