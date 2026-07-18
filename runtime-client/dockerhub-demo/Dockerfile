FROM ubuntu:24.04

ARG TARGETARCH

COPY artifacts/${TARGETARCH}/cli/ /opt/coakka/
COPY artifacts/${TARGETARCH}/customer-service/ /opt/coakka-demo/customer-service/
COPY entrypoint.sh /usr/local/bin/coakka-runtime-client-demo

ENV LD_LIBRARY_PATH=/opt/coakka/lib:/opt/coakka-demo/customer-service/lib

EXPOSE 19091 19092

ENTRYPOINT ["/usr/local/bin/coakka-runtime-client-demo"]
CMD ["walkthrough"]
