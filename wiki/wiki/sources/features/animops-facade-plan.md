---
type: source
title: AnimRenderOps 动画渲染门面 实施计划
source_path: docs/superpowers/plans/2026-08-08-animops-facade.md
date_ingested: 2026-08-10
tags: [rendering, facade, plan, multiloader]
key_concepts: [multiloader-architecture, rendering-pipeline, platform-mirror-discipline]
key_entities: [anim-render-ops, icon-list-tools, gui-item-move, render-font-tool]
---

# AnimRenderOps 动画渲染门面 — 实施计划

> source: `docs/superpowers/plans/2026-08-08-animops-facade.md`（对应设计 [[animops-facade]]；已落地为实体 [[anim-render-ops]]）

## Summary

[[animops-facade]] 的任务分解实现计划（agentic 逐任务执行）。目标：把 10 平台动画渲染版本差异收敛到每平台唯一 `utils/AnimRenderOps.java` 门面，屏与助手只调门面，动画逻辑层版本无关。已分阶段实施（P0 设计 → P5 后续）。

## Key takeaways

- **架构**：屏（动画逻辑版本无关）→ 逻辑助手（IconListTools/GuiItemMove/RenderFontTool 保留布局/旋转数学）→ `AnimRenderOps`（唯一版本差异点，5 时代变体 × 10 平台）；门面守原语级，TaczInspectViewport 独立路径不入门面。
- **关键约束**：每 Gradle 调用单版本；平台改动必须 clean 编译；`common/` 禁 import MC（门面全在平台模块）；禁止 `mirror.sh --force` 跨时代覆盖（仅同时代对可镜像）；门面头注释必须有 `// era:` 标记（漂移脚本依赖）；视觉零变化（逐字搬移不"顺手修"）；不提交依赖 jar。
- **执行阶段**：P1 基线（v1_21_1 legacy + v26_1_2 decoupled）→ P2 时代铺开（mid/renderpipeline/pip/26.2）→ P3 全屏收口 → P4 漂移检查+CI（`check-animops-drift.sh`）→ P5 后续（流畅度/终端机屏/3D 全平台）。

## Connections

- 概念：[[multiloader-architecture]] · [[rendering-pipeline]] · [[platform-mirror-discipline]]
- 实体：[[anim-render-ops]] · [[icon-list-tools]] · [[gui-item-move]] · [[render-font-tool]] · [[csgo-box]]
- 设计：[[animops-facade]]
