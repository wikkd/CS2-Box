# CS2-Box 配置文件参考

> 本文件随 mod 内置（离线可用），首次启动时由模组复制到 `config/csbox/` 下，文件名带当前版本号（如 `_tutorial_v2.0.0.md`）。英文版见同目录英文文件。

## 概述

每个 `.json` 文件定义一个箱子。文件名去掉 `.json` 后缀，就是箱子在 `csgobox:` 命名空间下的 id。比如 `my_custom_box.json` 会注册为 `csgobox:my_custom_box`。

以 `_` 开头的文件留给文档和模板用，不会被当成箱子加载。

新建一个箱子：

1. 在 `config/csbox/` 目录下创建 `my_custom_box.json`。
2. 填好[顶层字段](#顶层字段)和至少一个 `grade*` 数组，物品条目按[物品对象](#物品对象)的格式写。
3. 进游戏执行 `/csbox reload`，再执行 `/give @p csgobox:csgo_box[csgobox:box_id="csgobox:my_custom_box"]` 验证。

## 顶层字段

| 字段     | 类型           | 必填 | 默认值                  | 说明                                                                                                                                |
|----------|----------------|------|-------------------------|-------------------------------------------------------------------------------------------------------------------------------------|
| `name`   | 字符串         | 是   | 文件名                  | 箱子的显示名，显示在物品 tooltip 和 GUI 标题上。可选的 `#RRGGBB ` 前缀设置自定义颜色，见 [箱子名称颜色](#箱子名称颜色)。            |
| `key`    | 资源位置       | 是   | `csgobox:csgo_key0`     | 玩家打开此箱子需手持的物品 id。用 `minecraft:air` 表示无需钥匙。                                                                     |
| `drop`   | 浮点数         | 否   | `0.12`                  | 默认掉落概率（0.0 到 1.0），作用于 `entity` 列表中未单独指定的生物。                                                                  |
| `random` | 5 个整数数组   | 否   | `[625, 125, 25, 6, 4]`  | grade1 到 grade5 的权重。数字越大，被抽中的概率越高。                                                                                |
| `entity` | 数组           | 否   | `[]`                    | 会掉落此箱子的生物实体 id。支持两种格式（见下文）。                                                                                   |
| `type`   | 字符串         | 否   | `csbox`                 | 箱子类型标识：`csbox`（普通宝箱，默认）/ `terminal`（终端机）。                                                                           |
| `grade1` | 物品对象数组   | 否   | `[]`                    | 消费级物品（最低稀有度，蓝色）。                                                                                                     |
| `grade2` | 物品对象数组   | 否   | `[]`                    | 工业级物品（靛蓝色）。                                                                                                              |
| `grade3` | 物品对象数组   | 否   | `[]`                    | 军规级物品（品红色）。                                                                                                              |
| `grade4` | 物品对象数组   | 否   | `[]`                    | 受限级物品（橙红色）。                                                                                                              |
| `grade5` | 物品对象数组   | 否   | `[]`                    | 保密级物品（最高稀有度，金色）。                                                                                                     |

### entity 字段格式

`entity` 支持两种写法。

纯列表：所有生物都用默认的 `drop` 概率。

```json
"entity": ["minecraft:zombie", "minecraft:skeleton", "minecraft:creeper"]
```

交替对：每个生物单独给一个掉落概率。

```json
"entity": ["minecraft:zombie", 0.25, "minecraft:skeleton", 0.10, "minecraft:creeper", 0.05]
```

概率要落在 0.0 到 1.0 之间。超出范围的值会被原样接受，不会夹断。

### 箱子类型

`type` 字段决定箱子用哪种界面和规则：

- `csbox`（默认）：经典宝箱。右键打开物品网格预览界面，需要钥匙，开箱播放滚动动画。
- `terminal`：终端机掉落池，由 `csgobox:terminal` 物品使用。右键打开的是终端机界面，不是宝箱界面。终端机不需要钥匙，出价以武库点数计价，见[终端机](#终端机200)。

## 箱子名称颜色

在 `name` 字段前加一个 hex 颜色前缀和一个英文空格，就能给箱子设置彩色名称：

```json
{
  "name": "#FF5555 高级补给箱",
  "key":  "csgobox:csgo_key0",
  "drop": 0.12,
  "random": [625, 125, 25, 6, 4],
  "entity": ["minecraft:zombie"],
  "grade5": [{"id": "minecraft:diamond_sword"}]
}
```

前缀格式是 `#RRGGBB `，也就是一个 `#`、6 个十六进制数字（不区分大小写）和一个英文空格。颜色用在两处：

- 物品栏、tooltip 和手持渲染里的箱子名称
- 打开箱子 GUI 顶部居中的标题

不加前缀时，标题用默认的 `0xFFD3D3D3` 浅灰色，和这个功能加入之前完全一样，老箱子不受影响。前缀格式不对（比如 `#GG5555 Crate` 或 `#FFF Crate`，位数不足）时，整串字符串会当无色名称使用，服务端日志会打一条 warning，箱子照常加载。

颜色只在加载时解析，模组不会改写 `config/csbox/` 下的用户 JSON，原始前缀始终保留。

## 物品对象

每个 `grade*` 数组里的条目都是一个物品对象。

| 字段         | 类型       | 必填 | 默认值 | 说明                                                            |
|--------------|------------|------|--------|-----------------------------------------------------------------|
| `id`         | 资源位置   | 是   | -      | 物品 id，例如 `minecraft:diamond_sword`。未知 id 自动跳过。        |
| `count`      | 整数       | 否   | `1`    | 堆叠数量，大多数物品支持 1-64。                                   |
| `components` | 对象       | 否   | -      | Minecraft 1.21+ 数据组件（优先于 `tag`）。                         |
| `tag`        | 字符串     | 否   | -      | 旧版 NBT 标签字符串，保留以兼容 1.20.x。                          |

> v2.1.0 起**物品对象里不再有 `price` 字段**——终端机价格的唯一来源是
> `config/csbox/_prices.json`，见 [价格表](#价格表)。

### 物品示例

自定义名称：

```json
{
  "id": "minecraft:netherite_sword",
  "count": 1,
  "components": {
    "minecraft:custom_name": "{\"text\":\"Excalibur\",\"italic\":false}"
  }
}
```

附魔：

```json
{
  "id": "minecraft:diamond_sword",
  "count": 1,
  "components": {
    "minecraft:enchantments": {
      "levels": {
        "minecraft:sharpness": 5,
        "minecraft:looting": 3,
        "minecraft:unbreaking": 3
      }
    }
  }
}
```

玩家头颅：

```json
{
  "id": "minecraft:player_head",
  "count": 1,
  "components": {
    "minecraft:profile": {"name": "wikkd"}
  }
}
```

## 价格表

v2.1.0 起，所有终端机的物品成交价（武库点数）统一放在 **`config/csbox/_prices.json`**
管理，不再写进箱子 JSON。`_` 开头保证它不会被当成箱子加载。格式是**物品 id → 非负整数**：

```json
{
  "minecraft:diamond_sword": 1500,
  "minecraft:arrow": 200,
  "tacz:modern_kinetic_gun#tacz:deagle_golden": 30000
}
```

- 同一物品 id 在所有终端机同价（没有箱子级覆盖）。没写进表的物品**没有价格**：
  终端机不会报价、拆解回收为 0，且包含未定价 id 物品的箱子会被拒绝加载（必须先在
  价格表补价，无默认价回退）。
- 靠 NBT 区分不同价格的物品（如 TACZ 枪械/弹药）用 `id#变体` 子键：加载器会读物品
  条目旧版 `tag` 里的 `GunId` / `AmmoId` 拼键。查价优先变体键，没有就回退纯 id 价。
- 模组不会自动创建一张空表；但旧版（v2.0.x）写在箱子 JSON 里的 `price` 会在下次
  `/csbox reload`（或热重载/重启）时**自动迁移**进这张表，并把箱子里的 `price`
  字段一并删掉（幂等，不会反复改写）。同一物品在不同箱子价格不同时，按平均值
  （四舍五入）迁移；表里已有的价格优先，不会被旧配置覆盖。`#tag` / `loot_table`
  条目和非法价格（负数/小数）无法自动迁移，会保留原位报错。
- `/csbox validate` 会校验这张表，`/csbox reload`（或热重载）即时生效。

## 稀有度等级

| 等级      | 内部 id    | 颜色(hex) | 默认权重 | 大致概率  |
|-----------|------------|------------|----------|-----------|
| `grade1`  | consumer   | `#4C70FF`  | 625      | 79.6%     |
| `grade2`  | industrial | `#8D5EFF`  | 125      | 15.9%     |
| `grade3`  | mil_spec   | `#E54AF2`  | 25       | 3.2%      |
| `grade4`  | restricted | `#F86351`  | 6        | 0.8%      |
| `grade5`  | classified | `#FFDC1D`  | 4        | 0.5%      |

## 钥匙

| 物品 id                | 材质        |
|------------------------|-------------|
| `csgobox:csgo_key0`    | 铁          |
| `csgobox:csgo_key1`    | 金          |
| `csgobox:csgo_key2`    | 钻石        |
| `csgobox:csgo_key3`    | 下界合金    |

`key` 字段填 `minecraft:air` 表示这个箱子不需要钥匙。

## 终端机(2.0.0)

终端机（`csgobox:terminal`）是一种独立的箱子类物品，掉落池来自 `config/csbox/terminal.json` 里声明了 `type: terminal` 的箱子定义。右键终端机打开终端机界面，而不是宝箱界面。

一个 JSON 文件注册一个终端机。任何文件只要声明 `"type": "terminal"`，就会注册成对应 id 的终端机（比如 `terminal2.json` → `csgobox:terminal2`），各配各的掉落池。`csgobox:terminal` 本身是静态注册的（和 `csgobox:csgo_box` 同机制），就算没有 `terminal.json` 物品也一直存在，只是空箱（不绑奖池）。想配奖池就自己建 `terminal.json`。

- 终端机没有 `key` 字段，不消耗钥匙。
- 出价用武库点数算。成交价取自 `config/csbox/_prices.json`（按物品 id 查价，见
  [价格表](#价格表)）——没写进表 = 没有价格（终端机不报价该物品；包含未定价 id
  物品的箱子加载会报错）。物品没有耐久条的话还要加磨损惩罚，按磨损
  **百分比**加价：加价 = 基础价 × 20% × 磨损值（向上取整），满磨损（战痕累累）最多 +20%。

## 校验规则

- 如果 grade1 到 grade5 全部为空或全部无法解析，文件会被跳过并给出警告，箱子不会被注册。
- 负数或零的 `random` 权重会回退到该等级的默认权重。超过 10000 的权重会被夹断到 10000。
- 未知的物品 id 会被跳过并给出警告；同一等级中的其他物品仍会正常加载。
- 物品 `count` 默认为 1；0 或负值会产生空堆叠，该物品会从等级池中跳过（不会掉落）。
- 旧版物品对象里残留的 `price` 字段会被报告为加载错误，但**下次 `/csbox reload` 会
  自动迁移进 `_prices.json` 并移除该字段**（同物不同价取平均）。
- `_prices.json` 里的键必须是 `ns:path`（可选 `#变体`），值必须是非负整数；非法条目会报错并被跳过。
- 加载器容忍未知的顶层字段，会被静默忽略，不报警告。
- `name` 包含格式不合法的 `#RRGGBB ` 前缀时，整串会作为无色名称保留。见 [箱子名称颜色](#箱子名称颜色)。

## 故障排查

**箱子在游戏中没有出现。**
执行 `/csbox info` 查看所有已注册的箱子（`/csbox info error` 单独查看加载错误）。如果你的箱子不在列表里，检查 `latest.log` 里有没有 `Failed to load box JSON file` 错误。常见原因：JSON 语法错误、缺少逗号、物品 id 不存在。

**箱子在但没有物品掉落。**
grade1 到 grade5 全是空的，或者里面的物品全部解析失败。先用 `/give` 验证每个物品 id 都是真实存在的 Minecraft 物品。

**掉落率与预期不符。**
实体的掉落率按这个顺序取值：该实体单独设置的概率，然后是全局 `drop` 字段。

**箱子名称颜色没生效。**
前缀必须是 `#` + 恰好 6 个十六进制数字 + 一个英文空格，而且要出现在 `name` 字符串的最开头。任何偏差（`#FFF 名字`、`#GG5555 名字`、缺少空格）都会按无色名称处理。配了颜色但没效果的话，检查 `latest.log` 里有没有 `Box name has color prefix but empty text` 之类的 warning。

**想删除一个箱子。**
删掉对应的 `.json` 文件，然后执行 `/csbox reload`（或重启服务器）。

**想和朋友分享箱子。**
把 `.json` 文件复制到对方的 `config/csbox/` 目录。两边服务器会注册同一个 `csgobox:` id 的箱子。
