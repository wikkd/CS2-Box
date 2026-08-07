# 创造模式免钥匙开箱 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 创造模式下玩家开箱（单开 + 批量）无需持有钥匙，箱子照常消耗；判定统一用 `player.getAbilities().instabuild`，不加配置项。

**Architecture:** 服务端权威 + 客户端表现层。服务端在钥匙消耗入口（`tryConsumeKeys`）与批量钥匙计数（`countMatchingKeys`）加 instabuild 短路；客户端 3 个 GUI 文件跳过钥匙检查并显示 ∞。判定点最小化：`tryConsumeKeys` 一处覆盖单开 + 批量 finalize 两条路径。

**Tech Stack:** Java 21/25 + NeoForge（9 平台），Gradle 单 MC 版本构建（`-Pactive_versions=<v>`）。

**Spec:** `docs/superpowers/specs/2026-08-06-creative-no-key-design.md`

## Global Constraints

- **每次 Gradle 调用只能构建一个 MC 版本**（NeoGradle 历史限制）。用 `-Pactive_versions=<v>` 覆盖 gradle.properties 默认（26.1.2）。
- **镜像纪律**：本改动涉及的文件在 9 平台间**有适配差异**（`ResourceLocation` vs `Identifier`、list vs slot 遍历、重载签名不同），**禁止用 mirror.sh 整文件覆盖**；只做定点合入。基准模块：legacy 用 v1_21_1，new 用 v26_1_2，先改基准。
- **判定口径**：一律 `entity.getAbilities().instabuild`（public 字段，9 平台 API 一致；创造 true，生存/冒险/旁观 false）。
- **行为不变项**：箱子消耗（`tryConsumeBoxes` / `box.shrink(1)`）、批量上限 `bulkOpenCount`、成就/统计/`BoxOpenedEvent`、动画均不受影响。
- **common 不得 import `net.minecraft.*`**（CONSTRAINT-001）——本改动只碰 common 的 lang JSON，无 Java。
- 语言：新增 lang key `gui.csgobox.bulk.key_count_infinite`（zh: "钥匙数: ∞"，en: "Keys: ∞"，无参数）。
- 涉及平台改动时用 **clean 编译**确认（增量缓存可能造假象）。

## 平台家族（适配差异，决定插入点）

| 家族 | 平台 | 包名类型 | tryConsumeKeys 签名 | countMatchingKeys 签名 | GUI 背包遍历 |
|---|---|---|---|---|---|
| A | v1_21_1/3/4/5/8/10 | `ResourceLocation` | `(Player, ResourceLocation, int)` 存在（ItemStack 重载委托它） | `(Player, ResourceLocation)` 存在 | `getInventory().items` |
| B | v1_21_11 | `Identifier` | `(Player, Identifier, int)` 存在（slot 遍历） | `(Player, Identifier)` 存在 | `getNonEquipmentItems()` |
| C | v26_1_2 / v26_2 | `Identifier` | 仅 `(Player, ItemStack, int)`（slot 遍历） | 仅 `(Player, ItemStack)` | `getNonEquipmentItems()` |

GUI 插入代码在 9 平台**逐字相同**（新增代码不引用任何版本差异类型）。

---

### Task 1: common lang 新增 ∞ key

**Files:**
- Modify: `common/src/main/resources/assets/csgobox/lang/zh_cn.json`
- Modify: `common/src/main/resources/assets/csgobox/lang/en_us.json`

**Interfaces:**
- Consumes: 无
- Produces: lang key `gui.csgobox.bulk.key_count_infinite`（后续 GUI 任务引用）

- [ ] **Step 1: zh_cn.json 插入**

在 `"gui.csgobox.bulk.key_count_no_key"` 行之后插入：

```json
  "gui.csgobox.bulk.key_count_infinite": "钥匙数: ∞",
```

- [ ] **Step 2: en_us.json 插入**

在 `"gui.csgobox.bulk.key_count_no_key"` 行之后插入：

```json
  "gui.csgobox.bulk.key_count_infinite": "Keys: ∞",
```

