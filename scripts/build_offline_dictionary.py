#!/usr/bin/env python3
import argparse
import io
import json
import os
import re
import sqlite3
import sys
import urllib.request
import zipfile
from pathlib import Path

MOBY_URL = "https://www.gutenberg.org/files/3201/files.zip"
DWYL_FALLBACK_URL = "https://raw.githubusercontent.com/dwyl/english-words/master/words_alpha.txt"
POS_FILES = {
    "noun": "n",
    "verb": "v",
    "adj": "a",
    "adv": "r",
}


def normalize_word(value: str) -> str:
    return re.sub(r"\s+", " ", value.replace("_", " ").strip().lower())[:96]


def parse_gloss(gloss: str):
    gloss = gloss.strip()
    examples = re.findall(r'"([^"]+)"', gloss)
    definition = re.sub(r';?\s*"[^"]+"', '', gloss).strip().strip(';').strip()
    return definition, examples[:2]


def build_schema(db: sqlite3.Connection):
    db.executescript(
        """
        PRAGMA journal_mode=OFF;
        PRAGMA synchronous=OFF;
        PRAGMA temp_store=MEMORY;
        CREATE TABLE meta(key TEXT PRIMARY KEY, value TEXT NOT NULL);
        CREATE TABLE entries(
            word TEXT NOT NULL,
            pos TEXT NOT NULL,
            definition TEXT NOT NULL,
            example TEXT NOT NULL DEFAULT ''
        );
        CREATE TABLE aliases(
            word TEXT NOT NULL,
            lemma TEXT NOT NULL,
            PRIMARY KEY(word, lemma)
        ) WITHOUT ROWID;
        CREATE TABLE lexicon(
            word TEXT PRIMARY KEY
        ) WITHOUT ROWID;
        """
    )


def import_wordnet(db: sqlite3.Connection, wordnet_dir: Path):
    entry_rows = []
    lexicon_rows = set()

    for filename, pos in POS_FILES.items():
        data_path = wordnet_dir / f"data.{filename}"
        if not data_path.exists():
            raise FileNotFoundError(f"Missing WordNet file: {data_path}")

        with data_path.open("r", encoding="utf-8", errors="replace") as handle:
            for line in handle:
                if not line or not line[0].isdigit() or "|" not in line:
                    continue
                left, gloss = line.split("|", 1)
                tokens = left.split()
                if len(tokens) < 5:
                    continue
                try:
                    word_count = int(tokens[3], 16)
                except ValueError:
                    continue
                words = []
                index = 4
                for _ in range(word_count):
                    if index + 1 >= len(tokens):
                        break
                    word = normalize_word(tokens[index])
                    index += 2
                    if word:
                        words.append(word)
                if not words:
                    continue

                definition, examples = parse_gloss(gloss)
                if not definition:
                    continue
                example = examples[0] if examples else ""
                for word in words:
                    lexicon_rows.add(word)
                    entry_rows.append((word, pos, definition, example))

                if len(entry_rows) >= 20000:
                    db.executemany(
                        "INSERT INTO entries(word,pos,definition,example) VALUES(?,?,?,?)",
                        entry_rows,
                    )
                    entry_rows.clear()

    if entry_rows:
        db.executemany(
            "INSERT INTO entries(word,pos,definition,example) VALUES(?,?,?,?)",
            entry_rows,
        )

    db.executemany(
        "INSERT OR IGNORE INTO lexicon(word) VALUES(?)",
        ((word,) for word in sorted(lexicon_rows)),
    )

    alias_rows = []
    for filename in POS_FILES:
        path = wordnet_dir / f"{filename}.exc"
        if not path.exists():
            continue
        with path.open("r", encoding="utf-8", errors="replace") as handle:
            for line in handle:
                parts = [normalize_word(part) for part in line.strip().split()]
                parts = [part for part in parts if part]
                if len(parts) < 2:
                    continue
                inflected = parts[0]
                for lemma in parts[1:]:
                    alias_rows.append((inflected, lemma))
                    lexicon_rows.add(inflected)
                    lexicon_rows.add(lemma)
    if alias_rows:
        db.executemany(
            "INSERT OR IGNORE INTO aliases(word,lemma) VALUES(?,?)",
            alias_rows,
        )
        db.executemany(
            "INSERT OR IGNORE INTO lexicon(word) VALUES(?)",
            ((word,) for word in sorted(lexicon_rows)),
        )


