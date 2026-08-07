---
type: entity
title: BoxOpenedEvent.java
kind: class
platform: all
updated: 2026-08-03
---

# BoxOpenedEvent.java

## Overview
NeoForge 事件总线开箱通知（post-event），KubeJS 兼容。每次玩家成功开箱并**获得物品后**触发。

## Responsibilities
- 属性：`getEntity()`（Player）、`getBoxId()`（ResourceLocation/Identifier）、`getResultItem()`（ItemStack）、`getGrade()`（int 1–5）、`isBulk()`（boolean）
- non-cancelable（钥匙和箱子已消耗，回滚会引入复制漏洞）
- 批量开 N 箱触发 N 次事件
- 事件类包名随平台变：`com.reclizer.csgobox.<vX>.event.BoxOpenedEvent`（9 平台各不相同）

## Sources
- [[kubejs-events]]

## Related
- [[achievement-system]] / [[server-authoritative-rng]] / [[opened-box-trigger]]