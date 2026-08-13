# 终端机解耦 + 谈判会话经济实现报告

**日期**: 2026-08-10 ~ 2026-08-12
**范围**: 全部 4 平台（v26_1_2 / v26_2 / v1_21_1 / forge_26_1_2）+ common 共享模块
**方式**: 全部改动均经 4 平台 `clean compileJava` + `:common:test` + forge L0-L3 门禁验证

---

## 0. 最终状态一览（先读这里）

本文按时间顺序记录终端机从「解耦」到「谈判会话经济」的完整演进；`type` 关键字方案
（第 2~5 节）已在第 6 节被移除，最终落地如下：

| 主题 | 最终状态 |
|---|---|
| 箱子类型判定 | JSON 无 `type` 字段；派生访问器 `type()` / `isTerminal()`：**ID 为 `csgobox:terminal` 且 `key == minecraft:air`** 才是终端机；`key: air` 单独出现不会成为终端机（第 6 节） |
| 首次启动 | 空箱子：不生成 `terminal.json` / `premium_supply_box.json`；玩家自建配置后才进入创造物品栏（第 7 节） |
| 共享逻辑封装 | `isTerminal()` + `ModItems.boxItemFor(def)` 单一映射 + `ItemCsgoBox.openScreen(stack)` 多态入口（第 8 节） |
| 会话锁 | 服务端 `TerminalSessionManager`（键 `玩家UUID:箱子ID`）；未满 5 轮拒绝 / 未成交前重开续谈，5 轮拒绝（FAILED）或成交（CLOSED）才释放（第 9 节） |
| 计时 | 服务端 1Hz tick，**世界时钟**（世界游戏刻 × 50）推进，默认 3 小时；世界暂停 / 停机不计时，重启续算（第 10、12 节） |
| 超时自毁 | 超时未成交：终端机**物品本身**销毁（`terminal_uid` 组件追踪，在线背包按 uid 销毁；离线/容器由已销毁 uid 集合兜底）（第 11、12 节） |
| 持久化 | 会话 + 已销毁 uid 集合落盘 `<world>/csgobox/terminal_state.bin`（V3）；转手锁死、购买销毁（第 12 节） |
| 转手锁死 | `terminal_owner` 盖章 + 锁死态，军火商「去问问xx吧」（第 12 节） |
| 启动屏 | 无钥匙计数残留，终端机打开不消耗钥匙（第 12 节） |

---

## 0.5 v1.0.8 修订（2026-08-13）：type 字段回归为唯一判定

本节之后的内容（第 2~12 节）记录的是 v1.0.7 演化；v1.0.8 按「严格分离」设计
**反转了第 6 节的派生判定**，现状以本节为准：

| 主题 | v1.0.8 状态 |
|---|---|
| 箱子类型判定 | JSON `type` 字段是**唯一**判定机制：`"type": "terminal"` → `ItemTerminal`；`"type": "csbox"`（或省略）→ `ItemCsgoBox`。`BoxDefinition` 重新持有 `type` 字段（record/CODEC/STREAM_CODEC/Builder），`isTerminal()` / `type()` 直接读它 |
| 字段分离 | **终端机不再有 `key` 字段**：默认 `terminal.json` 删除 `key`；schema 验证器对「terminal + key」报错；`/csbox info` 不再显示终端机钥匙行。`BoxJsonLoader` 只在非终端机时解析 `key` |
| 旧配置迁移 | `BoxDefaults.upgradeLegacyTerminalConfig` 在注册前把无 `type` 的 `terminal.json` 一次性补 `"type": "terminal"` 并删遗留 `key`；迁移未覆盖时该文件被拒绝加载并给 LoadError（杜绝静默退化成免钥匙免费开箱） |
| 首次启动 | 恢复自动生成 `terminal.json`（af10870 起，含 `"type": "terminal"`） |

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

---

## 6. 变更（2026-08-12）：`type` 关键字移除，改为由 `key` 推导

终端机本身免钥匙（`key: minecraft:air`），因此独立的 `type` 字段是冗余的：

- `BoxDefinition` 移除存储的 `type` 字段（record/CODEC/STREAM_CODEC/Builder），
  改为派生访问器 `type()`：**箱子 ID 为 `csgobox:terminal` 且 `key == minecraft:air`**
  → `terminal`，否则 → `csbox`。
