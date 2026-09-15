# 开箱物品按磨损值损耗耐久 — 设计文档

日期：2026-08-05
状态：已批准（2026-08-05，含公式与钳制确认）

## 需求

玩家开箱抽出的物品**具有耐久属性**时，按抽出物品的磨损值百分比扣除耐久。
新增配置项控制开关，**默认开启**。

## 核心规则

- 公式：`耐久损失 = round(磨损值 × 最大耐久)`（四舍五入）
- 钳制：`damage = clamp(损失, 0, 最大耐久 - 1)`
  - 上限 `max-1`：物品永不因磨损碎裂消失（低耐久物品 0.99 磨损会 round 到满耐久）
  - 下限 0：近 0 磨损 = 崭新，0 损失
- 有耐久物品：查看界面磨损率 = `实际扣损 / 最大耐久`（显示与实物一致）
- 无耐久物品：查看界面保持现有随机磨损率显示（选项 A，不改 UI 行为）

## 架构

磨损值改为**服务端权威**（当前为客户端查看界面随机生成，纯装饰）。

### 服务端流程

**单人开箱** `PacketCsgoProgress.handleServer`：
- winner 最终确定后（fallback 分支之后、`setData` 之前）
- `wear = rng.nextFloat()`（同一 serverSeed 驱动 rng）
- 若 `CONFIG.damageItemByWear()` 且 `stack.isDamageableItem()` → 按公式扣耐久
- `giveItem` 与动画中奖槽位同一引用 → 动画中奖位自然显示耐久条（所见即所得）

**批量开箱** `PacketCsgoBulkProgress`：
- `computeKResults`（纯 Java 线程，禁止触碰 MC API — 既有约束）只 roll `wear` 存入 `BulkOpenResult`
- `finalizeBulkOpen`（主线程）发包/入库/事件前统一扣耐久

所有副本同步：`CsboxPlayerData`、`BoxOpenedEvent`、进库/掉落物品均为同一已损耗状态。

### 客户端显示

`CsLookItemScreen` 构造器：
- 有耐久：`wearValue = getDamageValue() / getMaxDamage()`
- 无耐久：`ThreadLocalRandom` 随机（现状）

`CsboxBulkResultScreen` 不涉及（不显示磨损）。

### 配置

- `advanced` 分组：`damageItemByWear`，`BooleanValue`，默认 `true`
- 服务端读取生效（COMMON 配置双侧加载，无影响）

## 跨平台（镜像纪律）

- 基准模块：legacy = `v1_21_1`，new = `v26_1_2`
- 有适配差异（旧 `setDamageValue()` vs 新 `DataComponents.DAMAGE`；26.x decoupled API）→ 定点合入，禁止整文件镜像覆盖
- 受影响文件（10 模块 × 5）：`config/CsboxConfig.java`、`box/BulkOpenResult.java`、`packet/PacketCsgoProgress.java`、`packet/PacketCsgoBulkProgress.java`、`gui/CsLookItemScreen.java`
- 每平台 `clean` 编译验证（防增量缓存假象）

## 验证

- 10 平台 `compileJava -Pactive_versions=<v>`（clean）
- `./gradlew :common:test` 无回归（不触 common）
- 运行时回归清单见 `docs/RELEASE.md` 质量门：单开/批量、有耐久/无耐久物品、开关关闭
