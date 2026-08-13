---
type: source
title: 武库商小屋（世界生成结构）
date_ingested: 2026-08-13
tags: [feature, worldgen, structure, economy, villager]
key_concepts: [platform-mirror-discipline]
key_entities: [csbox-config, csgo-box]
---

# 武库商小屋（世界生成结构）

## Summary

新增世界生成结构 `csgobox:arms_dealer_hut`「武库商小屋」：军火商（`arms_dealer`）的野外据点。纯数据驱动实现（`common` 资源四平台共享，零 Java 改动），模板 NBT 由脚本 `scripts/gen-arms-dealer-hut.py` 生成（NBT 二进制不入库手改，脚本是唯一事实来源）。

## Key takeaways

- **结构内容**：云杉木小屋（9×6×8）——内置武库拆解台（深板岩柜台展示）、军火商村民（`VillagerData.profession = csgobox:arms_dealer`，Lv.1，名字牌「军火商」）、商店宝箱（`LootTable = csgobox:chests/arms_dealer_hut`）、工作台、床、灯笼/火把；1 格门 + 四扇 2×2 铁栏杆窗。
- **世界生成**：`structure_set`（random_spread，spacing 30 / separation 10）+ jigsaw 结构（`size: 1`，单模板，`terrain_adaptation: beard_thin`，`WORLD_SURFACE_WG` 高度投影）；biome 覆盖陆地群系（森林/针叶林/草原/热带/恶地/丘陵 + 平原/草甸/樱花林等，自定义 tag `#csgobox:has_structure/arms_dealer_hut`）。
- **宝箱战利品**：武库点数 3–8（weight 30）/ 基础箱（12）/ 绿宝石 2–6（20）/ 铁锭 2–5（20）/ 铁钥匙（5），单池 roll 2。
- **村民刷新机制**：职业直接写进模板实体 NBT（`VillagerData` 字段 1.21.1 与 26.x 格式一致，已核验）；结构放置时游戏强制移除 UUID 重新生成（多座小屋无实体冲突）；`PersistenceRequired` 防消失；模板不带 `DataVersion`，两版本均按原样加载。
- **格式核验来源**：structure / structure_set / template_pool JSON 与 villager / chest 实体 NBT 均直接对照 1.21.1 与 26.2 原版 jar 核验（village_plains / igloo bottom / simple_dungeon / barracks 等）；`loot_table` 的 `set_count` count 需用 `{"type":"minecraft:uniform",...}`（两版本同格式）。
- **经济意义**：小屋让「武库拆解台 + 军火商」脱离手工建站前置，成为可探索发现的经济入口；村民仍为 Lv.1，交易升级机制不变（不破坏 GDD 的递进解锁）。

## Connections

- 概念：[[platform-mirror-discipline]]（军火商经济早期设计与点数方案已随实现完成归档至 `archived/`）
- 实体：[[csbox-config]] · [[csgo-box]]
