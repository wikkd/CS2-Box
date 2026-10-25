# Fabric 1.21.1 移植说明（fabric_1_21_1）

2026-10-23 首建，2026-10-24 编译与冒烟测试全绿。以 `v1_21_1`（NeoForge 21.1.248）为
移植基准，Fabric Loader + Fabric API + **Mojang 官方映射**（与 NeoForge 基线同名
API，最大化源码可移植性）。产物：`csgobox-fabric-1.21.1-<版本>.jar`。

## 模块结构

- 注册名 `fabric-1.21.1`（`-Pactive_versions=fabric-1.21.1`），模块目录
  `fabric_1_21_1`，包名 `com.reclizer.csgobox.fabric_1_21_1`。
- 与其它平台一致：`common/src/main/java` + `common/src/main/mcshared` 直编进模块，
  `common/src/main/resources` 共享资源；物品注册表仍是编译期常量（fixedBoxItem）。
- 机械移植走 `scripts/port-fabric-1211.py`（包名 + 窄文本替换：ModList→FabricLoader、
  FMLPaths→FabricLoader.getConfigDir、PacketDistributor→Client/ServerPlayNetworking、
  IPayloadContext→FabricPayloadContext 包装、NeoForge.EVENT_BUS.post→CsgoBus.post）。
- 冒烟测试 `PlatformSmokeTest`（3 用例：入口类可加载 / 网络接线类存在 /
  premium_supply_box 永久移除反向守卫）。

## 构建命令（注意 JAVA_HOME）

```
JAVA_HOME="C:/Users/FrostStarInquire/.jdks/ms-25.0.4.1" \
  ./gradlew :fabric_1_21_1:clean :fabric_1_21_1:build \
  -Pactive_versions=fabric-1.21.1 --no-build-cache
```

- Gradle 进程跑在 JDK 25 上（Loom 1.17+ 的依赖链需要；toolchain 仍按模块声明用
  Java 21 编译产物）。wrapper 仍是 9.5.1，**不要**全局升 wrapper（NeoGradle/ForgeGradle
  兼容性未验证，影响其它五平台）。
- **Loom 锁 1.17.21**：1.18 整线要求 Gradle ≥9.7（plugin-api 9.7 构建）+ JVM 25，
  1.19 线要求 Gradle ≥9.8，在 wrapper 9.5.1 下均不可用。
- **JEI fabric 锁 19.44.0.401**：19.69.0.457 fabric 版由 Loom 1.18.2 构建，Loom 的
  `ArtifactMetadata` 会拒绝「比自身新的 loom 构建的 mod jar」（检查 manifest 的
  `Fabric-Loom-Version`，比较 major.minor）。其余依赖（fabric-api 0.116.17、
  REI 16.0.799、EMI 1.1.24、Jade 15.10.6、WTHIT fabric-12.10.2、Cloth 15.0.140、
  Mod Menu 11.0.3）全部兼容。
- **Access widener 用 ClassTweaker 格式**（Loom 1.17 起 AW 解析委托
  `net.fabricmc:class-tweaker`）：header 必须是文件第一行
  `classTweaker v2 named`，**不支持 `#` 注释**。本模块用它放开 vanilla 的
  `PoiTypes.registerBlockStates`（private；NeoForge 注册 POI 时自动补状态表，
  Fabric 必须手动调用，否则村民无法认领工作方块）。
- 本机网络封锁 `maven.neoforged.net`（用户级 init 脚本 `~/.gradle/init.d/zz-bmclapi-mirror.gradle`
  移除该仓库）；`maven.fabricmc.net` 可达，已在 `settings.gradle` pluginManagement 增加
  `FabricLoom` 仓库解析 `fabric-loom` 插件（init 脚本只按名移除 NeoForged，不会动它）。

## 与 NeoForge 版（v1_21_1）的差异映射

