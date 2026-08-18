# forge_26_2 (MinecraftForge 26.2) 测试流程（新模块，2026-08-14 首建）

> 适用范围：`forge_26_2` 平台模块 = **MinecraftForge 26.2-65.1.1**（Java 25，
> ForgeGradle 7.0.34）。基于 `forge_26_1_2`（1.0.6/1.0.7 同步线）整模块迁移，
> **1.0.7 线已追平**（2026-08-18 全面审计确认）。与其余平台版本**隔离**：默认
> `active_versions=26.1.2` 不变、不进 CI 矩阵、不参与 3 平台镜像纪律与
> AnimRenderOps 漂移门禁（与 forge_26_1_2 同策略）。

## 1. 版本与来源

| 项 | 值 |
|---|---|
| MinecraftForge | `26.2-65.1.1`（2026-08-09，65.x 线最新；maven 元数据核对日期 2026-08-14） |
| ForgeGradle | `[7.0.17,8)` → 解析 7.0.34（与 forge_26_1_2 同一动态区间；7.0.31 起即可解析 26.2 userdev，实测 7.0.34 配置通过） |
| 迁移基准 | `forge_26_1_2` 1.0.6 发行基线（提交 `2a7251a` 的 55 文件，含 build.gradle 排除清单；Java 源 + resources + PlatformSmokeTest），包重命名 `forge_26_1_2 → forge_26_2` |
| 26.2 适配来源 | `v26_2`（NeoForge 26.2）相对 `v26_1_2` 的归一化 diff（`scripts/` 无此工具，人工比对） |

## 2. 26.1.2 → 26.2 编译必需适配（已应用）

| # | 文件 | 改动 |
|---|---|---|
| A | `utils/HudVisibility.java`（新增） | MC 26.2 删除 `Options.hideGui`；包装 `Minecraft.gui.hud.toggle()/isHidden()`（自 v26_2 移植，纯 Mojang API，直接可用） |
| B | `gui/CsboxScreen.java` / `CsboxProgressScreen.java` / `CsLookItemScreen.java` | 全部 10 处 `options.hideGui = true/false` → `HudVisibility.hide()/show()`（原 `if (this.minecraft != null)` 守卫由包装器内部空判取代） |
| C | `gui/BoxScreenOpener.java` / `CsboxConfirmScreen.java` / `CsboxBulkOverviewScreen.java` / `CsboxScreen.java` / `CsboxProgressScreen.java` | 全部 8 处 `setScreen(...)` → `setScreenAndShow(...)`（26.2 重命名，镜像 v26_2） |
| D | `advancement/ModLoadedTrigger.java` / `OpenedBoxTrigger.java` | import 包迁移 `advancements.criterion.*` → `advancements.predicates.*` / `advancements.triggers.*`；`trigger(player, ...)` lambda 需显式 `Predicate<TriggerInstance>` 转型（26.2 重载解析变化） |
| E | `gui/pip/Icon3DRenderState.java` | 删除 `pose()` override 与 `Matrix3x2f` import（26.2 接口自带默认实现） |
| F | `gui/pip/Icon3DRenderer.java` | 26.2 API：父类 `PictureInPictureRenderer` 变为注解式（无 BufferSource 构造器）；`renderToTexture(renderState, poseStack, SubmitNodeCollector)`；`gameRenderer.lighting()` 取代 `getLighting()`；不再自调 `FeatureRenderDispatcher.renderAllFeatures()`（父类负责）。**保留 1.0.6 euler 旋转 API**（`rotXDeg/rotYDeg/rotZDeg`，不引入 1.0.7 的 Quat 漂移） |
| G | `CsgoBox.java` | `RegisterPictureInPictureRendererEvent.register(...)` 改为无参构造实例注册（26.2 事件 API：`register(PictureInPictureRenderer<?>)`，无 BufferSource，事件自带静态 BUS） |

**已合入的 1.0.7 特性**（2026-08-18 追平）：
终端机（terminal 物品/屏/会话/倒计时）、军火商与武库拆解台（block/menu/villager/recipe）、
KubeJS 事件三件套（BoxOpeningEvent/TerminalBuyEvent/ArmoryRecycleEvent）、模糊增强
（ScreenBlurBoost）、批量开箱恢复、`AnimRenderOps` 渲染门面、`HudVisibility`、
教程系统（TutorialFetcher/TutorialSources/BoxJsonSchemaValidator）等全部 1.0.7 增量
已从 `forge_26_1_2` 同步。PIP 渲染器保持 **Forge 欧拉角方案**（与 NeoForge 的 Quat/Supplier
方案不同，有意保持）。

