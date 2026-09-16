# CS2-Box — Agent 指南

## 并发协作守则（构建互斥，重要）

工作区可能同时有多个 agent 会话/维护进程在改代码（2026-09 已有并行修改相互踩踏的教训：Create 集成与 `ArmsDealerPriceProvider` 曾在编译中途被改，造成非本会话引入的编译失败与缓存混乱）。为避免互相踩踏：

- **启动任何 Gradle 构建前必须先探测**。只要命中以下任一信号，即视为「其他 agent 进程正在编译/改代码」，本轮**不得启动编译**（不得内联重试、不得 clean、不得临时移走文件规避）：
  1. `tasklist`（Windows）或其他 `ps` 可见 `java.exe` 且命令行含 `gradle` 的进程；
  2. `~/.gradle/daemon/<版本>/` 下 daemon 日志的 mtime 在最近 1–2 分钟内仍在更新；
  3. 工作区任意 `*/src/` 源码文件近 60 秒内出现成批 mtime 变化（其他 agent 活跃写入）；
  4. 同一文件两次读取内容不一致（正被其他进程改写）。
- **命中后**：立即停下编译动作，向用户报告探测到的信号与冲突风险，**等待用户发起下一轮对话**再继续。不要自动重试，也不要自行修复属于其他 agent 领域（`create/` 集成、`villager/` 等）的文件。
- **确认可以编译时**：优先用 `--no-build-cache` / `--rerun-tasks` 或先删除编译输出再执行，避免把陈旧的 `UP-TO-DATE` / `FROM-CACHE` 快照误报为真实编译结果；报告结果时注明采用的重编译方式与平台清单。
- 单次 Gradle 调用只能构建一个 MC 版本（`-Pactive_versions=<v>`）；六平台全量验证需串行执行。

## 构建

```bash
./gradlew :<module>:compileJava -Pactive_versions=<v>  # 快速编译单个平台
./gradlew :<module>:jar -Pactive_versions=<v>          # 打包
./gradlew :common:test                                 # common 单元测试 (JUnit 5)
```

