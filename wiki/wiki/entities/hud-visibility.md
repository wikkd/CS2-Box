---
type: entity
title: HudVisibility.java
kind: class
platform: v26_2
updated: 2026-08-03
---

# HudVisibility.java

## Overview
仅 v26_2 的 HUD 隐藏工具类。MC 26.2 移除了 `Options.hideGui` 字段，用 `Minecraft.gui.hud.toggle()/isHidden()` 包装。

## Responsibilities
- 提供 `set` 语义的 HUD 隐藏/恢复
- 开箱动画屏自动隐藏 hotbar/血条
- 消除 1.0.6 遗留降级（v26_2 曾因 `Options.hideGui` 缺失而无法隐藏 HUD，用户接受降级）

## Cross-platform differences
- 仅 v26_2 需要；其他平台用 `Options.hideGui`

## Sources
- [[changelog]]

## Related
- [[rendering-pipeline]]