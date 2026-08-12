---
type: source
title: CsLookItemScreen 工具栏悬停反馈（设计）
source_path: docs/superpowers/specs/2026-08-07-toolbar-hover-design.md
date_ingested: 2026-08-10
tags: [ui, animation, hover, cslookitem]
key_concepts: [rendering-pipeline, code-review-standards]
key_entities: [anim-render-ops, render-font-tool, csgo-box]
---

# CsLookItemScreen 工具栏悬停反馈 — 设计

> source: `docs/superpowers/specs/2026-08-07-toolbar-hover-design.md`

## Summary

2026-08-07 设计批准。优化 `CsLookItemScreen` 底部 6 个工具栏按钮（检视/手套/模型/信息/贴纸/更多）的悬停交互：**tooltip 提示 + 图标反白高亮动画**，背景不动，保持 CS:GO 质感。关键发现：图标贴图本身是纯白色，故"反白"= 常态 55% 亮度、悬停平滑提亮至纯白。

## Key takeaways

- **状态字段**（11 平台同构）：`hoveredButton`（当前悬停 index）、`toolbarGlow`（0→1 平滑系数，tick 内帧率无关插值 ~200ms 趋近 90%）。
- **图标反白**：`hoveredButton` 命中图标亮度 `0.55 + 0.45*toolbarGlow`；ⓘ active 恒全亮。legacy 用 `RenderSystem.setShaderColor`，26.x 用 `blit` 的 color 参数（无 RenderSystem）——均经 [[anim-render-ops]] 门面。
- **Tooltip**：按钮上方固定标签，`toolbarGlow>0.05` 触发，暗色矩形 alpha 随 glow 淡入，复用 `renderText`。
- **本地化**：`common` 共享 6 个 `gui.csgobox.csgo_box.toolbar.<name>` lang key，一次改全平台。
- **YAGNI**：不给 5 个死按钮分配功能、不改背景 hover 变色、不加快捷键。
- **平台合入**：基准 `v1_21_1`→legacy 同构→`v26_1_2`→1.21.11/v26_2/forge 合入，每平台 compileJava 验证。

## Connections

- 概念：[[rendering-pipeline]] · [[code-review-standards]]
- 实体：[[anim-render-ops]] · [[render-font-tool]] · [[csgo-box]]
- 参考：[[platform-mirror-discipline]] · [[lookitem-info-panel]]