- **Java**: 21（legacy 平台）/ 25 + `--enable-preview`（v26_1_2/v26_2）。toolchain 由各模块 build.gradle 指定。
- **每次 Gradle 调用只能构建一个 MC 版本**（NeoGradle userdev IDEA 扩展冲突，历史限制）。用 `-Pactive_versions=<v>` 覆盖 `gradle.properties` 的默认值（当前默认 1.21.1，便于 IDEA 直接导入 v1_21_1 模块；CI 与各 run 配置均显式传 `-P`，不受默认影响）。
- **NeoGradle 全平台统一 7.1.38**（含 v1_21_1/3/4/5——曾用 7.0.171，与 Gradle wrapper 9.5.1 配置阶段不兼容已升级）。wrapper 9.5.1 满足全部模块（forge_26_1_2 的 ForgeGradle 7 要求 ≥9.3）。
- **forge_1_20_1 发布产物必须走 `:forge_1_20_1:renameJar`**（`net.minecraftforge.renamer` 插件，official→SRG 重映射）：1.20.1 生产运行时是 SRG 名，`jar` 直出产物会在第一个改名过的 Minecraft 成员调用处 `NoSuchMethodError`/`NoSuchFieldError`（2.0.0-beta 的 `CriteriaTriggers.register` 线上事故即此因）；发布物为 `build/libs/csgobox-forge-1.20.1-<mod_version>-srg.jar`，普通 `jar` 产物仅 dev 用。
- **物品注册表必须是编译期常量**（v2.0.1 联机事故教训）：**禁止**在 `RegisterEvent` 里按 `config/csbox/` 内容或本地模组环境注册物品——物品注册表是同步且启动期冻结的，客户端与服务端一旦不同就会以「Failed to synchronize registry data from server / 模组版本不匹配」拒绝连接（两侧版本号还会显示成一样，极难排查）。箱子 = **数据**（`config/csbox/*.json` + 物品的 `csgobox:box_id` 标签 + 服务端同步的 `BoxRegistry`），发放走 `/csbox give <玩家> <箱子> [数量]`；要新增"随模组发布的默认箱子"就在各平台 `ModItems` 里加一个固定物品（`fixedBoxItem`），**不要**恢复动态注册。
- **3 个平台模块**：`v1_21_1`（NeoForge 21.x，旧 API）+ `v26_1_2` / `v26_2`（NeoForge 26.x，decoupled API）。**已归档（EOL）平台** `v1_21_0` / `v1_21_3` / `v1_21_4` / `v1_21_5` / `v1_21_8` / `v1_21_10` / `v1_21_11` 于 2026-08-09 从仓库删除，最后状态在 tag `eol-legacy-21x-1.0.6`，复活需从该 tag 检出。
- **v1_21_1 有 compileOnly TACZ 依赖**（永恒枪械工坊：零，检视视口集成）：jar 不入库（~57MB，仓库惯例 `*.jar` 全局忽略、只提交 pom），首次构建前运行 `scripts/download-tacz.sh` 填充 `local-repo/com/tacz/` 并从 jarjar 提取编译所需的 `simplebedrockmodel`（CI 自动执行）。运行时经 `ModList.isLoaded("tacz")` 检测，无 TACZ 环境功能静默降级。**`forge_1_20_1` 同样有 TACZ 依赖**（official 1.20.1 构建），脚本 `scripts/download-tacz-1201.sh`（产物同机制，jar 入 `local-repo/` 不提交，CI 自动执行）。
- **同步开发模块 `forge_26_1_2`**（MinecraftForge 26.1.2-64.1.0，Java 25）：已注册在 `settings.gradle`（`-Pactive_versions=forge-26.1.2`），随 **1.0.6 发行** 纳入 git 管理，自 **2.0.0 线起纳入同步开发**——与 `v26_1_2` 基准保持特性同步（同一 `mod_version`，经 `scripts/port-forge-2612.py` 机械转换 + 手工适配，见「平台模块镜像纪律」§forge 同步），`build.gradle` 的 2.0.0 线排除清单已随首轮同步删除；**不在 CI 矩阵**（手工/门禁脚本发布）；自 **2.0.0 起纳入正式发布**（与 v26_1_2 同步发行，勿当测试平台对待）。测试流程与发布门禁见 `docs/TESTING-FORGE-2612.md`。
- **正式发布模块 `forge_26_2`**（MinecraftForge 26.2-65.1.1，Java 25，2026-08-14 首建）：注册在 `settings.gradle`（`-Pactive_versions=forge-26.2`）。**2.0.0 线已追平**：以 `forge_26_1_2`（1.0.6/2.0.0 同步线）为基准整模块迁移，经 `scripts/port-forge-262.py` 机械移植（包名 `forge_26_1_2 → forge_26_2` + Forge 26.1.2→26.2 API 映射）+ 手工适配（`Options.hideGui` 移除 → `utils/HudVisibility`、`setScreen` → `setScreenAndShow`、advancement 包迁移、PIP 渲染器保持 **Forge 欧拉角方案**——`event.register(new Icon3DRenderer())` + `getRenderState().addPicturesInPictureState`，与 NeoForge 26.2 的 Quat/Supplier 方案不同），`build.gradle` 的 2.0.0 线排除清单已删除，`PlatformSmokeTest` 已改为断言 2.0.0 物品存在。**不在 CI 矩阵**、不参与 3 平台镜像纪律与 AnimRenderOps 漂移门禁（与 `forge_26_1_2` 同策略）。2026-08-18 全面审计确认：`test-forge-262.sh` 7/7 PASS，5 平台 `clean compileJava` 全通过，版本四同步 OK，资源一致性已补齐（4 个物品定义从 `forge_26_1_2` 补入）。测试流程与迁移记录见 `docs/TESTING-FORGE-262.md`。
- **正式发布模块 `forge_1_20_1`**（MinecraftForge 1.20.1-47.4.22，Java 17，ForgeGradle 7.x，2026-08-18 首建）：注册在 `settings.gradle`（`-Pactive_versions=forge-1.20.1`）。2.0.0 线功能向 MC 1.20.1 的回移，以 `forge_26_1_2` 为基准、复用 `common/` 全部纯 Java 逻辑；三大重写区域：Networking 改 `SimpleChannel`（14 packet）、Capability 走 `LazyOptional` + `AttachCapabilitiesEvent`、渲染层 `GuiGraphics` 直调（**无 PIP 系统**，普通物品 `AnimRenderOps.renderItem3D` 降级 2D、`supports3D()` 返回 false，但 **TACZ 枪械经 `renderGunModel3D` 全 3D**——`RenderSystem.getModelViewStack()` 在 1.20.1 返回 `PoseStack`，用 `pushPose`+`mulPoseMatrix` 而非 26.x 的 Matrix4fStack 方案）；DataComponent 存储回退 ItemStack NBT，`StreamCodec`/`RegistryFriendlyByteBuf` 改 `FriendlyByteBuf` 手动序列化。TACZ 检视（`TaczInspectViewport`+`BoxItemCodec.validateTacz`）与 TACZ 依赖同 `v1_21_1` 机制（`scripts/download-tacz-1201.sh`，gun tag 读顶层 ItemStack NBT）。JEI 已接入（`cfd750f`，JEI 15.x 开箱概率查询，4 文件 `jei/` 包 + `BoxJeiSync` 静态桥，`mods.toml` 可选依赖 `[15.20,)`，与 NeoForge 三平台机制一致）。**不在 CI 矩阵**、不参与 3 平台镜像纪律与 AnimRenderOps 漂移门禁（与其他 forge 模块同策略），自 **2.0.0 起纳入正式发布**。测试流程见 `docs/TESTING-FORGE-1201.md`，迁移计划见 `.opencode/plans/2026-08-18-forge-1-20-1-port.md`。

