---
type: entity
title: IconListTools.java
kind: class
platform: all
updated: 2026-08-03
---

# IconListTools.java

## Overview
2D 物品网格渲染工具。26.x/1.21.8+ 支持 per-item bounding box 居中。

## Responsibilities
- `renderGuiItem`：用 `ItemModelResolver` + `getModelBoundingBox()` 测量模型范围并居中（26.x + 1.21.8/10/11）
- 1.21.1~1.21.5 无 `ItemModelResolver` API → 保持原锚定绘制

## Cross-platform differences
- per-item 居中仅 26.x + 1.21.8/10/11 支持
- 1.21.1~1.21.5 无 ItemModelResolver API

## Sources
- [[changelog]]

## Related
- [[rendering-pipeline]]