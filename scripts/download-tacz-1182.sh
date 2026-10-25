#!/usr/bin/env bash
# Populate local-repo with the TACZ 1.18.2 jar (com.tacz:tacz:1.1.4-hotfix)
# for the forge_1_18_2 module's compileOnly dependency.
#
# Same mechanism as download-tacz-1201.sh: the jar is NOT committed (~50MB,
# repo convention `*.jar` gitignored, only the pom is), CI/local builds run
# this script before compiling forge_1_18_2. Downloaded from the official
# TACZ Modrinth release (project SzzJttH8, 1.18.2 1.1.4-hotfix line).
#
# Environment overrides:
#   TACZ_1182_URL="<direct jar url>"      (e.g. a pre-signed/localhost mirror)
#   TACZ_1182_MIRROR="https://ghproxy.vip/" (prefix mirror for the CDN URL)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VERSION="1.1.4-hotfix"
URL="${TACZ_1182_URL:-https://cdn.modrinth.com/data/SzzJttH8/versions/XELsjBdz/tacz-1.18.2-1.1.4-hotfix.jar}"
DEST_DIR="${ROOT}/local-repo/com/tacz/tacz/${VERSION}"
DEST="${DEST_DIR}/tacz-${VERSION}.jar"

download_tacz() {
    local part="${DEST}.part"
    for attempt in 1 2 3; do
        if [[ -n "${TACZ_1182_MIRROR:-}" ]]; then
            echo "Downloading TACZ ${VERSION} from ${TACZ_1182_MIRROR}${URL} (mirror attempt ${attempt}/3) ..."
            if curl -4 --http1.1 -fL --retry 3 --retry-delay 2 \
                    --connect-timeout 20 --max-time 600 -o "$part" "${TACZ_1182_MIRROR}${URL}"; then
                if unzip -tq "$part" >/dev/null 2>&1; then
                    mv "$part" "$DEST"
                    echo "Done: $DEST"
                    return 0
                fi
                echo "mirror download failed integrity check; trying direct URL ..."
            fi
        fi
        echo "Downloading TACZ ${VERSION} (~50MB) from ${URL} (curl attempt ${attempt}/3) ..."
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
else
    echo "TACZ ${VERSION} already present: $DEST"
fi

# Minimal pom so Gradle's mavenPom() metadata source resolves cleanly
# (mirrors the download-tacz-1201.sh precedent).
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

# NOTE: the 1.20.1 script also extracts the jarjar-embedded simplebedrockmodel
# supertype. Whether TACZ 1.18.2 bundles it is verified on first compile —
# if javac cannot resolve AnimateGeoItemRenderer's hierarchy, extract the same
# way from META-INF/jarjar/ here.
