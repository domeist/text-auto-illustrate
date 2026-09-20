package autoillustrate.eval;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A set of passages paired with the images judged to suit them.
 *
 * <p>These judgements were written by hand for this project and are what make
 * the system measurable at all. They are used only to score results after the
 * fact — nothing in the retriever ever reads them.
 *
 * <p>Two file layouts are supported, both tab-separated:
 * <ul>
 *   <li><b>Binary</b> — {@code id, passage, relevantIds} — every listed image
 *       is equally relevant.</li>
 *   <li><b>Graded</b> — {@code id, passage, grade3Ids, grade2Ids, grade1Ids} —
 *       images are judged at three levels of relevance.</li>
 * </ul>
 * The layout is detected from the column count.
 */
public record EvaluationSet(String name, List<Topic> topics) {

    private static final int BINARY_COLUMNS = 3;
    private static final int GRADED_COLUMNS = 5;

    /**
     * One passage and its judgements.
     *
     * @param id         identifier used in reports
     * @param text       the passage to be illustrated
     * @param judgments  image id to relevance grade; higher is more relevant
     */
    public record Topic(String id, String text, Map<String, Integer> judgments) {
    }

    /** Reads an evaluation set, detecting its layout from the column count. */
    public static EvaluationSet load(Path file) throws IOException {
        List<Topic> topics = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                String[] columns = line.split("\t", -1);
                topics.add(switch (columns.length) {
                    case BINARY_COLUMNS -> binaryTopic(columns);
                    case GRADED_COLUMNS -> gradedTopic(columns);
                    default -> throw new IOException(
                            "%s line %d: expected %d or %d columns, found %d"
                                    .formatted(file.getFileName(), lineNumber,
                                            BINARY_COLUMNS, GRADED_COLUMNS, columns.length));
                });
            }
        }
        if (topics.isEmpty()) {
            throw new IOException("no topics found in " + file);
        }
        return new EvaluationSet(file.getFileName().toString(), topics);
    }

    private static Topic binaryTopic(String[] columns) {
        Map<String, Integer> judgments = new LinkedHashMap<>();
        addIds(judgments, columns[2], 1);
        return new Topic(columns[0], columns[1], judgments);
    }

    private static Topic gradedTopic(String[] columns) {
        Map<String, Integer> judgments = new LinkedHashMap<>();
        // Added highest grade first so that a duplicated id keeps its best grade.
        addIds(judgments, columns[2], 3);
        addIds(judgments, columns[3], 2);
        addIds(judgments, columns[4], 1);
        return new Topic(columns[0], columns[1], judgments);
    }

    /**
     * Records each id at the given grade. Ids repeated within or across grades
     * are collapsed, keeping the highest grade seen — the source files contain
     * duplicates, which would otherwise inflate the relevant-document count and
     * quietly depress recall.
     */
    private static void addIds(Map<String, Integer> judgments, String csv, int grade) {
        if (csv.isBlank()) {
            return;
        }
        for (String id : csv.split(",")) {
            String trimmed = id.strip();
            if (!trimmed.isEmpty()) {
                judgments.merge(trimmed, grade, Math::max);
            }
        }
    }

    /** True if any topic carries a grade above 1, meaning nDCG is meaningful. */
    public boolean isGraded() {
        return topics.stream()
                .flatMap(topic -> topic.judgments().values().stream())
                .anyMatch(grade -> grade > 1);
    }
}
