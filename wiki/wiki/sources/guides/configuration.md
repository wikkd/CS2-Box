---
type: source
title: docs/CONFIGURATION.md — 配置参考
source_path: docs/CONFIGURATION.md
date_ingested: 2026-08-03
tags: [config, toml, json-schema]
key_concepts: [server-authoritative-rng]
key_entities: [csbox-config, box-json-loader]
---

# docs/CONFIGURATION.md（配置参考）

## Summary
`config/csgobox.toml`（ModConfigSpec）+ `config/csbox/*.json`（箱子数据）完整参考。

## Key takeaways
- TOML 4 分组 11+ 项：`[general]`（animationSpeed/globalDropRatePercent）、`[advanced]`（loadDefaultBoxes/enableDebugLogging/enableAchievements/**bulkOpenCount**）、`[sound]`、`[animation]`
- JSON schema：`name`/`key`（`minecraft:air` = 免钥匙）/`drop`/`random`（5 权重）/`entity`/`grade1-5`；物品对象 `id`/`count`/`components`（旧版 `tag` 仍兼容）
- 5 档稀有度色系：consumer 灰 / industrial 浅蓝 / mil_spec 蓝 / restricted 紫 / classified 粉红
- TOML 改动 `/reload` 生效；JSON 改动必须重启（`loadAll()` 只在 `ServerStartingEvent`）
- `CONFIG` 永不为 null：删所有 null 守卫（v1.0.5 修复）
- 资源路径**必须单数** `data/csgobox/recipe/`

## Connections
- [[csbox-config]] / [[box-json-loader]] / [[server-authoritative-rng]]
