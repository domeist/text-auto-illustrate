package autoillustrate.query;

import autoillustrate.index.Analyzers;
import autoillustrate.index.CorpusIndexer;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;

import java.io.IOException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reduces a passage of prose to the handful of words that best represent it.
 *
 * <p>A paragraph is far too long to use as a search query, so each term is
 * scored by term frequency times inverse document frequency: how often the word
 * appears in this passage, weighted by how rare it is across the whole corpus.
 * Rare words carry more signal — "entablature" identifies a subject in a way
 * that "building" does not — so the top-scoring terms become the query.
 */
public final class QueryExtractor {

    private final IndexReader reader;
    private final Analyzer analyzer;

    public QueryExtractor(IndexReader reader) {
        this.reader = reader;
        this.analyzer = Analyzers.english();
    }

    /**
     * Returns the {@code limit} most distinctive terms in {@code text}, best
     * first. Fewer terms are returned if the text contains fewer distinct ones.
     */
    public List<String> extract(String text, int limit) throws IOException {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be at least 1, got " + limit);
        }
        Map<String, Double> scores = score(text);
        return scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(limit)
                .map(Map.Entry::getKey)
                .toList();
    }

    /**
     * Returns the number of terms making up {@code percent} of the passage,
     * never fewer than one. Used to scale query length with passage length.
     */
    public int termCountForPercentage(String text, int percent) {
        int total = Analyzers.terms(analyzer, text).size();
        return Math.max(1, total * percent / 100);
    }

    /** Scores every distinct term in the text by tf-idf. */
    Map<String, Double> score(String text) throws IOException {
        Map<String, Integer> frequencies = new LinkedHashMap<>();
        for (String term : Analyzers.terms(analyzer, text)) {
            frequencies.merge(term, 1, Integer::sum);
        }
        int corpusSize = reader.numDocs();
        Map<String, Double> scores = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : frequencies.entrySet()) {
            int documentFrequency = reader.docFreq(
                    new Term(CorpusIndexer.FIELD_CAPTION, entry.getKey()));
            scores.put(entry.getKey(), entry.getValue() * idf(corpusSize, documentFrequency));
        }
        return scores;
    }

    /**
     * Inverse document frequency. A term absent from the corpus scores zero
     * rather than infinity: it cannot match anything, so it is worthless as a
     * query term regardless of how rare it is.
     */
    private static double idf(int corpusSize, int documentFrequency) {
        if (documentFrequency == 0) {
            return 0.0;
        }
        return Math.log((double) corpusSize / documentFrequency);
    }

    static Comparator<Map.Entry<String, Double>> byScoreDescending() {
        return Map.Entry.<String, Double>comparingByValue().reversed();
    }
}
