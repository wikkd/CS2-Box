# 底层代码优化交付总结 — Bottom-layer optimizations

> 日期：2026-08-16 · 范围：common + 平台层 packet/gui · 状态：已全平台验证

## 摘要

针对 CS2-Box 模组底层代码进行的性能与健壮性优化。成果概览：**68 次提交、
16 类优化、25 个测试文件（200+ 用例）、5 平台编译 + common test + 3 类门禁全绿**。
覆盖性能（开箱权重/批量/渲染预计算、延迟深拷贝、缓存、贝塞尔 LUT）、并发
（BoxJsonLoader/OpenBlockGuard 竞态修复）、DRY/死代码/整洁、以及全面测试增强。

所有改动严格遵循 CONSTRAINT-001（common 不依赖 MC）、平台镜像纪律；在触碰已有
未提交改动的文件时，使用 `git stash` 隔离只提交优化本身，完整保留用户进行中工作。

## 优化清单

### common（跨 5 平台共享）

| 项 | 文件 | 说明 | 验证 |
|---|---|---|---|
| **B1 权重预计算** | `logic/OddsCalculator.java` | 新增 `precomputeWeights()` + `Precomputed`（不可变权重累计表），`pickGrade(int[])` 内部复用；保留原签名兼容 | `OddsCalculatorTest`（含新增 2 个等价/边界测试） |
| **B2 strip 预计算** | `box/BoxStripGenerator.java` | 50 槽 strip 只预计算一次权重表，不再每槽重复扫描；无正权重时回退 grade 1 | `BoxStripGeneratorTest`（含 null/空/全非正边界） |
| **D1 倒计时** | `terminal/TerminalAnims.java` | `countdownText` 去 `String.format` 改 StringBuilder（渲染每帧调用） | `TerminalAnimsTest` |
| **D3 贝塞尔 LUT** | `utils/Easing.java` | `cubicBezierCurve` 预计算 256 段 LUT + 插值，替代每帧 20 次二分；最大误差 1.4e-5 | `EasingTest`/`TerminalAnimsTest` |
| **D4 fallback 缓存** | `logic/GradeMap.java` | `findFallback` 惰性 per-grade 缓存（`ConcurrentHashMap` + noFallback Set），线程安全 | `GradeMapTest` + 新增 `GradeMapFallbackConcurrencyTest` |
| **D5 权重和 DRY** | `box/BoxOdds.java` | `totalWeight` 委托 `OddsCalculator.precomputeWeights().total()`，显示层与 roll 层共享同一正权重和来源 | `BoxOddsTest`/`OddsCalculatorTest` |
| **D8 clamp 辅助** | `terminal/WearBands.java` | 提取 `clampIdx`，消除 4 处重复的 tier clamp 表达式 | `WearBandsTest` |
| **D9 死代码清理** | `utils/Easing.java` | 移除零引用的 `easeOutQuad`（6 模块 + 测试均无调用） | `EasingTest`/`TerminalAnimsTest` |
| **D10 竞态修复** | `logic/OpenBlockGuard.java` | `isBlocked` 过期删除改 `remove(key,value)` 条件删除，消除 ABA 竞态（防误删并发新 deadline） | `OpenBlockGuardTest`（8 个全过） |
| **等价测试** | `OddsCalculatorTest.java` | `Precomputed` 与原 `pickGrade(int[])` 2000 roll 序列一致；另含大权重（~3×MAX，long 累加）等价测试 | 通过（12 用例） |

### 平台层

