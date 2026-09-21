# compat-packs/test-pack — CS2-Box 测试配置包

纯原版（无需任何其它模组）的**测试配置包**，覆盖 v2.0.1 常见配置字段，
用于本地验证箱子和终端机机制。所有物品 id 均为 Minecraft 原版注册表 id。

## 安装

把本目录的 JSON 复制进游戏配置目录（**`_prices.json` 是全局价格表，整个文件替换**——
如果你已有自己的价格表，请手动合并而不是直接覆盖）：

```bash
# 只装箱子/终端定义（推荐先手动合并 _prices.json）
cp compat-packs/test-pack/test_crate.json  config/csbox/
cp compat-packs/test-pack/keyed_crate.json config/csbox/
cp compat-packs/test-pack/test_terminal.json     config/csbox/
cp compat-packs/test-pack/materials_terminal.json config/csbox/

# 首次安装（若 config/csbox/ 还没有 _prices.json）
cp compat-packs/test-pack/_prices.json config/csbox/
```

进游戏执行：

```
/csbox reload      # 热加载（无需重启）
/csbox validate    # 干跑校验，看有没有报错
/csbox give @p test_crate 1
/csbox give @p test_terminal 1
```

> 自建箱子/终端不再按文件名注册物品（v2.0.1 起），请用 `/csbox give` 发放；
> `/give @p csgobox:<文件名>` 只对 5 个随模组发布的默认 id 有效。
> 若想覆盖默认终端机 `csgobox:terminal` 的奖池，把终端定义改名为 `terminal.json` 再放入配置目录。

## 文件说明

| 文件 | 类型 | 演示的 v2.0.1 字段 |
|---|---|---|
| `test_crate.json` | 普通箱（免钥匙） | `type: csbox`、`key: minecraft:air`、`entity` 实体掉落、`icon`（CMD）、`pity` 保底、`max_per_player` / `cooldown_seconds`、档内 `weight`、`count` 区间、`enchant` 随机附魔 |
| `keyed_crate.json` | 普通箱（需钥匙） | `key: csgobox:csgo_key3` 消耗钥匙、`max_per_player` / `cooldown_seconds` |
| `test_terminal.json` | 终端机 | `type: terminal`、`discount` 折扣、`stock` 库存（售罄即止）、`loot_table` 条目（开箱掉落，不参与终端报价）、范围价 |
| `materials_terminal.json` | 终端机 | 纯材料奖池 + 价格表范围价演示 |
| `_prices.json` | 全局价格表 | 固定价 + `[min, max]` 范围价（每次报价/拆解随机取整） |

## 测试要点

1. **免钥匙箱**：`test_crate` 空手右键直接开；`keyed_crate` 需要 `csgobox:csgo_key3`（锻造台合成，见配方），开箱后钥匙被消耗。
2. **保底**：`test_crate` 配置了 `pity`（restricted 档保底，每 5 次触发一次），连续开箱验证。
3. **约束**：`test_crate` 每玩家 10 次上限、60 秒冷却；`keyed_crate` 5 次上限、120 秒冷却。
4. **终端机**：`test_terminal` 售罄后（stock 50）显示空态（库存内存态，重启恢复）；`discount: 0.2` 所有成交 8 折。
5. **价格表**：`minecraft:diamond`、`minecraft:nautilus_shell`、`minecraft:heart_of_the_sea` 为范围价，每次报价会随机浮动。
6. **拆解台**：所有原版 id 均有价，盖章物品可按表价的 90% 回收（范围价按当次采样价）。

## 注意

- 价格表必须覆盖箱子里出现的**每一个 id 物品**（缺价会导致装箱子文件被拒绝加载）；
  本包已保证全覆盖，若你增删物品请同步补价。
- `loot_table` 条目没有固定物品 id，是唯一不需要定价、也不会被终端机报价的条目。
- 测试完删除对应文件并 `/csbox reload` 即卸载；价格表删除后所有物品恢复无价状态。