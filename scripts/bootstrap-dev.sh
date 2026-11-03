#!/usr/bin/env bash
# One-shot dev-environment bootstrap for CS2-Box.
#
# Fresh clones lack the compileOnly jars (TACZ / Create) that are never
# committed to git (*.jar gitignore convention). This wrapper runs the four
# idempotent download scripts; each one skips when its artifact is present
# and passes the integrity check, so re-running is always safe. CI runs the
# same scripts explicitly before Gradle.
#
# Usage:
#   scripts/bootstrap-dev.sh                 # everything (all platforms)
#   scripts/bootstrap-dev.sh v1_21_1         # one platform:
#   scripts/bootstrap-dev.sh forge_1_20_1    #   v1_21_1 | forge_1_20_1 |
#   scripts/bootstrap-dev.sh fabric_1_21_1   #   fabric_1_21_1
#
# Slow network? Proxy envs pass through to the underlying scripts:
#   TACZ_MIRROR="https://ghproxy.vip/" scripts/bootstrap-dev.sh
# (see the download-*.sh headers for the exact variable per script).
#
# The three Gradle platform modules (v1_21_1 / forge_1_20_1 / fabric_1_21_1)
# also run this wrapper automatically as a Gradle task on the first compile,
# so `./gradlew :<module>:compileJava` works on a fresh clone without any
# manual step — this script remains the manual fallback (and covers IDE
# Gradle sync, which resolves configurations before any task runs).
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

# python discovery with a runtime probe: the Windows Store "python3" stub
# exits 9009 with a sales pitch, so existence on PATH is not enough.
pick_python() {
    for cand in python3 python "py -3"; do
        # shellcheck disable=SC2086
        if "$cand" -c 'import sys' >/dev/null 2>&1; then
            echo "$cand"
            return 0
        fi
    done
    return 1
}

PYTHON="$(pick_python)" || {
    echo "ERROR: no working python interpreter found (python3 / python / py -3)." >&2
    exit 1
}

ok=1
run() {
    local name="$1" script="$2"
    shift 2
    echo ""
    echo "==> [$name] scripts/$script $*"
    if bash "$ROOT/scripts/$script" "$@"; then
        echo "==> [$name] OK"
    else
        echo "==> [$name] FAILED" >&2
        ok=0
    fi
}

run_py() {
    local name="$1" script="$2"
    shift 2
    echo ""
    echo "==> [$name] $PYTHON scripts/$script $*"
    # shellcheck disable=SC2086
    if "$PYTHON" "$ROOT/scripts/$script" "$@"; then
        echo "==> [$name] OK"
    else
        echo "==> [$name] FAILED" >&2
        ok=0
    fi
}

case "${1:-all}" in
    v1_21_1)
        run tacz download-tacz.sh
        run_py create download-create-deps.py
        ;;
    forge_1_20_1)
        run tacz-1201 download-tacz-1201.sh
        run_py create download-create-deps.py
        ;;
    fabric_1_21_1)
        run tacz-fabric download-tacz-fabric.sh
        ;;
    all)
        run tacz download-tacz.sh
        run tacz-1201 download-tacz-1201.sh
        run tacz-fabric download-tacz-fabric.sh
        run_py create download-create-deps.py
        ;;
    *)
        echo "usage: $0 [v1_21_1|forge_1_20_1|fabric_1_21_1|all]" >&2
        exit 2
        ;;
esac

echo ""
if (( ok )); then
    echo "bootstrap: all local-repo deps present — fresh clone is build-ready."
else
    echo "bootstrap: some downloads FAILED — see the [FAILED] blocks above." >&2
    echo "  Retry directly, or set the *_MIRROR proxy vars (see script headers)." >&2
    exit 1
fi
