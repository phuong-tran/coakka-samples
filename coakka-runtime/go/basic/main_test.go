package main

import (
	"encoding/json"
	"testing"

	connector "github.com/phuong-tran/coakka-runtime-go"
)

func TestEchoHandlerPreservesIdentityAndReportsFailure(t *testing.T) {
	failures := make(chan error, 1)
	handler := echoHandler("echo", failures)
	if reply := handler(nil); reply != nil {
		t.Fatal("malformed request must not produce a reply")
	}
	select {
	case err := <-failures:
		if err == nil {
			t.Fatal("missing callback failure")
		}
	default:
		t.Fatal("callback failure was not handed to the app owner")
	}
	request := &connector.Envelope{MessageId: "request", Source: "caller", Target: "echo",
		MessageType: "echo.request", PayloadSchemaVersion: 1, PayloadFormat: connector.PayloadFormatJSON}
	reply := handler(request)
	if reply == nil || reply.CorrelationId != "request" || reply.Target != "caller" ||
		reply.MessageType != request.MessageType || reply.PayloadSchemaVersion != 1 ||
		reply.PayloadFormat != connector.PayloadFormatJSON {
		t.Fatalf("reply identity differs: %+v", reply)
	}
	var payload map[string]any
	if err := json.Unmarshal(reply.Payload, &payload); err != nil || payload["echo"] != "hello-runtime-go" {
		t.Fatalf("reply payload differs: %q %v", reply.Payload, err)
	}
	if len(failures) != 0 {
		t.Fatal("successful callback reported a failure")
	}
}