- [ ] **Step 3: JSON 合法性验证**

Run: `python3 -m json.tool common/src/main/resources/assets/csgobox/lang/zh_cn.json > /dev/null && python3 -m json.tool common/src/main/resources/assets/csgobox/lang/en_us.json > /dev/null`
Expected: 退出码 0，无输出。

- [ ] **Step 4: 提交**

```bash
git add common/src/main/resources/assets/csgobox/lang/zh_cn.json common/src/main/resources/assets/csgobox/lang/en_us.json
git commit -m "feat: 创造模式免钥匙 — 共享 lang key key_count_infinite"
```

---

### Task 2: v1_21_1 基准（家族 A）— 服务端 + GUI

**Files:**
- Modify: `v1_21_1/src/main/java/com/reclizer/csgobox/v1_21_1/packet/PacketCsgoProgress.java`（`tryConsumeKeys(Player, ResourceLocation, int)`，约 line 275-281）
- Modify: `v1_21_1/src/main/java/com/reclizer/csgobox/v1_21_1/packet/PacketCsgoBulkProgress.java`（`countMatchingKeys(Player, ResourceLocation)`，约 line 132-165）
- Modify: `v1_21_1/src/main/java/com/reclizer/csgobox/v1_21_1/gui/CsboxScreen.java`（`countKeys` ~141、`mouseClicked` ~421、`renderLabels` ~311）
- Modify: `v1_21_1/src/main/java/com/reclizer/csgobox/v1_21_1/gui/CsboxBulkOverviewScreen.java`（`recount` ~93、`renderLabels` ~186）
- Modify: `v1_21_1/src/main/java/com/reclizer/csgobox/v1_21_1/gui/CsboxConfirmScreen.java`（`renderLabels` ~81）

**Interfaces:**
- Consumes: `gui.csgobox.bulk.key_count_infinite`（Task 1）
- Produces: 后续家族 A 平台（Task 4）的逐字拷贝模板

- [ ] **Step 1: PacketCsgoProgress.tryConsumeKeys 加 instabuild 短路**

在 `tryConsumeKeys(Player entity, ResourceLocation keyId, int count)` 内、`count <= 0` 早退之后、`int remaining = count;` 之前插入：

```java
        if (entity.getAbilities().instabuild) {
            return true;
        }
```

- [ ] **Step 2: PacketCsgoBulkProgress.countMatchingKeys 加 instabuild 短路**

在 `countMatchingKeys(Player player, ResourceLocation keyId)` 内、air 早退（`return Integer.MAX_VALUE;`）之后、`int total = 0;` 之前插入：

```java
        if (player.getAbilities().instabuild) {
            return Integer.MAX_VALUE;
        }
```

- [ ] **Step 3: CsboxScreen.countKeys 加短路**

`countKeys()` 开头 `int total = 0;` 之后插入：

```java
        if (this.entity != null && this.entity.getAbilities().instabuild) {
            return Integer.MAX_VALUE;
        }
```

- [ ] **Step 4: CsboxScreen.mouseClicked 钥匙检查包一层 instabuild**

现有代码（约 line 424-432）：

```java
                        if (keyRl != null && !keyRl.equals(ResourceLocation.parse("minecraft:air"))) {
                            canOpen = false;
                            for (ItemStack stack : entity.getInventory().items) {
                                if (keyRl.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))) {
                                    canOpen = true;
                                    break;
                                }
                            }
                        }
```

改为：

```java
                        if (keyRl != null && !keyRl.equals(ResourceLocation.parse("minecraft:air"))) {
                            canOpen = false;
                            if (entity.getAbilities().instabuild) {
                                canOpen = true;
                            } else {
                                for (ItemStack stack : entity.getInventory().items) {
                                    if (keyRl.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))) {
                                        canOpen = true;
                                        break;
                                    }
                                }
                            }
                        }
```

- [ ] **Step 5: CsboxScreen.renderLabels 钥匙计数显示 ∞**

现有代码（约 line 312-314）：

