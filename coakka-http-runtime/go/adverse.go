package main

// These deliberately held handlers demonstrate failure boundaries, not a
// production scheduling pattern. Channels control application work. Core owns
// deadlines and accepted capacity; Go owns admission to its bounded workers.
import (
	"context"
	"errors"
	"fmt"
	"io"
	"net/http"
	"sync"
	"time"

	coakkahttp "github.com/phuong-tran/coakka-http-runtime-go"
)

// sampleHTTPResult owns a bounded copy, never a response-body lease.
type sampleHTTPResult struct {
	status int
	body   string
	err    error
}

// readSampleResponse gives the diagnostic client an independent safety budget.
// A client timeout is an error here, never evidence that Core's deadline fired.
func readSampleResponse(ctx context.Context, client *http.Client, url string) sampleHTTPResult {
	request, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
	if err != nil {
		return sampleHTTPResult{err: err}
	}
	response, err := client.Do(request)
	if err != nil {
		return sampleHTTPResult{err: err}
	}
	body, readErr := io.ReadAll(io.LimitReader(response.Body, 4097))
	err = errors.Join(readErr, response.Body.Close())
	if len(body) > 4096 {
		err = errors.Join(err, errors.New("sample response exceeds 4 KiB diagnostic bound"))
	}
	return sampleHTTPResult{status: response.StatusCode, body: string(body), err: err}
}

func runAdverseSmoke() error {
	if err := demonstrateHandlerDeadline(); err != nil {
		return err
	}
	if err := demonstrateDispatchPressure(); err != nil {
		return err
	}
	for _, reason := range []coakkahttp.OutboundReason{coakkahttp.OutboundCancelled, coakkahttp.OutboundDeadlineExceeded} {
		if err := demonstrateOutboundTermination(reason); err != nil {
			return err
		}
	}
	return nil
}

// demonstrateHandlerDeadline proves that a late application result cannot
// replace the timeout response. The next request is also a one-worker barrier:
// it runs after the late handler finishes, without sleeping to guess retirement.
func demonstrateHandlerDeadline() (result error) {
	limits := coakkahttp.DefaultLimits()
	limits.RequestTimeoutMillis = 250
	release := make(chan struct{})
	var releaseOnce sync.Once
	unblock := func() { releaseOnce.Do(func() { close(release) }) }
	service, err := coakkahttp.NewBuilder().Concurrency(1).Limits(limits).
		Get("/held", func(*coakkahttp.Request) (coakkahttp.Response, error) {
			<-release
			return coakkahttp.Text(http.StatusOK, "too late")
		}).Get("/ok", textHandler(http.StatusOK, "ready")).Start()
	if err != nil {
		return err
	}
	defer func() {
		unblock()
		result = errors.Join(result, closeService(service, "deadline sample"))
	}()
	port, err := service.Port()
	if err != nil {
		return err
	}
	client := &http.Client{Timeout: 5 * time.Second}
	defer client.CloseIdleConnections()
	base := fmt.Sprintf("http://127.0.0.1:%d", port)
	response := readSampleResponse(context.Background(), client, base+"/held")
	if response.err != nil || response.status != http.StatusGatewayTimeout {
		return fmt.Errorf("Core deadline did not produce HTTP504: %+v", response)
	}
	unblock()
	response = readSampleResponse(context.Background(), client, base+"/ok")
	if response.err != nil || response.status != http.StatusOK || response.body != "ready" {
		return fmt.Errorf("service did not recover after late handler: %+v", response)
	}
	if err := service.PollError(); err != nil {
		return fmt.Errorf("late handler produced an unexpected diagnostic: %w", err)
	}
	fmt.Println("go-handler-deadline=pass")
	return nil
}

