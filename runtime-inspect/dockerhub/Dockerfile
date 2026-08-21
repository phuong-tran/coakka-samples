FROM ubuntu:24.04@sha256:4fbb8e6a8395de5a7550b33509421a2bafbc0aab6c06ba2cef9ebffbc7092d90

LABEL org.opencontainers.image.base.name="docker.io/library/ubuntu:24.04" \
      io.coakka.base.digest="sha256:4fbb8e6a8395de5a7550b33509421a2bafbc0aab6c06ba2cef9ebffbc7092d90"

ARG TARGETARCH
ARG RUNTIME_GENERATION
ARG SAMPLES_SOURCE_COMMIT

LABEL org.opencontainers.image.revision="${SAMPLES_SOURCE_COMMIT}" \
      io.coakka.runtime.generation="${RUNTIME_GENERATION}"

COPY inspect/${TARGETARCH}/ /opt/coakka-runtime-inspect/
COPY entrypoint.sh /usr/local/bin/coakka-runtime-inspect-sample
COPY IMAGE-RELEASE.txt /opt/coakka-image/RELEASE.txt

ENV LD_LIBRARY_PATH=/opt/coakka-runtime-inspect/lib

EXPOSE 18080

ENTRYPOINT ["/usr/local/bin/coakka-runtime-inspect-sample"]
CMD ["serve"]
