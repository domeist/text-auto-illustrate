# Text Auto Illustrate

Finds images that illustrate a passage of text.

Give it a paragraph and it extracts the words that best characterise it, searches
an index of 5.4 million captioned Wikipedia images, and returns the ones that fit.

```
$ text-auto-illustrate search --text "In architecture, the frieze is the wide
  central section of an entablature, often decorated with bas-reliefs."

query terms: entablatur friez ba decor relief wide architectur section central

1. Sankissa elephant frieze
   https://upload.wikimedia.org/wikipedia/commons/1/14/Sankissa_elephant_frieze.jpg
   https://en.wikipedia.org/wiki/Frieze

2. Frieze from Delphi, lotus with multiple calyx. Treasury of the Siphnians,
   525 BCE.
   https://upload.wikimedia.org/wikipedia/commons/8/83/Frieze_from_Delphi_lotus_with_multiple_calyx.jpg
   https://en.wikipedia.org/wiki/Frieze
```

University of Glasgow final-year project, BSc Computer Science, 2022 — rebuilt in 2026.

---

## Try it in about a minute

Needs **Java 17+** and **Maven**. Nothing else, and no dataset download.

```bash
git clone https://github.com/<you>/text-auto-illustrate.git
cd text-auto-illustrate

mvn package                                                  # build and test
java -jar target/text-auto-illustrate.jar index              # ~3 seconds
java -jar target/text-auto-illustrate.jar search --text "Your paragraph here."
```