## 架构约束（CONSTRAINT-001）

- **`common/` 不得 import 任何 `net.minecraft.*` / `net.neoforged.*`**（编译环境无 MC classpath，违反即编译失败）。共享资源（纹理/音效/lang/配方/成就）也放在 `common/src/main/resources/`。该约束由 `:common:checkCommonArchitecture` Gradle task 自动化执行（挂载在 `compileJava` 依赖上，任何编译/测试都会触发，含 forge_26_1_2 把 common 源码编进自身 classpath 的场景）。
- 平台模块通过 `srcDir project(':common').file('src/main/resources')` 共享资源。
- 依赖方向：`平台 → common`，`common` 不依赖任何平台。
- **`premium_supply_box` / `ItemPremiumBox` 永久移除（2026-08-19）**：该「军火商高级箱」物品已从代码、资源、配置与全部文档中彻底删除，**禁止以任何形式再次引入**（含历史条目复活）。`forge_26_1_2` / `forge_26_2` 的 `PlatformSmokeTest` 含反向守卫（`premiumBoxItemIsPermanentlyRemoved`）断言该字段永不回归，新增字段/资源/交易时勿再使用该命名。

## 平台模块镜像纪律（重要！）

3 个 NeoForge 平台模块**不是纯拷贝**：26.2 有 API 适配（如 `BuiltInRegistries.ITEM.get()` 返回 Optional、`spawnAtLocation(ServerLevel,...)`、`lookup()`、`MouseButtonEvent` 事件、`setScreenAndShow`、PIP 渲染器等）。**禁止用 `v26_1_2` 整文件覆盖 `v26_2`**——会破坏适配（历史教训，曾导致 v1_21_10 编译失败；该平台现已与其余 legacy 一并归档）。

跨平台改动的正确姿势：

1. 先改基准模块：new 用 `v26_1_2`（legacy 唯一模块 `v1_21_1` 直接改）
2. `scripts/mirror.sh new <rel-path>` — 仅用于**无适配差异**的纯新增文件（目标已存在会警告跳过，`--force` 覆盖，`--dry-run` 预演不写盘）
3. 有适配差异的文件用**定点合入**（`v26_1_2` → `v26_2` 手工适配；幂等合入脚本范例与 `scripts/port-12111.py` 已随 EOL 平台删除）
4. 每平台 `compileJava` 验证（增量缓存可能造假象——**改动涉及平台时用 `clean` 编译确认**）

### forge_26_1_2 同步（自 2.0.0 线起）

`forge_26_1_2` 与 `v26_1_2` 保持**特性同步**，但 loader 不同（MinecraftForge vs
NeoForge），**整文件覆盖同样禁止**。同步纪律：

1. 基准仍是 `v26_1_2`（先改基准模块）；
2. `scripts/port-forge-2612.py` 做机械转换（包名 `v26_1_2 → forge_26_1_2` +
   NeoForge→Forge import/API 映射），**只负责纯机械文件与新增文件**；
3. 有适配差异的文件（入口 `CsgoBox.java`、`Networking`、`ModItems`、`ModCapability`、
   packet handler、GUI/渲染层、AnimRenderOps 等）走**手工适配**，不得被脚本覆盖
   （`--force` 仅用于确认无本地改动时重灌）；
