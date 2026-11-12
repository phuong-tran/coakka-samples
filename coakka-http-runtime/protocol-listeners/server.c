#include <coakka/http/host.h>

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static coakka_http_host_bytes_t bytes(const char *value) {
    const coakka_http_host_bytes_t result = {(const uint8_t *)value, strlen(value)};
    return result;
}

static int checked(coakka_http_host_status_t status, const char *operation) {
    if (status == COAKKA_HTTP_HOST_OK) {
        return 1;
    }
    fprintf(stderr, "%s: %s\n", operation, coakka_http_host_status_name(status));
    return 0;
}

static int serve_one(coakka_http_host_service_t *service) {
    coakka_http_host_request_event_t event;
    coakka_http_host_request_event_init(&event);
    if (!checked(coakka_http_host_take_request(service, 15000, &event), "receive request")) {
        return 0;
    }
    const int is_request = event.kind == COAKKA_HTTP_HOST_REQUEST;
    const coakka_http_host_exchange_t exchange = event.exchange;
    if (!checked(coakka_http_host_release_request(service, &event), "release request") || !is_request) {
        return 0;
    }

    coakka_http_host_response_t response;
    coakka_http_host_response_init(&response);
    response.status_code = 200;
    response.body = bytes("hello");
    if (!checked(coakka_http_host_respond(service, exchange, &response), "send response")) {
        return 0;
    }
    coakka_http_host_request_event_init(&event);
    if (!checked(coakka_http_host_take_request(service, 15000, &event), "wait for completion")) {
        return 0;
    }
    const int completed = event.kind == COAKKA_HTTP_HOST_REQUEST_TERMINAL &&
        event.exchange.slot == exchange.slot && event.exchange.generation == exchange.generation;
    return checked(coakka_http_host_release_request(service, &event), "release completion") && completed;
}

int main(int argc, char **argv) {
    if (argc != 3) {
        fputs("usage: server http1|http2|http3 certificate-directory\n", stderr);
        return EXIT_FAILURE;
    }

    coakka_http_host_configuration_t config;
    coakka_http_host_configuration_init(&config);
    config.bind_address = bytes("127.0.0.1");
    config.port = 8080;
    if (strcmp(argv[1], "http1") == 0) {
        config.protocol = COAKKA_HTTP_HOST_PROTOCOL_HTTP_1_1;
    } else if (strcmp(argv[1], "http2") == 0) {
        config.protocol = COAKKA_HTTP_HOST_PROTOCOL_HTTP_2;
    } else if (strcmp(argv[1], "http3") == 0) {
        config.protocol = COAKKA_HTTP_HOST_PROTOCOL_HTTP_3;
    } else {
        fputs("choose http1, http2 or http3\n", stderr);
        return EXIT_FAILURE;
    }

    char certificate[4096];
    char private_key[4096];
    if (config.protocol != COAKKA_HTTP_HOST_PROTOCOL_HTTP_1_1) {
        const int cert_length = snprintf(certificate, sizeof certificate, "%s/server.pem", argv[2]);
        const int key_length = snprintf(private_key, sizeof private_key, "%s/server.key", argv[2]);
        if (cert_length < 0 || (size_t)cert_length >= sizeof certificate ||
            key_length < 0 || (size_t)key_length >= sizeof private_key) {
            fputs("certificate directory is too long\n", stderr);
            return EXIT_FAILURE;
        }
        config.port = 8443;
        config.security = COAKKA_HTTP_HOST_SECURITY_TLS;
        config.credential_generation = 1;
        config.credential_id = bytes("local-example");
        config.certificate_chain_file = bytes(certificate);
        config.private_key_file = bytes(private_key);
    }

    coakka_http_host_route_t route;
    coakka_http_host_route_init(&route);
    route.route_id = 1;
    route.handler_binding_id = 1;
    route.method = bytes("GET");
    route.encoded_path_pattern = bytes("/hello");

    coakka_http_host_service_t *service = NULL;
    uint32_t failed_route;
    if (!checked(coakka_http_host_service_create(&config, &route, 1, &service, &failed_route),
                 "create listener")) {
        return EXIT_FAILURE;
    }
    coakka_http_host_issue_t issue;
    coakka_http_host_issue_init(&issue);
    int served = 0;
    if (checked(coakka_http_host_service_start(service, &issue), "start listener")) {
        printf("listening on 127.0.0.1:%u\n", (unsigned)config.port);
        fflush(stdout);
        served = serve_one(service);
        if (served) {
            puts("Press Enter after curl completes.");
            fflush(stdout);
            (void)getchar();
        }
    }
    const int closed = checked(coakka_http_host_service_destroy(&service), "close listener");
    return served && closed ? EXIT_SUCCESS : EXIT_FAILURE;
}
