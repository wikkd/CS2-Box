---
type: entity
title: CsgoBox.java
kind: class
platform: all
updated: 2026-08-03
---

# CsgoBox.java（平台入口）

## Overview
每平台一份的 @Mod 入口类。持有 `CONFIG`（public static final，static 块初始化）与 `BULK_COMPUTE_POOL`（2 daemon 线程批量计算池）。

## Responsibilities
- 模组入口注册（DeferredRegister 集中注册物品）
- `CONFIG` 静态初始化（**勿改 static 块顺序**，勿写 null 守卫）
- `registerDynamicBoxItems`：用 `RegisterEvent` deferred supplier 注册 `config/csbox/*.json` 动态 item（**不要**用 `FMLCommonSetupEvent.enqueueWork`——registry 已 freeze）
- 批量开箱异步池管理

## Cross-platform differences
- 包名：`com.reclizer.csgobox.v1_21_1.*` / `v26_1_2.*` / `v26_2.*` 等各平台不同
- `@EventBusSubscriber` 26.x 无 `bus` 参数

## Sources
- [[changelog]] / [[architecture]] / [[readme]]

## Related
- [[dynamic-box-item-registration]] / [[csbox-config]]
