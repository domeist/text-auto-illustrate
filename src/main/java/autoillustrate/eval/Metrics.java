package autoillustrate.eval;

import autoillustrate.retrieve.Result;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Standard information-retrieval measures, computed over a ranked result list
 * and a set of relevance judgements.
 *
 * <p>Judgements map a document id to a relevance grade, where zero (or absent)
 * means not relevant. The binary measures treat any positive grade as relevant;
 * only {@link #ndcgAtK} distinguishes between grades.
 *
 * <p>All measures are cut off at a rank {@code k}, because a retriever only
 * ever returns a fixed number of results. Reporting uncapped recall against a
 * judgement set larger than {@code k} produces a number bounded well below 1
 * and invites misreading, so recall here is explicitly recall@k.
 */
public final class Metrics {

    private Metrics() {
    }

    /** Fraction of the top {@code k} results that are relevant. */
    public static double precisionAtK(List<Result> results, Map<String, Integer> judgments, int k) {
        requirePositive(k);
        int hits = 0;
        int limit = Math.min(k, results.size());
        for (int i = 0; i < limit; i++) {
            if (isRelevant(results.get(i), judgments)) {
                hits++;
            }
        }
        return (double) hits / k;
    }

    /**
     * Fraction of relevant documents found within the top {@code k}.
     *
     * <p>When there are more relevant documents than {@code k}, this cannot
     * reach 1. {@link #recallCeilingAtK} gives the highest attainable value so
     * the score can be read in context.
     */
    public static double recallAtK(List<Result> results, Map<String, Integer> judgments, int k) {
        requirePositive(k);
        long relevantTotal = relevantCount(judgments);
        if (relevantTotal == 0) {
            return 0.0;
        }
        int hits = 0;
        int limit = Math.min(k, results.size());
        for (int i = 0; i < limit; i++) {
            if (isRelevant(results.get(i), judgments)) {
                hits++;
            }
        }
        return (double) hits / relevantTotal;
    }

    /** The largest recall@k attainable given the number of relevant documents. */
    public static double recallCeilingAtK(Map<String, Integer> judgments, int k) {
        requirePositive(k);
        long relevantTotal = relevantCount(judgments);
        if (relevantTotal == 0) {
            return 0.0;
        }
        return Math.min(k, relevantTotal) / (double) relevantTotal;
    }

    /** Reciprocal of the rank of the first relevant result, or zero if none. */
    public static double reciprocalRank(List<Result> results, Map<String, Integer> judgments) {
        for (int i = 0; i < results.size(); i++) {
            if (isRelevant(results.get(i), judgments)) {
                return 1.0 / (i + 1);
            }
        }
        return 0.0;
    }

    /**
     * Average precision over the top {@code k}.
     *
     * <p>The sum of precisions at each relevant rank, divided by the number of
     * relevant documents reachable within {@code k}. Dividing by the number
     * actually retrieved instead — as an earlier version of this project did —
     * rewards finding one relevant document and stopping, and is not comparable
     * with published figures.
     */
    public static double averagePrecisionAtK(List<Result> results,
                                             Map<String, Integer> judgments, int k) {
        requirePositive(k);
        long reachable = Math.min(k, relevantCount(judgments));
        if (reachable == 0) {
            return 0.0;
        }
        int hits = 0;
        double sum = 0.0;
        int limit = Math.min(k, results.size());
        for (int i = 0; i < limit; i++) {
            if (isRelevant(results.get(i), judgments)) {
                hits++;
                sum += (double) hits / (i + 1);
            }
        }
        return sum / reachable;
    }

    /**
     * Normalised discounted cumulative gain over the top {@code k}, using the
     * relevance grades rather than treating all judgements alike.
     *
     * <p>Gain is discounted by {@code log2(rank + 1)} and normalised against the
     * best ordering achievable from the available grades.
     */
    public static double ndcgAtK(List<Result> results, Map<String, Integer> judgments, int k) {
        requirePositive(k);
        double ideal = idealDcgAtK(judgments, k);
        if (ideal == 0.0) {
            return 0.0;
        }
        return dcgAtK(results, judgments, k) / ideal;
    }

    static double dcgAtK(List<Result> results, Map<String, Integer> judgments, int k) {
        double dcg = 0.0;
        int limit = Math.min(k, results.size());
        for (int i = 0; i < limit; i++) {
            int grade = grade(results.get(i), judgments);
            if (grade > 0) {
                dcg += grade / log2(i + 2);
            }
        }
        return dcg;
    }

    static double idealDcgAtK(Map<String, Integer> judgments, int k) {
        List<Integer> grades = new ArrayList<>();
        for (int grade : judgments.values()) {
            if (grade > 0) {
                grades.add(grade);
            }
        }
        grades.sort(Comparator.reverseOrder());
        double idcg = 0.0;
        int limit = Math.min(k, grades.size());
        for (int i = 0; i < limit; i++) {
            idcg += grades.get(i) / log2(i + 2);
        }
        return idcg;
    }

    private static boolean isRelevant(Result result, Map<String, Integer> judgments) {
        return grade(result, judgments) > 0;
    }

    private static int grade(Result result, Map<String, Integer> judgments) {
        return judgments.getOrDefault(result.id(), 0);
    }

    private static long relevantCount(Map<String, Integer> judgments) {
        return judgments.values().stream().filter(grade -> grade > 0).count();
    }

    private static double log2(int value) {
        return Math.log(value) / Math.log(2);
    }

    private static void requirePositive(int k) {
        if (k < 1) {
            throw new IllegalArgumentException("k must be at least 1, got " + k);
        }
    }
}
