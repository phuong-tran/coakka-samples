# Runtime Package Licensing

CoAkka Runtime packages that bundle native libraries are file-scoped
multi-license distributions. The terms are simultaneous, not alternative
licenses for the package as a whole.

## File Scope

| Material | Terms | Packaged file |
| --- | --- | --- |
| Connector source, generated bindings, type declarations, package metadata, examples, and package documentation | [Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0) | `LICENSE` |
| CoAkka native libraries, native headers, symbol files, and native-only provenance | [CoAkka Public Artifact License 1.1](https://github.com/phuong-tran/coakka-publish/blob/main/LICENSE.md) | `NATIVE-LICENSE.md` |
| File-to-license mapping | The scope in this document and the package-specific map | `PACKAGE-LICENSE.md` |
| Aggregate package notice | Package identity and applicable license summary | `NOTICE` |

Third-party or vendored components retain their own terms. An explicit notice
inside a file or component controls that material.

## Registry Pages

Package registry pages do not expose arbitrary files embedded in an npm
tarball, Python wheel, or NuGet package at paths relative to the rendered
README. Registry-facing README links must therefore use absolute public URLs.
The embedded `LICENSE`, `NATIVE-LICENSE.md`, `PACKAGE-LICENSE.md`, and `NOTICE`
remain the authoritative offline copies shipped with each package.

Runtime `2.5.0` npm and PyPI READMEs incorrectly used relative links for those
four files. The files and package license metadata are present and unchanged,
but the registry-rendered links return `404`. Published package bytes are
immutable; corrected absolute links require a later package release. NuGet
Runtime `2.5.0` serves its `PACKAGE-LICENSE.md` through the registry's
`License Info` endpoint, while its README names the other embedded files
without linking them.
