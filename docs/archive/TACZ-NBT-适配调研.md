> ⚠️ 已归档（历史快照）：本文档记录 TACZ NBT 适配调研的当时状态，不再随项目更新；当前信息以 README.md 与 docs/ 为准。

# TACZ 1.21.1 NBT 适配调研：箱子物品“货不对版”根因

> 调研对象：`v1_21_1` 平台 + TACZ `1.1.8-hotfix-r6`（MUKSC/TACZ-1.21.1，NeoForge 1.21.1 移植版）。
> 方法：反编译 TACZ jar（CFR）+ MC 1.21.1 官方 client jar + DFU 8.0.16 源码级验证 `DataComponentPatch` 编解码语义。
> 结论先行：**“货不对版”不是 TACZ 渲染端的问题，而是 `BoxItemCodec` 把 NBT 解析失败静默吞掉，产出“裸枪”**。

---

## 1. TACZ 1.21.1 的 NBT 数据模型（实证）

### 1.1 存储位置

TACZ 1.21.1 移植版**不再使用 1.20 时代的顶层 `tag` 字段**，所有枪械/附件数据统一存放在
`minecraft:custom_data` 数据组件内的 `CompoundTag` 里（源码实证：

`GunItemDataAccessor` 全部读写都经
`ItemStack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()` /
`stack.update(DataComponents.CUSTOM_DATA, ...)`）。

### 1.2 枪械键表（`com.tacz.guns.api.item.nbt.GunItemDataAccessor`）

| 键 | NBT 类型 | 含义 | 缺失时行为 |
|---|---|---|---|
| `GunId` | String(RL) | 枪械 ID，如 `tacz:ak47` | 返回 `tacz:empty` → 无枪械索引 → 渲染缺失、无法射击 |
| `GunDisplayId` | String(RL) | 皮肤 ID | 返回 `tacz:default`（按 GunId 默认皮肤渲染） |
| `GunFireMode` | String(枚举名) | `AUTO`/`SEMI`/`BURST`/`UNKNOWN`，**大小写敏感** | `UNKNOWN` |
| `GunCurrentAmmoCount` | Int | 弹匣剩余 | `0` |
| `HasBulletInBarrel` | Byte | 膛内子弹（`contains(...,1)` 按 Byte 判） | `false` |
| `Attachment` + 槽位名 | Compound | 附件（见 1.3） | 无附件 |
| `AttachmentLock` | Byte/Compound | 附件锁 | `false` |
| `GunLevelExp` / `DummyAmmo` / `MaxDummyAmmo` | Int | 等级经验 / 备用弹药 | `0` |
| `LaserColor` | Int | 激光颜色 | 无 |
| `HeatAmount` | Float（运行时） | 过热值 | `0` |
| `OverHeated` | Byte（运行时） | 过热锁 | `false` |

关键代码路径：
- 读取：`getGunId` → `tacz:empty` 兜底（`DefaultAssets.EMPTY_GUN_ID`）；`getName`（客户端）查不到
  `ClientGunIndex` 时回退到物品通用名“现代动能枪械”（`AbstractGunItem#getName`）。
- 构建：`GunItemBuilder.build()` 要求 `TimelessAPI.getCommonGunIndex(gunId)` 存在，否则返回 `ItemStack.EMPTY`
  —— 即 TACZ 自己也不允许引用不存在的枪。

### 1.3 附件键（`AttachmentItemDataAccessor` + `GunItemDataAccessor`）

附件以完整 `ItemStack` NBT 存入 `custom_data.Attachment<槽位>`（如 `AttachmentSCOPE`），
由 `installAttachment` 的 `attachment.saveOptional(provider)` 生成：

```nbt
AttachmentSCOPE: {
  id: "tacz:attachment",
  count: 1,
  components: {
    "minecraft:custom_data": {
      AttachmentId: "tacz:sro_dot",   // 附件 ID（必填）
      Skin: "...",                    // 皮肤（可选）
      ZoomNumber: 0                   // 倍率档位（可选）
    }
  }
}
```

`getAttachment` 用 `ItemStack.CODEC.parse(NbtOps)` 读回，读不到 `components` 或
`components.minecraft:custom_data` 即视为无附件。

### 1.4 物品注册表

枪械物品本体是**单一物品** `tacz:modern_kinetic_gun`（`ModItems.MODERN_KINETIC_GUN`，
`GunItemManager` 注册 `item_type=modern_kinetic`），枪型全部靠 `GunId` 区分。
同理附件是 `tacz:attachment`，弹药是 `tacz:ammo`。这意味着：
**只要 `custom_data` 丢了，`BuiltInRegistries.ITEM.get("tacz:modern_kinetic_gun")` 依然成功**，
箱子照常加载，只是开出来的是一把无法使用的“裸枪” —— 这就是“货不对版”的机制。

---

## 2. CS2-Box 解析链路与失败模式

### 2.1 链路

