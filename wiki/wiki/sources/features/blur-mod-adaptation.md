---
type: source
title: CS2-Box × Blur 背景模糊适配（设计）
source_path: docs/superpowers/specs/2026-08-10-blur-mod-adaptation-design.md
date_ingested: 2026-08-10
tags: [ui, blur, background, compatibility]
key_concepts: [rendering-pipeline, platform-mirror-discipline, code-review-standards]
key_entities: [anim-render-ops, csbox-config, csgo-box]
---

# CS2-Box × Blur 背景模糊适配 — 设计

> source: `docs/superpowers/specs/2026-08-10-blur-mod-adaptation-design.md`

## Summary

2026-08-10 批准。让 CS2-Box 的 5 个 GUI 屏（主屏/出货/批量总览/批量结果/确认屏）与 [Motschen/Blur](https://github.com/Motschen/Blur) 模组（mod id `blur`）良好协作：模糊带淡入动画、尊重用户 blurriness 设置。**软适配**——无 blur 依赖声明，除一处 `ModList.isLoaded("blur")` 外无 Blur 引用；无 Blur 时退化为 vanilla 行为。范围 3 平台，common 仅 `OverlayColor` 新增一色，服务端/网络零改动。

## Key takeaways

- **机制**：Blur 完全靠 mixin 钩住 vanilla 背景渲染路径（`extractBlurredBackground`/`renderBlurredBackground` 等），**只有屏幕走 vanilla 背景调用路径 Blur 才生效**；屏幕 override 且不调 super 则钩子不触发。
- **现状问题**：CsboxScreen 用不透明 `0xFF2a2a33` 覆盖绕过 Blur；CsboxProgressScreen 26.x override 绕过 mixin（legacy 反射调用天然生效）；其余屏 vanilla 路径但被不透明层盖住（无效渲染）。
- **方案**：让屏幕**回归 vanilla 背景管线**（Blur 钩子自然生效），CS2-Box 覆盖层改半透明。新增 `ui` 配置节 `backgroundStyle`（OPAQUE / TRANSLUCENT，默认半透明）；common `OverlayColor.getBackgroundTranslucent()` → `0x8C2a2a33`。
- **CsboxProgressScreen（仅 26.x）**：`extractBlurredBackground` override 加 Blur 分支——`isLoaded("blur")` 调 `super` 拿淡入动画+用户半径，否则 `AnimRenderOps.renderBlurredBackground` 维持"强制模糊"现状。
- **TerminalScreen 零改动**（不透明风格保留）；无新增 AnimRenderOps op → 漂移检查不受影响；三平台 clean 编译验证。

## Connections

- 概念：[[rendering-pipeline]] · [[platform-mirror-discipline]] · [[code-review-standards]]
- 实体：[[anim-render-ops]] · [[csbox-config]] · [[csgo-box]]
- 参考：[[code-review]]（§4.8 渲染状态）· [[configuration]]