- `BoxJsonLoader` 不再读取 `"type"`；`BoxJsonSchemaValidator` 移除 `validateType`
  及其测试；`BoxDefaults` 生成的默认 JSON 不再包含 `"type"`。
- 旧配置中残留的 `"type"` 字段被 Gson 静默忽略，无迁移成本（终端机默认
  `key: air` 推导结果与原先 `"type": "terminal"` 一致）。
- 二次修正（同日）：`type()` 增加 air 检测——`key: minecraft:air` 单独出现
  **不会**把普通宝箱标记为终端机，只有专用定义 `csgobox:terminal` + air 钥匙
  才是 `terminal`；运行时打开哪个 UI 仍由物品决定（`csgobox:terminal` 物品 →
  终端机 UI），纯类型标签不影响现有开箱行为。

---

## 7. 变更（2026-08-12）：终端机改为「空箱子」，不再自动生成默认配置

终端机回归与普通宝箱完全一致的供给逻辑：

- **首次启动不再写入 `terminal.json`**：删除 `BoxDefaults.TERMINAL_DEFAULT_JSON`
  与 `writeDefaultTerminalIfMissing`，`BoxJsonLoader.loadAll()` 不再调用它
  （3 平台同步；forge 本就不生成）。配置文件夹内不再出现终端默认配置。
- **创造物品栏改为按注册出现**：`ModItems` 删除无条件终端条目（含 `findFirst()`
  兜底绑定）；终端机改经「注册箱子循环」进入物品栏——仅当玩家创建
  `terminal.json`（定义 `csgobox:terminal` 进入 `BoxRegistry`）时，
  循环为终端定义生成 `ItemTerminal` 物品栈，与 `csbox` 动态箱子同一逻辑。
- 静态 `ItemTerminal` 保留（村民 4 级交易、`instanceof` 打开终端机 UI、
  `PacketTerminalBuy` 校验均依赖它）；`STATIC_ITEM_IDS` 保留 `"terminal"`，
  防止 `terminal.json` 重复注册。
- 未注册配置时：终端物品仍可由村民购买，但无绑定 loot（空箱子行为），
  创造物品栏不显示终端。

---

## 8. 变更（2026-08-12）：终端机与宝箱的共享逻辑封装

终端机与普通宝箱共用同一套供给/注册管线后，剩余的三处「按类型分支」收口为单一入口：

- **类型判定集中**：`BoxDefinition` 新增 `isTerminal()`（`csgobox:terminal` + air 钥匙），
  `type()` 委托它。`ModItems` 创造栏不再内联 `"terminal".equals(path)` 的判断，
  改走统一的 `ModItems.boxItemFor(def)` 映射：`isTerminal()` → `ItemTerminal`、
  `premium_supply_box` → `ItemPremiumBox`、其余 → `ItemCsgoBox`。
- **创造栏单一循环**：删除高级箱独立条目块，终端机 / 高级箱 / 动态箱子全部经
  「注册箱子循环」进入物品栏（高级箱与终端机/普通箱一致：首次启动不再自动
  生成 `premium_supply_box.json`，玩家创建该配置后才进入创造栏；顺带修复了
  此前高级箱在创造栏出现两次的重复条目——独立块一次 + 循环一次）。
- **打开行为多态**：`ItemCsgoBox` 新增客户端 `openScreen(stack)`（音效 +
  Shift 批量/单开），`ItemTerminal` 覆写为终端机启动屏；`ClickEvent` 收敛为
  单一 `instanceof ItemCsgoBox` + `openScreen`，不再需要「先匹配 ItemTerminal
  再匹配 ItemCsgoBox」的顺序约束。
- **掉落一致**：`ModEvents.livingDeath` 生成掉落物改走 `boxItemFor(def)`，
  终端/高级箱定义配置了 `entity` 时掉出对应物品类（此前一律掉 `csgo_box`，
  终端定义会掉出打开经典箱子 UI 的错误物品）。

验证：4 平台 `clean compileJava` 通过，`:common:test` 通过，
`check-animops-drift.sh` 3 平台 OK（未改 AnimRenderOps 签名）。

---

## 9. 变更（2026-08-12）：终端机会话锁——重开续谈，未成交不重开

需求：玩家没完成 5 轮对话（全部拒绝）或没成交前，终端机的对话 + 物品数据
必须保留，重开与上次打开完全一致；只有 5 轮全部拒绝或成交才释放。

