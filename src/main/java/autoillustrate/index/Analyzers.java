package autoillustrate.index;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the single analyzer used for both indexing and querying.
 *
 * <p>Indexing and searching must agree on how text is broken into terms, so
 * everything in the project goes through {@link #english()} rather than
 * constructing analyzers ad hoc.
 */
public final class Analyzers {

    private static final String STOPWORDS_RESOURCE = "/stopwords.txt";

    private Analyzers() {
    }

    /** English analyzer: lowercases, strips stopwords, and stems. */
    public static Analyzer english() {
        return new EnglishAnalyzer(loadStopwords());
    }

    /**
     * Runs text through an analyzer and returns the resulting terms joined by
     * spaces — the same terms that were written to the index.
     */
    public static List<String> terms(Analyzer analyzer, String text) {
        List<String> terms = new ArrayList<>();
        try (TokenStream stream = analyzer.tokenStream("caption", text)) {
            CharTermAttribute term = stream.addAttribute(CharTermAttribute.class);
            stream.reset();
            while (stream.incrementToken()) {
                terms.add(term.toString());
            }
            stream.end();
        } catch (IOException e) {
            throw new UncheckedIOException("failed to analyze text", e);
        }
        return terms;
    }

    private static CharArraySet loadStopwords() {
        try (InputStream in = Analyzers.class.getResourceAsStream(STOPWORDS_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("missing resource " + STOPWORDS_RESOURCE);
            }
            List<String> words = new ArrayList<>();
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                String word = line.strip();
                if (!word.isEmpty()) {
                    words.add(word);
                }
            }
            return new CharArraySet(words, true);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read stopwords", e);
        }
    }
}
