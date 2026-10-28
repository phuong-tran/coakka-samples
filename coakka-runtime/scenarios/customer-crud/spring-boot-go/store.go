package main

import (
	"encoding/json"
	"fmt"
	"log"
	"os"
	"os/signal"
	"sort"
	"sync"
	"syscall"

	connector "github.com/phuong-tran/coakka-runtime-go"
)

const (
	localTarget = "samples.customer.store"
	peerTarget  = "samples.customer.frontend"
)

var identities = struct {
	create           connector.PayloadIdentity
	update           connector.PayloadIdentity
	delete           connector.PayloadIdentity
	list             connector.PayloadIdentity
	mutationResponse connector.PayloadIdentity
	listResponse     connector.PayloadIdentity
}{
	create:           connector.NewPayloadIdentity("samples.customer.create.request.v1", 1, connector.PayloadFormatJSON),
	update:           connector.NewPayloadIdentity("samples.customer.update.request.v1", 1, connector.PayloadFormatJSON),
	delete:           connector.NewPayloadIdentity("samples.customer.delete.request.v1", 1, connector.PayloadFormatJSON),
	list:             connector.NewPayloadIdentity("samples.customer.list.request.v1", 1, connector.PayloadFormatJSON),
	mutationResponse: connector.NewPayloadIdentity("samples.customer.mutation.response.v1", 1, connector.PayloadFormatJSON),
	listResponse:     connector.NewPayloadIdentity("samples.customer.list.response.v1", 1, connector.PayloadFormatJSON),
}

type customerDraft struct {
	ID    string `json:"id"`
	Name  string `json:"name"`
	Email string `json:"email"`
	Tier  string `json:"tier"`
	Notes string `json:"notes"`
}

type deleteCustomerRequest struct {
	ID string `json:"id"`
}

type customerView struct {
	ID       string `json:"id"`
	Name     string `json:"name"`
	Email    string `json:"email"`
	Tier     string `json:"tier"`
	Notes    string `json:"notes"`
	Revision int64  `json:"revision"`
}

type mutationResponse struct {
	Status     string `json:"status"`
	Operation  string `json:"operation"`
	CustomerID string `json:"customerId"`
	Revision   int64  `json:"revision"`
	HandledBy  string `json:"handledBy"`
}

type listResponse struct {
	Customers []customerView `json:"customers"`
}

type storeState struct {
	mu        sync.Mutex
	revision  int64
	customers map[string]customerView
}

func newStoreState() *storeState {
	return &storeState{customers: map[string]customerView{}}
}

func (s *storeState) upsert(operation string, customer customerDraft) mutationResponse {
	s.mu.Lock()
	defer s.mu.Unlock()

	s.revision++
	s.customers[customer.ID] = customerView{
		ID:       customer.ID,
		Name:     customer.Name,
		Email:    customer.Email,
		Tier:     customer.Tier,
		Notes:    customer.Notes,
		Revision: s.revision,
	}
	log.Printf("customer-store-go %s id=%s tier=%s", operation, customer.ID, customer.Tier)
	return s.mutation(operation, customer.ID)
}

func (s *storeState) delete(id string, correlationID string) mutationResponse {
	s.mu.Lock()
	defer s.mu.Unlock()

	s.revision++
	delete(s.customers, id)
	log.Printf("customer-store-go delete id=%s correlation=%s", id, correlationID)
	return s.mutation("delete", id)
}

func (s *storeState) list(correlationID string) listResponse {
	customers := s.snapshotCustomers()
	log.Printf("customer-store-go list correlation=%s", correlationID)
	return listResponse{Customers: customers}
}

func (s *storeState) snapshotCustomers() []customerView {
	s.mu.Lock()
	defer s.mu.Unlock()

	customers := make([]customerView, 0, len(s.customers))
	for _, customer := range s.customers {
		customers = append(customers, customer)
	}
	sort.Slice(customers, func(i, j int) bool {
		return customers[i].ID < customers[j].ID
	})
	return customers
}

