#!/usr/bin/env python3
"""Convert Google's WIT dataset into the corpus format this project indexes.

WIT ships as gzipped TSV with many columns describing Wikipedia images in over a
hundred languages. This keeps the English rows and reduces each to the four
columns the indexer reads:

    id <TAB> page_url <TAB> image_url <TAB> caption

Captions are taken from the first available of the attribution, reference and
alt-text descriptions, matching the 2022 dataset build.

    python3 tools/format_wit.py --input ~/wit --output data/corpus

Download the training split first:
https://github.com/google-research-datasets/wit
"""

from __future__ import annotations

import argparse
import csv
import gzip
import sys
from pathlib import Path

CAPTION_COLUMNS = (
    "caption_attribution_description",
    "caption_reference_description",
    "caption_alt_text_description",
)
REQUIRED_COLUMNS = ("language", "page_url", "image_url")

# WIT captions can be long; the default field limit rejects some rows outright.
csv.field_size_limit(min(sys.maxsize, 2**31 - 1))


def open_maybe_gzip(path: Path):
    if path.suffix == ".gz":
        return gzip.open(path, "rt", encoding="utf-8", newline="")
    return path.open(encoding="utf-8", newline="")


def caption_for(row: dict[str, str]) -> str:
    """First non-empty caption, newlines flattened so the row stays one line."""
    for column in CAPTION_COLUMNS:
        value = (row.get(column) or "").strip()
        if value:
            return " ".join(value.split())
    return ""


def input_files(directory: Path) -> list[Path]:
    files = sorted(p for p in directory.iterdir()
                   if p.name.endswith((".tsv", ".tsv.gz")))
    if not files:
        raise SystemExit(f"no .tsv or .tsv.gz files found in {directory}")
    return files


def convert(input_dir: Path, output_dir: Path, language: str) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    next_id = 0
    total_kept = 0
    total_skipped = 0

    for source in input_files(input_dir):
        destination = output_dir / (source.name.split(".")[0] + "_formatted.tsv")
        kept = skipped = 0
        with open_maybe_gzip(source) as handle, \
                destination.open("w", encoding="utf-8") as out:
            reader = csv.DictReader(handle, delimiter="\t")
            missing = [c for c in REQUIRED_COLUMNS if c not in (reader.fieldnames or [])]
            if missing:
                raise SystemExit(f"{source.name} is missing columns: {', '.join(missing)}")
            for row in reader:
                if row.get("language") != language:
                    skipped += 1
                    continue
                caption = caption_for(row)
                if not caption:
                    skipped += 1
                    continue
                out.write(f"{next_id}\t{row['page_url']}\t{row['image_url']}\t{caption}\n")
                next_id += 1
                kept += 1
        print(f"  {source.name} -> {destination.name}: {kept:,} kept, {skipped:,} skipped")
        total_kept += kept
        total_skipped += skipped

    print(f"\nwrote {total_kept:,} images to {output_dir} ({total_skipped:,} skipped)")
    print("ids are assigned sequentially across files in sorted order, so the "
          "evaluation sets only line up if the whole split is converted at once")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--input", type=Path, required=True,
                        help="directory of downloaded WIT .tsv or .tsv.gz files")
    parser.add_argument("--output", type=Path, default=Path("data/corpus"))
    parser.add_argument("--language", default="en",
                        help="WIT language code to keep (default: en)")
    args = parser.parse_args()
    convert(args.input, args.output, args.language)


if __name__ == "__main__":
    main()
