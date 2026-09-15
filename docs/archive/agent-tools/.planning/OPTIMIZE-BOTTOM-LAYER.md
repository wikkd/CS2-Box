# 底层代码优化计划 — Bottom-layer optimization plan

> 状态：进行中（2026，基于代码审查）
> 约束：严格遵循 CONSTRAINT-001（common 不 import MC）、平台镜像纪律、AnimRenderOps 漂移检查。

## 目标

针对 common 底层（跨 5 平台共享）的纯性能/健壮性热点做优化。
**原则：优先改 common/，一改全平台继承，不触碰镜像复杂度。**

## 已识别的优化项（来自代码审查报告）

### A. 高优先级 — 并发正确性（平台层，涉及多平台同步）
- [x] A1 `BoxJsonLoader.LAST_LOAD_ERRORS` 非线程安全（ArrayList 跨线程 clear/add）
      → 已用 CopyOnWriteArrayList 修复并覆盖全部 5 平台（v26_1_2 / v26_2 / v1_21_1 /
        forge_26_1_2 / forge_26_2），各平台均编译通过；通过 stash 隔离只提交修复、
        完整保留各文件上的用户进行中改动。

### B. 高收益 — common 纯性能热点（无平台差异，一改全生效）✅ 已落地
- [x] B1 `OddsCalculator.pickGrade` 预计算正权重/累计和，避免每槽重复求和
      → 新增 `precomputeWeights()` + `Precomputed` record（实例方法 pickGrade）。
      → 保留原 `pickGrade(Random,int[])` 签名完全兼容（null/空/全零→1）。
- [x] B2 `BoxStripGenerator.generate` 预计算一次权重表，50 槽不再重复扫描
      → 单开 + 批量第一条 strip 路径的绝对热点。
- [x] B3 批量路径 `computeKResults` 开头一次性 `precomputeWeights`，K 次
      follow-up roll 共用同一 Precomputed；null 回退 grade 1。
      → 已覆盖全部 5 平台（v26_1_2 / v26_2 / v1_21_1 / forge_26_1_2 / forge_26_2），
        各平台均编译通过；forge_26_1_2 通过 stash 隔离同步并保留用户改动。

### B5 批量路径延迟深拷贝（全 5 平台）
- [x] `PacketCsgoBulkProgress` 改为先取缓存的 GradeMap，用其 isEmpty() 判断空池，
      `ItemCsgoBox.getItemGroup`（每次深拷贝整张 ItemStack map）仅在 cache miss 时才执行。
      → v26_1_2/v26_2/v1_21_1/forge_26_2（干净）+ forge_26_1_2（stash 隔离）全部落地并编译通过。

### C. 中等 — 平台层纯机械
- [x] C1 `new EquipmentSlot[]` 提为常量（CsboxScreen countKeys/hasKeyAnywhere）
      → v26_1_2 / v26_2 / forge_26_1_2（三处有该数组；v1_21_1/forge_26_2 无此实现，不改）
- [x] C2a（forge_26_2 参考）：单开 fallback 路径复用已构建的 gradeMap，
      替代每次 fallback 重新 GradeMap.build 深拷贝整张 ItemStack map
      （其他平台 PacketCsgoProgress 文件脏，待同步）
- [ ] C2（完整）`resolveGrade` 全表扫描 → 查表（待同步到脏平台）

### D. 低 — common 微优化/健壮性
- [x] D1 `TerminalAnims.countdownText` String.format → StringBuilder（渲染每帧调用）
- [x] D3 `Easing.cubicBezierCurve` 预计算 LUT + 线性插值，替代每帧 20 次二分迭代
      （端到端最大偏差 1.4e-5，cubicBezierCurve(0.5)=0.88378 vs 参考 0.884，测试全过）
- [x] D4 `GradeMap.findFallback` 惰性 per-grade 缓存（ConcurrentHashMap + noFallback Set）
      → 批量/空池高频 fallback 只扫描一次；copier 每次仍应用，行为不变