```java
            if (boxKeyCount > 0) {
                String count = " \u00D7 " + boxKeyCount;
```

改为：

```java
            if (boxKeyCount > 0) {
                String count = boxKeyCount == Integer.MAX_VALUE
                        ? " \u00D7 \u221E"
                        : " \u00D7 " + boxKeyCount;
```

- [ ] **Step 6: CsboxBulkOverviewScreen.recount 创造下 keyCount = MAX_VALUE**

现有代码（约 line 93-95）：

```java
        this.boxCount = totalBoxes;
        this.keyCount = noKeyRequired ? totalBoxes : totalKeys;
        this.openableCount = Math.min(totalBoxes, this.keyCount);
```

改为：

```java
        this.boxCount = totalBoxes;
        this.keyCount = this.player.getAbilities().instabuild
                ? Integer.MAX_VALUE
                : (noKeyRequired ? totalBoxes : totalKeys);
        this.openableCount = Math.min(totalBoxes, this.keyCount);
```

- [ ] **Step 7: CsboxBulkOverviewScreen.renderLabels 钥匙行创造分支**

现有代码（约 line 186-193）：

```java
        String keyDisplay = (this.keyId == null) ? "—" : keyName(this.keyId);
        if (this.keyId == null) {
            drawCentered(guiGraphics, Component.translatable("gui.csgobox.bulk.key_count_no_key", this.boxCount).withStyle(row),
                    rowY, 0xFF55FF55);
        } else {
            drawCentered(guiGraphics, Component.translatable("gui.csgobox.bulk.key_count", keyDisplay, this.keyCount).withStyle(row),
                    rowY, 0xFF55FF55);
        }
```

改为：

```java
        String keyDisplay = (this.keyId == null) ? "—" : keyName(this.keyId);
        if (this.keyId != null && this.player.getAbilities().instabuild) {
            drawCentered(guiGraphics, Component.translatable("gui.csgobox.bulk.key_count_infinite").withStyle(row),
                    rowY, 0xFF55FF55);
        } else if (this.keyId == null) {
            drawCentered(guiGraphics, Component.translatable("gui.csgobox.bulk.key_count_no_key", this.boxCount).withStyle(row),
                    rowY, 0xFF55FF55);
        } else {
            drawCentered(guiGraphics, Component.translatable("gui.csgobox.bulk.key_count", keyDisplay, this.keyCount).withStyle(row),
                    rowY, 0xFF55FF55);
        }
```

- [ ] **Step 8: CsboxConfirmScreen.renderLabels 钥匙行创造分支**

现有代码（约 line 81-86）：

```java
        drawCentered(guiGraphics, Component.translatable(
                        this.keyCount == Integer.MAX_VALUE
                                ? "gui.csgobox.bulk.key_count_no_key"
                                : "gui.csgobox.bulk.key_count",
                        this.keyCount == Integer.MAX_VALUE ? this.boxCount : this.keyCount),
                rowY, 0xFF55FF55);
```

改为：

```java
        drawCentered(guiGraphics, Component.translatable(
                        this.player.getAbilities().instabuild
                                ? "gui.csgobox.bulk.key_count_infinite"
                                : this.keyCount == Integer.MAX_VALUE
                                        ? "gui.csgobox.bulk.key_count_no_key"
                                        : "gui.csgobox.bulk.key_count",
                        this.keyCount == Integer.MAX_VALUE ? this.boxCount : this.keyCount),
                rowY, 0xFF55FF55);
```

- [ ] **Step 9: 编译验证**

Run: `./gradlew clean :v1_21_1:compileJava -Pactive_versions=1.21.1`
Expected: BUILD SUCCESSFUL（`instabuild` / `getAbilities()` 均可用）。

- [ ] **Step 10: 提交**

