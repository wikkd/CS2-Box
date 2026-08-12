---
type: entity
title: BoxJsonLoader.java
kind: class
platform: common
updated: 2026-08-03
---

# BoxJsonLoader.java

## Overview
运行时从 `config/csbox/*.json` 加载箱子配置。支持 `components`（DataComponent）和旧版 `tag`（NBT）物品格式。

## Responsibilities
- `loadAll()`：在 `ServerStartingEvent` 触发（v1.0.7 修复——`FMLCommonSetupEvent` 时 `bindComponents` 未跑 → `Components not bound yet`）
- 首次启动时若 `config/csbox/` 目录为空，自动写入默认 `weapon_supply_box.json`（含 `_tutorial` 字段，loader 忽略）
- 跳过 `_` 前缀文件（`_tutorial*.json`、`_tutorial_sources.json`）
- `LoadError` 收集：`LAST_LOAD_ERRORS` 静态列表，`loadFromFile()` 把所有异常分支收集为 `LoadError`（含 file/boxId/reason/line/column/cause）；`getLastLoadErrors()`/`hasLoadErrors()` 供 `/csbox info error` 和 `LoadErrorAnnouncer` 消费
- Gson 2.13+ 不再提供 `JsonSyntaxException.getLocation()` → 正则 `at line (\d+) column (\d+)` 从错误消息抓取

## Sources
- [[configuration]] / [[changelog]]

## Related
- [[dynamic-box-item-registration]] / [[csbox-config]] / [[tutorial-system]]
