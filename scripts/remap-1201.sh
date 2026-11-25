#!/usr/bin/env bash
#
# Unified SRG -> official remap pipeline for every third-party 1.20.1 Forge
# jar that must load under the ForgeGradle 7 dev runtime (FG7 has no
# fg.deobf). Manifest-driven, idempotent, gated.
#
#   scripts/remap-1201-manifest.txt   one line per product (TAB-separated):
#     <source jar path>  <run/mods deploy name>  <jarjar inner paths | ->
#   scripts/TaczDeobf.java            bytecode remapper (ASM + srgutils)
#   scripts/remap-tacz-1201.py        mixin refmap rewriter
#   scripts/check-official-remap.py   executable-ref + refmap gate
#
# Product naming: "<source-name>-official.jar" next to the source in
# local-repo; deployed to forge_1_20_1/run/mods/<deploy name>.
#
# Cache: each product gets a sidecar "<product>.stamp" holding the tool
# fingerprint (sha256 of TaczDeobf.java + remap-tacz-1201.py + the tsrg).
# Touch either tool or the mapping and every product rebuilds on the next
# run — the round-21 "old artifact never re-run after tool fix" failure mode
# is closed mechanically. Products also rebuild when older than their source
# jar. The stamp is written ONLY after the product is fully processed and
# gated, so an interrupted run never leaves a half-remapped jar cached.
#
# Gate: every product is gated through check-official-remap.py --gate before
# deployment (executable SRG references / refmap leftovers -> no deploy).
# Deployment is diff-checked (cmp) — run/mods is only touched when content
# actually changes. jarjar inner bundles are remapped and re-embedded BEFORE
# the (single) gate+deploy.
#
# TSRG_OVERRIDE: export to point at a merged tsrg (e.g. the WoodType
# stale-SRG extra entry); the override becomes part of the fingerprint.
#
# Environment (Git Bash on Windows):
#   - JDK pinned to ~/.jdks/ms-21.0.9 (javac must exist; system PATH is not
#     relied on — remap-slashblade-1201.sh crashed on this in round 21).
#   - python resolved as python3 -> python -> py -3 (python3 is broken on
#     this machine, rc=49).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MANIFEST="${ROOT}/scripts/remap-1201-manifest.txt"
MODS_DIR="${ROOT}/forge_1_20_1/run/mods"

export PATH="${HOME}/.jdks/ms-21.0.9/bin:$PATH"

GRADLE_CACHE="${HOME}/.gradle/caches/modules-2/files-2.1"
MCP_CACHE="${HOME}/.gradle/caches/minecraftforge/forgegradle/mavenizer/caches/mcp/de/oceanlabs/mcp/mcp_config/1.20.1-20230612.114412"
# NOTE: despite the "-obf" filename, official-1.20.1-obf.tsrg.gz is the
# official -> SRG mapping (tsrg2). TaczDeobf / remap-tacz-1201.py reverse it
# in memory to get SRG -> official.
TSRG_GZ="${TSRG_OVERRIDE:-${MCP_CACHE}/data/mapings/official/1.20.1/official-1.20.1-obf.tsrg.gz}"

find_jar() { # "group/artifact:version"
    local spec="$1" gav ver artifact
    gav="${spec%:*}"
    ver="${spec##*:}"
    artifact="${gav##*/}"
    # `|| true`: with `set -o pipefail`, a missing cache directory would
    # otherwise abort the whole script before the download fallback runs.
    find "${GRADLE_CACHE}/${gav}" -name "${artifact}-${ver}.jar" \
        ! -name '*-sources.jar' ! -name '*-javadoc.jar' 2>/dev/null | head -1 || true
}

# The remapper's own tool jars are intentionally not part of any Gradle
# configuration (the pipeline runs outside Gradle), so a fresh CI runner or a
# brand-new dev machine has no copy in ~/.gradle. Fall back to downloading
# them into the gitignored build/ cache instead of failing.
TOOL_CACHE="${ROOT}/build/remap-tools"
mkdir -p "$TOOL_CACHE"