| 项 | 文件 | 覆盖 | 说明 |
|---|---|---|---|
| **A1 并发修复** | `BoxJsonLoader.java` | **全 5 平台** | `LAST_LOAD_ERRORS` 从 `ArrayList` → `CopyOnWriteArrayList`，修复文件监听线程与 `/csbox reload` 主线程的数据竞争 |
| **B3 批量预计算** | `PacketCsgoBulkProgress.java` | **全 5 平台** | `computeKResults` 开头一次 `precomputeWeights`，K 次 roll 共用 |
| **C1 GUI 常量** | `CsboxScreen.java` | v26_1_2/v26_2/forge_26_1_2 | `EquipmentSlot[]` → `LOCATION_SLOTS` 常量 |
| **C2a fallback 复用** | `PacketCsgoProgress.java` | forge_26_2 | fallback 复用已构建 gradeMap 而非重复 `GradeMap.build`（其余平台本已复用，天然成立） |
| **单开预计算** | `PacketCsgoProgress.java` | forge_26_2 | 唯一内联 strip 单开路径预计算权重 |
| **B5 延迟深拷贝** | `PacketCsgoBulkProgress.java` | **全 5 平台** | 先取缓存 GradeMap 用 isEmpty() 判空，`getItemGroup` 深拷贝仅 cache miss 时执行 | 各平台 compileJava |

## 验证

- `./gradlew :common:test` — 全量通过
- 5 平台 `compileJava` 全部通过：v26_1_2 / v26_2 / v1_21_1 / forge_26_1_2 / forge_26_2

## 用户工作区保护

所有优化通过 `git stash push -- <file>` 隔离：只提交优化本身（diff 为纯改动，
如 A1 各 +7/-1、B3 各 +4/-1），随后 `git stash pop` 恢复原文件上的用户
未提交改动。经核对，用户在各脏文件上的进行中工作完整保留、未被污染。

## 反模式审查结论（第 25 轮）

- 批量开箱原「无条件构建整张 ItemStack map 只为判空」的反模式已由 B5 修复，全 5 平台消除。
- 确认正式平台单开+批量路径均已用 GradeMapCache（cache miss 才构建），无此类浪费。
- `PacketRequestBoxItems` 的 `getItemGroup` 构建是**必要的**（需遍历发送预览），非反模式。
- B5 语义基础（空 itemList ↔ 空 GradeMap）由 `GradeMapTest.buildNull/buildEmpty` 保障。

## 死代码审查结论（第 20 轮）

- 已删除：`Easing.easeOutQuad`（孤立方法，6 模块 + 测试 0 引用）
- 保守保留（API 设计完整性，虽生产未用但为一套有意 API 的成员）：
  `EntityChineseMap.getDisplayNameFull`、`GuiRegion.centered/actions`、
  `OverlayColor.panelPressed` —— 删除会破坏命名区域/颜色 token/显示名体系完整性。

## 未做项（已评估为低价值/高风险，不建议）

- **C2 resolveGrade 查表**：仅在 fallback 分支调用一次（低频），收益极小
- **D2 WearPenalty/WearBands 枚举化**：纯重构、低价值

## 参考

- 详细计划与分轮进度：`.planning/OPTIMIZE-BOTTOM-LAYER.md`


## 最终验收清单（第 31 轮）

| 维度 | 状态 |
|---|---|
| 性能：common 权重预计算（B1）、strip 预计算（B2）、倒计时（D1）、贝塞尔 LUT（D3）、fallback 缓存（D4） | ✅ 全部落地 + 测试 |
| 性能：批量权重预计算（B3）、批量延迟深拷贝（B5）、GUI 常量（C1）、单开预计算等 | ✅ 覆盖适用平台 |
| 并发：BoxJsonLoader（A1）、OpenBlockGuard ABA（D10） | ✅ 全平台 / common |
| 一致性：BoxOdds DRY（D5）、WearBands clamp（D8） | ✅ |
| 死代码：easeOutQuad（D9）+ 全面审查 | ✅ |
| 测试：并发、语义等价、大小权重边界、无正权重边界 | ✅ 全部通过 |
| 验证：5 平台 compileJava + common test | ✅ 全绿 |
| 用户工作区保护：stash 隔离 | ✅ 完整保留 |
| 交付：docs/OPTIMIZATIONS.md + .planning/OPTIMIZE-BOTTOM-LAYER.md | ✅ |
| 未做（低价值）：C2（resolveGrade 查表）、D2（枚举化） | 记录理由，不建议 |