func (s *storeState) mutation(operation string, id string) mutationResponse {
	return mutationResponse{
		Status:     "ACCEPTED",
		Operation:  operation,
		CustomerID: id,
		Revision:   s.revision,
		HandledBy:  "customer-store-go",
	}
}

func main() {
	store := newStoreState()
	// Runtime route table for the Go store process.
	//
	// localTarget is marked EndpointFlagLocal because this process registers the
	// customer handler. peerTarget points to the Spring Boot web process.
	// QueueCapacity=128 keeps the sample bounded, StrictNoDrop=true makes
	// pressure visible, and Generation=1 is the first static route snapshot.
	runtimeHost, err := connector.StartRuntimeHost(connector.ConnectorStartSpec{
		SystemName:                   "customer-store-go",
		NodeID:                       "customer-store-go",
		StrictNoDrop:                 true,
		QueueCapacity:                128,
		EnableMonitor:                true,
		Generation:                   1,
		Routes: []connector.RouteSpec{
			{
				Target: localTarget,
				Endpoints: []connector.EndpointSpec{{
					Host:  "127.0.0.1",
					Port:  19122,
					Flags: uint32(connector.EndpointFlagLocal),
				}},
			},
			{
				Target: peerTarget,
				Endpoints: []connector.EndpointSpec{{
					Host:  "127.0.0.1",
					Port:  19121,
					Flags: uint32(connector.EndpointFlagNone),
				}},
			},
		},
	}, "")
	if err != nil {
		log.Fatalf("start runtime host: %v", err)
	}
	defer func() {
		if err := runtimeHost.Close(); err != nil {
			log.Printf("close runtime host: %v", err)
		}
	}()

	if err := runtimeHost.RegisterHandler(localTarget, func(request *connector.Envelope) *connector.Envelope {
		payload, identity, err := handleRuntimeRequest(store, request)
		if err != nil {
			log.Printf("runtime request failed type=%s correlation=%s error=%v", request.GetMessageType(), request.GetCorrelationId(), err)
			return nil
		}
		reply, err := connector.MakeJSONReply(request, localTarget, payload, identity)
		if err != nil {
			log.Printf("runtime reply failed type=%s correlation=%s error=%v", request.GetMessageType(), request.GetCorrelationId(), err)
			return nil
		}
		return reply
	}, true); err != nil {
		log.Fatalf("register handler: %v", err)
	}

	info := runtimeHost.RuntimeInfo()
	log.Printf(
		"customer-store-go ready headless runtime=%s target=%s",
		info.RuntimeVersion,
		localTarget,
	)
	signals := make(chan os.Signal, 1)
	signal.Notify(signals, syscall.SIGINT, syscall.SIGTERM)
	<-signals
}

func handleRuntimeRequest(store *storeState, request *connector.Envelope) (any, connector.PayloadIdentity, error) {
	switch request.GetMessageType() {
	case identities.create.MessageType:
		var payload customerDraft
		if err := decodeJSON(request.GetPayload(), &payload); err != nil {
			return nil, connector.PayloadIdentity{}, err
		}
		return store.upsert("create", payload), identities.mutationResponse, nil
	case identities.update.MessageType:
		var payload customerDraft
		if err := decodeJSON(request.GetPayload(), &payload); err != nil {
			return nil, connector.PayloadIdentity{}, err
		}
		return store.upsert("update", payload), identities.mutationResponse, nil
	case identities.delete.MessageType:
		var payload deleteCustomerRequest
		if err := decodeJSON(request.GetPayload(), &payload); err != nil {
			return nil, connector.PayloadIdentity{}, err
		}
		return store.delete(payload.ID, request.GetCorrelationId()), identities.mutationResponse, nil
	case identities.list.MessageType:
		return store.list(request.GetCorrelationId()), identities.listResponse, nil
	default:
		return nil, connector.PayloadIdentity{}, fmt.Errorf("unsupported customer message type: %s", request.GetMessageType())
	}
}

func decodeJSON(payload []byte, out any) error {
	if len(payload) == 0 {
		payload = []byte("{}")
	}
	return json.Unmarshal(payload, out)
}
