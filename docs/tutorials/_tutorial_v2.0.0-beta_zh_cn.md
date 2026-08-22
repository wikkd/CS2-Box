# CS2-Box 配置文件参考

> 本文件由模组自动从 `https://gitee.com/hou-xiangling/CS2-Box/docs/tutorials/` 下载。英文版见 [`_tutorial_v2.0.0-beta.md`](./_tutorial_v2.0.0-beta.md)。

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
| `random` | 5 个整数数组   | 否   | `[625, 125, 25, 6, 4]`  | grade1 到 grade5 的权重。数字越大,被抽中的概率越高。                                                                                |
| `entity` | 数组           | 否   | `[]`                    | 会掉落此箱子的生物实体 id。支持两种格式(见下文)。                                                                                   |
| `type`   | 字符串         | 否   | `csbox`                 | 箱子类型标识:`csbox`(普通宝箱,默认)/ `terminal`(终端机)。                                                                           |
| `grade1` | 物品对象数组   | 否   | `[]`                    | 消费级物品(最低稀有度,蓝色)。                                                                                                     |
| `grade2` | 物品对象数组   | 否   | `[]`                    | 工业级物品(靛蓝色)。                                                                                                              |
| `grade3` | 物品对象数组   | 否   | `[]`                    | 军规级物品(品红色)。                                                                                                              |
| `grade4` | 物品对象数组   | 否   | `[]`                    | 受限级物品(橙红色)。                                                                                                              |
| `grade5` | 物品对象数组   | 否   | `[]`                    | 保密级物品(最高稀有度,金色)。                                                                                                     |

### entity 字段格式

`entity` 字段支持两种格式。

**纯列表** —— 所有生物均使用默认 `drop` 概率:

```json
"entity": ["minecraft:zombie", "minecraft:skeleton", "minecraft:creeper"]
```

**交替对** —— 每个生物有独立的掉落概率:

```json
"entity": ["minecraft:zombie", 0.25, "minecraft:skeleton", 0.10, "minecraft:creeper", 0.05]
```

概率必须介于 0.0 到 1.0 之间。超出此范围的值会被原样接受,不会被夹断。

### 箱子类型

`type` 字段决定箱子使用哪种界面和规则:

