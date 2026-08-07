---
type: concept
title: 批量开箱管线
updated: 2026-08-03
tags: [bulk, async, gui]
---

# 批量开箱管线

## Overview
Shift+右键 → `CsboxBulkOverviewScreen` 总览屏 → `CsboxConfirmScreen` 二次确认 → `PacketCsgoBulkProgress` → 服务端异步预计算 → 主动画屏 + `CsboxBulkResultScreen` 流水结果屏。

## Details
- **总览屏**：实时计算 `min(背包内同名箱数, 钥匙数)`，按稀有度配色显示，K=0 时开启按钮禁用
- **确认屏**（1.0.8）：展示消耗量，确认后才发包（总览屏 → 确认屏 → 发包）
- **`bulkOpenCount` 配置**（`[advanced]`，0=无上限，默认 0）：服务端权威截断，客户端总览屏镜像 clamp
- **异步管线**：`BULK_COMPUTE_POOL`（2 daemon 线程）→ `CompletableFuture.supplyAsync` → `sp.level().getServer().execute(...)` 切回主线程收尾
- **结果**：Box 1 发 `PacketBoxOpenResult`（全量 50 项动画）；boxes 2..K 发 `PacketBoxBulkResult`（简洁 `(item, grade)`，max 1024 条/包）
- 主线程收尾：`inventory.add` 循环（vanilla 自动 merge，满则 `sp.drop`）+ `awardStat(OPENED_BOXES_STAT, K)` + `OpenedBoxTrigger.trigger() × K`
- **性能参考**：576 箱（36×16）异步 ~300ms 后台；风险已评估（退出/死亡丢弃、冷却防双发）
- 客户端路由：主动画播完分支检测 `PacketBoxBulkResult.consumeMatching(requestId)`，命中 → 流水屏；未命中 → 原单开路径（完全向后兼容）
- 流水屏：2D 底部上升 ticker，每 4 tick 推一条，最多 8 条，淡入 10%/稳定 70%/淡出 20%

## Sources
- [[changelog]]

## Related
- [[server-authoritative-rng]] / [[packet-csgo-bulk-progress]]
