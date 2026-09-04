#!/usr/bin/env python3
import sqlite3
import sys
from pathlib import Path

if len(sys.argv) != 2:
    raise SystemExit("usage: check_offline_dictionary.py <database>")

path = Path(sys.argv[1])
if not path.exists() or path.stat().st_size < 5_000_000:
    raise SystemExit("Offline dictionary database is missing or unexpectedly small")

db = sqlite3.connect(path)
try:
    entries = db.execute("SELECT COUNT(*) FROM entries").fetchone()[0]
    lexicon = db.execute("SELECT COUNT(*) FROM lexicon").fetchone()[0]
    aliases = db.execute("SELECT COUNT(*) FROM aliases").fetchone()[0]

    if entries < 100_000:
        raise SystemExit(f"Too few dictionary entries: {entries}")
    if lexicon < 300_000:
        raise SystemExit(f"Too few offline English words: {lexicon}")

    for word in ("education", "learning", "science", "computer", "focus"):
        count = db.execute("SELECT COUNT(*) FROM entries WHERE word = ?", (word,)).fetchone()[0]
        if count == 0:
            raise SystemExit(f"Expected WordNet definition missing for: {word}")

    print(
        f"Offline dictionary checks passed: {entries} entries, "
        f"{lexicon} words, {aliases} imported exception aliases. "
        "StudyLock also applies native morphology matching at lookup time."
    )
finally:
    db.close()