```
box JSON (gradeN items)
  → BoxItemCodec.parseItem            [v1_21_1/box/BoxItemCodec.java]
  → ItemStack（服务端权威）
  → PacketSyncBoxItems / PacketBoxOpenResult   [ItemStack.OPTIONAL_STREAM_CODEC]
  → 客户端盒子图鉴 / 开箱动画 / 检视屏
```

服务端解析出什么栈，客户端显示与掉落就是什么栈 —— 显示与掉落**不会**互相矛盾；
矛盾的是**配置意图**（作者想要 AK-47）与**解析结果**（裸枪）。

### 2.2 失败模式 A：`dispatchedMap` 全盘丢弃（根因）

`BoxItemCodec.parseItem` 的 `components` 路径（`BoxItemCodec.java:80-87`）：

```java
DataComponentPatch patch = DataComponentPatch.CODEC.parse(JsonOps.INSTANCE, componentsJson)
        .result().orElse(DataComponentPatch.EMPTY);
```

MC 1.21.1 的 `DataComponentPatch.CODEC = Codec.dispatchedMap(persistentTypeCodec, valueCodec)`。
DFU 8.0.16 `DispatchedMapCodec.decode` 实证语义：

- 逐条目解码 key（组件名 → `DataComponentType` 注册表查名）与 value（组件值）；
- **任一条目失败 → 整体 `DataResult.error(partial)`**；partial 里只含成功条目；
- `DataResult.result()` 在 error 时返回**空**（partial 只能经 `resultOrPartial` 拿到）。

结论：**只要 `components` 里有一个键/值解析失败，整个 patch 变成 `EMPTY`，所有 TACZ 数据全部丢失**，
且物品照常被接受（仅一条 `LOGGER.warn`，不进 `LoadError`，schema 校验也不看组件结构）。
常见触发：

- 组件名写错：`"custom_data"` 而非 `"minecraft:custom_data"`（未知组件 → key 解码失败）；
- `GunId` 键名大小写写错（TACZ 键是 `GunId`，不是 `gunid`/`GunID`）；
- 混入另一个格式错误的组件（如手写 `minecraft:enchantments` 值结构不对）—— **连带 TACZ 数据一起丢**；
- 从旧配置迁移时残留了 1.20 时代的 `tag` 内容。

### 2.3 失败模式 B：legacy `tag` 格式对 TACZ 完全无效

`BoxItemCodec.java:89-97`：

```java
var tag = TagParser.parseTag(tagStr);                       // {GunId:"tacz:ak47",...}
DataComponentPatch patch = DataComponentPatch.CODEC.parse(NbtOps.INSTANCE, tag)
        .result().orElse(DataComponentPatch.EMPTY);
```

`DataComponentPatch` 的 key 必须是**完整组件名**（`minecraft:custom_data` 等）。
1.20 时代 TACZ 的顶层 `{GunId:...}` 在这里被当作组件名解析 → 未知组件 → 整体失败 → 空 patch → 裸枪。
**只有**写成 `{minecraft:custom_data:{GunId:...}}` 才等价于 components —— 而这与“兼容旧格式”的初衷矛盾。
任何仍在使用 `"tag": "{GunId:...}"` 写法的旧箱子配置，在 1.21.1 上开出来的都是裸枪。

### 2.4 失败模式 C：JsonOps 往返的类型损失（次要）

`CustomData.CODEC = withAlternative(CompoundTag.CODEC, TagParser.CODEC)`，
`CompoundTag.CODEC` 是 PASSTHROUGH 经 `NbtOps` 转换。JSON 只有 number/boolean，往返后：

- Byte(1) / Short → IntTag（`HasBulletInBarrel` 的 `contains(key,1)` 判 false → 默认 false，影响小）；
- Float → DoubleTag（`HeatAmount` 的 `contains(key,5)` 判 false → 0，纯运行时状态，无妨）；
- IntArray → ListTag（TACZ 不使用）。

关键字段（GunId / GunFireMode / GunCurrentAmmoCount / 附件）无损，**只要 patch 整体没失败**。

### 2.5 失败模式 D：GunId / GunDisplayId 指向未安装内容

- `GunId` 引用了未加载的枪包：服务端 `getCommonGunIndex` 空 → 射击/装填/切火全 no-op（哑枪）；
  客户端 `getGunDisplay` 空 → 图鉴空白卡片（被 `AnimRenderOps.java:172` 守卫吞掉，不画紫黑格）。
- `GunDisplayId` 引用了未加载的皮肤：`getGunDisplay(gunId, displayId)` 空 → 同上。

### 2.6 失败模式 E：序列化端静默丢字段

`serializeItemStack`（`BoxItemCodec.java:116-122`）用 `.result().ifPresent(...)`：
若某个组件无法用 JsonOps 编码，`components` 字段整体不输出 —— `/csbox nbt hand` 给出残缺 JSON，
粘贴后解析即裸枪。TACZ 自身组件可正常编码，但混入第三方 mod 的怪异组件时可能触发。

