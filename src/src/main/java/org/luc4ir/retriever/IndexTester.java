package org.luc4ir.retriever;

import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.FSDirectory;
import org.luc4ir.indexing.TrecDocIndexer;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * @author Dom
 */

public class IndexTester {

    public static void main(String[] args) throws Exception {
        TrecDocIndexer indexer;
        IndexReader reader;
        Properties prop;

        indexer = new TrecDocIndexer("retrieve.properties");
        prop = indexer.getProperties();
        Path path = Paths.get(prop.getProperty("index"));
        reader = DirectoryReader.open(FSDirectory.open(path));

        String[] testWords = {"architecture", "above", "computers"};
        for (String testWord : testWords) {
            String analyzedWord = TrecDocIndexer.analyze(indexer.getAnalyzer(), testWord);
            assert analyzedWord != null;
            if (analyzedWord.length() > 0) {
                int df = reader.docFreq(new Term(TrecDocIndexer.FIELD_ANALYZED_CONTENT, analyzedWord));
                System.out.printf("DF(%s): %d%n", analyzedWord, df);
            }
        }
    }
}