| NeoForge | Fabric |
|---|---|
| `@Mod` + `IEventBus` 生命周期 | `ModInitializer` / `ClientModInitializer` |
| `DeferredRegister` | `Registry.register` + `Suppliers.memoize`（保留 `.get()` 调用形态） |
| `RegisterPayloadHandlersEvent` | `PayloadTypeRegistry` + `ServerPlayNetworking.registerGlobalReceiver`（客户端接收器在 `CsgoBoxClient` 注册，保专用服务器 dist 安全） |
| `IPayloadContext` | `net/FabricPayloadContext`（enqueueWork 内联执行——Fabric 播放接收器本就主线程；reply→ServerPlayNetworking.send） |
| `NeoForge.EVENT_BUS.post` | `event/CsgoBus`（静态小总线；六个模组自有事件改为普通类 + `Cancellable` 接口 + `event/PlayerEvent` 基类） |
| `ModConfigSpec`（csgobox.toml） | `config/CsboxConfig`（JSON：`config/csgobox.json`，同 getter/setter 面，Cloth 屏经 `CsgoBox.saveConfig()` 持久化；配置路径懒解析——裸 JVM 可加载入口类） |
| `IConfigScreenFactory` 扩展点 | Mod Menu `modmenu` entrypoint → `CsgoBoxModMenuHook`（无 Mod Menu 时不注册；Cloth 再校验一次） |
| `RegisterCommandsEvent` | `CommandRegistrationCallback`（`CsboxCommand/EditorCommand.register` 直收 dispatcher） |
| `LivingDeathEvent` / `PlayerEvent.PlayerLoggedIn/Out` / `ServerTickEvent.Pre` | `ServerLivingEntityEvents.AFTER_DEATH` / `ServerPlayConnectionEvents.JOIN/DISCONNECT`（取 `handler.player`） / `ServerTickEvents.START_SERVER_TICK` |
| `PlayerInteractEvent.RightClickItem`（客户端） | `UseItemCallback` |
| `ScreenEvent.Opening/Closing` | `ScreenEvents.BEFORE_INIT`（4 参）+ 每屏 `ScreenEvents.remove(screen)`（restore 幂等） |
| `ClientTickEvent.Post` | `ClientTickEvents.END_CLIENT_TICK` |
| `RegisterMenuScreensEvent` | `MenuScreens.register`（客户端 init 直调） |
| `ModelEvent.ModifyBakingResult`（动态箱模型兜底） | **不移植**（见降级表） |
| `RegisterEvent`（CUSTOM_STAT / TRIGGER_TYPE / POI / 职业 / 创造模式标签等） | `Registry.register(BuiltInRegistries...)`；注意 `BuiltInRegistries.TRIGGER_TYPES` 是复数；POI 手动 `PoiTypes.registerBlockStates`（经 access widener） |
| 物品 capability（RecyclerHandler） | 不需要——方块实体本身实现 vanilla `WorldlyContainer`，原版漏斗与 Fabric Transfer API 兜底包装直接可用 |
| `CreativeModeTab.builder()` 无参重载 | `FabricItemGroup.builder()`（vanilla builder 1.21.1 无 `withTabsBefore`，标签页顺序由注册顺序决定） |
| `Item.getMaxStackSize(stack)` / `ItemStack.getEnchantmentLevel(holder)` | `stack.getMaxStackSize()` / `EnchantmentHelper.getItemEnchantmentLevel(holder, stack)`（均为 NeoForge 补丁方法） |
| REI `@REIPluginClient` 注解发现 | fabric.mod.json `rei_plugins` entrypoint（注解是 Forge/NeoForge 机制） |
| `javax.annotation.Nullable`（NeoForge 自带 jsr305） | `org.jetbrains.annotations.Nullable`（MC 1.21 自带 jetbrains annotations） |

## 功能降级（相对 v1_21_1，均为上游无 Fabric 构建所致）