The repository ships a **demo corpus** of 200,722 captioned images, so there is
nothing to download and the index builds in seconds. See
[Demo corpus vs. full corpus](#demo-corpus-vs-full-corpus) for what that costs
you in accuracy.

## How it works

The project splits into two problems.

**Extracting the information need.** A paragraph is far too long to use as a
search query, so each word is scored by how often it appears in the passage
multiplied by how rare it is across the corpus. Rare words carry more signal —
*entablature* identifies a subject in a way *building* does not — and the
top-scoring words become the query.

**Cross-modal retrieval.** Every image in the corpus carries its Wikipedia
caption. Searching those captions with BM25 matches text against text, and
returns an image as a side effect. Nothing ever looks at the pixels.

Two fields are searched: the caption, and the title of the article the image
came from. Captions are often uninformative — *"scanned at 300 dpi from the
original plate"* names no subject — while the article title usually names it
exactly. Weight the title with `--title-boost`; zero searches captions alone.

There is no machine learning here and nothing is trained. BM25 is a fixed
ranking formula, and the relevance judgements are used only to score results
after the fact.

## Commands

| Command | What it does |
|---|---|
| `index` | Builds the search index from a corpus of captioned images |
| `search` | Returns images suiting a passage (`--text` or `--file`) |
| `evaluate` | Scores the retriever against an evaluation set |
| `sweep` | Scores across a grid of settings, writing CSV for plotting |

Run `java -jar target/text-auto-illustrate.jar help` for the full options.

## Evaluation

Measuring this needed ground truth that did not exist, so the project includes
two hand-built evaluation sets, in `data/evaluation/`. Both are reusable by
anyone working on similar problems.

| Set | Passages | Judgements | Per passage | Notes |
|---|---|---|---|---|
| `strict.tsv` | 25 | 722 images, relevant or not | 28 on average | Annotated by hand |
| `lenient.tsv` | 25 | 83,332 images, three grades | 4,421 on average | Generated, much broader |

Each row is a passage and the images judged to suit it:

```
0    In architecture, the frieze is the wide central...    0,4006352,2824798,...
```

The ids refer to rows of the corpus, so a set only means anything alongside the
corpus it was built against.

```bash
java -jar target/text-auto-illustrate.jar evaluate --set strict --verbose
```

### Results on the full corpus

Measured against all 5,411,977 images, BM25 with `k1=1.2`, `b=0.75`, 10 query
terms, title boost 1.0, top 100 results.

| | strict | lenient |
|---|---|---|
| Precision@5 | 0.176 | 0.432 |
| Recall@100 | 0.296 | 0.006 (ceiling 0.047) |
| MRR | 0.248 | 0.673 |
| MAP@100 | 0.137 | 0.063 |
| nDCG@100 | — | 0.163 |
| Passages scoring zero at rank 5 | 16 of 25 | 4 of 25 |

### What searching the article title is worth

The 2022 version searched captions only. Adding the title field, on the strict
set over the full corpus:

| | captions only | with titles |
|---|---|---|
| Precision@5 | 0.136 | **0.176** |
| Recall@100 | 0.207 | **0.296** |
| MAP@100 | 0.091 | **0.137** |
| Results sharing a score | 71% | **39%** |

The recall gain is the most trustworthy of these, since it depends on whether
relevant images appear at all rather than exactly where. Halving the proportion
of tied scores also makes the rank-sensitive measures steadier.

### Reading those numbers honestly

**The scores understate the system.** 16 of the 25 strict passages score zero at
rank 5, but inspecting them shows the retrieved images are often clearly
appropriate — they simply are not in the hand-written answer key. A paragraph
about fur-trade forts returns Hudson's Bay Company trading posts and scores
nothing. The strict ground truth is incomplete, and the lenient set exists to
compensate.

**Lenient recall cannot approach 1.** That set judges about 4,400 images
relevant per passage while only 100 are ever retrieved, capping recall at 0.047.
The reported 0.006 is roughly an eighth of what is attainable, not a
catastrophe. `evaluate` prints the ceiling beside the score for this reason.

**Ties used to dominate, and no longer do.** Captions are short, so many results
share an identical BM25 score: 71% searching captions alone, 39% once titles are
searched too. That mattered, because tied results are ordered arbitrarily.
Searching captions alone, reordering ties moved Precision@5 between 0.120 and
0.152 — a range covering most of the variation the 2022 sweep attributed to `k1`
and `b`, meaning that study largely measured noise. With titles searched, the
same three orderings give identical scores at the default settings. Results are
still sorted by score and then document id so runs agree exactly.

## Demo corpus vs. full corpus

The full corpus is 1.6 GB of captions and the index another 1.6 GB, which is too
large to distribute. The bundled demo corpus contains every image named in the
strict evaluation set plus 200,000 randomly sampled distractors.

Scores against it are roughly **double** the full-corpus figures — the answers
are all still present, but there are 27× fewer wrong answers to sift through.

| | demo (200,722) | full (5,411,977) |
|---|---|---|
| Precision@5 | 0.424 | 0.176 |
| Recall@100 | 0.488 | 0.296 |
| MRR | 0.636 | 0.248 |
| Index build | 3 seconds | 137 seconds |

It demonstrates the pipeline; it does not reproduce the published results.

The demo corpus is built from the strict set's judgements only, so scoring the
lenient set against it is meaningless — most of what that set judges relevant is
simply absent. Use the full corpus for lenient, or rebuild the demo corpus
passing both sets to `tools/build_demo_corpus.py`.

### Building the full corpus

The corpus derives from Google's [WIT](https://github.com/google-research-datasets/wit)
dataset (CC BY-SA 3.0). The full English corpus — all 5,411,977 images, 565 MB
gzipped — is attached to the [latest release](../../releases/latest):

```bash
curl -L -o data/corpus/wit-corpus-en.tsv.gz \
  https://github.com/domeist/text-auto-illustrate/releases/latest/download/wit-corpus-en.tsv.gz
rm data/corpus/demo.tsv.gz          # or index both; the demo is a subset
java -jar target/text-auto-illustrate.jar index --corpus data/corpus --index index
```

Indexing all 5.4 million takes about 95 seconds and produces a 1.4 GB index.
The figures in [Results on the full corpus](#results-on-the-full-corpus) were
measured from exactly this archive.

To rebuild the corpus from WIT yourself instead, download the training split
and run:

```bash
python3 tools/format_wit.py --input <wit tsv files> --output data/corpus
java -jar target/text-auto-illustrate.jar index --corpus data/corpus --index index
```

Rebuild the demo corpus from the full one with `tools/build_demo_corpus.py`.

## Reproducing the experiments

```bash
java -jar target/text-auto-illustrate.jar sweep --what bm25  --out results
java -jar target/text-auto-illustrate.jar sweep --what terms --out results

python3 -m venv .venv
.venv/bin/pip install -r tools/requirements.txt
.venv/bin/python tools/plot_results.py --results results
```

Charts land in `results/plots/`. The virtual environment is not optional on
recent Debian and Ubuntu, where a plain `pip install` is refused.

The grid spans `k1` up to 2.0 deliberately. The 2022 study stopped at 1.0 and so
never tested Lucene's own default of 1.2.

On the full corpus with titles searched, the best cell is `k1=2.0, b=0.4` at
Precision@5 0.192, against 0.176 for the shipped defaults. That gap survives
tie-reordering, so it is not the artefact the 2022 sweep was measuring.

**The defaults are kept anyway.** That cell is the best of 36, chosen on 25
passages, and amounts to two additional relevant results in the whole
evaluation. Picking the maximum of a grid on a test set that small is how you
end up reporting a number that does not survive contact with new data. The
sweep is worth running and worth reading; it is not worth tuning to.

## Built with

Java 17 · Apache Lucene 9.12 · Maven · JUnit 5 · Python (tooling only)

## Project layout

```
src/main/java/autoillustrate/
  index/     Building the Lucene index from a corpus file
  query/     Reducing a passage to its most distinctive terms
  retrieve/  The Retriever interface and its BM25 implementation
  eval/      Evaluation sets, measures, and the parameter sweep
  cli/       Command-line entry point
data/
  corpus/    The bundled demo corpus
  evaluation/The two hand-built evaluation sets
tools/       Corpus preparation and plotting
```

`Retriever` is an interface, so an implementation backed by image embeddings
could be dropped in and measured against the same evaluation sets.

## Dissertation

The dissertation, status report and presentation are attached to the
[latest release](../../releases/latest) rather than stored in the repository.

## Licence

Code: [MIT](LICENSE).

Data: [CC BY-SA 3.0](data/LICENSE.md). Everything under `data/` derives from
Wikipedia — via the [WIT dataset][wit] for the corpus, and directly for the
evaluation passages — and carries Wikipedia's share-alike licence rather than
the code licence. Attribution details are in [data/LICENSE.md](data/LICENSE.md).

[wit]: https://github.com/google-research-datasets/wit

The 2022 version was built on [Luc4IR](https://github.com/gdebasis/luc4ir), a
teaching framework by Debasis Ganguly, who supervised the project. The 2026
rewrite shares no code with it.
