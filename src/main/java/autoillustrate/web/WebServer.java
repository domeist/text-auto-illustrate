package autoillustrate.web;

import autoillustrate.retrieve.Bm25Retriever;
import autoillustrate.retrieve.Result;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Serves a page where a passage can be pasted and the suggested images looked at.
 *
 * <p>Everything here is a thin shell over {@link Bm25Retriever}: the retrieval
 * is already done, and this only makes it visible. The JDK's own HTTP server is
 * used so the project gains no dependency for what is one page and one endpoint.
 *
 * <p>The retriever is opened once and shared across requests. Opening a Lucene
 * index costs far more than a query does, so per-request construction would make
 * every search feel slow.
 */
public final class WebServer implements AutoCloseable {

    /** Bound to the loopback address by default: this serves a local index. */
    public static final String DEFAULT_HOST = "127.0.0.1";
    public static final int DEFAULT_PORT = 8080;
    private static final int MAX_LIMIT = 60;
    private static final int DEFAULT_LIMIT = 24;
    private static final int MAX_PASSAGE_CHARS = 20_000;

    private final HttpServer server;
    private final Bm25Retriever retriever;
    private final byte[] page;

    public WebServer(Path indexDir, String host, int port) throws IOException {
        this.retriever = new Bm25Retriever(indexDir);
        this.page = readPage();
        this.server = HttpServer.create(new InetSocketAddress(host, port), 0);
        this.server.createContext("/", this::handlePage);
        this.server.createContext("/search", this::handleSearch);
        this.server.setExecutor(Executors.newFixedThreadPool(4));
    }

    public void start() {
        server.start();
    }

    /** The port actually bound, which differs from the requested one when 0 was asked for. */
    public int port() {
        return server.getAddress().getPort();
    }

    @Override
    public void close() throws IOException {
        server.stop(0);
        retriever.close();
    }

    private void handlePage(HttpExchange exchange) throws IOException {
        if (!"/".equals(exchange.getRequestURI().getPath())) {
            respond(exchange, 404, "text/plain; charset=utf-8",
                    "Not found".getBytes(StandardCharsets.UTF_8));
            return;
        }
        respond(exchange, 200, "text/html; charset=utf-8", page);
    }

    private void handleSearch(HttpExchange exchange) throws IOException {
        Map<String, String> params = queryParameters(exchange.getRequestURI().getRawQuery());
        String text = params.getOrDefault("q", "").strip();

        if (text.isEmpty()) {
            respondJson(exchange, 400, Json.error("Paste a passage of text to illustrate."));
            return;
        }
        if (text.length() > MAX_PASSAGE_CHARS) {
            respondJson(exchange, 400, Json.error(
                    "That passage is longer than %,d characters.".formatted(MAX_PASSAGE_CHARS)));
            return;
        }

        int limit = clamp(parseInt(params.get("limit"), DEFAULT_LIMIT));
        try {
            List<Result> results = retriever.retrieve(text, limit);
            List<String> terms = retriever.queryTermsFor(text);
            respondJson(exchange, 200, Json.searchResponse(terms, results));
        } catch (Exception e) {
            respondJson(exchange, 500, Json.error("Search failed: " + e.getMessage()));
        }
    }

    private static int clamp(int limit) {
        return Math.max(1, Math.min(MAX_LIMIT, limit));
    }

    private static int parseInt(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.strip());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Parses a raw query string. Values are percent-decoded; malformed pairs are skipped. */
    private static Map<String, String> queryParameters(String rawQuery) {
        if (rawQuery == null || rawQuery.isEmpty()) {
            return Map.of();
        }
        Map<String, String> params = new java.util.LinkedHashMap<>();
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            try {
                params.put(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                        URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
            } catch (IllegalArgumentException e) {
                // A malformed escape sequence; that parameter is simply ignored.
            }
        }
        return params;
    }

    private static void respondJson(HttpExchange exchange, int status, String body)
            throws IOException {
        respond(exchange, status, "application/json; charset=utf-8",
                body.getBytes(StandardCharsets.UTF_8));
    }

    private static void respond(HttpExchange exchange, int status, String contentType, byte[] body)
            throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private static byte[] readPage() throws IOException {
        try (InputStream in = WebServer.class.getResourceAsStream("/web/index.html")) {
            if (in == null) {
                throw new IOException("missing resource /web/index.html");
            }
            return in.readAllBytes();
        }
    }
}