def download_bytes(url: str) -> bytes:
    request = urllib.request.Request(url, headers={"User-Agent": "StudyLock-Dictionary-Builder/1.0"})
    with urllib.request.urlopen(request, timeout=60) as response:
        return response.read()


def import_moby_or_fallback(db: sqlite3.Connection, cache_dir: Path):
    cache_dir.mkdir(parents=True, exist_ok=True)
    words = []
    source = ""

    try:
        archive_path = cache_dir / "moby-words.zip"
        if not archive_path.exists():
            archive_path.write_bytes(download_bytes(MOBY_URL))
        with zipfile.ZipFile(archive_path) as archive:
            names = archive.namelist()
            candidate = next(
                (name for name in names if name.lower().endswith("single.txt")),
                None,
            )
            if not candidate:
                raise RuntimeError("Moby single-word list was not found in the archive")
            raw = archive.read(candidate).decode("latin-1", errors="replace")
            words = raw.splitlines()
            source = "Moby Word Lists (Project Gutenberg #3201)"
    except Exception as moby_error:
        print(f"Moby download failed, using fallback word list: {moby_error}", file=sys.stderr)
        fallback_path = cache_dir / "words_alpha.txt"
        if not fallback_path.exists():
            fallback_path.write_bytes(download_bytes(DWYL_FALLBACK_URL))
        words = fallback_path.read_text(encoding="utf-8", errors="replace").splitlines()
        source = "english-words fallback lexicon"

    batch = []
    for raw in words:
        word = normalize_word(raw)
        if not word:
            continue
        if not re.fullmatch(r"[a-z][a-z' -]*", word):
            continue
        batch.append((word,))
        if len(batch) >= 25000:
            db.executemany("INSERT OR IGNORE INTO lexicon(word) VALUES(?)", batch)
            batch.clear()
    if batch:
        db.executemany("INSERT OR IGNORE INTO lexicon(word) VALUES(?)", batch)
    return source


def optimize(db: sqlite3.Connection):
    db.executescript(
        """
        CREATE INDEX entries_word_idx ON entries(word);
        CREATE INDEX aliases_word_idx ON aliases(word);
        ANALYZE;
        """
    )
    db.commit()
    db.execute("VACUUM")
    db.commit()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--wordnet-dir", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--cache-dir", default=".dictionary-cache")
    args = parser.parse_args()

    wordnet_dir = Path(args.wordnet_dir)
    output = Path(args.output)
    cache_dir = Path(args.cache_dir)
    output.parent.mkdir(parents=True, exist_ok=True)
    if output.exists():
        output.unlink()

    db = sqlite3.connect(output)
    try:
        build_schema(db)
        import_wordnet(db, wordnet_dir)
        lexicon_source = import_moby_or_fallback(db, cache_dir)
        meta = {
            "schema_version": "1",
            "definition_source": "Princeton WordNet 3.1",
            "lexicon_source": lexicon_source,
            "build_format": "StudyLock offline dictionary",
        }
        db.executemany(
            "INSERT INTO meta(key,value) VALUES(?,?)",
            meta.items(),
        )
        optimize(db)

        entries = db.execute("SELECT COUNT(*) FROM entries").fetchone()[0]
        words = db.execute("SELECT COUNT(*) FROM lexicon").fetchone()[0]
        aliases = db.execute("SELECT COUNT(*) FROM aliases").fetchone()[0]
        size_mb = output.stat().st_size / (1024 * 1024)
        print(json.dumps({
            "entries": entries,
            "lexicon_words": words,
            "aliases": aliases,
            "size_mb": round(size_mb, 2),
            "output": str(output),
        }))
    finally:
        db.close()


if __name__ == "__main__":
    main()
