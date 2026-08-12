---
type: source
title: 军火商经济数值设计（武库点数循环）
source_path: docs/superpowers/plans/2026-08-09-arms-dealer-economy.md
date_ingested: 2026-08-10
tags: [design, economy, balance, gdd]
key_concepts: [arms-dealer-economy, server-authoritative-rng]
key_entities: [csgo-box, csbox-config]
---

# 军火商经济数值设计（武库点数循环）— GDD

> source: `docs/superpowers/plans/2026-08-09-arms-dealer-economy.md`

## Summary

纯数值设计 GDD（未含实现），定义「武库点数」经济循环。核心循环：`击杀生物拿箱(时间) → 钥匙开箱 → 出垃圾 → 工作方块拆解成点数 → 点数补钥匙/买高级内容 → 再开箱`。设计支柱：**回收永远不回本**、**每点都来自时间**、**军火商是 premium 店不是免费箱贩**。所有未经 playtest 的数值标 `[PLACEHOLDER · 验证路径]`。

## Key takeaways

- **回收产出**（工作方块拆解，按稀有度，硬约束 `Σ(P×yield) < 9` 铁钥匙成本）：consumer 3 / industrial 5 / mil_spec 7 / restricted 8 / classified 8 点；均值 ≈ 3.61 点/箱 ≈ 0.40 钥匙。g3/g4/g5 单件 clamp ≤8，**严防单箱套利**。
- **矿物换点**（村民交易，时间闸）：铁锭×1=2 / 金锭×1=4 / 绿宝石×1=2（刻意低）/ 钻石×1=12。单笔 < 9 桶交换不算赚，采挖是慢速时间门控源。
- **军火商售卖**（L1–L5 递进）：村民不卖基础箱（mob 掉），卖 mob 掉不到的内容 + 钥匙反向兑换（L3 `点→key0` 9 点平价零套利）；L4 `点→终端` 18 点（批量开箱终端便利奖赏）；L5 `点→key2` 45 点+1 钻石（复合防 64 上限绕过）。
- **钥匙合成锚点**：**9 武库点数 → 1 铁钥匙**（`armory_point_exchange.json` 3×3），把循环绑到"时间"的关键闸，勿动。
  > 注：[[armory-points]] 计划的 64 点/钥匙为旧提议未落地，与本文冲突，以 9 点/钥匙为权威。
- **稳态方程**：N 箱成本 N×9，回收 N×3.61，缺口 N×5.39 由采挖/累积垃圾补——循环天然通缩可持续。
- **通胀信号（playtest 失败定义）**：A 钥匙自由（回收>64 或掉率过高）/ B 点数废纸（无 sink）/ C 采挖压倒玩法（16 铁=1 钥匙过快）。

## Connections

- 概念：[[arms-dealer-economy]] · [[server-authoritative-rng]]
- 实体：[[csgo-box]] · [[csbox-config]]
- 参考：[[armory-points]] · [[bulk-opening-pipeline]]
