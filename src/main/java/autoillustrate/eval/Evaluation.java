package autoillustrate.eval;

import autoillustrate.retrieve.Result;
import autoillustrate.retrieve.Retriever;

import java.util.List;

/**
 * Runs a retriever over an evaluation set and averages the results.
 *
 * <p>The retriever is handed only the passage text. Judgements are applied
 * afterwards, by {@link Metrics}, to score what came back.
 */
public final class Evaluation {

    /** How many results to retrieve per passage, and the cutoff for the measures. */
    public static final int DEFAULT_DEPTH = 100;
    /** Cutoff for the headline precision figure. */
    public static final int PRECISION_CUTOFF = 5;

    private Evaluation() {
    }

    /**
     * Averaged scores across an evaluation set.
     *
     * @param setName       the evaluation set these scores came from
     * @param topics        number of passages scored
     * @param depth         result-list depth the measures were cut off at
     * @param precisionAt5  mean precision within the top five results
     * @param recallAtDepth mean recall within {@code depth} results
     * @param recallCeiling highest mean recall attainable at this depth
     * @param meanReciprocalRank mean reciprocal rank of the first relevant result
     * @param meanAveragePrecision mean average precision at {@code depth}
     * @param ndcg          mean normalised discounted cumulative gain, or -1 when ungraded
     * @param topicsWithNoHits passages where nothing relevant surfaced in the top five
     */
    public record Report(String setName, int topics, int depth, double precisionAt5,
                         double recallAtDepth, double recallCeiling,
                         double meanReciprocalRank, double meanAveragePrecision,
                         double ndcg, int topicsWithNoHits) {

        public String format() {
            StringBuilder out = new StringBuilder();
            out.append("%s — %d passages, top %d%n".formatted(setName, topics, depth));
            out.append("  p@%d   %.4f%n".formatted(PRECISION_CUTOFF, precisionAt5));
            out.append("  recall %.4f  (ceiling %.4f at this depth)%n"
                    .formatted(recallAtDepth, recallCeiling));
            out.append("  MRR    %.4f%n".formatted(meanReciprocalRank));
            out.append("  MAP    %.4f%n".formatted(meanAveragePrecision));
            if (ndcg >= 0) {
                out.append("  nDCG   %.4f%n".formatted(ndcg));
            }
            out.append("  passages with nothing relevant in the top %d: %d of %d%n"
                    .formatted(PRECISION_CUTOFF, topicsWithNoHits, topics));
            return out.toString();
        }
    }

    /** Scores {@code retriever} against {@code set} at the default depth. */
    public static Report run(Retriever retriever, EvaluationSet set) throws Exception {
        return run(retriever, set, DEFAULT_DEPTH, false);
    }

    /**
     * Scores {@code retriever} against {@code set}.
     *
     * @param depth   how many results to retrieve and score per passage
     * @param verbose print the query terms and top hits for each passage
     */
    public static Report run(Retriever retriever, EvaluationSet set, int depth, boolean verbose)
            throws Exception {
        boolean graded = set.isGraded();
        double precision = 0;
        double recall = 0;
        double ceiling = 0;
        double reciprocalRank = 0;
        double averagePrecision = 0;
        double ndcg = 0;
        int noHits = 0;

        for (EvaluationSet.Topic topic : set.topics()) {
            List<Result> results = retriever.retrieve(topic.text(), depth);
            double p5 = Metrics.precisionAtK(results, topic.judgments(), PRECISION_CUTOFF);
            precision += p5;
            recall += Metrics.recallAtK(results, topic.judgments(), depth);
            ceiling += Metrics.recallCeilingAtK(topic.judgments(), depth);
            reciprocalRank += Metrics.reciprocalRank(results, topic.judgments());
            averagePrecision += Metrics.averagePrecisionAtK(results, topic.judgments(), depth);
            if (graded) {
                ndcg += Metrics.ndcgAtK(results, topic.judgments(), depth);
            }
            if (p5 == 0.0) {
                noHits++;
            }
            if (verbose) {
                printTopic(topic, results);
            }
        }

        int n = set.topics().size();
        return new Report(set.name(), n, depth,
                precision / n, recall / n, ceiling / n,
                reciprocalRank / n, averagePrecision / n,
                graded ? ndcg / n : -1, noHits);
    }

    private static void printTopic(EvaluationSet.Topic topic, List<Result> results) {
        System.out.printf("%npassage %s — %d judged relevant%n",
                topic.id(), topic.judgments().size());
        int shown = Math.min(PRECISION_CUTOFF, results.size());
        for (int i = 0; i < shown; i++) {
            Result result = results.get(i);
            boolean hit = topic.judgments().getOrDefault(result.id(), 0) > 0;
            System.out.printf("  %d. [%s] %s%n", i + 1, hit ? "relevant" : "        ",
                    truncate(result.caption()));
        }
    }

    private static String truncate(String text) {
        String single = text.replace('\n', ' ').strip();
        return single.length() <= 90 ? single : single.substring(0, 87) + "...";
    }
}
