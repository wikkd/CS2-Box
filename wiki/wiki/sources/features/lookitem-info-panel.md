---
type: source
title: CsLookItemScreen 信息面板重排（设计）
source_path: docs/superpowers/specs/2026-08-07-lookitem-info-panel-design.md
date_ingested: 2026-08-10
tags: [ui, cslookitem, info-panel, layout]
key_concepts: [rendering-pipeline, platform-mirror-discipline]
key_entities: [anim-render-ops, render-font-tool, csgo-box]
---

# CsLookItemScreen 信息面板重排 — 设计

> source: `docs/superpowers/specs/2026-08-07-lookitem-info-panel-design.md`

## Summary

2026-08-07 设计批准。复刻 CS:GO 磨损信息界面排版：把 `CsLookItemScreen` 的 ⓘ 信息面板从「标签左对齐、值右对齐、4 条白描边」改为「无边框、5 行整条 `translatable(key,value)` 左对齐、统一浅灰白 `0xFFCCCCCC`、删除 StatTrak 行」。文案与 `zh_cn.json` 现有翻译完全一致，差异完全在排版。

## Key takeaways

- **现状问题**：`drawInfoRow` 标签/值分两侧渲染，`%s` 无参时值显示为空白，与截图单行不符；StatTrak 行（12% 概率橙色）与目标不符。
- **目标排版**：保留暗底 `0xE0101014`，删 4 条白描边；每行 `Component.translatable(key, value)` 左对齐同一 X；固定 5 行（`皮肤风格/皮肤编号/图案模板/磨损率/外观`），`rowH=13`、scale 0.7；删除 StatTrak 字段/生成/渲染。
- **数值格式不变**：`formatWear()` `%.9f`、`wearTierKey()` 分档（FN/MW/FT/WW/BS）、skinId/patternSeed 随机范围保留。
- **平台合入**：既有文件有适配差异，禁整文件覆盖，定点合入；基准 `v1_21_1`→legacy 同构→`v26_1_2`→1.21.11/v26_2（注意 `GuiGraphicsExtractor` 差异）；每平台 clean compileJava。
- **YAGNI**：不调整检视页其它 UI、不引入 common UI 抽象（CONSTRAINT-001 禁止 common import MC 类型）。

## Connections

- 概念：[[rendering-pipeline]] · [[platform-mirror-discipline]]
- 实体：[[anim-render-ops]] · [[render-font-tool]] · [[csgo-box]]
- 参考：[[toolbar-hover]] · [[code-review]]（§4.1 CONSTRAINT-001）
