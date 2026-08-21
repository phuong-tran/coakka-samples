# Withdrawn Candidate: Camera Demo v1.1.1

Date: 2026-08-20

`camera-demo-v1.1.1` is an immutable source checkpoint for a withdrawn
prerelease candidate. No `v1.1.1` camera binary artifacts were published, no
`coakka-publish` current pointer moved to it, and it must not be presented as a
released camera package. The public prerelease erratum
[`Withdrawn candidate: camera demo v1.1.1`](https://github.com/phuong-tran/coakka-samples/releases/tag/camera-demo-v1.1.1)
is the external record of this withdrawal and has no attached artifacts.

The candidate was withdrawn because its systems audit incorrectly described a
focused AppleClang ASan/UBSan run as passing. The AppleClang 17 ASan runtime
actually spun before any test output and was terminated after 19 minutes; that
host result is blocked evidence, not a pass. A separate halt-on-error UBSan run
completed successfully. The candidate stager also retained a temporary raw
`bundles/` directory outside its release checksum set.

Replacement release `v1.1.2` uses a new source commit and
`camera-demo-v1.1.2` tag, records the Apple ASan limitation honestly, requires
the portable owner helper to pass Linux ASan with leak detection and UBSan,
links Windows libuv statically, and constructs platform archives outside the
promoted release tree.
