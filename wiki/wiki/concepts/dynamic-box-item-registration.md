---
type: concept
title: 动态 box item 注册
updated: 2026-08-03
tags: [registry, dynamic-items, config]
---

# 动态 box item 注册

## Overview
扫描 `config/csbox/*.json`，为每个 `<filename>.json` 自动注册 item ID `csgobox:<filename>`（`ItemCsgoBox` 子类，`getDefaultInstance` 预置 `box_id` = 自身 id）。vanilla `/give` 无需 components 语法直接生效。

## Details
- 触发点：**`RegisterEvent`**，内部 `event.register(Registries.ITEM, itemId, () -> new ItemCsgoBox(...))` deferred supplier —— Item 实例在 registry finalize 阶段构造，时机早于 freeze
- **不要**用 `FMLCommonSetupEvent.enqueueWork`：1.21.1/26.1.2 的 enqueueWork 晚于 item registry freeze → `MappedRegistry.createIntrusiveHolder` 抛 `IllegalStateException`（`Registry is already frozen`），integrated server 进不去 world
- 守卫：`if (!event.getRegistryKey().equals(Registries.ITEM))`（listener 注册时未做 key 过滤）
- **type 为唯一判定字段（v1.0.8 严格分离）**：JSON `"type": "terminal"` → `ItemTerminal`；`"type": "csbox"`（或省略）→ `ItemCsgoBox`。不再按「id + key」派生——终端机**没有 `key` 字段**（默认 `terminal.json` 已删除，配置里残留 `key` 会触发 schema 错误）。旧版 v1.0.7 配置（无 `type`）在注册前由 `BoxDefaults.upgradeLegacyTerminalConfig` 一次性自动迁移（补 `type`、删 `key`）；迁移失败的 `terminal.json` 在加载时被拒绝并报错，绝不静默退化成普通开箱
- 跳过规则：已存在 item id（避免与 `csgobox:csgo_box` 冲突）、`_` 前缀文件（`_tutorial*.json`/`_tutorial_sources.json`，loader 惯例）、非法 ResourceLocation path（log warn）
- 服务端日志：`[csgo-dynamic-items] registered N dynamic box item(s) ...`
- **紫黑纹理修复**（1.0.8）：`DataComponents.ITEM_MODEL` 复用 `csgobox:csgo_box` 模型（8 平台）；1.21.5+ 补 `items/*.json` 定义
- 与 `BoxJsonLoader` 的绑定：`loadAll()` 必须在 `ServerStartingEvent`（`FMLCommonSetupEvent` 时 `bindComponents` 未跑 → `Components not bound yet` warning，整箱解析失败）

## Sources
- [[changelog]]

## Related
- [[box-json-loader]] / [[csgo-box]]