4. forge 侧专有的修复（如 `GuiItemMove` PIP 高清 3D、`items/` 模型定义）保留在
   forge 模块内，同步时手工合入对应 v26_1_2 改动；
5. 每次同步后：删除 `build.gradle` 中已同步的 2.0.0 排除项 → `clean compileJava`
   （`-Pactive_versions=forge-26.1.2`）→ `scripts/test-forge-2612.sh` 门禁 → 必要时
   L4 运行时回归。漂移盘点用 `scripts/port-forge-2612.py --dry-run`。

## 版本号管理（升级时四处同步）

`gradle.properties` 的 `mod_version=` + `neoforge.mods.toml`（模板变量 `${mod_version}` 自动注入）+ `CHANGELOG.md` + `README.md`。发布流程见 `docs/RELEASE.md`。一致性检查：`scripts/check-version.sh`（CI 的 `common-test` job 已接入）。

### ⚠️ 版本号变更铁律

- **未经维护者明确给出新版本号，严禁修改 `gradle.properties` 的 `mod_version`** —— 改版本号即发布动作，只能由维护者拍板执行。
- 教程落盘文件名与 `mod_version` 强耦合（`_tutorial_v<版本>.md`，首次启动从**包内资源**复制，不再联网下载）。内嵌源为固定名 `common/src/main/resources/assets/csgobox/tutorials/tutorial.md` 与 `tutorial_zh_cn.md`；改版本号之前必须确认内嵌源内容已更新，并把同步副本 `docs/tutorials/_tutorial_v<新版本>*.md` 推送到 Gitee 在线版（见 `docs/RELEASE.md` §5「发布后收尾」）。
- 版本号无法从 jar 清单解析时（dev/IDE 无 manifest），教程系统自动整体跳过（`BoxDefaults.modVersion()` 返回 null → 不复制、不删除），杜绝 `_tutorial_vunknown.md` 落盘与误删已有教程。

## 关键文件