```bash
git add v1_21_1/src/main/java/com/reclizer/csgobox/v1_21_1/packet/PacketCsgoProgress.java v1_21_1/src/main/java/com/reclizer/csgobox/v1_21_1/packet/PacketCsgoBulkProgress.java v1_21_1/src/main/java/com/reclizer/csgobox/v1_21_1/gui/CsboxScreen.java v1_21_1/src/main/java/com/reclizer/csgobox/v1_21_1/gui/CsboxBulkOverviewScreen.java v1_21_1/src/main/java/com/reclizer/csgobox/v1_21_1/gui/CsboxConfirmScreen.java
git commit -m "feat: 创造模式免钥匙开箱（v1_21_1 基准）"
```

---

### Task 3: v26_1_2 基准（家族 C）— 服务端 + GUI

**Files:**
- Modify: `v26_1_2/src/main/java/com/reclizer/csgobox/v26_1_2/packet/PacketCsgoProgress.java`（`tryConsumeKeys(Player, ItemStack, int)`，约 line 264-290）
- Modify: `v26_1_2/src/main/java/com/reclizer/csgobox/v26_1_2/packet/PacketCsgoBulkProgress.java`（`countMatchingKeys(Player, ItemStack)`，约 line 153-200）
- Modify: `v26_1_2/src/main/java/com/reclizer/csgobox/v26_1_2/gui/CsboxScreen.java`（`countKeys` ~169、`mouseClicked` ~508、key display）
- Modify: `v26_1_2/src/main/java/com/reclizer/csgobox/v26_1_2/gui/CsboxBulkOverviewScreen.java`（`recount` ~88、`renderLabels`）
- Modify: `v26_1_2/src/main/java/com/reclizer/csgobox/v26_1_2/gui/CsboxConfirmScreen.java`（`renderLabels` ~90）

**Interfaces:**
- Consumes: `gui.csgobox.bulk.key_count_infinite`（Task 1）
- Produces: 家族 C 平台（Task 7）的逐字拷贝模板

注意：家族 C 的包内 `Identifier` / `ResourceLocation.parse` 已存在于现有代码中，插入的新代码**不含**任何类型名——因此 GUI 各步与 Task 2 逐字相同，直接复用。

- [ ] **Step 1: PacketCsgoProgress.tryConsumeKeys 加 instabuild 短路**

在 `tryConsumeKeys(Player entity, ItemStack box, int count)` 内、`count <= 0` 早退之后、`int remaining = count;` 之前插入：

```java
        if (entity.getAbilities().instabuild) {
            return true;
        }
```

- [ ] **Step 2: PacketCsgoBulkProgress.countMatchingKeys 加 instabuild 短路**

在 `countMatchingKeys(Player player, ItemStack box)` 内、`Identifier keyId = ItemCsgoBox.getKey(box);` + air 早退（`return Integer.MAX_VALUE;`）之后、`int total = 0;` 之前插入：

```java
        if (player.getAbilities().instabuild) {
            return Integer.MAX_VALUE;
        }
```

- [ ] **Step 3: CsboxScreen.countKeys 加短路**

`countKeys()` 开头 `int total = 0;` 之后插入：

```java
        if (this.entity != null && this.entity.getAbilities().instabuild) {
            return Integer.MAX_VALUE;
        }
```

- [ ] **Step 4: CsboxScreen.mouseClicked 钥匙检查包一层 instabuild**

现有代码（约 line 510-518，循环用 `entity.getInventory().getNonEquipmentItems()`）：

```java
                        if (keyRl != null && !keyRl.equals(Identifier.parse("minecraft:air"))) {
                            canOpen = false;
                            for (ItemStack stack : entity.getInventory().getNonEquipmentItems()) {
                                if (keyRl.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))) {
                                    canOpen = true;
                                    break;
                                }
                            }
                        }
```

改为（循环体保持原样，仅包一层）：

```java
                        if (keyRl != null && !keyRl.equals(Identifier.parse("minecraft:air"))) {
                            canOpen = false;
                            if (entity.getAbilities().instabuild) {
                                canOpen = true;
                            } else {
                                for (ItemStack stack : entity.getInventory().getNonEquipmentItems()) {
                                    if (keyRl.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))) {
                                        canOpen = true;
                                        break;
                                    }
                                }
                            }
                        }
```

