---
type: entity
title: GuiItemMove
kind: component
platform: all
updated: 2026-08-10
---

# GuiItemMove

## Overview

`utils/GuiItemMove.java` 是开箱/检视屏的 **3D 拖拽预览逻辑助手**：负责物品在屏幕内的 3D 旋转拖拽交互。与 `IconListTools` 同属「逻辑助手」层——只做布局与旋转数学（版本无关），实际绘制经 [[anim-render-ops]]（`renderItem3D`）委托到平台渲染原语，自身不分支版本（见 [[animops-facade]] 架构）。

## Responsibilities

- **3D 拖拽预览**：`renderRotAngleX/Y` 等旋转角纯数学计算（保留在助手层，稳定）。
- **物品跟随鼠标渲染**：`renderItemInInventoryFollowsMouse`（CsLookItemScreen 检视区）；TACZ 视口激活时由 `TaczInspectViewport.renderViewport` 替代（见 [[tacz-inspect-viewport]]）。
- 1.21.3+ 灯光（`Lighting`）、1.21.11/26.x PIP 路径由 [[anim-render-ops]] 内部吸收，助手层无时代特写。

## Cross-platform differences

- 纯数学助手，跨平台同构；渲染委托门面，无时代特写（除 1.21.1 TACZ 视口独立路径）。
- 与 [[icon-list-tools]] 同理，属「布局/数学稳定、draw 进门面」原则下的逻辑助手。

## Sources

- [[platform-apis]]（文件级差异指引：`utils/GuiItemMove.java` 受 1.21.3 渲染 / 1.21.8 灯光 / 1.21.11·26 PIP 断点影响）
- [[animops-facade]]（架构：助手层委托门面）

## Related

- [[anim-render-ops]] · [[icon-list-tools]] · [[render-font-tool]] · [[csgo-box]] · [[tacz-inspect-viewport]]
