# syntax=docker/dockerfile:1

FROM debian:stable-slim

ARG TARGETARCH
ARG COAKKA_RUNTIME_VERSION=1.3.2
ARG COAKKA_RUNTIME_REVISION=caff6d6d
ARG COAKKA_RUNTIME_GENERATION=1.3.2-caff6d6d-local
ARG COAKKA_NATIVE_ARTIFACT=runtime/native/releases/1.3.2+caff6d6d/coakka-runtime-native-v2-1.3.2.tar.gz
ARG COAKKA_ARTIFACT_MANIFEST_SHA256=resolved-at-build
ARG COAKKA_PUBLISH_RAW_BASE=https://raw.githubusercontent.com/phuong-tran/coakka-publish/main

LABEL org.opencontainers.image.title="CoAkka runtime base"
LABEL org.opencontainers.image.description="Shared native runtime base for CoAkka container samples"
LABEL org.opencontainers.image.version="${COAKKA_RUNTIME_VERSION}"
LABEL org.opencontainers.image.revision="${COAKKA_RUNTIME_REVISION}"
LABEL org.opencontainers.image.source="https://github.com/phuong-tran/coakka-samples"
LABEL coakka.runtime.generation="${COAKKA_RUNTIME_GENERATION}"
LABEL coakka.native.artifact="${COAKKA_NATIVE_ARTIFACT}"
LABEL coakka.artifact.manifest.sha256="${COAKKA_ARTIFACT_MANIFEST_SHA256}"

RUN apt-get update \
  && apt-get install -y --no-install-recommends bash ca-certificates curl tar \
  && rm -rf /var/lib/apt/lists/*

WORKDIR /opt/coakka
COPY scripts/resolve-artifact.sh /opt/coakka/scripts/resolve-artifact.sh

RUN bash -lc 'set -eu; \
  case "${TARGETARCH}" in \
    amd64) coakka_platform=linux-x86_64 ;; \
    arm64) coakka_platform=linux-aarch64 ;; \
    *) echo "unsupported TARGETARCH=${TARGETARCH}" >&2; exit 1 ;; \
  esac; \
  export COAKKA_PUBLISH_RAW_BASE; \
  if [[ "${COAKKA_ARTIFACT_MANIFEST_SHA256}" != "resolved-at-build" ]]; then \
    curl -fsSL "${COAKKA_PUBLISH_RAW_BASE%/}/artifacts/public-artifacts.tsv" -o /tmp/coakka-public-artifacts.tsv; \
    echo "${COAKKA_ARTIFACT_MANIFEST_SHA256}  /tmp/coakka-public-artifacts.tsv" | sha256sum -c - >/dev/null; \
  fi; \
  mkdir -p /tmp/coakka-artifacts /tmp/coakka-native runtime; \
  source /opt/coakka/scripts/resolve-artifact.sh; \
  package_path="$(coakka_resolve_artifact /not-present "${COAKKA_NATIVE_ARTIFACT}" /tmp/coakka-artifacts/coakka-runtime-native-v2-1.3.2.tar.gz)"; \
  tar -xzf "${package_path}" -C /tmp/coakka-native; \
  install -m 0755 "/tmp/coakka-native/coakka-runtime-native-v2-1.3.2/native/${coakka_platform}/libcoakka_runtime_v2.so" /opt/coakka/runtime/libcoakka_runtime_v2.so; \
  printf "COAKKA_RUNTIME_VERSION=%s\nCOAKKA_RUNTIME_REVISION=%s\nCOAKKA_RUNTIME_GENERATION=%s\nCOAKKA_NATIVE_ARTIFACT=%s\nCOAKKA_PLATFORM=%s\n" \
    "${COAKKA_RUNTIME_VERSION}" \
    "${COAKKA_RUNTIME_REVISION}" \
    "${COAKKA_RUNTIME_GENERATION}" \
    "${COAKKA_NATIVE_ARTIFACT}" \
    "${coakka_platform}" > /opt/coakka/runtime/runtime-base.env; \
  rm -rf /tmp/coakka-artifacts /tmp/coakka-native /tmp/coakka-public-artifacts.tsv'

ENV COAKKA_RUNTIME_LIB=/opt/coakka/runtime/libcoakka_runtime_v2.so
ENV LD_LIBRARY_PATH=/opt/coakka/runtime
