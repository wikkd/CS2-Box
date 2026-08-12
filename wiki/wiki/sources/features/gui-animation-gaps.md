---
type: source
title: GUI 动效补全 + Easing 提取 实施计划
source_path: docs/superpowers/plans/2026-08-10-gui-animation-gaps.md
date_ingested: 2026-08-10
tags: [ui, animation, easing, refactor]
key_concepts: [rendering-pipeline, multiloader-architecture, code-review-standards]
key_entities: [anim-render-ops, csgo-box]
---

# GUI 动效补全 + Easing 提取 实施计划

> source: `docs/superpowers/plans/2026-08-10-gui-animation-gaps.md`

## Summary

补全 CS2-Box 三平台 GUI 的 6 处动效缝隙，并把散落的 easing 曲线收敛为 **common 共享 `Easing` 库**（纯 Java，符合 CONSTRAINT-001，不 import MC 类型）。全部动画 tick 驱动，复用现有 13 个 [[anim-render-ops]] 原语，零新依赖；不新增 AnimRenderOps 原语（drift 脚本守护）。

## Key takeaways

- **Easing 库（common）**：新建 `utils/Easing.java` + `EasingTest.java`（JUnit 5 TDD）；公开纯函数 `clamp01` / `easeOutCubic` / `easeOutQuad` / `easeOutBack` / `smoothstep` / `cubicBezierCurve`；从 `terminal/TerminalAnims` 迁移曲线，全平台与终端共用一份实现。后续全部任务唯一曲线入口 = `Easing.easeOutCubic(t)`，**禁止**新写手写 ease 公式。
- **平台落地**：v26_1_2 基准，v26_2 / v1_21_1 定点适配（已归一化 diff 验证三平台同构）；禁整文件覆盖 v26_2（API 差异：`setScreen`→`setScreenAndShow`、`GuiGraphics`↔`GuiGraphicsExtractor`、tick↔ms、`BuiltInRegistries.ITEM.get()` Optional 等）。
- **时间源**：GUI 屏用 game tick；终端墙钟 ms 体系不动。
- **纪律**：`common/` 禁 import MC/NeoForge；每 Gradle 调用只构建一个版本，平台改动 `--rerun-tasks` 验证（收尾 clean）；commit 纪律严格（只 add 本任务涉及文件，禁 `git add .` / `-A`）。

## Connections

- 概念：[[rendering-pipeline]] · [[multiloader-architecture]] · [[code-review-standards]] · [[easing-library]]
- 实体：[[anim-render-ops]] · [[csgo-box]]
- 参考：[[code-review]]（§4.1/§4.4）· [[animops-facade]]
