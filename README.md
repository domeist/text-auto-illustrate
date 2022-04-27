# Text Auto Illustrate

## Project description

The project’s goal was to build a system that extracts the query terms from the text section, performs the query, and returns images that accurately represent the paragraph provided by the user. The text section could either be a pre-defined paragraph or a passage the user is writing in real-time. It allows the system to recommend relevant images to the user in real-time, which they can include in their work.

The main focus areas of the project were extracting the information need from some context and effectively retrieving the content of different modalities. The former represents a challenge to retrieve a fixed number of query terms from a long paragraph such that they accurately represent the text section. The second part relates to querying the index using previously retrieved query terms and applying appropriate retrieval techniques to recover information from different modalities.

We have also built two evaluation datasets to measure our model’s performance. The first dataset was built manually, while the second was built using an automated approach and contained a broader image ground truth. These datasets can be used by other researchers working on similar information retrieval projects.

## Build instructions

Because some files (the dataset and the index) were too big to keep on Github repository, we included them elsewhere. You can download them [here](https://gla-my.sharepoint.com/:f:/g/personal/2404288m_student_gla_ac_uk/ErF52dM4Y4VEsZ10lvoY6kQBqTdjA--PwJTKcnh95BQZ9Q?e=e8betH).

The downloaded folders `index` and `data` should be placed inside the directory `text-auto-illustrate/src/`

### Building the index

This step is only necessary if we only have `data` but not `index`. In order to build the index, from the directory `text-auto-illustrate/src/msmarco/` we run the command:

```
$ ./index_msmarco.sh
```

### Building the project

After adding the folders `index` and `data`, run these commands in terminal from the directory `text-auto-illustrate/src/`:

```
$ mvn clean install
$ mvn compile
```

To run the program on the first evaluation dataset (the strict ground truth), run the `RetrieverNew.java` using IDE.

To run the program on the first evaluation dataset (the strict ground truth) but in a different workflow, run the `Retriever.java` using IDE.

To run the program on the second evaluation dataset (the lenient ground truth), run the `RetrieverMulti.java` using IDE.

To evaluate parameters, run `Evaluator.java` using IDE.

## Requirements

Software and packets used in the project:

* Java 17.0.1
* Apache Maven 3.8.4
* Tested on Windows 10 using WSL 2 (Ubuntu)