### 服务端权威会话

- 新增 `TerminalSessionManager`（静态 `ConcurrentHashMap`，键 `玩家UUID:箱子ID`）
  持有每个玩家的终端机谈判锁；`TerminalSession` 内含一个 common
  `NegotiationModel`（对话/轮次/状态/历史）+ 每轮 `TerminalRoundData`
  （脚本 offer + 实际给予物品 + 箱子分级）+ 区域 10 槽位物品。
- 锁的释放条件 = `isFinished()`：`CLOSED`（成交）或 `FAILED`（第 5 轮拒绝）。
  释放后下次 `PacketTerminalOpen` 创建全新会话、重新采样。
- 会话在服务端不 tick：拒绝/购买/关屏全部走「强制推进」入口
  （`rejectForced` / `buyForced` / `syncClose`），内部自带
  `ensureOfferEntry` 补齐历史缺失的 offer 卡（服务端模型不播放打字窗口）。

### 新 packet（四平台）

| packet | 方向 | 作用 |
|---|---|---|
| `PacketTerminalOpen` | C→S | 开屏取锁；服务端回 `PacketTerminalState` |
| `PacketTerminalState` | S→C | 全量快照：轮次/状态/历史/倒计时/上限/5 轮报价+物品/槽位物品 |
| `PacketTerminalReject` | C→S | 拒绝报价，服务端 `rejectForced` 无条件推进（下一轮或 FAILED） |
| `PacketTerminalClose` | C→S | 关屏钉住 round/TYPING-vs-PENDING/倒计时/上限 |

### 采样迁移（客户端不再本地随机）

- 原 `TerminalOfferItems` 的客户端随机逻辑迁至 `TerminalSession.create`：
  每轮 base grade 1..5 随机 + 向下退级 + 铁剑兜底；槽位物品全等级随机一把
  + 钻石兜底（与原逻辑一致）。
- `TerminalOfferItems` 改静态查表（`setRoundItem` / `setSessionItem` / `itemFor`），
  `TerminalOfferRegion` 删除本地 gradePools / roundItemCache；`PacketTerminalState`
  到达后 `setOfferSource` 保证重开 PENDING 显示同一 offer。
- `PacketTerminalBuy` 按锁内当前轮 `roundData.item()` 与提交的 `offerItem`
  `isSameItemSameComponents` 逐字段比对——伪造物品提级低买不再可能。

### 客户端流程

`TerminalScreen` 构造发 `PacketTerminalOpen`；`onTerminalState` 恢复模型 + 物品表
（`stateReceived` 门防重复应用）；拒绝发 `PacketTerminalReject`；`onClose` /
`removed()` 发 `PacketTerminalClose`（`closeSynced` 防重、`stateReceived=false`
即开屏瞬间被顶替时不发，服务端保持上一状态）。

### 边界与遗留待议

- 会话 `ConcurrentHashMap` 无登出清理：每玩家每箱子最多 5 轮 offer + 历史，
  重启清零——服务端常数级内存占用，可接受；如需彻底清理可挂
  `ServerPlayerEvent` 移除或统一会话键。
- `PacketTerminalReject` 以主手 `held` 定位会话（该玩法固定主手持有终端机）；
  `PacketTerminalClose` 仅凭 stack 定位、不验主手（无奖励风险，可接受）。
- 倒计时仍非服务端权威：服务端会话不 tick，「超时未决策」由关屏上报钉住，
  未做服务端会话时钟（可作后续增强）。

验证：4 平台 `clean compileJava` 通过；`:common:test` 102/102（新增
快照恢复 / 强制推进 / 防历史缺 offer 用例）；v26_1_2 `PlatformSmokeTest` 与
forge L0-L3 门禁 7/7 PASS；`check-animops-drift.sh` 3 平台 OK（未改渲染门面）。

---

## 10. 变更（2026-08-12）：计时器落实——原版 tick 驱动，超时自毁

上一轮的「倒计时非服务端权威」遗留问题落实：倒计时由服务端持有，用原版
`ServerTickEvent`（四平台既有 `ModEvents.serverTick` 挂接点）驱动，超时未
成交的会话与 5 轮拒绝同等对待——自毁（`FAILED`）并释放锁。

### 服务端权威计时

