---
type: concept
title: 军火商经济（武库点数循环）
updated: 2026-08-10
tags: [economy, balance, design, gdd]
---

# 军火商经济（武库点数循环）

## Overview

CS2-Box 的「武库点数」经济循环设计（GDD，见 [[arms-dealer-economy]]），把开箱产出的垃圾转化为继续开箱的燃料，但严格保证**回收永远不回本**、**每点都来自玩家时间**，避免自循环套利。是 1.21 开箱体验的中长期留存系统。相关物品实施见 [[armory-points]]。

## Details

- **核心循环**：`击杀生物拿箱(时间) → 钥匙开箱 → 出垃圾 → 工作方块拆解成点数 → 点数补钥匙/买高级内容 → 再开箱`。
- **三实体职责切分**：
  - **工作方块**：物品 → 点数（唯一回收入口，工厂）
  - **军火商村民**：点数 → premium 箱/终端；矿物 → 点数（商店）
  - **铁砧/合成台**：64 点 → 钥匙（锚点 sink，绑定"时间"）
- **关键数值**（未经 playtest，待验证）：
  - 回收均值 ≈ 3.61 点/箱（consumer 3 / industrial 5 / mil_spec 7 / restricted 8 / classified 8），硬约束 `Σ(P×yield) < 9` 铁钥匙成本 → 每箱净亏 ≈5.39 点。
  - **钥匙锚点：9 武库点数 = 1 铁钥匙**（`armory_point_exchange.json`，权威；[[armory-points]] 的 64 点提议未落地）。
  - 矿物换点：铁锭 2 / 金锭 4 / 绿宝石 2 / 钻石 12（单笔 < 9 桶交换不算赚）。
- **通胀/失效信号**：A 钥匙自由（回收>64 或掉率过高）/ B 点数废纸（无 sink）/ C 采挖压倒玩法（16 铁=1 钥匙过快）。
- **稳态**：每点追溯至玩家时间（击杀/采挖），无封闭无限环。

## Platform notes

无平台差异——物品/配方/村民交易均为 `common` 资源 + 数据驱动，10 平台共享；仅物品注册行需各平台 3 行定点合入（[[armory-points]]）。

## Sources

- [[arms-dealer-economy]] · [[armory-points]]

## Related

- [[server-authoritative-rng]] · [[bulk-opening-pipeline]] · [[csgo-box]] · [[csbox-config]]