- `CsgoBox.java` — 平台入口；`CONFIG` 为 `public static final`（static 块初始化，勿改顺序）；`registerDynamicBoxItems` 注册 `config/csbox/*.json` 动态 item（用 `RegisterEvent` deferred supplier，**不要**用 `FMLCommonSetupEvent.enqueueWork`——registry 已 freeze）
- `CsboxConfig.java` — NeoForge `ModConfigSpec` / Forge `ForgeConfigSpec`，builder 每个 `define*` 用 `.get()`；`bulkOpenCount`（0=无上限）服务端权威。v2.0.x 起带公开 `set*` 写方法（供 Cloth Config GUI 用）
- `config/CsboxClothConfigScreen.java`（六平台各一份）— **可选** Cloth Config 配置屏：套在既有 `ConfigValue` 上（读 getter、写 setter、保存统一 `CsgoBox.CONFIG_SPEC.save()`），**不迁移配置存储**（TOML 仍是唯一来源）；注册点在各平台 `CsgoBox` 构造器内 `Dist.CLIENT && ModList.isLoaded("cloth_config")` 分支，Cloth 未装时无 Configure 按钮、功能不变；六平台同文（仅 package/loader 注册差异）
- `packet/PacketCsgoProgress.java` — 服务端权威 RNG + `OPEN_BLOCKED_UNTIL_TICK`（ConcurrentHashMap，`tickOpenBlockMap` 每 100 tick 清理）
- `packet/PacketCsgoBulkProgress.java` — 批量开箱（异步线程池 `BULK_COMPUTE_POOL` + 主线程 finalize）
- `gui/CsboxBulkOverviewScreen.java` — 批量开箱总览屏（Shift+右键进入；点「开启」直接发包 `PacketCsgoBulkProgress` 并进 `CsboxProgressScreen`，无二次确认屏；服务端权威复核库存与扣减）
- `common/box/BoxDefaults.java` — 教程内嵌资源复制（`writeTutorialIfMissing` + `refreshTutorials`；**当前版本教程全部就位后**才按 `^_tutorial_v.*\.md$` 白名单删除旧版；复制失败/无 jar 清单版本时**绝不删除**；无网络、无回收站）
- `common/box/BoxGrades.java` / `BoxRegistryStore.java` / `BoxStripGenerator.java` — 等级常量与纯函数 / 泛型注册表容器（回调契约固化）/ 泛型开箱滚动条（2026-08 重构下沉，平台 `BoxDefinition`/`BoxRegistry`/packet 引用指向 common）
- `common/logic/OpenBlockGuard.java` — 服务端权威开箱冷却（10 tick，`isBlocked`/`block`/`tick`），六平台 packet 与 `ModEvents#serverTick` 共用
- `common/config/CsboxConfigDefaults.java` — 六平台 `CsboxConfig` 默认值与取值范围唯一来源（枚举默认以常量名字符串存储）
- `common/box/BoxOdds.java` — 纯概率数学（`totalWeight` / `gradeChance` / `itemChance` / `percent` / v2.0.1 起 `weightedItemChance` / `positiveItemWeightSum`）；JEI 分类、`/csbox info`、箱子 tooltip 概率行共用同一来源（与服务端 roll 同口径）
- `item/ItemCsgoBox.java` — 箱子物品；`appendHoverText` 显示 5 档概率行 + 每档物品清单（v2.0.1 加权时逐物品显示概率）；`buildGradeMap()` 构建加权池；`applyIcon` 应用每箱图标（26.x 支持 ITEM_MODEL，1.21.1/1.20.1 仅 CMD）
- `box/BoxItemCodec.java` — v2.0.1 物品解析（`weight` / `count:[min,max]` / `#tag` 展开 / `loot_table` / `enchant` / 缺模组 id 区分；`item_spec` 标记）；forge_1_20_1 用 NBT 变体、v1_21_1 保留 TACZ 特判
- `box/BoxItemResolver.java`（v2.0.1 新增，每平台一份）— 开箱/购买时解析 `item_spec`（count 区间、随机附魔、loot_table 掷表），仅服务端调用
- `box/GradeGroup.java` — v2.0.1 第 7 字段 `itemWeights`（平行列表，默认全 1 = 均匀）；`itemWeightAt` / `positiveItemWeightSum`
- `box/BoxDefinition.java` — v2.0.1 新字段 `enabled`/`requires`/`icon`/`discount`/`stock`/`restockMinutes`/`maxPerPlayer`/`cooldownSeconds`/`permission`；`discountedPrice` / `missingRequirement`
- `box/BoxJsonLoader.java` — v2.0.1 文件名校验 / enabled+requires 门控 / 空档位 warning / `/csbox validate` 干跑（`validateFile`）/ 新字段解析；`downloadTutorialsAsync()` 统一教程入口（服务端 `loadAll()` + 客户端 `ClientModEvents#onClientSetup` 共用，把包内教程复制到本地 `config/csbox/`，无网络）
- `box/PriceTable.java`（v2.0.1 新增）— 全局终端价格表 `config/csbox/_prices.json` 的纯函数解析/查价（物品 id → 固定非负整数或 `[min, max]` 随机范围，`PriceRange`；`id#变体` 子键支持 TACZ 等 NBT 变体定价；未命中 = 无价格（终端机不售、拆解 0，装载器对未定价 id 物品报错，无默认价回退））；`lookupRange` + `PriceRange.sample(IntUnaryOperator)` 服务端每次报价/拆解采样；`box.schema.json` 已移除物品级 `price`，残留字段由 `BoxJsonSchemaValidator` 报错
- `box/PriceRange.java`（v2.0.1 新增）— 固定价/范围价值对象（`[min, max]` 闭区间、`UNPRICED` 哨兵、`sample` 取整），纯 Java 无 MC 依赖；六平台 `GradeGroup.priceForIndex` 返回 `PriceRange`，网络流按 `[min, max]` 双 int 序列化
- `box/LegacyPriceMigration.java`
- `box/PriceTableRegistry.java`（v2.0.1 新增）— 当前价格表的跨平台持有者：六平台 `BoxJsonLoader` 每次 load/reload 发布（/csbox validate 干跑不发布），武库拆解台只读消费，按 `PriceTable.recycleYield`(表价 × 90% 向上取整) 计价，表外未定价物品不可拆解（产出 0）
- **Create/自动化联动（v2.0.1）**：武库拆解台开放物品处理 capability——Forge 三平台
  `ArmoryRecyclerBlockEntity` 覆盖 `getCapability`（`ForgeCapabilities.ITEM_HANDLER` +
  内部 `RecyclerHandler`），NeoForge 1.21.1（v1_21_1）用 `RecyclerAutomation`
  （`@EventBusSubscriber(Bus.MOD)` + `RegisterCapabilitiesEvent.registerBlockEntity`，
  同包 `ArmoryRecyclerBlockEntity.RecyclerHandler` 为 public）。输入槽只收 grade 印记
  物品、输出槽只可提取武库点数；Create（1.20.1/1.21.1）机械臂/传送带/管道可直接自动化。
  `v26_1_2` / `v26_2`（NeoForge 26.x）刻意未做——该线 capability 已迁移 transfer API
  （`Capabilities.Item.BLOCK` = `ResourceHandler<ItemResource>`）且 Create 无 26.x 构建，
  待 Create 26.x 发布后按 transfer API 适配
