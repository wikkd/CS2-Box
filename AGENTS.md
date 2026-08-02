# CS2-Box — Agent 指南

## 构建

```bash
./gradlew :<module>:compileJava -Pactive_versions=<v>  # 快速编译单个平台
./gradlew :<module>:jar -Pactive_versions=<v>          # 打包
./gradlew :common:test                                 # common 单元测试 (JUnit 5)
```

- **Java**: 21（legacy 平台）/ 25 + `--enable-preview`（v26_1_2/v26_2）。toolchain 由各模块 build.gradle 指定。
- **每次 Gradle 调用只能构建一个 MC 版本**（NeoGradle userdev IDEA 扩展冲突，历史限制）。用 `-Pactive_versions=<v>` 覆盖 `gradle.properties` 的默认值（当前默认 26.1.2）。
- **9 个平台模块**：`v1_21_1` / `v1_21_3` / `v1_21_4` / `v1_21_5` / `v1_21_8` / `v1_21_10` / `v1_21_11`（NeoForge 21.x，旧 API）+ `v26_1_2` / `v26_2`（NeoForge 26.x，decoupled API）。

## 架构约束（CONSTRAINT-001）

- **`common/` 不得 import 任何 `net.minecraft.*` / `net.neoforged.*`**（编译环境无 MC classpath，违反即编译失败）。共享资源（纹理/音效/lang/配方/成就）也放在 `common/src/main/resources/`。
- 平台模块通过 `srcDir project(':common').file('src/main/resources')` 共享资源。
- 依赖方向：`平台 → common`，`common` 不依赖任何平台。

## 平台模块镜像纪律（重要！）

9 个平台模块**不是纯拷贝**：1.21.3+ 与 26.2 各自有 API 适配（如 `BuiltInRegistries.ITEM.get()` 返回 Optional、`spawnAtLocation(ServerLevel,...)`、`lookup()`、`MouseButtonEvent` 事件、`setScreenAndShow`、PIP 渲染器等）。**禁止用 `v1_21_1`/`v26_1_2` 整文件覆盖其他模块**——会破坏适配（历史教训，曾导致 v1_21_10 编译失败）。

跨平台改动的正确姿势：

1. 先改基准模块：legacy 用 `v1_21_1`，new 用 `v26_1_2`
2. `scripts/mirror.sh legacy|new|all <rel-path>` — 仅用于**无适配差异**的纯新增文件
3. 有适配差异的文件用**定点合入**（`scripts/merge-cooldown-fix.py` 是幂等合入脚本的范例），或用 `scripts/port-12111.py` 做规则化转换
4. 每平台 `compileJava` 验证（增量缓存可能造假象——**改动涉及平台时用 `clean` 编译确认**）

## 版本号管理（升级时四处同步）

`gradle.properties` 的 `mod_version=` + `neoforge.mods.toml`（模板变量 `${mod_version}` 自动注入）+ `CHANGELOG.md` + `README.md`。发布流程见 `docs/RELEASE.md`。

## 关键文件

- `CsgoBox.java` — 平台入口；`CONFIG` 为 `public static final`（static 块初始化，勿改顺序）；`registerDynamicBoxItems` 注册 `config/csbox/*.json` 动态 item（用 `RegisterEvent` deferred supplier，**不要**用 `FMLCommonSetupEvent.enqueueWork`——registry 已 freeze）
- `CsboxConfig.java` — NeoForge `ModConfigSpec`，builder 每个 `define*` 用 `.get()`；`bulkOpenCount`（0=无上限）服务端权威
- `packet/PacketCsgoProgress.java` — 服务端权威 RNG + `OPEN_BLOCKED_UNTIL_TICK`（ConcurrentHashMap，`tickOpenBlockMap` 每 100 tick 清理）
- `packet/PacketCsgoBulkProgress.java` — 批量开箱（异步线程池 `BULK_COMPUTE_POOL` + 主线程 finalize）
- `gui/CsboxConfirmScreen.java` — 批量开箱二次确认屏（总览屏 → 确认屏 → 发包）
- `common/box/BoxDefaults.java` — 教程下载（`writeTutorialIfMissing` + `refreshTutorials`，版本不匹配时按 `^_tutorial_v.*\.md$` 白名单直接删除旧版教程，无回收站）
- `utils/IconListTools.java` — 2D 物品网格（26.x/1.21.8+ 有 per-item bounding box 居中）
- `utils/HudVisibility.java`（仅 v26_2）— 26.2 无 `Options.hideGui`，用 `Minecraft.gui.hud.toggle()/isHidden()` 包装
- `common/utils/` — `ColorTools` / `OverlayColor`（三档 token：surface/panel/divider）/ `GuiRegion`（容器化布局）/ `EntityChineseMap`
- `advancement/OpenedBoxTrigger.java` — `csgobox:opened_box` trigger + `Stats.CUSTOM` 累加
- `event/BoxOpenedEvent.java` — NeoForge 事件总线开箱通知（post-event，KubeJS 兼容，见 `docs/KUBEJS-EVENTS.md`）

## 配方

`common/src/main/resources/data/csgobox/recipe/`（单数 `recipe`）。`csgo_key3` 仅锻造台（`smithing_transform`）。

## 测试

- `common` 有 JUnit 5（`BoxJsonSchemaValidatorTest` 24 用例）：`./gradlew :common:test`
- 平台层无自动化测试；运行时回归清单见 `docs/RELEASE.md` 质量门
