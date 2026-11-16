# 血酒（kaleidoscope_bloodwine）联动数据包

森罗物语系列附属模组的箱子与终端机配置示例。物品 id 以发布 jar 的
`assets/*/models/item` × `zh_cn` lang 键交叉实测为准（1.21.1 NeoForge 与
1.20.1 Forge 双线共有物品；单线特供不入包）。

> 「森罗物语：酒馆」附属（装载器强制）；可与 Vampirism（吸血鬼）联动。 本配置带 `"requires": ["kaleidoscope_bloodwine"]` 门控：模组未装时整箱
> 跳过并在 `/csbox info error` 报错。

## 文件

| 文件 | 内容 |
|---|---|
| `kbw_crate.json` | 血酒军火箱（csbox）：5 档池见下 |
| `kbw_terminal.json` | 血酒终端（terminal）：同一物品池的终端报价 |
| `_prices.json` | 全局价格表片段（12 条，全部入表满足 v2.0.1 全定价规则） |

## 安装（1.21.1 示例）

```bash
cp compat-packs/kaleidoscope-bloodwine-pack/kbw_crate.json compat-packs/kaleidoscope-bloodwine-pack/kbw_terminal.json config/csbox/
python scripts/merge-prices.py compat-packs/kaleidoscope-bloodwine-pack/_prices.json config/csbox/_prices.json
```

- 箱子：进游戏 `/csbox reload`，然后 `/csbox give @p csgobox:kbw_crate`。
- 终端机：**新增配置文件后需重启游戏**（动态物品在注册阶段创建），然后
  `/csbox give @p csgobox:kbw_terminal`。

## 预览说明

全平面贴图（血酒系列），预览与原版物品一致。
