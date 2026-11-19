# Prefab（预制建筑）联动数据包

演示「装了 Prefab 就有它的蓝图货」的箱子与终端机配置。物品 id 以
`prefab-neoforge-1.0.9` / `prefab-1.10.0.2` 发布 jar 的
`assets/prefab/models/item` 清单实测为准。

> **平台适配矩阵**：Prefab 官方支持 NeoForge 1.21.1（对应 `v1_21_1`，
> 1.0.9 线）与 Forge 1.20.1（对应 `forge_1_20_1`，1.10.0.2 线）；无 Fabric 与
> 26.x 构建，其余平台不适配。本配置带 `"requires": ["prefab"]` 门控：未装时
> 整箱跳过并在 `/csbox info error` 报错。

## 文件

| 文件 | 内容 |
|---|---|
| `prefab_crate.json` | Prefab 蓝图军火箱（csbox）：grade5 地标级蓝图（魔法寺庙 / 酒吧 / 风车 / 改进水下基地 / 市政厅 / 下界合金迅捷军刀），grade4 大型建筑与黑曜石军刀，grade3 高级住宅 / 高级农场 / 机械塔 / 矿坑入口 / 压缩箱子，grade2 改进住宅 / 农场 / 防御掩体 / 仓库 / 钻石镰刀，grade1 基础房子 / 草地 / 便捷桥 / 升级 / 镰刀四档 / 压缩方块 |
| `prefab_terminal.json` | Prefab 蓝图终端（terminal）：同一物品池的终端报价，手持右键进入交易 |
| `_prices.json` | 全局价格表片段：40 个条目的定价，按建筑规模与工具档位分层 |

## 安装（1.21.1 示例；1.20.1 路径换成 `run/config/csbox/`）

```bash
cp compat-packs/prefab-pack/prefab_crate.json compat-packs/prefab-pack/prefab_terminal.json config/csbox/
python scripts/merge-prices.py compat-packs/prefab-pack/_prices.json config/csbox/_prices.json
```

- 箱子：进游戏 `/csbox reload`，然后 `/csbox give @p csgobox:prefab_crate`。
- 终端机：**新增配置文件后需重启游戏**（动态物品在注册阶段创建，
  `/csbox reload` 只刷新已有物品的配置数据），然后 `/csbox give @p csgobox:prefab_terminal`，
  手持右键打开终端。
- 箱子内每个 id 都必须在 `_prices.json` 有价（装载器对未定价 id 报错、无默认价
  回退），本包片段已覆盖全部 40 个条目。

## 3D 预览说明

Prefab 的蓝图物品渲染为**整栋建筑的 3D 结构预览**（2026-11-18 适配，仅
`v1_21_1` 与 `forge_1_20_1`）：

- 结构数据直接从 Prefab jar 内的 `assets/prefab/structures/*.gz` 读取（gzip 压缩
  JSON），经 Prefab 自己的 `Structure.CreateInstance` + `BuildBlock.SetBlockState`
  管线解析，与模组自身的幽灵预览（ghost preview）同一条路径，零编译期依赖；
- 方块逐面烘焙 + 方块实体真渲染（箱子/告示牌等有内容），按结构包围盒自动居中 +
  自适应缩放，可拖拽旋转；
- 结构方块数超预算（32768，对齐 Create 政策）或结构缺失时降级为普通 3D 图标；
- 本包 40 个条目中 25 个有专属结构映射（见 `PrefabStructurePreview.BLUEPRINTS`）；
  其余条目（升级、压缩箱子、木箱、镰刀/军刀、压缩方块）走默认预览路径——
  普通物品的 baked 模型 3D 或方块物品的方块模型，观感与模组物品栏一致；
- 2D 小格子（箱子总览/滚动条）一律平面图标，不触发结构烘焙。

## 测试要点

- 开箱/终端预览大图：蓝图物品显示整栋建筑，拖拽旋转应左右=水平自转、
  上下=俯仰（与检视屏手感一致）；
- 大地标（`item_aqua_base_improved` 约 12.7k 方块）与大建筑逐帧烘焙的性能；
- 方块实体（箱子/床/告示牌）在结构预览内是否有内容、光照是否可辨；
- 降级路径：`item_upgrade` 等无结构映射条目应显示普通 3D 图标而非空预览。