- `csbox`(默认)—— 经典宝箱。右键打开物品网格预览界面,需要钥匙,开箱播放滚动动画。
- `terminal` —— 终端机掉落池。由 `csgobox:terminal` 物品使用:右键终端机打开终端机界面,而不是宝箱界面。终端机箱子无需钥匙,出价以武库点数计价,见 [终端机](#终端机107)。

## 箱子名称颜色

通过在 `name` 字段前添加一个 hex 颜色前缀和一个英文空格,可为箱子设置彩色名称:

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

前缀格式为 `#RRGGBB ` —— 一个 `#`、6 个十六进制数字(不区分大小写)、一个英文空格。颜色会应用到:

- 物品栏、tooltip 和手持渲染中的箱子名称
- 打开箱子 GUI 顶部居中的标题

未指定前缀时,标题使用默认的 `0xFFD3D3D3` 浅灰色 —— 与该特性加入前完全一致,已有箱子视觉上不受影响。若前缀格式不合法(例如 `#GG5555 Crate` 或 `#FFF Crate` —— hex 位数不足),整串字符串将作为无色名称使用,并在服务端日志中输出 warning,箱子仍能正常加载。

颜色仅在加载时解析,模组不会改写 `config/csbox/` 下的用户 JSON 文件,原始前缀始终保留。

## 物品对象

每个 `grade*` 数组中的条目都是一个物品对象。

| 字段         | 类型       | 必填 | 默认值 | 说明                                                            |
|--------------|------------|------|--------|-----------------------------------------------------------------|
| `id`         | 资源位置   | 是   | -      | 物品 id,例如 `minecraft:diamond_sword`。未知 id 自动跳过。        |
| `count`      | 整数       | 否   | `1`    | 堆叠数量,大多数物品支持 1-64。                                   |
| `price`      | 整数       | 否   | -      | 该物品在终端机的成交价(武库点数);缺省回退到档位默认价。必须为非负整数,见 [终端机](#终端机107)。 |
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
| `grade1`  | consumer   | `#4C70FF`  | 625      | 79.6%     |
| `grade2`  | industrial | `#8D5EFF`  | 125      | 15.9%     |
| `grade3`  | mil_spec   | `#E54AF2`  | 25       | 3.2%      |
| `grade4`  | restricted | `#F86351`  | 6        | 0.8%      |
| `grade5`  | classified | `#FFDC1D`  | 4        | 0.5%      |

这些 hex 颜色是宝箱 GUI、揭晓屏和批量结果屏中物品边框与名称实际使用的颜色。

## 钥匙

| 物品 id                | 材质        |
|------------------------|-------------|
| `csgobox:csgo_key0`    | 铁          |
| `csgobox:csgo_key1`    | 金          |
| `csgobox:csgo_key2`    | 钻石        |
| `csgobox:csgo_key3`    | 下界合金    |

`key` 字段填 `minecraft:air` 表示此箱子无需钥匙。`csgobox:csgo_key3` 只能通过锻造台将 `csgobox:csgo_key2` 与下界合金升级模板合成获得。

钥匙也可以通过军火商村民用武库点数兑换(见 [武库经济](#武库经济107)),或直接合成:

| 钥匙                    | 合成配方             |
|-------------------------|----------------------|
| `csgobox:csgo_key0`     | 3 个铁锭              |
| `csgobox:csgo_key1`     | 3 个金锭              |
| `csgobox:csgo_key2`     | 3 个钻石              |

## 批量开箱(2.0.0)

手持 `csgobox:csgo_box` **Shift+右键**打开批量总览屏,而不是单开预览界面。总览屏显示你拥有的箱子数、钥匙数和本次可开数量,点「开启」直接开箱(无二次确认屏)。

- 结果以流水式 ticker 逐条上升展示;服务器异步计算整批结果,不阻塞游戏主线程。
- 箱子和钥匙在服务端扣减。若中途耗尽,剩余箱子保留在背包中,下一轮继续开启。
- 单批数量上限由 `config/csgobox.toml` 的 `[advanced]` 下 `bulkOpenCount` 控制(`0` = 无上限,默认值)。上限由服务端强制执行,总览屏会镜像显示该限制。
- 终端机物品始终打开自己的界面,不支持批量开箱。

## 终端机(2.0.0)

终端机(`csgobox:terminal`)是一种独立的箱子类物品,拥有独立掉落池:`config/csbox/terminal.json` 中的 `type: terminal` 箱子定义。右键终端机打开终端机界面,而不是宝箱界面。

> **多终端**:与普通宝箱一样,**一个 JSON 文件注册一个终端机**——任意文件只要声明 `"type": "terminal"` 就会注册为对应 id 的终端机(如 `terminal2.json` → `csgobox:terminal2`),拥有自己独立的谈判掉落池。`csgobox:terminal` 本身是静态注册的(与 `csgobox:csgo_box` 同机制),即使 `terminal.json` 不存在物品也始终存在——此时它是**空箱**(不绑定奖池),需自建 `terminal.json` 配置谈判奖池;军火商村民固定出售 `csgobox:terminal`,额外终端机用 `/give` 获取。

- **无需钥匙** —— 终端机没有 `key` 字段,打开不消耗钥匙。
- **出价以武库点数计价**。成交价 = 物品 JSON 的 `price` 字段(若已设置)或该档位的默认价(grade1 = 6、grade2 = 10、grade3 = 16、grade4 = 22、grade5 = 30);若物品无耐久条,还需叠加磨损惩罚(每 5% 磨损 +1 点)。接受报价即支付该价格。
- 每次会话进行 5 轮谈判;每份报价带约 3 天的倒计时;出价物品从终端机箱子的各档位掉落池中挑选。
- 获取途径:创造模式物品栏,或军火商村民(4 级)用 12 武库点数兑换。
- 终端机箱子中物品的 `price` 字段(可选)即该物品的成交价:设了就按它收点数,没设就回退到上面的档位默认价。

## 武库经济(2.0.0)

武库点数(`csgobox:armory_point`)是模组的货币。把该物品加入任意箱子的等级池即可让箱子掉落它,军火商村民也会奖励武库点数。

- **武库拆解台**(`csgobox:armory_recycler`,用铁锭、漏斗、铜锭和红石合成):手持从箱子中开出的物品右键拆解台,可将整组物品兑换成点数。只有带开箱代码打上的档位印记的物品才能回收,普通战利品不能。各档位收益:grade1 = 3、grade2 = 5、grade3 = 8、grade4 = 11、grade5 = 15 点。漏斗也可以把带印记的物品推入自动回收。
- **兑换配方**:3×3 全部填满 64 个武库点数可合成 1 个 `csgobox:csgo_key0`。
- **军火商村民**(职业 `arms_dealer`,工作站点:武库拆解台方块)用材料换点数、用点数换物品:

| 等级 | 交易 |
|------|------|
| 1    | 1 铁锭 → 2 点数;1 绿宝石 → 2 点数 |
| 2    | 1 金锭 → 4 点数;8 点数 → `csgobox:csgo_box` |
| 3    | 1 钻石 → 12 点数;9 点数 → `csgobox:csgo_key0` |
| 4    | 24 点数 → `csgobox:csgo_key1`;12 点数 → `csgobox:terminal` |
| 5    | 45 点数 + 1 钻石 → `csgobox:csgo_key2` |

## 游戏内命令

| 命令 | 权限 | 说明 |
|------|------|------|
| `/csbox` | OP(权限等级 2) | 显示帮助摘要。 |
| `/csbox info` | OP | 列出所有已注册箱子及加载错误。 |
| `/csbox info <箱子ID>` | OP | 查看单个箱子的权重、掉落实体与各档位物品。 |
| `/csbox info error` | OP | 仅显示加载错误(无错误时绿色提示)。 |
| `/csbox reload` | OP | 重新读取 `config/csbox/*.json`。 |
| `/csbox reload tutorial` | OP | 同时强制重新下载教程文档。 |
| `/csbox nbt hand` | 任意玩家 | 打印主手物品的序列化 JSON,可直接粘贴到箱子 `items` 条目。 |
| `/give @p csgobox:csgo_box[csgobox:box_id="csgobox:my_custom_box"]` | OP | 发放指定动态箱子(vanilla 命令)。 |

## 校验规则

- 如果 grade1 到 grade5 全部为空或全部无法解析,文件会被跳过并给出警告,箱子不会被注册。
- 负数或零的 `random` 权重会回退到该等级的默认权重。超过 10000 的权重会被夹断到 10000。
- 未知的物品 id 会被跳过并给出警告;同一等级中的其他物品仍会正常加载。
- 物品 `count` 默认为 1;0 或负值会产生空堆叠,该物品会从等级池中跳过(不会掉落)。
- 物品 `price` 必须为非负整数,违反会被报告为加载错误。
- 加载器容忍未知的顶层字段,会被静默忽略,不报警告。
- `name` 包含格式不合法的 `#RRGGBB ` 前缀时,整串会作为无色名称保留。见 [箱子名称颜色](#箱子名称颜色)。

## 故障排查

**箱子在游戏中没有出现。**
执行 `/csbox info` 查看所有已注册的箱子(`/csbox info error` 单独查看加载错误)。如果你的箱子不在列表中,请检查 `latest.log` 中是否有 `Failed to load box JSON file` 错误。常见原因:JSON 语法错误、缺少逗号、物品 id 不存在。

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
