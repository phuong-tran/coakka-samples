package sample;

import coakka.http.FileAuthority;
import coakka.http.Responses;
import coakka.http.RouteControlCode;
import coakka.http.RouteRebind;
import coakka.http.RouteRebindOutcome;
import coakka.http.RouteSnapshot;
import coakka.http.RouteState;
import coakka.http.Service;
import coakka.http.ServiceBuilder;
import coakka.http.StaticMount;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

/**
 * Finite Java 8 consumer: ordinary lambdas, static files, SPA, download and
 * generation-checked handler replacement. No Kotlin source or coroutine API
 * is required by the application. Core remains the configuration/state owner.
 */
public final class JavaFeatures {
    private JavaFeatures() {}

    /**
     * Run against a trusted sample asset directory, then close before success.
     * The only HTTP client is an independent bounded test peer on the main
     * thread; it does not replace the runtime's outbound implementation.
     */
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("expected the sample assets directory");
        }
        Path assets = Paths.get(args[0]).toRealPath();
        // Files are tiny, trusted test inputs; reject large fixtures before read.
        byte[] index = fixture(assets.resolve("index.html"));
        byte[] file = fixture(assets.resolve("sample.txt"));
        Service service = new ServiceBuilder()
                .staticMount(new StaticMount(
                        "/app", assets.toString(), "index.html", null, "index.html"))
                .fileAuthority(new FileAuthority(82L, assets.toString(), 2, 1L << 20))
                .get("/version", request -> Responses.text("v1"))
                .get("/download", request -> Responses.file(82L, "/sample.txt"))
                .get("/api/hello/{name}", request -> Responses.text("hello " +
                        new String(request.getPathParameters().get(0).encodedValue(),
                                StandardCharsets.UTF_8)))
                .start();
        Throwable primary = null;
        try {
            int port = service.getPort();
            expect(port, "/version", null, null, 200, bytes("v1"));
            expect(port, "/api/hello/Ada", null, null, 200, bytes("hello Ada"));
            expect(port, "/app/index.html", null, null, 200, index);
            expect(port, "/app/client/route", "Accept", "text/html", 200, index);
            expect(port, "/download", null, null, 200, file);
            expect(port, "/download", "Range", "bytes=0-3", 206,
                    Arrays.copyOfRange(file, 0, 4));

            // Route 1 is application identity from the first get declaration.
            // Revisions come from Core, not a copied default or local counter.
            RouteSnapshot before = service.getRoutes();
            RouteState version = before.getRoutes().stream()
                    .filter(route -> route.getRouteId() == 1L)
                    .findFirst().orElseThrow(() -> new IllegalStateException("missing route"));
            service.prepareHandler(100L, request -> Responses.text("v2"));
            RouteRebindOutcome applied = service.rebindHandler(new RouteRebind(
                    1L, before.getRouteGeneration(), version.getRouteId(),
                    version.getBindingRevision(), 100L));
            if (applied.getCode() != RouteControlCode.APPLIED || !applied.getChanged()) {
                throw new IllegalStateException("handler activation refused: " + applied.getCode());
            }
            expect(port, "/version", null, null, 200, bytes("v2"));

            // A new activation carrying the old revision must not roll back v2.
            service.prepareHandler(101L, request -> Responses.text("unreachable"));
            RouteRebindOutcome refused = service.rebindHandler(new RouteRebind(
                    2L, before.getRouteGeneration(), version.getRouteId(),
                    version.getBindingRevision(), 101L));
            if (refused.getCode() != RouteControlCode.REVISION_MISMATCH
                    || refused.getChanged()
                    || refused.getEffectiveHandlerBindingId() != 100L) {
                throw new IllegalStateException("stale activation changed the binding");
            }
            expect(port, "/version", null, null, 200, bytes("v2"));
        } catch (Exception | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            // Preserve both failures without pretending a refused close freed
            // the owner. This finite executable fails instead of restarting it.
            try {
                service.close();
            } catch (Exception | Error failure) {
                if (primary == null) throw failure;
                primary.addSuppressed(failure);
            }
        }
        System.out.println("java-features=pass");
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] fixture(Path path) throws Exception {
        if (!Files.isRegularFile(path) || Files.size(path) > 65_536) {
            throw new IllegalArgumentException("invalid or oversized fixture");
        }
        return Files.readAllBytes(path);
    }

    /** One finite loopback request; cap retention independently of the peer. */
    private static void expect(int port, String path, String header, String value,
                               int status, byte[] expected) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(
                "http://127.0.0.1:" + port + path).openConnection();
        connection.setConnectTimeout(5_000);
        connection.setReadTimeout(5_000);
        connection.setInstanceFollowRedirects(false);
        if (header != null) connection.setRequestProperty(header, value);
        try {
            if (connection.getResponseCode() != status) {
                throw new IllegalStateException("unexpected HTTP status for " + path);
            }
            try (InputStream input = connection.getInputStream();
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                for (int count; (count = input.read(buffer)) != -1;) {
                    if (count > 65_536 - output.size()) {
                        throw new IllegalStateException("response exceeded test bound");
                    }
                    output.write(buffer, 0, count);
                }
                if (!Arrays.equals(expected, output.toByteArray())) {
                    throw new IllegalStateException("unexpected response for " + path);
                }
            }
        } finally {
            connection.disconnect();
        }
    }
}