- [x] D5 `BoxOdds.totalWeight` 委托给 `OddsCalculator.precomputeWeights`（DRY 统一正权重求和来源）
- [x] D8 `WearBands` 提取 `clampIdx` 辅助，消除 4 处重复的 clamp 表达式
- [x] D9 移除 `Easing.easeOutQuad` 死代码（6 模块 + 测试 0 引用）
- [x] D10 `OpenBlockGuard.isBlocked` 过期删除改用 `remove(key,value)` 条件删除，
      消除并发 ABA 竞态（防误删并发写入的新 deadline）
- [ ] D2 `WearPenalty`/`WearBands` 枚举化（低价值，可选）

## 执行纪律
1. ✅ 跑 `./gradlew :common:test` 基线确认无回归（BUILD SUCCESSFUL）。
2. ✅ 改 common → `./gradlew :common:test` 验证通过。
3. ✅ `./gradlew :v26_1_2:test -Pactive_versions=26.1.2` 平台编译+Smoke 通过。
4. 平台层改动（A1/C1/B3/C2）严格先改 v26_1_2，再 mirror/手工合入其他平台。
5. 不动工作区已有的未提交改动（仅叠加新优化）。

## 验证门禁
- ✅ `./gradlew :common:test`
- ✅ `./gradlew :v26_1_2:test -Pactive_versions=26.1.2`（PlatformSmokeTest）
- `scripts/check-animops-drift.sh`（仅当动渲染原语；本计划不涉及，跳过）

## 当前进度
- 已完成：B1、B2、D1、D3、C1、D4 + 并发测试 + A1（forge_26_2）+ C2a（forge_26_2）+ B3（四平台）
  - 第二轮新增 D3：Easing.cubicBezierCurve LUT 缓存（渲染热路径，common）
  - 第三轮新增 C1：CsboxScreen EquipmentSlot[] 提为 LOCATION_SLOTS 常量
    （v26_1_2 / v26_2 / forge_26_1_2，三平台编译通过；v1_21_1/forge_26_2 无此数组）
  - 第四轮新增 D4：GradeMap.findFallback 惰性缓存（common）+ 并发测试
  - 第六轮新增 A1（forge_26_2 LAST_LOAD_ERRORS 线程安全）+ C2a（fallback 复用 gradeMap）
  - 第七轮新增 B3（forge_26_2 批量权重预计算）
  - 第八轮 B3 同步到 v26_1_2 / v26_2 / v1_21_1（正式三平台，diff 对称）
  - 第九轮 forge_26_2 单开路径（唯一内联 strip）也预计算权重一次，对齐 B1/B3
  - 第十二轮：B3 同步到 forge_26_1_2——用 git stash 隔离用户对同文件的进行中
    import 清理，只提交我的 B3 改动，stash pop 恢复用户内容。至此 B3 覆盖全部 5 平台。
  - 第十三轮：A1（CopyOnWriteArrayList 并发修复）用 stash 隔离同步到 v26_1_2/v26_2/
    v1_21_1/forge_26_1_2，全部 5 平台完成 A1，且各平台用户遗漏改动完整保留。
  - 第十四轮：确认 C2a 在正式平台天然成立（无重复 build 反模式），无需同步；
    全 5 平台 final compileJava + common test 全绿。
- 待做（已确认非必要）：C2a（fallback 复用 gradeMap）经排查，正式平台 v26_1_2/v26_2/
  v1_21_1/forge_26_1_2 本就复用 gradeMap.findFallback(1)（用 BoxStripGenerator），
  无重复 build 反模式，无需同步；仅 forge_26_2 旧内联实现有该问题且已修复。
- 剩余低价值/高风险（不建议）：C2（resolveGrade 查表，fallback 低频、收益极小、触脏文件），
  D2（WearPenalty/WearBands 枚举化，纯重构低价值）。
- 结论：所有高价值、可安全实施的优化已完成并全平台验证通过。
- 第 32-33 轮：终端存在性终验（核心优化均真实存在生效）+ 全 5 平台 compileJava
  确认；C2（resolveGrade 查表）最终确认仅 fallback 低频调用、收益极小，不做。
  交付文档含最终验收清单。目标实质完成。