- [ ] **Step 5: CsboxScreen 钥匙计数显示 ∞**（与 Task 2 Step 5 逐字相同）

```java
            if (boxKeyCount > 0) {
                String count = boxKeyCount == Integer.MAX_VALUE
                        ? " \u00D7 \u221E"
                        : " \u00D7 " + boxKeyCount;
```

- [ ] **Step 6: CsboxBulkOverviewScreen.recount 创造下 keyCount = MAX_VALUE**（与 Task 2 Step 6 逐字相同）

```java
        this.boxCount = totalBoxes;
        this.keyCount = this.player.getAbilities().instabuild
                ? Integer.MAX_VALUE
                : (noKeyRequired ? totalBoxes : totalKeys);
        this.openableCount = Math.min(totalBoxes, this.keyCount);
```

- [ ] **Step 7: CsboxBulkOverviewScreen.renderLabels 钥匙行创造分支**（与 Task 2 Step 7 逐字相同）

```java
        String keyDisplay = (this.keyId == null) ? "—" : keyName(this.keyId);
        if (this.keyId != null && this.player.getAbilities().instabuild) {
            drawCentered(guiGraphics, Component.translatable("gui.csgobox.bulk.key_count_infinite").withStyle(row),
                    rowY, 0xFF55FF55);
        } else if (this.keyId == null) {
            drawCentered(guiGraphics, Component.translatable("gui.csgobox.bulk.key_count_no_key", this.boxCount).withStyle(row),
                    rowY, 0xFF55FF55);
        } else {
            drawCentered(guiGraphics, Component.translatable("gui.csgobox.bulk.key_count", keyDisplay, this.keyCount).withStyle(row),
                    rowY, 0xFF55FF55);
        }
```

- [ ] **Step 8: CsboxConfirmScreen.renderLabels 钥匙行创造分支**（与 Task 2 Step 8 逐字相同）

```java
        drawCentered(guiGraphics, Component.translatable(
                        this.player.getAbilities().instabuild
                                ? "gui.csgobox.bulk.key_count_infinite"
                                : this.keyCount == Integer.MAX_VALUE
                                        ? "gui.csgobox.bulk.key_count_no_key"
                                        : "gui.csgobox.bulk.key_count",
                        this.keyCount == Integer.MAX_VALUE ? this.boxCount : this.keyCount),
                rowY, 0xFF55FF55);
```

- [ ] **Step 9: 编译验证 + 冒烟测试**

Run: `./gradlew clean :v26_1_2:compileJava -Pactive_versions=26.1.2`
Expected: BUILD SUCCESSFUL。

Run: `./gradlew :v26_1_2:test -Pactive_versions=26.1.2`
Expected: PlatformSmokeTest 2 PASSED。

- [ ] **Step 10: 提交**

```bash
git add v26_1_2/src/main/java/com/reclizer/csgobox/v26_1_2/packet/PacketCsgoProgress.java v26_1_2/src/main/java/com/reclizer/csgobox/v26_1_2/packet/PacketCsgoBulkProgress.java v26_1_2/src/main/java/com/reclizer/csgobox/v26_1_2/gui/CsboxScreen.java v26_1_2/src/main/java/com/reclizer/csgobox/v26_1_2/gui/CsboxBulkOverviewScreen.java v26_1_2/src/main/java/com/reclizer/csgobox/v26_1_2/gui/CsboxConfirmScreen.java
git commit -m "feat: 创造模式免钥匙开箱（v26_1_2 基准）"
```

---

### Task 4: 家族 A 其余平台 — v1_21_3 / v1_21_4 / v1_21_5 / v1_21_8 / v1_21_10

**Files（每平台各 5 个，路径模式 `v1_21_<x>/src/main/java/com/reclizer/csgobox/v1_21_<x>/...`）:**
- `packet/PacketCsgoProgress.java`（`tryConsumeKeys(Player, ResourceLocation, int)`）
- `packet/PacketCsgoBulkProgress.java`（`countMatchingKeys(Player, ResourceLocation)`）
- `gui/CsboxScreen.java` / `gui/CsboxBulkOverviewScreen.java` / `gui/CsboxConfirmScreen.java`

