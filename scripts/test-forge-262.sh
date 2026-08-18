#!/usr/bin/env bash
# test-forge-262.sh — forge_26_2 (MinecraftForge) 模块一键回归（测试流程 L0-L3）。
#
# 设计对齐 mc_tools 的 test_all.sh 约定（PASS/FAIL/WARN 计数、JUnit XML 报告、
# 退出码 0/1/2），但只依赖 CS2-Box 仓库自身，不需要 MCP/TestHelper（forge 平台
# 尚无法加载 NeoForge 版 TestHelper，见 docs/TESTING-FORGE-2612.md）。
#
# 阶段:
#   S1  clean + compileJava            —— 防增量缓存假象，含 common 架构约束检查
#   S2  jar + 产物校验                 —— 文件名/非空/mods.toml 版本 = mod_version
#   S3  scripts/check-version.sh       —— 版本四同步
#   S4  scripts/check-animops-drift.sh —— 三平台渲染门面漂移（forge 按设计不在内）
#   S5  :forge_26_2:test             —— PlatformSmokeTest（入口可加载 + 1.0.6 基线守卫）
#
# 用法:
#   scripts/test-forge-262.sh                 # 全量
#   scripts/test-forge-262.sh --skip-test     # 跳过 S5（例如只验证构建门禁）
#   scripts/test-forge-262.sh --skip-drift --skip-version
#   FORGE_TEST_TIMEOUT=3600 scripts/test-forge-262.sh   # 覆盖 Gradle 阶段超时（默认 1800s）
#
# 退出码: 0 = 全部通过  1 = 有用例/阶段失败  2 = 前置失败（环境/工具错误）

set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

MODULE="forge_26_2"
ACTIVE="forge-26.2"
GRADLE_ARGS=(./gradlew --console=plain -Pactive_versions="${ACTIVE}")
FORGE_TEST_TIMEOUT="${FORGE_TEST_TIMEOUT:-1800}"

SKIP_COMPILE=0
SKIP_JAR=0
SKIP_VERSION=0
SKIP_DRIFT=0
SKIP_TEST=0

for arg in "$@"; do
  case "$arg" in
    --skip-compile) SKIP_COMPILE=1 ;;
    --skip-jar)     SKIP_JAR=1 ;;
    --skip-version) SKIP_VERSION=1 ;;
    --skip-drift)   SKIP_DRIFT=1 ;;
    --skip-test)    SKIP_TEST=1 ;;
    -h|--help)
      sed -n '2,30p' "${BASH_SOURCE[0]}"
      exit 0
      ;;
    *)
      echo "ERROR: unknown option: $arg" >&2
      exit 2
      ;;
  esac
done

# ---- 结果计数与报告（mc_tools 风格） ----
PASS=0; FAIL=0; WARN=0
RESULTS=()
CASES_XML=""

log()  { echo "[$(date +%H:%M:%S)] $*"; }
pass() { PASS=$((PASS+1)); RESULTS+=("PASS  $1"); log "PASS  $1"; }
fail() { FAIL=$((FAIL+1)); RESULTS+=("FAIL  $1"); log "FAIL  $1"; }
warn() { WARN=$((WARN+1)); RESULTS+=("WARN  $1"); log "WARN  $1"; }

# run_with_timeout <sec> <cmd...> — 超时 kill 包装（兼容 macOS 无 GNU timeout）。
# 轮询式实现（每 5s 检查一次），不产生孤儿 sleep 进程；超时返回 137，正常完成
# 返回原退出码。zombie（已退出未回收）按完成处理，避免误判超时。
run_with_timeout() {
  local sec="$1" pid rc=0 waited=0 stat; shift
  "$@" &
  pid=$!
  while :; do
    if ! kill -0 "${pid}" 2>/dev/null; then
      wait "${pid}" 2>/dev/null || rc=$?
      return "${rc}"
    fi
    stat="$(ps -o stat= -p "${pid}" 2>/dev/null)"
    case "${stat}" in
      Z*) # zombie: child already exited, reap and return its real status
        wait "${pid}" 2>/dev/null || rc=$?
        return "${rc}"
        ;;
    esac
    if [ "${waited}" -ge "${sec}" ]; then
      kill -9 "${pid}" 2>/dev/null
      wait "${pid}" 2>/dev/null
      return 137
    fi
    sleep 5
    waited=$((waited+5))
  done
}