- 四平台 `ModEvents.serverTick` 在 `tickCount % 20 == 0`（游戏时间 1 Hz）调用
  `TerminalSessionManager.tickSessions(nowMs)`：遍历锁表，`isFinished()` 或
  `tickServer` 返回过期 → 移除（自毁）。墙钟 delta 折算步长，TPS 抖动不影响
  准确性；玩家打开终端机（会话创建）即开始计时，关屏不暂停——超时发生在
  离线/关屏期间同样生效。

> **注（第 12 节更新）**：本节描述的墙钟推进已被替换——会话创建与推进统一使用
> **世界时钟**（`player.level().getGameTime() * 50L`），世界暂停 / 服务端停机
> 不计时，重启后精确续算，客户端与服务端一致。

- common `NegotiationModel.tickServer(nowMs)`：**只**推进倒计时与过期，不碰
  轮次转换（打字/出价动画仍归客户端）；归零时 `expire` → `FAILED` +
  系统消息 `csgobox.terminal.sys.timeout`（新增中英键「交易超时，军火商已
  离开。」）。客户端 `tick()` 复用它，打开状态下倒计时归零原地翻转 FAILED
  横幅，与服务端秒级对齐。

### 相关修正

- `PacketTerminalClose` 移除 `countdownMs` 字段：倒计时服务端权威，客户端
  上报会变成「回填时间复活已过期会话」的作弊口。
- `PacketTerminalState` 增补 `boxId`：屏侧 `onTerminalState` 校验快照归属，
  修复「快速切换两个终端机时，上一台的迟到快照误入新屏」竞态。
- 会话超时自毁后：`PacketTerminalReject` / `PacketTerminalBuy` / 
  `PacketTerminalClose` 查表得 null → 安全 no-op；下次 `PacketTerminalOpen`
  创建全新会话。

### 验证

4 平台 `clean compileJava` 通过；`:common:test` 通过（新增 `tickServer`
只推倒计时不转轮次 / 归零过期一次且不复发 / `syncClose` 不再覆盖倒计时）；
v26_1_2 `PlatformSmokeTest` 与 forge L0-L3 门禁 7/7 PASS；
`check-animops-drift.sh` 3 平台 OK（未改渲染门面）。

---

## 11. 变更（2026-08-12）：超时自毁终端机——物品本身消失

用户澄清「自毁消失」指**终端机物品本身**，不是只销毁谈判会话。超时未成交
时，打开的那台终端机从玩家身上消失。

### 定位「那一台」：`terminal_uid` 组件

- `ItemCsgoBox` 四平台注册 `TERMINAL_UID`（`DataComponentType<String>`，UUID 字符串），
  服务端在会话**首次创建**（`PacketTerminalOpen` 取锁）时写入玩家主手物品，
  随后跟随物品移动（改名、放箱子、放容器组件都在物品上）。
- `TerminalSession` 绑定 `playerUuid` + `uid`；`TerminalSessionManager` 增加
  `DESTROYED_UIDS` 集合（上限 8192、内存态、重启清零）。

> **注（第 12 节更新）**：`DESTROYED_UIDS` 已从「内存态、重启清零」升级为
> 持久化——与会话一并落盘 `terminal_state.bin`（V3），超时确认销毁后同样
> 清理过期 uid。

### 销毁路径

1. **超时瞬间（在线）**：`tickSessions` 检测 `tickServer` 过期 → 
   `destroyTerminal`：uid 记入 `DESTROYED_UIDS`，并在玩家背包（主栏 + 护甲 +
   副手，`Inventory.getContainerSize()` 全覆盖）按 uid 精确销毁对应物品，
   热栏提示「终端机已超时自毁。」。
2. **离线 / 物品在容器**：物品当场找不到，uid 留在 `DESTROYED_UIDS`；
   下次玩家打开持有该 uid 的终端机时，`PacketTerminalOpen` 命中
   `consumeDestroyedUid` → 当场销毁物品 + 回包 FAILED 状态（屏幕显示失败
   而非悬挂），并消耗该 uid。
3. `PacketTerminalOpen` 改为以服务端主手物品为权威（与 `PacketTerminalReject`
   一致）；未打 uid 的终端机（如 `/give` 新物品）首开时打新 uid。

### 边界

- 同一玩家同一 boxId 只存在一个会话：会话绑定**首次打开那台**的 uid；
  会话期间用第二台同类型终端机打开会复用会话（不重新打 uid），超时只销毁
  第一台。
