---
type: concept
title: 服务端权威 RNG
updated: 2026-08-03
tags: [network, rng, security]
---

# 服务端权威 RNG

## Overview
开箱结果由服务端在 `PacketCsgoProgress` 内计算（winningIndex + 50 项动画列表 + 最终 item），客户端只渲染动画，无法操纵结果。防双击由 `OPEN_BLOCKED_UNTIL_TICK` 冷却实现。

## Details
- 数据流：客户端右键预览（`PacketRequestBoxItems`/`PacketSyncBoxItems`）→ 放钥匙点开启 → 服务端校验（冷却/钥匙/箱）→ RNG 选 winningIndex → `PacketCsgoProgress`（含 requestId）→ 客户端滚动 → `CsLookItemScreen`
- **requestId 匹配**：防过期客户端响应被错误屏幕消费（1.0.4 引入）
- **冷却机制**：`OPEN_BLOCKED_UNTIL_TICK` 短效防双击（1.0.4 起从"完整动画时长"改为短效，ESC 取消不阻塞重试）；1.0.8 起 HashMap → ConcurrentHashMap + `tickOpenBlockMap` 每 100 tick 清理
- **异步批量**：见 [[bulk-opening-pipeline]]，后台线程只读快照（`BulkBoxContext`），主线程收尾发结果
- 玩家在 async 计算中退出/死亡 → 主线程收尾检查 `sp.isRemoved() || !sp.isAlive()` 丢弃结果
- 事件为 post-event 通知（见 [[box-opened-event]]），保证动画与物品一致

## Sources
- [[architecture]] / [[changelog]] / [[kubejs-events]]

## Related
- [[bulk-opening-pipeline]] / [[packet-csgo-progress]] / [[box-opened-event]]
