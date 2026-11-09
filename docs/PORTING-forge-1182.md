# Forge 1.18.2 移植说明（forge_1_18_2）

2026-10-24 首建并完成编译绿：`:forge_1_18_2:build` SUCCESSFUL、PlatformSmokeTest
3/3 通过、产物 `csgobox-forge-1.18.2-2.0.3.jar` 与 SRG 发布件
`csgobox-forge-1.18.2-2.0.3-srg.jar`（`renameJar`，发布必须走该产物，与
forge_1_20_1 同纪律）。构建命令：JAVA_HOME 用 ms-21.0.9，
`-Pactive_versions=forge-1.18.2 --no-build-cache`。
基线：`forge_1_20_1`（同为 SimpleChannel/NBT/LazyOptional/ForgeConfigSpec 线）。
机械移植脚本 `scripts/port-forge-1182.py`（89 文件，16 排除手工）。

## 已完成

- 骨架：settings.gradle 注册 `forge-1.18.2`、gradle.properties 版本块、
  build.gradle（FG7 + renamer SRG 产物，与 forge_1_20_1 同构）、mods.toml、pack.mcmeta。
- FG7 mavenizer 对 1.18.2 可用（Forge 40.3.0 补丁链已下载成功）。
- 机械层 89 文件（包名 + GuiGraphics→PoseStack 类型/参数重命名 + 直调点变换 +
  BuiltInRegistries→ForgeRegistries）。
- 手工：AnimRenderOps（PoseStack 版 13 原语，无 TACZ 分支）、ModItems（1.18.2
  CreativeModeTab 非 registry，改为 `new CreativeModeTab("csgobox")` 子类 +
  fillItemList）、CsgoBox 入口补丁（Stats.makeCustomStat 替代 CUSTOM_STAT registry、
  剔除 top/create 守卫调用）。

## 本机网络降级（待恢复后一行回启）

| 依赖 | 状态 | 恢复方式 |
|---|---|---|
| Jade 5.3.2（1.18.2-forge） | ✅ 已接入（2026-10-24，经 cursemaven） | cdn.modrinth.com 不可达，改走 CurseForge 文件 id 5079263（`curse.maven:jade-324717:5079263`）。注意 1.18.2 的 Jade 5.x API 仍是 WAILA 血统的 **mcp.mobius.waila.api** 包（非 snownee.jade.api）：IWailaPlugin 有 register/registerClient；块/实体提供者统一走 `registerComponentProvider(provider, TooltipPosition, Class)`；提供者无 getUid；ITooltip.add(Component) 可用 |
| REI 8.4.737 | ✅ 已移植（2026-10-24） | rei/ 三文件经 REI 8 API 考古移植：`DynamicDisplayGenerator`/`DisplayCategory` 与 REI 12 同形；差异仅两点——`Widgets.createDrawableWidget` 收 `DrawableConsumer.render(GuiComponent, PoseStack, mouseX, mouseY, delta)`（5 参 lambda，REI 12 为 4 参 Drawable），概率文本列走 PoseStack |
| TACZ 1.1.4-hotfix（1.18.2 官方构建存在） | ✅ **适配完成**（2026-10-24）。compileOnly 走 **API stub jar**（`com.tacz:tacz:1.1.8-api-1182`，local-repo 内手写编译期签名桩，JDK 17 编译——从 1.18.2 分支源码核实签名一致；运行时由 TACZ 1.1.4 的真实类替换）。完整 TACZ 路径已恢复：BoxItemCodec.validateTacz（IGun instanceof + TimelessAPI 索引检查）、build.gradle 依赖已取消注释（TaczList 收集器与 `/csbox tacz` 子树曾一并恢复，2.0.4 命令树简化时移除）。**compileOnly 桩不含方法体**——运行时 TACZ 必须在场（与其它平台 TACZ 可选依赖一致的门控设计）| `scripts/download-tacz-1182.sh`（已备好坐标与脚本）成功后：恢复 `compat/TaczInspectViewportImpl`（从 forge_1_20_1 经 port 脚本重灌）、还原 BoxItemCodec.validateTacz（对照 fabric_1_21_1 的同源实现）、取消 build.gradle 中 TACZ dep 注释 |
| Jade 5.3.2 | CDN 不可达 | jade/ 包从 forge_1_20_1 经 port 脚本重灌 + 取消 dep 注释 + mods.toml 已保留 [5.0,) 条目 |
| EMI / WTHIT / TOP | 上游无 1.18.2 构建（已核实） | 永久降级，Jade 覆盖悬停 |

## 编译错误修复进度（2026-10-24 会话）

