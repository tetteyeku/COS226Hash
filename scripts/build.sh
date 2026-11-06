#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build
javac -encoding UTF-8 -Xlint:all -Werror -d build src/*.java test/*.java
