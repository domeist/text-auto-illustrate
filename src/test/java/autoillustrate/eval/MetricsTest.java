package autoillustrate.eval;

import autoillustrate.retrieve.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetricsTest {

    private static final double TOLERANCE = 1e-9;

    private static List<Result> resultsWithIds(String... ids) {
        List<Result> results = new ArrayList<>();
        for (int i = 0; i < ids.length; i++) {
            results.add(new Result(ids[i], "image" + i, "page" + i, "caption " + i, 1.0 - i * 0.01));
        }
        return results;
    }

    private static Map<String, Integer> relevant(String... ids) {
        Map<String, Integer> judgments = new LinkedHashMap<>();
        for (String id : ids) {
            judgments.put(id, 1);
        }
        return judgments;
    }

    @Nested
    @DisplayName("precision@k")
    class PrecisionAtK {

        @Test
        void countsRelevantResultsInTheTopK() {
            List<Result> results = resultsWithIds("a", "x", "b", "y", "c");
            assertEquals(0.6, Metrics.precisionAtK(results, relevant("a", "b", "c"), 5), TOLERANCE);
        }

        @Test
        @DisplayName("divides by k even when fewer results were returned")
        void shortResultListStillDividesByK() {
            List<Result> results = resultsWithIds("a", "b");
            assertEquals(0.4, Metrics.precisionAtK(results, relevant("a", "b"), 5), TOLERANCE);
        }

        @Test
        @DisplayName("does not overrun a result list shorter than k")
        void doesNotThrowWhenResultsAreShorterThanK() {
            List<Result> results = resultsWithIds("a");
            assertEquals(0.2, Metrics.precisionAtK(results, relevant("a"), 5), TOLERANCE);
        }

        @Test
        void emptyResultsScoreZero() {
            assertEquals(0.0, Metrics.precisionAtK(List.of(), relevant("a"), 5), TOLERANCE);
        }

        @Test
        void rejectsNonPositiveK() {
            assertThrows(IllegalArgumentException.class,
                    () -> Metrics.precisionAtK(resultsWithIds("a"), relevant("a"), 0));
        }
    }

    @Nested
    @DisplayName("recall@k")
    class RecallAtK {

        @Test
        void measuresHowManyRelevantDocumentsWereFound() {
            List<Result> results = resultsWithIds("a", "x", "b");
            assertEquals(0.5, Metrics.recallAtK(results, relevant("a", "b", "c", "d"), 10), TOLERANCE);
        }

        @Test
        void emptyJudgmentsScoreZeroRatherThanDivideByZero() {
            assertEquals(0.0, Metrics.recallAtK(resultsWithIds("a"), Map.of(), 10), TOLERANCE);
        }

        @Test
        @DisplayName("ceiling reflects that k results cannot cover more than k relevant documents")
        void ceilingIsBelowOneWhenRelevantSetExceedsDepth() {
            Map<String, Integer> judgments = new LinkedHashMap<>();
            for (int i = 0; i < 1000; i++) {
                judgments.put("doc" + i, 1);
            }
            assertEquals(0.1, Metrics.recallCeilingAtK(judgments, 100), TOLERANCE);
        }

        @Test
        void ceilingIsOneWhenDepthCoversEverythingRelevant() {
            assertEquals(1.0, Metrics.recallCeilingAtK(relevant("a", "b"), 100), TOLERANCE);
        }
    }

    @Nested
    @DisplayName("reciprocal rank")
    class ReciprocalRank {

        @Test
        void isOneWhenTheFirstResultIsRelevant() {
            assertEquals(1.0,
                    Metrics.reciprocalRank(resultsWithIds("a", "x"), relevant("a")), TOLERANCE);
        }

        @Test
        void isOneOverTheRankOfTheFirstHit() {
            assertEquals(0.25,
                    Metrics.reciprocalRank(resultsWithIds("w", "x", "y", "a"), relevant("a")),
                    TOLERANCE);
        }

        @Test
        void isZeroWhenNothingRelevantWasFound() {
            assertEquals(0.0,
                    Metrics.reciprocalRank(resultsWithIds("x", "y"), relevant("a")), TOLERANCE);
        }
    }

    @Nested
    @DisplayName("average precision@k")
    class AveragePrecisionAtK {

        @Test
        @DisplayName("is 1 when every relevant document is ranked first")
        void perfectRankingScoresOne() {
            List<Result> results = resultsWithIds("a", "b", "x", "y");
            assertEquals(1.0,
                    Metrics.averagePrecisionAtK(results, relevant("a", "b"), 10), TOLERANCE);
        }

        @Test
        @DisplayName("divides by relevant documents, not by how many were found")
        void findingOneOfManyDoesNotScoreOne() {
            List<Result> results = resultsWithIds("a", "x", "y", "z");
            double score = Metrics.averagePrecisionAtK(results, relevant("a", "b", "c", "d"), 10);
            assertEquals(0.25, score, TOLERANCE);
        }

        @Test
        void rewardsRelevantDocumentsAppearingEarlier() {
            Map<String, Integer> judgments = relevant("a", "b");
            double early = Metrics.averagePrecisionAtK(resultsWithIds("a", "b", "x"), judgments, 10);
            double late = Metrics.averagePrecisionAtK(resultsWithIds("x", "a", "b"), judgments, 10);
            assertTrue(early > late, "earlier hits should score higher");
        }

        @Test
        void emptyJudgmentsScoreZero() {
            assertEquals(0.0,
                    Metrics.averagePrecisionAtK(resultsWithIds("a"), Map.of(), 10), TOLERANCE);
        }
    }

    @Nested
    @DisplayName("nDCG@k")
    class NdcgAtK {

        private Map<String, Integer> graded() {
            Map<String, Integer> judgments = new LinkedHashMap<>();
            judgments.put("best", 3);
            judgments.put("good", 2);
            judgments.put("ok", 1);
            return judgments;
        }

        @Test
        void idealOrderingScoresOne() {
            List<Result> results = resultsWithIds("best", "good", "ok");
            assertEquals(1.0, Metrics.ndcgAtK(results, graded(), 10), TOLERANCE);
        }

        @Test
        void reversedOrderingScoresLessThanIdeal() {
            List<Result> reversed = resultsWithIds("ok", "good", "best");
            assertTrue(Metrics.ndcgAtK(reversed, graded(), 10) < 1.0);
        }

        @Test
        @DisplayName("handles more results than judged documents without overrunning")
        void moreResultsThanJudgmentsDoesNotThrow() {
            List<Result> results = resultsWithIds("best", "x", "y", "z", "w", "v");
            double score = Metrics.ndcgAtK(results, graded(), 100);
            assertTrue(score > 0.0 && score < 1.0, "expected a partial score, got " + score);
        }

        @Test
        void nothingRelevantScoresZero() {
            assertEquals(0.0, Metrics.ndcgAtK(resultsWithIds("x", "y"), graded(), 10), TOLERANCE);
        }

        @Test
        void noJudgmentsScoreZeroRatherThanDivideByZero() {
            assertEquals(0.0, Metrics.ndcgAtK(resultsWithIds("a"), Map.of(), 10), TOLERANCE);
        }
    }
}
