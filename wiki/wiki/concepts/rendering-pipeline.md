---
type: concept
title: 渲染管线对比
updated: 2026-08-03
tags: [rendering, gui, decoupled]
---

# 渲染管线对比（旧 API vs decoupled）

## Overview
1.21.x（旧 API）与 26.x（decoupled API）GUI 渲染的关键差异。26.x 重写了 Minecraft 渲染引擎，大量静态 API 迁移到实例/管线模式。

## Details
| 维度 | 1.21.x（旧） | 26.x（decoupled） |
|---|---|---|
| Screen 渲染入口 | `render(GuiGraphics,...)` | `extractRenderState(GuiGraphicsExtractor,...)`（v1_21_11 起也是 decoupled） |
| 矩阵 | `PoseStack` | `Matrix3x2f`（`guiGraphics.pose()`） |
| 渲染管道 | 静态 `RenderSystem` | `RenderPipelines.GUI_TEXTURED` 等 |
| 3D 物品预览 | `BakedModel` 管线 | `PictureInPictureRenderer` + `Icon3DRenderer`（PIP 3D） |
| 2D 物品网格 | 直接 blit | `guiGraphics.item(...)`（deferred item pipeline） |
| Blit 签名 | 9-arg overload | `blit(RenderPipeline, Identifier,...)` |
| 分层 | 单 stratum | `guiGraphics.nextStratum()`（引擎控制排序） |
| HUD 隐藏 | `Options.hideGui` | 26.2 移除 → `HudVisibility`（`Minecraft.gui.hud.toggle()/isHidden()`） |

## Platform notes
- v26_2 破坏性变更：`PictureInPictureRenderer` 构造器不再接收 `MultiBufferSource`（`renderToTexture(state, poseStack, SubmitNodeCollector)`，`featureRenderDispatcher.renderAllFeatures()` 父类触发）；`setScreenAndShow`；`CriterionTrigger` 抽象类 → interface；`GameRenderer.getLighting()` → `lighting()`
- `Icon3DRenderer` 26.2 完全重写（106→99 LOC）：`scale(1,-1,-1)` + `Axis.{XP,YP,ZP}.rotationDegrees` 驱动旋转
- 1.21.1~1.21.5 无 `ItemModelResolver` API → `IconListTools` 保持原锚定绘制（per-item 居中仅 26.x + 1.21.8/10/11）

## Sources
- [[architecture]] / [[changelog]]

## Related
- [[multiloader-architecture]] / [[platform-mirror-discipline]] / [[hud-visibility]] / [[icon-list-tools]]
