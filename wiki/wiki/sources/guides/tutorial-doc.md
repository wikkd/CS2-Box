---
type: source
title: docs/tutorials/_tutorial_v1.0.6_zh_cn.md — 教程文档（中文）
source_path: docs/tutorials/_tutorial_v1.0.6_zh_cn.md
date_ingested: 2026-08-03
tags: [tutorial, json-schema]
---

# docs/tutorials/_tutorial_v1.0.6_zh_cn.md（教程文档）

## Summary
JSON 配置文件参考：字段说明、物品对象格式、稀有度等级、钥匙、校验规则、故障排查。由模组从 Gitee 自动下载。

## Key takeaways
- 文件名 = 箱子 ID（`my_custom_box.json` → `csgobox:my_custom_box`），`_` 前缀文件跳过加载
- 顶层字段：`name`（含 `#RRGGBB ` 颜色前缀）、`key`（`minecraft:air` 免钥匙）、`drop`、`random`（5 权重）、`entity`（纯列表/交替对）、`grade1-5`
- 物品对象：`id`/`count`/`components`（优先）/`tag`（旧版兼容）
- 稀有度对应：grade1 consumer 79.9% / grade2 industrial 16.0% / grade3 mil_spec 3.2% / grade4 restricted 0.64% / grade5 classified 0.26%
- 校验规则：空 grade 全跳过、权重负值回退默认、未知 id 跳过、非法颜色前缀整串作无色
- `csgo_key3` 仅锻造台升级获得

## Connections
- [[configuration]] / [[tutorial-system]] / [[box-json-loader]]