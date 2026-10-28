package main

import (
	"testing"

	connector "github.com/phuong-tran/coakka-runtime-go"
)

func TestRouteMissRequiresTypedCauseAndOriginalTarget(t *testing.T) {
	valid := &connector.Deadletter{Reason: connector.DeadletterReasonRouteMiss,
		OriginalEnvelope: &connector.Envelope{Target: "missing"}}
	if err := validateDeadletter(valid, "missing"); err != nil {
		t.Fatal(err)
	}
	for _, value := range []*connector.Deadletter{nil, {},
		{Reason: connector.DeadletterReasonRouteMiss},
		{Reason: connector.DeadletterReason(12345), OriginalEnvelope: valid.OriginalEnvelope},
		{Reason: connector.DeadletterReasonRouteMiss, OriginalEnvelope: &connector.Envelope{Target: "other"}},
	} {
		if err := validateDeadletter(value, "missing"); err == nil {
			t.Fatalf("admitted %+v", value)
		}
	}
}
