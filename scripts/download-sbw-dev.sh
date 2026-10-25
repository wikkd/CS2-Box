#!/usr/bin/env bash
# Deploy the Superb Warfare (卓越前线) dev test environment for :v1_21_1.
#
# Downloads the two runtime jars into v1_21_1/runs/{client,server}/mods/ (the
# per-run working directories NeoGradle userdev creates — NOT the legacy
# v1_21_1/run/ from the August 2026 shader tests) and deploys the
# compat-packs/superb-warfare-pack/ boxes into both runs/<side>/config/csbox/.
# Boxes go in BEFORE the first boot so the terminal's dynamic item registers
# during mod loading (adding it after a boot requires a restart, see
# compat-packs/README.md). _prices.json entries are MERGED (existing keys win,
# e.g. the dev table's minecraft:iron_ingot=50) so the other dev boxes keep
# their pricing.
#
# Everything lands inside v1_21_1/runs/ which is gitignored: no CI impact and
# no build.gradle changes — CS2-Box's SBW integration is zero-dependency by
# design; the jars here are purely the runtime side of the test environment.
# Idempotent: re-running only fetches/copies/merges what is missing.
#
# Versions (verified 2026-10-25 against the published mods.toml files):
#   Superb Warfare 0.8.9.2  NeoForge 1.21.1  CF 1218165 file 9019824
#     (mod id "superbwarfare"; Kotlin + Kotlin for Forge;
#      geckolib/sbm/curios/cloth/flywheel/ponder/rhino are jarjar-bundled)
#   Kotlin for Forge 5.12.0                  CF 351264  file 8335665
#     (SBW declares loader "kotlinforforge" "[5.8.0,)"; 5.8.0 does NOT work
#      with NeoForge 21.1.248's FML loader 4.0.43 — kfflang 5.8.0 hard-links
#      net.neoforged.fml.Bindings, a class that only exists on newer FML;
#      5.12.0's kfflang references net.neoforged.fml.IBindingsProvider which
#      loader 4.0.43 provides. Verified by runServer 2026-10-25.)
set -euo pipefail
cd "$(dirname "$0")/.."

RUN_BASE=v1_21_1/runs
SBW_JAR=superbwarfare-0.8.9.2.jar
KFF_JAR=kotlinforforge-5.12.0.jar
mkdir -p "$RUN_BASE/client/mods" "$RUN_BASE/server/mods"

fetch() {
  local url="$1" dest="$2"
  if [ -s "$dest" ]; then
    echo "[skip] $dest already present"
    return
  fi
  echo "[get ] $dest"
  # cursemaven layout: /curse/maven/<slug>-<projectId>/<fileId>/<artifactId>-<fileId>.jar
  curl -sfL --retry 3 --max-time 300 "$url" -o "$dest"
}

for side in client server; do
  fetch "https://cursemaven.com/curse/maven/superb-warfare-1218165/9019824/superb-warfare-1218165-9019824.jar" \
        "$RUN_BASE/$side/mods/$SBW_JAR"
  fetch "https://cursemaven.com/curse/maven/kotlin-for-forge-351264/8335665/kotlin-for-forge-351264-8335665.jar" \
        "$RUN_BASE/$side/mods/$KFF_JAR"

  for jar in "$SBW_JAR" "$KFF_JAR"; do
    unzip -tq "$RUN_BASE/$side/mods/$jar" > /dev/null
  done
  echo "[ok  ] $side mods: $SBW_JAR + $KFF_JAR"
done

# Boxes + price merge for both sides (client = singleplayer test, server =
# dedicated-server test; prices in multiplayer come from the server sync).
for side in client server; do
  CONF_DIR="$RUN_BASE/$side/config/csbox"
  mkdir -p "$CONF_DIR"
  for f in sbw_crate.json sbw_terminal.json; do
    if [ -s "$CONF_DIR/$f" ]; then
      echo "[skip] $side config $f"
    else
      cp "compat-packs/superb-warfare-pack/$f" "$CONF_DIR/$f"
      echo "[cfg ] $side $f"
    fi
  done
done

# Idempotent price merge: pack keys land only where absent; existing dev
# values (e.g. minecraft:iron_ingot=50) always win.
python - "$RUN_BASE" "compat-packs/superb-warfare-pack/_prices.json" <<'PY'
import json, pathlib, sys
run_base, pack_prices = pathlib.Path(sys.argv[1]), json.load(open(sys.argv[2], encoding='utf-8'))
for side in ('client', 'server'):
    target = run_base / side / 'config' / 'csbox' / '_prices.json'
    table = json.load(open(target, encoding='utf-8')) if target.exists() and target.stat().st_size else {}
    added = {k: v for k, v in pack_prices.items() if k not in table}
    if added:
        table.update(added)
        target.write_text(json.dumps(table, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
        print(f"[cfg ] {side} _prices.json +{len(added)} keys")
    else:
        print(f"[skip] {side} _prices.json already merged")
PY

echo "Done. In IDEA run 'MC 1.21.1 - Client' (Gradle :v1_21_1:runClient),"
echo "then /give @s csgobox:sbw_crate  (or csgobox:sbw_terminal) and open it."
