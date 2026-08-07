---
type: concept
title: 平台模块镜像纪律
updated: 2026-08-03
tags: [architecture, workflow, mirroring]
---

# 平台模块镜像纪律

## Overview
9 个平台模块**不是纯拷贝**：1.21.3+ 与 26.2 各自有 API 适配。禁止整文件覆盖其他模块——历史教训曾导致 v1_21_10 编译失败。

## Details
跨平台改动的正确姿势：
1. **先改基准模块**：legacy 用 `v1_21_1`，new 用 `v26_1_2`
2. `scripts/mirror.sh legacy|new|all <rel-path>` — 仅用于**无适配差异**的纯新增文件
3. 有适配差异的文件用**定点合入**（`scripts/merge-cooldown-fix.py` 是幂等合入脚本范例），或 `scripts/port-12111.py` 做规则化转换
4. **每平台 compileJava 验证**——增量缓存可能造假象，涉及平台改动用 `clean` 编译确认（曾有模块因 build 产物残留"假通过"）

## Platform notes
- v1_21_11 曾"从未真正编译通过"（clean 编译 80 错误），以 v26_1_2 为蓝本完整适配
- 26.x 的 `@EventBusSubscriber` 已移除 `bus` 参数

## Sources
- [[changelog]] / [[release]] / [[development]]

## Related
- [[multiloader-architecture]] / [[rendering-pipeline]]