# stage <name> <desc> <exit_code_of_command>  —— 记录结果并累计 JUnit XML
stage() {
  local name="$1" desc="$2" rc="$3"
  if [ "${rc}" -eq 0 ]; then
    pass "${desc}"
    CASES_XML+="    <testcase classname=\"forge-262.${name}\" name=\"${name}\" time=\"0\"/>"$'\n'
  else
    fail "${desc} (exit ${rc})"
    CASES_XML+="    <testcase classname=\"forge-262.${name}\" name=\"${name}\" time=\"0\"><failure message=\"exit ${rc}\"/></testcase>"$'\n'
  fi
}

# ---- 前置检查（失败即 exit 2） ----
if [ ! -f "gradle.properties" ]; then
  echo "ERROR: gradle.properties not found — run from the repo root" >&2
  exit 2
fi
if [ ! -x "./gradlew" ]; then
  echo "ERROR: ./gradlew not found or not executable" >&2
  exit 2
fi
if ! command -v unzip >/dev/null 2>&1; then
  echo "ERROR: unzip not found (needed for jar content checks)" >&2
  exit 2
fi

mod_version="$(grep -E '^mod_version=' gradle.properties | head -1 | cut -d= -f2- | tr -d '[:space:]')"
mod_id="$(grep -E '^mod_id=' gradle.properties | head -1 | cut -d= -f2- | tr -d '[:space:]')"
mc="$(grep -E '^mc_version_forge_26_2=' gradle.properties | head -1 | cut -d= -f2- | tr -d '[:space:]')"
if [ -z "${mod_version}" ] || [ -z "${mod_id}" ] || [ -z "${mc}" ]; then
  echo "ERROR: cannot read mod_version/mod_id/mc_version_forge_26_2 from gradle.properties" >&2
  exit 2
fi

ARTIFACT="${MODULE}/build/libs/${mod_id}-forge-${mc}-${mod_version}.jar"
REPORT_DIR="build/test-reports"

echo "============================================================"
echo " forge_26_2 回归  |  mod_version=${mod_version}  |  artifact=${ARTIFACT}"
echo "============================================================"
echo

# ---- S1 clean + compileJava ----
if [ "${SKIP_COMPILE}" -eq 0 ]; then
  log "S1 clean + compileJava (timeout ${FORGE_TEST_TIMEOUT}s)..."
  run_with_timeout "${FORGE_TEST_TIMEOUT}" "${GRADLE_ARGS[@]}" ":${MODULE}:clean" ":${MODULE}:compileJava"
  stage S1 "clean + compileJava" "$?"
else
  warn "S1 skipped (--skip-compile)"
  CASES_XML+="    <testcase classname=\"forge-262.S1\" name=\"S1 clean+compile\" time=\"0\"><skipped/></testcase>"$'\n'
fi

# ---- S2 jar + 产物校验 ----
if [ "${SKIP_JAR}" -eq 0 ]; then
  log "S2 jar (timeout ${FORGE_TEST_TIMEOUT}s)..."
  run_with_timeout "${FORGE_TEST_TIMEOUT}" "${GRADLE_ARGS[@]}" ":${MODULE}:jar"
  rc=$?
  if [ "${rc}" -ne 0 ]; then
    stage S2 "jar 构建" "${rc}"
  else
    stage S2 "jar 构建" 0
    if [ -f "${ARTIFACT}" ] && [ -s "${ARTIFACT}" ]; then
      pass "产物存在且非空 (${ARTIFACT})"
      CASES_XML+="    <testcase classname=\"forge-262.S2\" name=\"jar-artifact\" time=\"0\"/>"$'\n'
    else
      fail "产物缺失或为空 (${ARTIFACT})"
      CASES_XML+="    <testcase classname=\"forge-262.S2\" name=\"jar-artifact\" time=\"0\"><failure message=\"missing\"/></testcase>"$'\n'
    fi
    if unzip -p "${ARTIFACT}" META-INF/mods.toml 2>/dev/null | grep -q "version=\"${mod_version}\""; then
      pass "mods.toml 版本 = ${mod_version}"
      CASES_XML+="    <testcase classname=\"forge-262.S2\" name=\"mods-toml-version\" time=\"0\"/>"$'\n'
    else
      fail "mods.toml 未展开为 version=\"${mod_version}\""
      CASES_XML+="    <testcase classname=\"forge-262.S2\" name=\"mods-toml-version\" time=\"0\"><failure message=\"version mismatch\"/></testcase>"$'\n'
    fi
  fi
