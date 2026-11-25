# SlashBlade:Resharped（拔刀剑·重锻）联动数据包

拔刀剑现代分支（SlashBlade:Resharped）的箱子与终端机配置示例。物品 id 以
发布 jar（2.1.9-1.21.1 / 1.20.1-1.9.65）的 `assets/*/models/item` × `zh_cn`
lang 键交叉实测为准；24 把名刀从 `data/slashblade/slashblade/*.json` 定义
逐把抄录属性（攻击力/刀艺/渲染/附魔）。

> **平台适配矩阵**：`v1_21_1`（NeoForge 2.1.9 线）与 `forge_1_20_1`
> （Forge 1.9.65 线）。无 1.18.2 / 26.x / fabric 构建。本配置带
> `"requires": ["slashblade"]` 门控。

## 数据驱动的名刀（与 TACZ 同类）

拔刀剑的 24 把名刀**共用一个注册物品** `slashblade:slashblade`（BEWLR
`builtin/entity` 渲染），刀的身份存在物品数据里：

- **1.21.1（组件）**：`slashblade:blade_state` 组件（扁平结构），
  `translationKey` 字段决定是哪把刀（`item.slashblade.<id>`）。2.1.9 的
  codec 必需字段实测为 **11 个**（RCON 逐字段 bisect：9 个 core 字段 +
  `comboRoot` + `specialEffects`；带模型渲染的刀再加 texture/model/carryType/
  effectColor/effectColorInverse/adjust 6 个渲染字段——渲染字段成组出现）；
- **1.20.1（NBT）**：`tag.bladeState.translationKey`（能力序列化同名键）。

## 文件

| 文件 | 内容 |
|---|---|
| `sb_crate.json` | 1.21.1 组件形态军火箱：24 刀按 5 档 + 耀魂材料 |
| `sb_crate_1201.json` | 1.20.1 NBT 形态军火箱（同池，`tag` 写法） |
| `sb_terminal.json` | 终端机报价（1.21.1 形态） |
| `sb_terminal_1201.json` | 终端机报价（1.20.1 形态，crate 同池去 `key` 加 `"type": "terminal"`） |
| `_prices.json` | 价格表：24 刀全部用 **`slashblade:slashblade#<刀id>` 变体键** 单独定价 + 6 条材料价 |

## 变体定价（v2.0.4 新增通用钩子）

24 把刀共用物品 id，终端机无法按裸 id 区分价。价格表支持
`id#<variant>` 子键；变体 id 的提取在 TACZ `GunId`/`AmmoId` 之外新增通用
**`VariantId`** 标记（tag 字符串 / tag JSON 对象 / `minecraft:custom_data`
组件三处任一）。本包每个刀条目都带 `VariantId: slashblade:<name>`，
武库拆解台按同一变体键回收计价。

## 安装（1.21.1 示例）

```bash
cp compat-packs/slashblade-resharped-pack/sb_crate.json compat-packs/slashblade-resharped-pack/sb_terminal.json config/csbox/
python scripts/merge-prices.py compat-packs/slashblade-resharped-pack/_prices.json config/csbox/_prices.json
```

- 箱子：进游戏 `/csbox reload`，然后 `/csbox give @p csgobox:sb_crate`。
- 终端机：**新增配置文件后需重启游戏**，然后 `/csbox give @p csgobox:sb_terminal`。
- 1.20.1：`sb_crate_1201.json` → `config/csbox/sb_crate.json`、
  `sb_terminal_1201.json` → `config/csbox/sb_terminal.json`（**测试基准平台**，
  部署与 L4 验证先在 1.20.1 做）。

## 预览说明

刀物品是 `builtin/entity`（BEWLR 自渲染），但模组的 BEWLR 有个硬门：FIXED
透视下只有 `isFramed && getFrame() instanceof BladeStandEntity`（刀真的挂在
挂架实体上）才画真 3D OBJ 刀模，其余场景一律画平面图标（jar 字节码实证，
1.9.65 / 2.1.9 / 2.2.1 三版同形）。因此 3D 预览走**挂架形态**：客户端停放
一个 `slashblade:blade_stand_entity`（刀挂台）实体，经其两个 public 字段
（`currentTypeStack` = 刀、`currentType` = 架型物品）把刀挂上去，交给模组
自己的 `BladeStandEntityRenderer` 跑完整挂架渲染链。**只画刀、不画刀挂台
本体**：doRender 的第二个 renderItem 画的是 ItemFrame 自身物品（架子本体），
预览把它置空（`ItemFrame.setItem(ItemStack.EMPTY)`）命中其空栈早退（1.9.65 /
2.1.9 字节码实证）；刀的姿态只由 `currentType` 字段决定，不受影响。
零 SlashBlade 编译引用（原版注册表造实体 + 反射字段写 + vanilla API）。

