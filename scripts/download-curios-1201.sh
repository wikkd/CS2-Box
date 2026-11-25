#!/usr/bin/env bash
# Download Curios API (饰品栏) forge 1.20.1 5.14.1 into local-repo and run the
# shared SRG->official remap pipeline, so :forge_1_20_1 can resolve its
# compileOnly dependency (`top.theillusivec4:curios:5.14.1-1.20.1`, used by
# the curios wearable player preview) and the dev runtime keeps its
# run/mods/curios-forge-5.14.1+1.20.1-official.jar (Artifacts prerequisite).
#
# The jars are NOT committed (*.jar is gitignored). The compileOnly artifact
# is the official-mapped product of scripts/remap-1201.sh — the pipeline is
# stamp-cached, so on an already-remapped workspace this script only copies
# the existing product into the maven layout.
#
# Source: Modrinth project curios (vuO3ImH), forge 1.20.1 line 5.14.1.
set -euo pipefail

VERSION="5.14.1+1.20.1"
URL="${CURIOS_1201_URL:-https://cdn.modrinth.com/data/vvuO3ImH/versions/IPQlZkz1/curios-forge-5.14.1%2B1.20.1.jar}"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC_DIR="${ROOT}/local-repo/com/kaleidoscope/1.20.1"
SRC="${SRC_DIR}/curios-forge-${VERSION}.jar"

download_curios() {
    local part="${SRC}.part"
    local attempt
    for attempt in 1 2 3; do
        echo "Downloading Curios ${VERSION} from ${URL} (curl attempt ${attempt}/3) ..."
        # -4/--http1.1 and no resume: same integrity reasoning as
        # download-tacz-1201.sh (resumed or h2 streams can corrupt silently).
        if curl -4 --http1.1 -fL --retry 3 --retry-delay 2 \
                --connect-timeout 20 --max-time 300 -o "$part" "$URL"; then
            if unzip -tq "$part" >/dev/null 2>&1; then
                mkdir -p "$SRC_DIR"
                mv "$part" "$SRC"
                echo "Done: $SRC"
                return 0
            fi
            echo "curl download failed integrity check; retrying from scratch ..."
        fi
    done
    echo "ERROR: could not download a valid Curios jar after all attempts." >&2
    return 1
}

if [[ ! -f "$SRC" ]] || ! unzip -tq "$SRC" >/dev/null 2>&1; then
    if [[ -f "$SRC" ]]; then
        echo "Existing jar failed integrity check (unzip -tq); re-downloading."
    fi
    download_curios
fi

# Shared remap pipeline: manifest entry
#   local-repo/com/kaleidoscope/1.20.1/curios-forge-5.14.1+1.20.1.jar
# already lists this source; stamps make the run a no-op when the official
# product is current.
bash "${ROOT}/scripts/remap-1201.sh"

OFFICIAL="${SRC}/../curios-forge-${VERSION}-official.jar"
OFFICIAL="$(cd "$SRC_DIR" && pwd)/curios-forge-${VERSION}-official.jar"
MAVEN_VERSION="5.14.1-1.20.1"
MAVEN_DIR="${ROOT}/local-repo/top/theillusivec4/curios/${MAVEN_VERSION}"
MAVEN_JAR="${MAVEN_DIR}/curios-${MAVEN_VERSION}.jar"

if [[ ! -f "$OFFICIAL" ]]; then
    echo "ERROR: remap pipeline produced no official jar at $OFFICIAL" >&2
    exit 1
fi
mkdir -p "$MAVEN_DIR"
cp -f "$OFFICIAL" "$MAVEN_JAR"
echo "Installed compileOnly artifact: $MAVEN_JAR"

# Minimal pom so Gradle's mavenPom() metadata source resolves cleanly
# (mirrors the download-tacz-1201.sh precedent; the committed copy stays).
POM="${MAVEN_DIR}/curios-${MAVEN_VERSION}.pom"
if [[ ! -f "$POM" ]]; then
    cat > "$POM" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<project xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd"
         xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <modelVersion>4.0.0</modelVersion>
  <groupId>top.theillusivec4</groupId>
  <artifactId>curios</artifactId>
  <version>${MAVEN_VERSION}</version>
</project>
EOF
    echo "Wrote minimal pom: $POM"
fi
