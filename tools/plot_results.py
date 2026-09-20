#!/usr/bin/env python3
"""Plot the CSV files written by the `sweep` command.

    java -jar target/text-auto-illustrate.jar sweep --what bm25  --out results
    java -jar target/text-auto-illustrate.jar sweep --what terms --out results
    python3 tools/plot_results.py --results results --output results/plots

Every chart it can build from the files present is written; nothing needs to be
uncommented to choose one.
"""

from __future__ import annotations

import argparse
import csv
from collections import defaultdict
from pathlib import Path

try:
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
except ImportError:  # pragma: no cover - depends on the environment
    raise SystemExit("matplotlib is required: pip install -r tools/requirements.txt")

MEASURES = {
    "p_at_5": "Precision@5",
    "recall": "Recall@100",
    "mrr": "MRR",
    "map": "MAP",
}


def read_rows(path: Path) -> list[dict[str, str]]:
    with path.open(encoding="utf-8", newline="") as handle:
        return list(csv.DictReader(handle))


def plot_bm25(path: Path, output_dir: Path) -> int:
    """One chart per measure: b on the x axis, a line per k1 value."""
    rows = read_rows(path)
    written = 0
    for column, label in MEASURES.items():
        series: dict[str, list[tuple[float, float]]] = defaultdict(list)
        for row in rows:
            series[row["k1"]].append((float(row["b"]), float(row[column])))

        figure, axes = plt.subplots(figsize=(7, 4.5))
        for k1 in sorted(series, key=float):
            points = sorted(series[k1])
            axes.plot([p[0] for p in points], [p[1] for p in points],
                      marker="o", label=f"k1={k1}")
        axes.set_xlabel("b (caption length normalisation)")
        axes.set_ylabel(label)
        axes.set_title(f"{label} across BM25 settings")
        axes.legend(fontsize="small")
        axes.grid(alpha=0.3)
        figure.tight_layout()
        destination = output_dir / f"bm25-{column}.png"
        figure.savefig(destination, dpi=150)
        plt.close(figure)
        print(f"wrote {destination}")
        written += 1
    return written


def plot_query_length(path: Path, output_dir: Path) -> int:
    """A single chart with every measure against the number of query terms."""
    rows = read_rows(path)
    terms = [int(row["query_terms"]) for row in rows]

    figure, axes = plt.subplots(figsize=(7, 4.5))
    for column, label in MEASURES.items():
        axes.plot(terms, [float(row[column]) for row in rows], marker="o", label=label)
    axes.set_xlabel("Query terms extracted from the passage")
    axes.set_ylabel("Score")
    axes.set_title("Retrieval quality against query length")
    axes.set_xticks(terms)
    axes.legend(fontsize="small")
    axes.grid(alpha=0.3)
    figure.tight_layout()
    destination = output_dir / "query-length.png"
    figure.savefig(destination, dpi=150)
    plt.close(figure)
    print(f"wrote {destination}")
    return 1


def plot_title_boost(path: Path, output_dir: Path) -> int:
    """How much weighting article-title matches is worth."""
    rows = read_rows(path)
    boosts = [float(row["title_boost"]) for row in rows]

    figure, axes = plt.subplots(figsize=(7, 4.5))
    for column, label in MEASURES.items():
        axes.plot(boosts, [float(row[column]) for row in rows], marker="o", label=label)
    axes.axvline(1.0, color="grey", linestyle=":", linewidth=1)
    axes.annotate("shipped default", xy=(1.0, axes.get_ylim()[1]),
                  xytext=(4, -12), textcoords="offset points",
                  fontsize="small", color="grey")
    axes.set_xlabel("Weight given to article-title matches (0 = captions only)")
    axes.set_ylabel("Score")
    axes.set_title("Searching the article title as well as the caption")
    axes.set_xticks(boosts)
    axes.legend(fontsize="small")
    axes.grid(alpha=0.3)
    figure.tight_layout()
    destination = output_dir / "title-boost.png"
    figure.savefig(destination, dpi=150)
    plt.close(figure)
    print(f"wrote {destination}")
    return 1


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--results", type=Path, default=Path("results"),
                        help="directory holding the sweep CSV files")
    parser.add_argument("--output", type=Path, default=None,
                        help="where to write the charts (default: <results>/plots)")
    args = parser.parse_args()

    output_dir = args.output or args.results / "plots"
    output_dir.mkdir(parents=True, exist_ok=True)

    written = 0
    bm25 = args.results / "bm25-sweep.csv"
    if bm25.exists():
        written += plot_bm25(bm25, output_dir)
    query_length = args.results / "query-length-sweep.csv"
    if query_length.exists():
        written += plot_query_length(query_length, output_dir)
    title_boost = args.results / "title-boost-sweep.csv"
    if title_boost.exists():
        written += plot_title_boost(title_boost, output_dir)

    if written == 0:
        raise SystemExit(f"no sweep CSV files found in {args.results} — run the sweep command first")


if __name__ == "__main__":
    main()
