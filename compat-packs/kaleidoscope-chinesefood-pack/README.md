# 国味（kaleidoscope_chinesefood）联动数据包

森罗物语系列附属模组的箱子与终端机配置示例。物品 id 以发布 jar 的
`assets/*/models/item` × `zh_cn` lang 键交叉实测为准（1.21.1 NeoForge 与
1.20.1 Forge 双线共有物品；单线特供不入包）。

> 「森罗物语：厨房」附属（装载器强制）。 本配置带 `"requires": ["kaleidoscope_chinesefood"]` 门控：模组未装时整箱
> 跳过并在 `/csbox info error` 报错。

## 文件

| 文件 | 内容 |
|---|---|
| `kcf_crate.json` | 国味军火箱（csbox）：5 档池见下 |
| `kcf_terminal.json` | 国味终端（terminal）：同一物品池的终端报价 |
| `_prices.json` | 全局价格表片段（21 条，全部入表满足 v2.0.1 全定价规则） |

## 安装（1.21.1 示例）

```bash
cp compat-packs/kaleidoscope-chinesefood-pack/kcf_crate.json compat-packs/kaleidoscope-chinesefood-pack/kcf_terminal.json config/csbox/
python scripts/merge-prices.py compat-packs/kaleidoscope-chinesefood-pack/_prices.json config/csbox/_prices.json
```

- 箱子：进游戏 `/csbox reload`，然后 `/csbox give @p csgobox:kcf_crate`。
- 终端机：**新增配置文件后需重启游戏**（动态物品在注册阶段创建），然后
  `/csbox give @p csgobox:kcf_terminal`。

## 预览说明

稻草床（grade5）为 `elements` 真 3D；黄花鱼刷怪蛋（grade3）走刷怪蛋实体预览策略（真鱼模型）；其余菜肴为平面贴图，预览与原版物品一致。
