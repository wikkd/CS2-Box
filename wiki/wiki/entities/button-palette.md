---
type: entity
title: ButtonPalette
kind: class
platform: v26_1_2, v26_2
updated: 2026-08-03
---

# ButtonPalette

## Overview
v26_1_2/v26_2 专用按钮色系 token。取代硬编码 `0xFF00AA00` / `0xFFFF0000`。

## Responsibilities
- 常量：`OPEN` / `DANGER` / `DISABLED`（1.0.8 新增）
- `drawButton(...)` 统一按钮绘制（hover-aware 颜色切换）
- `isInside(...)` 点击区域检测
- 配合 `OverlayColor` 三档设计 token（surface/panel/divider）使用

## Cross-platform differences
- 仅 26.x 有；1.21.x 仍用硬编码色值

## Sources
- [[port-26-1-2]] / [[changelog]]

## Related
- [[rendering-pipeline]] / [[icon-list-tools]]