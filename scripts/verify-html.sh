#!/usr/bin/env bash
set -euo pipefail
cmp --silent studylock-exact.html app/src/main/assets/studylock-exact.html
sha256sum studylock-exact.html app/src/main/assets/studylock-exact.html
