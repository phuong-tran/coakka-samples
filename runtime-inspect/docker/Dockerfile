FROM ubuntu:24.04

COPY inspect/ /opt/coakka-runtime-inspect/
COPY entrypoint.sh /usr/local/bin/coakka-runtime-inspect-sample

ENV LD_LIBRARY_PATH=/opt/coakka-runtime-inspect/lib

EXPOSE 18080

ENTRYPOINT ["/usr/local/bin/coakka-runtime-inspect-sample"]
CMD ["serve"]
