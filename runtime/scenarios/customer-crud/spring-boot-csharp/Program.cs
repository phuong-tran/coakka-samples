using System.Text.Json;
using System.Text.Json.Serialization;
using CoAkka.Runtime;

const string localTarget = "samples.customer.store";
const string peerTarget = "samples.customer.frontend";

var identities = new CustomerPayloadIdentities();
var store = new CustomerStore();

var spec = new ConnectorStartSpec(
    SystemName: "customer-store-csharp",
    NodeId: "customer-store-csharp",
    StrictNoDrop: true,
    QueueCapacity: 128,
    Generation: 1,
    Routes:
    [
        RuntimeHost.LocalRoute(localTarget, diagnosticPort: 19142),
        new RuntimeRouteSpec(
            Target: peerTarget,
            Endpoints:
            [
                new RuntimeEndpointSpec(
                    Host: "127.0.0.1",
                    Port: 19141,
                    Flags: RuntimeEndpointFlags.None),
            ]),
    ],
    EnableMonitor: true);

using var runtime = RuntimeHost.Start(spec);
runtime.RegisterHandler(
    localTarget,
    request =>
    {
        var reply = HandleRuntimeRequest(store, identities, request);
        return RuntimeHost.MakeJsonReply(request, localTarget, reply.Payload, reply.Identity);
    });

var info = runtime.RuntimeInfo();
Console.WriteLine(
    $"customer-store-csharp ready headless runtime={info.RuntimeVersion} target={localTarget}");

var done = new ManualResetEventSlim(false);
Console.CancelKeyPress += (_, eventArgs) =>
{
    eventArgs.Cancel = true;
    done.Set();
};
done.Wait();

static RuntimeReply HandleRuntimeRequest(
    CustomerStore store,
    CustomerPayloadIdentities identities,
    TransportEnvelope request)
{
    return request.MessageType switch
    {
        CustomerMessageTypes.CreateRequest => new RuntimeReply(
            store.Upsert("create", DecodeJson<CustomerDraft>(request.Payload)),
            identities.MutationResponse),
        CustomerMessageTypes.UpdateRequest => new RuntimeReply(
            store.Upsert("update", DecodeJson<CustomerDraft>(request.Payload)),
            identities.MutationResponse),
        CustomerMessageTypes.DeleteRequest => new RuntimeReply(
            store.Delete(DecodeJson<DeleteCustomerRequest>(request.Payload).Id, request.CorrelationId),
            identities.MutationResponse),
        CustomerMessageTypes.ListRequest => new RuntimeReply(
            store.List(request.CorrelationId),
            identities.ListResponse),
        _ => throw new InvalidOperationException($"unsupported customer message type: {request.MessageType}"),
    };
}

static T DecodeJson<T>(byte[] payload)
{
    if (payload.Length == 0)
    {
        payload = "{}"u8.ToArray();
    }
    return JsonSerializer.Deserialize<T>(payload) ?? throw new InvalidOperationException($"invalid JSON payload for {typeof(T).Name}");
}

sealed class CustomerPayloadIdentities
{
    public PayloadIdentity Create { get; } = Identity(CustomerMessageTypes.CreateRequest);
    public PayloadIdentity Update { get; } = Identity(CustomerMessageTypes.UpdateRequest);
    public PayloadIdentity Delete { get; } = Identity(CustomerMessageTypes.DeleteRequest);
    public PayloadIdentity List { get; } = Identity(CustomerMessageTypes.ListRequest);
    public PayloadIdentity MutationResponse { get; } = Identity(CustomerMessageTypes.MutationResponse);
    public PayloadIdentity ListResponse { get; } = Identity(CustomerMessageTypes.ListResponse);

    private static PayloadIdentity Identity(string messageType) =>
        PayloadIdentity.Json(messageType, CustomerMessageTypes.SchemaVersion);
}

static class CustomerMessageTypes
{
    public const uint SchemaVersion = 1;
    public const string CreateRequest = "samples.customer.create.request.v1";
    public const string UpdateRequest = "samples.customer.update.request.v1";
    public const string DeleteRequest = "samples.customer.delete.request.v1";
    public const string ListRequest = "samples.customer.list.request.v1";
    public const string MutationResponse = "samples.customer.mutation.response.v1";
    public const string ListResponse = "samples.customer.list.response.v1";
}

sealed record RuntimeReply(object Payload, PayloadIdentity Identity);

sealed record CustomerDraft(
    [property: JsonPropertyName("id")] string Id,
    [property: JsonPropertyName("name")] string Name,
    [property: JsonPropertyName("email")] string Email,
    [property: JsonPropertyName("tier")] string Tier,
    [property: JsonPropertyName("notes")] string? Notes);

sealed record DeleteCustomerRequest([property: JsonPropertyName("id")] string Id);

sealed record CustomerView(
    [property: JsonPropertyName("id")] string Id,
    [property: JsonPropertyName("name")] string Name,
    [property: JsonPropertyName("email")] string Email,
    [property: JsonPropertyName("tier")] string Tier,
    [property: JsonPropertyName("notes")] string Notes,
    [property: JsonPropertyName("revision")] long Revision);

sealed record MutationResponse(
    [property: JsonPropertyName("status")] string Status,
    [property: JsonPropertyName("operation")] string Operation,
    [property: JsonPropertyName("customerId")] string CustomerId,
    [property: JsonPropertyName("revision")] long Revision,
    [property: JsonPropertyName("handledBy")] string HandledBy);

sealed record ListResponse([property: JsonPropertyName("customers")] IReadOnlyList<CustomerView> Customers);

sealed class CustomerStore
{
    private readonly object _mutex = new();
    private readonly Dictionary<string, CustomerView> _customers = new(StringComparer.Ordinal);
    private long _revision;

    public MutationResponse Upsert(string operation, CustomerDraft customer)
    {
        lock (_mutex)
        {
            _revision += 1;
            _customers[customer.Id] = new CustomerView(
                Id: customer.Id,
                Name: customer.Name,
                Email: customer.Email,
                Tier: customer.Tier,
                Notes: customer.Notes ?? string.Empty,
                Revision: _revision);
            Console.WriteLine($"customer-store-csharp {operation} id={customer.Id} tier={customer.Tier}");
            return Mutation(operation, customer.Id);
        }
    }

    public MutationResponse Delete(string id, string correlationId)
    {
        lock (_mutex)
        {
            _revision += 1;
            _customers.Remove(id);
            Console.WriteLine($"customer-store-csharp delete id={id} correlation={correlationId}");
            return Mutation("delete", id);
        }
    }

    public ListResponse List(string correlationId)
    {
        lock (_mutex)
        {
            Console.WriteLine($"customer-store-csharp list correlation={correlationId}");
            return new ListResponse(
                _customers.Values
                    .OrderBy(static customer => customer.Id, StringComparer.Ordinal)
                    .ToArray());
        }
    }

    private MutationResponse Mutation(string operation, string customerId) =>
        new(
            Status: "ACCEPTED",
            Operation: operation,
            CustomerId: customerId,
            Revision: _revision,
            HandledBy: "customer-store-csharp");
}