**双姿态（2026-11-16）**：挂架渲染器的架型分支决定刀姿态——落地架
（`bladestand_1`，分支 XP(-90) 与共同尾 XP(90) 相消）= 刀横置（挂式），
墙挂架（`bladestand_1w`，分支跳过 XP(-90)）= 刀竖立（立式）。预览每次
**随机**采用一种姿态播放（每次打开展示随机重掷）。探针自动居中与 auto-fit
叠加其上；渲染失败/实体缺失降级平面图标；2D 小格保持平面图标。耀魂材料为
普通平面贴图物品，预览与原版一致。

**自家连段动作检视（2026-11-25，forge_1_20_1 先行）**：点**检视**按钮
（检视人物模型钮）三态循环——挂式/立式切换 → **播放刀的自家连段** → 停止。
连段形态下预览换成持刀的预览玩家（检视者自己的皮肤），刀按模组自己的连段
状态机挥出（起手与逐段后继都由模组自己的选择函数决定，原版刀为
`combo_a1 → a2 → a3 → …`），刀身动作由模组自己的玩家渲染层驱动，全身动作
经模组自己的动作事件叠加；两者都是模组原代码，预览只负责"点击"（写连段
状态 + 发动作事件，不碰会真打伤害的点击入口）。装 Player Animator 时人物
随动作全身摆动；未装时刀照常挥、人物保持站姿（与游戏内同装环境一致）。
`v1_21_1` 待镜像。

## 未覆盖

- 耀魂附属刀（`SlashBladeJapaneseAddonPack` 等附属）未考古。
- `bladeState` 里更多玩法字段（`proudSoul`/`killCount`/`refine` 竞技化
  成长值）统一 0 起步；`specialEffects`（刀纹特效）未配置。
- 1.20.1 的刀是能力（capability）而非组件，`tag.bladeState` 只带身份
  字段，其余字段由模组默认值补齐——若个别刀外观/属性不对，把对应定义
  字段抄进 `tag.bladeState` 即可。
- **1.20.1 dev（ForgeGradle 7）装载需先重映射**：发布 jar 是 SRG 成员名
  （157/340 类含 `m_XXXXX_` 引用 + 1 个 mixin refmap）——这是 1.20.1 Forge
  **生产**运行时的标准形态，不是上游 bug；FG7 dev 运行时用官方成员名且无
  `fg.deobf`，原始 jar 直接进 dev 必现 mixin `@Shadow ... not located` /
  `NoSuchMethodError`。解法与 TACZ 同款：`scripts/remap-1201.sh` 统一管线
  整 jar SRG→official 重映射（`TaczDeobf.java` 字节码 + refmap 重写，映射源
  为 FG7 缓存的 `official-1.20.1-obf.tsrg.gz`），产物
  `SlashBladeResharped-1.20.1-1.9.65-official.jar` 落 `forge_1_20_1/run/mods/`。
  2026-11-16 实测：dev server `Done (2.003s)`、15 箱全载（sb_crate/sb_terminal
  门控全开）、零 SRG 崩溃，RCON 探针 `tag.bladeState.translationKey` 解析
  `-Agito-`/`-Haze- SakuraFox` 真名成功。首版重映射漏掉 2 处
  `f_268485_`（`ItemDisplayParameters.holders()` 的 record 访问器——SRG
  给 record accessor 分配的是 **f_ 型 id 但语义是方法**，旧版工具的方法
  引用改写只认 `m_` 型）导致打开创造背包崩 `NoSuchMethodError`；工具补
  f_ 型方法引用改写后重跑即净（字节级残留全为 EnclosingMethod 元数据与
  LocalVariableTable，运行时不可达）。
- 1.9.65 上游 `en_us.json` 缺 `item.slashblade.koseki` 键（zh_cn 有
  「枯石大刀」）——英文环境该刀显示原始键，中文环境正常；上游翻译缺口，
  非本包问题。
