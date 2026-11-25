#!/usr/bin/env bash
# Deploy the dev test environment for every platform module.
#
# Everything lands inside the gitignored run/ or runs/client|server/ folders:
# no build.gradle changes, no CI impact. Re-running is idempotent:
#   - mods are copied only when absent
#   - boxes are copied only when absent (existing local edits win)
#   - _prices.json fragments are merged with "existing keys win"
#
# Mods are staged in local-repo/dev-mods-staging (downloaded by the
# per-mod scripts) and local-repo/_probe (full Jade jars). Nothing here
# downloads; run scripts/download-*.sh first when a jar is missing.
#
# Platform matrix (2026-11-23):
#   v1_21_1        TACZ unofficial 1.1.8 + Jade; all mod packs (SBW/Create/
#                  Aeronautics/IA/KC family/MG/Prefab/SlashBlade/TACZ) + test
#   v26_1_2        IA is NOT loadable: its NeoForge floor is 26.1.2.109 while
#                  the repo pins 26.1.2.95 and maven.neoforged.net is
#                  unreachable. MG 26.1.2.3 + l2library + EMF/ETF + Jade;
#                  vanilla test pack + modern_crate + preview G1.
#   v26_2          IA needs NeoForge 26.2.0.88 > pinned 26.2.0.59; MG has no
#                  26.2 build. EMF/ETF + Jade + vanilla test pack +
#                  modern_crate + preview G1.
#   forge_26_1_2   TestHelper 26.1.2 already installed; no content mod has a
#                  Forge 26.x build. Vanilla test pack + modern_crate +
#                  preview G1.
#   forge_26_2     No TestHelper 26.2 jar (26.1.2 declares MC <26.2); vanilla
#                  test pack + modern_crate + preview G1.
#   forge_1_20_1   Already fully populated; add Jade + vanilla test pack +
#                  preview G1.
#   forge_1_18_2   IA 0.5.2 + EMF/ETF + TACZ 1.1.4; 1.18.2 test pack + TACZ
#                  pack (no preview G1: chest boats are 1.19+).
#   fabric_1_21_1  Already has IA/KC/TACZ; add vanilla test pack, modern_crate,
#                  preview G1/IA/KC.
#
# Usage: ./scripts/deploy-all-dev-envs.sh [--dry-run]
set -euo pipefail
cd "$(dirname "$0")/.."

DRY_RUN=0
if [ "${1:-}" = "--dry-run" ]; then
    DRY_RUN=1
fi

STAGING=local-repo/dev-mods-staging
PROBE=local-repo/_probe
TACZ_1211=local-repo/com/tacz/tacz/1.1.8-hotfix-r6/tacz-1.1.8-hotfix-r6.jar

# WSL has python3 but no `python`; Git Bash often has the opposite.
PYTHON=${PYTHON:-}
if [ -z "$PYTHON" ]; then
    if command -v python3 >/dev/null 2>&1; then
        PYTHON=python3
    elif command -v python >/dev/null 2>&1; then
        PYTHON=python
    else
        echo "python3/python not found; cannot merge price tables" >&2
        exit 1
    fi
fi

copy_mod() {
    local src="$1" dst="$2" name
    name=$(basename "$src")
    mkdir -p "$dst"
    if [ -s "$dst/$name" ]; then
        echo "[skip] mods/$name"
        return
    fi
    if [ ! -s "$src" ]; then
        echo "[warn] missing mod source: $src" >&2
        return
    fi
    if [ "$DRY_RUN" = 1 ]; then
        echo "[plan] mods/$name"
    else
        cp "$src" "$dst/$name"
        echo "[mod ] $name"
    fi
}

