#!/usr/bin/env python3
"""Record source/data hashes, raw results and a commit message for a real experiment.

Run after tests, measurement and capture; this script does not create commits.
"""
import argparse
import csv
import hashlib
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("attempt", type=int)
    parser.add_argument("--title", required=True)
    parser.add_argument("--explanation", required=True)
    args = parser.parse_args()
    stem = f"attempt-{args.attempt:02d}"
    with (ROOT / "results" / f"{stem}.csv").open(newline="", encoding="utf-8") as file:
        rows = list(csv.DictReader(file))
    if len(rows) != 2 or {r["key"] for r in rows} != {"TITLE", "QUOTE"}:
        raise SystemExit("Expected exactly two measured tables")
    test = subprocess.run(["java", "-cp", "build", "HashTableTests"], cwd=ROOT,
                          capture_output=True, text=True, check=True)
    (ROOT / "results" / f"{stem}-tests.txt").write_text(test.stdout, encoding="utf-8")
    files = sorted((ROOT / "src").glob("*.java")) + sorted((ROOT / "test").glob("*.java"))
    files += [ROOT / "data" / "MOCK_DATA.csv", ROOT / "results" / f"{stem}.txt",
              ROOT / "results" / f"{stem}.csv", ROOT / "results" / f"{stem}-tests.txt",
              ROOT / "screenshots" / f"{stem}.png"]
    manifest = {"attempt": args.attempt, "title": args.title, "explanation": args.explanation,
                "files_sha256": {str(p.relative_to(ROOT)): digest(p) for p in files}}
    (ROOT / "results" / f"{stem}-manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    message = f"Attempt {args.attempt}: {args.title}\n\n{args.explanation}\n\n"
    for row in rows:
        message += (f"{row['key']}: {int(row['records']):,} records; capacity {int(row['capacity']):,}; "
                    f"wasted {int(row['wasted']):,} ({float(row['waste_percent']):.2f}%); "
                    f"collisions {int(row['collisions']):,}; construction median "
                    f"{float(row['median_ms']):.3f} ms (min {float(row['min_ms']):.3f}, "
                    f"max {float(row['max_ms']):.3f}).\n")
    message += "\n" + test.stdout + "Measured 3 warmups + 9 samples per table; raw samples, source hashes and screenshot included.\n"
    output = ROOT / "build" / "commit-message.txt"
    output.write_text(message, encoding="utf-8")
    print(message)


if __name__ == "__main__":
    main()