**Interfaces:**
- Consumes: Task 2 的 5 处插入点（家族 A 逐字相同；行号随平台 ±2 浮动，用函数名定位）
- Produces: 无（终端任务）

- [ ] **Step 1: 对每个平台（v1_21_3、v1_21_4、v1_21_5、v1_21_8、v1_21_10）逐字应用 Task 2 的 Step 1-8**

用 `grep -n "tryConsumeKeys(Player entity, ResourceLocation keyId"` 与 `grep -n "private static int countMatchingKeys(Player player, ResourceLocation keyId"` 定位函数，其余 4 处 GUI 插入点以 Task 2 代码块锚定（`countKeys()` / `canOpen = false;` 后的循环 / `String count = " \u00D7 "` / `this.keyCount = noKeyRequired` / `String keyDisplay` / `this.keyCount == Integer.MAX_VALUE`）。

每平台完成 8 处插入后立刻编译：

```bash
./gradlew clean :v1_21_<x>:compileJava -Pactive_versions=<ver>
```

Expected: BUILD SUCCESSFUL。

- [ ] **Step 2: 提交（每平台一个 commit）**

```bash
git add v1_21_<x>/src/main/java/com/reclizer/csgobox/v1_21_<x>/
git commit -m "feat: 创造模式免钥匙开箱（v1_21_<x>）"
```

---

### Task 5: v1_21_11（家族 B）

**Files:**
- Modify: `v1_21_11/src/main/java/com/reclizer/csgobox/v1_21_11/packet/PacketCsgoProgress.java`（`tryConsumeKeys(Player, Identifier, int)`，约 line 279-300）
- Modify: `v1_21_11/src/main/java/com/reclizer/csgobox/v1_21_11/packet/PacketCsgoBulkProgress.java`（`countMatchingKeys(Player, Identifier)`，约 line 134-165）
- Modify: `v1_21_11/src/main/java/com/reclizer/csgobox/v1_21_11/gui/CsboxScreen.java` / `CsboxBulkOverviewScreen.java` / `CsboxConfirmScreen.java`

**Interfaces:**
- Consumes: Task 2 模板（仅包名类型不同）
- Produces: 无（终端任务）

- [ ] **Step 1: PacketCsgoProgress.tryConsumeKeys 加短路**

在 `tryConsumeKeys(Player entity, Identifier keyId, int count)` 内、`count <= 0` 早退之后、`int remaining = count;` 之前插入：

```java
        if (entity.getAbilities().instabuild) {
            return true;
        }
```

- [ ] **Step 2: PacketCsgoBulkProgress.countMatchingKeys 加短路**

在 `countMatchingKeys(Player player, Identifier keyId)` 内、air 早退（`return Integer.MAX_VALUE;`）之后、`int total = 0;` 之前插入：

```java
        if (player.getAbilities().instabuild) {
            return Integer.MAX_VALUE;
        }
```

- [ ] **Step 3: GUI 4 处插入**

CsboxScreen.countKeys / mouseClicked / 钥匙计数显示、CsboxBulkOverviewScreen.recount / renderLabels、CsboxConfirmScreen.renderLabels —— 与 Task 2 Step 3-8 **逐字相同**（GUI 新代码不引用版本类型；mouseClicked 循环体为 `getNonEquipmentItems()`，保持原样只包一层）。

- [ ] **Step 4: 编译验证**

Run: `./gradlew clean :v1_21_11:compileJava -Pactive_versions=1.21.11`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 5: 提交**

```bash
git add v1_21_11/src/main/java/com/reclizer/csgobox/v1_21_11/
git commit -m "feat: 创造模式免钥匙开箱（v1_21_11）"
```

---

### Task 6: v26_2（家族 C）

