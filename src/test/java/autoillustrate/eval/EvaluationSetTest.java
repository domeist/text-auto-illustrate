package autoillustrate.eval;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvaluationSetTest {

    private static Path write(Path dir, String name, String content) throws IOException {
        Path file = dir.resolve(name);
        Files.writeString(file, content);
        return file;
    }

    @Test
    @DisplayName("reads the three-column binary layout")
    void loadsBinarySet(@TempDir Path dir) throws IOException {
        Path file = write(dir, "strict.tsv", "0\tA passage about friezes.\t10,11,12\n");
        EvaluationSet set = EvaluationSet.load(file);

        assertEquals(1, set.topics().size());
        EvaluationSet.Topic topic = set.topics().get(0);
        assertEquals("0", topic.id());
        assertEquals("A passage about friezes.", topic.text());
        assertEquals(3, topic.judgments().size());
        assertEquals(1, topic.judgments().get("10"));
        assertFalse(set.isGraded());
    }

    @Test
    @DisplayName("reads the five-column graded layout")
    void loadsGradedSet(@TempDir Path dir) throws IOException {
        Path file = write(dir, "lenient.tsv", "0\tA passage.\t10\t20,21\t30,31,32\n");
        EvaluationSet set = EvaluationSet.load(file);

        EvaluationSet.Topic topic = set.topics().get(0);
        assertEquals(3, topic.judgments().get("10"));
        assertEquals(2, topic.judgments().get("20"));
        assertEquals(1, topic.judgments().get("30"));
        assertEquals(6, topic.judgments().size());
        assertTrue(set.isGraded());
    }

    @Test
    @DisplayName("collapses ids repeated within a grade, which the source files contain")
    void duplicateIdsAreCountedOnce(@TempDir Path dir) throws IOException {
        Path file = write(dir, "dupes.tsv", "0\tA passage.\t97,15,97\n");
        EvaluationSet set = EvaluationSet.load(file);

        assertEquals(2, set.topics().get(0).judgments().size(),
                "duplicate ids would otherwise inflate the relevant-document count");
    }

    @Test
    @DisplayName("keeps the highest grade when an id appears at several grades")
    void duplicateAcrossGradesKeepsBest(@TempDir Path dir) throws IOException {
        Path file = write(dir, "graded-dupes.tsv", "0\tA passage.\t50\t50\t50\n");
        EvaluationSet set = EvaluationSet.load(file);

        assertEquals(1, set.topics().get(0).judgments().size());
        assertEquals(3, set.topics().get(0).judgments().get("50"));
    }

    @Test
    void toleratesEmptyGradeColumns(@TempDir Path dir) throws IOException {
        Path file = write(dir, "sparse.tsv", "0\tA passage.\t\t\t5\n");
        EvaluationSet set = EvaluationSet.load(file);

        assertEquals(1, set.topics().get(0).judgments().size());
        assertEquals(1, set.topics().get(0).judgments().get("5"));
    }

    @Test
    void rejectsUnexpectedColumnCounts(@TempDir Path dir) throws IOException {
        Path file = write(dir, "broken.tsv", "0\tA passage.\n");
        IOException error = assertThrows(IOException.class, () -> EvaluationSet.load(file));
        assertTrue(error.getMessage().contains("line 1"), error.getMessage());
    }

    @Test
    void rejectsAnEmptyFile(@TempDir Path dir) throws IOException {
        Path file = write(dir, "empty.tsv", "");
        assertThrows(IOException.class, () -> EvaluationSet.load(file));
    }
}
