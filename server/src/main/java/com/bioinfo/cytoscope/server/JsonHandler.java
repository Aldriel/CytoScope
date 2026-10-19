package com.bioinfo.cytoscope.server;

import com.bioinfo.cytoscope.experiment.MissingSampleException;
import com.bioinfo.cytoscope.fcs.FcsFormatException;
import com.bioinfo.cytoscope.server.workspace.CapacityException;
import com.bioinfo.cytoscope.server.workspace.WorkspaceGoneException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Base class of the REST controllers (Template Method pattern): JSON serialization, CORS, request
 * size limits and translation of exceptions into HTTP status codes. Same error format as the
 * other APIs of the portfolio (ProteoSpectra Explorer, MabScope).
 *
 * <p>Subclasses only implement {@link #handle(Request)}, which returns the object to send back.
 * Errors are answered as {@code {"error": "...", "kind": "..."}}.
 */
public abstract class JsonHandler implements HttpHandler {

    /** Largest JSON request body accepted, in bytes. */
    static final int MAX_BODY_BYTES = 2 * 1024 * 1024;

    /** Largest FCS file accepted, in bytes. */
    public static final int MAX_UPLOAD_BYTES = 50 * 1024 * 1024;

    private static final int MEGABYTE = 1024 * 1024;

    private static final Logger LOG = Logger.getLogger(JsonHandler.class.getName());

    /** Category of an error, which the web interface translates. */
    public enum Kind {
        /** The file is not a valid FCS file. */
        PARSING,
        /** Invalid request or parameter. */
        INVALID_INPUT,
        /** Unknown endpoint or tube. */
        NOT_FOUND,
        /** The workspace has expired: the files must be loaded again. */
        EXPIRED,
        /** The uploaded file or the request body is too large. */
        TOO_LARGE,
        /** The server holds too much data from other users for now. */
        BUSY,
        /** The free trial is spent or the access code has no credits left. */
        PAYMENT_REQUIRED,
        /** Bug or unexpected failure. */
        UNEXPECTED
    }

    /**
     * Error sent to the client.
     *
     * @param status HTTP status code
     * @param kind category of the error
     * @param message explanation, in English
     */
    public record Failure(int status, Kind kind, String message) {}

    /**
     * File returned by a controller instead of a JSON document, sent as an attachment so that the
     * browser saves it.
     *
     * @param content bytes of the file
     * @param contentType media type, such as {@code application/pdf}
     * @param fileName name proposed to the user
     */
    public record FileResponse(byte[] content, String contentType, String fileName) {}

    /**
     * Decoded HTTP request handed to the controllers.
     *
     * @param method HTTP method, in upper case
     * @param segments parts of the path after the context prefix, decoded; may be empty
     * @param query decoded query parameters; the first value wins when a name is repeated
     * @param content raw request body, read by {@link #json} or {@link #upload()}
     */
    public record Request(
            String method, List<String> segments, Map<String, String> query, InputStream content) {

        /** Copies the segments. */
        public Request {
            segments = List.copyOf(segments);
        }

        /**
         * Reads the body as an uploaded file.
         *
         * @return the bytes of the file
         * @throws HttpError with status 413 beyond {@value #MAX_UPLOAD_BYTES} bytes
         * @throws IOException if the body cannot be read
         */
        public byte[] upload() throws IOException {
            byte[] bytes = content.readNBytes(MAX_UPLOAD_BYTES + 1);
            if (bytes.length > MAX_UPLOAD_BYTES) {
                throw new HttpError(413, "Files are limited to " + MAX_UPLOAD_BYTES / MEGABYTE
                        + " MB");
            }
            return bytes;
        }

        /**
         * Returns a query parameter, stripped of surrounding spaces.
         *
         * @param name parameter name
         * @param defaultValue value returned when the parameter is absent or blank
         * @return the value, or {@code defaultValue}
         */
        public String param(String name, String defaultValue) {
            String v = query.get(name);
            return v == null || v.isBlank() ? defaultValue : v.strip();
        }

        /**
         * Decodes the JSON body.
         *
         * @param <T> type of the body
         * @param type class of the body
         * @return the decoded body; never {@code null}
         * @throws HttpError with status 400 if the body is not valid JSON for {@code type}, 413 if
         *     it exceeds {@value #MAX_BODY_BYTES} bytes
         * @throws IOException if the body cannot be read
         */
        public <T> T json(Class<T> type) throws IOException {
            byte[] bytes = content.readNBytes(MAX_BODY_BYTES + 1);
            if (bytes.length > MAX_BODY_BYTES) {
                throw new HttpError(413, "Request body exceeds " + MAX_BODY_BYTES / MEGABYTE
                        + " MB");
            }
            T value;
            try {
                value = Json.mapper().readValue(bytes, type);
            } catch (JsonProcessingException e) {
                throw new HttpError(400, "Invalid request: " + e.getOriginalMessage());
            }
            if (value == null) {
                throw new HttpError(400, "Empty request");
            }
            return value;
        }
    }

    private final String contextPath;

    /**
     * Creates a controller.
     *
     * @param contextPath path prefix the controller is registered under, such as {@code /api/x}
     */
    protected JsonHandler(String contextPath) {
        this.contextPath = contextPath;
    }

    /**
     * Processes a request.
     *
     * @param request decoded request
     * @return the object to serialize as JSON, sent with status 200, or a {@link FileResponse}
     * @throws IOException if the request body cannot be read
     */
    protected abstract Object handle(Request request) throws IOException;

    @Override
    public final void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            int status = 200;
            Object payload;
            try {
                payload = handle(toRequest(exchange));
            } catch (IOException | RuntimeException e) {
                Failure failure = describe(e);
                if (failure.status() == 500) {
                    LOG.log(Level.SEVERE, "Unexpected error on " + exchange.getRequestURI(), e);
                }
                status = failure.status();
                payload = error(failure);
            }
            if (payload instanceof FileResponse file) {
                sendFile(exchange, file);
            } else {
                sendJson(exchange, status, payload);
            }
        }
    }

    /**
     * Translates an exception into the error sent to the client:
     *
     * <ul>
     *   <li>the status of an {@link HttpError};
     *   <li>400 for a file that is not FCS ({@link FcsFormatException}) and for other invalid
     *       input ({@link IllegalArgumentException});
     *   <li>404 for a tube that is no longer loaded;
     *   <li>410 for an expired workspace;
     *   <li>503 when the server holds too much data;
     *   <li>500 for any other error.
     * </ul>
     *
     * @param e exception thrown while processing a request
     * @return the status code and message
     */
    public static Failure describe(Exception e) {
        return switch (e) {
            case HttpError http -> new Failure(http.status(), kindOf(http.status()),
                    http.getMessage());
            case FcsFormatException fcs -> new Failure(400, Kind.PARSING, fcs.getMessage());
            case MissingSampleException missing -> new Failure(404, Kind.NOT_FOUND,
                    missing.getMessage());
            case WorkspaceGoneException gone -> new Failure(410, Kind.EXPIRED, gone.getMessage());
            case CapacityException busy -> new Failure(503, Kind.BUSY, busy.getMessage());
            case IllegalArgumentException invalid -> new Failure(400, Kind.INVALID_INPUT,
                    invalid.getMessage());
            default -> new Failure(500, Kind.UNEXPECTED, "Internal server error");
        };
    }

    private static Kind kindOf(int status) {
        return switch (status) {
            case 400, 405 -> Kind.INVALID_INPUT;
            case 402 -> Kind.PAYMENT_REQUIRED;
            case 404 -> Kind.NOT_FOUND;
            case 413 -> Kind.TOO_LARGE;
            default -> Kind.UNEXPECTED;
        };
    }

    /** Allows the Vite development server, which runs on another port, to call the API. */
    private static void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods",
                "GET, POST, PUT, DELETE, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
        exchange.getResponseHeaders().add("Access-Control-Expose-Headers", "Content-Disposition");
    }

    private static void sendJson(HttpExchange exchange, int status, Object payload)
            throws IOException {
        byte[] bytes = Json.mapper().writeValueAsBytes(payload);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static void sendFile(HttpExchange exchange, FileResponse file) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", file.contentType());
        exchange.getResponseHeaders()
                .set("Content-Disposition", contentDisposition(file.fileName()));
        exchange.sendResponseHeaders(200, file.content().length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(file.content());
        }
    }

    /**
     * Returns the Content-Disposition header of an attachment: an ASCII name for old clients, and
     * the exact name encoded in UTF-8 (RFC 6266).
     *
     * @param fileName name proposed to the user
     * @return the header value
     */
    static String contentDisposition(String fileName) {
        String ascii = fileName.replaceAll("[^A-Za-z0-9._-]", "_");
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment; filename=\"" + ascii + "\"; filename*=UTF-8''" + encoded;
    }

    private Request toRequest(HttpExchange exchange) {
        String path = exchange.getRequestURI().getRawPath();
        String sub = path.length() > contextPath.length() ? path.substring(contextPath.length())
                : "";
        List<String> segments = Arrays.stream(sub.split("/")).filter(s -> !s.isEmpty())
                .map(s -> decode(s.replace("+", "%2B"))).toList();
        return new Request(
                exchange.getRequestMethod().toUpperCase(Locale.ROOT),
                segments,
                parseQuery(exchange.getRequestURI().getRawQuery()),
                exchange.getRequestBody());
    }

    /**
     * Decodes a URL query string. Parameters without value map to an empty string; when a name is
     * repeated, the first value is kept.
     *
     * @param rawQuery encoded query, without the leading {@code ?}; may be {@code null}
     * @return the parameters, in order of appearance
     */
    static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> params = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isEmpty()) {
            return params;
        }
        for (String pair : rawQuery.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String key = decode(eq < 0 ? pair : pair.substring(0, eq));
            String value = eq < 0 ? "" : decode(pair.substring(eq + 1));
            params.putIfAbsent(key, value);
        }
        return params;
    }

    private static String decode(String encoded) {
        return URLDecoder.decode(encoded, StandardCharsets.UTF_8);
    }

    /**
     * Rejects a request whose HTTP method is not the expected one.
     *
     * @param request current request
     * @param method expected method, in upper case
     * @throws HttpError with status 405 if the methods differ
     */
    protected static void requireMethod(Request request, String method) {
        if (!request.method().equals(method)) {
            throw new HttpError(405, "Method " + request.method() + " not allowed, use " + method);
        }
    }

    /**
     * Returns the error of an unknown endpoint.
     *
     * @param request current request
     * @return the error to throw
     */
    protected static HttpError notFound(Request request) {
        return new HttpError(404, "Unknown endpoint '" + String.join("/", request.segments())
                + "'");
    }

    private static Map<String, Object> error(Failure failure) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", failure.message() == null ? "Unknown error" : failure.message());
        body.put("kind", failure.kind());
        return body;
    }
}