已修复（七批）：Component 工厂（28 文件 `new TextComponent/TranslatableComponent`）、
`sendSuccess/failure(() -> ...)` lambda 移除（1.18.2 直收 Component）、
`EntityPredicate.And→Composite`（4 触发器）、`Stats.CUSTOM.get(id, formatter)`、
`getActiveContainer`、`MissingTextureAtlasSprite.getLocation`、ModelBakeEvent 重写、
MenuType 单参、`Properties.of(Material.METAL)`、MerchantOffer 双 ItemStack、
`ForgeRegistries.BLOCK_ENTITIES`、loot API（`getLootTables().get` + LootContext）、
DFU `error(String)`、`RenderGameOverlayEvent`、Cloth 走 `ExtensionPoint.CONFIGFACTORY`、
`PlayerInteractEvent/LivingDeathEvent` 的 Entity 强转、`sendMessage`、ScreenBlurBoost
转 no-op（1.18.2 无菜单模糊）、回收站实体剥离 capability（走 WorldlyContainer）、
`ForgeRegistries.*.get→getValue`。

当前余量（每轮编译暴露下一层，~101 个中多数为连带性错误，按文件修）：
1. `block/entity/ArmoryRecyclerBlockEntity.java` — capability 剥离的正则误伤，**从
   forge_1_20_1 重灌后手工剥**（删 RecyclerHandler/getCapability/recyclerCap 三段 +
   4 个 import，1.18.2 无 ForgeCapabilities）；
2. `item/ModItems.java` — RegistryObject/DeferredRegister import 被早期补丁误删，
   补回两个 import 即可；`ModVillagers` 同类检查；
3. `command/CsboxCommand.java:384+` 的 `Component 不是函数接口` — 还有多行/块形
   lambda 形态的 sendSuccess（读行后定点移除 `() -> `）；
4. `block.entity不存在` 编译错误 — 上面的正则误伤殃及 import，重灌即消；
5. `AnimRenderOps.renderBlurredBackground` 签名不匹配 + 屏幕内 `pose` 变量重名
   （CsboxProgressScreen/TerminalConfirmDialog 的 `PoseStack pose` 参数与本地变量
   冲突，改名即可）；
6. `BoxItemCodec:11/456` 残留 BuiltInRegistries import（grep 清零）；
7. EditorCommand:53 / ModBlocks:35/37 / ArmoryRecyclerBlock:70/81 / 各 gui 找不到符号
   — 逐个看符号名，多为 1.19+/1.20+ API 回退（同已修模式）。

历史首轮清单（供参考）：

1. **`org.joml` 不存在**（1.18.2 用 Mojang 自带 math 类）：AnimRenderOps 与其它
   joml 触点 → `com.mojang.math.Matrix4f/Vector3f/Quaternion/Vector4f`；
   `Quaternionf.rotationXYZ(...)` → `Quaternion.fromXYZ(...)`（弧度）；
   `p.transformPosition(m)` → `p.transform(m)`（Mojang Vector3f 有 transform(Matrix4f)）。
2. **`net.minecraft.world.flag` 不存在**（FeatureFlags 1.20+）：ModMenus 的
   `new MenuType<>(factory, FeatureFlags.VANILLA_SET)` → `new MenuType<>(factory)`。
3. **ModItems 创造标签**：1.18.2 CreativeModeTab 无 `fillItemList`/`makeIcon`？——
   用 FG7 缓存的 1.18.2 映射 jar `javap net.minecraft.world.item.CreativeModeTab`
   核实可覆盖方法（候选：`makeIcon` ✓ 存在；内容填充在 Forge 1.18.2 走
   `Item#getCreativeTabs()` Forge 补丁或 fillItemList，以 javap 为准），
   并把 ModItems 的 `BuiltInRegistries.ITEM` 残留 → `ForgeRegistries.ITEMS`。
4. **MerchantOffer 形态**（ModVillagers:15/95）：无 ItemCost/Optional 重载 ——
   key2 双输入交易改 `new MerchantOffer(costA, costB, result, maxUses, xp, priceMult)`。
5. **BoxItemResolver:93 / ModLoadedTrigger:6 / AnimRenderOps:198** 等：编译错误明细
   会给出符号，多半是 1.19+/1.20+ API 回退（如 `Lighting`、`RandomSource`、
   `getQuads` 签名）。
6. 屏幕直调残余：`fill(pose,...)`/`drawString(pose,...)` 在非 Screen 类需
   `GuiComponent.` 限定；`pose.blit(` 需改 `RenderSystem.setShaderTexture` + 
   GuiComponent.blit（仅 2 处）。
7. REI 插件 3 文件（rei/ 排除中）：REI 8.x 的 render API 是 PoseStack 旧形态，
   对照 REI 8 `REIClientPlugin`/`IRecipeCategory` 签名重写后从 EXCLUDE 摘除。

## 构建命令

```
JAVA_HOME="C:/Users/FrostStarInquire/.jdks/ms-21.0.9" \
  ./gradlew :forge_1_18_2:compileJava -Pactive_versions=forge-1.18.2 --no-build-cache
```

发布产物必须走 `:forge_1_18_2:renameJar`（SRG 重映射，与 forge_1_20_1 同纪律）。

## IDEA 启动项（2026-10-24）

仓库共享启动项已加入 `.idea/runConfigurations/`（IDEA 自动识别，随 git 提交）：