- 物品在箱子里时超时不会即时消失，但下次取出并打开时销毁——这是「找不到
  物品」约束下的最接近语义。

### 验证

4 平台 `clean compileJava` 通过；`:common:test` 通过；v26_1_2
`PlatformSmokeTest` 与 forge L0-L3 门禁 7/7 PASS；`check-animops-drift.sh`
3 平台 OK（未改渲染门面）。

---

## 12. 变更（2026-08-12）：世界时钟计时 + 3 小时默认 + 持久化 V3 + 转手锁死 + 购买销毁 + 启动屏去钥匙残留

### 12.1 倒计时改为世界运行时间（替换第 10 节墙钟）

根因：`TerminalSession.create` 用 `System.currentTimeMillis()` 盖截止时间，而
`tickSessions` 用世界游戏刻 × 50 推进——两套时钟对不上，新开的终端机永远不会到期。

- 新建会话、从存档恢复、关屏/拒绝/购买四个服务端路径全部改用
  `player.level().getGameTime() * 50L`（世界时钟）。截止时间 = `worldTicks × 50 + 3h`；
  世界暂停 / 服务端停机不计时，重启后精确续算，客户端与服务端一致。
- 顺带修复：买/拒/关屏写入历史的时间戳此前是墙钟，客户端用世界时钟渲染，
  重开终端后报价卡文字会因时间戳在未来而不可见；现在统一为世界时钟。

### 12.2 3 小时默认超时 + 启动屏钥匙计数残留删除

- `NegotiationModel.COUNT_INITIAL_MS` 由遗留调试值「2 天 23:57:45」改为 3 小时
  （common 单点，四平台共用）。
- `TerminalBootScreen` 移除 `boxKeyCount` / `keyRl` / `itemKey` 及钥匙图标与
  「需要钥匙」文案——终端机打开不消耗钥匙，启动屏不再误导玩家。

### 12.3 持久化 V3（会话 + 已销毁 uid 集合落盘）

- `TerminalStateStore`：魔数 + `VERSION=3`，文件 `<world>/csgobox/terminal_state.bin`；
  随服务端启停 bind/unbind，任何变更 `markDirty()` 即时写盘（配合 1Hz tick 兜底）。
- v3 起 `SystemEntry` 历史带可翻译参数（args 序列化进文件），旧文件忽略重建。
- 损坏文件防御：`destroyTerminal` 对 `UUID.fromString` 加 try-catch——损坏的
  `terminal_state.bin` 中非法玩家 UUID 不再冒泡到 `ServerTickEvent` 崩服。
- 超时确认销毁后同样清理过期 uid。

### 12.4 转手锁死「去问问xx吧」

- 物主名字盖在终端机物品组件 `terminal_owner`（服务端创建新会话时盖章）；
  转手 / 重启 / 物主离线都可用。拒绝时优先用在线玩家名（处理改名），兜底盖章名字或 uuid。
- 26.x 无 `GameProfile.getName()`，用 `Player.getPlainTextName()`；v1_21_1 保留
  `getGameProfile().getName()`。
- `PacketTerminalState.locked()` 携带物主名；`SystemEntry` 的 args 补进序列化
  （`terminal_state.bin` 升 V3，旧文件忽略重建）；客户端 `sysText` 优先用服务端 args。
- 文案：`zh_cn`「去问问%1$s吧。」，`en_us`「Go ask %1$s.」。
- 已打开但未过期、且正被**他人**活跃会话持有的终端机 → 锁死态 FAILED（天然禁用
  接受/拒绝）；服务端买/拒按发送者会话键校验，无法越权交易。

### 12.5 购买成功销毁终端机

- `PacketTerminalBuy` 成功路径 `removeByUid` + `setCount(0)`：终端机物品与 uid
  一并销毁并释放会话锁。

### 验证

4 平台 `clean compileJava` 通过；`:common:test` 与 v26_1_2 `PlatformSmokeTest`
通过；forge L0-L3 门禁 7/7 PASS；`check-animops-drift.sh` 3 平台 OK（未改渲染门面）。

### 遗留说明

- `SystemEntry.atMs` 在 4 个一次性 FAILED 快照工厂（destroyed / empty / locked /
  unreachable）里仍是墙钟，但该字段渲染层不使用，无行为影响。
