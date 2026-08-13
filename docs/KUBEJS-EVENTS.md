# CS2-Box 事件系统 & KubeJS 兼容

## 概述

CS2-Box 通过 **NeoForge 原生事件总线**（forge 实验模块为 Forge 事件总线）暴露开箱 / 终端 / 回收事件，无需任何额外依赖。
当服务器安装了 KubeJS 时，整合包作者可以直接在 JavaScript 脚本中监听这些事件。

> **KubeJS 不是 CS2-Box 的前置模组。** 无论是否安装 KubeJS，CS2-Box 均正常运行。

**事件一览**：

| 事件 | 时机 | 可取消 | 作用 |
|------|------|--------|------|
| `BoxOpeningEvent` | RNG 之前、消耗之前 | ✅ | 拒绝/放行开箱（权限、任务、活动门槛） |
| `BoxOpenedEvent` | 开箱成功、物品已发放 | ❌ | 广播、统计、追加奖励 |
| `TerminalBuyEvent` | 终端机成交后 | ❌ | 首次获得登记、军械点经济记录 |
| `ArmoryRecycleEvent` | 回收站消耗物品前 | ✅ | 物品黑名单（刷点屏蔽） |

---

## BoxOpeningEvent（开箱前，可取消）

每次玩家请求开箱、且通过内置守卫（主手是箱子、存活、冷却已过、非终端机）之后、**服务端 RNG 与任何消耗之前**触发。取消后开箱干净中止：无回滚、不消耗钥匙/箱子、不设置冷却。

| 属性 | 类型 | 说明 |
|------|------|------|
| `getEntity()` | `Player` | 开箱的玩家（继承自 `PlayerEvent`） |
| `getBoxId()` | `ResourceLocation` / `Identifier` | 箱子定义 ID，如 `csgobox:weapon_case` |
| `isBulk()` | `boolean` | 是否为批量开箱请求 |
| `getCount()` | `int` | 本次开箱数量：单开 = 1；批量 = 服务端授权的批次大小 |
| `isCanceled()` / `setCanceled(boolean)` | — | 取消则拒绝本次开箱（KubeJS 脚本用 `event.cancel()`） |

> **边界契约**：事件在 RNG 之前、消耗之前触发，但不会为已被内置守卫拒绝的请求触发（例如冷却中、物品不是箱子）。它只能**允许或拒绝**开箱，不能改变开箱结果；如需把开箱重定向到另一个箱子定义，请使用包装箱（wrapper box）方案。

### KubeJS 示例：按权限/任务门槛拒绝开箱

```js
// kubejs/server_scripts/csbox_gate.js
const BOX_EVENT = 'com.reclizer.csgobox.<版本>.event.BoxOpeningEvent'

NeoForgeEvents.onEvent(BOX_EVENT, event => {
    // 活动箱子：只有拥有标签 csbox_vip 的玩家能开
    if (event.getBoxId().toString() == 'csgobox:vip_case') {
        let player = event.getEntity()
        if (!player.persistentData.trusted) {
            player.tell('你没有权限开启这个箱子')
            event.cancel()
        }
    }
    // 每日限量：通过玩家 NBT 计数
    if (event.isBulk()) return
    let p = event.getEntity()
    let today = new Date().toISOString().slice(0, 10)
    if (p.persistentData.csboxDay != today) {
        p.persistentData.csboxDay = today
        p.persistentData.csboxCount = 0
    }
    if (p.persistentData.csboxCount >= 20) {
        p.tell('今日开箱次数已用完')
        event.cancel()
    } else {
        p.persistentData.csboxCount++
    }
})
```

> 批量开箱是**批次级**事件：一次批量请求触发一次 `BoxOpeningEvent`（`isBulk()=true`），取消即拒绝整批。批量内的每一次开箱成功仍各自触发 `BoxOpenedEvent`。

---

## BoxOpenedEvent（开箱后，通知）

每次玩家成功开箱并获得物品后触发（钥匙和箱子已被消耗，物品已发放）。

| 属性 | 类型 | 说明 |
|------|------|------|
| `getEntity()` | `Player` | 开箱的玩家（继承自 `PlayerEvent`） |
| `getBoxId()` | `ResourceLocation` / `Identifier` | 箱子定义 ID，如 `csgobox:weapon_case` |
| `getResultItem()` | `ItemStack` | 玩家获得的物品 |
| `getGrade()` | `int` | 稀有度等级 1–5（见下表） |
| `isBulk()` | `boolean` | 是否为批量开箱的一部分 |

### 稀有度等级

| 等级 | 名称 | 颜色 |
|------|------|------|
| 1 | Consumer（消费级） | 灰色 |
| 2 | Industrial（工业级） | 浅蓝 |
| 3 | Mil-Spec（军规级） | 蓝色 |
| 4 | Restricted（受限级） | 紫色 |
| 5 | Classified（保密级） | 粉/红 |

### 事件类全限定名

不同 MC 版本的包名不同：

