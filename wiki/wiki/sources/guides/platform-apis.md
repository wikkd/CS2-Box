---
type: source
title: 多平台 API 差异开发指南
source_path: docs/PLATFORM-APIS.md
date_ingested: 2026-08-10
tags: [platform, api, multiloader, migration, rendering]
key_concepts: [multiloader-architecture, platform-mirror-discipline, rendering-pipeline]
key_entities: [anim-render-ops, hud-visibility, icon-list-tools, gui-item-move, csgo-box]
---

# 多平台 API 差异开发指南

> source: `docs/PLATFORM-APIS.md`

## Summary

面向 3 个在维护平台模块（`v1_21_1` legacy、`v26_1_2` / `v26_2` decoupled）的 API 差异速查手册。按「版本分类 → 全量速查矩阵 → 主题展开」组织，帮助开发者在任何平台快速定位「哪个 API、哪个版本、怎么写」。明确 **21.x→26.x 是全面重构的 decoupled API**，**26.1.2→26.2 只是小改**；legacy 内部 6 个断点（1.21.3~1.21.11）已随 EOL 平台于 2026-08-09 归档（tag `eol-legacy-21x-1.0.6`）。

## Key takeaways

- **版本分类**：legacy(`v1_21_1`, Java21) / new(`v26_1_2`, Java25+preview) / `v26_2`(小改) / `forge_26_1_2`(实验，非正式平台)。
- **全量速查矩阵**覆盖 14 个 API 主题 × 各版本断点：`BuiltInRegistries.ITEM.get()`(→Optional)、Item 构造(注入 `Properties`+`setId`)、`renderItem`、`blit`(`RenderPipelines`)、`appendHoverText`(`TooltipDisplay`+`Consumer`)、背包遍历(`getNonEquipmentItems`)、发包(`conn.send`/`context.reply`)、Screen 事件对象化、`HUD`(`Options.hideGui`→`HudVisibility`)、attachment(`IAttachmentSerializer`+`ValueIO`)、`ResourceLocation`→`Identifier`、权限(`permissions().hasPermission`)、成就包(`critereon`→`criterion`→`predicates/triggers`)。
- **文件级差异指引**：`CsboxScreen` 等全部 Screen + `IconListTools` / `GuiItemMove` / `RenderFontTool` / `PacketCsgoProgress` / `ModCapability` / `BoxItemCodec` 是改动最重的文件，逐一列出受影响断点。
- **主题展开**共 10 节：注册表 / Screen·GUI·输入 / 渲染管线·物品渲染 / 网络层 / attachment·序列化 / 背包·Tooltip·输入 / 命令·权限·成就 / 命名结构迁移 / 26.2 专属适配 / forge 实验模块。
- **§11 AnimRenderOps 渲染门面**（2026-08-09 重构）：每平台 `utils/AnimRenderOps.java` 是**唯一**渲染原语适配点，13 个公开 op 跨平台签名一致，新增原语须三平台同步补（详见 [[anim-render-ops]]）。
- **维护约定**：新增断点先更新矩阵再补主题章节；平台 diff 以基准模块（`v1_21_1` / `v26_1_2`）为基线，用 `diff -rq` + 剥离 package 行对比；涉及平台的改动务必 **clean 编译**确认（增量缓存会造假象）。

## Connections

- 概念：[[multiloader-architecture]] · [[platform-mirror-discipline]] · [[rendering-pipeline]]
- 实体：[[anim-render-ops]] · [[hud-visibility]] · [[icon-list-tools]] · [[gui-item-move]] · [[csgo-box]]
- 对比：[[1-21-1-vs-26-1-2-gui]] · [[v26-1-2-vs-v26-2]]
- 参考：[[code-review]]（§4.4 漂移审查）· [[architecture]] · [[port-26-1-2]]
