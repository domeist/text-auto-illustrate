package org.luc4ir.retriever;

import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.FSDirectory;
import org.luc4ir.indexing.TrecDocIndexer;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Dom
 */

public class RetrieverMulti {

    static final int defaultNumberQueryWords = 10;
    static final int defaultNumberRetrievedDocuments = 100;
    static final float defaultK = 1.2F;
    static final float defaultB = 0.75F;

    static boolean debug = true;

    ArrayList<String> sectionIds;
    ArrayList<String> sectionContents;
    ArrayList<String> imageIds1;
    ArrayList<String> imageIds2;
    ArrayList<String> imageIds3;
    Path path;
    TrecDocIndexer indexer;
    IndexReader reader;
    IndexSearcher searcher;
    EnglishAnalyzer analyzer;
    QueryParser parser;
    PerformanceMetrics performanceMetrics;
    Properties prop;

    public RetrieverMulti() throws Exception {
        indexer = new TrecDocIndexer("retrieve.properties");
        prop = indexer.getProperties();
        String indexPath = prop.getProperty("index");
        sectionIds = new ArrayList<>();
        sectionContents = new ArrayList<>();
        imageIds1 = new ArrayList<>();
        imageIds2 = new ArrayList<>();
        imageIds3 = new ArrayList<>();
        path = Paths.get(indexPath);
        reader = DirectoryReader.open(FSDirectory.open(path));
        searcher = new IndexSearcher(reader);
        analyzer = new EnglishAnalyzer();
        parser = new QueryParser("words", analyzer);
        performanceMetrics = new PerformanceMetrics();
    }

    /**
     * Print the string if the debug flag is true
     *
     * @param string value to print
     */
    public void print(String string) {
        if (debug)
            System.out.println(string);
    }

    /**
     * Read the evaluation set
     *
     * @throws IOException IOException
     */
    public void readEvaluationSet() throws IOException {
        String queriesPath = prop.getProperty("queries.extended");
        BufferedReader reader = new BufferedReader(new FileReader(queriesPath));
        String line = reader.readLine();
        while (line != null) {
            String[] fields = line.split("\t");
            String sectionId = fields[0];
            String sectionContent = fields[1];
            String images1 = fields[2];
            String images2 = fields[3];
            String images3 = fields[4];
            sectionIds.add(sectionId);
            sectionContents.add(sectionContent);
            imageIds1.add(images1);
            imageIds2.add(images2);
            imageIds3.add(images3);
            line = reader.readLine();
        }
        reader.close();
        print("*** Our evaluation dataset ***");
        print("------------------------------");
        print("sectionIds: " + sectionIds);
        print("sectionContents: " + sectionContents + "\n\n");
    }

    /**
     * Take a text section and create a frequency hashmap
     *
     * @param textSection text section
     * @return frequency hashmap
     */
    public HashMap<String,Integer> getFrequencyHashMap(String textSection) {
        HashMap<String,Integer> frequencyHashMap = new HashMap<>();
        String[] words = textSection.split(" ");
        ArrayList<String> wordsList = new ArrayList<>(Arrays.asList(words));
        for (String word : wordsList) {
            if (frequencyHashMap.containsKey(word))
                frequencyHashMap.put(word, frequencyHashMap.get(word) + 1);
            else
                frequencyHashMap.put(word, 1);
        }
        print("frequencyHashMap: " + frequencyHashMap);
        return frequencyHashMap;
    }

    /**
     * Sort the hashmap by the values in the descending order
     *
     * @param hashMap frequency hashmap
     * @return sorted hashmap
     */
    public HashMap<String,Float> sortHashMap(HashMap<String,Float> hashMap) {
        List<Map.Entry<String, Float>> list = new LinkedList<>(hashMap.entrySet());
        list.sort(Map.Entry.<String, Float>comparingByValue().reversed());
        HashMap<String, Float> sortedHashMap = new LinkedHashMap<>();
        for (Map.Entry<String,Float> entry : list) {
            sortedHashMap.put(entry.getKey(), entry.getValue());
        }
        return sortedHashMap;
    }

    /**
     * Take a frequency hashmap, calculate the inverse document frequency (idf), multiply it by each frequency in the
     * hashmap, and sort the hashmap in descending order by values (frequencies)
     *
     * @param hashMap frequency hashmap
     * @return frequency hashmap with modified scores
     * @throws IOException IOException
     */
    public HashMap<String,Float> scoreQueryWords(HashMap<String,Integer> hashMap) throws IOException {
        HashMap<String,Float> modifiedHashMap = new HashMap<>();
        float idf;
        int N = reader.numDocs();
        for (HashMap.Entry<String,Integer> entry : hashMap.entrySet()) {
            String key = entry.getKey();
            int value = entry.getValue();
            int df = reader.docFreq(new Term(TrecDocIndexer.FIELD_ANALYZED_CONTENT, key));
            if (df != 0)
                idf = (float)Math.log(N/(float)df);
            else
                idf = 0;
            modifiedHashMap.put(key, (value * idf));
        }
        print("modifiedHashMap: " + sortHashMap(modifiedHashMap));
        return sortHashMap(modifiedHashMap);
    }

