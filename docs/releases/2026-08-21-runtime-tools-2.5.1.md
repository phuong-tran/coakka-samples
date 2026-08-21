# Runtime Client And Inspect 2.5.1

The public `coakka-client` and `coakka-runtime-inspect` products now use exact
Runtime native generation
`2.5.1+26f7944de4a4e0598845a54e4775f9463a9e33be`.

## Native Archives

The immutable archives are published under:

- `coakka-tools/coakka-client/releases/2.5.1+26f7944de4a4e0598845a54e4775f9463a9e33be/`
- `coakka-tools/coakka-runtime-inspect/releases/2.5.1+26f7944de4a4e0598845a54e4775f9463a9e33be/`
- `coakka-tools/coakka-client/docker-demo/releases/2.5.1+26f7944de4a4e0598845a54e4775f9463a9e33be/`

All five Client and Inspect archives pass exact Runtime identity, architecture,
dependency, export, package-surface, checksum, and legal-bundle verification.
The Client Docker bundle passes the same controls for Linux amd64 and arm64.
The authoritative artifact rows are in `artifacts/public-artifacts.tsv` at
Publish commit `9429dd78fee2127e3aacbf0e753ec1a7bc141f6b`.

## Docker Hub

The published multi-architecture images are:

- `docker.io/gabrielgun1983/coakka-runtime-client-demo:2.5.1-26f7944d-remote`
  at `sha256:30586188a7a400b8085a3eed475ad086761f1506816ac6d1b70887fe901958e6`
- `docker.io/gabrielgun1983/coakka-runtime-inspect-sample:2.5.1-26f7944d-remote`
  at `sha256:8dae4f4f392a83a2cbbc4d6d4e15b39ad4186996a469d36a11e0992334329ac3`

Samples commit `a24c1fb6891a9c219de6137d7aa704fbdbb5b070` is embedded in both image
receipts. The Client walkthrough and Inspect smoke passed for Linux amd64 and
arm64 using the immutable image digests.

## Scope

`coakka-client` is the command-line Runtime product. `coakka-runtime-inspect`
is the browser Runtime explorer product. The Raspberry Pi camera executable is
separate: it remains an evaluation demo for Stream Lane and is not a product or
a production camera support claim.

Native matching-host execution is not claimed for every archive target. The
Docker evidence covers Linux amd64/arm64 image execution; archive identity,
dependency, architecture, and checksum gates cover all advertised native
packages.
