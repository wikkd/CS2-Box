---
type: concept
title: Easing 公共缓动库
updated: 2026-08-10
tags: [animation, easing, common, refactor]
---

# Easing 公共缓动库

## Overview

`common/src/main/java/com/reclizer/csgobox/utils/Easing.java`——CS2-Box 把散落在各平台、终端的缓动曲线收敛出的 **common 共享纯函数库**，供全部 GUI 屏（tick 驱动）与终端（墙钟驱动）统一调用。因是纯 Java、不 import 任何 MC/NeoForge 类型，符合 CONSTRAINT-001，可安全置于 `common/`，10 平台共享一份实现（见 [[gui-animation-gaps]]）。

## Details

- **公开 API**（全部 `static float`）：`clamp01(float)`、`easeOutCubic(float)`、`easeOutQuad(float)`、`easeOutBack(float)`、`smoothstep(float,float,float)`、`cubicBezierCurve(float)`。
- **来源**：从 `terminal/TerminalAnims` 迁移曲线，避免平台各自手写 ease 公式导致行为漂移。TDD 落地：`common/src/test/java/.../EasingTest.java`（JUnit 5）先写失败测试再实现。
- **纪律**：后续全部动画的**唯一曲线入口 = `Easing.easeOutCubic(t)`**；禁止新写手写 ease 公式（[[gui-animation-gaps]] 约束）。
- **与渲染门面关系**：Easing 管"数值缓动"，[[anim-render-ops]] 管"绘制原语"，两者正交——GUI 动效 = Easing（common 纯数学）驱动 + AnimRenderOps（平台原语）绘制。

## Platform notes

无平台差异——库在 common，编译期即被全部平台复用；GUI 屏用 game tick、终端用 wall-clock 仅时间源不同，曲线本身一致。

## Sources

- [[gui-animation-gaps]]

## Related

- [[anim-render-ops]] · [[rendering-pipeline]] · [[multiloader-architecture]] · [[csgo-box]