| 适配项 | 状态 | 说明 |
|---|---|---|
| Create 部署器自动开箱 | 降级 | Create 无 1.21.1 Fabric 构建（`create-fabric` 查询为空）。`create/` 3 个 NeoForge 文件未移植；回收站仍可被原版漏斗/Transfer API 管道自动化 |
| The One Probe | 不接入 | TOP 仅发布 Neo 构建（Modrinth `1.21_neo-*`）；悬停信息由 Jade + WTHIT 覆盖 |
| 动态箱模型兜底重映射 | 不移植 | 1.21.1 的 `ModelResourceLocation` 是 record（不继承 ResourceLocation），Fabric `resolveModel` 的 id 流无法表达 MRL；该路径只为「物品模型文件缺失」的病态安装兜底，正常安装所有箱子物品均带模型文件。代价仅是损坏安装时显示紫黑棋盘格 |

（TACZ 检视视口 / `/csbox tacz` 曾在此表——2026-10-25 起已完整适配 TaCZ:
Refabricated，见文末「TACZ 适配」节。）

## 完整保留的适配（Fabric 1.21.1 均有构建，版本已核实）

JEI 19.44.0.401 / REI 16.0.799 / EMI 1.1.24+1.21.1+fabric（JEI 注解发现、REI 经
`rei_plugins` entrypoint、EMI 注解发现）、Jade 15.10.6+fabric（@WailaPlugin）、
WTHIT fabric-12.10.2（`waila_plugins.json` 原生机制）、Cloth Config 15.0.140
（同一配置屏，JSON 后端）、Mod Menu 11.0.3（配置入口）。

## 待办（后续版本）

- [ ] L4 运行时回归：开箱 / 批量开箱 / 终端机 / 回收站 / 村民交易 / `/csbox` 全子命令
- [ ] AGENTS.md 平台清单与文件差异矩阵补 fabric_1_21_1 行
- [ ] CHANGELOG Unreleased 段补 Fabric 平台条目
- [ ] 视需求补 ProGuard minify / CI 矩阵接线

## IDEA 启动项（2026-10-24）

仓库共享启动项已加入 `.idea/runConfigurations/`（IDEA 自动识别，随 git 提交）：

- **MC Fabric 1.21.1 - Client / Server** → `:fabric_1_21_1:runClient|runServer`
- **MC Forge 1.18.2 - Client / Server** → `:forge_1_18_2:runClient|runServer`

每个启动项自带 `-Pactive_versions=<版本>` 脚本参数——IDEA 保持默认 sync（1.21.1）
即可直接启动：运行时是独立的 Gradle 调用（daemon 已关，单版本配置），完全符合
「单次调用单版本」纪律。Gradle JVM 用项目默认 21 即可（已实测 Loom 1.17.21
在 JVM 21 上配置/编译正常；JVM-25 要求只属 Loom 1.18+ 线）。fabric 首次
runClient/runServer 需联网下载 assets/服务端分发包。

## 2026-10-24 运行验证补充（REI 入口点 / loader 钉版 / #tag 时机）

测试数据包 `compat-packs/test-pack-fabric-1.21.1/` 首次实机验证暴露并修复了四个问题：

1. **REI 入口点键名（致命）**：`fabric.mod.json` 里 REI 插件注册在已废弃的
   `rei_plugins` 键下。REI 16 在客户端只是 deprecation 警告，但在**专用服务器
   main entrypoint 阶段直接抛异常**（"Rerached side issue ... use rei_server /
   rei_client / rei_common"）——fabric 的 runServer 从接入 REI 起就没真正跑
   通过。已改为 `rei_client`（插件实现的是 `REIClientPlugin`）。
2. **fabric-loader 钉版 0.16.14**：0.20.x 与 Loom 1.17 版本错位（0.20 要求
   access widener 头为新名 `classTweaker v2 named`，0.16.x 时代标准头为
   `accessWidener v2 named`；AW 头已随钉版改回）。0.16.x 是 1.21.1 时代与
   Loom 1.17 同期的正确线。