fetch_tool() { # <url> <file-name>; echoes the absolute path on success
    local url="$1" name="$2" out="${TOOL_CACHE}/$2"
    if [[ ! -f "$out" ]]; then
        echo "==> fetching ${name}" >&2
        if ! curl -sS -4 --http1.1 -fL --retry 3 --retry-delay 2 \
                --connect-timeout 20 --max-time 180 -o "${out}.part" "$url"; then
            rm -f "${out}.part"
            return 1
        fi
        mv "${out}.part" "$out"
    fi
    printf '%s' "$out"
}

# ---- python probe: python3 -> python -> py -3 ----
PY=""
for cand in python3 python "py -3"; do
    if $cand -c "import sys" >/dev/null 2>&1; then PY="$cand"; break; fi
done
[[ -n "$PY" ]] || { echo "ERROR: no usable python (python3/python/py -3)" >&2; exit 1; }
echo "python: $PY"

# ---- prereqs ----
[[ -f "$MANIFEST" ]] || { echo "ERROR: manifest missing: $MANIFEST" >&2; exit 1; }
[[ -f "$TSRG_GZ" ]] || { echo "ERROR: tsrg missing: $TSRG_GZ" >&2; exit 1; }
# The deploy folder is an output of this pipeline, not a prerequisite: fresh
# clones and CI checkouts have no forge_1_20_1/run/ directory yet.
mkdir -p "$MODS_DIR"

ASM="$(find_jar org.ow2.asm/asm:9.9.1)"
[[ -n "$ASM" ]] || ASM="$(fetch_tool https://repo1.maven.org/maven2/org/ow2/asm/asm/9.9.1/asm-9.9.1.jar asm-9.9.1.jar)" || ASM=""
ASMT="$(find_jar org.ow2.asm/asm-tree:9.9.1)"
[[ -n "$ASMT" ]] || ASMT="$(fetch_tool https://repo1.maven.org/maven2/org/ow2/asm/asm-tree/9.9.1/asm-tree-9.9.1.jar asm-tree-9.9.1.jar)" || ASMT=""
ASMC="$(find_jar org.ow2.asm/asm-commons:9.9.1)"
[[ -n "$ASMC" ]] || ASMC="$(fetch_tool https://repo1.maven.org/maven2/org/ow2/asm/asm-commons/9.9.1/asm-commons-9.9.1.jar asm-commons-9.9.1.jar)" || ASMC=""
SRGUTILS="$(find_jar net.minecraftforge/srgutils:0.4.11)"
[[ -n "$SRGUTILS" ]] || SRGUTILS="$(fetch_tool https://maven.minecraftforge.net/net/minecraftforge/srgutils/0.4.11/srgutils-0.4.11.jar srgutils-0.4.11.jar)" || SRGUTILS=""
for v in ASM ASMT ASMC SRGUTILS; do
    [[ -n "${!v}" ]] || { echo "ERROR: could not resolve $v" >&2; exit 1; }
done
DEOBF_CP="$ASM:$ASMT:$ASMC:$SRGUTILS"

WORK="$(mktemp -d -t remap1201.XXXXXX)"
trap 'rm -rf "$WORK"' EXIT

# ---- incremental mapping merge (intermediary leftovers etc.) ----
# scripts/tsrg-extra-1201.txt exists -> merge into the default tsrg and use the
# result for this run (replaces the old TSRG_OVERRIDE-by-hand workflow).
EXTRA_TSRG="${ROOT}/scripts/tsrg-extra-1201.txt"
if [[ -f "$EXTRA_TSRG" && -z "${TSRG_OVERRIDE:-}" ]]; then
    MERGED_TSRG="$WORK/tsrg-merged.tsrg.gz"
    "$PY" "$ROOT/scripts/merge-tsrg-1201.py" "$MERGED_TSRG" >/dev/null
    TSRG_GZ="$MERGED_TSRG"