// demonstrateOutboundTermination keeps one terminal reader and acts only on
// Core's named cause. Cancelling a call is intent, not proof of its outcome.
// Application work is released independently after observing that outcome.
func demonstrateOutboundTermination(reason coakkahttp.OutboundReason) (result error) {
	entered, release := make(chan struct{}), make(chan struct{})
	var releaseOnce sync.Once
	unblock := func() { releaseOnce.Do(func() { close(release) }) }
	limits := coakkahttp.DefaultLimits()
	limits.RequestTimeoutMillis = 10000 // The outbound deadline must fire first.
	upstream, err := coakkahttp.NewBuilder().Concurrency(1).Limits(limits).
		Get("/held", func(*coakkahttp.Request) (coakkahttp.Response, error) {
			close(entered)
			<-release
			return coakkahttp.Text(http.StatusOK, "late upstream")
		}).Get("/ready", textHandler(http.StatusOK, "ready")).Start()
	if err != nil {
		return err
	}
	defer func() {
		unblock()
		result = errors.Join(result, closeService(upstream, "held outbound target"))
	}()
	port, err := upstream.Port()
	if err != nil {
		return err
	}
	owner, err := coakkahttp.NewBuilder().Get("/health", textHandler(http.StatusOK, "ready")).
		OutboundTarget(coakkahttp.OutboundTarget{Name: "sample.held", Generation: 1,
			Endpoints: []coakkahttp.OutboundEndpoint{{NodeID: "loopback", ConnectHost: "127.0.0.1", ConnectPort: port,
				HTTPAuthority: fmt.Sprintf("127.0.0.1:%d", port), Security: coakkahttp.SecurityPlaintext}},
		}).Start()
	if err != nil {
		return err
	}
	defer func() {
		unblock()
		result = errors.Join(result, closeService(owner, "outbound termination"))
	}()
	budget := uint32(3000)
	if reason == coakkahttp.OutboundDeadlineExceeded {
		budget = 500
	}
	call, err := owner.SubmitOutbound(coakkahttp.ClientRequest{
		TimeoutMillis: budget, LogicalTarget: "sample.held", Method: http.MethodGet, Target: "/held",
	})
	if err != nil {
		return err
	}
	timer := time.NewTimer(3 * time.Second)
	defer timer.Stop()
	select {
	case <-entered:
	case <-timer.C:
		return errors.New("outbound target was not admitted within the diagnostic budget")
	}
	if reason == coakkahttp.OutboundCancelled {
		if err := owner.CancelOutbound(call); err != nil {
			return err
		}
	}
	terminal, err := owner.TakeOutbound(5 * time.Second)
	if err != nil {
		return err
	}
	if terminal == nil || terminal.Call != call || terminal.Reason != reason || terminal.ResponseStatus != 0 {
		return fmt.Errorf("outbound terminal did not match %s", reason)
	}
	unblock()
	call, err = owner.SubmitOutbound(coakkahttp.ClientRequest{
		TimeoutMillis: 3000, LogicalTarget: "sample.held", Method: http.MethodGet, Target: "/ready",
	})
	if err != nil {
		return err
	}
	terminal, err = owner.TakeOutbound(5 * time.Second)
	if err != nil {
		return err
	}
	if terminal == nil || terminal.Call != call || terminal.Reason != coakkahttp.OutboundResponse ||
		terminal.Certainty != coakkahttp.OutboundCompleteResponse || terminal.ResponseStatus != http.StatusOK ||
		string(terminal.ResponseBody) != "ready" {
		return errors.New("outbound call did not recover after termination")
	}
	if extra, err := owner.TakeOutbound(0); err != nil || extra != nil {
		return errors.Join(errors.New("outbound terminal was not consumed exactly once"), err)
	}
	fmt.Printf("go-outbound-termination=%s pass\n", reason)
	return nil
}

// demonstrateDispatchPressure reserves one queued request and holds one worker.
// A fixed nine-client burst then demonstrates bounded refusal, not a throughput
// benchmark. Different scheduler cuts may refuse different numbers of requests.
func demonstrateDispatchPressure() (result error) {
	limits := coakkahttp.DefaultLimits()
	limits.RequestQueueCapacity = 1
	limits.RequestTimeoutMillis = 10000 // Longer than the refusal observation budget.
	entered, release := make(chan struct{}), make(chan struct{})
	var enteredOnce, releaseOnce sync.Once
	unblock := func() { releaseOnce.Do(func() { close(release) }) }
	service, err := coakkahttp.NewBuilder().Concurrency(1).Limits(limits).
		Get("/pressure", func(*coakkahttp.Request) (coakkahttp.Response, error) {
			enteredOnce.Do(func() { close(entered) })
			<-release
			return coakkahttp.Text(http.StatusOK, "ready")
		}).Get("/ok", textHandler(http.StatusOK, "ready")).Start()
	if err != nil {
		return err
	}
	client := &http.Client{Timeout: 5 * time.Second}
	ctx, cancel := context.WithCancel(context.Background())
	var clients sync.WaitGroup
	defer func() {
		// Release application work even on failed assertions, then stop and join
		// every diagnostic client before closing the service. Refused close is
		// preserved as an error; no native lifetime is inferred from cancellation.
		unblock()
		cancel()
		clients.Wait()
		client.CloseIdleConnections()
		result = errors.Join(result, closeService(service, "pressure sample"))
	}()
	port, err := service.Port()
	if err != nil {
		return err
	}
	base := fmt.Sprintf("http://127.0.0.1:%d", port)
	const count = 9
	results := make(chan sampleHTTPResult, count)
	launch := func() {
		clients.Add(1)
		go func() {
			defer clients.Done()
			results <- readSampleResponse(ctx, client, base+"/pressure")
		}()
	}
	launch()
	timer := time.NewTimer(4 * time.Second)
	defer timer.Stop()
	select {
	case <-entered:
	case <-timer.C:
		return errors.New("pressure handler was not admitted within the diagnostic budget")
	}
	for index := 1; index < count; index++ {
		launch()
	}
	refused := 0
	for index := 0; index < count; index++ {
		select {
		case response := <-results:
			if response.err != nil {
				return response.err
			}
			switch response.status {
			case http.StatusServiceUnavailable:
				refused++
				// The actual wire refusal is the barrier. No sleep and no
				// local queue-size guess authorize releasing the held handler.
				unblock()
			case http.StatusOK:
				if response.body != "ready" {
					return fmt.Errorf("pressure response changed: %+v", response)
				}
			default:
				return fmt.Errorf("unexpected pressure response: %+v", response)
			}
		case <-timer.C:
			return errors.New("pressure requests did not converge within the diagnostic budget")
		}
	}
	if refused == 0 {
		return errors.New("bounded pressure produced no HTTP503 refusal")
	}
	response := readSampleResponse(ctx, client, base+"/ok")
	if response.err != nil || response.status != http.StatusOK || response.body != "ready" {
		return fmt.Errorf("service did not recover after pressure: %+v", response)
	}
	fmt.Printf("go-dispatch-pressure=pass refused=%d total=%d\n", refused, count)
	return nil
}
