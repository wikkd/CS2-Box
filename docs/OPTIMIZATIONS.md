# 底层代码优化交付总结 — Bottom-layer optimizations

> 日期：2026-08-16 · 范围：common + 平台层 packet/gui · 状态：已全平台验证

## 摘要

针对 CS2-Box 模组底层代码进行的性能与健壮性优化。所有改动严格遵循
CONSTRAINT-001（common 不依赖 MC）、平台镜像纪律；在触碰已有未提交改动的
文件时，使用 `git stash` 隔离只提交优化本身，完整保留用户进行中工作。

## 优化清单

### common（跨 5 平台共享）

| 项 | 文件 | 说明 | 验证 |
|---|---|---|---|
| **B1 权重预计算** | `logic/OddsCalculator.java` | 新增 `precomputeWeights()` + `Precomputed`（不可变权重累计表），`pickGrade(int[])` 内部复用；保留原签名兼容 | `OddsCalculatorTest`（含新增 2 个等价/边界测试） |
| **B2 strip 预计算** | `box/BoxStripGenerator.java` | 50 槽 strip 只预计算一次权重表，不再每槽重复扫描 | `BoxStripGeneratorTest` |
| **D1 倒计时** | `terminal/TerminalAnims.java` | `countdownText` 去 `String.format` 改 StringBuilder（渲染每帧调用） | `TerminalAnimsTest` |
| **D3 贝塞尔 LUT** | `utils/Easing.java` | `cubicBezierCurve` 预计算 256 段 LUT + 插值，替代每帧 20 次二分；最大误差 1.4e-5 | `EasingTest`/`TerminalAnimsTest` |
| **D4 fallback 缓存** | `logic/GradeMap.java` | `findFallback` 惰性 per-grade 缓存（`ConcurrentHashMap` + noFallback Set），线程安全 | `GradeMapTest` + 新增 `GradeMapFallbackConcurrencyTest` |
| **D5 权重和 DRY** | `box/BoxOdds.java` | `totalWeight` 委托 `OddsCalculator.precomputeWeights().total()`，显示层与 roll 层共享同一正权重和来源 | `BoxOddsTest`/`OddsCalculatorTest` |
| **D8 clamp 辅助** | `terminal/WearBands.java` | 提取 `clampIdx`，消除 4 处重复的 tier clamp 表达式 | `WearBandsTest` |
| **D9 死代码清理** | `utils/Easing.java` | 移除零引用的 `easeOutQuad`（6 模块 + 测试均无调用） | `EasingTest`/`TerminalAnimsTest` |
| **等价测试** | `OddsCalculatorTest.java` | `Precomputed` 与原 `pickGrade(int[])` 2000 roll 序列一致 | 通过 |

### 平台层

| 项 | 文件 | 覆盖 | 说明 |
|---|---|---|---|
| **A1 并发修复** | `BoxJsonLoader.java` | **全 5 平台** | `LAST_LOAD_ERRORS` 从 `ArrayList` → `CopyOnWriteArrayList`，修复文件监听线程与 `/csbox reload` 主线程的数据竞争 |
| **B3 批量预计算** | `PacketCsgoBulkProgress.java` | **全 5 平台** | `computeKResults` 开头一次 `precomputeWeights`，K 次 roll 共用 |
| **C1 GUI 常量** | `CsboxScreen.java` | v26_1_2/v26_2/forge_26_1_2 | `EquipmentSlot[]` → `LOCATION_SLOTS` 常量 |
| **C2a fallback 复用** | `PacketCsgoProgress.java` | forge_26_2 | fallback 复用已构建 gradeMap 而非重复 `GradeMap.build`（其余平台本已复用，天然成立） |
| **单开预计算** | `PacketCsgoProgress.java` | forge_26_2 | 唯一内联 strip 单开路径预计算权重 |

## 验证

- `./gradlew :common:test` — 全量通过
- 5 平台 `compileJava` 全部通过：v26_1_2 / v26_2 / v1_21_1 / forge_26_1_2 / forge_26_2

## 用户工作区保护

所有优化通过 `git stash push -- <file>` 隔离：只提交优化本身（diff 为纯改动，
如 A1 各 +7/-1、B3 各 +4/-1），随后 `git stash pop` 恢复原文件上的用户
未提交改动。经核对，用户在各脏文件上的进行中工作完整保留、未被污染。

## 未做项（已评估为低价值/高风险，不建议）

- **C2 resolveGrade 查表**：仅在 fallback 分支调用一次（低频），收益极小
- **D2 WearPenalty/WearBands 枚举化**：纯重构、低价值

## 参考

- 详细计划与分轮进度：`.planning/OPTIMIZE-BOTTOM-LAYER.md`
