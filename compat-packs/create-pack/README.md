# 机械动力（Create）蓝图预览数据包

演示「装了 Create 就有它的货」的箱子与终端机配置，核心条目是
`create:schematic` 蓝图的结构预览。物品 id 以
`create-1.21.1-6.0.10` 发布 jar 实测为准。

> **平台适配矩阵**：Create 官方支持 NeoForge 1.21.1（对应 `v1_21_1`，
> 6.x）与 Forge 1.20.1（对应 `forge_1_20_1`，1.20.1 线最新 6.0.8，同为
> 6.x 文件线——但 1.20.1 无 DataComponents，蓝图文件名的 NBT tag 键待
> 考古，移植时该包条目写法随之调整）；1.18.2 仅有 0.5.1.i 旧线，蓝图
> 预览不做。本配置带 `"requires": ["create"]` 门控。

## 文件

| 文件 | 内容 |
|---|---|
| `schematic_crate.json` | 蓝图军火箱（csbox）：grade5 两个蓝图——`preview_test`（要求 `schematics/preview_test.nbt` 存在，渲染真实建筑 3D）与 `no_such_file`（文件缺失，验证哨兵缓存降级平面图标不炸）；grade1-4 Create 机械材料 |
| `schematic_terminal.json` | 蓝图终端（terminal）：同一物品池（grade5 仅保留 `preview_test` 蓝图） |
| `_prices.json` | 全局价格表片段：Create 材料平价；`create:schematic` 按基础 id 平价（蓝图文件名变体不区分价格） |

## 安装（1.21.1 示例）

```bash
cp compat-packs/create-pack/schematic_crate.json compat-packs/create-pack/schematic_terminal.json config/csbox/
python scripts/merge-prices.py compat-packs/create-pack/_prices.json config/csbox/_prices.json
```

- 箱子：进游戏 `/csbox reload`，然后 `/csbox give @p csgobox:schematic_crate`。
- 终端机：**新增配置文件后需重启游戏**（动态物品在注册阶段创建，
  `/csbox reload` 只刷新已有物品的配置数据），然后
  `/csbox give @p csgobox:schematic_terminal`，手持右键打开终端。

## 蓝图 3D 预览说明

`create:schematic` 物品只带文件名组件（`create:schematic_file`），结构存在
本地 `schematics/<名字>.nbt`。**组件里的文件名必须带 `.nbt` 后缀**——Create
的 `loadSchematic` 有硬守卫（owner/file 任一为空或后缀不是 `.nbt` 直接返回
空模板），缺后缀的蓝图会静默降级平面图标。CS2-Box 自 2.0.4 起在 `v1_21_1` 的
`ItemPreviewStrategies` 内置蓝图策略：反射 Create 自己的
`SchematicItem.loadSchematic` 读文件（零 Create 编译期引用），按 RenderType
一次性顶点捕获 + 每帧重放；**方块数超过 32768 的蓝图降级平面图标**
（2026-11-14 预算，捕获一次性、每帧只是顶点拷贝），文件缺失/空结构同样
哨兵缓存降级（各有一次性日志）。测试前用蓝图笔框选一个 32768 方块以内的
建筑保存为 `preview_test`（组件写 `preview_test.nbt`）；若开过缺失哨兵条目
需重启客户端。
