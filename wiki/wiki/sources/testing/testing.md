---
type: source
title: docs/TESTING.md — 测试指南
source_path: docs/TESTING.md
date_ingested: 2026-08-03
tags: [testing, gametest]
---

# docs/TESTING.md（测试指南）

## Summary
NeoForge GameTest 框架集成测试指南 + 手动测试清单。

## Key takeaways
- 运行：`./gradlew gameTestServer` 或 `runGameTestClient`
- 测试文件：`common/src/test/java/`（跨版本）、`v1_21_1/src/test/java/`（平台特化）
- 注解：`@GameTestHolder("csgobox")` + `@GameTest(batch="...", setupTicks=N)`
- 手动测试清单：GUI 渲染、动画、音效、批量开箱、成就、锻造台配方
- 项目未配置 CI 流水线

## Connections
- [[multiloader-architecture]]