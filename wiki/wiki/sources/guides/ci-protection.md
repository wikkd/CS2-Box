---
type: source
title: CI 门禁：分支保护设置指南
source_path: docs/CI-PROTECTION.md
date_ingested: 2026-08-10
tags: [ci, github, process, quality]
key_concepts: [code-review-standards]
key_entities: []
---

# CI 门禁：分支保护设置指南

> source: `docs/CI-PROTECTION.md`

## Summary

把 [[code-review]] §6.4 的合并硬条件（CI 全绿 + 无未解决 Blocker + ≥1 Reviewer 批准）落到 GitHub 分支保护规则，使 `main` 不再接受未过门禁的代码。前置条件：仓库 admin 权限 + `gh` 已登录（`gh auth login`）。

## Key takeaways

- **§1 Required Check 清单（7 个）**：`1.21.1 (Java 21)`、`26.1.2 (Java 25)`、`26.2 (Java 25)`（`build.yml` → `build` job matrix 各组合）+ `common unit tests + checks`（`common-test` job）+ `GameTest 1.21.1` / `GameTest 26.1.2`（`gametest.yml`，无用例时成功跳过）+ `PR description check`（`pr-checks.yml`）。
- **方式 A Web UI** / **方式 B gh CLI 一键**：`gh api -X PUT .../branches/main/protection`，`required_approving_review_count=1`、`enforce_admins=false`、`allow_force_pushes=false`、`allow_deletions=false`；附验证与回滚命令。
- **§4 行为变化**：直接 push / force push 到 `main` 被拒；每个 PR 须 7 check 全过 + 1 Reviewer 批准；若某 check 名写错会「永不通过」，建议先开 test PR 核对；`pr-checks.yml` 仅在 PR 事件触发，直接 push 时不阻塞（符合预期）。
- **§5 后续演进**：补 `gameTestServer` 用例后把 `GameTest 26.2` 加入矩阵；更严格可开 `enforce_admins` 或给 `multiloader-refactor` 分支加规则。

## Connections

- 概念：[[code-review-standards]]
- 参考：[[code-review]]（§6.4 合并门禁）· [[architecture]]（CI 矩阵与 common-test）
