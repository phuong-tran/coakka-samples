# CoAkka Runtime Client Docs

`coakka-runtime-client` is the CLI runtime client for CoAkka Runtime. The
published command and archive prefix is `coakka-client`.

After unpacking a published archive, run `coakka-client --help`. The
`coakka-runtime-client` name identifies this product lane and docs folder.

Public runtime-client archives are distributed through
[`coakka-publish`](https://github.com/phuong-tran/coakka-publish), while this
repository provides runnable samples, docs, and verification commands.

This docs set is for users who want more than the short sample README:

- [Introduction](introduction.md): what the runtime client is, where it fits,
  and what it is not.
- [Usage Guide](usage.md): command discovery, diagnostics, request/reply,
  JSON payloads, and shell-script mode.
- [Technical Notes](technical-notes.md): artifact layout, runtime boundary,
  transport profile, payload metadata, and verification posture.

Start with the CLI walkthrough when you want to see the command flow:

![CoAkka Runtime Client CLI walkthrough](../../docs/assets/coakka-runtime-client.gif)

Full recording: [coakka-runtime-client.mp4](../../docs/assets/coakka-runtime-client.mp4)

Then run the local smoke:

```sh
bash run.sh runtime-client
```
