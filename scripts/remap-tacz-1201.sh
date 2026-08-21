#!/usr/bin/env bash
#
# Produce a dev-environment-compatible ("official-mapped") copy of the TACZ
# 1.20.1 Forge jar so it can run under the ForgeGradle 7 userdev client.
#
# WHY:
#   The production TACZ 1.20.1 jar is SRG-mapped (mixin refmap targets
#   e.g. LivingEntity.m_8119_, and its bytecode references f_49792_ /
#   RangedAttribute.m_22084_ / override m_7926_). The ForgeGradle 7 dev client
#   runs with official-mapped names (LivingEntity.tick()) and has no fg.deobf,
#   so the un-remapped jar crashes at startup (mixin injection failure and
#   later NoSuchFieldError/NoSuchMethodError during registry setup).
#
#   This script reproduces what fg.deobf used to do, offline, in two steps:
#     1. scripts/TaczDeobf.java (ASM + srgutils) remaps the whole jar SRG ->
#        official: every method/field reference AND declaration (including
#        mixin @Shadow members and vanilla overrides such as
#        createBlockStateDefinition). SpecialSource was found to only remap
#        part of such jars, so it is not used here.
#     2. scripts/remap-tacz-1201.py rewrites the mixin refmap targets from SRG
#        to official names (the bytecode remapper leaves the JSON untouched).
#   Both run on the TACZ jar and on the jarjar'd simplebedrockmodel (also SRG).
#
# Prereqs (all cached locally, no internet):
#   - local-repo/com/tacz/tacz/1.1.8-hotfix/tacz-1.1.8-hotfix.jar (download-tacz-1201.sh)
#   - ASM (asm, asm-tree, asm-commons) + srgutils in the Gradle module cache
#   - mcp official->srg tsrg for 1.20.1 (already fetched by the userdev setup)
#
# Outputs:
#   local-repo/com/tacz/tacz/1.1.8-hotfix/tacz-1.1.8-hotfix-official.jar
#   forge_1_20_1/run/mods/tacz-1.20.1-1.1.8-hotfix-official.jar
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VERSION="1.1.8-hotfix"
TACZ_SRC="${ROOT}/local-repo/com/tacz/tacz/${VERSION}/tacz-${VERSION}.jar"
TACZ_OUT="${ROOT}/local-repo/com/tacz/tacz/${VERSION}/tacz-${VERSION}-official.jar"
MODS_OUT="${ROOT}/forge_1_20_1/run/mods/tacz-1.20.1-${VERSION}-official.jar"

GRADLE_CACHE="${HOME}/.gradle/caches/modules-2/files-2.1"
MCP_CACHE="${HOME}/.gradle/caches/minecraftforge/forgegradle/mavenizer/caches/mcp/de/oceanlabs/mcp/mcp_config/1.20.1-20230612.114412"
# NOTE: despite the "-obf" filename, official-1.20.1-obf.tsrg.gz is the
# official -> SRG mapping (tsrg2: "tick ()V m_8119_"). TaczDeobf reverses it
# in memory to get SRG -> official.
TSRG_GZ="${MCP_CACHE}/data/mapings/official/1.20.1/official-1.20.1-obf.tsrg.gz"

find_jar() { # "group/artifact:version"
    local spec="$1" gav ver artifact d
    gav="${spec%:*}"
    ver="${spec##*:}"
    artifact="${gav##*/}"
    d="${GRADLE_CACHE}/${gav}"
    find "$d" -name "${artifact}-${ver}.jar" ! -name '*-sources.jar' ! -name '*-javadoc.jar' 2>/dev/null | head -1
}

[[ -f "$TACZ_SRC" ]] || { echo "ERROR: $TACZ_SRC missing — run scripts/download-tacz-1201.sh first" >&2; exit 1; }
[[ -f "$TSRG_GZ" ]] || { echo "ERROR: $TSRG_GZ missing" >&2; exit 1; }

ASM="$(find_jar org.ow2.asm/asm:9.9.1)"
ASMT="$(find_jar org.ow2.asm/asm-tree:9.9.1)"
ASMC="$(find_jar org.ow2.asm/asm-commons:9.9.1)"
SRGUTILS="$(find_jar net.minecraftforge/srgutils:0.4.11)"
for v in ASM ASMT ASMC SRGUTILS; do
    [[ -n "${!v}" ]] || { echo "ERROR: could not resolve $v" >&2; exit 1; }
done
DEOBF_CP="$ASM:$ASMT:$ASMC:$SRGUTILS"

WORK="$(mktemp -d -t tacz-remap.XXXXXX)"
trap 'rm -rf "$WORK"' EXIT

echo "==> compiling TaczDeobf"
mkdir -p "$WORK/deobf-classes"
javac -cp "$DEOBF_CP" -d "$WORK/deobf-classes" "$ROOT/scripts/TaczDeobf.java"
DEOBF_CP_FULL="$DEOBF_CP:$WORK/deobf-classes"

echo "==> remapping TACZ jar (SRG -> official)"
java -Xmx2g -cp "$DEOBF_CP_FULL" TaczDeobf "$TSRG_GZ" "$TACZ_SRC" "$TACZ_OUT"

echo "==> remapping jarjar'd simplebedrockmodel"
unzip -o -q "$TACZ_OUT" 'META-INF/jarjar/*' -d "$WORK"
SBM_JAR="$WORK/META-INF/jarjar/simplebedrockmodel-2.2.2-forge+mc1.20.1.jar"
SBM_REMAP="$WORK/simplebedrockmodel-official.jar"
java -Xmx1g -cp "$DEOBF_CP_FULL" TaczDeobf "$TSRG_GZ" "$SBM_JAR" "$SBM_REMAP"

echo "==> rewriting mixin refmaps (SRG -> official)"
python3 "$ROOT/scripts/remap-tacz-1201.py" "$TSRG_GZ" "$SBM_REMAP"
python3 "$ROOT/scripts/remap-tacz-1201.py" "$TSRG_GZ" "$TACZ_OUT"

echo "==> re-embedding remapped simplebedrockmodel into TACZ"
python3 - "$TACZ_OUT" "$SBM_REMAP" <<'PY'
import shutil, sys, zipfile
tacz, sbm = sys.argv[1], sys.argv[2]
sbm_data = open(sbm, 'rb').read()
tmp = tacz + '.tmp.jar'
zin = zipfile.ZipFile(tacz, 'r')
with zipfile.ZipFile(tmp, 'w', zipfile.ZIP_DEFLATED) as zout:
    for item in zin.infolist():
        if item.filename == 'META-INF/jarjar/simplebedrockmodel-2.2.2-forge+mc1.20.1.jar':
            zout.writestr(item, sbm_data)
        else:
            zout.writestr(item, zin.read(item.filename))
zin.close()
shutil.move(tmp, tacz)
print('re-embedded', len(sbm_data), 'bytes')
PY

echo "==> deploying to dev run mods"
mkdir -p "$(dirname "$MODS_OUT")"
cp "$TACZ_OUT" "$MODS_OUT"
echo "Done:"
echo "  $TACZ_OUT"
echo "  $MODS_OUT"