# Forge 1.20.1 needs SRG->official remapped jars (see scripts/remap-1201.sh);
# deploy them under the pipeline's own file names so both stay idempotent.
copy_mod_as() {
    local src="$1" name="$2" dst="$3"
    mkdir -p "$dst"
    if [ -s "$dst/$name" ]; then
        echo "[skip] mods/$name"
        return
    fi
    if [ ! -s "$src" ]; then
        echo "[warn] missing mod source: $src" >&2
        return
    fi
    if [ "$DRY_RUN" = 1 ]; then
        echo "[plan] mods/$name"
    else
        cp "$src" "$dst/$name"
        echo "[mod ] $name"
    fi
}

copy_file() {
    local src="$1" dst="$2" base
    base=$(basename "$src")
    mkdir -p "$dst"
    if [ -s "$dst/$base" ]; then
        echo "[skip] config/$base"
        return
    fi
    if [ "$DRY_RUN" = 1 ]; then
        echo "[plan] config/$base"
    else
        cp "$src" "$dst/$base"
        echo "[cfg ] $base"
    fi
}

copy_pack() {
    local pack="$1" dst="$2" exclude="${3:-}" f base
    for f in "compat-packs/$pack"/*.json; do
        [ -e "$f" ] || continue
        base=$(basename "$f")
        [ "$base" = "_prices.json" ] && continue
        if [ -n "$exclude" ] && [[ "$base" == $exclude ]]; then
            continue
        fi
        copy_file "$f" "$dst"
    done
}

merge_prices() {
    local pack="$1" dst="$2"
    if [ "$DRY_RUN" = 1 ]; then
        echo "[plan] merge compat-packs/$pack/_prices.json -> $dst/_prices.json"
        return
    fi
    mkdir -p "$dst"
    "$PYTHON" scripts/merge-prices.py "compat-packs/$pack/_prices.json" "$dst/_prices.json"
}

# --- v1_21_1 (NeoForge 1.21.1) -----------------------------------------
echo
echo "== v1_21_1 (NeoForge 1.21.1) =="
C=v1_21_1/runs/client
copy_mod "$TACZ_1211" "$C/mods"
copy_mod "$PROBE/jade-1211.jar" "$C/mods"
copy_mod "$STAGING/wthit-1.21.1-neo-12.10.2.jar" "$C/mods"
copy_mod "$STAGING/badpackets-neo-0.8.2.jar" "$C/mods"
for pack in \
    kaleidoscope-cookery-pack kaleidoscope-chinesefood-pack \
    kaleidoscope-grilling-pack kaleidoscope-bloodwine-pack \
    kaleidoscope-dim-wine-pack kaleidoscope-end-pack \
    kaleidoscope-nether-pack kaleidoscope-world-liquor-pack \
    kaleidoscope-doll-pack kaleidoscope-tavern-pack \
    tacz-pack slashblade-resharped-pack test-pack preview-test-pack; do
    copy_pack "$pack" "$C/config/csbox" '*_1201.json'
    merge_prices "$pack" "$C/config/csbox"
done
copy_file compat-packs/test-pack-fabric-1.21.1/modern_crate.json "$C/config/csbox"
merge_prices test-pack-fabric-1.21.1 "$C/config/csbox"

S=v1_21_1/runs/server
copy_pack test-pack "$S/config/csbox"
merge_prices test-pack "$S/config/csbox"
copy_file compat-packs/preview-test-pack/preview_g1_entities.json "$S/config/csbox"
merge_prices preview-test-pack "$S/config/csbox"
copy_pack slashblade-resharped-pack "$S/config/csbox" '*_1201.json'
merge_prices slashblade-resharped-pack "$S/config/csbox"

# --- v26_1_2 (NeoForge 26.1.2) -----------------------------------------
echo
echo "== v26_1_2 (NeoForge 26.1.2) =="
# NeoGradle userdev run dirs are runs/<name>, not run/.
R=v26_1_2/runs/client
copy_mod "$STAGING/entity_model_features-3.3.12-26.1-neoforge.jar" "$R/mods"
copy_mod "$STAGING/entity_texture_features-7.2.7-26.1-neoforge.jar" "$R/mods"
copy_mod "$STAGING/modulargolems-26.1.2.3.jar" "$R/mods"
copy_mod "$STAGING/l2library-26.1.2.1+2.jar" "$R/mods"
copy_mod "$PROBE/jade-2612.jar" "$R/mods"
copy_mod "$STAGING/wthit-26.1-neo-19.0.1.jar" "$R/mods"
copy_mod "$STAGING/badpackets-neo-0.12.2.jar" "$R/mods"
copy_pack test-pack "$R/config/csbox"
merge_prices test-pack "$R/config/csbox"
copy_file compat-packs/test-pack-fabric-1.21.1/modern_crate.json "$R/config/csbox"
merge_prices test-pack-fabric-1.21.1 "$R/config/csbox"
copy_file compat-packs/preview-test-pack/preview_g1_entities.json "$R/config/csbox"
merge_prices preview-test-pack "$R/config/csbox"
S=v26_1_2/runs/server
copy_pack test-pack "$S/config/csbox"
merge_prices test-pack "$S/config/csbox"
copy_file compat-packs/test-pack-fabric-1.21.1/modern_crate.json "$S/config/csbox"
merge_prices test-pack-fabric-1.21.1 "$S/config/csbox"
copy_file compat-packs/preview-test-pack/preview_g1_entities.json "$S/config/csbox"
merge_prices preview-test-pack "$S/config/csbox"

# --- v26_2 (NeoForge 26.2) ---------------------------------------------
echo
echo "== v26_2 (NeoForge 26.2) =="
R=v26_2/runs/client
copy_mod "$STAGING/entity_model_features-3.3.12-26.2-neoforge.jar" "$R/mods"
copy_mod "$STAGING/entity_texture_features-7.2.7-26.2-neoforge.jar" "$R/mods"
copy_mod "$PROBE/jade-262.jar" "$R/mods"
copy_mod "$STAGING/wthit-26.2-neo-20.0.0.jar" "$R/mods"
copy_mod "$STAGING/badpackets-neo-0.12.3.jar" "$R/mods"
copy_pack test-pack "$R/config/csbox"
merge_prices test-pack "$R/config/csbox"
copy_file compat-packs/test-pack-fabric-1.21.1/modern_crate.json "$R/config/csbox"
merge_prices test-pack-fabric-1.21.1 "$R/config/csbox"
copy_file compat-packs/preview-test-pack/preview_g1_entities.json "$R/config/csbox"
merge_prices preview-test-pack "$R/config/csbox"
S=v26_2/runs/server
copy_pack test-pack "$S/config/csbox"
merge_prices test-pack "$S/config/csbox"
copy_file compat-packs/test-pack-fabric-1.21.1/modern_crate.json "$S/config/csbox"
merge_prices test-pack-fabric-1.21.1 "$S/config/csbox"
copy_file compat-packs/preview-test-pack/preview_g1_entities.json "$S/config/csbox"
merge_prices preview-test-pack "$S/config/csbox"

# --- forge_26_1_2 / forge_26_2 (MinecraftForge 26.x) -------------------
for M in forge_26_1_2 forge_26_2; do
    echo
    echo "== $M (MinecraftForge 26.x) =="
    R="$M/run"
    case "$M" in
        forge_26_1_2)
            copy_mod "$STAGING/wthit-26.1-forge-19.0.1.jar" "$R/mods"
            copy_mod "$STAGING/badpackets-forge-0.12.2.jar" "$R/mods"
            ;;
        forge_26_2)
            copy_mod "$STAGING/wthit-26.2-forge-20.0.0.jar" "$R/mods"
            copy_mod "$STAGING/badpackets-forge-0.12.3.jar" "$R/mods"
            ;;
    esac
    copy_pack test-pack "$R/config/csbox"
    merge_prices test-pack "$R/config/csbox"
    copy_file compat-packs/test-pack-fabric-1.21.1/modern_crate.json "$R/config/csbox"
    merge_prices test-pack-fabric-1.21.1 "$R/config/csbox"
    copy_file compat-packs/preview-test-pack/preview_g1_entities.json "$R/config/csbox"
    merge_prices preview-test-pack "$R/config/csbox"
done

# --- forge_1_20_1 -------------------------------------------------------
echo
echo "== forge_1_20_1 (MinecraftForge 1.20.1) =="
R=forge_1_20_1/run
copy_mod_as "$PROBE/jade-1201-official.jar" 'jade-11.13.3+forge-official.jar' "$R/mods"
copy_mod_as "$STAGING/wthit-1.20.1-forge-8.21.1-official.jar" 'wthit-1.20.1-forge-8.21.1-official.jar' "$R/mods"
copy_mod_as "$STAGING/badpackets-forge-0.4.3-official.jar" 'badpackets-forge-0.4.3-official.jar' "$R/mods"
copy_pack test-pack "$R/config/csbox"
merge_prices test-pack "$R/config/csbox"
copy_file compat-packs/preview-test-pack/preview_g1_entities.json "$R/config/csbox"
merge_prices preview-test-pack "$R/config/csbox"

# --- forge_1_18_2 -------------------------------------------------------
echo
echo "== forge_1_18_2 (MinecraftForge 1.18.2) =="
R=forge_1_18_2/run
# No third-party jars: ForgeGradle 5 dev runs on official names, while every
# 1.18.2 release jar (TACZ/EMF/ETF/IA/WTHIT) carries SRG refmaps or SRG member
# references. They crash at mixin apply; there is no 1.18.2 remap pipeline
# (scripts/remap-1201.sh is 1.20.1-only). Keep the 1.18.2 env vanilla + data
# packs; validate mod compat on the 1.20.1/1.21.1 modules instead.
copy_pack test-pack-forge-1.18.2 "$R/config/csbox"
merge_prices test-pack-forge-1.18.2 "$R/config/csbox"

# --- fabric_1_21_1 ------------------------------------------------------
echo
echo "== fabric_1_21_1 (Fabric 1.21.1) =="
R=fabric_1_21_1/run
copy_mod "$STAGING/jade-15.10.6+fabric.jar" "$R/mods"
copy_mod "$STAGING/wthit-1.21.1-fabric-12.10.2.jar" "$R/mods"
copy_mod "$STAGING/badpackets-fabric-0.8.2.jar" "$R/mods"
copy_pack test-pack "$R/config/csbox"
merge_prices test-pack "$R/config/csbox"
copy_pack test-pack-fabric-1.21.1 "$R/config/csbox"
merge_prices test-pack-fabric-1.21.1 "$R/config/csbox"
copy_file compat-packs/preview-test-pack/preview_g1_entities.json "$R/config/csbox"
copy_file compat-packs/preview-test-pack/preview_ia_vehicles.json "$R/config/csbox"
copy_file compat-packs/preview-test-pack/preview_kc_food.json "$R/config/csbox"
merge_prices preview-test-pack "$R/config/csbox"

# --- summary ------------------------------------------------------------
echo
echo "== summary =="
for R in \
    v1_21_1/runs/client v1_21_1/runs/server \
    v26_1_2/runs/client v26_1_2/runs/server \
    v26_2/runs/client v26_2/runs/server \
    forge_26_1_2/run forge_26_2/run \
    forge_1_20_1/run forge_1_18_2/run fabric_1_21_1/run; do
    [ -d "$R" ] || continue
    mods=$(find "$R/mods" -maxdepth 1 -type f -name '*.jar' 2>/dev/null | wc -l || true)
    boxes=$(find "$R/config/csbox" -maxdepth 1 -type f -name '*.json' 2>/dev/null | wc -l || true)
    printf '%-28s mods=%-4s configs=%s\n' "$R" "$mods" "$boxes"
done
