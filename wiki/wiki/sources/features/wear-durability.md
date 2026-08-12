---
type: source
title: 开箱物品按磨损值损耗耐久（设计）
source_path: docs/superpowers/specs/2026-08-05-wear-durability-design.md
date_ingested: 2026-08-10
tags: [feature, wear, durability, server-authoritative]
key_concepts: [server-authoritative-rng, bulk-opening-pipeline]
key_entities: [csbox-config, packet-csgo-progress, packet-csgo-bulk-progress, box-defaults]
---

# 开箱物品按磨损值损耗耐久 — 设计

> source: `docs/superpowers/specs/2026-08-05-wear-durability-design.md`

## Summary

2026-08-05 批准。开箱抽出的**有耐久物品**按磨损值百分比扣除耐久，新增配置项 `damageItemByWear`（默认开启）。核心转变：磨损值从客户端查看界面随机生成的纯装饰，**改为服务端权威**（与开箱 RNG 同一 `serverSeed` 驱动），所有副本（动画/进库/掉落/事件）同步同一已损耗状态。

## Key takeaways

- **公式**：`耐久损失 = round(磨损值 × 最大耐久)`，钳制 `damage = clamp(损失, 0, 最大耐久-1)`（上限 `max-1` 保证永不碎裂，下限 0 近崭新无损）。
- **服务端流程**：单开在 `PacketCsgoProgress.handleServer` winner 确定后 roll `wear` 并扣耐久（与中奖槽位同一引用，动画所见即所得）；批量 `computeKResults`（纯线程只 roll wear）在 `finalizeBulkOpen`（主线程）统一扣耐久。
- **客户端显示**：`CsLookItemScreen` 有耐久取 `getDamageValue()/getMaxDamage()`，无耐久保持 `ThreadLocalRandom` 随机（现状不变）。
- **配置**：`advanced` 分组 `damageItemByWear`（`BooleanValue`，默认 `true`），服务端读取生效。
- **跨平台**：旧 `setDamageValue()` vs 新 `DataComponents.DAMAGE` → 定点合入，禁整文件覆盖；受影响 5 文件 × 各平台（`CsboxConfig`/`BulkOpenResult`/`PacketCsgoProgress`/`PacketCsgoBulkProgress`/`CsLookItemScreen`），每平台 clean 编译。

## Connections

- 概念：[[server-authoritative-rng]] · [[bulk-opening-pipeline]]
- 实体：[[csbox-config]] · [[packet-csgo-progress]] · [[packet-csgo-bulk-progress]] · [[box-defaults]]
- 参考：[[configuration]] · [[code-review]]（§4.7 并发权威）