（v2.0.1 新增）— 旧版适配：每次 `loadAll`/`/csbox reload` 前把残留的旧 `price` 自动转移进 `_prices.json`（同物不同价按平均值四舍五入；表已有价优先；`#tag`/`loot_table`/非法价保留报错；表损坏则整体中止不写入），并幂等清除已迁移字段（六平台 `BoxJsonLoader` 各在 loadAll + reloadPreserving 调一次）
- `common/logic/BoxConstraintTracker.java`（v2.0.1 新增）— 开箱约束（max_per_player / cooldown）内存追踪，六平台共用
- `common/terminal/TerminalStockManager.java`（v2.0.1 新增）— 终端库存/补货内存态，六平台共用
- `command/CsboxCommand.java` — `/csbox` 命令树；`showInfoOverview` 末尾输出「物品来源模组」命名空间统计（v26 系 `BuiltInRegistries` / forge_1_20_1 `ForgeRegistries`）；v2.0.1 起 `/csbox validate [box]` 干跑 + info 展示新字段
- `command/EditorCommand.java`（六平台各一份，v2.0.1 新增）— 独立类注册 `/csbox editor` 子命令（**全员可用，无需 OP**），聊天栏输出可点击的网页配置工具链接（https://wikkd.github.io/CS2-Box/）；`CsboxCommand` 的 `/csbox help` 复用 `EditorCommand.EDITOR_URL` 聚合同一链接，并另给两个可点击入口：**打开教程文件夹**（`open_file` 相对路径 `config/csbox`，客户端游戏目录下）与**在线教程**（Gitee `docs/tutorials/`），help 全员可用（`info`/`reload`/`validate`/`give` 权限不变）；新增/调整需六平台同步并逐平台编译验证
- `scripts/add-biome.py` — 向 `has_structure/*` 群系标签追加 biome（去重/幂等/`--dry-run`/`--replace`），让武库商小屋在模组群系生成（机制见 `docs/BIOME-INTEGRATION.md`）
- `scripts/boxgen.py` / `scripts/check-ids.py`（v2.0.1 新增）— 箱子配置生成器 / compat-packs id 核查工具
- `scripts/sync-box-editor-data.py`（v2.0.1 新增）+ `box-editor/` — 可视化网页配置工具（纯前端零构建，`file://` 可离线打开，可整目录发布 GitHub Pages；中英双语、跟随六平台版本控制 TACZ 变体/Data Components 可见性（TACZ 专属字段另有手动开关可强制启用/禁用；撤销/重做、校验项点击定位、物品
  复制/批量粘贴、价格批量导入（固定价或 `min-max` 范围价）、分享链接、悬浮教程、折叠记忆；改动后跑
  `npm run test:smoke`（Playwright 冒烟，CI `box-editor-smoke.yml` 已接线））；实时预览 + 轻量校验 + 旧版内联 `price` 自动迁移（平均/已有价优先，与 `LegacyPriceMigration` 一致）；**数据生成式**：schema/示例/版本元数据内嵌进 `js/data.js`，修改 `docs/box-schema/*.schema.json` 或 `docs/examples` 后必须重跑该脚本，勿手改 `data.js`）。本地部署套件：`server.mjs`（零依赖静态服务器）/ `build.mjs`（产出 `dist/`，已 gitignore）/ `start.bat`+`start.sh` / `package.json`（npm start|build|sync）；`scripts/sync-box-editor-data.py` 亦可由 `npm run sync` 触发（Windows `python` 为占位符时用 venv python 直跑）；GitHub Pages 自动发布走 `.github/workflows/box-editor-pages.yml`（Settings → Pages → Source: GitHub Actions）
