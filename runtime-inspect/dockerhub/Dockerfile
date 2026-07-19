FROM ubuntu:24.04

ARG TARGETARCH

RUN apt-get update \
  && apt-get install -y --no-install-recommends libprotobuf32t64 libuv1 \
  && rm -rf /var/lib/apt/lists/*

COPY inspect/${TARGETARCH}/ /opt/coakka-runtime-inspect/
COPY entrypoint.sh /usr/local/bin/coakka-runtime-inspect-sample

ENV LD_LIBRARY_PATH=/opt/coakka-runtime-inspect/lib

EXPOSE 18080

ENTRYPOINT ["/usr/local/bin/coakka-runtime-inspect-sample"]
CMD ["serve"]
