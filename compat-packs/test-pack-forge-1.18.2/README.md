# compat-packs/test-pack-forge-1.18.2 — Forge 1.18.2 平台测试包

针对 `forge_1_18_2` 平台的纯原版（无需任何其它模组）测试配置包，重点覆盖
该版本线独有的能力：

- **`tag` NBT 直写**（1.18.2 无数据组件系统，物品数据走 ItemStack NBT）：
  「1.18 NBT 测试剑」（Unbreakable + 金色自定义名）、
  `{Potion: ...}` 药剂与药箭——`tag` 用 SNBT 字符串（tacz-pack 同款写法）；
  装载器也接受 JSON 对象形式（自动转 NBT，布尔转 byte），但
  `scripts/check-ids.py` 目前只认字符串形式，故本包统一用 SNBT；
- **`format_version: 2`** 新 schema 标记；
- **`#minecraft:wool` 物品标签展开**：16 色羊毛与 1.21.1 成员完全一致，
  价格表整组定价，可与其他版本测试包对照；
- 物品 id 全部为 1.18.2 注册表内 id（未用任何 1.19+ 内容）。

> 本包为 `forge_1_18_2` 编写并在该平台验证；所用物品 id 在新版均仍存在，
> 理论上其它平台也能装入（NBT 写法在 1.21.x 会被当作数据组件迁移处理）。

## 安装

```bash
cp compat-packs/test-pack-forge-1.18.2/*.json config/csbox/
# _prices.json 是全局价格表；若已有自己的价格表请手动合并而非覆盖
```

进游戏执行：

```
/csbox reload
/csbox validate
/csbox give @p nbt_crate 1
/csbox give @p legacy_terminal 1
```

## 文件说明

| 文件 | 类型 | 演示内容 |
|---|---|---|
| `nbt_crate.json` | 普通箱（免钥匙） | `tag` NBT 剑/药水/药箭、`#minecraft:wool` 标签、`pity`（restricted 每 6 次）、`max_per_player`/`cooldown_seconds`、`format_version: 2` |
| `legacy_terminal.json` | 终端机 | `discount: 0.2`、`stock: 40`、范围价（diamond）、`loot_table` 条目（不参与报价） |
| `_prices.json` | 全局价格表 | 覆盖包内全部 id（含 16 色羊毛逐色定价），固定价 + `[min, max]` 范围价 |

## 测试要点

1. **NBT 剑**：连续开 `nbt_crate` 到 grade4，验证钻石剑不可破坏、名称金色
  「1.18 NBT 测试剑」（1.18.2 的 display.Name 为 JSON 文本组件字符串）。
2. **药水**：grade3 的药剂为强治疗药水、grade2 的药箭为长迟缓药箭
  （`tag` SNBT 直写 NBT；NBT 条目按基础 id 定价）。
3. **标签展开**：grade1 的羊毛条目开箱得 8 个随机颜色羊毛；终端/拆解按
   15 点每个回收（16 色全部有价，缺一个都会拒载整箱）。
4. **范围价**：diamond [100,400]，每次报价随机取整。
5. **实体掉落**：creeper 2%、phantom 3% 概率掉箱。

## 注意

- 价格表必须覆盖箱子里出现的每一个 id 物品（含标签展开成员与 NBT 条目的
  基础 id），缺价会导致箱子文件被拒绝加载。
- 通用字段演示（钥匙箱、附魔书等）见 `compat-packs/test-pack/`；TACZ 枪械
  NBT 写法见 `compat-packs/tacz-pack/`。
