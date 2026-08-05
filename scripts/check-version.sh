#!/usr/bin/env bash
# Check mod_version sync across gradle.properties / CHANGELOG.md / README.md
# and that every platform mods.toml keeps the ${mod_version} template variable.
#
# Usage: scripts/check-version.sh
# Exit code 0 when everything is in sync, 1 when a mismatch is found.
# Does NOT modify any file.

set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if [ ! -f gradle.properties ]; then
  echo "ERROR: gradle.properties not found — run from the repo root" >&2
  exit 2
fi

mod_version="$(grep -E '^mod_version=' gradle.properties | head -1 | cut -d= -f2- | tr -d '[:space:]')"
if [ -z "$mod_version" ]; then
  echo "ERROR: mod_version= not found in gradle.properties" >&2
  exit 2
fi

fail=0

check_pattern() {
  # check_pattern <description> <file> <egrep-pattern>
  local desc=$1 file=$2 pattern=$3
  if [ ! -f "$file" ]; then
    echo "FAIL ${file}: missing"
    fail=1
  elif grep -Eq -- "$pattern" "$file"; then
    echo "OK   ${desc} (${file})"
  else
    echo "FAIL ${desc}: no match for '${pattern}' in ${file}"
    fail=1
  fi
}

echo "mod_version = ${mod_version}"
echo

# CHANGELOG.md must have a release header for the current version.
check_pattern "CHANGELOG release entry" "CHANGELOG.md" "## \\[${mod_version//./\\.}\\]"
# README.md must mention the current version.
check_pattern "README version mention" "README.md" "${mod_version//./\\.}"

# Every platform mod manifest must keep the ${mod_version} template variable
# (it is injected by Gradle; manual version strings there go stale).
declare -a TOMLS
while IFS= read -r toml; do
  TOMLS+=("$toml")
done < <(find . -path '*/src/main/resources/META-INF/*.toml' -type f 2>/dev/null | sed 's#^\./##' | sort)

if [ "${#TOMLS[@]}" -eq 0 ]; then
  echo "FAIL no META-INF/*.toml found under src/main/resources" >&2
  fail=1
else
  echo "checking \${mod_version} template in ${#TOMLS[@]} manifest(s)..."
  for toml in "${TOMLS[@]}"; do
    if grep -Fq "\${mod_version}" "$toml"; then
      echo "OK   \${mod_version} template (${toml})"
    else
      echo "FAIL missing \${mod_version} template (${toml})"
      fail=1
    fi
  done
fi

echo
if [ "$fail" -eq 0 ]; then
  echo "VERSION SYNC OK: ${mod_version}"
else
  echo "VERSION SYNC FAILURE: see lines above" >&2
  exit 1
fi