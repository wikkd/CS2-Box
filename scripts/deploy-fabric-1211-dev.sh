#!/usr/bin/env bash
# Deploy the Fabric 1.21.1 dev test environment for :fabric_1_21_1.
#
# Copies the Fabric 1.21.1 builds of the third-party mods into
# fabric_1_21_1/run/mods/ and the matching compat-packs boxes into
# fabric_1_21_1/run/config/csbox/. Everything lands inside run/ (gitignored):
# no CI impact and no build.gradle changes.
#
# Mod set (verified 2026-11-22 against the published fabric.mod.json files):
#   Immersive Aircraft 1.7.0+1.21.1        (mod id immersive_aircraft)
#   Kaleidoscope Cookery Refabricated 1.6.0.9 (mod id kaleidoscope_cookery)
#   TaCZ: Refabricated 0.7.1               (mod id tacz; local-repo, see
#                                           scripts/download-tacz-fabric.sh)
#   Forge Config API Port 21.1.6           (required by Cookery Refabricated
#                                           and TaCZ Refabricated)
#
# fabric-api is NOT copied: Loom already puts fabric-api 0.116.17 on the dev
# classpath, and a second fabric-api jar in run/mods is a duplicate-mod crash.
#
# Loader note: Cookery Refabricated 1.6.0.9 needs fabricloader >=0.19 and
# TaCZ Refabricated needs >=0.17.2, so gradle.properties pins
# fabric_loader_version=0.19.5. The mod's own
# fabric.mod.json still declares >=0.16.0, so production players are
# unaffected. Loader 0.19.5 accepts the legacy `accessWidener v2 named`
# header (the `classTweaker` rename is a 0.20 thing), so Loom 1.17.21 works.
#
# Idempotent: re-running only copies/merges what is missing. _prices.json
# merges with "existing keys win", so the test-pack prices stay authoritative.
set -euo pipefail
cd "$(dirname "$0")/.."

RUN_DIR=fabric_1_21_1/run
MODS_DIR="$RUN_DIR/mods"
CONF_DIR="$RUN_DIR/config/csbox"
STAGING=local-repo/dev-mods-staging
TACZ_DIR=local-repo/com/tacz/tacz-refabricated/1.21.1-0.7.1-forge1.1.8-hotfix2
TACZ_JAR="$TACZ_DIR/tacz-refabricated-1.21.1-0.7.1-forge1.1.8-hotfix2.jar"

mkdir -p "$MODS_DIR" "$CONF_DIR"

fetch() {
  local url="$1" dest="$2"
  if [ -s "$dest" ]; then
    echo "[skip] $dest already present"
    return
  fi
  echo "[get ] $dest"
  curl -sfL --retry 3 --max-time 300 "$url" -o "$dest"
}

# --- mods ---------------------------------------------------------------
fetch "https://cdn.modrinth.com/data/x3HZvrj6/versions/xOnNs6dK/immersive_aircraft-fabric-1.7.0%2B1.21.1.jar" \
      "$STAGING/immersive_aircraft-fabric-1.7.0+1.21.1.jar"
fetch "https://cdn.modrinth.com/data/Ct11Kuii/versions/JHI49bug/kaleidoscope_cookery-1.6.0.9-fabric%2Bmc1.21.1.jar" \
      "$STAGING/kaleidoscope_cookery-1.6.0.9-fabric+mc1.21.1.jar"
fetch "https://cdn.modrinth.com/data/ohNO6lps/versions/N5qzq0XV/ForgeConfigAPIPort-v21.1.6-1.21.1-Fabric.jar" \
      "$STAGING/ForgeConfigAPIPort-v21.1.6-1.21.1-Fabric.jar"

if [ ! -s "$TACZ_JAR" ]; then
  echo "TaCZ: Refabricated is missing from local-repo; run scripts/download-tacz-fabric.sh first." >&2
  exit 1
fi

copy_mod() {
  local src="$1" name
  name=$(basename "$src")
  if [ -s "$MODS_DIR/$name" ]; then
    echo "[skip] mods/$name"
  else
    cp "$src" "$MODS_DIR/$name"
    echo "[mod ] $name"
  fi
}

copy_mod "$STAGING/immersive_aircraft-fabric-1.7.0+1.21.1.jar"
copy_mod "$STAGING/kaleidoscope_cookery-1.6.0.9-fabric+mc1.21.1.jar"
copy_mod "$STAGING/ForgeConfigAPIPort-v21.1.6-1.21.1-Fabric.jar"
copy_mod "$TACZ_JAR"

# --- boxes + prices -----------------------------------------------------
for pack in immersive-aircraft-pack kaleidoscope-cookery-pack tacz-pack; do
  for f in "compat-packs/$pack"/*.json; do
    base=$(basename "$f")
    [ "$base" = "_prices.json" ] && continue
    if [ -s "$CONF_DIR/$base" ]; then
      echo "[skip] config/$base"
    else
      cp "$f" "$CONF_DIR/$base"
      echo "[cfg ] $base"
    fi
  done
done

# Idempotent price merge: pack keys land only where absent; existing dev
# values (e.g. the fabric test pack's minecraft:iron_ingot=50) always win.
for pack in immersive-aircraft-pack kaleidoscope-cookery-pack tacz-pack; do
  python scripts/merge-prices.py "compat-packs/$pack/_prices.json" "$CONF_DIR/_prices.json"
done

echo
echo "Done. Launch with (runClient only — runServer is blocked upstream):"
echo "  ./gradlew :fabric_1_21_1:runClient -Pactive_versions=fabric-1.21.1 --max-workers=2"
echo "Then: /csbox give @p csgobox:ia_crate | csgobox:kc_crate | csgobox:gun_crate"
echo "Terminals (ia_terminal / kc_terminal / tacz_terminal) need a restart to register."
