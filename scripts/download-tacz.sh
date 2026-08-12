#!/usr/bin/env bash
# Download the TACZ (Timeless & Classics Guns: Zero) unofficial 1.21.1
# NeoForge port jar into local-repo so :v1_21_1 can resolve its compileOnly
# dependency (used by the inspect viewport integration).
#
# The jar (~57MB) is NOT committed to git (see .gitignore). Run this script
# once before the first local build of :v1_21_1; CI runs it automatically.
#
# Source: https://github.com/MUKSC/TACZ-1.21.1 (unofficial port, mod id "tacz")
# Slow direct GitHub release assets? Set TACZ_MIRROR to a proxy prefix, e.g.
#   TACZ_MIRROR="https://ghproxy.vip/" ./scripts/download-tacz.sh
# (tested ~3.8MB/s vs ~30KB/s direct on CN networks, 2026-08)
set -euo pipefail

VERSION="1.1.8-hotfix-r6"
TAG="neoforge-${VERSION}"
MIRROR="${TACZ_MIRROR:-}"
URL="${MIRROR}https://github.com/MUKSC/TACZ-1.21.1/releases/download/${TAG}/tacz-neoforge-1.21.1-${VERSION}.jar"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST_DIR="${ROOT}/local-repo/com/tacz/tacz/${VERSION}"
DEST="${DEST_DIR}/tacz-${VERSION}.jar"

if [[ ! -f "$DEST" ]]; then
    mkdir -p "$DEST_DIR"
    echo "Downloading TACZ ${VERSION} (~57MB) from ${URL} ..."
    # -4: force IPv4 (IPv6 to GitHub release assets times out on some networks)
    curl -4 -fL --retry 3 -C - -o "${DEST}.part" "$URL"
    mv "${DEST}.part" "$DEST"
    echo "Done: $DEST"
fi

# Minimal pom so Gradle's mavenPom() metadata source resolves cleanly
# (mirrors local-repo/org/lwjgl/lwjgl-freetype/3.3.3/lwjgl-freetype-*.pom precedent).
POM="${DEST_DIR}/tacz-${VERSION}.pom"
if [[ ! -f "$POM" ]]; then
    cat > "$POM" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<project xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd"
         xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.tacz</groupId>
  <artifactId>tacz</artifactId>
  <version>${VERSION}</version>
</project>
EOF
    echo "Wrote minimal pom: $POM"
fi

# Extract the jarjar-embedded simplebedrockmodel library: javac needs its
# IFPGeoItemRenderer interface to resolve AnimateGeoItemRenderer's hierarchy.
# Like all local-repo jars it is not committed (*.jar is gitignored), so CI
# regenerates it here together with a minimal pom.
SBM_VERSION="2.2.1-neoforge+mc1.21.1"
SBM_DIR="${ROOT}/local-repo/com/github/mcmodderanchor/simplebedrockmodel/${SBM_VERSION}"
SBM_JAR="${SBM_DIR}/simplebedrockmodel-${SBM_VERSION}.jar"
if [[ ! -f "$SBM_JAR" ]]; then
    mkdir -p "$SBM_DIR"
    unzip -p "$DEST" "META-INF/jarjar/simplebedrockmodel-${SBM_VERSION}.jar" > "$SBM_JAR"
    echo "Extracted: $SBM_JAR"
fi
SBM_POM="${SBM_DIR}/simplebedrockmodel-${SBM_VERSION}.pom"
if [[ ! -f "$SBM_POM" ]]; then
    cat > "$SBM_POM" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<project xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd"
         xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.github.mcmodderanchor</groupId>
  <artifactId>simplebedrockmodel</artifactId>
  <version>${SBM_VERSION}</version>
</project>
EOF
    echo "Wrote minimal pom: $SBM_POM"
fi