3. **`#tag` 条目加载时机（自愈）**：物品标签在客户端首次资源加载 /
   服务器数据包加载前未绑定，早期扫描（`onInitialize`）必然展开失败；而
   `BoxJsonLoader` 的解析缓存会把带错误的解析结果按文件+价格表哈希**粘住**，
   后续重扫永远复用失败结果。修复：带 LoadError 的解析不再缓存复用
   （错误箱每次重扫重解析，自愈）；客户端在 `CLIENT_STARTED`（资源与标签
   已绑定）补一次 `loadAll()`。
4. **标签成员伪 NBT 警告**：`parseItemTag` 给每个展开成员调
   `applyComponents` 时会把 `tag: "#xxx"` 再当 NBT 解析（每成员一条
   "Failed to parse NBT tag"）。已在 `applyComponents` 加 `#` 前缀守卫。

**已知 dev 环境限制（上游问题，非本模组）**：fabric 专用 dev 服务器在世界生成
阶段崩溃于 fabric-resource-loader-v0 的 `server.LanguageMixin`（"Scanned 0
target(s)"）。根因：Loom 在 `officialMojangMappings()` 下重映射该 jar 时注解
半重映射——类名已 mojmap（`net/minecraft/locale/Language`）而方法名仍是
intermediary（`method_29425`），dev 命名环境找不到目标。已排除：loader 版本
（0.16.14/0.20.1 均复现）、loom 缓存陈旧（清缓存重建后复现）。**生产环境
（intermediary 命名）与客户端（不加载 server mixin 配置）均不受影响**；箱子
加载在崩溃点之前已全部完成并验证（wool 标签展开 16 成员、`server started
with 2 box definitions`）。等待上游（loom/fabric-api）修复，不在本仓库处理。

验证记录（专用服务器 + `csbox info`）：两箱装载 skipped 0、32 条价格、
`#minecraft:wool` 展开 16 成员、`components` 组件剑条目无警告、`#` 守卫后
NBT 伪警告 0 条；remapJar 在 loader 0.16.14 下正常出包。另：runServer 首次
运行需在 `fabric_1_21_1/run/eula.txt` 写 `eula=true`（Forge 模块 run 目录
已带，fabric 是本次首次跑过 EULA 检查）。

## 2026-10-25 TACZ 适配（TaCZ: Refabricated）