**Files:**
- Modify: `v26_2/src/main/java/com/reclizer/csgobox/v26_2/packet/PacketCsgoProgress.java`（`tryConsumeKeys(Player, ItemStack, int)`，line 264）
- Modify: `v26_2/src/main/java/com/reclizer/csgobox/v26_2/packet/PacketCsgoBulkProgress.java`（`countMatchingKeys(Player, ItemStack)`，line 151）
- Modify: `v26_2/src/main/java/com/reclizer/csgobox/v26_2/gui/CsboxScreen.java` / `CsboxBulkOverviewScreen.java` / `CsboxConfirmScreen.java`

**Interfaces:**
- Consumes: Task 3 模板（家族 C 逐字相同）
- Produces: 无（终端任务）

- [ ] **Step 1: 服务端 2 处插入**

与 Task 3 Step 1-2 **逐字相同**（`tryConsumeKeys(Player, ItemStack, int)` + `countMatchingKeys(Player, ItemStack)`）。

- [ ] **Step 2: GUI 4 处插入**

与 Task 3 Step 3-8 **逐字相同**。

- [ ] **Step 3: 编译验证**

Run: `./gradlew clean :v26_2:compileJava -Pactive_versions=26.2`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 4: 提交**

```bash
git add v26_2/src/main/java/com/reclizer/csgobox/v26_2/
git commit -m "feat: 创造模式免钥匙开箱（v26_2）"
```

---

### Task 7: 全平台回归验证

**Files:** 无（只跑命令）

**Interfaces:**
- Consumes: Task 1-6 全部改动
- Produces: 完成证明

- [ ] **Step 1: 9 平台 clean 编译矩阵**

逐平台运行（每次一个 MC 版本）：

```bash
./gradlew clean :v1_21_1:compileJava -Pactive_versions=1.21.1
./gradlew clean :v1_21_3:compileJava -Pactive_versions=1.21.3
./gradlew clean :v1_21_4:compileJava -Pactive_versions=1.21.4
./gradlew clean :v1_21_5:compileJava -Pactive_versions=1.21.5
./gradlew clean :v1_21_8:compileJava -Pactive_versions=1.21.8
./gradlew clean :v1_21_10:compileJava -Pactive_versions=1.21.10
./gradlew clean :v1_21_11:compileJava -Pactive_versions=1.21.11
./gradlew clean :v26_1_2:compileJava -Pactive_versions=26.1.2
./gradlew clean :v26_2:compileJava -Pactive_versions=26.2
```

Expected: 全部 BUILD SUCCESSFUL。

- [ ] **Step 2: common 测试 + 版本一致性**

Run: `./gradlew :common:test`
Expected: BUILD SUCCESSFUL（BoxJsonSchemaValidatorTest 24 用例）。

Run: `scripts/check-version.sh`
Expected: `VERSION SYNC OK: 1.0.6`。

- [ ] **Step 3: 新增代码残留审计**

Run: `git diff HEAD~9 --stat | tail -5` 与 `grep -rn "getAbilities().instabuild" v1_21_1/src v1_21_3/src v1_21_4/src v1_21_5/src v1_21_8/src v1_21_10/src v1_21_11/src v26_1_2/src v26_2/src | wc -l`
Expected: 服务端 2 处/平台 × 9 = 18 处（`tryConsumeKeys` + `countMatchingKeys`），GUI `instabuild` 出现次数 ≥ 36 处（countKeys/mouseClicked/overview recount/overview renderLabels/confirm renderLabels × 9）。

- [ ] **Step 4: 运行时回归清单（手工，dev 环境）**

按 `docs/RELEASE.md` 质量门执行：
1. 创造无钥匙单开 → 开箱动画 + 箱子 -1，钥匙行 "× ∞"。
2. 创造无钥匙批量 → openableCount = 箱数，全部开启，钥匙不消耗。
3. 创造 + `bulkOpenCount` 配置 5 → 批量仍截断 5。
4. 生存无钥匙单开 → 按钮不可点；伪造包被服务端拒绝。
5. 生存批量钥匙不足 → openableCount 截断到钥匙数，行为不变。

- [ ] **Step 5: 收尾提交（如 Task 4/5/6 有遗漏合并）**

若 Step 3 审计发现未提交改动：`git add` 后 commit；否则跳过。
