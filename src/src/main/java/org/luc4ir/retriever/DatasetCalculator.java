package org.luc4ir.retriever;

import org.luc4ir.indexing.TrecDocIndexer;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;

/**
 * @author Dom
 */

public class DatasetCalculator {

    TrecDocIndexer indexer;
    static Properties prop;

    public DatasetCalculator() throws Exception {
        indexer = new TrecDocIndexer("retrieve.properties");
        prop = indexer.getProperties();
    }

    /**
     * Print average text section length and average number of images in single-level evaluation dataset
     *
     * @param path dataset path
     * @throws IOException IOException
     */
    public void calculateLength(String path) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(path));
        String line = reader.readLine();
        int textSectionsLength = 0;
        int numberImages = 0;
        int numberQueries = 0;
        while (line != null) {
            String[] fields = line.split("\t");
            String textSection = fields[1];
            String imageIds = fields[2];
            String[] textSectionArray = textSection.split(" ");
            String[] imageIdsArray = imageIds.split(",");
            textSectionsLength += textSectionArray.length;
            numberImages += imageIdsArray.length;
            numberQueries += 1;
            line = reader.readLine();
        }
        reader.close();
        float averageTextSectionLength = (float) textSectionsLength / numberQueries;
        float averageNumberImages = (float) numberImages / numberQueries;
        System.out.println("*** Single-level evaluation dataset ***");
        System.out.println("average text section length: " + averageTextSectionLength);
        System.out.println("average number of images: " + averageNumberImages + "\n");
    }

    /**
     * Print average text section length and average number of images in multi-level evaluation dataset
     *
     * @param path dataset path
     * @throws IOException IOException
     */
    public void calculateLengthMulti(String path) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(path));
        String line = reader.readLine();
        int textSectionsLength = 0;
        int numberImages1 = 0;
        int numberImages2 = 0;
        int numberImages3 = 0;
        int numberQueries = 0;
        while (line != null) {
            String[] fields = line.split("\t");
            String textSection = fields[1];
            String imageIds1 = fields[2];
            String imageIds2 = fields[3];
            String imageIds3 = fields[4];
            String[] textSectionArray = textSection.split(" ");
            String[] imageIds1Array = imageIds1.split(",");
            String[] imageIds2Array = imageIds2.split(",");
            String[] imageIds3Array = imageIds3.split(",");
            textSectionsLength += textSectionArray.length;
            numberImages1 += imageIds1Array.length;
            numberImages2 += imageIds2Array.length;
            numberImages3 += imageIds3Array.length;
            numberQueries += 1;
            line = reader.readLine();
        }
        reader.close();
        float averageTextSectionLength = (float) textSectionsLength / numberQueries;
        float averageNumberImages1 = (float) numberImages1 / numberQueries;
        float averageNumberImages2 = (float) numberImages2 / numberQueries;
        float averageNumberImages3 = (float) numberImages3 / numberQueries;
        System.out.println("*** Multi-level evaluation dataset ***");
        System.out.println("average text section length: " + averageTextSectionLength);
        System.out.println("average number of images in level 1: " + averageNumberImages1);
        System.out.println("average number of images in level 2: " + averageNumberImages2);
        System.out.println("average number of images in level 3: " + averageNumberImages3);
    }

    public static void main(String[] args) throws Exception {
        DatasetCalculator calculator = new DatasetCalculator();
        calculator.calculateLength(prop.getProperty("queries"));
        calculator.calculateLengthMulti(prop.getProperty("queries.extended"));
    }
}
