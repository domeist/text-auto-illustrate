#!/usr/bin/env python3
"""Rebuild the lenient evaluation set.

The strict set was annotated by hand and is small. The lenient set was generated
instead, on the assumption that images near a passage's own Wikipedia article
are plausible illustrations for it. Each passage gets three grades:

    3  the image WIT originally paired with the passage
    2  every other image on the same article
    1  every image on an article linked from it

Grade 1 is deliberately generous — tens of thousands of images per passage — so
the set measures something much weaker than the strict one. Read the two
together: strict understates quality because its judgements are incomplete,
lenient overstates it because its judgements are loose.

    pip install wikipedia
    python3 tools/build_lenient_set.py \
        --articles data/evaluation/source-articles.tsv \
        --strict data/evaluation/strict.tsv \
        --corpus /path/to/corpus \
        --output data/evaluation/lenient.tsv

This reconstructs the 2022 procedure, which used the `wikipedia` package the
same way. Wikipedia changes, so a rebuild today will not reproduce the shipped
file exactly; the committed set remains the one the published results used.
"""

from __future__ import annotations

import argparse
import gzip
import sys
from collections import defaultdict
from pathlib import Path

WIKI_PREFIX = "/wiki/"


def open_maybe_gzip(path: Path):
    if path.suffix == ".gz":
        return gzip.open(path, "rt", encoding="utf-8")
    return path.open(encoding="utf-8")


def corpus_files(corpus: Path) -> list[Path]:
    if corpus.is_file():
        return [corpus]
    files = sorted(p for p in corpus.iterdir() if p.name.endswith((".tsv", ".tsv.gz")))
    if not files:
        raise SystemExit(f"no corpus files found in {corpus}")
    return files


def article_of(page_url: str) -> str:
    """The article title a corpus row belongs to, as it appears in a URL."""
    marker = page_url.rfind(WIKI_PREFIX)
    return page_url[marker + len(WIKI_PREFIX):] if marker >= 0 else ""


def read_articles(path: Path) -> list[tuple[str, str, str]]:
    """Rows of (passage id, article title, grade-3 image id)."""
    out = []
    for line in path.open(encoding="utf-8"):
        parts = line.rstrip("\n").split("\t")
        if len(parts) >= 4:
            out.append((parts[0], parts[1], parts[3]))
    return out


def read_passages(path: Path) -> dict[str, str]:
    return {p[0]: p[1] for p in
            (l.rstrip("\n").split("\t") for l in path.open(encoding="utf-8")) if len(p) >= 2}


def linked_articles(title: str) -> set[str]:
    """Titles of articles linked from `title`, as URL slugs."""
    try:
        import wikipedia
    except ImportError:
        raise SystemExit("this script needs the wikipedia package: pip install wikipedia")
    try:
        links = wikipedia.page(title, auto_suggest=False).links
    except Exception as error:  # disambiguation, redirect, network, removal
        print(f"  ! could not read links for {title!r}: {type(error).__name__}", file=sys.stderr)
        return set()
    return {link.replace(" ", "_") for link in links}


def index_corpus(corpus: Path) -> dict[str, list[str]]:
    """Article slug -> ids of every corpus image on that article."""
    by_article: dict[str, list[str]] = defaultdict(list)
    for file in corpus_files(corpus):
        print(f"  scanning {file.name}")
        with open_maybe_gzip(file) as handle:
            for line in handle:
                parts = line.split("\t", 3)
                if len(parts) >= 3:
                    by_article[article_of(parts[1])].append(parts[0])
    return by_article


def build(articles: Path, strict: Path, corpus: Path, output: Path) -> None:
    sources = read_articles(articles)
    passages = read_passages(strict)
    by_article = index_corpus(corpus)
    print(f"corpus covers {len(by_article):,} articles")

    with output.open("w", encoding="utf-8") as out:
        for passage_id, title, seed_image in sources:
            slug = title.replace(" ", "_")
            same = [i for i in by_article.get(slug, []) if i != seed_image]

            print(f"  {title}: fetching links")
            linked = linked_articles(title)
            nearby: list[str] = []
            for other in linked:
                nearby.extend(by_article.get(other, []))

            seen = {seed_image, *same}
            nearby = [i for i in dict.fromkeys(nearby) if i not in seen]

            out.write("\t".join([passage_id, passages.get(passage_id, ""),
                                 seed_image, ",".join(same), ",".join(nearby)]) + "\n")
            print(f"    grade 3: 1   grade 2: {len(same):,}   grade 1: {len(nearby):,}")
    print(f"\nwrote {output}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--articles", type=Path,
                        default=Path("data/evaluation/source-articles.tsv"))
    parser.add_argument("--strict", type=Path, default=Path("data/evaluation/strict.tsv"))
    parser.add_argument("--corpus", type=Path, required=True,
                        help="corpus directory, or a single .tsv/.tsv.gz file")
    parser.add_argument("--output", type=Path, default=Path("data/evaluation/lenient.tsv"))
    args = parser.parse_args()
    build(args.articles, args.strict, args.corpus, args.output)


if __name__ == "__main__":
    main()
