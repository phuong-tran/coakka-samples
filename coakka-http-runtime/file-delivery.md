# File delivery: sendfile, downloads and uploads

Use a static mount for public assets or a confined file response from a handler
for an application-selected download. Both let Core own file delivery instead
of first constructing a whole-file byte array in the host language.

## Contents

- [Why sendfile exists](#why-sendfile-exists)
- [Upload is the opposite direction](#upload-is-the-opposite-direction)
- [Core chooses the delivery path](#core-chooses-the-delivery-path)
- [Use the file response for your language](#use-the-file-response-for-your-language)
- [Application responsibilities](#application-responsibilities)

## Why sendfile exists

A read-then-send download can move file bytes through application buffers
before writing them to a socket. A sendfile-style path lets the operating
system transfer eligible file content to a socket without that application
buffer round trip. It can reduce copies, host allocations and per-chunk
boundary crossings; it is not a promise of zero CPU, zero copies everywhere,
or a fixed throughput gain. Storage, cache state, network, protocol and client
speed still matter.

The important application choice is **return a file reference, not the whole
file as a byte array**. Core owns file resolution, bounded delivery and the
selected protocol path. A connector does not implement its own sendfile loop.

## Upload is the opposite direction

| Operation | Direction | Application role |
| --- | --- | --- |
| Static asset or file download | Server file to client response | Declare a public mount or authorize a confined file response. |
| Upload, including multipart form uploads | Client request body to server | Consume bounded input, validate it and decide how/where to persist it. |
| Generated streaming response | Application-produced chunks to client | Produce bounded chunks, handle pressure and cancellation. |

Sendfile is a file-to-response optimization, **not a replacement for upload**.
It does not parse multipart input, choose an upload destination, check business
content or persist incoming bytes. The samples' `/upload` routes demonstrate
bounded request consumption; they are not automatically an upload storage
service. Small read-and-echo examples must not become unbounded large-file
collectors in production.

## Core chooses the delivery path

Eligible plaintext HTTP/1.1 file delivery can use the native sendfile path.
TLS, HTTP/2, HTTP/3 and other platform/protocol conditions use the corresponding
bounded file-delivery path. Core handles pressure and the supported fallback;
the application keeps the same file-response contract.

Do not disable TLS to obtain sendfile or infer the mechanism from a successful
download. The selected package/platform must supply evidence for performance
claims. Do not call all file responses “zero-copy”, assume every platform uses
the same syscall, or bypass Core with a connector-owned socket/file loop.
Returning a file also avoids making the host own a whole-file response buffer
when the transport requires a different delivery mechanism.

## Use the file response for your language

Each guide contains the authority/mount declaration, a `/download` handler and
range verification; use the complete sample for imports and checked lifecycle.

| Language | File-response surface and guide |
| --- | --- |
| C | `coakka_http_request_respond_file` with `coakka_http_file_response_t`; [C guide](c/integration.md#static-files-and-an-spa-frontend) |
| C++ | The public C file-response contract from a C++ handler; [C++ guide](cpp/integration.md#static-files-and-an-spa-frontend) |
| Go | `coakkahttp.ServeFile`; [Go guide](go/integration.md#static-files-frontend-and-downloads) |
| JVM: Kotlin and Java | `Responses.file`; [JVM guide](kotlin/integration.md#static-files-frontend-and-downloads) |
| Python | The file-response recipe in the [Python guide](python/integration.md#static-files-frontend-and-downloads) |
| Node.js and Bun | The shared file-response recipe in the [TypeScript guide](typescript/integration.md#static-files-frontend-and-downloads) |

## Application responsibilities

- Put only public assets in a static mount. For private downloads, authorize
  in the handler before selecting a file from the configured authority.
- A confined path is not an arbitrary OS path. Never grant a broad root merely
  to accept a client-supplied filename.
- Keep file sizes, active transfers and timeouts finite. Slow clients consume
  capacity even when the application does not buffer an entire file.
- Use the runtime's validator/range contract; test full and partial responses,
  missing files, invalid paths and interrupted downloads.
- Publish stable file versions; define how deployment replaces assets rather
  than modifying bytes underneath an active transfer.
- Apply separate upload admission, naming, storage, authorization and content
  policies. File download optimization does not supply these policies.

See the [feature evidence index](FEATURES.md) for sample verification scope.
This explanation adds no new platform benchmark or file-transfer test result.
