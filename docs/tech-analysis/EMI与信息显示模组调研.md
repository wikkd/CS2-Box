# EMI 与 Jade/WTHIT/TOP 生态调研（CS2-Box 集成前置研究）

> 调研时间：本环境 2026-09；版本数据来自 GitHub Releases / Modrinth API 实时查询。
> 26.x 为本项目虚构的未来 MC 版本，凡引用 26.x 构建均为「接口能查到、需自行在网页端核实」，未核实一律标注。
> **落地状态（2026-09-11）**：EMI（v1_21_1 / forge_1_20_1）与 Jade / WTHIT / TOP
> （平台覆盖见本文档 B.1 表）已接线实现；刷新机制采用「`reloadResourcePacks()` 触发 EMI
> 重跑」与「JEI 并存时由 JemiPlugin 桥接、不注册原生 EMI 分类」；Jade/WTHIT/TOP 共用
> 客户端 `BoxRegistry` + `BoxOdds` 薄适配器方案。26.x Forge 仅 WTHIT 有构建（已接入）。

## A. EMI（配方查看器）

### A.1 生态事实
- **EMI**（作者 EmilyPloszaj）是现代配方/物品查看器，API 纯客户端，独立于 JEI/REI。版本线 **1.1.x**，最新 **1.1.24+1.21.1**（2026-05-13，GitHub Releases 与 [Modrinth](https://modrinth.com/mod/emi) 一致）。
- **NeoForge 1.21.1 有官方构建**（`emi-1.1.24+1.21.1+neoforge.jar`）；1.20.1 有 forge 构建（`1.1.24+1.20.1+forge`）。**26.x：Modrinth 按 neoforge+26.1.2/26.2 查询返回空 → 无 26.x 构建，需自行核实**（勿假定有）。
- 依赖坐标（[EMI wiki](https://github.com/emilyploszaj/emi/wiki/Getting-Started-Guide)，Sleeping Town Maven）：`dev.emi:emi-neoforge:1.1.24+1.21.1:api`（精确坐标需核实）。

### A.2 API 形态（对照现有 JEI 集成）
- 入口：`EmiPlugin`；Forge/NeoForge 用 `@EmiEntrypoint` 注解扫描（Fabric 走 `fabric.mod.json` entrypoint）。核心回调 `EmiPlugin.register(EmiRegistry)`。
- 自定义分类：`new EmiRecipeCategory(Identifier, EmiRenderable icon, EmiRenderable simplified)`，icon 可直接 `EmiStack.of(箱子)`；再 `registry.addCategory(cat)` + `registry.addWorkstation(cat, EmiIngredient)`。
- 自定义配方：实现 `EmiRecipe`（`getCategory/getId/getInputs/getOutputs/getDisplayWidth/getDisplayHeight/addWidgets(WidgetHolder)`），可继承 `BasicEmiRecipe` 简化；概率文本用 `widgets.addText(...)` 或 `slotWidget.appendTooltip(...)`；**`slotWidget.recipeContext(this)` 是关键**：让物品可收藏、可被「来源箱」反查/定位（即 CS2-Box 的「掉落物反查来源箱」）。
- **与 JEI 的核心差异**：JEI 有运行时 `IRecipeManager.addRecipes/hideRecipes`（CS2-Box 现用 `BoxJeiSync` + `@JeiPlugin` 快照刷新）；**EMI 没有公共运行时增删配方 API**——所有配方在 `register()` 一次性注册，每次 EMI reload 会清空并重跑全部插件（内部 `EmiReloadManager` 干这事，属 `dev.emi.emi.runtime`，非公共 API）。EMI 内建 JEI 兼容层 `JemiPlugin`（[源码](https://github.com/emilyploszaj/emi/blob/1.21/xplat/src/main/java/dev/emi/emi/jemi/JemiPlugin.java)），会自动桥接已装 JEI 插件的分类——并存时需防重复分类。
- **现成「概率/掉落表 → EMI 分类」案例**（均已开源，直接可抄）：
  - **[EMI Loot](https://modrinth.com/mod/emi-loot)**（fzzyhmstrs，860 万下载）：chest/block/mob 掉落概率做成多个 `EmiRecipeCategory`，按百分比分组，`EmiIngredient.of(同概率物品列表)` + `appendTooltip(概率)`；数据经**服务端→客户端包同步**进客户端缓存，EMI 插件每次 reload 读缓存重建配方（[源码](https://github.com/fzzyhmstrs/EMI_loot)）——与 CS2-Box 的 `PacketSyncBoxDefinitions` 完全同构。
  - **[Advanced Loot Info (ALI)](https://modrinth.com/mod/advanced-loot-info)**（yanny7，700 万下载）：EMI/JEI/REI **三套适配器共享一个 common 数据层**（`ali:common-emi/common-jei/common-rei` 模块，`@EmiEntrypoint` 实例），1.21.1 与 26.1.1/26.1.2/26.2 均有 neoforge 构建（[源码](https://github.com/yanny7/AdvancedLootInfo)）。是「多查看器共享 provider」的直接范本。

### A.3 集成设计
- 每个平台模块加 `emi/` 包（镜像现有 `jei/`、`rei/`）：`BoxEmiPlugin`（`@EmiEntrypoint` + `EmiPlugin`）在 `register()` 里 `addCategory` + 遍历 client `BoxRegistry` 生成 `BoxEmiRecipe`；概率计算复用现有 `BoxOdds`/`BoxGrades`/`GradeGroup`（含 `grade.color()` 上色），文本渲染复用 `RenderFontTool`。
- `BoxEmiRecipe`：`getOutputs` = 全物品池 `EmiStack` 列表，keyItem 放 `getInputs`；`addWidgets` 用 `widgets.addText` 画「掉落率 + 每档概率」文本列，每个物品 slot `.recipeContext(this).appendTooltip(概率%)`。反查由 EMI 按 outputs 自动完成，无需手写。
- **刷新（服务端同步盒定义后）**：EMI 无 JEI 式 registry 刷新。标准做法（EMI Loot 实证）：客户端 `BoxRegistry` 为数据源，`PacketSyncBoxDefinitions` 到达→更新 `BoxRegistry`→**触发 `Minecraft.getInstance().reloadResources()`**（EMI 在资源重载尾部 hook 到 `EmiReloadManager.reload()`，会重跑全部插件）→立即生效；或不做强制刷新、等下次资源重载/重进世界（EMI Loot 现状；社区补丁 [EMI Loot + LootJS Fix](https://github.com/mosharky/EMI_Loot_Fix) 也只是重跑数据管线，并未调用 EMI 内部 API）。**不建议反射调 `EmiReloadManager.reload()`**（内部类、跨版本易碎）。

### A.4 风险 / 工作量
- 工作量中等：每平台 3~4 个类（plugin/category/recipe），概率与渲染全部复用，预计与现有 JEI 集成相当。
- 风险：①EMI 1.1.x 的 API 偶有变动，需 pinned 版本 + 实测；②**26.x 无 EMI 构建**（需核实），v26 模块只能条件编译或标注不支持；③与 JEI 并存时 JemiPlugin 可能自动桥接出重复「开箱概率」分类，需实测去重。

## B. Jade / WTHIT / TOP（信息显示）

### B.1 生态事实（版本均为 Modrinth 实时查询，需自行核实网页端）
| 模组 | 谱系 | 1.21.1 NeoForge | 1.20.1 Forge | 26.1.2/26.2 NeoForge |
| --- | --- | --- | --- | --- |
| [Jade](https://modrinth.com/mod/jade)（Snownee） | HWYLA 分支，活跃 | 15.10.6+neoforge | 11.13.3+forge | 26.1.10+ / 26.2.10+neoforge |
| [WTHIT](https://modrinth.com/mod/wthit)（badasintended/deirn） | HWYLA 分支，活跃 | neo-12.10.2 | forge-8.21.1 | neo-19.0.1 / neo-20.0.0 |
| [The One Probe](https://modrinth.com/mod/the-one-probe)（McJty） | WAILA 系，仍在更 | 1.21_neo-12.0.8 | 1.20.1-10.0.3 | 26.1.2_neo-14.0.0 / 26.2_neo-15.0.0 |

- **WTHIT 不是 Jade 的 Forge 移植**：二者是平行的 HWYLA 分支（WTHIT 自述为 HWYLA 的 fork，见其 [README](https://github.com/badasintended/wthit)；Jade 自述同为 HWYLA 分支）。API 同源但类名完全不同（Jade=`snownee.jade.api`，WTHIT=`mcp.mobius.waila.api`）。WTHIT API 以 `-api` jar 发布，Maven 为 maven2/maven4.bai.lol（坐标需核实）。
- TOP 默认**需手持探针才显示**（可配置关闭）；其 26.x 构建 2026-09-03 发布。

### B.2 API 形态
- **Jade**：`@WailaPlugin` 标注类实现 `IWailaPlugin`（`register(IWailaCommonRegistration)` + `registerClient(IWailaClientRegistration)`）。客户端 `registerBlockComponent(IComponentProvider<BlockAccessor>, Class<? extends Block>)` / `registerEntityComponent(...Class<? extends Entity>)`；provider 实现 `appendTooltip(ITooltip, Accessor, IPluginConfig)`（`IBlockComponentProvider`/`IEntityComponentProvider` 是快捷子接口），`TooltipPosition.HEAD/BODY/TAIL` 控制顺序。服务端数据用 `IServerDataProvider.appendServerData(CompoundTag, Accessor)` 同步，客户端 `Accessor.getServerData()` 读回；`StreamServerDataProvider` 用 `StreamCodec` 编码。**NBT/方块实体显示先例**：Jade 自带 `ItemTooltipProvider`（对 ItemEntity 渲染完整物品 tooltip，[源码](https://github.com/Snownee/Jade/tree/1.21-neoforge/src/main/java/snownee/jade/addon/vanilla/ItemTooltipProvider.java)）。已核实 1.21-neoforge 与 26.3-fabric 两分支 API 同构。
- **WTHIT**：新 API 拆 `IWailaCommonPlugin`/`IWailaClientPlugin`，用 `waila_plugins.json` 声明（`@WailaPlugin` 注解已标 deprecated）；provider 为 `appendHead/appendBody/appendTail` 三段。服务端 `IDataProvider.appendData(IDataWriter,...)`，可写 **raw NBT**（`IDataWriter.raw()`）或**类型化 `IData`**（`IData.createType` + `ICommonRegistrar.dataType`），客户端 `IDataReader` 读回——**是 NBT / StreamCodec 型，不是 `com.mojang.serialization.Codec`**（「codec 化 provider」的说法不准确，需纠正）。
- **TOP**：`ITheOneProbe.registerProvider(IProbeInfoProvider)` / `registerEntityProvider(IProbeInfoEntityProvider)`（经 IMC `getTheOneProbe` 取实例）；provider 的 `addProbeInfo` **服务端**调用，用 `IProbeInfo.text()/item()/progress(current,max)/horizontal()/vertical()` 链式建布局。**现版 API 无 ProbeEvent**（用户提到的「事件形态」在 1.21/26.x 源码中不存在，是否指旧版或其他模组需核实）。

### B.3 集成设计
- **共享 provider 逻辑层**：可行，仿 ALI。在 common 建 `BoxTooltipInfo`（入参：`box_id`/目标对象 → 产出着色行列表），Jade/WTHIT/TOP 各写薄适配器，把行转成自家 `ITooltip`/`IProbeInfo`。
- **箱子 5 档概率 + 名称颜色**：目标是**掉落在地上的箱子物品（ItemEntity）**（本模组箱子是物品不是方块）→ 用实体 provider（Jade `registerEntityComponent` on `ItemEntity`；WTHIT `appendBody`；TOP `IProbeInfoEntityProvider`）读 `ItemCsgoBox.BOX_ID` 组件 → client `BoxRegistry` → 复用 `BoxOdds` 算 5 档概率、用 `GradeGroup.color()` 上色。**数据已在客户端（BoxRegistry 已同步），无需服务端数据往返**。
- **终端机轮次/剩余时间**：终端机是物品+UI、**无落地方块**，准星 tooltip 不适用；轮次/倒计时已由 `PacketTerminalState` 送到客户端，直接画在 `TerminalScreen` 上更合理。若未来终端落地方块，才用 Jade `IServerDataProvider` / WTHIT `IDataProvider` 同步 `TerminalSession` 状态，客户端画 `progress()`/文本（WTHIT 内置 `ProgressData` 即此类先例）。
- 现有唯一方块 `ArmoryRecyclerBlock`（`ArmoryRecyclerBlockEntity`）可顺带做 block provider（如显示军械点）。

### B.4 风险 / 工作量
- 工作量：单做一家约 2 个类（provider + 注册）几十行；三平台全覆盖约 6~10 个薄类 + common 数据层。
- 风险：①三套 API 互不兼容，需各自 compileOnly + `ModList.isLoaded` 降级（沿用 TACZ 模式），common 层不得直接依赖三家类型；②三家对 ItemEntity tooltip 的支持与优先级不同，需实测显示与冲突；③26.x 三家构建需按项目虚构前提核实；④Jade/WTHIT 的服务端数据 provider 需**双端注册**（common+client），漏一端即不显示，是常见坑；⑤TOP 默认需探针才显示，验收时注意。

## 结论
- **EMI**：官方有 NeoForge 1.21.1 构建、无 26.x；集成沿用「客户端 BoxRegistry + reload 时快照」范式（EMI Loot 实证），刷新用 `reloadResources()` 触发；26.x 模块建议条件编译。
- **信息显示**：Jade/WTHIT/TOP 三者各写薄适配器 + common 共享层最省；箱子概率纯客户端可行，终端机轮次/时间建议放终端 UI（无方块前提）。
