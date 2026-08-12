---
type: source
title: AnimRenderOps 动画渲染门面（设计）
source_path: docs/superpowers/specs/2026-08-08-animops-facade-design.md
date_ingested: 2026-08-10
tags: [rendering, facade, architecture, multiloader]
key_concepts: [multiloader-architecture, rendering-pipeline, platform-mirror-discipline]
key_entities: [anim-render-ops, icon-list-tools, gui-item-move, render-font-tool]
---

# AnimRenderOps 动画渲染门面 — 设计

> source: `docs/superpowers/specs/2026-08-08-animops-facade-design.md`

## Summary

2026-08-08 批准（待实施）。把 10 个平台模块的动画渲染版本适配收敛到每平台唯一的 `utils/AnimRenderOps.java`——屏幕与逻辑助手只调门面，版本差异全部入门面。战略前提：1.21.1 是长期主力、后续持续加新 MC 版本（每次都是一轮渲染 API 断点）、终端机抽卡是新屏族（第七个屏），版本适配成本是复利型支出。该设计已落地为 [[anim-render-ops]] 实体（13 公开 op，三平台签名一致）。

## Key takeaways

- **方案选型**：A 每平台渲染门面（采用）/ B common 动画引擎（否决：跨界泄漏、工作量 3-5 倍）/ C 技术收口（并入 A 作为实现子原则）。
- **架构**：6+1 屏（布局/数学版本无关，镜像即通）→ 逻辑助手（IconListTools/GuiItemMove/RenderFontTool，版本无关）→ `AnimRenderOps`（唯一版本差异点）→ pip/Icon3DRenderer（仅 1.21.11+/26.x）、compat/TaczInspectViewport（仅 1.21.1）留在内部间接调用。
- **关键原则**：布局与数学留屏/助手（稳定），draw 调用进门面（可变）；门面守**原语级**（功能级逻辑不入门面防抽象泄漏）；屏与助手**永不分支版本**，时代特有能力走 `supports3D()` 式探测；TACZ 路径不并入 `renderItem3D`。
- **5 时代变体**：legacy(1.21.0/1.21.1) / mid(1.21.3/1.21.5) / renderpipeline(1.21.8/1.21.10) / pip(1.21.11) / decoupled(26.1.2/26.2)，各吸收 blit/blend/item2D/item3D/blur 差异。
- **一次性缺陷修复**：legacy blend 残留、帧首 setShaderColor 三连、blur 4 种签名、tint/color 参数差异，全部收口进门面。
- **收益**：渲染原语适配工作量降 60-80%，缺陷单点修复，新版本接入成固定工序；风险含迁移回归（flush/blend 时序最敏感）、签名弱校验靠 `check-animops-drift.sh`。
- **执行阶段**：P0 设计 → P1 基线(v1_21_1+v26_1_2) → P2 时代铺开 → P3 全屏收口 → P4 漂移检查+CI → P5 后续（流畅度/终端机屏/3D 全平台）。

## Connections

- 概念：[[multiloader-architecture]] · [[rendering-pipeline]] · [[platform-mirror-discipline]]
- 实体：[[anim-render-ops]] · [[icon-list-tools]] · [[gui-item-move]] · [[render-font-tool]] · [[csgo-box]]
- 参考：[[platform-apis]]（§11）· [[code-review]]（§4.4）· [[hud-visibility]]
