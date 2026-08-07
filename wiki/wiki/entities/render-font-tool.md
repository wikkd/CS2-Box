---
type: entity
title: RenderFontTool
kind: class
platform: v26_1_2, v26_2
updated: 2026-08-03
---

# RenderFontTool

## Overview
v26_1_2/v26_2 文字渲染工具。提供 `drawString` 和 `drawStringClamped`（限宽省略号）。

## Responsibilities
- `drawString(...)`：返回 `Math.round(width * scale)`（修复 26.1.2 移植时返回 0 破坏居中计算的问题）
- `drawStringClamped(...)`：二分截断 + `"…"` 后缀，防止长物品名溢出
- 空字体保护：`Minecraft.getInstance().font` 回退（修复 1.0.4 字体 null 崩溃）

## Cross-platform differences
- 仅 26.x 有；1.21.x 用原版 `Font.draw` 直接渲染

## Sources
- [[port-26-1-2]] / [[changelog]]

## Related
- [[rendering-pipeline]] / [[button-palette]]