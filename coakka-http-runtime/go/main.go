// Command http-sample demonstrates the public Go service API with finite
// resource limits, application routes, streaming, realtime responses, files,
// monitoring, outbound HTTP, and generation-checked handler activation.
package main

import (
	"context"
	"encoding/json"
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
func run() (result error) {
	var smoke bool
	var adverse bool
	var ioUring bool
	var assets string
	var securityFixtures string
	var protocol, protocolFixtures string
	var tuning, singleCPU, compression bool
	flag.BoolVar(&smoke, "smoke", false, "exercise representative routes and exit")
	flag.BoolVar(&adverse, "adverse-smoke", false, "exercise bounded deadline and pressure failures and exit")
	flag.BoolVar(&ioUring, "io-uring", false, "request io_uring and allow native fallback to epoll")
	flag.StringVar(&assets, "assets", "../assets", "static and application-file root")
	flag.StringVar(&securityFixtures, "security-smoke", "", "exercise TLS and mutual TLS using this fixture directory")
	flag.StringVar(&protocol, "protocol", "", "run a TLS listener sample: http2 or http3")
	flag.StringVar(&protocolFixtures, "protocol-fixtures", "", "test certificate directory for the protocol listener")
	flag.BoolVar(&tuning, "tuning", false, "demonstrate explicit batch and transport timeout settings")
	flag.BoolVar(&singleCPU, "single-cpu", false, "request Core SINGLE placement; unsupported hosts refuse")
	flag.BoolVar(&compression, "compression", false, "enable Core-owned negotiated GZIP responses")
	flag.Parse()
	if protocol != "" {
		return runProtocolServer(protocol, protocolFixtures)
	}
	if adverse {
		return runAdverseSmoke()
	}
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
	// Keep close refusal as an application outcome, including when an earlier
	// operation failed. Do not restart or report success after refused drain.
	defer func() { result = errors.Join(result, closeService(upstream, "upstream")) }()
	upstreamPort, err := upstream.Port()
	if err != nil {
		return fmt.Errorf("read outbound target port: %w", err)
	}

	service, err := newService(root, upstreamPort, ioUring, tuning, singleCPU, compression)
	if err != nil {
		return err
	}
	defer func() { result = errors.Join(result, closeService(service, "application")) }()
	if err = demonstrateMonitorReload(service); err != nil {
		return err
	}
	if err = verifySettings(service, tuning, singleCPU); err != nil {
		return err
	}

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
func newService(root string, upstreamPort uint16, ioUring, tuning, singleCPU, compression bool) (*coakkahttp.Service, error) {
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
				Data: []byte("ready\nsecond line"), EventType: "state", ID: "1", RetryMillis: 1000,
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
			case coakkahttp.WebSocketBinary:
				return session.SendBinary(event.Data)
			default:
				return nil
			}
		})
	}

	endpoint := coakkahttp.OutboundEndpoint{
		NodeID: "loopback", ConnectHost: "127.0.0.1", ConnectPort: upstreamPort,
		HTTPAuthority: fmt.Sprintf("127.0.0.1:%d", upstreamPort),
		Security:      coakkahttp.SecurityPlaintext,
	}

	limits := sampleLimits(tuning)
	builder := coakkahttp.NewBuilder().
		Concurrency(2).
		Limits(limits).
		Monitor(coakkahttp.MonitorOptions{
			Collection:    coakkahttp.MonitorAggregatesAndEvents,
			EventCapacity: 32, MaxEventsPerRead: 8,
			AggregateCategories: coakkahttp.MonitorCategoryLifecycle | coakkahttp.MonitorCategoryExchange,
			EventCategories:     coakkahttp.MonitorCategoryLifecycle, SignalReserved: true,
		}).
		StaticMount(coakkahttp.StaticMount{
			URLPrefix: "/app", RootPath: root,
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
			// This small echo intentionally collects a body. Core enforces the
			// declared 64 KiB ceiling before admission; this is not a recipe for
			// buffering arbitrary files. Read errors (including cancellation)
			// return immediately and release the handler's local buffer.
			fmt.Println("go-upload-reading")
			body, readErr := io.ReadAll(request.Body)
			if readErr != nil {
				// A body-read failure is not by itself proof of a particular
				// transport cause. This marker proves the blocked reader returned.
				fmt.Println("go-upload-read-failed")
				return coakkahttp.Response{}, readErr
			}
			trailer, _ := request.Trailers.Get("x-upload-check")
			header, err := coakkahttp.NewHeader("x-upload-observed", trailer)
			if err != nil {
				return coakkahttp.Response{}, err
			}
			headers, err := coakkahttp.NewHeaders(header)
			if err != nil {
				return coakkahttp.Response{}, err
			}
			return coakkahttp.NewResponse(200, headers, body)
		}).
		Get("/stream", stream).
		Get("/events", events).
		WebSocket("/socket", socket).
		Get("/download", func(_ *coakkahttp.Request) (coakkahttp.Response, error) {
			return coakkahttp.ServeFile(200, textHeaders, 82, "/sample.txt")
		}).
		Get("/version", textHandler(200, "v1")).
		Get("/parameters/{id}", parameterHandler)
	if ioUring {
		builder.IOBackend(coakkahttp.IOUring)
	}
	if singleCPU {
		builder.CPU(coakkahttp.CPUSingle)
	}
	if compression {
		// Only the tiny demonstration threshold/level are explicit. Core owns
		// workspace/size defaults and negotiates eligible responses; handlers
		// keep returning normal uncompressed application values.
		builder.Compression(&coakkahttp.Compression{
			Mode: coakkahttp.CompressionGZIP, MinimumBodyBytes: 1, GZIPLevel: 6,
		})
	}
	return builder.Start()
}

