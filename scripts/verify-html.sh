#!/usr/bin/env bash
set -euo pipefail

expected="cdb73b446b821a877df14927daaa00cea95171b753d180b1c52edf1733f4b3ca"
printf '%s  %s\n' "$expected" "studylock-exact.html" | sha256sum --check --strict
printf '%s  %s\n' "$expected" "app/src/main/assets/studylock-exact.html" | sha256sum --check --strict
cmp --silent studylock-exact.html app/src/main/assets/studylock-exact.html
