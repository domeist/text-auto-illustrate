package autoillustrate.web;

import autoillustrate.index.CorpusIndexer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Starts the server on an ephemeral port and drives it over HTTP. */
class WebServerTest {

    @TempDir
    static Path workspace;

    private static WebServer server;
    private static HttpClient client;
    private static String base;

    private static final String CORPUS = String.join("\n",
            "1\thttps://en.wikipedia.org/wiki/Frieze\thttps://example.org/frieze.jpg\t"
                    + "Carved marble frieze with acanthus ornament on an entablature",
            "2\thttps://en.wikipedia.org/wiki/Parthenon\thttps://example.org/p.jpg\t"
                    + "The Parthenon frieze depicting a procession of horsemen",
            "3\thttps://en.wikipedia.org/wiki/Trout\thttps://example.org/t.jpg\t"
                    + "A brown trout swimming in a shallow river",
            // A caption carrying characters that must survive JSON encoding.
            "4\thttps://en.wikipedia.org/wiki/Cornice\thttps://example.org/c.jpg\t"
                    + "Stone cornice: \"architrave\" & <column>, 5\\8 scale") + "\n";

    @BeforeAll
    static void startServer() throws IOException {
        Path corpusDir = Files.createDirectories(workspace.resolve("corpus"));
        Files.writeString(corpusDir.resolve("corpus.tsv"), CORPUS);
        Path indexDir = workspace.resolve("index");
        new CorpusIndexer(indexDir).indexDirectory(corpusDir);

        server = new WebServer(indexDir, "127.0.0.1", 0);
        server.start();
        base = "http://127.0.0.1:" + server.port();
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @AfterAll
    static void stopServer() throws IOException {
        if (server != null) {
            server.close();
        }
    }

    private static HttpResponse<String> get(String path) throws Exception {
        return client.send(
                HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(10)).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static HttpResponse<String> search(String passage, String extra) throws Exception {
        return get("/search?q=" + URLEncoder.encode(passage, StandardCharsets.UTF_8) + extra);
    }

    @Test
    @DisplayName("the page is served at the root")
    void servesThePage() throws Exception {
        HttpResponse<String> response = get("/");
        assertAll(
                () -> assertEquals(200, response.statusCode()),
                () -> assertTrue(response.headers().firstValue("content-type")
                        .orElse("").startsWith("text/html")),
                () -> assertTrue(response.body().contains("Text Auto Illustrate")),
                () -> assertTrue(response.body().contains("/search?q="),
                        "the page should call the search endpoint"));
    }

    @Test
    void unknownPathsReturn404() throws Exception {
        assertEquals(404, get("/nope").statusCode());
    }

    @Test
    @DisplayName("a passage returns ranked results and the terms it searched on")
    void searchReturnsResults() throws Exception {
        HttpResponse<String> response = search(
                "The frieze sits on the entablature above the column and cornice.", "");

        assertEquals(200, response.statusCode());
        String body = response.body();
        assertAll(
                () -> assertTrue(body.contains("\"queryTerms\""), body),
                () -> assertTrue(body.contains("\"results\""), body),
                () -> assertTrue(body.contains("\"rank\":1"), body),
                () -> assertTrue(body.contains("friez"), "expected the stemmed term: " + body),
                () -> assertTrue(body.contains("example.org"), body),
                () -> assertFalse(body.contains("trout"), "the trout should not match: " + body));
    }

    @Test
    @DisplayName("captions with quotes, backslashes and angle brackets stay valid JSON")
    void escapesAwkwardCaptions() throws Exception {
        HttpResponse<String> response = search("A stone cornice and architrave.", "");

        assertEquals(200, response.statusCode());
        String body = response.body();
        assertAll(
                () -> assertTrue(body.contains("\\\"architrave\\\""),
                        "quotes should be escaped: " + body),
                () -> assertTrue(body.contains("5\\\\8"), "backslash should be escaped: " + body),
                () -> assertTrue(body.contains("\\u003ccolumn>"),
                        "< should be escaped: " + body),
                () -> assertFalse(body.contains("<column>"), body));
    }

    @Test
    void anEmptyPassageIsRejected() throws Exception {
        HttpResponse<String> response = get("/search?q=%20%20");
        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("\"error\""), response.body());
    }

    @Test
    void aMissingQueryIsRejected() throws Exception {
        assertEquals(400, get("/search").statusCode());
    }

    @Test
    @DisplayName("limit caps the number of results and survives nonsense")
    void limitIsClamped() throws Exception {
        String passage = "frieze cornice entablature column trout river";
        assertEquals(1, countRanks(search(passage, "&limit=1").body()));
        // Not a number, and wildly out of range: both fall back to something sane.
        assertTrue(countRanks(search(passage, "&limit=banana").body()) >= 1);
        assertTrue(countRanks(search(passage, "&limit=99999").body()) <= 60);
    }

    @Test
    @DisplayName("text with no indexable terms returns an empty list, not an error")
    void unmatchableTextReturnsEmptyResults() throws Exception {
        HttpResponse<String> response = search("the and of it", "");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"results\":[]"), response.body());
    }

    private static int countRanks(String body) {
        int count = 0;
        int from = 0;
        while ((from = body.indexOf("\"rank\":", from)) != -1) {
            count++;
            from += 7;
        }
        return count;
    }
}
