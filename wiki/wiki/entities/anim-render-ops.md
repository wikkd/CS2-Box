---
type: entity
title: AnimRenderOps
kind: component
platform: all
updated: 2026-08-10
---

# AnimRenderOps

## Overview

每个平台的 `utils/AnimRenderOps.java` 是 CS2-Box **唯一的动画渲染原语适配点**（2026-08-09 重构确立）。所有 Screen（`CsboxScreen` / `CsboxProgressScreen` / `CsboxBulkOverviewScreen` / `CsboxBulkResultScreen` / `CsLookItemScreen` / `CsboxConfirmScreen`）与逻辑助手（`IconListTools` / `GuiItemMove` / `ButtonPalette`）**只经本类**调用渲染 API，版本相关的渲染差异全部收敛于此，屏与助手不得残留原始 `draw` / `RenderSystem` 调用。

## Responsibilities

对外暴露 **13 个公开 op**，跨平台签名一致：

- `blitTextured` ×3：6 参（`tex,x,y,w,h`）/ 7 参（追加 `texW,texH` 源像素尺寸）/ 13 参（UV 窗口 `u,v,uw,vh` + `texW,texH` + ARGB `tint`，供工具栏雪碧图）
- `fill` / `fillGradient`：矩形填充与渐变
- `scissor` / `scissorDisable`：裁剪区开关
- `setBlendNormal` / `flush`：混合状态复位（decoupled 下为 no-op）
- `renderBlurredBackground`：背景模糊
- `renderItem2D`：2D 物品图标（26.x 含 per-item bounding box 居中）
- `renderItem3D`：3D 拖拽预览（26.x 走 PIP 路径 `Icon3DRenderState` + `submitPictureInPictureRenderState`，radians→degrees 转换在门面内部）
- `supports3D()`：是否支持 3D 预览

## Cross-platform differences

| 平台 | era | 说明 |
|---|---|---|
| `v1_21_1` | legacy | `GuiGraphics` + 立即模式；`blitTextured` 内部强制 SRC_ALPHA blend；`renderBlurredBackground` 反射桥接；残留 `RenderSystem` 状态操作（深度测试开关、工具栏 tint 循环）为有意保留，非 draw 原语 |
| `v26_1_2` | decoupled | `GuiGraphicsExtractor` + `RenderPipelines`（自带 blend 状态）；`setBlendNormal`/`flush` 空操作；`renderBlurredBackground` → `blurBeforeThisStratum()` |
| `v26_2` | decoupled | 与 26.1.2 同代，整文件镜像；HUD 差异由 `HudVisibility` 承载，不入本门面 |

- 文件头 `// era: legacy|decoupled` 标注区分时代；新增渲染原语**须三平台同步补**，签名漂移由 `scripts/check-animops-drift.sh` 守护（CI `common-test` 已接线），漏改即检查失败。
- `RenderFontTool` 文本调用不入门面（各平台 `drawString` 签名一致）；1.21.1 的 TACZ 视口（`TaczInspectViewport`）是独立路径，不并入 `renderItem3D`。
- `forge_26_1_2` 实验模块另有一份副本，不参与镜像纪律。

## Sources

- [[platform-apis]]（§11 渲染门面）
- [[code-review]]（§4.4 AnimRenderOps 跨平台签名漂移）

## Related

- [[rendering-pipeline]] · [[hud-visibility]] · [[icon-list-tools]] · [[gui-item-move]] · [[button-palette]] · [[csgo-box]]