### 2.7 失败模式 F：`GunFireMode` 大小写

`getFireMode` 用 `FireMode.valueOf(nbt.getString(...))`，写 `"auto"`/`"Semi"` 直接抛
`IllegalArgumentException`。在 CS2-Box 渲染路径上会被 `catch(Throwable)` 兜住回退 2D；
在 TACZ 服务端射击路径上是真实异常。规范值是 `AUTO`/`SEMI`/`BURST`/`UNKNOWN`。

---

## 3. “货不对版”在界面上的完整表现

| 阶段 | 表现 |
|---|---|
| 盒子图鉴（CsboxScreen 卡片） | 该槽位**空白**（`renderItem2D` 对无 display 的 TACZ 枪直接 return） |
| 开箱动画 | 非中奖位靠 `PacketBoxOpenResult.stripComponents` 保留 `custom_data` 才不糊（已修过） |
| 检视屏（CsLookItemScreen） | `isAvailable`=false → 2D 图标；3D 视口不出现 |
| 掉落物品 | “现代动能枪械”裸枪：无模型、无名字、无法开火/装填 |
| 日志 | 仅一条 `Failed to parse components for item ...` warn，`/csbox info error` 查不到 |

已存在的正面兜底（不是 bug，是防紫黑格设计）：`AnimRenderOps.java:172`、`:209-222`
（`getGunDisplay` 空 → 不画）；`PacketBoxOpenResult.stripComponents` 特意保留 `custom_data`；
`TaczInspectViewportImpl` 全程 `catch(Throwable)` 降级。

---

## 4. 修复建议（按优先级）

1. **解析失败降级为逐组件丢弃，而不是全盘丢弃**（治本，改动中等）
   `parseItem` 不再用 `DataResult.result()` 一把梭：对 `components` 逐键解码，
   失败键单独 warn（带键名），成功键照常应用 —— TACZ 数据与坏组件互不拖累。
2. **TACZ 专检（v1_21_1 可编译期拿到 `IGun`）**：解析完成后若 `stack.getItem() instanceof IGun`
   且 `custom_data` 缺 `GunId` / `GunId == tacz:empty`，向 `LoadError` 记一条
   “TACZ gun missing GunId (components parse failed?) + 文件名 + 物品 JSON 摘要”，
   让 `/csbox info error` 直接可见 —— 把静默失败变成可诊断失败。
3. **legacy `tag` 路径失败时明确报错**：`{GunId:...}` 这类旧格式在 1.21.1 已不可能正确解析，
   解析失败时给出指向 `components` 写法的可操作错误（进 LoadError），而不是静默接受裸枪。
4. **服务端校验 GunId 存在性**：`TimelessAPI.getCommonGunIndex(gunId).isEmpty()` →
   LoadError“枪械未加载（缺枪包？）”，与 TACZ 自身 `GunItemBuilder.build()` 的行为对齐。
5. **文档**：教程/README 补充 TACZ 枪械的 `components` 正确写法与常见坑（键名、大小写、
   不要用 `tag`），并提示用 `/csbox nbt hand` 生成条目后**不要手改键名**。

---

## 5. 附录：正确的 TACZ 箱子条目示例

```json
{
  "id": "tacz:modern_kinetic_gun",
  "count": 1,
  "components": {
    "minecraft:custom_data": {
      "GunId": "tacz:ak47",
      "GunFireMode": "AUTO",
      "GunCurrentAmmoCount": 30,
      "HasBulletInBarrel": 0,
      "AttachmentSCOPE": {
        "id": "tacz:attachment",
        "count": 1,
        "components": {
          "minecraft:custom_data": {
            "AttachmentId": "tacz:sro_dot"
          }
        }
      }
    }
  }
}
```

要点：

- `components` 的键必须是完整组件名（`minecraft:custom_data`）；
- TACZ 私有键全在 `custom_data` 内层，键名与大小写严格按上表；
- `GunFireMode` 只能写枚举名；`GunId` 必须是已加载枪包里的 ID（`/csbox nbt hand` 生成的最保险）；
- 推荐工作流：TACZ 里组装好枪 → `/csbox nbt hand` 复制 → 原样粘贴，不要手改。

---

## 附：实证来源

- TACZ jar：`local-repo/com/tacz/tacz/1.1.8-hotfix-r6/tacz-1.1.8-hotfix-r6.jar`
  （CFR 反编译 `GunItemDataAccessor` / `AbstractGunItem` / `GunItemRendererWrapper` / `GunItemBuilder` / `DefaultAssets`）
- MC 1.21.1 官方 client jar + mojmap（`DataComponentPatch` / `DataComponentType` / `CustomData` / `CompoundTag`）
- DFU 8.0.16（`DispatchedMapCodec.decode`：error 携带 partial，`DataResult.result()` 为空）
