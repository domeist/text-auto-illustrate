package autoillustrate.retrieve;

import autoillustrate.eval.Evaluation;
import autoillustrate.eval.EvaluationSet;
import autoillustrate.index.CorpusIndexer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Indexes a small corpus and searches it, covering the path from a raw corpus
 * file through query extraction to ranked results.
 */
class RetrievalRoundTripTest {

    @TempDir
    static Path workspace;

    private static Path indexDir;

    private static final String CORPUS = String.join("\n",
            "1\thttps://example.org/frieze\thttps://example.org/frieze.jpg\t"
                    + "Carved marble frieze with acanthus ornament on an entablature",
            "2\thttps://example.org/parthenon\thttps://example.org/parthenon.jpg\t"
                    + "The Parthenon frieze depicting a procession of horsemen",
            "3\thttps://example.org/trout\thttps://example.org/trout.jpg\t"
                    + "A brown trout swimming in a shallow river",
            "4\thttps://example.org/kingfisher\thttps://example.org/kingfisher.jpg\t"
                    + "A kingfisher perched above the riverbank",
            "5\thttps://example.org/cornice\thttps://example.org/cornice.jpg\t"
                    + "Stone cornice and architrave above a column",
            // Caption says nothing about the subject; only the article title does.
            "6\thttps://en.wikipedia.org/wiki/Pediment\thttps://example.org/p.jpg\t"
                    + "Scanned at 300 dpi from the original plate") + "\n";

    @BeforeAll
    static void buildIndex() throws IOException {
        Path corpusDir = Files.createDirectories(workspace.resolve("corpus"));
        Files.writeString(corpusDir.resolve("corpus.tsv"), CORPUS);
        indexDir = workspace.resolve("index");
        long indexed = new CorpusIndexer(indexDir).indexDirectory(corpusDir);
        assertEquals(6, indexed);
    }

    @Test
    @DisplayName("an architectural passage retrieves the architectural images")
    void retrievesTopicallyRelevantImages() throws Exception {
        try (Bm25Retriever retriever = new Bm25Retriever(indexDir)) {
            List<Result> results = retriever.retrieve(
                    "The frieze sits on the entablature, above the column and below the cornice.", 3);

            assertFalse(results.isEmpty(), "expected at least one match");
            List<String> ids = results.stream().map(Result::id).toList();
            assertTrue(ids.contains("1") || ids.contains("5"),
                    "expected an architecture image, got " + ids);
            assertFalse(ids.contains("3"), "the trout should not match, got " + ids);
        }
    }

    @Test
    @DisplayName("query extraction keeps distinctive words and drops stopwords")
    void extractsDistinctiveTerms() throws Exception {
        try (Bm25Retriever retriever = new Bm25Retriever(indexDir)) {
            List<String> terms = retriever.queryTermsFor(
                    "The kingfisher was perched there above the riverbank.");

            assertTrue(terms.contains("kingfish"), "expected the stemmed subject, got " + terms);
            assertFalse(terms.contains("the"), "stopwords should be removed, got " + terms);
        }
    }

    @Test
    @DisplayName("an image is findable by its article title when the caption is uninformative")
    void titleMakesUninformativeCaptionsFindable() throws Exception {
        try (Bm25Retriever retriever = new Bm25Retriever(indexDir)) {
            List<String> ids = retriever.retrieve("A classical pediment.", 5)
                    .stream().map(Result::id).toList();
            assertTrue(ids.contains("6"),
                    "expected the pediment image, whose caption never says 'pediment': " + ids);
        }
    }

    @Test
    @DisplayName("with the title boost off, that image is unreachable")
    void withoutTitleBoostTheSameImageIsMissed() throws Exception {
        try (Bm25Retriever retriever = Bm25Retriever.withTitleBoost(
                indexDir, 10, Bm25Retriever.DEFAULT_K1, Bm25Retriever.DEFAULT_B, 0f)) {
            List<String> ids = retriever.retrieve("A classical pediment.", 5)
                    .stream().map(Result::id).toList();
            assertFalse(ids.contains("6"), "caption-only search should not find it: " + ids);
        }
    }

    @Test
    void limitCapsTheNumberOfResults() throws Exception {
        try (Bm25Retriever retriever = new Bm25Retriever(indexDir)) {
            assertEquals(2, retriever.retrieve("frieze cornice column river trout", 2).size());
        }
    }

    @Test
    @DisplayName("text with no indexable terms returns nothing rather than failing")
    void unmatchableTextReturnsEmpty() throws Exception {
        try (Bm25Retriever retriever = new Bm25Retriever(indexDir)) {
            assertTrue(retriever.retrieve("the and of it", 5).isEmpty());
        }
    }

    @Test
    void readsGzippedCorpusFiles(@TempDir Path dir) throws Exception {
        Path corpusDir = Files.createDirectories(dir.resolve("corpus"));
        try (var out = new GZIPOutputStream(Files.newOutputStream(corpusDir.resolve("c.tsv.gz")))) {
            out.write(CORPUS.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        assertEquals(6, new CorpusIndexer(dir.resolve("index")).indexDirectory(corpusDir));
    }

    @Test
    void skipsRowsWithoutACaption(@TempDir Path dir) throws Exception {
        Path corpusDir = Files.createDirectories(dir.resolve("corpus"));
        Files.writeString(corpusDir.resolve("c.tsv"),
                "1\tpage\timage\tA proper caption\n2\tpage\timage\t\n3\tmalformed row\n");
        assertEquals(1, new CorpusIndexer(dir.resolve("index")).indexDirectory(corpusDir));
    }

    @Test
    void missingCorpusDirectoryFailsClearly(@TempDir Path dir) {
        IOException error = assertThrows(IOException.class,
                () -> new CorpusIndexer(dir.resolve("index")).indexDirectory(dir.resolve("nope")));
        assertTrue(error.getMessage().contains("corpus directory not found"), error.getMessage());
    }

    @Test
    @DisplayName("evaluation scores a perfect retriever at 1.0")
    void evaluationRunsEndToEnd() throws Exception {
        Path setFile = workspace.resolve("set.tsv");
        Files.writeString(setFile,
                "0\tThe frieze sits on the entablature above the column and cornice.\t1,5\n");
        EvaluationSet set = EvaluationSet.load(setFile);

        try (Bm25Retriever retriever = new Bm25Retriever(indexDir)) {
            Evaluation.Report report = Evaluation.run(retriever, set, 10, false);
            assertEquals(1, report.topics());
            assertTrue(report.meanReciprocalRank() > 0.0,
                    "expected a relevant image in the results");
            assertTrue(report.recallCeiling() > 0.0);
        }
    }
}
