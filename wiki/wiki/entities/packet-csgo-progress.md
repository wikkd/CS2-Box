---
type: entity
title: PacketCsgoProgress.java
kind: class
platform: all
updated: 2026-08-03
---

# PacketCsgoProgress.java

## Overview
单开核心数据包（S→C）：开箱进度 + 服务端权威 RNG 结果（winningIndex、50 动画 items、grades、requestId）。

## Responsibilities
- 服务端权威 RNG：计算中奖索引与物品，客户端只渲染
- 开箱防双击冷却：`OPEN_BLOCKED_UNTIL_TICK`（ConcurrentHashMap，1.0.8 起；`tickOpenBlockMap` 每 100 tick 清理）
- 辅助 API：`tryConsumeKeys(player, box, count)`（count 签名）、`tryConsumeBoxes(player, box, count)`（全背包消耗）、`isOpenBlockedStatic` / `blockFurtherOpensStatic`（package-private 供 bulk handler 复用）
- 服务端拒绝请求（冷却/钥匙缺失/空箱/物品无效）时发送匹配的空白结果，客户端动画正常退出（1.0.4 修复）

## Cross-platform differences
- 26.x 用 `RegistryFriendlyByteBuf`/StreamCodec 接线方式差异

## Sources
- [[architecture]] / [[changelog]]

## Related
- [[server-authoritative-rng]] / [[packet-csgo-bulk-progress]]
