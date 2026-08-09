# CS2-Box 事件系统 & KubeJS 兼容

## 概述

CS2-Box 通过 **NeoForge 原生事件总线** 暴露开箱事件，无需任何额外依赖。
当服务器安装了 KubeJS 时，整合包作者可以直接在 JavaScript 脚本中监听该事件。

> **KubeJS 不是 CS2-Box 的前置模组。** 无论是否安装 KubeJS，CS2-Box 均正常运行。

---

## BoxOpenedEvent

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
| 1.21.1 | `com.reclizer.csgobox.v1_21_1.event.BoxOpenedEvent` |
| 26.1.2 | `com.reclizer.csgobox.v26_1_2.event.BoxOpenedEvent` |
| 26.2 | `com.reclizer.csgobox.v26_2.event.BoxOpenedEvent` |

> 已归档（EOL）平台（1.21.0 / 1.21.3 / 1.21.4 / 1.21.5 / 1.21.8 / 1.21.10 / 1.21.11，最后状态见 tag `eol-legacy-21x-1.0.6`）的事件类名规则相同：`com.reclizer.csgobox.v1_21_X.event.BoxOpenedEvent`。

---

## KubeJS 脚本用法

在 `kubejs/server_scripts/` 下创建 `.js` 文件：

```js
// kubejs/server_scripts/csbox_events.js

NeoForgeEvents.onEvent('com.reclizer.csgobox.v1_21_1.event.BoxOpenedEvent', event => {
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

- **为何不取消（non-cancelable）？**
  钥匙和箱子在事件触发前已被消耗。若支持取消需要回滚逻辑，增加复杂度和潜在
  的复制漏洞。如需"禁止某箱子开启"，建议通过修改 `config/csbox/*.json` 移除
  该箱子定义。

- **批量开箱性能**
  批量开启 N 个箱子会触发 N 次事件。对于极大 N（如 100+），监听器应避免
  在事件处理中执行昂贵操作（如数据库写入）。可通过 `isBulk()` 判断并跳过。

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
