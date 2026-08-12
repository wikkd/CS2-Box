---
type: source
title: CHANGELOG.md — 版本历史（1.0.2–1.0.8）
source_path: CHANGELOG.md
date_ingested: 2026-08-03
tags: [changelog, history, fixes]
key_concepts: [multiloader-architecture, bulk-opening-pipeline, dynamic-box-item-registration, tutorial-system, achievement-system]
key_entities: [packet-csgo-bulk-progress, csgo-box, box-defaults]
---

# CHANGELOG.md（版本历史）

## Summary
版本演进时间线，记录每个版本的架构里程碑与踩坑教训。

## Key takeaways
- **1.0.2**：Forge 1.20.1 → NeoForge 1.21.1 迁移；KubeJS 集成；`PacketBoxOpenResult` 解决 UI 渲染竞态
- **1.0.4**：服务端权威动画数据；requestId 防过期响应；开箱冷却改为短效防双击
- **1.0.5**：`CsboxConfig` `init()` 从未被调用 bug（所有配置字段读为 0/null，开箱即 NPE 崩溃）→ `.get()` 内联构造器；移除 Cloth Config；`csgo_key3` 改锻造台唯一路径；成就系统 + `导购` 200 箱隐藏挑战
- **1.0.6**：v26_2 平台扩展（decoupled API 破坏性变更：PIP 渲染器、`setScreenAndShow`、`Options.hideGui` 移除）；common 首批 A 类迁移（ColorTools/OverlayColor）；教程系统（网络下载 + 版本化文件名 + OS 回收站）；LoadError 玩家可见纠错
- **1.0.7**：动态 box item 注册（方案 A，`RegisterEvent` deferred supplier 修复 `Registry is already frozen`）；批量开箱（异步预计算管线 + 总览屏 + 流水结果屏）；`/csbox scoreboard`（后移除）
- **1.0.8**（WIP 基线）：9 平台矩阵完成；`OPEN_BLOCKED_UNTIL_TICK` 并发安全（ConcurrentHashMap）；批量开箱确认屏 + `bulkOpenCount` 上限配置；26.2 HUD 隐藏恢复（`HudVisibility`）；设计 token + `GuiRegion` 容器化布局 + per-item 视觉基线；教程系统收敛 common；v1_21_11 用 v26_1_2 为蓝本重写适配

## 历史教训
- `enqueueWork` 晚于 registry freeze → `Registry is already frozen` 崩溃，改用 `RegisterEvent` deferred supplier
- `BoxJsonLoader.loadAll()` 在 `FMLCommonSetupEvent` 时 `Components not bound yet` → 移到 `ServerStartingEvent`
- SLF4J 日志 `"{}", elem, e.getMessage()` 只有 1 个占位符 → 真因丢失，用 Throwable variant

## Connections
- [[multiloader-architecture]] / [[bulk-opening-pipeline]] / [[tutorial-system]]
- [[csbox-config]]（init() bug）/ [[packet-csgo-progress]]（防双击演进）
