> ⚠️ **已归档（ARCHIVED · 2026-08-13）**：军火商经济方向已废弃，本文不再作为实现依据，仅作历史参考。原位置 `docs/superpowers/plans/2026-08-09-armory-points.md`。

# 武库点数 (Armory Point) 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增「武库点数」物品 `csgobox:armory_point`，接入动态箱子 JSON 掉落体系，并提供兑换铁钥匙的合成配方。

**Architecture:** 物品注册沿用现有 `ModItems` 模式；贴图/模型/lang/recipe 全部放 `common` 资源（10 平台共享只写一份）；平台层仅注册行 + creative tab 一行；有 API 适配差异的平台用定点合入，禁止整文件覆盖。

**Tech Stack:** NeoForge (1.21.x legacy / 26.x new)，Java 21/25，Gradle 9.5.1，JUnit 5，Pillow（素材转换）。

## Global Constraints

- `common/` 禁止 import `net.minecraft.*` / `net.neoforged.*`（`:common:checkCommonArchitecture` 自动检查）
- 只改基准平台 `v1_21_1`（legacy）/ `v26_1_2`（new）；其余 8 平台仅定点加 3 行，禁止用基准模块整文件覆盖（v1_21_0/1 可互相镜像，但也逐平台验证）
- 每次 Gradle 调用只构建一个 MC 版本：`-Pactive_versions=1.21.1`（legacy）/ `-Pactive_versions=26.1.2`（new）
- 贴图：`assets/csgobox/textures/item/armory_point.png`，16×16 RGBA（无色区域→alpha 0）
- 兑换配方由用户确认保留：合成表 64 武库点 → 1 铁钥匙

---

### Task 1: 贴图素材转换（JPEG → 16×16 透明 PNG）

**Files:**
- Create: `common/src/main/resources/assets/csgobox/textures/item/armory_point.png`
- Source: `~/Downloads/微信图片_20260809103023_672_36.jpg` (2079×2016 JPEG, 无 alpha)

- [ ] **Step 1: 转换脚本（Pillow，LANCZOS 缩放 + 无色抠图）**

```bash
python3 - <<'PY'
from PIL import Image
src = "/Users/shuangyuexingxun/Downloads/微信图片_20260809103023_672_36.jpg"
out = "common/src/main/resources/assets/csgobox/textures/item/armory_point.png"
im = Image.open(src).convert("RGB").resize((16, 16), Image.LANCZOS)
px = im.load()
result = Image.new("RGBA", (16, 16))
for y in range(16):
    for x in range(16):
        r, g, b = px[x, y]
        sat = (max(r, g, b) - min(r, g, b)) / 255.0
        val = max(r, g, b) / 255.0
        a = 0 if (sat < 0.25 and val > 0.85) else 255
        result.putpixel((x, y), (r, g, b, a))
result.save(out)
print("saved", out)
PY
```

- [ ] **Step 2: 目检抠图效果**

放大预览像素分布：`python3 -c "from PIL import Image; im=Image.open('...'); print(im.size, im.mode); c=0; [c:=c+1 for p in im.getdata() if p[3]==0]; print('transparent px:', c, '/ 256')"`
预期：4~80 个透明像素（背景面积视原图而定），主体留白合理。

- [ ] **Step 3: 入库生成文件**

- [ ] **Step 4: 提交**

```bash
git add common/src/main/resources/assets/csgobox/textures/item/armory_point.png
git commit -m "feat(resource): add armory_point texture (16x16, 无色区透明)"
```

---

### Task 2: 公共模型 + lang（common 资源）

**Files:**
- Create: `common/src/main/resources/assets/csgobox/models/item/armory_point.json`
- Modify: `common/src/main/resources/assets/csgobox/lang/zh_cn.json`
- Modify: `common/src/main/resources/assets/csgobox/lang/en_us.json`

- [ ] **Step 1: 模型 JSON**

```json
{
  "parent": "minecraft:item/generated",
  "textures": {
    "layer0": "csgobox:item/armory_point"
  }
}
```

- [ ] **Step 2: lang（zh_cn.json 的 item 区块加一行）**

`"item.csgobox.armory_point": "武库点数",`

- [ ] **Step 3: lang（en_us.json）**

`"item.csgobox.armory_point": "Armory Point",`

- [ ] **Step 4: 提交**

```bash
git add common/src/main/resources/assets/csgobox/models/item/armory_point.json \
        common/src/main/resources/assets/csgobox/lang/zh_cn.json \
        common/src/main/resources/assets/csgobox/lang/en_us.json
git commit -m "feat(resource): add armory_point item model and lang entries"
```

---

### Task 3: v1_21_1 物品注册（基准 legacy）

**Files:**
- Modify: `v1_21_1/src/main/java/com/reclizer/csgobox/v1_21_1/item/ModItems.java`

- [ ] **Step 1: 注册行 + tab 行（v1_21_1 API：`ITEMS.register(name, Supplier<Item>)`）**

在 `ITEM_CSGO_KEY3` 之后加：

```java
    public static final Supplier<Item> ITEM_ARMORY_POINT = ITEMS.register("armory_point", () -> new Item(new Item.Properties().rarity(Rarity.COMMON)));
```

