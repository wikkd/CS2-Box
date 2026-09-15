# 武库商小屋进模组群系

让 CS2-Box 的世界生成结构「武库商小屋」在**其它模组的群系 / 村庄**里出现。纯数据驱动，
**不需要改任何 Java 代码**。

> 机制前提：structure 的 `"biomes"` 指向 biome tag；原版 biome tag 跨数据包**默认合并追加**
> （只有 `"replace": true` 才整体覆盖）。所以「让某结构在哪些群系生成」本质是「往对应
> biome tag 追加条目」。

## 两条生成通道

| 通道 | 由什么控制 | 示例 |
|---|---|---|
| 野外据点 | `#csgobox:has_structure/arms_dealer_hut` | 沙漠里孤零零一座小屋 |
| 村庄版 | `#minecraft:has_structure/village_<biome>` | 模组群系里的原版村庄附带小屋（weight 6） |

村庄版的小屋之所以「免费」：CS2-Box 已经把 `csgobox:arms_dealer_hut_<biome>` 挂进了原版
5 个村庄的 houses 池（`village/{plains,desert,savanna,snowy,taiga}/houses.json`，weight 6）。
某群系能生成哪类村庄完全由 `#minecraft:has_structure/village_*` 决定，所以把模组群系加进
对应 village tag，小屋就跟着村庄一起出现。

## 方法一：让野外据点进模组群系（最简）

数据包里放 `data/csgobox/tags/worldgen/biome/has_structure/arms_dealer_hut.json`：

```json
{
  "values": [
    "mymod:my_biome",
    "#mymod:is_rare"
  ]
}
```

不写 `"replace": true` 即追加，与仓库自带白名单合并。structure / structure_set 都不用动。

## 方法二：让村庄版小屋进模组群系

数据包里放 `data/minecraft/tags/worldgen/biome/has_structure/village_plains.json`：

```json
{
  "values": [
    "mymod:my_plains_biome"
  ]
}
```

（`village_desert` / `village_savanna` / `village_snowy` / `village_taiga` 同理。）

## 方法三：自建村庄类型（进阶）

- 覆盖 `data/minecraft/worldgen/template_pool/village/<biome>/houses.json`：**整文件复制 CS2-Box
  的对应 houses.json 再追加**（这是「整文件覆盖」语义，多数据包同改会按顺序后者胜，务必以
  CS2-Box 的 houses.json 为基底）。
- 或自建 structure_set + start_pool 直接引用 `csgobox:arms_dealer_hut/start_pool`。

## 辅助脚本 `scripts/add-biome.py`

仓库提供脚本来做方法一/二的 tag 追加（去重、幂等）：

```bash
# 野外据点加两个群系（dry-run 预演，不写盘）
python3 scripts/add-biome.py --biome mymod:my_biome --biome '#mymod:is_rare' --dry-run
# 村庄版：target 指向原版 village tag
python3 scripts/add-biome.py --tag data/minecraft/tags/worldgen/biome/has_structure/village_plains.json \
  --biome mymod:my_biome
# 整体覆盖（慎用：会丢弃其它数据包的追加）
python3 scripts/add-biome.py --biome mymod:my_biome --replace
```

脚本参数见 `python3 scripts/add-biome.py -h`。

## 与 TerraBlender 的关系

TerraBlender 是「加群系」的库（Region + 参数点把模组群系注入 MultiNoise 参数空间），它**只改
群系分布**，不替代 biome tag 机制。TerraBlender 群系要让小屋生成，做法与上述完全一致：把 biome
id 加进 `#csgobox:has_structure/arms_dealer_hut`（或对应 `village_*` tag）。

## 风险与注意

- **间距**：野外据点 structure_set 为 spacing 30 / separation 10，与原版村庄（34 / 8）相互独立；
  同一群系可能两者都刷。想拉开间距可覆盖 `csgobox` 的 structure_set 调大 spacing。
- **群系气质**：把雪原类模组群系挂进 `village_plains` 会建筑违和——按群系气质选对应
  `village_*` tag。
- **冲突面**：biome tag 是合并语义最安全；`houses.json` / `structure_set` 是整文件覆盖最危险，
  文档与示例均引导包作者以 CS2-Box 文件为基底。
- **26.x 差异**：1.20.1 与 26.x 的 structure / tag 数据格式一致（已用原版数据核对），但
  pack_format 80/81 下的格式细节若有变化以真实环境回归为准。

## 现成示例

`docs/examples/biome-datapack/` 是一个可用的示例 datapack（虚构模组 `mythicmod` 演示，
含方法一与方法二两个文件 + README）。
