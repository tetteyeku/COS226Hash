#!/usr/bin/env python3
"""Capture the unmodified Java text report in headless Chrome, not fabricated metrics.

Only Python's standard library and a locally installed Chrome/Chromium are needed.
Each PNG contains both title and quote tables for one historical experiment.
"""
import argparse
import html
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("attempt", type=int)
    args = parser.parse_args()
    stem = f"attempt-{args.attempt:02d}"
    report_path = ROOT / "results" / f"{stem}.txt"
    report = report_path.read_text(encoding="utf-8")
    for required in [f"Attempt {args.attempt} |", "TITLE HASH TABLE", "QUOTE HASH TABLE", "Collisions:", "Construction:"]:
        if required not in report:
            raise SystemExit(f"Missing required statistics: {required}")
    candidates = [
        "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
        shutil.which("google-chrome"), shutil.which("chromium"), shutil.which("chromium-browser"),
    ]
    chrome = next((p for p in candidates if p and Path(p).is_file()), None)
    if not chrome:
        raise SystemExit("Install Chrome or Chromium to capture screenshots.")
    output = ROOT / "screenshots" / f"{stem}.png"
    output.parent.mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="cos226-capture-") as directory:
        page = Path(directory) / "results.html"
        page.write_text("""<!doctype html><html lang="en"><meta charset="utf-8">
<title>COS 226 measured results</title><style>
*{box-sizing:border-box}body{margin:0;padding:32px;background:#0c1322;color:#e8eef8}
header{font:600 17px system-ui;margin-bottom:20px;color:#9bcaee;letter-spacing:.06em}
pre{margin:0;padding:24px 28px;background:#141f33;border:1px solid #31445f;
border-radius:12px;font:17px/1.45 Menlo,Consolas,monospace;white-space:pre-wrap;overflow-wrap:anywhere}
footer{font:14px system-ui;color:#a9b9d0;margin-top:18px}
</style><header>EXECUTED JAVA PROGRAM / RECORDED CONSTRUCTION STATISTICS</header><pre>"""
                        + html.escape(report.rstrip()) + "</pre><footer>Source: results/"
                        + stem + ".txt | Exact program output, rendered by Chrome | Both tables included</footer></html>",
                        encoding="utf-8")
        run = subprocess.run([
            chrome, "--headless=new", "--disable-gpu", "--no-sandbox", "--hide-scrollbars",
            "--no-first-run", "--no-default-browser-check", "--force-device-scale-factor=1",
            "--window-size=1440,1120", f"--user-data-dir={directory}/profile",
            f"--screenshot={output}", page.as_uri(),
        ], capture_output=True, text=True, timeout=60)
        if run.returncode or not output.is_file():
            raise SystemExit(run.stderr or "Chrome did not write a screenshot")
    print(f"Captured {output.relative_to(ROOT)} from {report_path.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