    /**
     * Take the frequency hash map and the number of words and return top query words
     *
     * @param hashMap frequency hashmap
     * @param numberWords number of words that we want to return
     * @return string of top n query words
     */
    public String getTopQueryWords(HashMap<String,Float> hashMap, int numberWords) {
        List<String> topQueryWords = hashMap.keySet().stream().limit(numberWords).collect(Collectors.toList());
        print("topQueryWords: " + String.join(" ", topQueryWords) + "\n");
        return String.join(" ", topQueryWords);
    }

    /**
     * Take a text section, number of top query words, and number of documents to retrieve, and return the ids of the
     * retrieved documents
     *
     * @param textSection text section
     * @param numberQueryWords number of top query words
     * @param numberRetrievedDocuments number of top documents to retrieve
     * @return ids of the retrieved documents
     * @throws Exception Exception
     */
    public ArrayList<String> retrieveDocumentIds(String textSection, int numberQueryWords, int numberRetrievedDocuments) throws Exception {
        HashMap<String,Integer> frequencyHashMap = getFrequencyHashMap(textSection);
        HashMap<String,Float> modifiedFrequencyHashMap = scoreQueryWords(frequencyHashMap);
        String topQueryWords = getTopQueryWords(modifiedFrequencyHashMap, numberQueryWords);
        Query query = parser.parse(topQueryWords);
        TopDocs topDocs = searcher.search(query, numberRetrievedDocuments);
        ScoreDoc[] hits = topDocs.scoreDocs;
        ArrayList<String> retrievedIds = new ArrayList<>();
        for (ScoreDoc hit : hits) {
            Document hitDoc = searcher.doc(hit.doc);
            retrievedIds.add(hitDoc.get("id"));
        }
        return retrievedIds;
    }

    /**
     * Run the retriever program for all queries
     *
     * @param numberQueryWords number of top query words
     * @param k BM25 similarity parameter k
     * @param b BM25 similarity parameter b
     * @throws Exception Exception
     */
    public void runRetriever(int numberQueryWords, float k, float b) throws Exception {
        readEvaluationSet();
        searcher.setSimilarity(new BM25Similarity(k, b));
        float[] metrics = new float[5];
        for (int i = 0; i < sectionContents.size(); i++) {

            print("*** Evaluating text section: " + sectionIds.get(i) + " ***");
            print("----------------------------------");

            String textSection = sectionContents.get(i);
            String analyzedTextSection = TrecDocIndexer.analyze(indexer.getAnalyzer(), textSection);

            print("textSection: " + textSection);
            print("analyzedTextSection: " + analyzedTextSection);

            ArrayList<String> groundTruthIds1 = new ArrayList<>(Arrays.asList(imageIds1.get(i).split(",")));
            ArrayList<String> groundTruthIds2 = new ArrayList<>(Arrays.asList(imageIds2.get(i).split(",")));
            ArrayList<String> groundTruthIds3 = new ArrayList<>(Arrays.asList(imageIds3.get(i).split(",")));
            ArrayList<String> retrievedIds = retrieveDocumentIds(analyzedTextSection, numberQueryWords, defaultNumberRetrievedDocuments);

            ArrayList<String> combinedGroundTruthIds = new ArrayList<>(groundTruthIds1);
            combinedGroundTruthIds.addAll(groundTruthIds2);
            combinedGroundTruthIds.addAll(groundTruthIds3);

            metrics[0] += performanceMetrics.precisionAtK(5, retrievedIds, combinedGroundTruthIds);
            metrics[1] += performanceMetrics.recall(retrievedIds, combinedGroundTruthIds);
            metrics[2] += performanceMetrics.MRR(retrievedIds, combinedGroundTruthIds);
            metrics[3] += performanceMetrics.AP(retrievedIds, combinedGroundTruthIds);
            metrics[4] += performanceMetrics.nDCG(retrievedIds, groundTruthIds1, groundTruthIds2, groundTruthIds3);
        }

        for (int i = 0; i < metrics.length; i++) {
            metrics[i] = metrics[i] / sectionContents.size();
        }

        print("*** Final Average Values");
        print("------------------------");
        print("p@5: " + metrics[0]);
        print("recall: " + metrics[1]);
        print("MRR: " + metrics[2]);
        print("MAP: " + metrics[3]);
        print("nDCG: " + metrics[4]);
    }

    public static void main(String[] args) throws Exception {
        RetrieverMulti retrieverMulti = new RetrieverMulti();
        retrieverMulti.runRetriever(defaultNumberQueryWords, defaultK, defaultB);
    }
}
