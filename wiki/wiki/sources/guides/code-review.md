---
type: source
title: 代码审查标准与流程
source_path: docs/CODE-REVIEW.md
date_ingested: 2026-08-10
tags: [process, quality, review, ci]
key_concepts: [code-review-standards, multiloader-architecture, platform-mirror-discipline]
key_entities: [anim-render-ops, csbox-config, packet-csgo-progress, packet-csgo-bulk-progress, box-defaults]
---

# 代码审查标准与流程

> source: `docs/CODE-REVIEW.md`

## Summary

定义 CS2-Box 的代码审查标准与流程，把跨版本镜像、架构约束、并发权威、渲染状态等项目固有风险变成**显式、可勾选**的审查项。审阅者遵循「Code Review Expert」角色：每条意见具体、解释 why、给可操作建议、按优先级标注。核心概念提炼见 [[code-review-standards]]，落地门禁见 [[ci-protection]]。

## Key takeaways

- **优先级体系**：🔴 Blocker（破坏编译/架构/数据安全，不修不合并）/ 🟡 Suggestion（可靠性/可维护性隐患，需共识）/ 💭 Nit（风格，可后续）。
- **§4 专属审查清单（核心）**：
  - CONSTRAINT-001 — `common/` 不得 import `net.minecraft.*` / `net.neoforged.*`（[[multiloader-architecture]]）
  - 多平台镜像纪律 — 禁整文件覆盖 `v26_2`，纯新增走 `mirror.sh`，适配差异走定点合入（[[platform-mirror-discipline]]）
  - 版本号四同步 — `mods.toml` 保留 `${mod_version}` 模板变量（[[version-sync]]）
  - AnimRenderOps 跨平台签名漂移 — 改门面须三平台同步 13 op（[[anim-render-ops]]）
  - 配置同步 — `CONFIG` 无 null 守卫（[[csbox-config]]）
  - TACZ 软依赖守卫 — `isLoaded("tacz")` 内静默降级
  - 并发与权威 — 服务端权威 RNG + 复核，不信任客户端数值（[[server-authoritative-rng]]、[[bulk-opening-pipeline]]）
  - GUI/动画渲染状态 — 防 `RenderSystem` 状态泄漏，26.2 HUD 走 `HudVisibility`（[[hud-visibility]]）
  - forge 实验模块边界 — 审查时忽略其编译状态
- **§5 通用五维**：正确性 / 安全 / 可维护性 / 性能 / 测试（含 `BoxJsonSchemaValidatorTest`、`PlatformSmokeTest`）。
- **§6 流程**：作者自查 → PR 模板 → Reviewer 逐条核对 §4 + 五维 + 本地验证（`:common:test`、`:<m>:clean compileJava`、`gameTestServer`）→ 合并门禁（**CI 全绿 + 无未解决 Blocker + ≥1 Reviewer 批准**）。
- **§8 评论格式**：固定模板（优先级 + 文件:行 + Why + Suggestion），附 CS2-Box 示例（架构约束 / 镜像纪律 / Nit / 澄清先问）。
- **§9 反模式速查**：9 条一票告警（common import MC、整文件覆盖 `v26_2`、手改 `mods.toml` 版本、AnimRenderOps 单平台、CONFIG null 守卫、信任客户端数值、渲染状态不复位、教程刷新无回收站、阻塞主线程下载）。

## Connections

- 概念：[[code-review-standards]] · [[multiloader-architecture]] · [[platform-mirror-discipline]] · [[server-authoritative-rng]] · [[version-sync]] · [[platform-mirror-discipline]]
- 实体：[[anim-render-ops]] · [[csbox-config]] · [[packet-csgo-progress]] · [[packet-csgo-bulk-progress]] · [[box-defaults]]
- 参考：[[ci-protection]]（把 §6.4 落到分支保护）· [[architecture]] · [[configuration]] · [[testing]]