- **MC Fabric 1.21.1 - Client / Server** → `:fabric_1_21_1:runClient|runServer`
- **MC Forge 1.18.2 - Client / Server** → `:forge_1_18_2:runClient|runServer`

每个启动项自带 `-Pactive_versions=<版本>` 脚本参数——IDEA 保持默认 sync（1.21.1）
即可直接启动：运行时是独立的 Gradle 调用（daemon 已关，单版本配置），完全符合
「单次调用单版本」纪律。Gradle JVM 用项目默认 21 即可（已实测 Loom 1.17.21
在 JVM 21 上配置/编译正常；JVM-25 要求只属 Loom 1.18+ 线）。fabric 首次
runClient/runServer 需联网下载 assets/服务端分发包。

## 2026-10-24 补充（创造栏标题 / #tag 自愈）

1. **创造模式物品栏标题显示原始键名**：1.18.2 的 `CreativeModeTab(String)`
   构造器把标题键派生为 `itemGroup.<name>`（即 `itemGroup.csgobox`），而共享
   语言文件与其它所有平台用的键是 `itemGroup.csgobox.cs_tab`。已在
   `ModItems.EQUIPMENT_TAB` 匿名子类覆写 `getDisplayName()` 对齐共享键。
2. **`#tag` 条目加载时机（与 fabric 同源）**：`BoxJsonLoader` 解析缓存会把
   带错误的解析粘住（早期 commonSetup 扫描时物品标签未绑定，`#tag` 展开
   失败被缓存，ServerStarting 重扫永远复用失败结果）。修复同 fabric：带
   LoadError 的解析不再缓存复用；`applyLegacyTag` 加 `#` 前缀守卫消除标签
   成员伪 NBT 警告。

验证记录（专用服务器到 Done 4.363s + `csbox info csgobox:nbt_crate`）：
consumer 档 `items=18`（火药+面包+16 色 wool 全部展开）、grade4 的
「1.18 NBT 测试剑」带自定义名（Unbreakable + display.Name SNBT 生效）、
强治疗药水 / 迟缓药箭（`{Potion: ...}` NBT）、`Validation: 2 file(s) OK,
0 file(s) failed`；ServerStarting 重扫后 `server started with 2 box
definitions`，无 tag/NBT 错误。

## 已知限制：武库商小屋不生成（2026-10-25 确认并清理）

1.18.2 **没有数据包驱动的结构放置**：原版 jar 中不存在 `worldgen/structure_set` /
`worldgen/configured_structure_feature` 数据注册表（后者只有 locate 用的 tags），
结构的 spacing/separation/salt 是纯代码注册（`Registry.STRUCTURE_FEATURE`），
`worldgen/template_pool` 也不可数据包注入（村庄池同理，村庄版小屋无数据包路径）。
首建时机械移植遗留的死资源文件已删除（`worldgen/configured_structure_feature/arms_dealer_hut.json`、
`tags/worldgen/biome/has_structure/arms_dealer_hut.json`、`structures/*.nbt` 共 8 个）——
那个 JSON 是不匹配任何真实版本 codec 的杂交格式，且引用了不存在的 start_pool，从未生效。

**2026-11-07 补刀（processResources 排除共享泄漏）**：上面删的只是本模块的本地副本，
common 的共享 1.19+ 版式 worldgen 资源（`worldgen/structure_set`、`worldgen/template_pool`、
`tags/worldgen/biome`、`data/minecraft/worldgen/template_pool/village/*` 村庄池覆盖）经
共享资源 srcDir 仍会打进 1.18.2 jar——structure_set 引用 `csgobox:arms_dealer_hut` 使
`worldgen/configured_structure_feature` 注册表出现 **Unbound value**；GameTest 服务器的
datapack 加载在 `RegistryAccess.freeze` 直接拒绝（普通 boot 的同一次加载早前已被村庄池
覆盖文件的解析错误打断而"侥幸"通过）。已在 `forge_1_18_2/build.gradle` 的
processResources 排除这四组死资源（`gameTestServer` 6/6 全绿，普通 boot 的注册表
解析错误随之消失）。

**玩家获取军火商的方式不受影响**：合成军火拆解台（**本平台复数副本 `data/csgobox/recipes/armory_recycler.json`**
——2026-11-06 修复前这里引用的共享单数 `recipe/` 目录在 1.18.2 不被读取、配方实际全部失效，
同批补齐 `advancements/` 13 份并接线 `TerminalDealTrigger`/`TerminalBrokeTrigger` 注册与触发）
放置后，原版村民经 `minecraft:acquirable_job_site` 标签（含 `csgobox:armory_recycler`，1.18.2 生效）
转职军火商，动态定价与全部交易照常。若未来要真正补上小屋生成：需代码注册 configured
structure feature + `StructureFeature` 放置 + start_pool（1.18.2 的 template pool 同为代码
注册，无数据包路径）；共享 NBT 不携带 DataVersion、方块按名称解析（需逐变体核对方块 id
在 1.18.2 均存在），内嵌村民的职业字段是 1.14 起的字符串格式，天然兼容。