// parameterHandler demonstrates Core-parsed values without reparsing Target
// or folding duplicates into a map. Serialization is bounded by Core's input
// metadata ceilings. Only this demo's named header is reflected, not arbitrary
// headers, credentials or cookies.
func parameterHandler(request *coakkahttp.Request) (coakkahttp.Response, error) {
	value := struct {
		Path    []coakkahttp.PathParameter  `json:"path"`
		Query   []coakkahttp.QueryParameter `json:"query"`
		Headers []string                    `json:"headers"`
	}{request.PathParameters, request.QueryParameters, request.Headers.GetAll("x-sample-value")}
	body, err := json.Marshal(value)
	if err != nil {
		return coakkahttp.Response{}, err
	}
	header, err := coakkahttp.NewHeader("content-type", "application/json")
	if err != nil {
		return coakkahttp.Response{}, err
	}
	headers, err := coakkahttp.NewHeaders(header)
	if err != nil {
		return coakkahttp.Response{}, err
	}
	return coakkahttp.NewResponse(200, headers, body)
}

// textHandler is intentionally small: handler errors stay typed and are
// surfaced by Service instead of being converted to an accidental empty body.
func textHandler(status uint32, body string) coakkahttp.Handler {
	return func(_ *coakkahttp.Request) (coakkahttp.Response, error) {
		return coakkahttp.Text(status, body)
	}
}

// closeService preserves the connector's typed cause while naming the owner.
// This command exits unsuccessfully on refusal; it never guesses that timeout
// means native work stopped, frees native state itself, or starts a replacement.
func closeService(service *coakkahttp.Service, owner string) error {
	if err := service.Close(); err != nil {
		return fmt.Errorf("close %s service: %w", owner, err)
	}
	return nil
}

// activateReplacement demonstrates a handler-only update. The route shape is
// unchanged and any already admitted request keeps its captured v1 binding.
func activateReplacement(service *coakkahttp.Service) error {
	if err := service.PrepareHandler(100, textHandler(201, "v2")); err != nil {
		return fmt.Errorf("prepare replacement: %w", err)
	}
	outcome, err := service.RebindHandler(coakkahttp.RebindRequest{
		ActivationID: 1, ExpectedRouteGeneration: 1, RouteID: 8,
		ExpectedBindingRevision: 1, NewHandlerBindingID: 100,
	})
	if err != nil {
		return fmt.Errorf("activate replacement: %w", err)
	}
	if outcome.Code != coakkahttp.RebindApplied || !outcome.Changed {
		return fmt.Errorf("replacement rejected with code %d", outcome.Code)
	}
	// Core, not the application, decides whether this old revision can apply.
	// Keep the replacement binding prepared but prove it never becomes active.
	if err := service.PrepareHandler(101, textHandler(200, "unreachable")); err != nil {
		return err
	}
	refused, err := service.RebindHandler(coakkahttp.RebindRequest{
		ActivationID: 2, ExpectedRouteGeneration: 1, RouteID: 8,
		ExpectedBindingRevision: 1, NewHandlerBindingID: 101,
	})
	if err != nil {
		return err
	}
	if refused.Code != coakkahttp.RebindBindingRevisionMismatch || refused.Changed ||
		refused.EffectiveHandlerBindingID != outcome.EffectiveHandlerBindingID {
		return fmt.Errorf("stale replacement did not preserve active binding: %+v", refused)
	}
	return nil
}

