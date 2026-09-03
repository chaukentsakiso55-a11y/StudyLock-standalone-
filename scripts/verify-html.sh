#!/usr/bin/env bash
set -euo pipefail

expected="3920e817ef6e294ca603e0b72d29834833c9ddd22d5fea4286594345c05a4803"
printf '%s  %s\n' "$expected" "studylock-exact.html" | sha256sum --check --strict
printf '%s  %s\n' "$expected" "app/src/main/assets/studylock-exact.html" | sha256sum --check --strict
cmp --silent studylock-exact.html app/src/main/assets/studylock-exact.html