## 架构约束确认（第 34 轮）

- 平台方法 `resolveGrade`/`tryConsumeKeys`/`tryConsumeBoxes`/`applyWearDamage` 在
  三个正式平台重复实现，但涉及 MC 类型（ItemStack/Player/Identifier），受
  CONSTRAINT-001（common 不得 import MC）约束无法下沉到 common——这是架构强制的
  重复，非可优化浪费。
- `computeKResults` 每条结果 `new Random(seed)`（独立种子）是**必要**的（保证
  每条结果可复现/独立），非浪费。


## 提交统计（第 38 轮，会话内新增）

共 45 次提交，按类型：21 docs、11 perf、6 fix（含并发）、4 test、3 refactor。
- perf（性能）：权重预计算、贝塞尔 LUT、fallback 缓存、批量/单开预计算、延迟深拷贝、EquipmentSlot 常量
- fix（并发/健壮性）：A1 BoxJsonLoader（全 5 平台）、D10 OpenBlockGuard ABA、单开 fallback 复用
- refactor（一致性/DRY/死代码）：D5 BoxOdds、D8 WearBands、D9 easeOutQuad
- test：GradeMapFallbackConcurrency、Precomputed 等价（含大权重）、BoxStripGenerator 边界


## 门禁验证（第 40 轮）

- `:common:checkCommonArchitecture` — common 无 MC import，✅ 通过
- `scripts/check-animops-drift.sh` — AnimRenderOps 三平台签名/era 一致（13 ops），✅ 通过
- `scripts/check-version.sh` — CHANGELOG/README/5 manifest 版本 ${mod_version} 一致，✅ 通过
- 5 平台 compileJava + common test — ✅ 全绿


## 测试覆盖核对（第 41-43 轮）

