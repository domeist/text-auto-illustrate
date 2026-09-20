package autoillustrate.retrieve;

import autoillustrate.index.CorpusIndexer;
import autoillustrate.query.QueryExtractor;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.FSDirectory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Retrieves images by matching a passage against image captions with BM25.
 *
 * <p>BM25 is a fixed ranking formula, not a trained model: a caption scores
 * well when it contains the query terms, those terms are rare across the
 * corpus, and the caption is short. Two parameters tune it — see
 * {@link #withParameters}.
 */
public final class Bm25Retriever implements Retriever {

    /** Lucene's default term-frequency saturation; higher means repeats keep counting. */
    public static final float DEFAULT_K1 = 1.2f;
    /** Lucene's default length normalisation; 0 ignores caption length, 1 penalises it fully. */
    public static final float DEFAULT_B = 0.75f;
    /** Query terms extracted from the passage when no other count is given. */
    public static final int DEFAULT_QUERY_TERMS = 10;

    private final DirectoryReader reader;
    private final IndexSearcher searcher;
    private final QueryExtractor extractor;
    private final int queryTerms;

    /**
     * Highest score first, ties broken by document id.
     *
     * <p>Without the tie-break, documents sharing a score come back in whatever
     * order the index segments dictate, which varies between builds of the same
     * corpus and makes MRR and MAP irreproducible.
     */
    private static final Sort SCORE_THEN_ID = new Sort(
            SortField.FIELD_SCORE,
            new SortField(CorpusIndexer.FIELD_ID_SORT, SortField.Type.STRING));

    public Bm25Retriever(Path indexDir) throws IOException {
        this(indexDir, DEFAULT_QUERY_TERMS, DEFAULT_K1, DEFAULT_B);
    }

    public Bm25Retriever(Path indexDir, int queryTerms, float k1, float b) throws IOException {
        if (queryTerms < 1) {
            throw new IllegalArgumentException("queryTerms must be at least 1, got " + queryTerms);
        }
        this.reader = DirectoryReader.open(FSDirectory.open(indexDir));
        this.searcher = new IndexSearcher(reader);
        this.searcher.setSimilarity(new BM25Similarity(k1, b));
        this.extractor = new QueryExtractor(reader);
        this.queryTerms = queryTerms;
    }

    /** Returns a retriever over the same index with different BM25 parameters. */
    public static Bm25Retriever withParameters(Path indexDir, int queryTerms, float k1, float b)
            throws IOException {
        return new Bm25Retriever(indexDir, queryTerms, k1, b);
    }

    @Override
    public List<Result> retrieve(String text, int limit) throws IOException {
        return retrieve(text, limit, queryTerms);
    }

    /** Retrieves using an explicit number of query terms, overriding the default. */
    public List<Result> retrieve(String text, int limit, int terms) throws IOException {
        List<String> queryWords = extractor.extract(text, terms);
        if (queryWords.isEmpty()) {
            return List.of();
        }
        TopDocs topDocs = searcher.search(toQuery(queryWords), limit, SCORE_THEN_ID, true);
        List<Result> results = new ArrayList<>(topDocs.scoreDocs.length);
        for (ScoreDoc hit : topDocs.scoreDocs) {
            Document doc = searcher.storedFields().document(hit.doc);
            results.add(new Result(
                    doc.get(CorpusIndexer.FIELD_ID),
                    doc.get(CorpusIndexer.FIELD_IMAGE_URL),
                    doc.get(CorpusIndexer.FIELD_PAGE_URL),
                    doc.get(CorpusIndexer.FIELD_CAPTION),
                    hit.score));
        }
        return results;
    }

    /**
     * Builds the query directly from analyzed terms rather than parsing a
     * string. The terms already came out of the analyzer, so re-parsing them
     * would risk a stray character being read as query syntax.
     */
    private static Query toQuery(List<String> terms) {
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        for (String term : terms) {
            builder.add(new TermQuery(new Term(CorpusIndexer.FIELD_CAPTION, term)),
                    BooleanClause.Occur.SHOULD);
        }
        return builder.build();
    }

    /** The terms this retriever would search for, exposed for explaining results. */
    public List<String> queryTermsFor(String text) throws IOException {
        return extractor.extract(text, queryTerms);
    }

    public IndexReader reader() {
        return reader;
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }
}