上游出现了 TACZ 的非官方 Fabric 移植 [TaCZ: Refabricated]
(https://github.com/Sh1roCu/TACZ-Refabricated)（Modrinth/CurseForge 同名，
mod id 仍为 `tacz`，1.21.1 线追踪上游 TACZ 1.1.8），本模块的 TACZ 集成自此
**完整对齐 v1_21_1 基准**，不再降级。

依赖（与 v1_21_1 的 `com.tacz:tacz:1.1.8-hotfix-r6` 同机制）：

- `com.tacz:tacz-refabricated:1.21.1-0.7.1-forge1.1.8-hotfix2`（~58MB，
  gitignored；`scripts/download-tacz-fabric.sh` 灌入 local-repo，cursemaven
  主路由 + Modrinth CDN 兜底）。**`modCompileOnly`**（非 plain
  compileOnly）：生产 jar 是 intermediary 命名，Loom 借此重映射到
  Mojang 官方映射供 javac 使用——这是与 NeoForge 模组的实质差异。
- `com.github.mcmodderanchor:simplebedrockmodel-fabric:2.3.0.1+mc1.21.1`
  （从 TACZ jarjar 内嵌 jar 提取；`AnimateGeoItemRenderer` 的父接口
  `IFPGeoItemRenderer` 所在，javac 解析类层次必需；pom 随库提交、jar 忽略）。

代码适配点（相对 v1_21_1 仅三处，其余逐行同源）：

1. `ModList.get().isLoaded("tacz")` → `FabricLoader.getInstance().isModLoaded("tacz")`
   （TaczInspectViewportImpl / AnimRenderOps / TaczList / BoxItemCodec 四处）。
2. 渲染器获取：NeoForge 的
   `IClientItemExtensions.of(stack).getCustomRenderer()` 在 Fabric 不存在；
   Refabricated 用自家 `cn.sh1rocu.tacz.api.extension.IItem#getCustomRenderer()`
   暴露（枪械物品 `AbstractGunItem` 实现该接口，返回
   `GunItemRendererWrapper extends AnimateGeoItemRenderer`）。
3. `ServerLifecycleHooks.getCurrentServer()` → 本模块既有的
   `CsgoBox.currentServer()`（validateTacz 的「服务器在场才查 gun pack」门）。

解桩/移植清单：`TaczInspectViewportImpl`（新建，检视视口全套：enter/display/
trigger/render/exit + 第一人称检视会话）、`AnimRenderOps`（renderItem2D 的
无显示实例守卫、renderItem3D 的枪械分支、`renderGunModel3D` 与包围盒遍历
`gunBounds/collectPartBounds`）、`TaczList` 收集器（`/csbox tacz` 子树随
`isTaczLoaded()` 自动恢复注册）、`BoxItemCodec.validateTacz`（IGun 门 +
checkTaczNbt/normalizeTaczNbt + gun pack 缺失警告——这两个纯 NBT 助手此前
就保留着，本次接上）。`TaczInspectViewport` 门面与全部调用点
（CsLookItemScreen / FirstPersonInspectHandler）在移植时就已按门面模式写好，
本次零改动。

API 核验方式：对 release jar（0.7.1-forge1.1.8-hotfix2）逐类 javap 比对
v1_21_1 集成用到的全部签名（TimelessAPI/GunDisplayInstance/BedrockGunModel
六参 render/AnimateGeoItemRenderer/LuaAnimationStateMachine/AnimationController/
ObjectAnimationRunner/IClientPlayerGunOperator/LocalPlayerDataHolder.clientStateLock/
SoundPlayManager/IGun/Common*Index/POJO/AttachmentType）——全部同名同签名；
注意 1.21.1 分支源码已比 release 新（引用 MAE 动画库），**以 release jar 为准**。

运行时注意：Refabricated 声明 `fabricloader >= 0.17.2`，而本模块 dev 环境
钉 0.16.14（AW 头时代匹配，见上文 loader 钉版说明）——**dev runClient/
runServer 无法直接加载 TACZ 做联测**；生产环境玩家侧 loader 自选不受影响。
另一运行时依赖 Forge Config API Port（`forgeconfigapiport`），仅真实加载
TACZ 时需要。枪包方面：1.20.1 枪包需经 TACZ Pack Upgrader 升级为 1.21.1
NeoForge 兼容格式后方可用于 Refabricated（上游 README 声明）。

## 2026-10-25 Superb Warfare（卓越前线）3D 预览

与 TACZ 的「借渲染器」路线不同，SBW 集成**零依赖**：`AnimRenderOps.renderItem3D`
新增 `isSuperbWarfareItem` 分支（`BuiltInRegistries.ITEM` 命名空间判
`superbwarfare`，物品存在即证明模组已载，无需 `FabricLoader` 门），对命中物品改用
`ItemDisplayContext.FIXED`（展示框上下文）走 vanilla `ItemRenderer`——SBW 的
separate-transforms 模型把 gui 透视指到 2D 图标、3D 模型只在其余透视渲染，
FIXED 是其中标定最好的上下文（`displaysettings/*.item.json` 按一格展示框调参）。
不 import 任何 SBW/GeckoLib 类，未装 SBW 或渲染异常时按序降级（命名空间不命中
→ 原有 GUI 平面路径；渲染抛错 → 2D 图标）。三平台（v1_21_1 / forge_1_20_1 /
fabric_1_21_1）同构，26.x 无 SBW 不加。示例箱
`compat-packs/superb-warfare-pack/`。**本平台遗留**：ReFabricated（Fabric 移植）
的模型接线机制未核验（Modrinth CDN 网络不可达拉不到 jar）——若其枪械不走
separate-transforms，FIXED 渲染退化为与 GUI 相同的平面图标，无崩溃面；
待拿到 jar 后实测确认。
