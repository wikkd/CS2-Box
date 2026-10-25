# compat-packs/test-pack-fabric-1.21.1 — Fabric 1.21.1 平台测试包

针对 `fabric_1_21_1` 平台的纯原版（无需任何其它模组）测试配置包，重点覆盖该
版本线独有的能力：

- **1.21 新物品与新附魔**：mace / heavy_core / trial_key / breeze_rod /
  wind_charge、mace 上的 `minecraft:density` 随机等级附魔；
- **`components` 数据组件字段**（1.21+ 平台专有，1.20.1 忽略）：grade4 的
  「组件之刃」一次声明 custom_name / lore / rarity / unbreakable /
  enchantments 五个组件；
- **`format_version: 2`** 新 schema 标记；
- **`#minecraft:wool` 物品标签展开**：16 色羊毛在 1.18.2 与 1.21.1 成员完全
  一致，价格表整组定价，可与其他版本测试包对照。

> 本包物品 id 含 1.21 新增内容，**只在 Minecraft 1.21.1（fabric_1_21_1）有效**；
> 1.18.2 装入会因未知物品拒载（那边用 `test-pack-forge-1.18.2`）。

## 安装

```bash
cp compat-packs/test-pack-fabric-1.21.1/*.json config/csbox/
# _prices.json 是全局价格表；若已有自己的价格表请手动合并而非覆盖
```

进游戏执行：

```
/csbox reload
/csbox validate
/csbox give @p modern_crate 1
/csbox give @p fabric_terminal 1
```

## 文件说明

| 文件 | 类型 | 演示内容 |
|---|---|---|
| `modern_crate.json` | 普通箱（免钥匙） | `components` 组件剑、1.21 物品/附魔、`#minecraft:wool` 标签、`pity`（classified 每 10 次）、`max_per_player`/`cooldown_seconds`、`format_version: 2` |
| `fabric_terminal.json` | 终端机 | `discount: 0.15`、`stock: 30`、范围价（trial_key / heavy_core / diamond）、`loot_table` 条目（不参与报价） |
| `_prices.json` | 全局价格表 | 覆盖包内全部 id（含 16 色羊毛逐色定价），固定价 + `[min, max]` 范围价 |

## 测试要点

1. **组件剑**：连续开 `modern_crate` 到 grade4，验证钻石剑带自定义名称
  「组件之刃」、紫色史诗边框、传说行、不可破坏与锋利 5（`components` 逐项
   codec 解析失败只会降级为 warning，不拒载整箱）。
2. **mace 附魔**：grade5 的 mace 附 `density` 等级 [1, 5]，每次开箱随机。
3. **标签展开**：grade1 的羊毛条目开箱得 8 个随机颜色羊毛；终端/拆解按
   15 点每个回收（16 色全部有价，缺一个都会拒载整箱）。
4. **范围价**：trial_key [300,500]、heavy_core [2000,3000]、diamond
   [100,400]，每次报价随机取整。
5. **实体掉落**：breeze 10%、zombie 2% 概率掉箱（1.21 新生物演示）。

## 注意

- 价格表必须覆盖箱子里出现的每一个 id 物品（含标签展开成员与 NBT 条目的
  基础 id），缺价会导致箱子文件被拒绝加载。
- Fabric 平台无 TACZ / Create / TOP（上游无 Fabric 构建），本包不含联动内容；
  通用字段演示见 `compat-packs/test-pack/`。
