# Biome Datapack — 让武库商小屋在模组群系生成

这是一个**可用的示例 datapack**：把本目录放进 `世界/saves/<世界>/datapacks/`（或服务器
`world/datapacks/`），并执行 `/reload`，武库商小屋就会在下列「模组群系」生成。

> 本示例用虚构模组 `mythicmod` 演示。换成你整合包里的真实模组群系即可。
> 两条 tag 都是**追加语义**（不写 `"replace": true`），与原版/其它数据包内容合并，
> 互不覆盖。

## 文件

| 文件 | 作用 |
|---|---|
| `data/csgobox/tags/worldgen/biome/has_structure/arms_dealer_hut.json` | 让野外据点「武库商小屋」在 `mythicmod` 群系生成（含嵌套 tag `#mythicmod:is_rare`） |
| `data/minecraft/tags/worldgen/biome/has_structure/village_plains.json` | 让原版平原村庄（其 houses 池已含 `csgobox:arms_dealer_hut_plains`，weight 6）在 `mythicmod` 群系生成 |

## 更快的方式：用脚本

不想手写 JSON？仓库提供 `scripts/add-biome.py`：

```bash
# 给野外据点加两个群系（dry-run 预演）
python3 scripts/add-biome.py --biome mymod:my_biome --biome '#mymod:is_my' --dry-run
# 给村庄版小屋开模组群系（target 指向原版 village_plains 的 tag）
python3 scripts/add-biome.py --tag data/minecraft/tags/worldgen/biome/has_structure/village_plains.json \
  --biome mymod:my_biome
```

## 注意事项

- **野外据点 vs 村庄版**：野外据点走 `csgobox` 自己的 structure_set（spacing 30 / separation 10）；
  村庄版走原版村庄结构集（spacing 34 / separation 8）。两者独立，同一群系可能两个都刷——
  想拉开间距，可再覆盖 `csgobox` 的 structure_set 调大 spacing。
- **群系气质**：把雪原类模组群系挂进 `village_plains` 会让村庄建筑违和，按群系气质选对应的
  `village_<biome>` tag。
- **别动 structure JSON**：本方案完全不碰 `csgobox:arms_dealer_hut` 的 structure / structure_set，
  纯 tag 追加，升级模组不冲突。
- TerraBlender 群系同理：把 biome id 加进 tag 即可（TerraBlender 只改群系分布，不替代 tag 机制）。
