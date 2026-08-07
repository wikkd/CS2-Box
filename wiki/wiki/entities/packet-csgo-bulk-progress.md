---
type: entity
title: PacketCsgoBulkProgress.java
kind: class
platform: all
updated: 2026-08-03
---

# PacketCsgoBulkProgress.java

## Overview
批量开箱请求（C→S），StreamCodec 仅 `long`（requestId）。

## Responsibilities
- `handleServer` 主线程：校验 / 快照（`BulkBoxContext` record：weights + gradeMap，后台线程只读）/ 预消耗 K 箱 + K 钥匙 / `CompletableFuture.supplyAsync` 提交 `BULK_COMPUTE_POOL`
- 后台计算：Box 1 含完整 50 项动画 + serverSeed；其余仅 `(item, grade)`（`BulkOpenResult` record）
- 主线程收尾（`sp.level().getServer().execute`）：发 `PacketBoxOpenResult`（box1）+ `PacketBoxBulkResult`（boxes 2..K，max 1024 条/包）+ `inventory.add` 循环（满则 `sp.drop`）+ `awardStat` + `OpenedBoxTrigger.trigger() × K`
- 风险处理：玩家退出/死亡（`sp.isRemoved() || !sp.isAlive()`）丢弃结果；`OPEN_BLOCKED_UNTIL_TICK` 10 tick 防双发

## Sources
- [[changelog]]

## Related
- [[bulk-opening-pipeline]] / [[packet-csgo-progress]] / [[server-authoritative-rng]]
