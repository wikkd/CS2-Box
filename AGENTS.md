# CS2-Box — Agent 指南

## 构建

```bash
./gradlew :<module>:compileJava -Pactive_versions=<v>  # 快速编译单个平台
./gradlew :<module>:jar -Pactive_versions=<v>          # 打包
./gradlew :common:test                                 # common 单元测试 (JUnit 5)
```

- **Java**: 21（legacy 平台）/ 25 + `--enable-preview`（v26_1_2/v26_2）。toolchain 由各模块 build.gradle 指定。
- **每次 Gradle 调用只能构建一个 MC 版本**（NeoGradle userdev IDEA 扩展冲突，历史限制）。用 `-Pactive_versions=<v>` 覆盖 `gradle.properties` 的默认值（当前默认 1.21.1，便于 IDEA 直接导入 v1_21_1 模块；CI 与各 run 配置均显式传 `-P`，不受默认影响）。
- **NeoGradle 全平台统一 7.1.38**（含 v1_21_1/3/4/5——曾用 7.0.171，与 Gradle wrapper 9.5.1 配置阶段不兼容已升级）。wrapper 9.5.1 满足全部模块（forge_26_1_2 的 ForgeGradle 7 要求 ≥9.3）。
- **3 个平台模块**：`v1_21_1`（NeoForge 21.x，旧 API）+ `v26_1_2` / `v26_2`（NeoForge 26.x，decoupled API）。**已归档（EOL）平台** `v1_21_0` / `v1_21_3` / `v1_21_4` / `v1_21_5` / `v1_21_8` / `v1_21_10` / `v1_21_11` 于 2026-08-09 从仓库删除，最后状态在 tag `eol-legacy-21x-1.0.6`，复活需从该 tag 检出。
- **v1_21_1 有 compileOnly TACZ 依赖**（永恒枪械工坊：零，检视视口集成）：jar 不入库（~57MB，仓库惯例 `*.jar` 全局忽略、只提交 pom），首次构建前运行 `scripts/download-tacz.sh` 填充 `local-repo/com/tacz/` 并从 jarjar 提取编译所需的 `simplebedrockmodel`（CI 自动执行）。运行时经 `ModList.isLoaded("tacz")` 检测，无 TACZ 环境功能静默降级。**`forge_1_20_1` 同样有 TACZ 依赖**（official 1.20.1 构建），脚本 `scripts/download-tacz-1201.sh`（产物同机制，jar 入 `local-repo/` 不提交，CI 自动执行）。
- **同步开发模块 `forge_26_1_2`**（MinecraftForge 26.1.2-64.1.0，Java 25）：已注册在 `settings.gradle`（`-Pactive_versions=forge-26.1.2`），随 **1.0.6 发行** 纳入 git 管理，自 **2.0.0 线起纳入同步开发**——与 `v26_1_2` 基准保持特性同步（同一 `mod_version`，经 `scripts/port-forge-2612.py` 机械转换 + 手工适配，见「平台模块镜像纪律」§forge 同步），`build.gradle` 的 2.0.0 线排除清单已随首轮同步删除；**不在 CI 矩阵**（手工/门禁脚本发布）；自 **2.0.0-beta 起纳入正式发布**（与 v26_1_2 同步发行，勿当测试平台对待）。测试流程与发布门禁见 `docs/TESTING-FORGE-2612.md`。
- **正式发布模块 `forge_26_2`**（MinecraftForge 26.2-65.1.1，Java 25，2026-08-14 首建）：注册在 `settings.gradle`（`-Pactive_versions=forge-26.2`）。**2.0.0 线已追平**：以 `forge_26_1_2`（1.0.6/2.0.0 同步线）为基准整模块迁移，经 `scripts/port-forge-262.py` 机械移植（包名 `forge_26_1_2 → forge_26_2` + Forge 26.1.2→26.2 API 映射）+ 手工适配（`Options.hideGui` 移除 → `utils/HudVisibility`、`setScreen` → `setScreenAndShow`、advancement 包迁移、PIP 渲染器保持 **Forge 欧拉角方案**——`event.register(new Icon3DRenderer())` + `getRenderState().addPicturesInPictureState`，与 NeoForge 26.2 的 Quat/Supplier 方案不同），`build.gradle` 的 2.0.0 线排除清单已删除，`PlatformSmokeTest` 已改为断言 2.0.0 物品存在。**不在 CI 矩阵**、不参与 3 平台镜像纪律与 AnimRenderOps 漂移门禁（与 `forge_26_1_2` 同策略）。2026-08-18 全面审计确认：`test-forge-262.sh` 7/7 PASS，5 平台 `clean compileJava` 全通过，版本四同步 OK，资源一致性已补齐（4 个物品定义从 `forge_26_1_2` 补入）。测试流程与迁移记录见 `docs/TESTING-FORGE-262.md`。
- **正式发布模块 `forge_1_20_1`**（MinecraftForge 1.20.1-47.4.22，Java 17，ForgeGradle 7.x，2026-08-18 首建）：注册在 `settings.gradle`（`-Pactive_versions=forge-1.20.1`）。2.0.0 线功能向 MC 1.20.1 的回移，以 `forge_26_1_2` 为基准、复用 `common/` 全部纯 Java 逻辑；三大重写区域：Networking 改 `SimpleChannel`（14 packet）、Capability 走 `LazyOptional` + `AttachCapabilitiesEvent`、渲染层 `GuiGraphics` 直调（**无 PIP 系统**，普通物品 `AnimRenderOps.renderItem3D` 降级 2D、`supports3D()` 返回 false，但 **TACZ 枪械经 `renderGunModel3D` 全 3D**——`RenderSystem.getModelViewStack()` 在 1.20.1 返回 `PoseStack`，用 `pushPose`+`mulPoseMatrix` 而非 26.x 的 Matrix4fStack 方案）；DataComponent 存储回退 ItemStack NBT，`StreamCodec`/`RegistryFriendlyByteBuf` 改 `FriendlyByteBuf` 手动序列化。TACZ 检视（`TaczInspectViewport`+`BoxItemCodec.validateTacz`）与 TACZ 依赖同 `v1_21_1` 机制（`scripts/download-tacz-1201.sh`，gun tag 读顶层 ItemStack NBT）。JEI 未实现（依赖已声明）。**不在 CI 矩阵**、不参与 3 平台镜像纪律与 AnimRenderOps 漂移门禁（与其他 forge 模块同策略），自 **2.0.0-beta 起纳入正式发布**。测试流程见 `docs/TESTING-FORGE-1201.md`，迁移计划见 `.opencode/plans/2026-08-18-forge-1-20-1-port.md`。

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

