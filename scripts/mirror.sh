#!/usr/bin/env bash
# Mirror platform files across the 9 version modules.
#
# Usage:
#   scripts/mirror.sh <variant> <relative-path> [<relative-path>...]
#
#   variant is one of:
#     legacy — copy from v1_21_1 to v1_21_3/4/5/8/10/11 (package rename v1_21_1 -> v1_21_X)
#     new    — copy from v26_1_2 to v26_2               (package rename v26_1_2 -> v26_2)
#     all    — both variants for the same file set
#
# The source file must have been edited first; every destination gets the same
# content with the version-specific package renamed. Files under common/ are
# shared and must NOT be mirrored.
#
# Examples:
#   scripts/mirror.sh all src/main/java/com/reclizer/csgobox/v1_21_1/packet/PacketCsgoProgress.java
#   scripts/mirror.sh legacy src/main/resources/META-INF/neoforge.mods.toml

set -euo pipefail

variant="${1:-}"
shift || true

LEGACY_MODULES=()
NEW_MODULES=()
case "$variant" in
  legacy) LEGACY_MODULES=(v1_21_3 v1_21_4 v1_21_5 v1_21_8 v1_21_10 v1_21_11) ;;
  new)    NEW_MODULES=(v26_2) ;;
  all)
    LEGACY_MODULES=(v1_21_3 v1_21_4 v1_21_5 v1_21_8 v1_21_10 v1_21_11)
    NEW_MODULES=(v26_2)
    ;;
  *)
    echo "usage: $0 <legacy|new|all> <relative-path>..." >&2
    exit 2
    ;;
esac

mirror_legacy() {
  local src_rel=$1
  local src="v1_21_1/$src_rel"
  for mod in "${LEGACY_MODULES[@]}"; do
    local pkg="v1_21_${mod#v1_21_}"
    local out
    out=$(printf '%s' "$mod/$src_rel" | sed "s#csgobox/v1_21_1#csgobox/$pkg#")
    mkdir -p "$(dirname "$out")"
    # BSD/GNU portable: no \b word boundary (BSD sed lacks it).
    # Source files only ever contain the v1_21_1 segment, so a plain
    # global replacement is safe.
    sed "s/csgobox\.v1_21_1/csgobox.$pkg/g" "$src" > "$out"
    echo "mirrored -> $out"
  done
}

mirror_new() {
  local src_rel=$1
  local src="v26_1_2/$src_rel"
  for mod in "${NEW_MODULES[@]}"; do
    local out
    out=$(printf '%s' "$mod/$src_rel" | sed 's#csgobox/v26_1_2#csgobox/v26_2#')
    mkdir -p "$(dirname "$out")"
    sed 's/csgobox\.v26_1_2/csgobox.v26_2/g' "$src" > "$out"
    echo "mirrored -> $out"
  done
}

for src_rel in "$@"; do
  [ -n "$src_rel" ] || continue
  if [ ${#LEGACY_MODULES[@]} -gt 0 ]; then
    [ -f "v1_21_1/$src_rel" ] || { echo "missing source: v1_21_1/$src_rel" >&2; exit 1; }
    mirror_legacy "$src_rel"
  fi
  if [ ${#NEW_MODULES[@]} -gt 0 ]; then
    # new-variant path is derived: csgobox/v1_21_1 -> csgobox/v26_1_2
    new_rel=$(printf '%s' "$src_rel" | sed 's#csgobox/v1_21_1#csgobox/v26_1_2#')
    [ -f "v26_1_2/$new_rel" ] || { echo "missing source: v26_1_2/$new_rel" >&2; exit 1; }
    mirror_new "$new_rel"
  fi
done
