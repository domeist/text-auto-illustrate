#!/usr/bin/env python3
"""Build the small demo corpus that ships with the repository.

The full WIT-derived corpus is 5.4 million captioned images and 1.6 GB, which is
far too large to distribute. This script produces a subset that still exercises
the whole pipeline: every image named in the evaluation sets, plus a random
sample of others to act as distractors.

Scores measured against the demo corpus are higher than against the full one --
there is far less to sift through -- so it is a demonstration, not a
reproduction of the published figures. Run it against the full corpus:

    python3 tools/build_demo_corpus.py \
        --corpus /path/to/wit/formatted \
        --evaluation data/evaluation/strict.tsv \
        --output data/corpus/demo.tsv.gz
"""

from __future__ import annotations

import argparse
import gzip
import random
from pathlib import Path

DEFAULT_DISTRACTORS = 200_000
DEFAULT_SEED = 20220401


def evaluation_files(paths: list[Path]) -> list[Path]:
    """Expand the given paths into a list of evaluation .tsv files."""
    files: list[Path] = []
    for path in paths:
        if path.is_dir():
            files.extend(sorted(path.glob("*.tsv")))
        elif path.is_file():
            files.append(path)
        else:
            raise SystemExit(f"evaluation path not found: {path}")
    if not files:
        raise SystemExit("no evaluation files given")
    return files


def required_ids(paths: list[Path]) -> set[str]:
    """Collect every document id referenced by the given evaluation sets."""
    ids: set[str] = set()
    for path in evaluation_files(paths):
        with path.open(encoding="utf-8") as handle:
            for line in handle:
                columns = line.rstrip("\n").split("\t")
                for column in columns[2:]:
                    ids.update(part.strip() for part in column.split(",") if part.strip())
    return ids


def corpus_files(corpus_dir: Path) -> list[Path]:
    files = sorted(p for p in corpus_dir.iterdir() if p.suffix == ".tsv")
    if not files:
        raise SystemExit(f"no .tsv files found in {corpus_dir}")
    return files


def build(corpus_dir: Path, evaluation: list[Path], output: Path,
          distractors: int, seed: int) -> None:
    wanted = required_ids(evaluation)
    print(f"evaluation sets reference {len(wanted):,} distinct images")

    rng = random.Random(seed)
    kept_required: list[str] = []
    sampled: list[str] = []
    seen = 0

    for path in corpus_files(corpus_dir):
        print(f"  scanning {path.name}")
        with path.open(encoding="utf-8") as handle:
            for line in handle:
                doc_id = line.split("\t", 1)[0]
                if doc_id in wanted:
                    kept_required.append(line)
                    continue
                # Reservoir sampling: one pass, no need to hold the corpus in memory.
                seen += 1
                if len(sampled) < distractors:
                    sampled.append(line)
                else:
                    j = rng.randrange(seen)
                    if j < distractors:
                        sampled[j] = line

    missing = len(wanted) - len(kept_required)
    if missing:
        print(f"warning: {missing:,} referenced ids were not found in the corpus")

    output.parent.mkdir(parents=True, exist_ok=True)
    rows = kept_required + sampled
    rng.shuffle(rows)
    with gzip.open(output, "wt", encoding="utf-8", compresslevel=9) as out:
        out.writelines(rows)

    print(f"wrote {output} — {len(rows):,} images "
          f"({len(kept_required):,} judged, {len(sampled):,} distractors)")
    print(f"size: {output.stat().st_size / 1_048_576:.1f} MB")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--corpus", type=Path, required=True,
                        help="directory of formatted WIT .tsv files")
    parser.add_argument("--evaluation", type=Path, nargs="+",
                        default=[Path("data/evaluation/strict.tsv")],
                        help="evaluation set files or directories whose ids must be kept "
                             "(default: the strict set only -- the lenient set judges "
                             "285,611 images and will not fit in a demo corpus)")
    parser.add_argument("--output", type=Path, default=Path("data/corpus/demo.tsv.gz"))
    parser.add_argument("--distractors", type=int, default=DEFAULT_DISTRACTORS,
                        help=f"non-relevant images to include (default {DEFAULT_DISTRACTORS:,})")
    parser.add_argument("--seed", type=int, default=DEFAULT_SEED,
                        help="random seed, so the corpus is reproducible")
    args = parser.parse_args()
    build(args.corpus, args.evaluation, args.output, args.distractors, args.seed)


if __name__ == "__main__":
    main()
