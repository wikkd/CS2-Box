---
type: concept
title: 容器化布局（GuiRegion）
updated: 2026-08-03
tags: [gui, layout, component]
---

# 容器化布局（GuiRegion）

## Overview
`common/utils/GuiRegion` 命名区域系统，按区域划分 GUI 布局避免硬编码坐标。P1-1 容器化布局的落地产物。

## Details
- 命名区域：`title` / `preview` / `list` / `actions` / `actionPair`
- 落地 Screen：`CsboxBulkOverviewScreen` 与 `CsboxScreen`
- 配合 `OverlayColor` 三档设计 token 使用
- 1.0.8 首次引入，替代部分硬编码坐标

## Sources
- [[changelog]]

## Related
- [[rendering-pipeline]] / [[overlay-color]] / [[bulk-opening-pipeline]]