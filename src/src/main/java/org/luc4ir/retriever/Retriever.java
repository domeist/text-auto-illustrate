package org.luc4ir.retriever;

import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.Similarity;
import org.luc4ir.indexing.TrecDocIndexer;
import org.luc4ir.trec.TRECQuery;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Dom
 */

public class Retriever extends TrecDocRetriever {

    static boolean debug = true;
    static final int defaultNumberQueryWords = 10;

    EnglishAnalyzer analyzer;
    QueryParser parser;
    ArrayList<String> sectionContents;
    ArrayList<String> sectionIds;
    ArrayList<String> imageIds;

    public Retriever(String propFile, Similarity sim) throws Exception {
        super(propFile, sim);
        analyzer = new EnglishAnalyzer();
        parser = new QueryParser("words", analyzer);
        sectionContents = new ArrayList<>();
        sectionIds = new ArrayList<>();
        imageIds = new ArrayList<>();
    }

    /**
     * Read the evaluation set and generate a list of TrecQuery objects
     *
     * @return list of TrecQuery objects
     * @throws IOException IOException
     * @throws ParseException ParseException
     */
    @Override
    public List<TRECQuery> constructQueries() throws IOException, ParseException {
        readEvaluationSet();
        List<TRECQuery> queries = new ArrayList<>();
        for (int i = 0; i < sectionContents.size(); i++) {
            String textSection = sectionContents.get(i);
            String analyzedTextSection = TrecDocIndexer.analyze(indexer.getAnalyzer(), textSection);
            HashMap<String, Integer> frequencyHashMap;
            if (analyzedTextSection != null) {
                frequencyHashMap = getFrequencyHashMap(analyzedTextSection);
                HashMap<String, Float> modifiedFrequencyHashMap = scoreQueryWords(frequencyHashMap);
                String topQueryWords = getTopQueryWords(modifiedFrequencyHashMap, defaultNumberQueryWords);
                Query query = parser.parse(topQueryWords);
                TRECQuery trecQuery = new TRECQuery(sectionIds.get(i), query);
                queries.add(trecQuery);
            }
        }
        return queries;
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
        String queriesPath = prop.getProperty("queries");
        BufferedReader reader = new BufferedReader(new FileReader(queriesPath));
        String line = reader.readLine();
        while (line != null) {
            String[] fields = line.split("\t");
            String sectionId = fields[0];
            String sectionContent = fields[1];
            String imageId = fields[2];
            sectionIds.add(sectionId);
            sectionContents.add(sectionContent);
            imageIds.add(imageId);
            line = reader.readLine();
        }
        reader.close();
        print("sectionIds: " + sectionIds);
        print("sectionContents: " + sectionContents);
        print("imageIds: " + imageIds + "\n");
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
            // print("word: " + key + "\n" + "df: " + df + "\n" + "idf: " + idf + "\n" + "-------------------");
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

    public static void main(String[] args) {
        if (args.length < 1) {
            args = new String[1];
            args[0] = "retrieve.properties";
        }
        Retriever searcher;
        try {
            searcher = new Retriever(args[0], new BM25Similarity());
            searcher.setSimilarity(new BM25Similarity());
            searcher.retrieveAll();
            searcher.reader.close();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
