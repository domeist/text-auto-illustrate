# Text Auto Illustrate

Finds images that illustrate a passage of text.

Give it a paragraph and it extracts the words that best characterise it, searches
an index of 5.4 million captioned Wikipedia images, and returns the ones that fit.

```
$ text-auto-illustrate search --text "In architecture, the frieze is the wide
  central section of an entablature, often decorated with bas-reliefs."

query terms: entablatur friez ba decor relief wide architectur section central

1. The Circus, Bath, UK. Architectural detail of the frieze showing the
   alternating triglyphs and decorative emblems. John Wood, architect.
   https://upload.wikimedia.org/wikipedia/commons/4/43/Decorative_emblems_The_Circus_Bath.jpg
   https://en.wikipedia.org/wiki/Circus_(Bath)
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

| Set | Passages | Judgements | Notes |
|---|---|---|---|
| `strict.tsv` | 25 | 722 images, binary | Annotated by hand |
| `lenient.tsv` | 25 | 285,611 images, 3 grades | Generated, much broader |

```bash
java -jar target/text-auto-illustrate.jar evaluate --set strict --verbose
```

### Results on the full corpus

Measured against all 5,411,977 images, BM25 with `k1=1.2`, `b=0.75`, 10 query
terms, top 100 results.

| | strict | lenient |
|---|---|---|
| Precision@5 | 0.136 | 0.272 |
| Recall@100 | 0.207 | 0.005 (ceiling 0.047) |
| MRR | 0.218 | 0.445 |
| MAP@100 | 0.091 | 0.036 |
| nDCG@100 | — | 0.116 |

### Reading those numbers honestly

**The scores understate the system.** 17 of the 25 strict passages score zero at
rank 5, but inspecting them shows the retrieved images are often clearly
appropriate — they simply are not in the hand-written answer key. A paragraph
about fur-trade forts returns Hudson's Bay Company trading posts and scores
nothing. The strict ground truth is incomplete, and the lenient set exists to
compensate.

**Lenient recall cannot approach 1.** That set judges roughly 11,400 images
relevant per passage while only 100 are ever retrieved, capping recall at 0.047.
The reported figure of 0.005 is about a tenth of what is attainable, not a
catastrophe. `evaluate` prints the ceiling alongside the score for this reason.

**Ties dominate.** Captions are short, so 51% of retrieved results share a BM25
score with another result. Ordering ties differently moves Precision@5 between
0.120 and 0.152 — a range that covers most of the variation the 2022 parameter
sweep attributed to `k1` and `b`. Results are sorted by score and then by
document id so that repeated runs agree, but the absolute values carry real
uncertainty, and small differences between configurations are not meaningful.

## Demo corpus vs. full corpus

The full corpus is 1.6 GB of captions and the index another 1.6 GB, which is too
large to distribute. The bundled demo corpus contains every image named in the
strict evaluation set plus 200,000 randomly sampled distractors.

Scores against it are roughly **double** the full-corpus figures — the answers
are all still present, but there are 27× fewer wrong answers to sift through.

| | demo (200,722) | full (5,411,977) |
|---|---|---|
| Precision@5 | 0.320 | 0.136 |
| MRR | 0.456 | 0.218 |
| Index build | 3 seconds | 110 seconds |

It demonstrates the pipeline; it does not reproduce the published results. The
lenient set needs the full corpus, since it judges more images than the demo
corpus contains.

### Building the full corpus

The corpus derives from Google's [WIT](https://github.com/google-research-datasets/wit)
dataset. Download the training split, then:

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

The BM25 grid spans `k1` up to 2.0 deliberately. The original study stopped at
1.0 and so never tested Lucene's default of 1.2, which turned out to match or
beat everything it did test.

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

[MIT](LICENSE).

The 2022 version was built on [Luc4IR](https://github.com/gdebasis/luc4ir), a
teaching framework by Debasis Ganguly, who supervised the project. The 2026
rewrite shares no code with it.
