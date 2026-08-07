---
type: entity
title: Platform26 / Platform26V2
kind: class
platform: v26_1_2, v26_2
updated: 2026-08-03
---

# Platform26 / Platform26V2

## Overview
26.x 平台接口实现，注入 `common/` 的 Platform 接口。`Platform26`（v26_1_2）、`Platform26V2`（v26_2，独立 IPlatform 实现 `mcVersion()` 返回 `"26.2"`）。

## Responsibilities
- 实现 `common/src/main/java/platform/` 的 10 个接口
- 解耦：平台代码不直接 new 业务对象，通过 `Platform26.boxRegistry()` 等接口方法取
- 注册平台特有组件（PIP 渲染器、ButtonPalette、RenderFontTool）
- 包名：`com.reclizer.csgobox.v26_1_2.platform.Platform26`

## Cross-platform differences
- v26_2 的 `Platform26V2.mcVersion()` 返回 `"26.2"`
- 26.2 `@EventBusSubscriber` 无 `bus` 参数

## Sources
- [[port-26-1-2]] / [[architecture]]

## Related
- [[multiloader-architecture]] / [[rendering-pipeline]]