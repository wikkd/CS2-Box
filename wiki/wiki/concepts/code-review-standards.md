---
type: concept
title: 代码审查标准
updated: 2026-08-10
tags: [process, quality, review]
---

# 代码审查标准

## Overview

CS2-Box 把项目固有风险（跨版本镜像、架构约束、并发权威、渲染状态）转化为**显式、可勾选**的代码审查项，使质量验收从「靠个人经验」变为「可预期、可自动化门禁」。完整规范见 [[code-review]]，落地门禁见 [[ci-protection]]。

## Details

- **优先级**：🔴 Blocker / 🟡 Suggestion / 💭 Nit；合并硬条件 = CI 全绿 + 无未解决 Blocker + ≥1 Reviewer 批准。
- **CS2-Box 专属必查面**（命中即升级优先级）：
  1. CONSTRAINT-001 — `common/` 不得 import `net.minecraft.*` / `net.neoforged.*`（[[multiloader-architecture]]）
  2. 多平台镜像纪律 — 禁整文件覆盖 `v26_2`，纯新增走 `mirror.sh`，适配差异走定点合入（[[platform-mirror-discipline]]）
  3. 版本号四同步 — `gradle.properties` + `mods.toml`(`${mod_version}`) + `CHANGELOG` + `README`（[[version-sync]]）
  4. AnimRenderOps 漂移 — 改门面须三平台同步 13 op（[[anim-render-ops]]）
  5. 配置同步 — `CONFIG` 无 null 守卫（[[csbox-config]]）
  6. TACZ 软依赖守卫 — `isLoaded("tacz")` 内降级
  7. 并发与权威 — 服务端权威 RNG + 复核，不信任客户端数值（[[server-authoritative-rng]]、[[bulk-opening-pipeline]]）
  8. GUI/动画渲染状态 — 防 `RenderSystem` 泄漏，26.2 HUD 走 `HudVisibility`（[[hud-visibility]]）
  9. forge 实验模块边界 — 审查时忽略其编译状态
- 自动化门禁：`:common:checkCommonArchitecture` / `check-version.sh` / `check-animops-drift.sh` / `:common:test` / 3 平台 clean 编译 / `gameTestServer`。

## Platform notes

无平台差异——审查标准统一适用于 `common` 与全部活跃平台；`forge_26_1_2` 实验模块审查时忽略。

## Sources

- [[code-review]]
- [[ci-protection]]

## Related

- [[multiloader-architecture]] · [[platform-mirror-discipline]] · [[version-sync]] · [[anim-render-ops]] · [[server-authoritative-rng]] · [[csbox-config]] · [[hud-visibility]]
