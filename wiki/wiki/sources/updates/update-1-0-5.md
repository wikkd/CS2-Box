---
type: source
title: docs/update-1.0.5.md — 1.0.5 更新说明
source_path: docs/update-1.0.5.md
date_ingested: 2026-08-03
tags: [release, config, recipe]
key_concepts: [version-sync]
key_entities: [csbox-config]
---

# docs/update-1.0.5.md（1.0.5 更新说明）

## Summary
移除 Cloth Config、ModConfigSpec 迁移、`csgo_key3` 锻造台唯一路径。

## Key takeaways
- Cloth Config 完全移除 → NeoForge 原生 `ModConfigSpec`
- 配置文件路径 `config/csgobox-common.toml` → 后又改回 `config/csgobox.toml`（1.0.5 后续）
- 扁平化字段访问：`CONFIG.fieldName` 取代 `CONFIG.section.fieldName`
- `csgo_key3` 原工作台 3 下界合金锭配方移除，仅保留锻造台升级路径

## Connections
- [[csbox-config]] / [[version-sync]] / [[changelog]]