| MC 版本 | 事件类 |
|---------|--------|
| 1.21.1 | `com.reclizer.csgobox.v1_21_1.event.BoxOpeningEvent` / `BoxOpenedEvent` |
| 26.1.2 | `com.reclizer.csgobox.v26_1_2.event.BoxOpeningEvent` / `BoxOpenedEvent` |
| 26.2 | `com.reclizer.csgobox.v26_2.event.BoxOpeningEvent` / `BoxOpenedEvent` |
| forge_26_1_2（实验） | `com.reclizer.csgobox.forge_26_1_2.event.BoxOpeningEvent` / `BoxOpenedEvent`（KubeJS 用 `ForgeEvents.onEvent`） |

> 已归档（EOL）平台（1.21.0 / 1.21.3 / 1.21.4 / 1.21.5 / 1.21.8 / 1.21.10 / 1.21.11，最后状态见 tag `eol-legacy-21x-1.0.6`）的事件类名规则相同：`com.reclizer.csgobox.v1_21_X.event.BoxOpenedEvent`。

---

## KubeJS 脚本用法（BoxOpenedEvent）

在 `kubejs/server_scripts/` 下创建 `.js` 文件：

```js
// kubejs/server_scripts/csbox_events.js

NeoForgeEvents.onEvent('com.reclizer.csgobox.<版本>.event.BoxOpenedEvent', event => {
    let player = event.getEntity()
    let boxId = event.getBoxId()       // ResourceLocation
    let item = event.getResultItem()    // ItemStack
    let grade = event.getGrade()        // int 1-5
    let bulk = event.isBulk()           // boolean

    // 示例 1：全服广播稀有掉落（等级 >= 4）
    if (grade >= 4 && !bulk) {
        event.getServer().runCommandSilent(
            `tellraw @a {"text":"${player.getName().getString()} 开出了 ${item.getHoverName().getString()}！","color":"gold"}`
        )
    }

    // 示例 2：批量开箱时给额外经验
    if (bulk) {
        player.giveExperiencePoints(5)
    }

    // 示例 3：按箱子 ID 触发自定义逻辑
    if (boxId.toString() == 'csgobox:dragon_box') {
        player.potionEffects.add('minecraft:regeneration', 200, 0)
    }
})
```

> **注意**：请将事件类名中的版本号替换为你实际使用的 MC 版本对应的包名（见上表）。

---

## TerminalBuyEvent（终端机成交后，通知）

终端机谈判成交后触发：军械点已扣除、物品已发放、终端机物品已消耗。终端机购买**不会**触发 `BoxOpenedEvent`（是独立管线），需要同时覆盖开箱与终端两条路径的监听器应同时订阅两个事件。

| 属性 | 类型 | 说明 |
|------|------|------|
| `getEntity()` | `Player` | 购买玩家（继承自 `PlayerEvent`） |
| `getGrade()` | `int` | 成交物品等级 1–5 |
| `getPrice()` | `int` | 实际扣除的军械点数（含磨损惩罚，服务端权威） |
| `getWearVal()` | `float` | 该报价的磨损值（已应用到物品） |
| `getItem()` | `ItemStack` | 成交物品（数量恒为 1） |
| `getRound()` | `int` | 成交发生的谈判轮次 1–5 |

```js
// kubejs/server_scripts/terminal_track.js
NeoForgeEvents.onEvent('com.reclizer.csgobox.<版本>.event.TerminalBuyEvent', event => {
    let item = event.getItem()
    // 首次获得登记：把物品 id 写入全局进度
    let id = item.getId().toString()
    if (!global.csboxFirstSeen) global.csboxFirstSeen = []
    if (!global.csboxFirstSeen.includes(id)) {
        global.csboxFirstSeen.push(id)
        event.getServer().runCommandSilent(`title ${event.getEntity().getName().getString()} title {"text":"新收藏！","color":"gold"}`)
    }
})
```

---

## ArmoryRecycleEvent（回收站消耗前，可取消）

武库拆解台（回收站）即将消耗输入物品并产出军械点之前触发。取消后**输入物品保留在机器内**、不产出任何点数（进度重置，玩家可自行取回）。适用于刷点屏蔽与物品黑名单。

| 属性 | 类型 | 说明 |
|------|------|------|
| `getBlockEntity()` | `ArmoryRecyclerBlockEntity` | 拆解台机器 |
| `getInputItem()` | `ItemStack` | 即将被消耗的物品（**副本**，修改无效） |
| `getGrade()` | `int` | 输入物品等级 1–5 |
| `getYield()` | `int` | 本将产出的军械点数 |
| `isCanceled()` / `setCanceled(boolean)` | — | 取消则跳过本次回收（KubeJS 脚本用 `event.cancel()`） |

```js
// kubejs/server_scripts/recycle_blacklist.js
NeoForgeEvents.onEvent('com.reclizer.csgobox.<版本>.event.ArmoryRecycleEvent', event => {
    let id = event.getInputItem().getId().toString()
    // 被刷点路径污染的物品：禁止回收成军械点
    if (global.csboxRecycleBlacklist?.includes(id)) {
        event.cancel()
    }
})
```

---

## 只读访问（查询箱子定义与配置）

脚本可直接用 `Java.loadClass` 读取服务端注册表与配置（只读，勿调用 `register` / `remove` / `clear` 修改）：

