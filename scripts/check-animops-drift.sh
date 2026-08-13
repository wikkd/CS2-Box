#!/usr/bin/env bash
# Check that every platform's AnimRenderOps exposes the same public method
# shape (name + arity + type family) and declares the correct era.
# Usage: scripts/check-animops-drift.sh
# Exit 0 when in sync, 1 on drift. Does NOT modify any file.
set -uo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

# platform -> expected era (EOL platforms archived 2026-08-09, tag eol-legacy-21x-1.0.6).
# Plain "platform era" pairs (macOS bash 3.2 has no associative arrays).
PLATFORMS="v1_21_1 legacy
v26_1_2 decoupled
v26_2 decoupled"

norm() {
  # Keep "public static" method signatures (multi-line safe), keep only the
  # param TYPES (names may legitimately differ), map GUI type families,
  # strip generics, collapse whitespace; output sorted lines.
  # Note: return/param type regexes include digits (e.g. Vector3f).
  perl -0pe 's/\s+/ /g' "$1" \
    | grep -oE 'public static [A-Za-z0-9<>?]* [A-Za-z0-9_]+\([A-Za-z0-9_, <>?]*\)' \
    | grep -v ' gunModelCenter(' \
    | perl -pe 's/(\(|, )([A-Za-z0-9<>?]+) [A-Za-z0-9_]+/$1$2/g; s/\([A-Za-z0-9_, <>?]*\)/()/ if /renderBlurredBackground/' \
    | sed -E 's/<[^>]*>//g' \
    | sed -E 's/GuiGraphicsExtractor/GuiGraphics/g' \
    | sed -E 's/Identifier/ResourceLocation/g' \
    | sed -E 's/[[:space:]]+/ /g' \
    | sort
}

# gunModelCenter is a v1_21_1-only TACZ op (compileOnly dependency; the
# decoupled platforms have no TACZ integration and cannot implement the same
# signature), so it is explicitly exempted from the cross-platform shape
# contract above. Any other op added to one platform must exist on all three.

fail=0
REF="$(norm v1_21_1/src/main/java/com/reclizer/csgobox/v1_21_1/utils/AnimRenderOps.java)"
[ -z "$REF" ] && { echo "FAIL: reference AnimRenderOps not found/empty"; exit 2; }
echo "reference: v1_21_1 ($(echo "$REF" | wc -l | tr -d ' ') ops)"

while read -r mod era; do
  [ -z "$mod" ] && continue
  f="$mod/src/main/java/com/reclizer/csgobox/$mod/utils/AnimRenderOps.java"
  [ -f "$f" ] || { echo "FAIL $mod: missing $f"; fail=1; continue; }
  got="$(norm "$f")"
  if [ "$got" = "$REF" ]; then
    echo "OK   $mod (era: $era)"
  else
    echo "FAIL $mod: signature drift vs v1_21_1"
    diff <(echo "$REF") <(echo "$got") | head -10
    fail=1
  fi
  grep -q "era: $era" "$f" || { echo "FAIL $mod: era header != $era"; fail=1; }
done <<< "$PLATFORMS"

exit $fail