fi

# ---- tool fingerprint (invalidates every stamp when anything changes) ----
DEOBF_HASH="$(sha256sum "$ROOT/scripts/TaczDeobf.java" | cut -d' ' -f1)"
PYHASH_HASH="$(sha256sum "$ROOT/scripts/remap-tacz-1201.py" | cut -d' ' -f1)"
TSRG_ID="$(sha256sum "$TSRG_GZ" | cut -d' ' -f1)"
FINGERPRINT="deobf=${DEOBF_HASH}
py=${PYHASH_HASH}
tsrg=${TSRG_ID}"
FP_FILE="$WORK/fingerprint"
printf '%s\n' "$FINGERPRINT" > "$FP_FILE"

mkdir -p "$WORK/deobf-classes"
echo "==> compiling TaczDeobf"
javac -cp "$DEOBF_CP" -d "$WORK/deobf-classes" "$ROOT/scripts/TaczDeobf.java"
DEOBF_CP_FULL="$DEOBF_CP:$WORK/deobf-classes"

# remap one jar (bytecode + refmap rewrite)
remap_one() { # <src> <out> [heap]
    local src="$1" out="$2" heap="${3:--Xmx2g}"
    java "$heap" -cp "$DEOBF_CP_FULL" TaczDeobf "$TSRG_GZ" "$src" "$out" | sed 's/^/    /'
    "$PY" "$ROOT/scripts/remap-tacz-1201.py" "$TSRG_GZ" "$out"
}

built=0 skipped=0 failed=0 deployed=0

while IFS=$'\t' read -r src_rel deploy_name jarjars; do
    case "${src_rel}" in ''|'#'*) continue;; esac
    SRC="${ROOT}/${src_rel}"
    OUT="$(dirname "$SRC")/$(basename "${SRC%.jar}")-official.jar"
    STAMP="${OUT}.stamp"

    if [[ ! -f "$SRC" ]]; then
        # The manifest covers every 1.20.1 dev jar across all platforms; a
        # checkout only carries the ones its download scripts fetched (CI has
        # TACZ / Create / Curios, nothing else). Missing sources are not
        # failures — `failed` is reserved for remap/gate errors.
        echo "SKIP (source missing): $src_rel" >&2
        skipped=$((skipped + 1))
        continue
    fi

    need_build=false
    if [[ -f "$OUT" && -f "$STAMP" ]] \
        && cmp -s "$STAMP" "$FP_FILE" && [[ "$OUT" -nt "$SRC" ]]; then
        echo "skip (up-to-date): $(basename "$OUT")"
        skipped=$((skipped + 1))
    else
        echo "==> remapping $(basename "$SRC")"
        if ! remap_one "$SRC" "$OUT"; then
            echo "ERROR: remap failed for $SRC" >&2
            failed=$((failed + 1))
            continue
        fi
        # ---- mod-specific post-remap bytecode patches ----
        # TB 9.1.58: TravelersBackpackItem$2 reads the SERVER config in the
        # item capability path; SERVER specs don't load on a client until a
        # world exists, so Epic Fight's config-load item scan trips Forge's
        # dev-only strict check and kills the FG7 dev client (2026-11-18 and
        # 2026-11-20 crashes). Production never throws, so this patch is
        # dev-environment hygiene only.
        case "$(basename "$SRC")" in
            travelersbackpack-*)
                echo "==> TB capability config guard"
                $PY -I "$ROOT/scripts/patch-tb-capability-guard-1201.py" "$OUT" | sed 's/^/    /'
                ;;
        esac
        need_build=true
    fi

    # ---- jarjar inner bundles (only on a fresh build; a cached product
    # already has remapped bundles embedded from its successful run) ----
    if $need_build && [[ "$jarjars" != "-" ]]; then
        IFS=';' read -ra JJ <<< "$jarjars"
        JJ_DIR="$WORK/jj-$(basename "$OUT")"
        rm -rf "$JJ_DIR"
        for inner in "${JJ[@]}"; do
            unzip -o -q "$OUT" "$inner" -d "$JJ_DIR"
            inner_src="$JJ_DIR/$inner"
            if [[ ! -f "$inner_src" ]]; then
                echo "ERROR: jarjar bundle missing in $(basename "$OUT"): $inner" >&2
                failed=$((failed + 1))
                need_build=false
                break
            fi
            inner_out="$WORK/inner-${deploy_name}-$(basename "$inner")"
            echo "==> remapping inner $(basename "$inner")"
            if remap_one "$inner_src" "$inner_out" "-Xmx1g"; then
                # re-embed (atomic rewrite of the outer jar)
                "$PY" - "$OUT" "$inner" "$inner_out" <<'PYEOF'