## 关键文件

- `CsgoBox.java` — 平台入口；`CONFIG` 为 `public static final`（static 块初始化，勿改顺序）；`registerDynamicBoxItems` 注册 `config/csbox/*.json` 动态 item（用 `RegisterEvent` deferred supplier，**不要**用 `FMLCommonSetupEvent.enqueueWork`——registry 已 freeze）
- `CsboxConfig.java` — NeoForge `ModConfigSpec`，builder 每个 `define*` 用 `.get()`；`bulkOpenCount`（0=无上限）服务端权威
- `packet/PacketCsgoProgress.java` — 服务端权威 RNG + `OPEN_BLOCKED_UNTIL_TICK`（ConcurrentHashMap，`tickOpenBlockMap` 每 100 tick 清理）
- `packet/PacketCsgoBulkProgress.java` — 批量开箱（异步线程池 `BULK_COMPUTE_POOL` + 主线程 finalize）
- `gui/CsboxBulkOverviewScreen.java` — 批量开箱总览屏（Shift+右键进入；点「开启」直接发包 `PacketCsgoBulkProgress` 并进 `CsboxProgressScreen`，无二次确认屏；服务端权威复核库存与扣减）
- `common/box/BoxDefaults.java` — 教程下载（`writeTutorialIfMissing` + `refreshTutorials`，版本不匹配时按 `^_tutorial_v.*\.md$` 白名单直接删除旧版教程，无回收站）
- `common/box/BoxGrades.java` / `BoxRegistryStore.java` / `BoxStripGenerator.java` — 等级常量与纯函数 / 泛型注册表容器（回调契约固化）/ 泛型开箱滚动条（2026-08 重构下沉，平台 `BoxDefinition`/`BoxRegistry`/packet 引用指向 common）
- `common/logic/OpenBlockGuard.java` — 服务端权威开箱冷却（10 tick，`isBlocked`/`block`/`tick`），四平台 packet 与 `ModEvents#serverTick` 共用
- `common/config/CsboxConfigDefaults.java` — 四平台 `CsboxConfig` 默认值与取值范围唯一来源（枚举默认以常量名字符串存储）
- `utils/AnimRenderOps.java` — **动画渲染唯一适配点**（各平台一份，`// era: legacy|decoupled` 头标注）：屏与逻辑助手只经它调用渲染原语（`blitTextured`×3 变体 / `fill` / `fillGradient` / `scissor` / `scissorDisable` / `setBlendNormal` / `flush` / `renderBlurredBackground` / `renderItem2D` / `renderItem3D` / `supports3D`，共 13 个公开 op）。跨平台签名一致性由 `scripts/check-animops-drift.sh` 守护（CI `common-test` job 已接线）。**新增原语须三平台同步补**，否则漂移检查失败
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

