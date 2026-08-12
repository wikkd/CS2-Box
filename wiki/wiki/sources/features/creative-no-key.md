---
type: source
title: 创造模式免钥匙开箱（设计）
source_path: docs/superpowers/specs/2026-08-06-creative-no-key-design.md
date_ingested: 2026-08-10
tags: [feature, creative, key, mirror-discipline]
key_concepts: [platform-mirror-discipline, server-authoritative-rng]
key_entities: [packet-csgo-progress, packet-csgo-bulk-progress, csbox-config]
---

# 创造模式免钥匙开箱 — 设计

> source: `docs/superpowers/specs/2026-08-06-creative-no-key-design.md`

## Summary

2026-08-06 确认。创造模式下玩家无需持有钥匙即可开箱（创造物品栏视为无限钥匙来源），**箱子本身照常消耗**（`box.shrink(1)` 不变）。不加配置项，创造模式永远免钥匙；批量开箱同样适用，且 `bulkOpenCount` 上限照常生效（创造不绕过）。

## Key takeaways

- **判定口径**：统一用 `player.getAbilities().instabuild`（服务端与客户端值一致，9 平台 API 完全一致，跨平台零风险）。
- **服务端权威改动**：`PacketCsgoProgress.tryConsumeKeys` 开头 `instabuild → return true`；`PacketCsgoBulkProgress.countMatchingKeys` 开头 `instabuild → return Integer.MAX_VALUE`（一处覆盖单开/批量两条路径）。批量 `bulkOpenCount` 在 K 上继续生效。
- **客户端 GUI**：`CsboxScreen.mouseClicked`/`countKeys()`、`CsboxBulkOverviewScreen`、`CsboxConfirmScreen` 在创造模式显示 "× ∞"；需区分"箱子本无钥匙需求"与"创造免钥匙"两种 ∞ 文案（新增 lang key `key_count_infinite`）。
- **本地化**：`common` 共享 lang（`zh_cn.json`/`en_us.json`）一次改动 9 平台生效。
- **跨平台**：定点合入（文件整体有适配差异），每平台 5 文件；非创造伪造包仍被 `tryConsumeKeys` 拒绝，旁观 `instabuild=false` 不受影响。

## Connections

- 概念：[[platform-mirror-discipline]] · [[server-authoritative-rng]]
- 实体：[[packet-csgo-progress]] · [[packet-csgo-bulk-progress]] · [[csbox-config]]
- 参考：[[code-review]]（§4.2 镜像纪律）· [[configuration]]