```js
// kubejs/server_scripts/csbox_readonly.js
const BoxRegistry = Java.loadClass('com.reclizer.csgobox.<版本>.box.BoxRegistry')
const CsgoBox    = Java.loadClass('com.reclizer.csgobox.<版本>.CsgoBox')

// 1) 列出所有箱子定义（BoxDefinition 是 record，字段名即 getter 名）
let all = BoxRegistry.getAll()
for (let def of all) {
    // def.id() / def.name() / def.key() / def.grades() / def.dropEntities() ...
    console.log(`箱子 ${def.id()}：${def.grades().size()} 级`)
}

// 2) 查单个箱子
let def = BoxRegistry.get(Identifier.parse('csgobox:weapon_case'))
if (def) {
    let weights = def.getWeightArray()   // 每级权重
    console.log(`权重: ${weights.join('/')}`)
}

// 3) 读配置快照（CsgoBox.CONFIG 是 public static final）
let cfg = CsgoBox.CONFIG
console.log(`全局掉率 ${cfg.globalDropRatePercent()}% · 批量上限 ${cfg.bulkOpenCount()}`)

// 4) 开箱 JSON 加载错误（/csbox error 的数据源）
const BoxJsonLoader = Java.loadClass('com.reclizer.csgobox.<版本>.box.BoxJsonLoader')
if (BoxJsonLoader.hasLoadErrors()) {
    for (let err of BoxJsonLoader.getLastLoadErrors()) {
        console.log(`箱子配置错误: ${err}`)
    }
}
```

> 配置值在服务端为权威；客户端脚本读到的是客户端侧配置。不要从脚本调用 `BoxRegistry.register/remove/clear`——箱子定义由 `config/csbox/*.json` 与热重载管理。

---

## Java 模组监听

其他 Java 模组可以直接通过 NeoForge 事件总线监听：

```java
@EventBusSubscriber(modid = "your_mod_id")
public class CsboxEventHandler {

    @SubscribeEvent
    public static void onBoxOpened(BoxOpenedEvent event) {
        Player player = event.getEntity();
        int grade = event.getGrade();

        if (grade >= 5) {
            // 保密级掉落：全服广播
            player.getServer().getPlayerList().broadcastSystemMessage(
                Component.literal(player.getName().getString() + " 开出了传说物品！")
                    .withStyle(ChatFormatting.GOLD),
                false
            );
        }
    }

    @SubscribeEvent
    public static void onBoxOpening(BoxOpeningEvent event) {
        // 取消开箱（例如玩家处于某任务状态）
        if (playerIsLocked(event.getEntity())) {
            event.setCanceled(true);
        }
    }
}
```

添加对 CS2-Box 的编译依赖（`build.gradle`）：

```groovy
dependencies {
    compileOnly files('libs/csgobox-1.21.1-1.0.6.jar') // 对应版本
}
```

---

## 设计说明

- **为何是 post-event（事后通知）？**
  开箱动画依赖服务端发送的完整结果序列。如果在 RNG 计算后、发包前修改结果，
  会导致客户端动画与实际物品不一致。因此事件在物品发放后触发，保证一致性。

- **可取消的钩子放在哪里？**
  唯一可取消的入口是 RNG **之前**的 `BoxOpeningEvent`（和回收站的 `ArmoryRecycleEvent`，
  其消耗点是安全的取消边界）。RNG 后 / 发包前是禁区——取消或修改结果都需要回滚，
  增加复杂度和潜在的复制漏洞。如需"禁止某箱子开启"，优先用 `BoxOpeningEvent` 取消，
  或通过修改 `config/csbox/*.json` 移除该箱子定义。

- **批量开箱性能**
  批量开启 N 个箱子会触发 1 次 `BoxOpeningEvent`（批次级）+ N 次 `BoxOpenedEvent`。
  对于极大 N（如 100+），`BoxOpenedEvent` 监听器应避免在事件处理中执行昂贵操作
  （如数据库写入）。可通过 `isBulk()` 判断并跳过。

- **终端机是独立管线**
  终端购买（`TerminalBuyEvent`）与武库拆解（`ArmoryRecycleEvent`）都使用军械点
  经济，但不经过开箱管线；做经济统计时请订阅三个事件各自记账，避免重复。

---

## 自定义钥匙（KubeJS 物品）

CS2-Box 的钥匙匹配是**纯 ID 匹配**（`BuiltInRegistries.ITEM.getKey()`），
不要求物品是 `ItemCsgoKey` 的实例。因此 KubeJS 注册的物品可直接用作钥匙：

```js
// kubejs/startup_scripts/items.js
StartupEvents.registry('item', event => {
    event.create('dragon_key')
        .displayName('龙钥匙')
        .texture('kubejs:item/dragon_key')
        .maxStackSize(64)
})
```

```json
// config/csbox/dragon_box.json
{
    "name": "龙箱",
    "key": "kubejs:dragon_key",
    "drop": 0.08,
    "weights": [500, 200, 50, 10, 3],
    "grade5": ["minecraft:dragon_egg"],
    "grade4": ["minecraft:elytra"]
}
```

无需任何代码修改即可生效。
