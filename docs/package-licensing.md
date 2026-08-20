# CoAkka Package Licensing

## Free For Application Use

CoAkka platform packages are free to use in applications, including commercial
and production applications. Connector code, bindings, samples, and
documentation use Apache-2.0. Bundled Native Core files use the CoAkka Native
Artifact License, which permits ordinary application use but reserves selling
CoAkka itself as managed runtime or infrastructure.

No runtime activation, paid feature gate, or production-use fee is imposed by
these package licenses.

## File Scope

Binary-bearing packages are file-scoped multi-license distributions. The terms
apply simultaneously to their respective files; they are not alternative
licenses for the package as a whole.

| Material | Terms | Packaged copy |
| --- | --- | --- |
| Connector source, generated bindings, type declarations, package metadata, examples, and package documentation | [Apache License, Version 2.0](https://github.com/phuong-tran/coakka-samples/blob/main/LICENSE) | `LICENSE` |
| Compiled CoAkka native libraries or executables, native headers, symbols, and native-only provenance | [CoAkka Native Artifact License 1.2](https://github.com/phuong-tran/coakka-samples/blob/main/NATIVE-LICENSE.md) | `NATIVE-LICENSE.md` |
| File-to-license mapping | This document and the package-specific scope map | `PACKAGE-LICENSE.md` |
| Aggregate attribution | Package identity and applicable license summary | `NOTICE` |

Source-only framework adapters that do not contain Native Artifacts can use
Apache-2.0 alone. A package that contains Native Artifacts must preserve both
license copies and the file-scope map.

## Infrastructure Boundary

The Native Artifact License allows companies of any size, including cloud and
infrastructure companies, to run CoAkka internally and to use it inside an
independent application or SaaS product. A separate agreement is required when
the primary or substantial product offered to third parties is hosted CoAkka,
a CoAkka control plane, a CoAkka runtime platform, or equivalent managed
infrastructure.

The restriction depends on what is offered, not who the user is.

## Package Manager Display

Registry README pages should lead with the free application-use statement and
link to the three stable `coakka-samples` documents above. Registry metadata
must still identify the embedded package license file when a package contains
custom-licensed Native Artifacts. It must not label the complete binary-bearing
package as Apache-2.0.

Every package also carries offline copies of `LICENSE`, `NATIVE-LICENSE.md`,
`PACKAGE-LICENSE.md`, and `NOTICE`. Registry rendering is not assumed to expose
arbitrary package members through README-relative URLs.

## Historical Releases

Runtime `2.5.0` npm and PyPI packages contain their required offline license
files, but their immutable registry READMEs use relative links that the
registries do not expose as package-member pages. NuGet Runtime `2.5.0` serves
its embedded package license through the registry License Info endpoint. The
documentation-and-licensing patch train supersedes the broken README links
without changing the Runtime `2.5.0` native generation.