- `common` 有 JUnit 5（`BoxJsonSchemaValidatorTest` 24 用例）：`./gradlew :common:test`（CI 独立 `common-test` job 跑一次，不再随各平台矩阵重复执行）
- `common` 架构约束检查由 `:common:checkCommonArchitecture` 自动挂载在编译上（见「架构约束」节）
- AnimRenderOps 跨平台签名漂移检查：`scripts/check-animops-drift.sh`（3 平台，CI 已接线，本地改门面后必跑）
- 平台层最小测试：`v26_1_2` / `v26_2` / `forge_26_1_2` / `forge_26_2` 均有 `PlatformSmokeTest`（JUnit 5，验证入口类可加载，不初始化 MC 运行时）：`./gradlew :<module>:test -Pactive_versions=<v>`
- 其余平台暂无自动化测试；运行时回归清单见 `docs/RELEASE.md` 质量门
- **代码审查标准与流程见 `docs/CODE-REVIEW.md`**（专属审查清单：CONSTRAINT-001 / 镜像纪律 / 版本四同步 / AnimRenderOps 漂移 / 并发权威等）；PR 描述模板 `.github/PULL_REQUEST_TEMPLATE.md` 由 CI `pr-checks.yml` 校验；GameTest 集成测试 CI 见 `gametest.yml`（当前无用例时跳过）；分支保护设置见 `docs/CI-PROTECTION.md`

### 平台 Java 文件差异矩阵（2026-08-18 审计）

| 文件 | v1_21_1 | v26_1_2 | v26_2 | forge_26_1_2 | forge_26_2 | forge_1_20_1 |
|------|:-------:|:-------:|:-----:|:------------:|:----------:|:------------:|
| TACZ compat (2 文件) | ✅ | — | — | — | — | ✅ |
| `ButtonPalette` | — | ✅ | ✅ | — | — | ✅ |
| `HudVisibility` | — | — | ✅ | — | ✅ | — |
| JEI (4 文件) | 3 文件 | ✅ | ✅ | ❌ | ❌ | ❌ |
| `PacketSyncBoxDefinitions` | — | ✅ | ✅ | — | — | — |
| `Networking`（Forge 专用） | — | — | — | ✅ | ✅ | ✅ |
| **文件数** | **80** | **79** | **80** | **75** | **76** | **77** |

- TACZ：`v1_21_1`（unofficial 1.21.1 port，`scripts/download-tacz.sh`）与 `forge_1_20_1`（official 1.20.1，`scripts/download-tacz-1201.sh`）有 `compileOnly` 依赖，其它平台不需要；`forge_1_20_1` 的 gun NBT 在 ItemStack 顶层 tag（无 DataComponent 系统），`BoxItemCodec.validateTacz` 直接读写 `stack.getTag()`，枪 tag 的 `GunFireMode` 规范化在内联修正
- ButtonPalette：`v26_1_2`/`v26_2` 的 26.x 辅助类，Forge 侧未移植（非功能阻塞）
- JEI：NeoForge 3 平台已同步；**Forge 2 平台均缺失**（已知待办，Modrinth 无 JEI 26.x Forge 构建）
- Networking vs PacketSyncBoxDefinitions：Forge 用 `SimpleChannel`，NeoForge 用 `CustomPacketPayload`，平台差异正常；`forge_1_20_1` 同为 `SimpleChannel`（Forge 47.x API）
- Tutorial/Validator：`TutorialSources` / `TutorialFetcher` / `BoxJsonSchemaValidator` 全部走 `common/` 唯一实现（六平台共用，无平台本地副本）；教程下载六平台统一为后台线程异步执行
- **代码审查标准与流程见 `docs/CODE-REVIEW.md`**（专属审查清单：CONSTRAINT-001 / 镜像纪律 / 版本四同步 / AnimRenderOps 漂移 / 并发权威等）；PR 描述模板 `.github/PULL_REQUEST_TEMPLATE.md` 由 CI `pr-checks.yml` 校验；GameTest 集成测试 CI 见 `gametest.yml`（当前无用例时跳过）；分支保护设置见 `docs/CI-PROTECTION.md`
