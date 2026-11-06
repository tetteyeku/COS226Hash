#!/usr/bin/env python3
"""Audit saved evidence and optionally rebuild each original optimization commit.

Requires Python 3.10+, git, and JDK 21+. Timings are validated for internal
consistency; replay compares deterministic statistics, never exact elapsed time.
"""
import argparse
import csv
import hashlib
import io
import json
from pathlib import Path
import statistics
import struct
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
DETERMINISTIC = ["attempt", "key", "method", "hash", "records", "distinct_keys",
                 "capacity", "used", "wasted", "waste_percent", "load", "collisions"]


def run(args, cwd=ROOT):
    return subprocess.run(args, cwd=cwd, capture_output=True, text=True, check=True, timeout=120).stdout


def require(condition, description):
    if not condition:
        raise AssertionError(description)


def read_rows(path):
    with path.open(newline="", encoding="utf-8") as file:
        return list(csv.DictReader(file))


def compare(expected, actual):
    require(len(actual) == 2, "Replay must contain both tables")
    for before, after in zip(expected, actual):
        for key in DETERMINISTIC:
            require(before[key] == after[key], f"Replay differs: attempt {before['attempt']} {before['key']} {key}")


def audit_evidence():
    commits = []
    readme_rows = [line.split("|")[1:-1] for line in (ROOT / "README.md").read_text().splitlines()
                   if line.startswith(tuple(f"| {n}:" for n in range(1, 6)))]
    require(len(readme_rows) == 10, "README must contain all ten measured table rows")
    for number in range(1, 6):
        stem = f"attempt-{number:02d}"
        manifest_path = f"results/{stem}-manifest.json"
        manifest = json.loads((ROOT / manifest_path).read_text())
        commit = run(["git", "log", "--diff-filter=A", "-1", "--format=%H", "--", manifest_path]).strip()
        require(len(commit) == 40, f"Missing original optimization commit {number}")
        message = run(["git", "show", "-s", "--format=%B", commit])
        require(message.startswith(f"Attempt {number}:"), "Missing descriptive attempt commit")
        require(manifest["explanation"] in message, "Commit must explain its measured optimization")
        for filename, expected in manifest["files_sha256"].items():
            content = subprocess.run(["git", "show", f"{commit}:{filename}"], cwd=ROOT,
                                     capture_output=True, check=True).stdout
            require(hashlib.sha256(content).hexdigest() == expected,
                    f"Historical source/evidence hash differs: {filename}")
            if not filename.startswith(("src/", "test/")):
                require(hashlib.sha256((ROOT / filename).read_bytes()).hexdigest() == expected,
                        f"Saved evidence changed after original commit: {filename}")
        rows = read_rows(ROOT / "results" / f"{stem}.csv")
        require([r["key"] for r in rows] == ["TITLE", "QUOTE"], "Both independent tables required")
        report = (ROOT / "results" / f"{stem}.txt").read_text()
        require(f"Attempt {number} |" in report, "Attempt label missing from captured output")
        for offset, row in enumerate(rows):
            capacity, used, wasted = (int(row[k]) for k in ["capacity", "used", "wasted"])
            require(capacity - used == wasted, "Incorrect wasted-space arithmetic")
            require(abs(float(row["waste_percent"]) - 100 * wasted / capacity) < 0.000002,
                    "Incorrect waste percentage")
            samples = list(map(float, row["samples_ms"].split(";")))
            require(len(samples) == 9 and min(samples) > 0, "Nine positive real timing samples required")
            for column, value in [("median_ms", statistics.median(samples)),
                                  ("min_ms", min(samples)), ("max_ms", max(samples))]:
                require(abs(float(row[column]) - value) < 0.000002, f"Inconsistent {column}")
            require(int(row["records"]) == 15000, "All input records required")
            require(int(row["distinct_keys"]) == (11309 if offset == 0 else 14891), "Duplicate keys must survive")
            cells = [cell.strip() for cell in readme_rows[(number - 1) * 2 + offset]]
            require([int(cells[i].replace(",", "")) for i in [2, 3, 5]] ==
                    [capacity, wasted, int(row["collisions"])], "README deterministic statistics differ")
            require(abs(float(cells[6]) - float(row["median_ms"])) <= 0.000501, "README timing differs")
        png = (ROOT / "screenshots" / f"{stem}.png").read_bytes()
        require(png[:8] == b"\x89PNG\r\n\x1a\n", "Actual PNG screenshot required")
        require(struct.unpack(">II", png[16:24]) == (1440, 1120), "Unexpected screenshot dimensions")
        commits.append((number, commit, rows))
        print(f"PASS attempt {number}: original commit {commit[:7]}, source hashes, preserved evidence, samples and README")
    require(len({c[1] for c in commits}) == 5, "Five separate optimization commits required")
    return commits


