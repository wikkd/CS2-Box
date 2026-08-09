#!/usr/bin/env bash
# Mirror platform files across the maintained version modules.
#
# Usage:
#   scripts/mirror.sh <variant> <relative-path> [<relative-path>...] [--force] [--dry-run]
#
#   variant is one of:
#     new — copy from v26_1_2 to v26_2 (package rename v26_1_2 -> v26_2)
#
#   options (can appear anywhere):
#     --force   overwrite destination files that already exist (default: skip)
#     --dry-run print what would be done without writing anything
#
# SAFETY: mirror.sh is only meant for *brand-new* files that have NO per-module
# adaptation differences. Destination files that already exist are skipped with
# a warning — the platform modules are not pure copies (26.2 has API
# adaptations), so blindly overwriting them silently destroys platform-specific
# code. Use --force only when you have verified the file is identical across
# the target modules.
#
# The source file must have been edited first; every destination gets the same
# content with the version-specific package renamed. Files under common/ are
# shared and must NOT be mirrored.
#
# Legacy platform mirroring was removed 2026-08-09 when v1_21_0/3/4/5/8/10/11
# were archived (tag eol-legacy-21x-1.0.6); v1_21_1 is the only legacy module.
#
# Examples:
#   scripts/mirror.sh new src/main/java/com/reclizer/csgobox/v26_1_2/gui/NewScreen.java
#   scripts/mirror.sh --dry-run new src/main/java/com/reclizer/csgobox/v26_1_2/packet/PacketCsgoProgress.java

set -euo pipefail

FORCE=0
DRY_RUN=0
OPERANDS=()
for arg in "$@"; do
  case "$arg" in
    --force) FORCE=1 ;;
    --dry-run) DRY_RUN=1 ;;
    -h|--help)
      grep '^#' "$0" | sed 's/^# \{0,1\}//'
      exit 0
      ;;
    -*) echo "unknown option: $arg" >&2; exit 2 ;;
    *) OPERANDS+=("$arg") ;;
  esac
done

variant="${OPERANDS[0]:-}"
case "$variant" in
  new|all) NEW_MODULES=(v26_2) ;;
  *)
    echo "usage: $0 <new> <relative-path>... [--force] [--dry-run]" >&2
    exit 2
    ;;
esac

mirror_to() {
  local src=$1 mod=$2 out=$3
  if [ -e "$out" ] && [ "$FORCE" -eq 0 ]; then
    echo "skip (exists; use --force to overwrite) -> $out"
    return
  fi
  if [ "$DRY_RUN" -eq 1 ]; then
    echo "would mirror -> $out"
    return
  fi
  mkdir -p "$(dirname "$out")"
  sed "$SED_EXPR" "$src" > "$out"
  echo "mirrored -> $out"
}

mirror_new() {
  local src_rel=$1
  local src="v26_1_2/$src_rel"
  for mod in "${NEW_MODULES[@]}"; do
    local out
    out=$(printf '%s' "$mod/$src_rel" | sed 's#csgobox/v26_1_2#csgobox/v26_2#')
    SED_EXPR='s/csgobox\.v26_1_2/csgobox.v26_2/g'
    mirror_to "$src" "$mod" "$out"
  done
}

for src_rel in "${OPERANDS[@]:1}"; do
  [ -n "$src_rel" ] || continue
  new_rel=$(printf '%s' "$src_rel" | sed 's#csgobox/v1_21_1#csgobox/v26_1_2#')
  [ -f "v26_1_2/$new_rel" ] || { echo "missing source: v26_1_2/$new_rel" >&2; exit 1; }
  mirror_new "$new_rel"
done