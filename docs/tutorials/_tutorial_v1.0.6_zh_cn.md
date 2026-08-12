# CS2-Box 配置文件参考

> 本文件由模组自动从 `https://gitee.com/hou-xiangling/CS2-Box/docs/tutorials/` 下载。英文版见 [`_tutorial_v1.0.6.md`](./_tutorial_v1.0.6.md)。

## 概述

每个 `.json` 文件定义一个箱子。文件名去掉 `.json` 后,会成为 `csgobox:` 命名空间下的箱子 id(例如 `my_custom_box.json` → `csgobox:my_custom_box`)。

以 `_` 开头的文件保留为文档/模板用途,不会被加载为箱子。

创建一个新箱子:

1. 在 `config/csbox/` 目录下创建 `my_custom_box.json`。
2. 填写[顶层字段](#顶层字段)和至少一个 `grade*` 数组,物品条目按[物品对象](#物品对象)格式编写。
3. 进入游戏执行 `/csbox reload`,然后执行 `/give @p csgobox:csgo_box[csgobox:box_id="csgobox:my_custom_box"]`。

## 顶层字段

| 字段     | 类型           | 必填 | 默认值                  | 说明                                                                                                                                |
|----------|----------------|------|-------------------------|-------------------------------------------------------------------------------------------------------------------------------------|
| `name`   | 字符串         | 是   | 文件名                  | 箱子的显示名,显示在物品 tooltip 和 GUI 标题上。可选的 `#RRGGBB ` 前缀设置自定义颜色,见 [箱子名称颜色](#箱子名称颜色)。            |
| `key`    | 资源位置       | 是   | `csgobox:csgo_key0`     | 玩家打开此箱子需手持的物品 id。用 `minecraft:air` 表示无需钥匙。                                                                     |
| `drop`   | 浮点数         | 否   | `0.12`                  | 默认掉落概率(0.0 到 1.0),作用于 `entity` 列表中未单独指定的生物。                                                                  |
| `random` | 5 个整数数组   | 否   | `[625, 125, 25, 5, 2]`  | grade1 到 grade5 的权重。数字越大,被抽中的概率越高。                                                                                |
| `entity` | 数组           | 否   | `[]`                    | 会掉落此箱子的生物实体 id。支持两种格式(见下文)。                                                                                   |
| `grade1` | 物品对象数组   | 否   | `[]`                    | 消费级物品(最低稀有度,蓝色)。                                                                                                     |
| `grade2` | 物品对象数组   | 否   | `[]`                    | 工业级物品(靛蓝色)。                                                                                                              |
| `grade3` | 物品对象数组   | 否   | `[]`                    | 军规级物品(品红色)。                                                                                                              |
| `grade4` | 物品对象数组   | 否   | `[]`                    | 受限级物品(橙红色)。                                                                                                              |
| `grade5` | 物品对象数组   | 否   | `[]`                    | 保密级物品(最高稀有度,金色)。                                                                                                     |

### entity 字段格式

`entity` 字段支持两种格式。

**纯列表**: 所有生物均使用默认 `drop` 概率:

```json
"entity": ["minecraft:zombie", "minecraft:skeleton", "minecraft:creeper"]
```

**交替对**: 每个生物有独立的掉落概率:

```json
"entity": ["minecraft:zombie", 0.25, "minecraft:skeleton", 0.10, "minecraft:creeper", 0.05]
```

概率必须介于 0.0 到 1.0 之间。超出此范围的值会被原样接受,不会被夹断。

## 箱子名称颜色

在 `name` 字段前添加一个 hex 颜色前缀和一个英文空格,可以为箱子名称上色:

```json
{
  "name": "#FF5555 高级补给箱",
  "key":  "csgobox:csgo_key0",
  "drop": 0.12,
  "random": [625, 125, 25, 5, 2],
  "entity": ["minecraft:zombie"],
  "grade5": [{"id": "minecraft:diamond_sword"}]
}
```

前缀格式为 `#RRGGBB `: 一个 `#`、6 个十六进制数字(不区分大小写)、一个英文空格。颜色作用于:

- 物品栏、tooltip 和手持渲染中的箱子名称
- 打开箱子 GUI 顶部居中的标题

未指定前缀时,标题使用默认的 `0xFFD3D3D3` 浅灰色,与该特性加入前完全一致,已有箱子视觉上不受影响。若前缀格式不合法(例如 `#GG5555 Crate` 或 `#FFF Crate`,hex 位数不足),整串字符串将作为无色名称使用,并在服务端日志中输出 warning,箱子仍能正常加载。

颜色仅在加载时解析,模组不会改写 `config/csbox/` 下的用户 JSON 文件,原始前缀始终保留。

## 物品对象

每个 `grade*` 数组中的条目都是一个物品对象。

| 字段         | 类型       | 必填 | 默认值 | 说明                                                            |
|--------------|------------|------|--------|-----------------------------------------------------------------|
| `id`         | 资源位置   | 是   | -      | 物品 id,例如 `minecraft:diamond_sword`。未知 id 自动跳过。        |
| `count`      | 整数       | 否   | `1`    | 堆叠数量,大多数物品支持 1-64。                                   |
| `components` | 对象       | 否   | -      | Minecraft 1.21+ 数据组件(优先于 `tag`)。                         |
| `tag`        | 字符串     | 否   | -      | 旧版 NBT 标签字符串,保留以兼容 1.20.x。                          |

### 物品示例

自定义名称:

```json
{
  "id": "minecraft:netherite_sword",
  "count": 1,
  "components": {
    "minecraft:custom_name": "{\"text\":\"Excalibur\",\"italic\":false}"
  }
}
```

附魔:

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

玩家头颅:

```json
{
  "id": "minecraft:player_head",
  "count": 1,
  "components": {
    "minecraft:profile": {"name": "wikkd"}
  }
}
```

## 稀有度等级

| 等级      | 内部 id    | 颜色(hex) | 默认权重 | 大致概率  |
|-----------|------------|------------|----------|-----------|
| `grade1`  | consumer   | `#4C70FF`  | 625      | 79.9%     |
| `grade2`  | industrial | `#8D5EFF`  | 125      | 16.0%     |
| `grade3`  | mil_spec   | `#E54AF2`  | 25       | 3.2%      |
| `grade4`  | restricted | `#F86351`  | 5        | 0.64%     |
| `grade5`  | classified | `#FFDC1D`  | 2        | 0.26%     |

这些 hex 颜色是宝箱 GUI、揭晓屏和批量结果屏中物品边框与名称实际使用的颜色。

## 钥匙

| 物品 id                | 材质        |
|------------------------|-------------|
| `csgobox:csgo_key0`    | 铁          |
| `csgobox:csgo_key1`    | 金          |
| `csgobox:csgo_key2`    | 钻石        |
| `csgobox:csgo_key3`    | 下界合金    |

`key` 字段填 `minecraft:air` 表示此箱子无需钥匙。`csgobox:csgo_key3` 只能通过锻造台将 `csgobox:csgo_key2` 与下界合金升级模板合成获得。

## 校验规则

- 如果 grade1 到 grade5 全部为空或全部无法解析,文件会被跳过并给出警告,箱子不会被注册。
- 负数或零的 `random` 权重会回退到该等级的默认权重。超过 10000 的权重会被夹断到 10000。
- 未知的物品 id 会被跳过并给出警告;同一等级中的其他物品仍会正常加载。
- 物品 `count` 默认为 1;0 或负值会产生空堆叠,该物品会从等级池中跳过(不会掉落)。
- 加载器容忍未知的顶层字段,会被静默忽略,不报警告。
- `name` 包含格式不合法的 `#RRGGBB ` 前缀时,整串会作为无色名称保留。见 [箱子名称颜色](#箱子名称颜色)。

## 故障排查

**箱子在游戏中没有出现。**
执行 `/csbox list` 查看所有已注册的箱子(`/csbox errors` 查看加载错误)。如果你的箱子不在列表中,请检查 `latest.log` 中是否有 `Failed to load box JSON file` 错误。常见原因:JSON 语法错误、缺少逗号、物品 id 不存在。

**箱子在但没有物品掉落。**
所有 grade1 到 grade5 数组都是空的,或里面的物品全部解析失败。请先用 `/give` 命令验证每个物品 id 都是真实存在的 Minecraft 物品。

**掉落率与预期不符。**
实体的掉落率按以下优先级取值:该实体单独设置的概率 → 全局 `drop` 字段。Looting 附魔每级额外 +50%(上限 100%)。

**箱子名称颜色没生效。**
前缀必须是 `#` + 恰好 6 个十六进制数字 + 一个英文空格,且出现在 `name` 字符串的最开头。任何偏差(例如 `#FFF 名字`、`#GG5555 名字`、缺少空格)都会按无色名称处理。如果配置了颜色但看不到效果,请检查 `latest.log` 是否有 `Box name has color prefix but empty text` 之类的 warning。

**想删除一个箱子。**
删除对应的 `.json` 文件,然后执行 `/csbox reload`(或重启服务器)。

**想和朋友分享箱子。**
把 `.json` 文件复制到他们的 `config/csbox/` 目录。两边服务器会注册同一个 `csgobox:` id 的箱子。
