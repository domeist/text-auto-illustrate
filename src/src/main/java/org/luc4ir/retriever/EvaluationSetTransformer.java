package org.luc4ir.retriever;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.store.FSDirectory;
import org.luc4ir.indexing.TrecDocIndexer;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Properties;

/**
 * @author Dom
 */

public class EvaluationSetTransformer {

    public static ArrayList<String> sectionIds = new ArrayList<>();
    public static ArrayList<String> sectionContents = new ArrayList<>();
    public static ArrayList<ArrayList<String>> urlsList = new ArrayList<>();
    public static ArrayList<ArrayList<String>> urlsList2 = new ArrayList<>();
    public static ArrayList<ArrayList<String>> urlsIdList = new ArrayList<>();
    public static ArrayList<ArrayList<String>> urlsIdList2 = new ArrayList<>();

    TrecDocIndexer indexer;
    Properties prop;

    public EvaluationSetTransformer() throws Exception {
        indexer = new TrecDocIndexer("retrieve.properties");
        prop = indexer.getProperties();
    }

    public void readEvaluationSet() throws IOException {
        String queriesPath = prop.getProperty("queries.unformatted");
        BufferedReader reader = new BufferedReader(new FileReader(queriesPath));
        String line = reader.readLine();
        while (line != null) {
            String[] fields = line.split("\t");
            String sectionId = fields[0];
            String sectionContent = fields[1];
            String urlLine = fields[2];
            System.out.println("urlLine: " + urlLine);
            String[] urlsArr = urlLine.split(",");
            ArrayList<String> urls = new ArrayList<>(Arrays.asList(urlsArr));
            System.out.println("urls: " + urls);
            sectionIds.add(sectionId);
            sectionContents.add(sectionContent);
            urlsList.add(urls);
            line = reader.readLine();
        }
        reader.close();
    }

    public void readEvaluationSetMulti() throws IOException {
        String queriesPath = prop.getProperty("queries.unformatted");
        BufferedReader reader = new BufferedReader(new FileReader(queriesPath));
        String line = reader.readLine();
        while (line != null) {
            String[] fields = line.split("\t");
            String sectionId = fields[0];
            String sectionContent = fields[1];
            String urlLine = fields[2];
            String urlLine2 = fields[3];
            System.out.println("urlLine: " + urlLine);
            String[] urlsArr = urlLine.split(",");
            String[] urlsArr2 = urlLine2.split(",");
            ArrayList<String> urls = new ArrayList<>(Arrays.asList(urlsArr));
            ArrayList<String> urls2 = new ArrayList<>(Arrays.asList(urlsArr2));
            System.out.println("urls: " + urls);
            sectionIds.add(sectionId);
            sectionContents.add(sectionContent);
            urlsList.add(urls);
            urlsList2.add(urls2);
            line = reader.readLine();
        }
        reader.close();
    }

    public void formatEvaluationSet() throws IOException, ParseException {
        int length = sectionIds.size();
        for (int i = 0; i < length; i++) {
            ArrayList<String> urls = urlsList.get(i);
            ArrayList<String> urlIds = new ArrayList<>();
            for (String url : urls) {
                String urlId = urlToId(url);
                urlIds.add(urlId);
            }
            urlsIdList.add(urlIds);
        }
    }

    public void formatEvaluationSetMulti() throws IOException, ParseException {
        int length = sectionIds.size();
        for (int i = 0; i < length; i++) {
            ArrayList<String> urls = urlsList.get(i);
            ArrayList<String> urls2 = urlsList2.get(i);
            ArrayList<String> urlIds = new ArrayList<>();
            ArrayList<String> urlIds2 = new ArrayList<>();
            for (String url : urls) {
                String urlId = urlToId(url);
                urlIds.add(urlId);
            }
            for (String url2 : urls2) {
                String urlId2 = urlToId(url2);
                urlIds2.add(urlId2);
            }
            urlsIdList.add(urlIds);
            urlsIdList2.add(urlIds2);
        }
    }

    public void writeEvaluationSet() throws IOException {
        String queriesPath = prop.getProperty("queries");
        FileWriter fw = new FileWriter(queriesPath);
        int length = sectionIds.size();
        for (int i = 0; i < length; i++) {
            String sectionId = sectionIds.get(i);
            String sectionContent = sectionContents.get(i);
            String urlsIdString = String.join(",", urlsIdList.get(i));
            System.out.println("urlsIdString: " + urlsIdString);
            fw.write(sectionId + "\t" + sectionContent + "\t" + urlsIdString + "\n");
        }
        fw.close();
    }

    public void writeEvaluationSetMulti() throws IOException {
        String queriesPath = prop.getProperty("queries");
        FileWriter fw = new FileWriter(queriesPath);
        int length = sectionIds.size();
        for (int i = 0; i < length; i++) {
            String sectionId = sectionIds.get(i);
            String sectionContent = sectionContents.get(i);
            String urlsIdString = String.join(",", urlsIdList.get(i));
            String urlsIdString2 = String.join(",", urlsIdList2.get(i));
            System.out.println("urlsIdString: " + urlsIdString);
            System.out.println("urlsIdString2: " + urlsIdString2);
            fw.write(sectionId + "\t" + sectionContent + "\t" + urlsIdString + "\t" + urlsIdString2 + "\n");
        }
        fw.close();
    }

    public String urlToId(String image) throws IOException, ParseException {
        String indexPath = prop.getProperty("index");
        Path path = Paths.get(indexPath);
        IndexReader reader = DirectoryReader.open(FSDirectory.open(path));
        IndexSearcher searcher = new IndexSearcher(reader);
        StandardAnalyzer analyzer = new StandardAnalyzer();
        QueryParser parser = new QueryParser("image", analyzer);
        Query query = parser.parse(QueryParser.escape(image));
        ScoreDoc[] hits = searcher.search(query, 1).scoreDocs;
        ScoreDoc hit = hits[0];
        Document hitDoc = searcher.doc(hit.doc);
        return hitDoc.get("id");
    }

    public void performTransformation() throws IOException, ParseException {
        readEvaluationSet();
        formatEvaluationSet();
        writeEvaluationSet();
    }

    public void performTransformationMulti() throws IOException, ParseException {
        readEvaluationSetMulti();
        formatEvaluationSetMulti();
        writeEvaluationSetMulti();
    }

    public static void main(String[] args) throws Exception {
        EvaluationSetTransformer evaluationSetTransformer = new EvaluationSetTransformer();
        evaluationSetTransformer.performTransformation();
        evaluationSetTransformer.performTransformationMulti();
    }
}
