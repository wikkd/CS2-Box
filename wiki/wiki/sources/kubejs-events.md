---
type: source
title: docs/KUBEJS-EVENTS.md — 事件系统与 KubeJS 兼容
source_path: docs/KUBEJS-EVENTS.md
date_ingested: 2026-08-03
tags: [events, kubejs, integration]
key_concepts: [achievement-system]
key_entities: [box-opened-event]
---

# docs/KUBEJS-EVENTS.md（事件系统）

## Summary
`BoxOpenedEvent` 事件 API 与 KubeJS 脚本监听用法。

## Key takeaways
- `BoxOpenedEvent` 是 **post-event**：钥匙/箱子已消耗、物品已发放后才触发，保证动画与物品一致；**non-cancelable**（避免回滚与复制漏洞）
- 属性：`getEntity()` / `getBoxId()` / `getResultItem()` / `getGrade()`（1–5）/ `isBulk()`
- 事件类包名随平台变：`com.reclizer.csgobox.<vX>.event.BoxOpenedEvent`（9 平台各不相同）
- KubeJS 非前置依赖；脚本用 `NeoForgeEvents.onEvent(...)` 监听
- 批量开 N 箱触发 N 次事件，监听器避免昂贵操作
- 钥匙匹配是**纯 ID 匹配**（`BuiltInRegistries.ITEM.getKey()`），KubeJS 自定义物品可直接当钥匙

## Connections
- [[box-opened-event]] / [[achievement-system]] / [[server-authoritative-rng]]
