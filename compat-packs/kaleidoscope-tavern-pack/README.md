# 森罗酒馆（kaleidoscope_tavern）联动数据包

森罗物语系列附属模组的箱子与终端机配置示例。物品 id 以发布 jar 的
`assets/*/models/item` × `zh_cn` lang 键交叉实测为准（1.21.1 NeoForge 与
1.20.1 Forge 双线共有物品；单线特供不入包）。

> 酒馆家族本体（世界名酒/次元酒窖/血酒等附属的前置）。 本配置带 `"requires": ["kaleidoscope_tavern"]` 门控：模组未装时整箱
> 跳过并在 `/csbox info error` 报错。

## 文件

| 文件 | 内容 |
|---|---|
| `kt_crate.json` | 森罗酒馆军火箱（csbox）：5 档池见下 |
| `kt_terminal.json` | 森罗酒馆终端（terminal）：同一物品池的终端报价 |
| `_prices.json` | 全局价格表片段（21 条，全部入表满足 v2.0.1 全定价规则） |

## 安装（1.21.1 示例）

```bash
cp compat-packs/kaleidoscope-tavern-pack/kt_crate.json compat-packs/kaleidoscope-tavern-pack/kt_terminal.json config/csbox/
python scripts/merge-prices.py compat-packs/kaleidoscope-tavern-pack/_prices.json config/csbox/_prices.json
```

- 箱子：进游戏 `/csbox reload`，然后 `/csbox give @p csgobox:kt_crate`。
- 终端机：**新增配置文件后需重启游戏**（动态物品在注册阶段创建），然后
  `/csbox give @p csgobox:kt_terminal`。

## 预览说明

唯一 `elements` 几何物品是酒桶（grade5），走默认 baked-model 真 3D；吧台/酒柜/沙发/高脚凳等家具为方块父链物品，3D 预览按方块模型呈现；其余鸡尾酒/原料为平面贴图，预览与原版物品一致。