else
  warn "S2 skipped (--skip-jar)"
  CASES_XML+="    <testcase classname=\"forge-262.S2\" name=\"jar\" time=\"0\"><skipped/></testcase>"$'\n'
fi

# ---- S3 版本同步 ----
if [ "${SKIP_VERSION}" -eq 0 ]; then
  log "S3 check-version.sh..."
  bash scripts/check-version.sh >/dev/null 2>&1
  stage S3 "check-version.sh (版本四同步)" "$?"
else
  warn "S3 skipped (--skip-version)"
  CASES_XML+="    <testcase classname=\"forge-262.S3\" name=\"version-sync\" time=\"0\"><skipped/></testcase>"$'\n'
fi

# ---- S4 AnimRenderOps 漂移 ----
if [ "${SKIP_DRIFT}" -eq 0 ]; then
  log "S4 check-animops-drift.sh..."
  bash scripts/check-animops-drift.sh >/dev/null 2>&1
  stage S4 "check-animops-drift.sh (3 平台渲染门面)" "$?"
else
  warn "S4 skipped (--skip-drift)"
  CASES_XML+="    <testcase classname=\"forge-262.S4\" name=\"animops-drift\" time=\"0\"><skipped/></testcase>"$'\n'
fi

# ---- S5 PlatformSmokeTest ----
if [ "${SKIP_TEST}" -eq 0 ]; then
  log "S5 :forge_26_2:test (timeout ${FORGE_TEST_TIMEOUT}s)..."
  run_with_timeout "${FORGE_TEST_TIMEOUT}" "${GRADLE_ARGS[@]}" ":${MODULE}:test"
  rc=$?
  if [ "${rc}" -ne 0 ]; then
    stage S5 "PlatformSmokeTest (Gradle test)" "${rc}"
  else
    # Gradle 返回 0 时再核对测试结果 XML，防止测试被跳过/过滤
    test_report_failures="$(grep -h -o 'failures="[0-9]*"' "${MODULE}"/build/test-results/test/TEST-*.xml 2>/dev/null | grep -v 'failures="0"' | wc -l | tr -d ' ')"
    if [ -z "${test_report_failures}" ] || [ "${test_report_failures}" -eq 0 ]; then
      stage S5 "PlatformSmokeTest (JUnit)" 0
    else
      stage S5 "PlatformSmokeTest (JUnit 报告含失败)" 1
    fi
  fi
else
  warn "S5 skipped (--skip-test)"
  CASES_XML+="    <testcase classname=\"forge-262.S5\" name=\"platform-smoke-test\" time=\"0\"><skipped/></testcase>"$'\n'
fi

# ---- 报告 ----
mkdir -p "${REPORT_DIR}"
{
  echo "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
  echo "<testsuite name=\"forge-262\" tests=\"$((PASS+FAIL))\" failures=\"${FAIL}\" skipped=\"${WARN}\">"
  printf '%s' "${CASES_XML}"
  echo "</testsuite>"
} > "${REPORT_DIR}/forge-262.xml"

echo
echo "============================================================"
echo " 结果: PASS=${PASS}  FAIL=${FAIL}  WARN=${WARN}"
echo " 报告: ${REPORT_DIR}/forge-262.xml"
for r in "${RESULTS[@]}"; do
  echo "  ${r}"
done
echo "============================================================"

if [ "${FAIL}" -gt 0 ]; then
  exit 1
fi
exit 0
