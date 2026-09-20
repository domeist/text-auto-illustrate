package autoillustrate.eval;

import autoillustrate.retrieve.Bm25Retriever;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Scores the retriever across a grid of BM25 settings and writes the results
 * as CSV for plotting.
 *
 * <p>This is the experiment, not the application: it exists to justify the
 * parameter choices the application ships with. The default grid spans k1 up to
 * 2.0 deliberately — the original study stopped at 1.0 and so never tested
 * Lucene's own default of 1.2, which turned out to beat everything it did test.
 */
public final class ParameterSweep {

    private static final float[] K1_VALUES = {0.2f, 0.6f, 1.0f, 1.2f, 1.6f, 2.0f};
    private static final float[] B_VALUES = {0.0f, 0.2f, 0.4f, 0.6f, 0.75f, 1.0f};
    private static final int[] TERM_COUNTS = {1, 3, 5, 10, 15, 20, 25};
    private static final float[] TITLE_BOOSTS = {0f, 0.5f, 1f, 2f, 3f, 5f};

    private ParameterSweep() {
    }

    /** Sweeps k1 and b at a fixed query length, writing one CSV row per combination. */
    public static void sweepBm25(Path indexDir, EvaluationSet set, int queryTerms, Path output)
            throws Exception {
        List<String> rows = new ArrayList<>();
        rows.add("k1,b,p_at_5,recall,mrr,map");
        for (float k1 : K1_VALUES) {
            for (float b : B_VALUES) {
                Evaluation.Report report = score(indexDir, set, queryTerms, k1, b);
                rows.add("%.2f,%.2f,%.6f,%.6f,%.6f,%.6f".formatted(k1, b,
                        report.precisionAt5(), report.recallAtDepth(),
                        report.meanReciprocalRank(), report.meanAveragePrecision()));
                System.out.printf("  k1=%.2f b=%.2f  p@5=%.4f  MRR=%.4f%n",
                        k1, b, report.precisionAt5(), report.meanReciprocalRank());
            }
        }
        write(output, rows);
    }

    /** Sweeps the number of query terms at fixed BM25 parameters. */
    public static void sweepQueryLength(Path indexDir, EvaluationSet set, Path output)
            throws Exception {
        List<String> rows = new ArrayList<>();
        rows.add("query_terms,p_at_5,recall,mrr,map");
        for (int terms : TERM_COUNTS) {
            Evaluation.Report report = score(indexDir, set, terms,
                    Bm25Retriever.DEFAULT_K1, Bm25Retriever.DEFAULT_B);
            rows.add("%d,%.6f,%.6f,%.6f,%.6f".formatted(terms,
                    report.precisionAt5(), report.recallAtDepth(),
                    report.meanReciprocalRank(), report.meanAveragePrecision()));
            System.out.printf("  terms=%d  p@5=%.4f  MRR=%.4f%n",
                    terms, report.precisionAt5(), report.meanReciprocalRank());
        }
        write(output, rows);
    }

    /**
     * Sweeps the weight given to article-title matches, from 0 (captions only,
     * as the 2022 version worked) upwards.
     */
    public static void sweepTitleBoost(Path indexDir, EvaluationSet set, int queryTerms,
                                       Path output) throws Exception {
        List<String> rows = new ArrayList<>();
        rows.add("title_boost,p_at_5,recall,mrr,map");
        for (float boost : TITLE_BOOSTS) {
            try (Bm25Retriever retriever = Bm25Retriever.withTitleBoost(indexDir, queryTerms,
                    Bm25Retriever.DEFAULT_K1, Bm25Retriever.DEFAULT_B, boost)) {
                Evaluation.Report report = Evaluation.run(retriever, set);
                rows.add("%.2f,%.6f,%.6f,%.6f,%.6f".formatted(boost,
                        report.precisionAt5(), report.recallAtDepth(),
                        report.meanReciprocalRank(), report.meanAveragePrecision()));
                System.out.printf("  title boost=%.1f  p@5=%.4f  recall=%.4f  MRR=%.4f%n",
                        boost, report.precisionAt5(), report.recallAtDepth(),
                        report.meanReciprocalRank());
            }
        }
        write(output, rows);
    }

    private static Evaluation.Report score(Path indexDir, EvaluationSet set, int queryTerms,
                                           float k1, float b) throws Exception {
        try (Bm25Retriever retriever = Bm25Retriever.withParameters(indexDir, queryTerms, k1, b)) {
            return Evaluation.run(retriever, set);
        }
    }

    /**
     * Writes the grid, replacing any previous run. The original implementation
     * appended, so repeated runs silently stacked new results onto old ones.
     */
    private static void write(Path output, List<String> rows) throws IOException {
        Path parent = output.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(output))) {
            rows.forEach(writer::println);
        }
        System.out.println("wrote " + output);
    }
}
