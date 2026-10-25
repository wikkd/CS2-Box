#!/usr/bin/env bash
# Download the TaCZ: Refabricated jar (Fabric port of TACZ for 1.21.1) into
# local-repo so :fabric_1_21_1 can resolve its modCompileOnly dependency
# (inspect viewport / 3D gun previews / /csbox tacz browser).
#
# The jar (~58MB) is NOT committed to git (see .gitignore). Run this script
# once before the first local build of :fabric_1_21_1; CI runs it
# automatically. Downloads are only accepted after an integrity check
# (`unzip -tq`), mirroring scripts/download-tacz.sh.
#
# Source: https://github.com/Sh1roCu/TACZ-Refabricated (mod id "tacz",
# version 1.21.1-0.7.1-forge1.1.8-hotfix2, tracking upstream TACZ 1.1.8).
# Primary route is cursemaven (CurseForge project 1334246, file 9043429);
# the Modrinth CDN is the fallback for networks where cursemaven is blocked.
set -euo pipefail

VERSION="1.21.1-0.7.1-forge1.1.8-hotfix2"
CF_SLUG_ID="tacz-refabricated-1334246"
CF_FILE_ID="9043429"
MODRINTH_URL="https://cdn.modrinth.com/data/1j76DVHU/versions/veM4XOhW/TACZ-Refabricated-${VERSION}.jar"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST_DIR="${ROOT}/local-repo/com/tacz/tacz-refabricated/${VERSION}"
DEST="${DEST_DIR}/tacz-refabricated-${VERSION}.jar"

download_tacz() {
    local part="${DEST}.part"
    local attempt
    for attempt in 1 2 3; do
        local url="https://cursemaven.com/curse/maven/${CF_SLUG_ID}/${CF_FILE_ID}/${CF_SLUG_ID}-${CF_FILE_ID}.jar"
        if [[ "$attempt" -eq 3 ]]; then
            # cursemaven failed twice; try the Modrinth CDN before giving up.
            url="${MODRINTH_URL}"
        fi
        echo "Downloading TaCZ: Refabricated ${VERSION} (~58MB) from ${url} (attempt ${attempt}/3) ..."
        # -4: force IPv4 (IPv6 to release CDNs times out on some networks);
        # --http1.1: HTTP2 framing is unreliable on some networks; no -C -
        # resume either, since a resumed stream can silently corrupt content.
        if curl -4 --http1.1 -fL --retry 3 --retry-delay 2 \
                --connect-timeout 20 --max-time 600 -o "$part" "$url"; then
            if unzip -tq "$part" >/dev/null 2>&1; then
                mv "$part" "$DEST"
                echo "Done: $DEST"
                return 0
            fi
            echo "download failed integrity check; retrying from scratch ..."
        fi
    done
    echo "ERROR: could not download a valid TaCZ: Refabricated jar after all attempts." >&2
    return 1
}

if [[ ! -f "$DEST" ]] || ! unzip -tq "$DEST" >/dev/null 2>&1; then
    if [[ -f "$DEST" ]]; then
        echo "Existing jar failed integrity check (unzip -tq); re-downloading."
    fi
    mkdir -p "$DEST_DIR"
    download_tacz
fi

# Minimal pom so Gradle's mavenPom() metadata source resolves cleanly
# (mirrors the scripts/download-tacz.sh precedent).
POM="${DEST_DIR}/tacz-refabricated-${VERSION}.pom"
if [[ ! -f "$POM" ]]; then
    cat > "$POM" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<project xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd"
         xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.tacz</groupId>
  <artifactId>tacz-refabricated</artifactId>
  <version>${VERSION}</version>
</project>
EOF
    echo "Wrote minimal pom: $POM"
fi

# Extract the jarjar-embedded simplebedrockmodel library: javac needs its
# IFPGeoItemRenderer interface to resolve AnimateGeoItemRenderer's hierarchy
# (same mechanism as the v1_21_1 NeoForge module). Like all local-repo jars
# it is not committed (*.jar is gitignored), so CI regenerates it here
# together with a minimal pom.
SBM_VERSION="2.3.0.1+mc1.21.1"
SBM_DIR="${ROOT}/local-repo/com/github/mcmodderanchor/simplebedrockmodel-fabric/${SBM_VERSION}"
SBM_JAR="${SBM_DIR}/simplebedrockmodel-fabric-${SBM_VERSION}.jar"
if [[ ! -f "$SBM_JAR" ]]; then
    mkdir -p "$SBM_DIR"
    unzip -p "$DEST" "META-INF/jars/simplebedrockmodel-fabric-${SBM_VERSION}.jar" > "$SBM_JAR"
    echo "Extracted: $SBM_JAR"
fi
SBM_POM="${SBM_DIR}/simplebedrockmodel-fabric-${SBM_VERSION}.pom"
if [[ ! -f "$SBM_POM" ]]; then
    cat > "$SBM_POM" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<project xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd"
         xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.github.mcmodderanchor</groupId>
  <artifactId>simplebedrockmodel-fabric</artifactId>
  <version>${SBM_VERSION}</version>
</project>
EOF
    echo "Wrote minimal pom: $SBM_POM"
fi
