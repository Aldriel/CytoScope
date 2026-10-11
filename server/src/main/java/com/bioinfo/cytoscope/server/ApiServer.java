package com.bioinfo.cytoscope.server;

import com.bioinfo.cytoscope.server.controller.ExampleController;
import com.bioinfo.cytoscope.server.controller.WorkspaceController;
import com.bioinfo.cytoscope.server.workspace.WorkspaceRegistry;
import com.bioinfo.cytoscope.service.CytoService;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * REST API consumed by the React front-end. Uses the JDK HTTP server and virtual threads.
 *
 * <p>The uploaded files stay in memory, in the workspace of their visitor, for 30 minutes after
 * the last request; nothing is written to disk. Each route is served by a controller of the
 * {@code controller} package, which delegates the analyses to the shared {@link CytoService}.
 */
public final class ApiServer {

    /** Port used when neither an argument nor the {@code PORT} variable gives one. */
    public static final int DEFAULT_PORT = 7072;

    /** Version of the application, from the Maven build. */
    public static final String VERSION = readVersion();

    /** Compensated tubes kept by the service, shared by all the workspaces. */
    static final int CACHE_CAPACITY = 24;

    private static final Logger LOG = Logger.getLogger(ApiServer.class.getName());

    private ApiServer() {}

    /**
     * Creates a server with every route registered; the caller starts it.
     *
     * @param host address to listen on
     * @param port port to listen on, or 0 for any free port
     * @param service the analyses the controllers delegate to
     * @param registry the workspaces
     * @return the server, not started
     * @throws IOException if the address cannot be bound
     */
    public static HttpServer create(String host, int port, CytoService service,
                                    WorkspaceRegistry registry) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(host, port), 0);
        server.createContext("/api/health", new HealthController(registry));
        server.createContext("/api/examples", new ExampleController());
        server.createContext("/api/workspaces",
                new WorkspaceController(service, registry, VERSION));
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        return server;
    }

    /**
     * Starts the API on {@code 127.0.0.1}, and forgets the expired workspaces every minute.
     *
     * @param args optional port number; defaults to the {@code PORT} variable, then 7072
     * @throws IOException if the port is already in use
     */
    public static void main(String[] args) throws IOException {
        start(args.length > 0 ? args[0] : System.getenv("PORT"));
    }

    /**
     * Starts the API on {@code 127.0.0.1}, and forgets the expired workspaces every minute.
     *
     * @param port port number, 0 for any free port; null for {@value #DEFAULT_PORT}
     * @return the running server
     * @throws IOException if the port is already in use
     */
    static HttpServer start(String port) throws IOException {
        int portNumber = port == null ? DEFAULT_PORT : Integer.parseInt(port);
        WorkspaceRegistry registry = new WorkspaceRegistry();
        HttpServer server = create("127.0.0.1", portNumber, new CytoService(CACHE_CAPACITY),
                registry);
        ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().daemon().name("workspace-cleaner").factory());
        cleaner.scheduleAtFixedRate(registry::removeExpired, 1, 1, TimeUnit.MINUTES);
        server.start();
        int actualPort = server.getAddress().getPort();
        LOG.info(() -> "CytoScope API " + VERSION + " on http://127.0.0.1:" + actualPort
                + "/api");
        return server;
    }

    private static String readVersion() {
        Properties p = new Properties();
        try (InputStream in = ApiServer.class.getResourceAsStream("server.properties")) {
            p.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return p.getProperty("version");
    }

    /** {@code GET /api/health}: lets clients and the monitoring check that the server runs. */
    private static final class HealthController extends JsonHandler {

        private final WorkspaceRegistry registry;

        HealthController(WorkspaceRegistry registry) {
            super("/api/health");
            this.registry = registry;
        }

        @Override
        protected Object handle(Request request) {
            return Map.of("status", "UP", "version", VERSION, "workspaces", registry.size());
        }
    }
}
