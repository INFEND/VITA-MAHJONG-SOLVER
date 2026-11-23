#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
javac -d "$tmp" app/src/main/java/dev/infend/vitasolver/core/*.java tests/SolverSelfTest.java
java -cp "$tmp" SolverSelfTest
