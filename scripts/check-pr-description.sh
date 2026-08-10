#!/usr/bin/env bash
# Validate that a PR description follows the CS2-Box PR template
# (docs/CODE-REVIEW.md §6.1 / .github/PULL_REQUEST_TEMPLATE.md).
#
# Usage: scripts/check-pr-description.sh [pr-body-file]
# Reads the body from $1 or stdin. Does NOT modify any file.
# Exit 0 = pass, 1 = fail (soft gate — keeps the review flow usable).
set -uo pipefail

body="$(cat "${1:-/dev/stdin}")"
fail=0
warn=0

if [ -z "$body" ]; then
  echo "FAIL: PR 描述为空，请使用 .github/PULL_REQUEST_TEMPLATE.md 填写"
  fail=1
fi

len="$(printf '%s' "$body" | tr -d '[:space:]' | wc -c | tr -d ' ')"
if [ "$len" -lt 40 ]; then
  echo "FAIL: PR 描述过短（${len} 字符），请填写「改动说明 / 影响平台 / 测试说明」"
  fail=1
fi

printf '%s' "$body" | grep -q '## 改动说明' || { echo "FAIL: 缺少「## 改动说明」小节"; fail=1; }
printf '%s' "$body" | grep -q '作者自查' || { echo "FAIL: 缺少「作者自查」清单（对照 docs/CODE-REVIEW.md §6.1）"; fail=1; }

# "影响平台" section must have at least one checked box
platform="$(printf '%s' "$body" | sed -n '/## 影响平台/,/## 作者自查/p')"
platform_checked="$(printf '%s' "$platform" | grep -c -- '- \[x\]' || true)"
if [ "$platform_checked" -lt 1 ]; then
  echo "FAIL: 「影响平台」至少勾选一项（common / v1_21_1 / v26_1_2 / v26_2 / 仅文档）"
  fail=1
fi

# Soft signal: encourage the self-check, but don't block on it
checked="$(printf '%s' "$body" | grep -c -- '- \[x\]' || true)"
unchecked="$(printf '%s' "$body" | grep -c -- '- \[ \]' || true)"
if [ "$checked" -lt 3 ]; then
  echo "WARN: 自查项仅勾选 ${checked} 项（建议 ≥3），请对照 docs/CODE-REVIEW.md §6.1 逐项确认"
  warn=1
fi

echo "----"
echo "PR 描述检查：勾选 ${checked} / 未勾选 ${unchecked} / 影响平台勾选 ${platform_checked}"
if [ "$fail" -eq 0 ] && [ "$warn" -eq 0 ]; then
  echo "PASS: PR 描述符合模板要求"
elif [ "$fail" -eq 0 ]; then
  echo "PASS (with warnings): 模板已填，仍有未确认项建议作者自查"
else
  echo "FAIL: 请按 .github/PULL_REQUEST_TEMPLATE.md 补充描述后重新提交"
fi

exit "$fail"
