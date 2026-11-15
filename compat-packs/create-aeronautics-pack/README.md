# 创造航空（Create: Aeronautics）联动数据包

演示「装了 Create: Aeronautics 就有它的货」的箱子与终端机配置。物品 id 以
`create-aeronautics-bundled-1.21.1-1.3.2`（内层
`aeronautics-neoforge-1.21.1-1.3.2`）发布 jar 实测为准，命名空间为
`aeronautics`。运行时依赖 Create 6.x（jarjar bundled 已带
offroad / simulated 物理库）。

> **平台适配矩阵**：仅 NeoForge 1.21.1（对应 `v1_21_1`）；无其他 MC 版本或
> loader 构建已确认，其余平台不适配。本配置带 `"requires": ["aeronautics"]`
> 门控：未装时整箱跳过并在 `/csbox info error` 报错（Create 6.x 在场但缺
> aeronautics 时同样跳过）。

## 文件

| 文件 | 内容 |
|---|---|
| `aero_crate.json` | 创造航空军火箱（csbox）：grade5 珠光浮空石 / 陀螺轴承 / 唱片，grade4 智能螺旋桨与土豆炮，grade1-3 燃烧器 / 护目镜 / 信封等杂项 |
| `aero_terminal.json` | 创造航空终端（terminal）：同一物品池的终端报价，手持右键进入交易 |
| `_prices.json` | 全局价格表片段：航空材料与机械零件平价 |

## 安装（1.21.1 示例）

```bash
cp compat-packs/create-aeronautics-pack/aero_crate.json compat-packs/create-aeronautics-pack/aero_terminal.json config/csbox/
python scripts/merge-prices.py compat-packs/create-aeronautics-pack/_prices.json config/csbox/_prices.json
```

- 箱子：进游戏 `/csbox reload`，然后 `/csbox give @p csgobox:aero_crate`。
- 终端机：**新增配置文件后需重启游戏**（动态物品在注册阶段创建，
  `/csbox reload` 只刷新已有物品的配置数据），然后 `/csbox give @p csgobox:aero_terminal`，
  手持右键打开终端。

## 3D 预览说明

Aeronautics 的物品全部是普通 baked model（考古实证，无 geckolib/自定义
渲染器），走默认路径即得正确 3D/2D 表现，**零适配代码**；其「物理飞行器」
本体是 Create contraption 实体而非物品，不在箱子预览范围内（Create 蓝图
条目可预览的只是蓝图 .nbt 静态结构，见 `create-pack`）。
