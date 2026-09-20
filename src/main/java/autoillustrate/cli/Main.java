package autoillustrate.cli;

import autoillustrate.eval.Evaluation;
import autoillustrate.eval.EvaluationSet;
import autoillustrate.eval.ParameterSweep;
import autoillustrate.index.CorpusIndexer;
import autoillustrate.retrieve.Bm25Retriever;
import autoillustrate.retrieve.Result;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Command-line entry point.
 *
 * <p>Every workflow is reachable from here, so nothing requires an IDE to run.
 */
public final class Main {

    private static final Path DEFAULT_INDEX = Path.of("index");
    private static final Path DEFAULT_CORPUS = Path.of("data/corpus");
    private static final Path STRICT_SET = Path.of("data/evaluation/strict.tsv");
    private static final Path LENIENT_SET = Path.of("data/evaluation/lenient.tsv");

    public static void main(String[] args) {
        if (args.length == 0) {
            usage();
            System.exit(2);
        }
        try {
            switch (args[0]) {
                case "index" -> index(args);
                case "search" -> search(args);
                case "evaluate" -> evaluate(args);
                case "sweep" -> sweep(args);
                case "help", "--help", "-h" -> usage();
                default -> {
                    System.err.println("unknown command: " + args[0]);
                    usage();
                    System.exit(2);
                }
            }
        } catch (Exception e) {
            System.err.println("error: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void index(String[] args) throws Exception {
        Map<String, String> options = Options.parse(args);
        Path corpus = options.containsKey("corpus") ? Path.of(options.get("corpus")) : DEFAULT_CORPUS;
        Path index = options.containsKey("index") ? Path.of(options.get("index")) : DEFAULT_INDEX;
        System.out.printf("indexing %s into %s%n", corpus, index);
        long start = System.currentTimeMillis();
        long count = new CorpusIndexer(index).indexDirectory(corpus);
        System.out.printf("indexed %,d images in %.1fs%n",
                count, (System.currentTimeMillis() - start) / 1000.0);
    }

    private static void search(String[] args) throws Exception {
        Map<String, String> options = Options.parse(args);
        String text = options.get("text");
        if (text == null) {
            if (options.containsKey("file")) {
                text = Files.readString(Path.of(options.get("file")));
            } else {
                throw new IllegalArgumentException("search needs --text \"...\" or --file <path>");
            }
        }
        Path index = options.containsKey("index") ? Path.of(options.get("index")) : DEFAULT_INDEX;
        int limit = Options.intValue(options, "limit", 10);
        try (Bm25Retriever retriever = new Bm25Retriever(index)) {
            System.out.println("query terms: " + String.join(" ", retriever.queryTermsFor(text)));
            List<Result> results = retriever.retrieve(text, limit);
            if (results.isEmpty()) {
                System.out.println("no matches");
                return;
            }
            for (int i = 0; i < results.size(); i++) {
                Result result = results.get(i);
                System.out.printf("%n%d. %s%n   %s%n   %s%n",
                        i + 1, result.caption(), result.imageUrl(), result.pageUrl());
            }
        }
    }

    private static void evaluate(String[] args) throws Exception {
        Map<String, String> options = Options.parse(args);
        Path index = options.containsKey("index") ? Path.of(options.get("index")) : DEFAULT_INDEX;
        Path setPath = resolveSet(options.getOrDefault("set", "strict"));
        int terms = Options.intValue(options, "terms", Bm25Retriever.DEFAULT_QUERY_TERMS);
        float k1 = Options.floatValue(options, "k1", Bm25Retriever.DEFAULT_K1);
        float b = Options.floatValue(options, "b", Bm25Retriever.DEFAULT_B);
        boolean verbose = options.containsKey("verbose");

        EvaluationSet set = EvaluationSet.load(setPath);
        try (Bm25Retriever retriever = Bm25Retriever.withParameters(index, terms, k1, b)) {
            Evaluation.Report report =
                    Evaluation.run(retriever, set, Evaluation.DEFAULT_DEPTH, verbose);
            System.out.println();
            System.out.print(report.format());
        }
    }

    private static void sweep(String[] args) throws Exception {
        Map<String, String> options = Options.parse(args);
        Path index = options.containsKey("index") ? Path.of(options.get("index")) : DEFAULT_INDEX;
        EvaluationSet set = EvaluationSet.load(resolveSet(options.getOrDefault("set", "strict")));
        Path outDir = Path.of(options.getOrDefault("out", "results"));
        String what = options.getOrDefault("what", "bm25");
        switch (what) {
            case "bm25" -> ParameterSweep.sweepBm25(index, set,
                    Options.intValue(options, "terms", Bm25Retriever.DEFAULT_QUERY_TERMS),
                    outDir.resolve("bm25-sweep.csv"));
            case "terms" -> ParameterSweep.sweepQueryLength(index, set,
                    outDir.resolve("query-length-sweep.csv"));
            default -> throw new IllegalArgumentException("--what must be bm25 or terms");
        }
    }

    private static Path resolveSet(String name) {
        return switch (name) {
            case "strict" -> STRICT_SET;
            case "lenient" -> LENIENT_SET;
            default -> Path.of(name);
        };
    }

    private static void usage() {
        System.out.print("""
                Text Auto Illustrate — finds images that suit a passage of text.

                Usage: text-auto-illustrate <command> [options]

                Commands:
                  index      Build a search index from a corpus of captioned images
                               --corpus <dir>   corpus directory      (default data/corpus)
                               --index  <dir>   index destination     (default index)

                  search     Find images for a passage of text
                               --text "<passage>" | --file <path>
                               --limit <n>      results to show       (default 10)
                               --index  <dir>

                  evaluate   Score the retriever against an evaluation set
                               --set strict|lenient|<path>            (default strict)
                               --terms <n>      query terms           (default 10)
                               --k1 <f> --b <f> BM25 parameters       (default 1.2, 0.75)
                               --verbose        show per-passage hits
                               --index  <dir>

                  sweep      Score across a grid of settings, writing CSV
                               --what bm25|terms                      (default bm25)
                               --set strict|lenient|<path>
                               --out <dir>      output directory      (default results)

                Examples:
                  text-auto-illustrate index --corpus data/corpus
                  text-auto-illustrate search --text "The frieze is the wide central section..."
                  text-auto-illustrate evaluate --set strict --verbose
                """);
    }
}
