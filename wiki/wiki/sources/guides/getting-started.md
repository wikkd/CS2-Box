---
type: source
title: docs/GETTING-STARTED.md — 快速入门
source_path: docs/GETTING-STARTED.md
date_ingested: 2026-08-03
tags: [setup, run]
key_concepts: [multiloader-architecture]
---

# docs/GETTING-STARTED.md（快速入门）

## Summary
5 分钟跑起客户端并体验一次开箱。

## Key takeaways
- Java 版本必须与平台对应（21 vs 25）；Gradle Wrapper 自动下载对应版本
- 首次运行：`/csbox give @p csgobox:csgo_box 1` + `csgo_key0`，手持右键 → 预览网格 → 放钥匙点开启
- JSON 改动必须重启生效
- v26_1_2 `pack.mcmeta` 必须用 `supported_formats`（无 min/max）；配方 `ingredients` 用裸字符串

## Connections
- [[multiloader-architecture]] / [[box-json-loader]]
