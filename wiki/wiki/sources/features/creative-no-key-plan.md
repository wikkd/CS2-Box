---
type: source
title: 创造模式免钥匙开箱 实施计划
source_path: docs/superpowers/plans/2026-08-06-creative-no-key.md
date_ingested: 2026-08-10
tags: [feature, creative, key, plan]
key_concepts: [platform-mirror-discipline, server-authoritative-rng]
key_entities: [packet-csgo-progress, packet-csgo-bulk-progress, csbox-config]
---

# 创造模式免钥匙开箱 — 实施计划

> source: `docs/superpowers/plans/2026-08-06-creative-no-key.md`（对应设计 [[creative-no-key]]）

## Summary

[[creative-no-key]] 的任务分解实现计划（agentic 逐任务执行）。目标：创造模式单开+批量无需钥匙、箱子照常消耗，判定统一 `player.getAbilities().instabuild`，不加配置项。服务端权威 + 客户端表现层。

## Key takeaways

- **架构**：服务端在钥匙消耗入口 `tryConsumeKeys` 与批量计数 `countMatchingKeys` 加 `instabuild` 短路（一处覆盖单开+批量 finalize 两条路径）；客户端 3 个 GUI 文件跳过钥匙检查显 ∞。判定点最小化。
- **平台家族适配差异**（决定插入点）：A 族 `ResourceLocation`+`getInventory().items`（1.21.0~1.21.10）/ B 族 `Identifier`+`getNonEquipmentItems()`（1.21.11）/ C 族 `Identifier`+仅 `(Player,ItemStack)` 重载（26.x）。GUI 插入代码 9 平台逐字相同。
- **约束**：每 Gradle 调用单版本；镜像纪律定点合入禁整文件覆盖；行为不变项（箱子消耗/`bulkOpenCount`/成就/动画）不受影响；common 仅动 lang JSON（无 Java）。
- **任务流**：Task1 common lang ∞ key → Task2 服务端 `tryConsumeKeys`/`countMatchingKeys` 短路 → Task3 客户端 `CsboxScreen`/`CsboxBulkOverviewScreen`/`CsboxConfirmScreen` ∞ 显示 → 各平台 clean 编译。

## Connections

- 概念：[[platform-mirror-discipline]] · [[server-authoritative-rng]]
- 实体：[[packet-csgo-progress]] · [[packet-csgo-bulk-progress]] · [[csbox-config]]
- 设计：[[creative-no-key]]
