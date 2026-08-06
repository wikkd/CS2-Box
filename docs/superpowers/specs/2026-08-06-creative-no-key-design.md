# 设计：创造模式免钥匙开箱

日期：2026-08-06
状态：已确认
模块：9 平台（v1_21_1 / v1_21_3 / v1_21_4 / v1_21_5 / v1_21_8 / v1_21_10 / v1_21_11 / v26_1_2 / v26_2）+ common 共享资源

## 背景与需求

用户需求：创造模式下玩家可以不用钥匙开箱 —— 创造物品栏被视为无限钥匙来源，背包中无需持有钥匙即可开箱。

已确认的约束（与用户逐项确认）：
- 箱子本身照常消耗（`box.shrink(1)` 行为不变），只免钥匙。
- 不加配置项，创造模式永远免钥匙。
- 批量开箱同样适用：创造下钥匙无限制，批量上限 `bulkOpenCount` 配置照常生效。

## 判定口径

统一使用 `player.getAbilities().instabuild`：

- 服务端与客户端均存在且值一致（创造模式时服务器同步给客户端 `Abilities.instabuild = true`）。
- 创造模式为 true；生存/冒险/旁观均为 false（旁观本就不能交互，无需特判）。
- 9 平台 API 完全一致（`Player#getAbilities()`），无适配差异，跨平台落地零风险。

## 服务端改动（权威）

### 1. `PacketCsgoProgress.tryConsumeKeys(Player entity, ResourceLocation keyId, int count)`

函数开头新增：

```java
if (entity.getAbilities().instabuild) {
    return true;
}
```

位置在 `keyId == null || minecraft:air` 早退之后、`count <= 0` 早退之后均可（建议放 `count <= 0` 之后，保持"无消耗需求"逻辑优先）。

覆盖路径：
- 单开 `handleServer`（line 94 调用）。
- 批量 `finalizeBulkOpen`（line 309 调用）—— 批量钥匙消耗共用此函数，一处覆盖两条路径。

### 2. `PacketCsgoBulkProgress.countMatchingKeys(Player player, ResourceLocation keyId)`

函数开头新增：

```java
if (player.getAbilities().instabuild) {
    return Integer.MAX_VALUE;
}
```

覆盖路径：
- `handleServer` 的 `availableKeys`（line 83）→ K 计算（line 84）。
- `finalizeBulkOpen` 的 `recheckKeys`（line 271）→ `actualK = Math.min(recheckBoxes, recheckKeys)`（line 272）。

批量上限 `CsgoBox.CONFIG.bulkOpenCount()`（line 91-94）在 K 上继续生效，创造模式不绕过。

## 客户端 GUI 改动（表现层）

### 3. `CsboxScreen.mouseClicked`（v1_21_1:421-432）

钥匙检查改为：

```java
if (keyRl != null && !keyRl.equals(ResourceLocation.parse("minecraft:air"))) {
    canOpen = false;
    if (entity.getAbilities().instabuild) {
        canOpen = true;
    } else {
        for (ItemStack stack : entity.getInventory().items) { ... }
    }
}
```

### 4. `CsboxScreen.countKeys()`

创造模式返回 `Integer.MAX_VALUE`，单开屏钥匙行显示 "× ∞"（渲染逻辑需处理 MAX_VALUE，参考现有用法）。

### 5. `CsboxBulkOverviewScreen`

- 构造/计数逻辑：`keyCount = instabuild ? Integer.MAX_VALUE : totalKeys`。
- `openableCount = Math.min(totalBoxes, keyCount)` 不变 —— MAX_VALUE 时自然等于 totalBoxes。
- 钥匙行显示 ∞（复用新 lang key）。
- 传给 `CsboxConfirmScreen` 的 `keyCount` 为 `Integer.MAX_VALUE`。

### 6. `CsboxConfirmScreen`

现有 `keyCount == Integer.MAX_VALUE` 分支（显示 `gui.csgobox.bulk.key_count_no_key`，即"无钥匙需求"文案）—— 创造模式下应显示 ∞ 而非"无钥匙需求"，需区分"箱子本来就无钥匙需求"与"创造模式免钥匙"两种情形：

- 新增判断：创造模式 → 用新 lang key 显示 ∞。
- 其余保持现有逻辑不变。

## 本地化（common 共享 lang）

`common/src/main/resources/assets/csgobox/lang/zh_cn.json` + `en_us.json` 各新增：

```
"gui.csgobox.bulk.key_count_infinite": "钥匙数: ∞" / "Keys: ∞"
```

common 共享资源一处改动，9 平台生效。

## 跨平台落地（镜像纪律）

- 基准模块：legacy 用 `v1_21_1`，new 用 `v26_1_2`，先改基准并验证。
- 其余平台：**定点合入**（文件整体有适配差异，禁止 mirror.sh 整文件覆盖）。
- 涉及平台文件（每平台 5 个）：
  - `packet/PacketCsgoProgress.java`
  - `packet/PacketCsgoBulkProgress.java`
  - `gui/CsboxScreen.java`
  - `gui/CsboxBulkOverviewScreen.java`
  - `gui/CsboxConfirmScreen.java`
- common 共享文件（1 处）：`lang/zh_cn.json`、`lang/en_us.json`。
- 26.x 平台核对 `getAbilities()` 可用性（预计 `Player#getAbilities()` 一致，编译验证）。

## 边界与安全性

- 服务端权威：非创造模式伪造包仍被 `tryConsumeKeys` 拒绝（无钥匙 → false）；批量路径 `countMatchingKeys` 非创造照常计数，refund 逻辑不受影响。
- 创造模式箱子照常消耗（`tryConsumeBoxes` / `box.shrink(1)` 不变）。
- 旁观模式 instabuild=false，不受影响。
- 无配置项。
- 动画、成就、统计数据（`OPENED_BOXES_STAT`）、`BoxOpenedEvent` 均不受影响。

## 测试

- 单开：
  - 创造 + 无钥匙 → 可开，开箱后箱子 -1。
  - 生存 + 无钥匙 → 仍拒绝（客户端按钮不可点 + 服务端拒绝）。
  - 生存 + 有钥匙 → 行为不变。
- 批量：
  - 创造 + 无钥匙 → openableCount = 箱数，可全部开启；箱子消耗、钥匙不消耗。
  - 创造 + `bulkOpenCount` 上限 → 仍截断。
  - 生存 + 钥匙不足 → openableCount 截断到钥匙数，行为不变。
- 每平台 `compileJava` 验证；涉及平台改动时用 `clean` 编译确认（增量缓存可能造假象）。
