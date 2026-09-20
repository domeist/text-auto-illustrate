package autoillustrate.index;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;

/**
 * Builds a searchable index from a corpus of captioned images.
 *
 * <p>The corpus is a tab-separated file, one image per line:
 * {@code id <TAB> pageUrl <TAB> imageUrl <TAB> caption}. Only the caption is
 * searchable — this project matches text against text, and never looks at the
 * image itself. Files ending in {@code .gz} are decompressed on the fly.
 */
public final class CorpusIndexer {

    public static final String FIELD_ID = "id";
    public static final String FIELD_PAGE_URL = "pageUrl";
    public static final String FIELD_IMAGE_URL = "imageUrl";
    public static final String FIELD_CAPTION = "caption";
    /**
     * The Wikipedia article title, recovered from the page URL.
     *
     * <p>Captions are often uninformative about their subject — "copied at 300
     * pixels/inch from the original PDF" says nothing — while the article the
     * image came from usually names it exactly. Indexing the title recovers
     * that signal, and gives short captions something beyond a single matching
     * word to be ranked on.
     */
    public static final String FIELD_TITLE = "title";
    /**
     * Sortable copy of the id, used only to break ties.
     *
     * <p>Short captions produce many identical BM25 scores. Left alone, Lucene
     * orders tied documents by internal document number, which depends on the
     * order segments happened to merge — so the same corpus indexed twice can
     * rank tied results differently and shift rank-sensitive measures like MRR.
     * Sorting ties by this field makes results reproducible across builds.
     */
    public static final String FIELD_ID_SORT = "idSort";

    private static final int EXPECTED_COLUMNS = 4;

    private final Path indexDir;

    public CorpusIndexer(Path indexDir) {
        this.indexDir = indexDir;
    }

    /**
     * Indexes every {@code .tsv} or {@code .tsv.gz} file in {@code corpusDir},
     * replacing any existing index.
     *
     * @return the number of images indexed
     */
    public long indexDirectory(Path corpusDir) throws IOException {
        if (!Files.isDirectory(corpusDir)) {
            throw new IOException("corpus directory not found: " + corpusDir);
        }
        Analyzer analyzer = Analyzers.english();
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
        config.setRAMBufferSizeMB(512);

        long total = 0;
        try (FSDirectory directory = FSDirectory.open(indexDir);
             IndexWriter writer = new IndexWriter(directory, config)) {
            try (var files = Files.list(corpusDir)) {
                for (Path file : files.filter(CorpusIndexer::isCorpusFile).sorted().toList()) {
                    long count = indexFile(writer, file);
                    System.out.printf("  %s: %,d images%n", file.getFileName(), count);
                    total += count;
                }
            }
        }
        return total;
    }

    private static boolean isCorpusFile(Path path) {
        String name = path.getFileName().toString();
        return name.endsWith(".tsv") || name.endsWith(".tsv.gz");
    }

    private long indexFile(IndexWriter writer, Path file) throws IOException {
        long count = 0;
        long skipped = 0;
        try (BufferedReader reader = open(file)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] columns = line.split("\t", -1);
                if (columns.length < EXPECTED_COLUMNS) {
                    skipped++;
                    continue;
                }
                String caption = columns[3].strip();
                if (caption.isEmpty()) {
                    skipped++;
                    continue;
                }
                writer.addDocument(toDocument(columns[0], columns[1], columns[2], caption));
                count++;
            }
        }
        if (skipped > 0) {
            System.out.printf("  %s: skipped %,d rows (malformed or no caption)%n",
                    file.getFileName(), skipped);
        }
        return count;
    }

    private static BufferedReader open(Path file) throws IOException {
        InputStream in = Files.newInputStream(file);
        if (file.getFileName().toString().endsWith(".gz")) {
            in = new GZIPInputStream(in, 1 << 16);
        }
        return new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8), 1 << 16);
    }

    private static Document toDocument(String id, String pageUrl, String imageUrl, String caption) {
        Document doc = new Document();
        doc.add(new StringField(FIELD_ID, id, Field.Store.YES));
        doc.add(new SortedDocValuesField(FIELD_ID_SORT, new BytesRef(id)));
        doc.add(new StoredField(FIELD_PAGE_URL, pageUrl));
        doc.add(new StoredField(FIELD_IMAGE_URL, imageUrl));
        doc.add(new TextField(FIELD_CAPTION, caption, Field.Store.YES));
        doc.add(new TextField(FIELD_TITLE, titleFrom(pageUrl), Field.Store.YES));
        return doc;
    }

    /**
     * Recovers the article title from a Wikipedia URL:
     * {@code .../wiki/Hill_City,_Kansas} becomes {@code Hill City, Kansas}.
     *
     * <p>Returns an empty string for anything that does not look like an
     * article URL, so an unusual corpus simply gets no title signal rather
     * than a misleading one.
     */
    static String titleFrom(String pageUrl) {
        if (pageUrl == null || pageUrl.isBlank()) {
            return "";
        }
        int marker = pageUrl.lastIndexOf("/wiki/");
        int start = marker >= 0 ? marker + "/wiki/".length() : pageUrl.lastIndexOf('/') + 1;
        if (start <= 0 || start >= pageUrl.length()) {
            return "";
        }
        String slug = pageUrl.substring(start);
        int fragment = slug.indexOf('#');
        if (fragment >= 0) {
            slug = slug.substring(0, fragment);
        }
        int query = slug.indexOf('?');
        if (query >= 0) {
            slug = slug.substring(0, query);
        }
        try {
            slug = URLDecoder.decode(slug, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            // A stray '%' that is not a valid escape; the raw slug is still usable.
        }
        return slug.replace('_', ' ').strip();
    }
}