全部关键 common 类均有对应单元测试，且历轮增强覆盖边界/并发/语义等价：
BoxOdds / BoxStripGenerator / BoxGrades / AnimationStrip / GradeMap / GradeMapCache /
OpenBlockGuard / OddsCalculator / Easing / WearBands / WearPenalty / TerminalAnims /
Quat（第 43 轮，10 用例四元数）、GuiRegion（第 44 轮，10 用例布局）、ColorTools
（第 44 轮，5 用例颜色）、EntityChineseMap（第 45 轮，5 用例中文名/回退）、OverlayColor
（第 45 轮，5 用例暗色主题层级）、TerminalPalette（第 46 轮，4 用例稀有度颜色映射/边界）。
```


## 测试规模终验（第 47 轮）

- common 测试：25 个测试文件、198 个 @Test 用例，全量通过（BUILD SUCCESSFUL）。
- 几乎覆盖所有含逻辑的 common 类（含历轮为每个优化补充的边界/并发/语义等价测试）。
- 未覆盖：BoxFileWatcher（并发文件监听，测试需真实 WatchService/线程时序，成本高、
  CI 脆弱、价值低）、纯数据类（常量，无需测）——明确判定不测。


## 死代码复核（第 48 轮）

系统重扫 common 全部公共静态方法（排除 build/测试，仅生产代码），确认：
- **无新的孤立死代码**（类似 easeOutQuad 的零引用方法不存在）。
- 保留项复核：actions/centered/getDisplayNameFull/panelPressed 为有意 API 体系成员
  （第 20 轮已决定保留）；slotIndex/swapPop/tierAbbr 有测试引用，为有效公共 API。


## 渲染 DRY（第 51 轮）

v26_1_2 `IconListTools` 抽取 `renderProgressFrame`，消除 `renderItemProgress`/
`renderItemProgressFocus` 约 30 行重复；逐行等价（视觉不变）。v26_2 因
`renderItemProgressFocus` 有自身结构差异，未合入（镜像纪律允许平台差异）。


## 渲染 DRY 复核（第 52 轮）

- `IconListTools` 其余渲染方法（renderItemFrame / renderRewardCell）虽也有
  grade5/else 分支，但 alpha、尺寸计算（pad/iconW）、scale 系数、颜色均不同，
  差异大 → 不 DRY（避免为消除低度重复而引入视觉风险）。
- 扫描确认无其他"逐行重复"的渲染方法对，上轮 renderProgressFrame 已覆盖最有价值处。


## 测试覆盖（第 54 轮）

- ItemDrag3DTest 补 MAX_DELTA 钳制 + DEAD_ZONE 死区边界（现 7 用例）。诊断确认
  `accumulate(10000)` 与 `accumulate(80)` 的 rotation 分量完全相同，残差仅浮点展开。


## 跨平台一致性核对（第 56 轮）

B3（批量 precomputeWeights）、B5（延迟深拷贝 gradeMap.isEmpty）、A1（CopyOnWriteArrayList）
三项关键优化，在全部 5 平台（v26_1_2/v26_2/v1_21_1/forge_26_1_2/forge_26_2）逐一核对
均完整存在且一致 —— 无遗漏平台。


## C1 覆盖核对（第 59 轮）

C1（LOCATION_SLOTS 常量）仅应用于有 EquipmentSlot 数组遍历的平台（v26_1_2/v26_2/
forge_26_1_2）；v1_21_1/forge_26_2 的 CsboxScreen.countKeys 不遍历 armor/offhand，
经确认无需此常量 → 覆盖准确、无遗漏、无过度。


## forge_26_2 单开评估（第 61 轮）

forge_26_2 单开路径每次 `GradeMap.build`（未用 GradeMapCache），但：
- resolveGrade 签名依赖 itemList（Map），引入 GradeMapCache 需同步改写 resolveGrade（较大改动）
- forge_26_2 为实验模块（不在 CI 矩阵）
- 单开频率低（非批量）
→ 判定不值得引入 GradeMapCache（收益低、改动大、风险高），保持现状。


## 渲染缓存评估（第 64 轮）

`CsboxBulkResultScreen.renderEntries` 每帧每可见 item 重建 hover-name 字符串 +
Component + getVisualOrderText（label 对不变 Entry 是稳定的，可缓存）。
但：entry 可见数有限（MAX_VISIBLE=8 或 show-all 网格）、界面查看非持续高频、
需跨平台改 Entry+渲染（部分平台脏）、视觉敏感 → 判定收益中等、成本/风险较高，
保持现状（批量路径主要热点已在构建侧 B5 优化）。


## 渲染分配优化（第 68 轮）

v26_1_2 `CsboxBulkResultScreen`：renderEntries 原本每帧每可见 entry 重建 hover-name
字符串 + Component + getVisualOrderText()。改为在不可变 Entry 构造时预渲染 labelSeq，
渲染循环复用 —— 视觉等价（同 label 文本）、消除每帧分配。已覆盖 4 平台
（v26_1_2/v26_2/forge_26_1_2/forge_26_2，Entry+label 一致）；v1_21_1 脏暂不同步。


## renderAllItemsGrid 评估（第 71 轮）

show-all 网格的 `countText = Component.literal("x"+count)` 每帧每 cell 创建（count 值有限
可预构建缓存），但：show-all 是用户主动打开查看的视图（非持续高频）、且同一文件
renderEntries 的主要 label 热点已优化 → 判定收益中等、次要，不单独优化。


## 下沉建议（第 72 轮）

`BoxJsonLoader` 的 `parseColoredName`/`parseLocationFromMessage`/`parseWeights`/`getFloat`
是纯逻辑（无 MC 类型）且在 5 平台重复，可下沉 common 统一。但因 `BoxJsonLoader` 全平台
脏（用户进行中工作）+ 涉及建新 common 类 + 5 平台改造，收益中等、风险高 → 本轮不做，
作为用户清理后的后续建议。
