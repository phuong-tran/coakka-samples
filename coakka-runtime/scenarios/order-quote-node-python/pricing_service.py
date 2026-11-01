"""Run the Python pricing handler until Enter, EOF or Ctrl+C."""
import argparse
import sys
from coakka_v2_connector import ConnectorStartSpec, RuntimeHost, RuntimeNetworkConfig, local_route
from pricing import quote_payload

TARGET = "samples.order.quote"


def port_number(text):
    value = int(text)
    if not 1 <= value <= 65535:
        raise argparse.ArgumentTypeError("port must be between 1 and 65535")
    return value


def serve(port, handler=quote_payload):
    spec = ConnectorStartSpec(
        system_name="order-quote-demo", node_id="python-pricing",
        routes=[local_route(TARGET)],
        network=RuntimeNetworkConfig.network_node("127.0.0.1", port, "127.0.0.1"),
    )
    # Runtime owns request delivery and replies; the handler owns only pricing.
    with RuntimeHost.start(start_spec=spec) as host:
        host.register_payload_handler(TARGET, handler)
        accepted = host.runtime_network_config()
        print(f"Pricing service ready at {accepted['bindHost']}:{accepted['bindPort']}", flush=True)
        print("Press Enter or Ctrl+C to stop.", flush=True)
        try:
            sys.stdin.read(1)
        except KeyboardInterrupt:
            pass
    print("Pricing service stopped.", flush=True)


def main():
    parser = argparse.ArgumentParser(description="Price a cart received from the Node.js demo.")
    parser.add_argument("--port", type=port_number, default=19360)
    args = parser.parse_args()
    try:
        serve(args.port)
    except Exception:
        print("Pricing service could not run. Check that the port is free and the package is installed.", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