- `compat-packs/` — 官方联动数据包示例（Apotheosis / Iron's Spells / TACZ 各一个箱子包 + README，v2.0.1 示范 requires/weight/count 区间/enchant）
- `docs/box-schema/box.schema.json`（v2.0.1 新增）— 箱子配置 JSON Schema（IDE 补全用）
- `utils/AnimRenderOps.java` — **动画渲染唯一适配点**（各平台一份，`// era: legacy|decoupled` 头标注）：屏与逻辑助手只经它调用渲染原语（`blitTextured`×3 变体 / `fill` / `fillGradient` / `scissor` / `scissorDisable` / `setBlendNormal` / `flush` / `renderBlurredBackground` / `renderItem2D` / `renderItem3D` / `supports3D`，共 13 个公开 op）。跨平台签名一致性由 `scripts/check-animops-drift.sh` 守护（CI `common-test` job 已接线）。**新增原语须三平台同步补**，否则漂移检查失败。**Shader 兼容**：`supports3D()` 返回 `!isShaderModActive()`（检测 `iris`/`oculus`），shader 激活时 `renderItem3D` 内部回退 `renderItem2D`（公开 op 表面不变，drift 仍过）；见 `docs/SHADER-COMPAT.md`。**Modern UI 软兼容**：装 `modernui` **不降级 3D**（官方声明兼容 vanilla GUI 系统模组），首次 `supports3D()` 调用打印渲染环境诊断（`[csgobox] 3D preview env: ... modernui=...`）；策略与验证清单见 `docs/MODERN-UI-COMPAT.md`
- `docs/SHADER-COMPAT.md` / `docs/DESIGN-jade-wthit-integration.md` / `docs/DESIGN-rarity-mapping.md` / `docs/DESIGN-terminal-protocol.md` — 批次 B 与立项待办的设计/兼容文档（Jade/WTHIT 待联网接线，RFC 与终端机协议待评审）
- `utils/IconListTools.java` — 2D 物品网格（26.x/1.21.8+ 有 per-item bounding box 居中；渲染原语已委托 AnimRenderOps）
- `utils/GuiItemMove.java` — 3D 拖拽预览（`renderRotAngleX/Y` 纯数学保留，渲染委托 `AnimRenderOps.renderItem3D`）
- `utils/ButtonPalette.java`（v26_1_2 / v26_2）— 按钮调色板常量（CLOSE 等），Forge 侧未移植
- `utils/HudVisibility.java`（v26_2 / forge_26_2）— 26.2 无 `Options.hideGui`，用 `Minecraft.gui.hud.toggle()/isHidden()` 包装
- `common/utils/` — `ColorTools` / `OverlayColor`（三档 token：surface/panel/divider）/ `GuiRegion`（容器化布局）/ `EntityChineseMap`
- `advancement/OpenedBoxTrigger.java` — `csgobox:opened_box` trigger + `Stats.CUSTOM` 累加
- `event/BoxOpenedEvent.java` — NeoForge 事件总线开箱通知（post-event，KubeJS 兼容，见 `docs/KUBEJS-EVENTS.md`）

## 配方

`common/src/main/resources/data/csgobox/recipe/`（单数 `recipe`）。`csgo_key3` 仅锻造台（`smithing_transform`）。

## 测试

- `common` 有 JUnit 5（25 个测试类 / 210 用例，其中 `BoxJsonSchemaValidatorTest` 34 用例）：`./gradlew :common:test`（CI 独立 `common-test` job 跑一次，不再随各平台矩阵重复执行）
- `common` 架构约束检查由 `:common:checkCommonArchitecture` 自动挂载在编译上（见「架构约束」节）
- AnimRenderOps 跨平台签名漂移检查：`scripts/check-animops-drift.sh`（3 平台，CI 已接线，本地改门面后必跑）
- 平台层最小测试：`v26_1_2` / `v26_2` / `forge_26_1_2` / `forge_26_2` 均有 `PlatformSmokeTest`（JUnit 5，验证入口类可加载，不初始化 MC 运行时）：`./gradlew :<module>:test -Pactive_versions=<v>`
- 其余平台暂无自动化测试；运行时回归清单见 `docs/RELEASE.md` 质量门
- **代码审查标准与流程见 `docs/CODE-REVIEW.md`**（专属审查清单：CONSTRAINT-001 / 镜像纪律 / 版本四同步 / AnimRenderOps 漂移 / 并发权威等）；PR 描述模板 `.github/PULL_REQUEST_TEMPLATE.md` 由 CI `pr-checks.yml` 校验；GameTest 集成测试 CI 见 `gametest.yml`（当前无用例时跳过）；分支保护设置见 `docs/CI-PROTECTION.md`

