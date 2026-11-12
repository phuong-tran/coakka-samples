import coakka.http.Listener;
import coakka.http.ListenerProtocol;
import coakka.http.Responses;
import coakka.http.Service;
import coakka.http.ServiceBuilder;
import coakka.http.TransportSecurity;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;

public final class Server {
    public static void main(String[] args) throws InterruptedException, TimeoutException {
        if (args.length != 2) {
            throw new IllegalArgumentException("usage: Server http1|http2|http3 certificate-directory");
        }
        ServiceBuilder builder = new ServiceBuilder();
        switch (args[0]) {
            case "http1":
                builder.listen("127.0.0.1", 8080);
                break;
            case "http2":
            case "http3":
                ListenerProtocol protocol = args[0].equals("http2")
                    ? ListenerProtocol.HTTP_2 : ListenerProtocol.HTTP_3;
                Path certificates = Path.of(args[1]).toAbsolutePath();
                builder.listener(new Listener("127.0.0.1", 8443, protocol, TransportSecurity.TLS,
                    1, "local-example", certificates.resolve("server.pem").toString(),
                    certificates.resolve("server.key").toString()));
                break;
            default:
                throw new IllegalArgumentException("choose http1, http2 or http3");
        }

        CountDownLatch stop = new CountDownLatch(1);
        CountDownLatch closed = new CountDownLatch(1);
        Service service = builder.get("/hello", request -> Responses.text("hello")).start();
        try {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                stop.countDown();
                try {
                    closed.await();
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                }
            }));
            System.out.println("listening on 127.0.0.1:" + service.getPort());
            stop.await();
        } finally {
            try {
                service.close();
            } finally {
                closed.countDown();
            }
        }
    }
}
