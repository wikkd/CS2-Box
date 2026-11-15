# 预览管线测试数据包

CS2-Box 预览适配的专项测试箱合集（2026-11-14）。全部 `drop: 1.0`（每开必出）、
五档等权重，一箱覆盖一条预览管线分支。物品 id 全部经 dev 环境发布 jar
`models/item` 清单实测（create-6.0.10 / kaleidoscopecookery-1.6.0 /
modulargolems-3.1.44 / immersive_aircraft-1.7.0）。

| 文件 | 测试目标 | requires |
|---|---|---|
| `preview_g1_entities.json` | 同 id 实体约定（G1）：物品平面贴图 + 实体注册表同名 → 实体 3D 预览。末影水晶/展示框/画/盔甲架/船/运输矿车均为离线碰撞分析命中的 vanilla 案例 | （无） |
| `preview_create_packages.json` | 默认路径 3D：Create 包裹是纯 Item + 手作 Blockbench 元素模型（parent `block/block`），零适配即 3D；打包机/青蛙港为方块物品 | `create` |
| `preview_kc_food.json` | 方块背书平面回退（G0）：森罗物语餐盘食物放置后才有 3D 形态；稻草人为 G1 主目标（平面贴图 + `kaleidoscope_cookery:scarecrow` 实体同名） | `kaleidoscope_cookery` |
| `preview_mg_golems.json` | BEWLR 物品 FIXED 重路由：三个 `*_golem_holder` 是 `builtin/entity`（模组自带 BEWLR 画傀儡）；部件/武器/配置卡同走 BEWLR | `modulargolems` |
| `preview_ia_vehicles.json` | G1 模组案例：沉浸式载具物品全是生成式平面图标（无物品 renderer），实体渲染走模组自研 bbmodel | `immersive_aircraft` |

## 安装（dev 环境）

```bash
for f in compat-packs/preview-test-pack/preview_*.json; do
  cp "$f" v1_21_1/runs/client/config/csbox/
  cp "$f" v1_21_1/runs/server/config/csbox/
done
python scripts/merge-prices.py compat-packs/preview-test-pack/_prices.json v1_21_1/runs/client/config/csbox/_prices.json
python scripts/merge-prices.py compat-packs/preview-test-pack/_prices.json v1_21_1/runs/server/config/csbox/_prices.json
```

进游戏 `/csbox reload` 后 `/csbox give @p <箱子 id>`（箱子文件名即 id）。
`_prices.json` 里既有键优先（KC 部分餐盘价沿用 kaleidoscope-cookery-pack 的定价）。

## 测试时注意

- 带 `requires` 的箱子在对应模组未装时整箱禁用（`/csbox validate` 可核对）。
- G1 箱首次检视会打一次性 INFO 日志（`[csgobox] preview path: ...`）；若
  稻草人仍渲染为平面贴图，把该行日志与 `entity preview` 相关行发给维护者定位。
- 模组物品仅在对应模组加载时可通过校验；vanilla 箱无需任何模组。
