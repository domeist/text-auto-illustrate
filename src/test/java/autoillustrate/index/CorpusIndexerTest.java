package autoillustrate.index;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CorpusIndexerTest {

    @Test
    @DisplayName("recovers the article title from a Wikipedia URL")
    void extractsTitle() {
        assertEquals("Frieze",
                CorpusIndexer.titleFrom("https://en.wikipedia.org/wiki/Frieze"));
    }

    @Test
    @DisplayName("underscores become spaces, so the title analyzes as words")
    void underscoresBecomeSpaces() {
        assertEquals("Hill City, Kansas",
                CorpusIndexer.titleFrom("https://en.wikipedia.org/wiki/Hill_City,_Kansas"));
    }

    @Test
    void decodesPercentEscapes() {
        assertEquals("Gauḍa (city)",
                CorpusIndexer.titleFrom("https://en.wikipedia.org/wiki/Gau%E1%B8%8Da_(city)"));
    }

    @Test
    void stripsFragmentsAndQueryStrings() {
        assertEquals("Frieze",
                CorpusIndexer.titleFrom("https://en.wikipedia.org/wiki/Frieze#Architecture"));
        assertEquals("Frieze",
                CorpusIndexer.titleFrom("https://en.wikipedia.org/wiki/Frieze?action=edit"));
    }

    @Test
    @DisplayName("a malformed escape falls back to the raw slug rather than failing")
    void toleratesBadEscapes() {
        assertEquals("100% cotton",
                CorpusIndexer.titleFrom("https://en.wikipedia.org/wiki/100%_cotton"));
    }

    @Test
    void handlesUrlsThatAreNotArticles() {
        assertEquals("", CorpusIndexer.titleFrom(""));
        assertEquals("", CorpusIndexer.titleFrom(null));
        assertEquals("", CorpusIndexer.titleFrom("https://example.org/"));
    }
}