## 3. 自动化门禁（L0-L3）

```bash
./scripts/test-forge-262.sh          # 全量（clean+compile / jar / 版本同步 / 漂移 / 冒烟）
```

| 阶段 | 通过条件 |
|---|---|
| S1 | `:forge_26_2:clean :forge_26_2:compileJava -Pactive_versions=forge-26.2` exit 0 |
| S2 | jar 存在非空、`csgobox-forge-26.2-<ver>.jar`、mods.toml `version="<ver>"` 已展开 |
| S3 | `scripts/check-version.sh`（版本四同步；forge_26_2 的 mods.toml 自动纳入模板校验） |
| S4 | `scripts/check-animops-drift.sh`（3 NeoForge 平台；forge 按设计不在内） |
| S5 | `:forge_26_2:test` JUnit 全绿（PlatformSmokeTest，入口可加载 + 1.0.7 物品守卫：断言 ITEM_TERMINAL / ITEM_ARMORY_POINT 存在） |

## 4. 隔离性（与其他版本互不影响）

- `settings.gradle` 新增 `'forge-26.2': 'forge_26_2'` 映射；`gradle.properties`
  默认 `active_versions=26.1.2` **未改**；每次 Gradle 调用仍需显式
  `-Pactive_versions=forge-26.2`。
- `build.yml` / `pr-checks.yml` 矩阵不含任何 forge 模块（forge_26_1_2 同），
  本模块不触发 CI 变化。
- 共享资源/源码照常经 `sourceSets` 引用 `common`；CONSTRAINT-001 由
  `:common:checkCommonArchitecture` 随编译自动执行。
- 版本四同步（gradle.properties / CHANGELOG / README / mods.toml 模板）对
  forge_26_2 自动生效，无需额外接线。

## 5. 运行时 E2E（L4，待人工）

`./gradlew :forge_26_2:runClient -Pactive_versions=forge-26.2`（macOS 自动带
`-XstartOnFirstThread`）。用例清单沿用 docs/TESTING-FORGE-2612.md §5.2 F1-F12
（forge_26_2 为 1.0.6 基线：**F5 批量开箱预期不可用**——1.0.6 已屏蔽批量开箱，
1.0.7 线才恢复，本模块未合入该特性线）。mc_tools 黑盒 E2E 需 TestHelper 增加
forge-26.2 构建目标（当前仅 forge-26.1.2，见 TESTING-FORGE-2612.md §5.3），暂不可用。

## 6. 发布门禁对照

- [x] L0-L3 全绿（`./scripts/test-forge-262.sh`，PASS 7 / FAIL 0 / WARN 0，2026-08-18）
- [ ] L4 关键路径 F1-F4（待人工）
- [x] 版本四同步（`scripts/check-version.sh` 通过，2026-08-18）
- [x] `mods.toml` 区间：forge `[65,)`、MC `[26.2,26.3)`、pack_format 81

> 状态（2026-08-18）：**1.0.7 线已追平**。以 `forge_26_1_2` 为基准整模块迁移
>（`scripts/port-forge-262.py` + 手工适配），`build.gradle` 排除清单已删除，
> `PlatformSmokeTest` 断言 1.0.7 物品存在。2026-08-18 全面审计：5 平台
> `clean compileJava` 全通过、`test-forge-262.sh` 7/7 PASS、版本四同步 OK、
> 资源一致性补齐（4 个物品定义从 `forge_26_1_2` 补入）。jar 产物
> `csgobox-forge-26.2-1.0.6.jar`。

## 7. 已知差异与注意

- forge_26_2 的 Icon3DRenderState/Icon3DRenderer 保留 1.0.6 的 **euler 旋转** API
  （`rotXDeg/rotYDeg/rotZDeg`），与 NeoForge 线的 Quat API 不一致——这是有意保持的
  1.0.6 基线（Quat 是 1.0.7 才引入的漂移），追平 1.0.7+ 线时一并处理。
- 首个 `runClient` 前先删 `forge_26_2/run/`（首启生成），避免脏配置。
