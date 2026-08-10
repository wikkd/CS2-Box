# 终端解耦 + `type` 关键字实现报告

**日期**: 2026-08-10
**范围**: 全部 4 平台（v26_1_2 / v26_2 / v1_21_1 / forge_26_1_2）+ common 共享模块
**方式**: 纯源码改动，未编译（遵循「修改进游戏，不编译」）

---

## 1. 背景

上一轮经济重平衡审计发现遗留问题：**终端机（Terminal）在创造模式物品栏中会强制绑定 `BoxRegistry` 第一个已注册箱子**（`ModItems` 中 `findFirst()`），导致终端机的 loot 池永远是"复制第一个箱子"，没有自己的独立掉落。

本次实现两件事：
1. **解耦** —— 终端机拥有独立的 loot 层级（专属 `BoxDefinition` + 独立 JSON 配置）
2. **`type` 关键字** —— box JSON 中新增 `type` 字段，表明该箱子是 `csbox`（普通宝箱）还是 `terminal`（终端机）

---

## 2. 改动清单

### 2.1 `BoxDefinition` 增加 `type` 字段（4 平台）

文件：`{v26_1_2,v26_2,v1_21_1,forge_26_1_2}/src/main/java/com/reclizer/csgobox/<平台>/box/BoxDefinition.java`

- record 末位新增 `String type`
- CODEC：`Codec.STRING.optionalFieldOf("type", "csbox")` —— **默认 `csbox`，旧配置零迁移成本**
- STREAM_CODEC：`write()` 末尾 `buf.writeUtf(def.type())`；`read()` 末尾 `buf.readUtf()` 传入构造器
- Builder：新增 `type` 字段（默认 `"csbox"`）、`type(String)` 方法、`build()` 传参
- 紧凑构造器归一化 + `withUpdatedGrade`（v1_21_1/forge 独有）同步追加

### 2.2 `BoxJsonLoader` 解析 `type` + 生成默认终端（4 平台）

文件：`{v26_1_2,v26_2,v1_21_1,forge_26_1_2}/.../box/BoxJsonLoader.java`

- `parseFromBytes()`：`String type = getString(json, "type", "csbox")` → `builder.type(type)`
- `loadAll()`：在扫描现有 box JSON **之前**调用 `BoxDefaults.writeDefaultTerminalIfMissing(BOXES_DIR)`，保证首次运行即有 `terminal.json`

### 2.3 `BoxJsonSchemaValidator` 校验 `type`（common 共享）

文件：`common/src/main/java/com/reclizer/csgobox/box/BoxJsonSchemaValidator.java`

- `validate()` 链新增 `validateType(json, issues)`
- 仅接受 `csbox` / `terminal`，其他值记 `SchemaIssue`（提示不阻断加载）
- 测试：`BoxJsonSchemaValidatorTest` 新增 `@Nested TypeField` 用例组

### 2.4 `BoxDefaults` 生成默认 `terminal.json`（common 共享）

文件：`common/src/main/java/com/reclizer/csgobox/box/BoxDefaults.java`

- 新增常量 `TERMINAL_DEFAULT_JSON` + 方法 `writeDefaultTerminalIfMissing(Path boxesDir)`
- 仅在 `terminal.json` 不存在时写入，**用户配置永不被覆盖**
- 默认内容：`type: "terminal"`，随机权重 `[20, 40, 80, 160, 300]` **偏向高档**（grade1 最低、grade5 最高），全部使用原版物品（任何版本恒定存在），让终端机开箱即用且是"高级机"

### 2.5 `ModItems` 终端绑定解耦（4 平台）

文件：`{v26_1_2,v26_2,v1_21_1,forge_26_1_2}/.../item/ModItems.java`

```java
// 原：BoxRegistry.getAll().stream().findFirst() → 复制第一个箱子
// 新：优先绑定专属终端定义，缺失时才回退第一个箱子
BoxDefinition terminalDef = BoxRegistry.get(Identifier.parse("csgobox:terminal"));
if (terminalDef != null) {
    terminalStack.set(ItemCsgoBox.BOX_ID.get(), terminalDef.id());
} else {
    BoxRegistry.getAll().stream().findFirst()
            .ifPresent(def -> terminalStack.set(ItemCsgoBox.BOX_ID.get(), def.id()));
}
```

- v26_1_2 / v26_2：`Identifier.parse`；v1_21_1：`ResourceLocation.parse`（API 差异）
- forge_26_1_2：原本**没有**终端绑定块（终端注册为普通 `Item`），本次新增完整绑定块（`setBoxId` 对任意 stack 通用）

### 2.6 现有配置加 `type: "csbox"`（7 个副本）

以下 `weapon_supply_box.json` 均插入 `"type": "csbox"`：

- `runs/client/config/csbox/`
- `runs/server/config/csbox/`
- `v1_21_1/runs/client/config/csbox/`
- `v1_21_1/runs/server/config/csbox/`
- `v26_1_2/runs/client/config/csbox/`
- `v26_1_2/runs/server/config/csbox/`
- `v26_2/runs/client/config/csbox/`

（v26_2/runs/server 亦已更新；forge 尚无 runs 目录，首次运行由 loader 生成）

### 2.7 文档同步

- **README.md**：修正过时的 `grades` 数组示例 → 实际代码用的独立 `grade1`~`grade5` 字段；示例加 `"type": "csbox"`；补充 type 说明
- **docs/CONFIGURATION.md**：3.1 顶级字段表新增 `type` 行；**修正 3.3 节错误说法** —— 代码从不自动写 `weapon_supply_box.json`（普通箱子无内置默认配置，需玩家自建），首次启动只生成 `terminal.json` + 教程 md

---

## 3. 解耦后的行为

| 场景 | 改动前 | 改动后 |
|---|---|---|
| 终端创造物品栏 | 绑定第一个箱子（内容随机） | 绑定 `csgobox:terminal`（专属 loot） |
| 终端开箱掉落 | 与第一个箱子相同 | 独立掉落池（默认偏向高档） |
| 旧 JSON 无 `type` | — | 默认 `csbox`，完全兼容 |
| 玩家自建 `terminal.json` | — | 不会被默认配置覆盖 |
| 终端专属定义缺失 | — | 回退 `findFirst()`，不报错 |

**开箱流水线零改动**：`ItemTerminal extends ItemCsgoBox`，`instanceof` 检查与 `getBoxId()` 回退（item registry id = `csgobox:terminal`）自然解析到新定义。

---

## 4. 验证

- ✅ 4 个 `BoxDefinition`：`type` 贯穿 record/CODEC/STREAM_CODEC/Builder（grep 确认）
- ✅ 4 个 `BoxJsonLoader`：`type` 解析 + `writeDefaultTerminalIfMissing` 调用（grep 确认）
- ✅ common 校验器：`validateType` + 测试用例
- ✅ common BoxDefaults：`TERMINAL_DEFAULT_JSON` + 写入方法
- ✅ 4 个 `ModItems`：`csgobox:terminal` 绑定 + `findFirst()` 兜底（grep 确认）
- ✅ 7 个主箱子配置：`"type": "csbox"`
- ✅ README / docs/CONFIGURATION.md 同步

**未编译**（按用户指示）。建议下次构建时在各平台运行编译确认。

---

## 5. 后续建议

1. 编译验证 4 平台（`./gradlew build`）
2. 游戏内验证：创造栏取终端 → 打开确认显示专属 loot 列表（下界合金/钻石为主）
3. 若需调整终端 loot，直接编辑 `config/csbox/terminal.json`（`/csbox reload` 或重启生效）