### 平台 Java 文件差异矩阵（2026-09 刷新：forge_1_20_1 接入 JEI/REI，文件数同步当前树）

| 文件 | v1_21_1 | v26_1_2 | v26_2 | forge_26_1_2 | forge_26_2 | forge_1_20_1 |
|------|:-------:|:-------:|:-----:|:------------:|:----------:|:------------:|
| TACZ compat (2 文件) | ✅ | — | — | — | — | ✅ |
| `ButtonPalette` | — | ✅ | ✅ | — | — | ✅ |
| `HudVisibility` | — | — | ✅ | — | ✅ | — |
| JEI (4 文件) | 4 文件 | ✅ | ✅ | ❌ | ❌ | ✅ |
| REI (3 文件) | 3 文件 | ✅ | ✅ | ❌ | ❌ | 3 文件 |
| `PacketSyncBoxDefinitions` | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `Networking`（Forge 专用） | — | — | — | ✅ | ✅ | ✅ |
| **文件数** | **86** | **85** | **86** | **79** | **80** | **88** |

- TACZ：`v1_21_1`（unofficial 1.21.1 port，`scripts/download-tacz.sh`）与 `forge_1_20_1`（official 1.20.1，`scripts/download-tacz-1201.sh`）有 `compileOnly` 依赖，其它平台不需要；`forge_1_20_1` 的 gun NBT 在 ItemStack 顶层 tag（无 DataComponent 系统），`BoxItemCodec.validateTacz` 直接读写 `stack.getTag()`，枪 tag 的 `GunFireMode` 规范化在内联修正
- ButtonPalette：`v26_1_2`/`v26_2` 的 26.x 辅助类，Forge 侧未移植（非功能阻塞）
- JEI：NeoForge 3 平台已同步；`forge_1_20_1` 已接入（JEI 15.x，`mods.toml` 可选依赖 `[15.20,)`）；**`forge_26_1_2` / `forge_26_2` 缺失**（已知待办，Modrinth 无 JEI 26.x Forge 构建）
- REI：`v1_21_1`（16.x）/`v26_1_2`（26.1.x）/`v26_2`（26.2.x）NeoForge 已同步——`@REIPluginClient` + 动态显示生成器（`/csbox reload` / 文件热重载后无需手动刷新），`forge_1_20_1` 已接入（REI 12.x，`mods.toml` 可选依赖 `[12.0,)`）；**`forge_26_1_2` / `forge_26_2` 缺失**（与 JEI 同策略，暂不接入）
- Networking vs PacketSyncBoxDefinitions：Forge 用 `SimpleChannel`，NeoForge 用 `CustomPacketPayload`，平台差异正常；`forge_1_20_1` 同为 `SimpleChannel`（Forge 47.x API）。**Forge 三平台已补齐全量定义同步**（`PacketSyncBoxDefinitions`，`sync_box_definitions`：玩家加入 / `/csbox reload` / 文件热重载时服务端广播整份 `BoxRegistry`，客户端 `clear + register` 覆盖，`forge_1_20_1` 同时刷新 JEI），与 NeoForge 三平台架构对齐——专用服务器下客户端定义内容（权重/价格/物品清单/JEI 概率）始终以服务端为准
- Tutorial/Validator：`BoxJsonSchemaValidator` 走 `common/` 唯一实现（六平台共用，无平台本地副本）；教程**随包内置**（固定资源名 `assets/csgobox/tutorials/tutorial.md` / `tutorial_zh_cn.md`），六平台统一为后台线程把内嵌资源复制到 `config/csbox/`（落盘名带版本号 `_tutorial_v<版本>.md`），服务端（`loadAll`）与客户端（`onClientSetup`）各触发一次，`BoxDefaults.writeTutorialIfMissing` 幂等 + synchronized 串行防单机双线程并发；**联网下载类 `TutorialFetcher` / `TutorialSources` 已随 v2.0.1 换用包内嵌而删除，运行时零网络外联**
- **代码审查标准与流程见 `docs/CODE-REVIEW.md`**（专属审查清单：CONSTRAINT-001 / 镜像纪律 / 版本四同步 / AnimRenderOps 漂移 / 并发权威等）；PR 描述模板 `.github/PULL_REQUEST_TEMPLATE.md` 由 CI `pr-checks.yml` 校验；GameTest 集成测试 CI 见 `gametest.yml`（当前无用例时跳过）；分支保护设置见 `docs/CI-PROTECTION.md`
