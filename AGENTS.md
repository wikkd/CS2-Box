# CS2-Box — Agent 指南

## 构建

```bash
./gradlew :<module>:compileJava -Pactive_versions=<v>  # 快速编译单个平台
./gradlew :<module>:jar -Pactive_versions=<v>          # 打包
./gradlew :common:test                                 # common 单元测试 (JUnit 5)
```

- **Java**: 21（legacy 平台）/ 25 + `--enable-preview`（v26_1_2/v26_2）。toolchain 由各模块 build.gradle 指定。
- **每次 Gradle 调用只能构建一个 MC 版本**（NeoGradle userdev IDEA 扩展冲突，历史限制）。用 `-Pactive_versions=<v>` 覆盖 `gradle.properties` 的默认值（当前默认 26.1.2）。
- **NeoGradle 全平台统一 7.1.38**（含 v1_21_1/3/4/5——曾用 7.0.171，与 Gradle wrapper 9.5.1 配置阶段不兼容已升级）。wrapper 9.5.1 满足全部模块（forge_26_1_2 的 ForgeGradle 7 要求 ≥9.3）。
- **3 个平台模块**：`v1_21_1`（NeoForge 21.x，旧 API）+ `v26_1_2` / `v26_2`（NeoForge 26.x，decoupled API）。**已归档（EOL）平台** `v1_21_0` / `v1_21_3` / `v1_21_4` / `v1_21_5` / `v1_21_8` / `v1_21_10` / `v1_21_11` 于 2026-08-09 从仓库删除，最后状态在 tag `eol-legacy-21x-1.0.6`，复活需从该 tag 检出。
- **v1_21_1 有 compileOnly TACZ 依赖**（永恒枪械工坊：零，检视视口集成）：jar 不入库（~57MB，仓库惯例 `*.jar` 全局忽略、只提交 pom），首次构建前运行 `scripts/download-tacz.sh` 填充 `local-repo/com/tacz/` 并从 jarjar 提取编译所需的 `simplebedrockmodel`（CI 自动执行）。运行时经 `ModList.isLoaded("tacz")` 检测，无 TACZ 环境功能静默降级。
- **实验模块 `forge_26_1_2`**（MinecraftForge 26.1.2-64.1.0，Java 25）：已注册在 `settings.gradle`（`-Pactive_versions=forge-26.1.2`），源码**未提交**（本地 WIP），**不在 CI 矩阵**，不参与 mirror/镜像纪律；内容由 `scripts/port-forge-2612.py` 从 v26_1_2 机械转换 + 手工适配。勿误当正式平台发布。

## 架构约束（CONSTRAINT-001）

- **`common/` 不得 import 任何 `net.minecraft.*` / `net.neoforged.*`**（编译环境无 MC classpath，违反即编译失败）。共享资源（纹理/音效/lang/配方/成就）也放在 `common/src/main/resources/`。该约束由 `:common:checkCommonArchitecture` Gradle task 自动化执行（挂载在 `compileJava` 依赖上，任何编译/测试都会触发，含 forge_26_1_2 把 common 源码编进自身 classpath 的场景）。
- 平台模块通过 `srcDir project(':common').file('src/main/resources')` 共享资源。
- 依赖方向：`平台 → common`，`common` 不依赖任何平台。

## 平台模块镜像纪律（重要！）

3 个平台模块**不是纯拷贝**：26.2 有 API 适配（如 `BuiltInRegistries.ITEM.get()` 返回 Optional、`spawnAtLocation(ServerLevel,...)`、`lookup()`、`MouseButtonEvent` 事件、`setScreenAndShow`、PIP 渲染器等）。**禁止用 `v26_1_2` 整文件覆盖 `v26_2`**——会破坏适配（历史教训，曾导致 v1_21_10 编译失败；该平台现已与其余 legacy 一并归档）。

跨平台改动的正确姿势：

1. 先改基准模块：new 用 `v26_1_2`（legacy 唯一模块 `v1_21_1` 直接改）
2. `scripts/mirror.sh new <rel-path>` — 仅用于**无适配差异**的纯新增文件（目标已存在会警告跳过，`--force` 覆盖，`--dry-run` 预演不写盘）
3. 有适配差异的文件用**定点合入**（`v26_1_2` → `v26_2` 手工适配；幂等合入脚本范例与 `scripts/port-12111.py` 已随 EOL 平台删除）
4. 每平台 `compileJava` 验证（增量缓存可能造假象——**改动涉及平台时用 `clean` 编译确认**）

## 版本号管理（升级时四处同步）

