# Product Layout

CoAkka uses stable product directories in Samples and Publish:

| Directory | Product |
| --- | --- |
| `coakka-runtime/` | CoAkka Runtime, its language integrations, tools, optional addons and demonstrations. |
| `coakka-http-runtime/` | The independent CoAkka HTTP Runtime server and client. |
| `coakka-logger/` | CoAkka Logger and its language integrations. |

Start at the repository README, select a product, then follow its guide.
Shared orientation, terminology, architecture and support documents live in
the flat root `docs/` directory. Product-specific examples and operations stay
with their product. The repositories contain real Markdown files, not symlinks.

Samples is a rolling collection of executable examples. Its root runner can
select `coakka-runtime`, `coakka-http-runtime` or `coakka-logger`; each product
also owns its runner and supporting tools. A scenario may use another product
without merging their lifecycle or package identities. Versioned source mirrors
retain their recorded locations and contents.

Publish is a versioned artifact warehouse. Product directories provide the
current catalog and integration entrypoints. Existing release archives, Maven
coordinates, checksum files, receipts and their recorded paths remain stable.
Older warehouse directories may therefore remain beside the product catalog.
Follow the catalog's exact artifact URL; never derive a download path from a
sample directory or rewrite a pinned URL to match the navigation layout.

Each product versions independently. Adding a future product means adding one
stable directory and an ecosystem entrypoint, with its own lifecycle,
distribution contract and evidence. A product name does not imply that every
host language or platform is supported.

Public documentation is authored in CoAkka's owning repository and projected
one way into Samples and Publish. Documentation synchronization does not
publish packages, admit private candidates, alter released checksums or grant
new platform support.

A small set of older sample directories retain only a README pointing to the
current product location. These pages preserve links from published package
and release documentation. They contain no executable copy, archive or symlink;
current code and instructions live under the product root.
