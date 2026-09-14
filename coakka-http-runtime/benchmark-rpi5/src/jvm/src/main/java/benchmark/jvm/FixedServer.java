package benchmark.jvm;

import coakka.http.server.Responses;
import coakka.http.server.Service;
import coakka.http.server.ServiceBuilder;
import com.sun.net.httpserver.HttpServer;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

public final class FixedServer {
    private static final byte[] BODY =
        "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.US_ASCII);
    private static final String HOST = "127.0.0.1";
    private static final int WORKERS = 3;

    private FixedServer() {}

    public static void main(String[] args) throws Exception {
        if (args.length == 2 && args[0].equalsIgnoreCase("core")) {
            CoreFixedServer.run(args[1]);
            return;
        }
        if (args.length != 1) {
            throw new IllegalArgumentException(
                "usage: FixedServer <coakka|direct|netty|tomcat|jetty> | core <platform-default|io-uring>"
            );
        }
        String mode = args[0].toLowerCase(Locale.ROOT);
        RunningServer server = switch (mode) {
            case "coakka" -> startCoakka();
            case "direct" -> startDirect();
            case "netty" -> startNetty();
            case "tomcat" -> startTomcat();
            case "jetty" -> startJetty();
            default -> throw new IllegalArgumentException("unknown JVM lane: " + mode);
        };
        CountDownLatch stopped = new CountDownLatch(1);
        AtomicBoolean closing = new AtomicBoolean();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (!closing.compareAndSet(false, true)) {
                return;
            }
            try {
                server.close();
                System.out.println("{\"stopped\":true}");
            } catch (Exception error) {
                error.printStackTrace(System.err);
            } finally {
                stopped.countDown();
            }
        }, "benchmark-shutdown"));
        String identity = mode.equals("coakka")
            ? ",\"application_path\":\"normal\""
            : "";
        System.out.println(
            "{\"ready\":true,\"bound_port\":" + server.port() + identity + "}"
        );
        stopped.await();
    }

    private static RunningServer startCoakka() {
        Service service = new ServiceBuilder()
            .listen(HOST, 0)
            .concurrency(WORKERS, 256)
            .get("/fixed", request -> Responses.bytes(BODY))
            .start();
        return new RunningServer() {
            @Override
            public int port() {
                return service.getPort();
            }

            @Override
            public void close() {
                service.close();
            }
        };
    }

    private static RunningServer startDirect() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(HOST, 0), 256);
        ExecutorService executor = Executors.newFixedThreadPool(WORKERS);
        server.setExecutor(executor);
        server.createContext("/fixed", exchange -> {
            if (!exchange.getRequestMethod().equals("GET")) {
                exchange.sendResponseHeaders(405, -1);
            } else {
                exchange.sendResponseHeaders(200, BODY.length);
                exchange.getResponseBody().write(BODY);
            }
            exchange.close();
        });
        server.start();
        return new RunningServer() {
            @Override
            public int port() {
                return server.getAddress().getPort();
            }

            @Override
            public void close() throws InterruptedException {
                server.stop(0);
                executor.shutdown();
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("JDK HTTP workers did not stop");
                }
            }
        };
    }

    private static RunningServer startNetty() throws InterruptedException {
        EventLoopGroup boss = new NioEventLoopGroup(1);
        EventLoopGroup workers = new NioEventLoopGroup(WORKERS);
        Channel listener;
        try {
            listener = new ServerBootstrap()
                .group(boss, workers)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 256)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel channel) {
                        channel.pipeline()
                            .addLast(new HttpServerCodec())
                            .addLast(new HttpObjectAggregator(1 << 20))
                            .addLast(new SimpleChannelInboundHandler<FullHttpRequest>() {
                                @Override
                                protected void channelRead0(
                                    ChannelHandlerContext context,
                                    FullHttpRequest request
                                ) {
                                    FullHttpResponse response = request.method().name().equals("GET")
                                            && request.uri().equals("/fixed")
                                        ? nettyResponse()
                                        : new DefaultFullHttpResponse(
                                            HttpVersion.HTTP_1_1,
                                            HttpResponseStatus.NOT_FOUND
                                        );
                                    HttpUtil.setContentLength(
                                        response,
                                        response.content().readableBytes()
                                    );
                                    HttpUtil.setKeepAlive(response, HttpUtil.isKeepAlive(request));
                                    context.writeAndFlush(response);
                                }
                            });
                    }
                })
                .bind(HOST, 0)
                .sync()
                .channel();
        } catch (RuntimeException | InterruptedException error) {
            workers.shutdownGracefully().syncUninterruptibly();
            boss.shutdownGracefully().syncUninterruptibly();
            throw error;
        }
        return new RunningServer() {
            @Override
            public int port() {
                return ((InetSocketAddress) listener.localAddress()).getPort();
            }

            @Override
            public void close() {
                listener.close().syncUninterruptibly();
                workers.shutdownGracefully(0, 5, TimeUnit.SECONDS).syncUninterruptibly();
                boss.shutdownGracefully(0, 5, TimeUnit.SECONDS).syncUninterruptibly();
            }
        };
    }

    private static FullHttpResponse nettyResponse() {
        return new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1,
            HttpResponseStatus.OK,
            Unpooled.wrappedBuffer(BODY)
        );
    }

    private static RunningServer startTomcat() throws Exception {
        Tomcat tomcat = new Tomcat();
        tomcat.setHostname(HOST);
        tomcat.setPort(0);
        tomcat.getConnector().setProperty("maxThreads", Integer.toString(WORKERS));
        tomcat.getConnector().setProperty("minSpareThreads", Integer.toString(WORKERS));
        tomcat.getConnector().setProperty("acceptCount", "256");
        Path base = Files.createTempDirectory("coakka-http-tomcat-");
        tomcat.setBaseDir(base.toString());
        Context context = tomcat.addContext("", base.toString());
        Tomcat.addServlet(context, "fixed", new HttpServlet() {
            @Override
            protected void doGet(
                HttpServletRequest request,
                HttpServletResponse response
            ) throws IOException {
                response.setStatus(200);
                response.setContentLength(BODY.length);
                response.getOutputStream().write(BODY);
            }
        });
        context.addServletMapping("/fixed", "fixed");
        tomcat.start();
        return new RunningServer() {
            @Override
            public int port() {
                return tomcat.getConnector().getLocalPort();
            }

            @Override
            public void close() throws Exception {
                tomcat.stop();
                tomcat.destroy();
                Files.deleteIfExists(base);
            }
        };
    }

    private static RunningServer startJetty() throws Exception {
        QueuedThreadPool threads = new QueuedThreadPool(8, 8);
        threads.setName("jetty-benchmark");
        Server server = new Server(threads);
        ServerConnector connector = new ServerConnector(server, 1, 1);
        connector.setHost(HOST);
        connector.setPort(0);
        connector.setAcceptQueueSize(256);
        server.addConnector(connector);
        server.setHandler(new Handler.Abstract() {
            @Override
            public boolean handle(
                Request request,
                Response response,
                Callback callback
            ) {
                if (!request.getHttpURI().getPath().equals("/fixed")
                    || !request.getMethod().equals("GET")) {
                    response.setStatus(404);
                    callback.succeeded();
                    return true;
                }
                response.setStatus(200);
                response.getHeaders().put(HttpHeader.CONTENT_LENGTH, BODY.length);
                response.write(true, ByteBuffer.wrap(BODY), callback);
                return true;
            }
        });
        server.start();
        return new RunningServer() {
            @Override
            public int port() {
                return connector.getLocalPort();
            }

            @Override
            public void close() throws Exception {
                server.stop();
                server.join();
            }
        };
    }

    private interface RunningServer {
        int port();

        void close() throws Exception;
    }
}
