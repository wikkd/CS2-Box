# 底层代码优化计划 — Bottom-layer optimization plan

> 状态：进行中（2026，基于代码审查）
> 约束：严格遵循 CONSTRAINT-001（common 不 import MC）、平台镜像纪律、AnimRenderOps 漂移检查。

## 目标

针对 common 底层（跨 5 平台共享）的纯性能/健壮性热点做优化。
**原则：优先改 common/，一改全平台继承，不触碰镜像复杂度。**

## 已识别的优化项（来自代码审查报告）

### A. 高优先级 — 并发正确性（平台层，涉及多平台同步）
- [ ] A1 `BoxJsonLoader.LAST_LOAD_ERRORS` 非线程安全（ArrayList 跨线程 clear/add）
      → 5 个平台各改一份（v26_1_2/v26_2/v1_21_1/forge_26_1_2/forge_26_2）。
      → 用 CopyOnWriteArrayList 或加锁。风险中等，涉及手工合入（工作区已有大量改动时谨慎）。

### B. 高收益 — common 纯性能热点（无平台差异，一改全生效）✅ 已落地
- [x] B1 `OddsCalculator.pickGrade` 预计算正权重/累计和，避免每槽重复求和
      → 新增 `precomputeWeights()` + `Precomputed` record（实例方法 pickGrade）。
      → 保留原 `pickGrade(Random,int[])` 签名完全兼容（null/空/全零→1）。
- [x] B2 `BoxStripGenerator.generate` 预计算一次权重表，50 槽不再重复扫描
      → 单开 + 批量第一条 strip 路径的绝对热点。
- [ ] B3（后续）批量路径 `computeKResults` 复用 `BulkBoxContext` 里的预计算表
      → 平台层改动，待做。

### C. 中等 — 平台层纯机械
- [x] C1 `new EquipmentSlot[]` 提为常量（CsboxScreen countKeys/hasKeyAnywhere）
      → v26_1_2 / v26_2 / forge_26_1_2（三处有该数组；v1_21_1/forge_26_2 无此实现，不改）
- [ ] C2 `resolveGrade` 全表扫描 → 一次性展开 `Map<ItemStack,Integer>` 查表

### D. 低 — common 微优化/健壮性
- [x] D1 `TerminalAnims.countdownText` String.format → StringBuilder（渲染每帧调用）
- [x] D3 `Easing.cubicBezierCurve` 预计算 LUT + 线性插值，替代每帧 20 次二分迭代
      （端到端最大偏差 1.4e-5，cubicBezierCurve(0.5)=0.88378 vs 参考 0.884，测试全过）
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
- 已完成：B1、B2、D1、D3、C1
  - 第二轮新增 D3：Easing.cubicBezierCurve LUT 缓存（渲染热路径，common）
  - 第三轮新增 C1：CsboxScreen EquipmentSlot[] 提为 LOCATION_SLOTS 常量
    （v26_1_2 / v26_2 / forge_26_1_2，三平台编译通过；v1_21_1/forge_26_2 无此数组）
- 待做（平台层，需谨慎跨平台同步）：A1、B3、C1、C2、D2