`gradle.properties` 的 `mod_version=` + `neoforge.mods.toml`（模板变量 `${mod_version}` 自动注入）+ `CHANGELOG.md` + `README.md`。发布流程见 `docs/RELEASE.md`。一致性检查：`scripts/check-version.sh`（CI 的 `common-test` job 已接入）。

## 关键文件

- `CsgoBox.java` — 平台入口；`CONFIG` 为 `public static final`（static 块初始化，勿改顺序）；`registerDynamicBoxItems` 注册 `config/csbox/*.json` 动态 item（用 `RegisterEvent` deferred supplier，**不要**用 `FMLCommonSetupEvent.enqueueWork`——registry 已 freeze）
- `CsboxConfig.java` — NeoForge `ModConfigSpec`，builder 每个 `define*` 用 `.get()`；`bulkOpenCount`（0=无上限）服务端权威
- `packet/PacketCsgoProgress.java` — 服务端权威 RNG + `OPEN_BLOCKED_UNTIL_TICK`（ConcurrentHashMap，`tickOpenBlockMap` 每 100 tick 清理）
- `packet/PacketCsgoBulkProgress.java` — 批量开箱（异步线程池 `BULK_COMPUTE_POOL` + 主线程 finalize）
- `gui/CsboxConfirmScreen.java` — 批量开箱二次确认屏（总览屏 → 确认屏 → 发包）
- `common/box/BoxDefaults.java` — 教程下载（`writeTutorialIfMissing` + `refreshTutorials`，版本不匹配时按 `^_tutorial_v.*\.md$` 白名单直接删除旧版教程，无回收站）
- `utils/AnimRenderOps.java` — **动画渲染唯一适配点**（各平台一份，`// era: legacy|decoupled` 头标注）：屏与逻辑助手只经它调用渲染原语（`blitTextured`×3 变体 / `fill` / `fillGradient` / `scissor` / `scissorDisable` / `setBlendNormal` / `flush` / `renderBlurredBackground` / `renderItem2D` / `renderItem3D` / `supports3D`，共 13 个公开 op）。跨平台签名一致性由 `scripts/check-animops-drift.sh` 守护（CI `common-test` job 已接线）。**新增原语须三平台同步补**，否则漂移检查失败
- `utils/IconListTools.java` — 2D 物品网格（26.x/1.21.8+ 有 per-item bounding box 居中；渲染原语已委托 AnimRenderOps）
- `utils/GuiItemMove.java` — 3D 拖拽预览（`renderRotAngleX/Y` 纯数学保留，渲染委托 `AnimRenderOps.renderItem3D`）
- `utils/HudVisibility.java`（仅 v26_2）— 26.2 无 `Options.hideGui`，用 `Minecraft.gui.hud.toggle()/isHidden()` 包装
- `common/utils/` — `ColorTools` / `OverlayColor`（三档 token：surface/panel/divider）/ `GuiRegion`（容器化布局）/ `EntityChineseMap`
- `advancement/OpenedBoxTrigger.java` — `csgobox:opened_box` trigger + `Stats.CUSTOM` 累加
- `event/BoxOpenedEvent.java` — NeoForge 事件总线开箱通知（post-event，KubeJS 兼容，见 `docs/KUBEJS-EVENTS.md`）

## 配方

`common/src/main/resources/data/csgobox/recipe/`（单数 `recipe`）。`csgo_key3` 仅锻造台（`smithing_transform`）。

## 测试

- `common` 有 JUnit 5（`BoxJsonSchemaValidatorTest` 24 用例）：`./gradlew :common:test`（CI 独立 `common-test` job 跑一次，不再随各平台矩阵重复执行）
- `common` 架构约束检查由 `:common:checkCommonArchitecture` 自动挂载在编译上（见「架构约束」节）
- AnimRenderOps 跨平台签名漂移检查：`scripts/check-animops-drift.sh`（3 平台，CI 已接线，本地改门面后必跑）
- 平台层最小测试：`v26_1_2` 有 `PlatformSmokeTest`（JUnit 5，验证入口类可加载，不初始化 MC 运行时）：`./gradlew :v26_1_2:test -Pactive_versions=26.1.2`
- 其余平台暂无自动化测试；运行时回归清单见 `docs/RELEASE.md` 质量门
- **代码审查标准与流程见 `docs/CODE-REVIEW.md`**（专属审查清单：CONSTRAINT-001 / 镜像纪律 / 版本四同步 / AnimRenderOps 漂移 / 并发权威等）；PR 描述模板 `.github/PULL_REQUEST_TEMPLATE.md` 由 CI `pr-checks.yml` 校验；GameTest 集成测试 CI 见 `gametest.yml`（当前无用例时跳过）；分支保护设置见 `docs/CI-PROTECTION.md`
