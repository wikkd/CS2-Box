---
type: concept
title: 设计 Token（OverlayColor）
updated: 2026-08-03
tags: [gui, design, tokens]
---

# 设计 Token（OverlayColor）

## Overview
`common/utils/OverlayColor` 三档设计 token 系统，提供一致的视觉颜色体系。P2-2 的落地产物。

## Details
- 三档：`surface` / `panel` / `divider`
- 1.0.8 扩展：`panelHover` / `panelPressed` / `panelDisabled`
- 配合 `ButtonPalette.DISABLED` 使用
- 与 `ColorTools` 同属 `common/utils/` 首批 A 类迁移文件

## Sources
- [[changelog]]

## Related
- [[gui-region]] / [[button-palette]] / [[rendering-pipeline]]