package org.luc4ir.retriever;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Dom
 */

public class Evaluator {

    static final int defaultNumberQueryWords = 10;
    static final int maxFractionQueryWords = 25;
    static final int maxFractionQueryWordsLow = 5;
    static final float defaultK = 1.2F;
    static final float defaultB = 0.75F;
    static final float startingK = 0.2F;
    static final float endingK = 1F;
    static final float startingB = 0F;
    static final float endingB = 1F;
    static final float intervalK = 0.2F;
    static final float intervalB = 0.2F;

    public static void writeList(String fileName, List<Float> values) throws IOException {
        FileWriter myWriter = new FileWriter(fileName, true);
        for (int i = 0; i < values.size(); i++) {
            Float value = values.get(i);
            if (i == values.size() - 1)
                myWriter.write(String.valueOf(value));
            else
                myWriter.write(value + ",");
        }
        myWriter.write("\n");
        myWriter.close();
    }

    public static void writeValue(String fileName, float value) throws IOException {
        FileWriter myWriter = new FileWriter(fileName, true);
        myWriter.write(String.valueOf(value));
        myWriter.write("\n");
        myWriter.close();
    }

    public static void evaluateNumberQueryWords() throws Exception {
        List<Float> precision = new ArrayList<>();
        List<Float> recall = new ArrayList<>();
        List<Float> mrr = new ArrayList<>();
        List<Float> ap = new ArrayList<>();

        for (int i = 5; i <= maxFractionQueryWords; i+=5) {
            System.out.println("\n\n" + "*** Iteration " + i + " ***" + "\n");
            RetrieverNew retrieverNew = new RetrieverNew();
            float[] metrics = retrieverNew.runRetriever(defaultK, defaultB, i);
            precision.add(metrics[0]);
            recall.add(metrics[1]);
            mrr.add(metrics[2]);
            ap.add(metrics[3]);
        }

        System.out.println("\n\n" + "*** Combined Average Values ***");
        System.out.println("-------------------------------");
        System.out.println("Precision: " + precision);
        System.out.println("Recall: " + recall);
        System.out.println("MRR: " + mrr);
        System.out.println("AP: " + ap + "\n\n");

        writeList("top_query_words_fraction_precision.txt", precision);
        writeList("top_query_words_fraction_recall.txt", recall);
        writeList("top_query_words_fraction_mrr.txt", mrr);
        writeList("top_query_words_fraction_ap.txt", ap);
    }

    public static void evaluateNumberQueryWordsLow() throws Exception {
        List<Float> precision = new ArrayList<>();
        List<Float> recall = new ArrayList<>();
        List<Float> mrr = new ArrayList<>();
        List<Float> ap = new ArrayList<>();

        for (int i = 1; i <= maxFractionQueryWordsLow; i++) {
            System.out.println("\n\n" + "*** Iteration " + i + " ***" + "\n");
            RetrieverNew retrieverNew = new RetrieverNew();
            float[] metrics = retrieverNew.runRetriever(defaultK, defaultB, i);
            precision.add(metrics[0]);
            recall.add(metrics[1]);
            mrr.add(metrics[2]);
            ap.add(metrics[3]);
        }

        System.out.println("\n\n" + "*** Combined Average Values ***");
        System.out.println("-------------------------------");
        System.out.println("Precision: " + precision);
        System.out.println("Recall: " + recall);
        System.out.println("MRR: " + mrr);
        System.out.println("AP: " + ap + "\n\n");

        writeList("top_query_words_fraction_low_precision.txt", precision);
        writeList("top_query_words_fraction_low_recall.txt", recall);
        writeList("top_query_words_fraction_low_mrr.txt", mrr);
        writeList("top_query_words_fraction_low_ap.txt", ap);
    }

    public static void evaluateBM25Parameters() throws Exception {
        int repetitionsK = (int) ((endingK - startingK) / intervalK + 1);
        int repetitionsB = (int) ((endingB - startingB) / intervalB + 1);
        float k = startingK;
        for (int i = 0; i < repetitionsK; i++) {
            List<Float> precision = new ArrayList<>();
            List<Float> recall = new ArrayList<>();
            List<Float> mrr = new ArrayList<>();
            List<Float> ap = new ArrayList<>();
            List<Float> bValues = new ArrayList<>();
            float b = startingB;
            for (int j = 0; j < repetitionsB; j++) {
                System.out.printf("\n\n" + "*** Iteration (k=%f, b=%f) ***\n\n", k, b);
                RetrieverNew retrieverNew = new RetrieverNew();
                float[] metrics = retrieverNew.runRetriever(defaultNumberQueryWords, k, b);
                precision.add(metrics[0]);
                recall.add(metrics[1]);
                mrr.add(metrics[2]);
                ap.add(metrics[3]);
                bValues.add(b);
                b += intervalB;
            }
            System.out.println("\n\n" + "*** Combined Average Values ***");
            System.out.println("-------------------------------");
            System.out.println("Precision: " + precision);
            System.out.println("Recall: " + recall);
            System.out.println("MRR: " + mrr);
            System.out.println("AP: " + ap + "\n\n");

            writeValue("bm25_parameters_precision.txt", k);
            writeValue("bm25_parameters_recall.txt", k);
            writeValue("bm25_parameters_mrr.txt", k);
            writeValue("bm25_parameters_ap.txt", k);

            writeList("bm25_parameters_precision.txt", bValues);
            writeList("bm25_parameters_recall.txt", bValues);
            writeList("bm25_parameters_mrr.txt", bValues);
            writeList("bm25_parameters_ap.txt", bValues);

            writeList("bm25_parameters_precision.txt", precision);
            writeList("bm25_parameters_recall.txt", recall);
            writeList("bm25_parameters_mrr.txt", mrr);
            writeList("bm25_parameters_ap.txt", ap);

            k += intervalK;
        }
    }

    public static void main(String[] args) throws Exception {
        evaluateNumberQueryWords();
        evaluateNumberQueryWordsLow();
        evaluateBM25Parameters();
    }
}