// demonstrateRoutePublication replaces a complete route generation on an
// isolated service. Structural publication is intentionally separate from a
// handler-only rebind because it has different generation and replay guards.
func demonstrateRoutePublication() (result error) {
	limits := coakkahttp.DefaultLimits()
	limits.MaxHandlerBindings = 3
	service, err := coakkahttp.NewBuilder().Limits(limits).
		Get("/old", textHandler(200, "old-generation")).Start()
	if err != nil {
		return fmt.Errorf("start route publication sample: %w", err)
	}
	defer func() { result = errors.Join(result, closeService(service, "route publication")) }()
	initial, err := service.Routes()
	if err != nil {
		return err
	}
	if len(initial.Routes) != 1 || initial.Routes[0].RouteID != 1 {
		return errors.New("unexpected initial Core route cut")
	}
	if err = service.PrepareHandler(2, textHandler(201, "new-generation")); err != nil {
		return fmt.Errorf("prepare published route: %w", err)
	}
	publication := coakkahttp.RoutePublication{
		ActivationID: 1, ExpectedRouteGeneration: initial.RouteGeneration,
		ExpectedBindingChangeSequence: initial.BindingChangeSequence,
		Routes: []coakkahttp.Route{{
			ID: 2, HandlerBindingID: 2, Method: http.MethodGet, Pattern: "/published",
			BodyDelivery: coakkahttp.BodyInline,
			// Leave policy absent: DefaultLimits delegates its body ceiling to
			// Core; the zero declaration is not an effective body-size value.
		}},
	}
	outcome, err := service.PublishRoutes(publication)
	if err != nil {
		return fmt.Errorf("publish route generation: %w", err)
	}
	if outcome.Code != coakkahttp.RoutePublicationApplied || !outcome.Changed {
		return fmt.Errorf("route generation rejected: outcome=%+v", outcome)
	}
	// This complete pull comes from Core even though monitoring is disabled.
	// Local declarations and prepared handlers are not effective route truth.
	current, err := service.Routes()
	if err != nil {
		return err
	}
	if current.RouteGeneration != outcome.EffectiveRouteGeneration ||
		current.BindingChangeSequence != outcome.EffectiveBindingChangeSequence ||
		len(current.Routes) != 1 || current.Routes[0].RouteID != 2 || current.Routes[0].HandlerBindingID != 2 {
		return errors.New("published Core route cut does not match the accepted generation")
	}
	// Replay must use the exact original activation and payload. A new
	// activation with stale guards is a different operation and must refuse.
	replayed, err := service.PublishRoutes(publication)
	if err != nil {
		return err
	}
	if replayed.Code != coakkahttp.RoutePublicationApplied || !replayed.Replayed ||
		replayed.OperationDigest != outcome.OperationDigest ||
		replayed.EffectiveRouteGeneration != outcome.EffectiveRouteGeneration {
		return fmt.Errorf("exact publication replay mismatch: %+v", replayed)
	}
	publication.ActivationID = 2
	refused, err := service.PublishRoutes(publication)
	if err != nil {
		return err
	}
	if refused.Code != coakkahttp.RoutePublicationGenerationMismatch || refused.Changed ||
		refused.EffectiveRouteGeneration != outcome.EffectiveRouteGeneration ||
		refused.EffectiveBindingChangeSequence != outcome.EffectiveBindingChangeSequence {
		return fmt.Errorf("stale publication did not preserve Core state: %+v", refused)
	}
	afterRefusal, err := service.Routes()
	if err != nil {
		return err
	}
	if afterRefusal.RouteGeneration != current.RouteGeneration ||
		afterRefusal.BindingChangeSequence != current.BindingChangeSequence ||
		len(afterRefusal.Routes) != 1 || afterRefusal.Routes[0] != current.Routes[0] {
		return errors.New("stale publication changed the effective Core route cut")
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
	for _, item := range []struct {
		path   string
		status uint16
	}{{"/source", 200}, {"/missing", 404}} {
		call, err := service.SubmitOutbound(coakkahttp.ClientRequest{
			TimeoutMillis: 3000, LogicalTarget: outboundTarget, Method: "GET", Target: item.path,
		})
		if err != nil {
			return fmt.Errorf("submit outbound request: %w", err)
		}
		terminal, err := service.TakeOutbound(5 * time.Second)
		if err != nil {
			return fmt.Errorf("take outbound result: %w", err)
		}
		// An HTTP404 remains a received response, not a local transport error.
		// The runtime releases its native response lease before returning this
		// copied value; the application never guesses or releases that lease.
		if terminal == nil || terminal.Call != call || terminal.Reason != coakkahttp.OutboundResponse || terminal.ResponseStatus != item.status ||
			terminal.LogicalTarget != outboundTarget || terminal.TargetGeneration != 1 ||
			terminal.SelectedNodeID != "loopback" ||
			(item.status == 200 && string(terminal.ResponseBody) != "outbound-ready") {
			return errors.New("outbound result did not match the declared target")
		}
	}
	if extra, err := service.TakeOutbound(0); err != nil || extra != nil {
		return fmt.Errorf("unexpected extra outbound terminal: value=%+v error=%v", extra, err)
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
		"ready=%t monitor-latest=%d retained=%d io-uring-requested=%t io-uring-effective=%t\n",
		health.Ready, page.LatestSequence, len(page.Events),
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
