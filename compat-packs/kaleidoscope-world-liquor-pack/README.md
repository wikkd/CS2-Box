# 世界名酒（kaleidoscope_world_liquor）联动数据包

森罗物语系列附属模组的箱子与终端机配置示例。物品 id 以发布 jar 的
`assets/*/models/item` × `zh_cn` lang 键交叉实测为准（1.21.1 NeoForge 与
1.20.1 Forge 双线共有物品；单线特供不入包）。

> 「森罗物语：酒馆」附属（装载器强制）。 本配置带 `"requires": ["kaleidoscope_world_liquor"]` 门控：模组未装时整箱
> 跳过并在 `/csbox info error` 报错。

另附 NeoForge 26.1.2 官方构建（1.1.6），`v26_1_2` / `v26_2` 平台装该 jar 后同包可用。
## 文件

| 文件 | 内容 |
|---|---|
| `kwl_crate.json` | 世界名酒军火箱（csbox）：5 档池见下 |
| `kwl_terminal.json` | 世界名酒终端（terminal）：同一物品池的终端报价 |
| `_prices.json` | 全局价格表片段（21 条，全部入表满足 v2.0.1 全定价规则） |

## 安装（1.21.1 示例）

```bash
cp compat-packs/kaleidoscope-world-liquor-pack/kwl_crate.json compat-packs/kaleidoscope-world-liquor-pack/kwl_terminal.json config/csbox/
python scripts/merge-prices.py compat-packs/kaleidoscope-world-liquor-pack/_prices.json config/csbox/_prices.json
```

- 箱子：进游戏 `/csbox reload`，然后 `/csbox give @p csgobox:kwl_crate`。
- 终端机：**新增配置文件后需重启游戏**（动态物品在注册阶段创建），然后
  `/csbox give @p csgobox:kwl_terminal`。

## 预览说明

全平面贴图（名酒瓶/鸡尾酒/家具），预览与原版物品一致。
