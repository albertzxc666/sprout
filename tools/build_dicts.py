#!/usr/bin/env python3
"""
Convert FreeDict TEI XML dictionaries into TransCard JSON format.

Where to get TEI dumps:
  https://freedict.org/freedict-database/
  Download per-pair tarballs (e.g. "fra-rus.tei.tar.xz"), extract somewhere,
  then drop the *.tei file into tools/sources/<from>-<to>/ using FreeDict's
  ISO 639-3 directory naming, e.g.:

    tools/sources/eng-rus/eng-rus.tei
    tools/sources/rus-eng/rus-eng.tei
    tools/sources/fra-rus/fra-rus.tei

Then run:

    python tools/build_dicts.py

Output goes to:
    shared/src/commonMain/composeResources/files/dictionaries/<from>-<to>.json

Where <from>/<to> are ISO 639-1 codes (en/ru/fr/...) matching Space.nativeLang.

The TransCard LocalDictionary loader auto-builds a reverse index, so a single
"eng-rus.json" already covers EN->RU and RU->EN lookups. Generating both
directions is optional but yields better quality (each direction's source
file is curated for that direction).

Only the Python stdlib is used. Tested on Python 3.10+.
"""

from __future__ import annotations

import argparse
import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

# FreeDict uses ISO 639-3 in filenames. We store ISO 639-1 to match
# Space.nativeLang/targetLang in the app (LanguagePair.DEFAULT).
ISO3_TO_ISO1: dict[str, str] = {
    "eng": "en",
    "rus": "ru",
    "deu": "de",
    "ger": "de",  # legacy alias
    "spa": "es",
    "fra": "fr",
    "fre": "fr",  # legacy alias
    "ita": "it",
    "jpn": "ja",
    "zho": "zh",
    "chi": "zh",  # legacy alias
    "por": "pt",
    "tur": "tr",
}

TEI_NS = "{http://www.tei-c.org/ns/1.0}"


def parse_tei(tei_path: Path) -> dict[str, list[str]]:
    """Parse a FreeDict TEI file into {headword_lower: [translations...]}."""
    try:
        tree = ET.parse(tei_path)
    except ET.ParseError as e:
        sys.exit(f"ERROR: failed to parse {tei_path}: {e}")

    root = tree.getroot()
    entries: dict[str, list[str]] = {}

    for entry in root.iter(f"{TEI_NS}entry"):
        # Headwords (sometimes multiple <form>/<orth> per entry).
        headwords: list[str] = []
        for form in entry.findall(f"{TEI_NS}form"):
            for orth in form.findall(f"{TEI_NS}orth"):
                if orth.text:
                    headwords.append(orth.text.strip())
        if not headwords:
            continue

        # Translations: <cit type="trans"><quote>...</quote></cit>
        translations: list[str] = []
        for cit in entry.iter(f"{TEI_NS}cit"):
            if cit.attrib.get("type") != "trans":
                continue
            for quote in cit.findall(f"{TEI_NS}quote"):
                if quote.text:
                    t = quote.text.strip()
                    if t and t not in translations:
                        translations.append(t)
        if not translations:
            continue

        for hw in headwords:
            key = hw.lower()
            existing = entries.setdefault(key, [])
            for t in translations:
                if t not in existing:
                    existing.append(t)

    return entries


def split_pair_name(name: str) -> tuple[str, str] | None:
    """'eng-rus' -> ('en','ru'), or None if codes unknown."""
    parts = name.split("-")
    if len(parts) != 2:
        return None
    src = ISO3_TO_ISO1.get(parts[0].lower())
    dst = ISO3_TO_ISO1.get(parts[1].lower())
    if not src or not dst:
        return None
    return src, dst


def build_one(
    pair_dir: Path,
    out_dir: Path,
    max_entries: int | None,
) -> tuple[Path, int] | None:
    pair = split_pair_name(pair_dir.name)
    if pair is None:
        print(f"  skip: {pair_dir.name} — unknown ISO 639-3 codes")
        return None
    src, dst = pair

    tei_files = sorted(pair_dir.glob("*.tei"))
    if not tei_files:
        print(f"  skip: {pair_dir.name} — no .tei file inside")
        return None

    entries = parse_tei(tei_files[0])
    if not entries:
        print(f"  skip: {pair_dir.name} — TEI parsed but no <entry> with translations found")
        return None

    if max_entries is not None and len(entries) > max_entries:
        # Keep the first N keys (sorted for determinism).
        keys = sorted(entries.keys())[:max_entries]
        entries = {k: entries[k] for k in keys}

    payload = {
        "version": 1,
        "from": src,
        "to": dst,
        "entries": entries,
    }
    out_path = out_dir / f"{src}-{dst}.json"
    out_path.write_text(
        json.dumps(payload, ensure_ascii=False, separators=(",", ":")),
        encoding="utf-8",
    )
    return out_path, len(entries)


def main() -> int:
    here = Path(__file__).resolve().parent
    repo_root = here.parent
    default_sources = here / "sources"
    default_output = repo_root / "shared/src/commonMain/composeResources/files/dictionaries"

    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--sources", type=Path, default=default_sources,
                        help=f"Directory with <iso3>-<iso3>/<file>.tei subdirs (default: {default_sources})")
    parser.add_argument("--output", type=Path, default=default_output,
                        help=f"Output directory for *.json (default: {default_output})")
    parser.add_argument("--max-entries", type=int, default=None,
                        help="Optional cap per dictionary (helpful to keep APK small)")
    args = parser.parse_args()

    if not args.sources.is_dir():
        sys.exit(f"ERROR: sources directory not found: {args.sources}")

    args.output.mkdir(parents=True, exist_ok=True)
    pair_dirs = [p for p in sorted(args.sources.iterdir()) if p.is_dir()]
    if not pair_dirs:
        sys.exit(f"ERROR: no language pair subdirectories in {args.sources}")

    print(f"Sources: {args.sources}")
    print(f"Output:  {args.output}")
    if args.max_entries:
        print(f"Cap per dict: {args.max_entries}")
    print()

    built = 0
    for pair_dir in pair_dirs:
        print(f"-> {pair_dir.name}")
        result = build_one(pair_dir, args.output, args.max_entries)
        if result is not None:
            out_path, count = result
            size_kb = out_path.stat().st_size / 1024
            print(f"   wrote {out_path.name}  ({count} entries, {size_kb:.1f} KB)")
            built += 1

    print()
    print(f"Done. Built {built} dictionary file(s).")
    return 0 if built > 0 else 1


if __name__ == "__main__":
    sys.exit(main())
