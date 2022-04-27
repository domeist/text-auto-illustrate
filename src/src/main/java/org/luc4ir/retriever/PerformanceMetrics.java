package org.luc4ir.retriever;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.lang.Math;

/**
 * @author Dom
 */

public class PerformanceMetrics {

    static boolean debug = true;

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
     * Calculate precision @ k
     *
     * @param k number of retrieved ids
     * @param retrievedIds retrieved ids
     * @param groundTruthIds ground truth ids
     * @return precision @ k
     */
    public float precisionAtK(int k, ArrayList<String> retrievedIds, ArrayList<String> groundTruthIds) {
        Set<String> groundTruthIdsSet = new HashSet<>(groundTruthIds);
        int matched = 0;
        for (int i = 0; i < k; i++) {
            String retrievedId = retrievedIds.get(i);
            if (groundTruthIdsSet.contains(retrievedId))
                matched++;
        }
        float precision = (float) matched / k;
        print("p@" + k + ": " + precision);
        return precision;
    }

    /**
     * Calculate recall
     *
     * @param retrievedIds retrieved ids
     * @param groundTruthIds ground truth ids
     * @return recall
     */
    public float recall(ArrayList<String> retrievedIds, ArrayList<String> groundTruthIds) {
        Set<String> retrievedIdsSet = new HashSet<>(retrievedIds);
        int matched = 0;
        for (String groundTruthId : groundTruthIds) {
            if (retrievedIdsSet.contains(groundTruthId))
                matched++;
        }
        float recall = (float) matched / groundTruthIds.size();
        print("recall: " + recall);
        return recall;
    }

    /**
     * Calculate MRR
     *
     * @param retrievedIds retrieved ids
     * @param groundTruthIds ground truth ids
     * @return MRR
     */
    public float MRR(ArrayList<String> retrievedIds, ArrayList<String> groundTruthIds) {
        int rank = 1;
        float mrr = 0;
        Set<String> groundTruthSet = new HashSet<>(groundTruthIds);
        for (String retrievedImage : retrievedIds) {
            if (groundTruthSet.contains(retrievedImage)) {
                mrr = 1 / (float) rank;
                break;
            }
            rank++;
        }
        print("MRR: " + mrr);
        return mrr;
    }

    /**
     * Calculate AP
     *
     * @param retrievedIds retrieved ids
     * @param groundTruthIds ground truth ids
     * @return AP
     */
    public float AP(ArrayList<String> retrievedIds, ArrayList<String> groundTruthIds) {
        int count = 1;
        int rel = 0;
        float sum = 0;
        Set<String> groundTruthSet = new HashSet<>(groundTruthIds);
        for (String retrievedId: retrievedIds) {
            if (groundTruthSet.contains(retrievedId)) {
                rel++;
                sum += (float) rel / count;
            }
            count++;
        }
        if (rel == 0) {
            print("AP: " + rel);
            return rel;
        }
        print("AP: " + sum / rel);
        return sum / rel;
    }

    /**
     * Calculate DCG
     *
     * @param retrievedIds retrieved ids
     * @param ids1 ground truth level 1 ids
     * @param ids2 ground truth level 2 ids
     * @param ids3 ground truth level 3 ids
     * @return dcg
     */
    public double DCG(ArrayList<String> retrievedIds, ArrayList<String> ids1, ArrayList<String> ids2, ArrayList<String> ids3) {
        double dcg = 0;
        int pos = 0;
        for (String retrievedId: retrievedIds) {
            if (ids1.contains(retrievedId)) {
                double denom = Math.log(pos+1);
                if (denom == 0) denom = 1;
                dcg += 3 / denom;
            }
            else if (ids2.contains(retrievedId)) {
                double denom = Math.log(pos+1);
                if (denom == 0) denom = 1;
                dcg += 2 / denom;
            }
            else if (ids3.contains(retrievedId)) {
                double denom = Math.log(pos+1);
                if (denom == 0) denom = 1;
                dcg += 1 / denom;
            }
            pos++;
        }
        return dcg;
    }

    /**
     * Calculate iDCG
     *
     * @param retrievedIds retrieved ids
     * @param ids1 ground truth level 1 ids
     * @param ids2 ground truth level 2 ids
     * @param ids3 ground truth level 3 ids
     * @return dcg
     */
    public double iDCG(ArrayList<String> retrievedIds, ArrayList<String> ids1, ArrayList<String> ids2, ArrayList<String> ids3) {
        ArrayList<Double> values = new ArrayList<>();
        double idcg = 0;
        int pos = 0;
        for (String ignored : ids1) {
            double denom = Math.log(pos+1);
            if (denom == 0) denom = 1;
            values.add(3 / denom);
            pos++;
        }
        for (String ignored : ids2) {
            double denom = Math.log(pos+1);
            if (denom == 0) denom = 1;
            values.add(2 / denom);
            pos++;
        }
        for (String ignored : ids3) {
            double denom = Math.log(pos+1);
            if (denom == 0) denom = 1;
            values.add(1 / denom);
            pos++;
        }
        for (int i = 0; i < retrievedIds.size(); i++)
            idcg += values.get(i);
        return idcg;
    }

    /**
     * Calculate nDCG
     *
     * @param retrievedIds retrieved ids
     * @param ids1 ground truth level 1 ids
     * @param ids2 ground truth level 2 ids
     * @param ids3 ground truth level 3 ids
     * @return dcg
     */
    public double nDCG(ArrayList<String> retrievedIds, ArrayList<String> ids1, ArrayList<String> ids2, ArrayList<String> ids3) {
        double dcg = DCG(retrievedIds, ids1, ids2, ids3);
        double idcg = iDCG(retrievedIds, ids1, ids2, ids3);
        double ndcg = dcg / idcg;
        print("nDCG: " + ndcg + "\n\n");
        return ndcg;
    }
}
