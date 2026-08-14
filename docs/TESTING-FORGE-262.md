# forge_26_2 (MinecraftForge 26.2) 测试流程（新模块，2026-08-14 首建）

> 适用范围：`forge_26_2` 平台模块 = **MinecraftForge 26.2-65.1.1**（Java 25，
> ForgeGradle 7.0.34）。基于 `forge_26_1_2` 当前源（**1.0.7/1.0.8 开发线**，含
> KubeJS 事件三件套、模糊增强、`GradeMapCache`/`BoxStripGenerator` 重构、批量开箱
> 恢复管线等）整模块迁移，只应用 **MC 26.1.2 → 26.2 编译必需的 API 适配**，
> 行为与 `forge_26_1_2` 保持特性同步（不做 v26_2 特性漂移合入，见 §2）。与其余
> 平台版本**隔离**：默认
> `active_versions=26.1.2` 不变、不进 CI 矩阵、不参与 3 平台镜像纪律与
> AnimRenderOps 漂移门禁（与 forge_26_1_2 同策略）。

## 1. 版本与来源

| 项 | 值 |
|---|---|
| MinecraftForge | `26.2-65.1.1`（2026-08-09，65.x 线最新；maven 元数据核对日期 2026-08-14） |
| ForgeGradle | `[7.0.17,8)` → 解析 7.0.34（与 forge_26_1_2 同一动态区间；7.0.31 起即可解析 26.2 userdev，实测 7.0.34 配置通过） |
| 迁移基准 | `forge_26_1_2` 全部 tracked 源（77 Java + resources + PlatformSmokeTest，**1.0.7/1.0.8 开发线**），包重命名 `forge_26_1_2 → forge_26_2` |
| 26.2 适配来源 | `v26_2`（NeoForge 26.2）相对 `v26_1_2` 的归一化 diff（`scripts/` 无此工具，人工比对） |

## 2. 26.1.2 → 26.2 编译必需适配（已应用）

| # | 文件 | 改动 |
|---|---|---|
| A | `utils/HudVisibility.java`（新增） | MC 26.2 删除 `Options.hideGui`；包装 `Minecraft.gui.hud.toggle()/isHidden()`（自 v26_2 移植，纯 Mojang API，直接可用） |
| B | `gui/CsboxScreen.java` / `CsboxProgressScreen.java` / `CsLookItemScreen.java` | 全部 10 处 `options.hideGui = true/false` → `HudVisibility.hide()/show()`（原 `if (this.minecraft != null)` 守卫由包装器内部空判取代） |
| C | `gui/BoxScreenOpener.java` / `CsboxConfirmScreen.java` / `CsboxBulkOverviewScreen.java` / `CsboxScreen.java` / `CsboxProgressScreen.java` | 全部 8 处 `setScreen(...)` → `setScreenAndShow(...)`（26.2 重命名，镜像 v26_2） |
| D | `advancement/ModLoadedTrigger.java` / `OpenedBoxTrigger.java` | import 包迁移 `advancements.criterion.*` → `advancements.predicates.*` / `advancements.triggers.*`；`trigger(player, ...)` lambda 需显式 `Predicate<TriggerInstance>` 转型（26.2 重载解析变化） |
| E | `gui/pip/Icon3DRenderState.java` | 删除 `pose()` override 与 `Matrix3x2f` import（26.2 接口自带默认实现） |
| F | `gui/pip/Icon3DRenderer.java` | 26.2 API：父类 `PictureInPictureRenderer` 变为注解式（无 BufferSource 构造器）；`renderToTexture(renderState, poseStack, SubmitNodeCollector)`；`gameRenderer.lighting()` 取代 `getLighting()`；不再自调 `FeatureRenderDispatcher.renderAllFeatures()`（父类负责）。**保留 euler 旋转 API**（forge_26_1_2 侧既有 Quat 漂移不随本次迁移修正） |
| G | `CsgoBox.java` | `RegisterPictureInPictureRendererEvent.register(...)` 改为无参构造实例注册（26.2 事件 API：`register(PictureInPictureRenderer<?>)`，无 BufferSource，事件自带静态 BUS） |

**未合入的 v26_2 特性漂移**（非编译必需，行为与 `forge_26_1_2` 当前 1.0.8 线保持一致）：
CsboxScreen 翻页动画删除、CsboxBulkResultScreen 墙钟计时/`LIFE_TICKS` 变更、
TerminalBootScreen/TerminalScreen 淡入淡出、PacketCsgoProgress `instanceof` 重构、
PacketBoxBulkResult 重命名、ButtonPalette `DISABLED` 删除、动态箱子 `stacksTo(16)`
移除等。后续如需对齐 v26_2 特性线，按「镜像纪律」定点合入。

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
| S5 | `:forge_26_2:test` JUnit 全绿（PlatformSmokeTest，入口可加载 + 1.0.7/1.0.8 基线字段守卫） |

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
（forge_26_2 为 1.0.7/1.0.8 开发线基线：**F5 批量开箱可用**——1.0.7 线已恢复，
本模块已含该特性线）。mc_tools 黑盒 E2E 需 TestHelper 增加 forge-26.2 构建目标
（当前仅 forge-26.1.2，见 TESTING-FORGE-2612.md §5.3），暂不可用。

## 6. 发布门禁对照

- [ ] L0-L3 全绿（`./scripts/test-forge-262.sh`）
- [ ] L4 关键路径 F1-F4
- [ ] 版本四同步
- [ ] `mods.toml` 区间：forge `[65,)`、MC `[26.2,26.3)`、pack_format 81

> 状态（2026-08-14）：模块首建完成（调研 + 环境 + 迁移 + 隔离），L0-L3 门禁
> 待首次跑通记录；L4 待人工。已提交 git（源码，build/`run/` 不入库）。

## 7. 已知差异与注意

- forge_26_2 的 Icon3DRenderState/Icon3DRenderer 沿用 forge_26_1_2 的 **euler 旋转**
  API（`rotXDeg/rotYDeg/rotZDeg`），与 NeoForge 线的 Quat API 不一致——这是
  forge_26_1_2 侧既有漂移（手工适配文件未合入 Quat 迁移），非本次引入；如需
  对齐需在两条 forge 线一并处理。
- 首个 `runClient` 前先删 `forge_26_2/run/`（首启生成），避免脏配置。