import shutil, sys, zipfile
outer, inner_path, repl = sys.argv[1], sys.argv[2], sys.argv[3]
data = open(repl, "rb").read()
tmp = outer + ".tmp.jar"
zin = zipfile.ZipFile(outer, "r")
with zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED) as zout:
    for item in zin.infolist():
        if item.filename == inner_path:
            print("re-embedded", item.filename.split("/")[-1], len(data), "bytes")
            zout.writestr(item, data)
        else:
            zout.writestr(item, zin.read(item.filename))
zin.close()
shutil.move(tmp, outer)
PYEOF
            else
                echo "ERROR: inner remap failed: $(basename "$inner")" >&2
                failed=$((failed + 1))
                need_build=false
                break
            fi
        done
        rm -rf "$JJ_DIR"
        $need_build || continue
    fi

    # ---- gate (always, incl. cached products) ----
    echo "==> gate $(basename "$OUT")"
    if ! "$PY" -I "$ROOT/scripts/check-official-remap.py" --gate "$OUT"; then
        echo "ERROR: gate FAILED, not deploying: $(basename "$OUT")" >&2
        failed=$((failed + 1))
        continue
    fi

    # ---- deploy (diff-checked) ----
    # A "!"-prefixed deploy name marks a classpath-only artifact (the KFF dev
    # set): build + gate as usual, but never copy into run/mods — the mods
    # folder would double-register kffmod while its language provider is only
    # discoverable on the FG7 dev classpath (no build.gradle wiring shipped;
    # dormant until the KFF Gradle plugin lands).
    if [[ "$deploy_name" == "!"* ]]; then
        echo "no-deploy (classpath artifact): ${deploy_name#!}"
    elif [[ ! -f "$MODS_DIR/$deploy_name" ]] || ! cmp -s "$OUT" "$MODS_DIR/$deploy_name"; then
        cp "$OUT" "$MODS_DIR/$deploy_name"
        echo "deployed: $deploy_name"
        deployed=$((deployed + 1))
    fi

    # ---- stamp last: full success is the only cacheable state ----
    if $need_build; then
        printf '%s\n' "$FINGERPRINT" > "$STAMP.tmp"
        mv "$STAMP.tmp" "$STAMP"
        built=$((built + 1))
    fi
done < "$MANIFEST"

# ---- stale products not in the manifest (warn only, never auto-delete) ----
while IFS= read -r stray; do
    echo "WARN: run/mods product not in manifest: $(basename "$stray")"
done < <(find "$MODS_DIR" -maxdepth 1 -name '*-official.jar' | while read -r f; do
    hit=0
    while IFS=$'\t' read -r src_rel deploy_name jarjars; do
        case "${src_rel}" in ''|'#'*) continue;; esac
        [[ "$(basename "$f")" == "$deploy_name" ]] && hit=1
    done < "$MANIFEST"
    [[ "$hit" == 0 ]] && echo "$f"
done)

echo ""
echo "Summary: built=$built skipped=$skipped deployed=$deployed failed=$failed"
[[ "$failed" == 0 ]] || exit 1
echo "Done."
