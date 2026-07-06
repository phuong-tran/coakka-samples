# Contributing To coakka-samples

Thanks for contributing.

This repository is the public sample and documentation surface for CoAkka.
It is not the place to redesign runtime-core internals, transport internals,
or unpublished connector boundaries.

## What This Repo Accepts

Good contributions here include:

- sample fixes that keep the current public artifact line runnable
- documentation clarifications
- better sample diagnostics or operator-facing evidence
- wording cleanups that make the public story easier to understand
- sample-smoke or pin-check fixes that keep published samples honest

## What Should Go Somewhere Else

Open or route changes elsewhere when the work is really about:

- runtime-core internals
- private addon incubation
- unpublished connector contracts
- source-build or packaging mechanics that belong in source-owner repos
- artifact publication layout or manifest policy that belongs in
  `coakka-publish`

## Public Wording Rule

Please keep the public story clear and intentional.

- do not introduce internal implementation names unless users truly need them
- do not turn samples into a source of hidden promises
- do not add hedging language that makes the repo sound unsure about features
  that already work
- do not describe CoAkka as replacing every HTTP API, broker, CQRS design, or
  actor system

Prefer:

- what the sample proves
- what boundary CoAkka owns
- what still belongs to the app-host, public edge, or deployment policy

## Version And Artifact Rule

If a change touches artifact coordinates, Docker tags, or versioned sample
pins, keep the affected surfaces aligned in the same PR:

- README snippets
- docs
- sample config
- helper scripts
- pinned artifact checks

Do not update one visible sample path to a new version while leaving the rest
of the public story on another version.

## Before Opening A PR

Please run the smallest checks that match your change.

Common checks:

```sh
bash run.sh list
bash scripts/check-artifact-pins.sh
bash v2/scripts/check_public_samples_surface_wording.sh ../coakka-samples
```

If you changed a runnable sample, also run the narrowest relevant sample smoke.

Examples:

```sh
bash run.sh runtime jvm basic
bash run.sh containers node-python smoke
```

## PR Notes

Keep PRs narrow.

Please say:

- what changed
- which sample or doc surface changed
- what you ran to verify it
- whether the change depends on another repository or release train update

If the change needs a coordinated artifact refresh across repositories, call
that out explicitly instead of silently editing only this repo.
