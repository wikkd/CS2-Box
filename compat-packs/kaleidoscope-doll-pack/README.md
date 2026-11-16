# 森罗玩偶（kaleidoscope_doll）联动数据包

森罗物语系列附属模组的箱子与终端机配置示例。物品 id 以发布 jar 的
`assets/*/models/item` × `zh_cn` lang 键交叉实测为准（1.21.1 NeoForge 与
1.20.1 Forge 双线共有物品；单线特供不入包）。

> 独立模组（无前置）。 本配置带 `"requires": ["kaleidoscope_doll"]` 门控：模组未装时整箱
> 跳过并在 `/csbox info error` 报错。

另有 Fabric 双线（1.0.9）与 NeoForge 1.21.4 线。
## 文件

| 文件 | 内容 |
|---|---|
| `kd_crate.json` | 森罗玩偶军火箱（csbox）：5 档池见下 |
| `kd_terminal.json` | 森罗玩偶终端（terminal）：同一物品池的终端报价 |
| `_prices.json` | 全局价格表片段（8 条，全部入表满足 v2.0.1 全定价规则） |

## 安装（1.21.1 示例）

```bash
cp compat-packs/kaleidoscope-doll-pack/kd_crate.json compat-packs/kaleidoscope-doll-pack/kd_terminal.json config/csbox/
python scripts/merge-prices.py compat-packs/kaleidoscope-doll-pack/_prices.json config/csbox/_prices.json
```

- 箱子：进游戏 `/csbox reload`，然后 `/csbox give @p csgobox:kd_crate`。
- 终端机：**新增配置文件后需重启游戏**（动态物品在注册阶段创建），然后
  `/csbox give @p csgobox:kd_terminal`。

## 预览说明

8 个物品中 5 个带 `elements` 几何（玩偶抽奖机/三色礼品盲盒/指指点点）集中在高 grade 走真 3D；`doll_entity_item`（玩偶）为 `builtin/entity` 物品，检视屏走 BEWLR 重路由。