import 区追加 `net.minecraft.world.item.Rarity;`（若缺失）。creative tab `displayItems` 的 `entries.accept(ModItems.ITEM_CSGO_KEY3.get());` 之后加：

```java
                entries.accept(ModItems.ITEM_ARMORY_POINT.get());
```

- [ ] **Step 2: 编译验证**

```bash
./gradlew :v1_21_1:compileJava -Pactive_versions=1.21.1
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add v1_21_1/src/main/java/com/reclizer/csgobox/v1_21_1/item/ModItems.java
git commit -m "feat(v1_21_1): register armory_point item"
```

---

### Task 4: v26_1_2 注册（基准 new）

**Files:**
- Modify: `v26_1_2/src/main/java/com/reclizer/csgobox/v26_1_2/item/ModItems.java`
- Modify: `v26_1_2/src/test/java/com/reclizer/csgobox/v26_1_2/PlatformSmokeTest.java`

- [ ] **Step 1: 注册行（v26 API：`registerItem(name, factory, propsFn)`）**

```java
    public static final Supplier<Item> ITEM_ARMORY_POINT = ITEMS.registerItem("armory_point", p -> new Item(p.rarity(Rarity.COMMON)), p -> p);
```

tab 内 `entries.accept(ModItems.ITEM_ARMORY_POINT.get());`（KEY3 之后）。

- [ ] **Step 2: PlatformSmokeTest 追加反射断言（不触发静态初始化）**

```java
    @Test
    void armoryPointItemIsDeclared() throws NoSuchFieldException {
        assertNotNull(ModItems.class.getDeclaredField("ITEM_ARMORY_POINT"));
    }
```

- [ ] **Step 3: 测试 + 编译**

```bash
./gradlew :v26_1_2:test -Pactive_versions=26.1.2
```

Expected: BUILD SUCCESSFUL, PlatformSmokeTest 3→4 用例全绿

- [ ] **Step 4: 提交**

```bash
git add v26_1_2/src/main/java v26_1_2/src/test/java
git commit -m "feat(v26_1_2): register armory_point item + smoke test"
```

---

### Task 5: 其余 8 平台定点镜像（禁止整文件覆盖）

**Files:**
- Modify ×8: `v1_21_0`, `v1_21_3`, `v1_21_4`, `v1_21_5`, `v1_21_8`, `v1_21_10`, `v1_21_11`, `v26_2` 各自的 `item/ModItems.java`

- [ ] **Step 1: legacy 模式平台（v1_21_0/3/4/5/8/10/11）**：照 Task 3 的 2 行插入（注册行 + tab 行）
- [ ] **Step 2: new 模式平台（v26_2）**：照 Task 4 的 2 行插入
- [ ] **Step 3: 逐平台 clean 编译**

```bash
./gradlew :v1_21_0:compileJava -Pactive_versions=1.21.0
./gradlew :v1_21_3:compileJava -Pactive_versions=1.21.3
# ...（每个平台一次）
```

Expected: 全部 BUILD SUCCESSFUL

- [ ] **Step 4: 提交**

```bash
git add v1_21_0 v1_21_3 v1_21_4 v1_21_5 v1_21_8 v1_21_10 v1_21_11 v26_2
git commit -m "feat(platforms): mirror armory_point registration across all platforms"
```

---

### Task 6: 兑换配方（64 武库点 → 1 铁钥匙）

**Files:**
- Create: `common/src/main/resources/data/csgobox/recipe/armory_point_exchange.json`

- [ ] **Step 1: 配方 JSON（crafting_shaped 3×3 全填）**

```json
{
  "type": "minecraft:crafting_shaped",
  "pattern": [
    "PPP",
    "PPP",
    "PPP"
  ],
  "key": {
    "P": "csgobox:armory_point"
  },
  "result": {
    "id": "csgobox:csgo_key0",
    "count": 1
  }
}
```

- [ ] **Step 2: 核对 key0 可被 64 格配方接受（armory_point 默认堆叠 64，check）**

- [ ] **Step 3: 提交**

```bash
git add common/src/main/resources/data/csgobox/recipe/armory_point_exchange.json
git commit -m "feat(recipe): exchange 64 armory points for iron key"
```

---

### Task 7: 全平台验证 + 收尾

- [ ] **Step 1: 全平台 clean 编译（改平台时以 clean 确认，防增量造假象）**

```bash
./gradlew clean -Pactive_versions=1.21.1        # legacy 基准
./gradlew :v1_21_1:compileJava -Pactive_versions=1.21.1
# 其余平台同上逐个 clean 编译；new 侧用 26.1.2
```

- [ ] **Step 2: common 测试**

```bash
./gradlew :common:test
```

- [ ] **Step 3: CHANGELOG.md 加条目（`## [Unreleased]` 区块），README 物品列表同步**——如有
- [ ] **Step 4: 最终提交**

---

## Self-Review 记录

- Spec 覆盖：物品（T1 贴图 / T2 模型 / T3-5 注册 / T6 配方）/ lang（T2）/ 测试（T4、T8#2）✓
- 占位符检查：无 TODO/TBD；补偿说明见 T3-T6
- 类型一致性：ITEM_ARMORY_POINT 在 T3/T4 注册、T5 镜像、T7 断言引用一致 ✓
