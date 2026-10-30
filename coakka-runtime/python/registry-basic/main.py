from coakka_v2_connector import ConnectorStartSpec, RuntimeHost, local_route


def main() -> None:
    target = "samples.echo"
    spec = ConnectorStartSpec(
        system_name="python-first-run",
        node_id="python-first-run-node",
        routes=[local_route(target)],
    )
    with RuntimeHost.start(start_spec=spec) as runtime:
        runtime.register_payload_handler(target, lambda payload: b"accepted-" + payload)
        response = runtime.ask_payload(
            source="python-first-run", target=target, payload=b"hello", timeout_ms=2000,
        )
        print(response.decode("utf-8"))


if __name__ == "__main__":
    main()
