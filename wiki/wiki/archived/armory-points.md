---
type: source
title: 武库点数（Armory Point）实施计划
source_path: docs/superpowers/plans/2026-08-09-armory-points.md
date_ingested: 2026-08-10
tags: [feature, item, economy, resource]
key_concepts: [arms-dealer-economy-design, multiloader-architecture]
key_entities: [csbox-config, csgo-box]
---

> ⚠️ **已归档（2026-08-13）**：军火商经济已废弃，本页移入 `wiki/wiki/archived/`，不再属于活跃知识库，仅作历史参考。

# 武库点数（Armory Point）实施计划

> source: `docs/superpowers/plans/2026-08-09-armory-points.md`

## Summary

新增物品 `csgobox:armory_point`，接入动态箱子 JSON 掉落体系，并提供兑换铁钥匙的合成配方。贴图/模型/lang/recipe 全部放 `common` 资源（10 平台共享只写一份），平台层仅注册行 + creative tab 一行；有适配差异的平台用定点合入，禁整文件覆盖。

## Key takeaways

- **资源（common）**：`textures/item/armory_point.png`（16×16 RGBA，Pillow LANCZOS 缩放 + 无色抠图）、`models/item/armory_point.json`（`item/generated`）、`lang/zh_cn.json`+`en_us.json`（`武库点数`/`Armory Point`）。
- **物品注册**：复用 `ModItems` 模式；基准 `v1_21_1`（legacy `ITEMS.register`）与 `v26_1_2`（new），其余 8 平台仅定点加 3 行。
- **兑换配方**：用户确认保留 **64 武库点 → 1 铁钥匙**合成（`armory_point_exchange.json`）。
  > ⚠️ 数值冲突：经济数值 GDD [[arms-dealer-economy-design]] 以 **9 点 = 1 铁钥匙** 为权威锚点（对应 `data/csgobox/recipe/armory_point_exchange.json` 的 3×3 配方），明确指出 64 点的旧提议未落地。两文档不一致，以 GDD 为准。
- **约束**：`common/` 禁 import MC/NeoForge（CONSTRAINT-001）；每 Gradle 调用只构建一个版本；legacy 与 new 各自基准 + 定点合入。

## Connections

- 概念：[[arms-dealer-economy-design]] · [[multiloader-architecture]]
- 实体：[[csbox-config]] · [[csgo-box]]
- 参考：[[code-review]]（§4.1/§4.2）· [[configuration]]
