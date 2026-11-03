import coakka.http.Responses;
import coakka.http.Service;
import coakka.http.ServiceBuilder;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

public final class Hello {
    public static void main(String[] args) throws Exception {
        Service service = new ServiceBuilder()
            .listen("127.0.0.1", 0)
            .get("/hello", request -> Responses.bytes("hello".getBytes(StandardCharsets.UTF_8), 200))
            .start();
        try {
            HttpURLConnection reply = (HttpURLConnection) URI.create(
                "http://127.0.0.1:" + service.getPort() + "/hello").toURL().openConnection();
            reply.setConnectTimeout(5000);
            reply.setReadTimeout(5000);
            try (InputStream body = reply.getInputStream()) {
                if (reply.getResponseCode() != 200 ||
                    body.read() != 'h' || body.read() != 'e' || body.read() != 'l' ||
                    body.read() != 'l' || body.read() != 'o' || body.read() != -1) {
                    throw new IllegalStateException("unexpected HTTP response");
                }
            } finally {
                reply.disconnect();
            }
        } finally {
            service.close();
        }
        System.out.println("HTTP 200 hello");
    }
}