def cli_cases():
    run(["bash", "scripts/build.sh"])
    for args in [["--runs", "0"], ["--runs", "2"], ["--warmups", "-1"],
                 ["--attempt", "999"], ["--data", "not-a-real-file.csv"],
                 ["--unknown", "value"], ["--data"]]:
        result = subprocess.run(["java", "-cp", "build", "HashSomethingOut", *args],
                                cwd=ROOT, capture_output=True, text=True, timeout=15)
        require(result.returncode == 1 and result.stderr.startswith("Error:"), f"CLI failure handling: {args}")
    with tempfile.TemporaryDirectory(prefix="cos226-cli-") as temporary:
        path = Path(temporary)
        header = "movie_title,genre,release_date,director,box_office_revenue,rating,duration_minutes,production_company,quote\n"
        (path / "empty.csv").write_text(header, encoding="utf-8")
        run(["java", "-cp", "build", "HashSomethingOut", "--data", str(path / "empty.csv"),
             "--warmups", "0", "--runs", "1", "--output", str(path / "empty-results")])
        for number in range(1, 6):
            for row in read_rows(path / "empty-results" / f"attempt-{number:02d}.csv"):
                require(row["records"] == "0" and row["collisions"] == "0", "Empty-dataset CLI behavior")
        text = io.StringIO(newline="")
        writer = csv.writer(text)
        writer.writerow(header.strip().split(","))
        writer.writerow(["", "", "", "", "", "", "", "", ""])
        writer.writerow(['Café, "🙂"', "Drama", "", "", "", "", "", "", "line1\nline2"])
        (path / "edge.csv").write_text(text.getvalue(), encoding="utf-8")
        output = run(["java", "-cp", "build", "HashSomethingOut", "--data", str(path / "edge.csv"),
                      "--warmups", "0", "--runs", "1", "--title", "", "--quote", "line1\nline2"])
        require(output.count("Lookup TITLE []: 1 records") == 5, "Empty-key lookup through CLI")
        require(output.count("Lookup QUOTE [line1\nline2]: 1 records") == 5, "Multiline lookup through CLI")
    print("PASS CLI: invalid arguments/files, empty datasets, empty keys, quoted Unicode and multiline retrieval")


def history_replay(commits):
    with tempfile.TemporaryDirectory(prefix="cos226-history-") as temporary:
        clone = Path(temporary) / "repo"
        run(["git", "clone", "--quiet", "--no-hardlinks", str(ROOT), str(clone)])
        for number, commit, expected in commits:
            run(["git", "checkout", "--quiet", commit], cwd=clone)
            tests = run(["bash", "scripts/test.sh"], cwd=clone).strip()
            run(["java", "-cp", "build", "HashSomethingOut", "--attempt", str(number),
                 "--warmups", "0", "--runs", "1", "--output", "build/replay"], cwd=clone)
            compare(expected, read_rows(clone / "build/replay" / f"attempt-{number:02d}.csv"))
            print(f"PASS history replay {number} ({commit[:7]}): {tests} Deterministic statistics match.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--history", action="store_true", help="Compile, test and replay each original optimization commit")
    args = parser.parse_args()
    commits = audit_evidence()
    cli_cases()
    if args.history:
        history_replay(commits)
    print("PASS: all requested evidence checks completed.")


if __name__ == "__main__":
    main()
