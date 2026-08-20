#!/usr/bin/env bash
# Download the TACZ (Timeless & Classics Guns: Zero) official 1.20.1 Forge
# jar into local-repo so :forge_1_20_1 can resolve its compileOnly dependency
# (used by the TACZ item validation + inspect viewport integration).
#
# The jar (~55MB) is NOT committed to git (see .gitignore). Run this script
# once before the first local build of :forge_1_20_1; CI runs it automatically.
# Downloads are only accepted after an integrity check: Modrinth CDN streams
# can arrive byte-count-correct but corrupt mid-stream on flaky networks, so a
# jar that fails `unzip -tq` is re-downloaded (curl HTTP/1.1).
#
# Source: Modrinth project timeless-and-classics-zero (official 1.20.1 line,
# version 1.1.8-hotfix, same 1.1.x API family as the 1.21.1 port used by
# :v1_21_1). Mirror the curseforge file id 8141310 / modrinth version
# yOVIzIJR. Slow CDN? Set TACZ_1201_MIRROR to a proxy prefix, e.g.
#   TACZ_1201_MIRROR="https://ghproxy.vip/" TACZ_1201_URL="<presigned>" ...
set -euo pipefail

VERSION="1.1.8-hotfix"
URL="${TACZ_1201_URL:-https://cdn.modrinth.com/data/SzzJttH8/versions/yOVIzIJR/tacz-1.20.1-${VERSION}.jar}"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST_DIR="${ROOT}/local-repo/com/tacz/tacz/${VERSION}"
DEST="${DEST_DIR}/tacz-${VERSION}.jar"

download_tacz() {
    local part="${DEST}.part"
    local attempt
    for attempt in 1 2 3; do
        echo "Downloading TACZ ${VERSION} (~55MB) from ${URL} (curl attempt ${attempt}/3) ..."
        # -4: force IPv4 (IPv6 to CDNs can time out on some networks)
        # --http1.1: HTTP2 framing is unreliable on some networks; no -C -
        # resume either, since a resumed stream can silently corrupt content.
        if curl -4 --http1.1 -fL --retry 3 --retry-delay 2 \
                --connect-timeout 20 --max-time 600 -o "$part" "$URL"; then
            if unzip -tq "$part" >/dev/null 2>&1; then
                mv "$part" "$DEST"
                echo "Done: $DEST"
                return 0
            fi
            echo "curl download failed integrity check; retrying from scratch ..."
        fi
    done
    echo "ERROR: could not download a valid TACZ jar after all attempts." >&2
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
# (mirrors the v1_21_1 download-tacz.sh precedent).
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
SBM_VERSION="2.2.2-forge+mc1.20.1"
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