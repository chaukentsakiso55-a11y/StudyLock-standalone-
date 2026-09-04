#!/usr/bin/env python3
import argparse
import json
import sqlite3
from datetime import datetime, timezone
from pathlib import Path


def parse_args():
    parser = argparse.ArgumentParser(description="Build the StudyLock Offline Tutor Library database")
    parser.add_argument("--input", action="append", required=True, help="JSONL source file; repeat for multiple files")
    parser.add_argument("--output", required=True, help="Output SQLite database")
    parser.add_argument("--version", type=int, default=1)
    return parser.parse_args()


def normalize_record(raw):
    title = str(raw.get("title", "")).strip()
    body = str(raw.get("body", raw.get("content", ""))).strip()
    if not title or not body:
        return None
    return (
        title,
        str(raw.get("subject", "")).strip(),
        str(raw.get("grade", "")).strip(),
        str(raw.get("source", "")).strip(),
        str(raw.get("source_url", "")).strip(),
        str(raw.get("license", "")).strip(),
        body,
    )


def main():
    args = parse_args()
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    if output.exists():
        output.unlink()

    db = sqlite3.connect(output)
    try:
        db.execute("PRAGMA page_size=4096")
        db.execute("PRAGMA journal_mode=OFF")
        db.execute("PRAGMA synchronous=OFF")
        db.execute("PRAGMA temp_store=MEMORY")
        db.execute(
            "CREATE TABLE reference_entries ("
            "id INTEGER PRIMARY KEY, "
            "title TEXT NOT NULL, "
            "subject TEXT NOT NULL DEFAULT '', "
            "grade TEXT NOT NULL DEFAULT '', "
            "source TEXT NOT NULL DEFAULT '', "
            "source_url TEXT NOT NULL DEFAULT '', "
            "license TEXT NOT NULL DEFAULT '', "
            "body TEXT NOT NULL"
            ")"
        )
        db.execute("CREATE INDEX idx_reference_subject ON reference_entries(subject)")
        db.execute("CREATE INDEX idx_reference_grade ON reference_entries(grade)")
        db.execute(
            "CREATE VIRTUAL TABLE reference_fts USING fts4("
            "content='reference_entries', title, subject, grade, source, body)"
        )
        db.execute("CREATE TABLE library_metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL)")

        inserted = 0
        for filename in args.input:
            path = Path(filename)
            with path.open("r", encoding="utf-8") as handle:
                batch = []
                for line in handle:
                    line = line.strip()
                    if not line:
                        continue
                    record = normalize_record(json.loads(line))
                    if record is None:
                        continue
                    batch.append(record)
                    if len(batch) >= 1000:
                        db.executemany(
                            "INSERT INTO reference_entries(title, subject, grade, source, source_url, license, body) "
                            "VALUES (?, ?, ?, ?, ?, ?, ?)",
                            batch,
                        )
                        inserted += len(batch)
                        batch.clear()
                if batch:
                    db.executemany(
                        "INSERT INTO reference_entries(title, subject, grade, source, source_url, license, body) "
                        "VALUES (?, ?, ?, ?, ?, ?, ?)",
                        batch,
                    )
                    inserted += len(batch)

        if inserted == 0:
            raise SystemExit("No valid reference entries were found in the input files")

        db.execute("INSERT INTO reference_fts(reference_fts) VALUES('rebuild')")
        metadata = {
            "schema_version": "1",
            "library_version": str(max(1, args.version)),
            "entry_count": str(inserted),
            "created_at": datetime.now(timezone.utc).isoformat(),
        }
        db.executemany("INSERT INTO library_metadata(key, value) VALUES (?, ?)", metadata.items())
        db.commit()
        db.execute("VACUUM")
        db.commit()

        size_mb = output.stat().st_size / (1024 * 1024)
        print(json.dumps({"entries": inserted, "size_mb": round(size_mb, 2), "output": str(output)}))
    finally:
        db.close()


if __name__ == "__main__":
    main()
