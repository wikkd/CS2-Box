---
type: source
title: CsLookItemScreen 信息面板重排 实施计划
source_path: docs/superpowers/plans/2026-08-07-lookitem-info-panel.md
date_ingested: 2026-08-10
tags: [ui, cslookitem, info-panel, plan]
key_concepts: [platform-mirror-discipline, rendering-pipeline]
key_entities: [anim-render-ops, render-font-tool, csgo-box]
---

# CsLookItemScreen 信息面板重排 — 实施计划

> source: `docs/superpowers/plans/2026-08-07-lookitem-info-panel.md`（对应设计 [[lookitem-info-panel]]）

## Summary

[[lookitem-info-panel]] 的任务分解实现计划（agentic 逐任务执行）。目标：9 平台 `CsLookItemScreen` ⓘ 信息面板改 CS:GO 截图样式——无描边暗色面板、5 行「标签: 值」整行左对齐统一浅灰白、删 StatTrak 行。

## Key takeaways

- **架构**：每平台独立文件、区域结构同构（legacy `GuiGraphics` / 26.x `GuiGraphicsExtractor`）；改动 = 重写 `renderInfoPanel()` + 简化 `drawInfoRow()` + 删 StatTrak 字段/生成。逐平台定点合入（禁整文件覆盖），每平台 clean 编译。
- **范围纪律**：仅改信息面板区域；不动 `renderBg`/`renderToolbar`/`renderLabels`/物品渲染/随机值范围/磨损分级；新色常量 `0xFFCCCCCC`、面板底 `0xE0101014`、删四行描边；数值 `%.9f`/`wearTierKey()`/随机范围不变；不覆盖 `forge_26_1_2`。
- **平台映射**：legacy = v1_21_1/3/4/5/8/10，new = v26_1_2/v1_21_11/v26_2。
- **任务流**：Task1 v1_21_1 基准（删 StatTrak 字段/生成 + 重写 renderInfoPanel/drawInfoRow）→ Task2 legacy 同构合入 → Task3 v26_1_2 → Task4 1.21.11/v26_2（注意 `GuiGraphicsExtractor`）。

## Connections

- 概念：[[platform-mirror-discipline]] · [[rendering-pipeline]]
- 实体：[[anim-render-ops]] · [[render-font-tool]] · [[csgo-box]]
- 设计：[[lookitem-info-panel]]
