# forge_26_1_2 (MinecraftForge) 测试流程（随 1.0.7 线同步开发）

> 适用范围：`forge_26_1_2` 平台模块，与 `v26_1_2` 基准**特性同步**（自 1.0.7 线起，
> 见 AGENTS.md「forge_26_1_2 同步」；1.0.6 基线发行记录见 §6 发布记录）。
> 设计对齐 `mc_tools`（`~/Desktop/mc_tools`）的测试理念（分层门禁 + 回归清单 +
> 统一报告/退出码），但**只依赖 CS2-Box 仓库自身**——forge 是 MinecraftForge
> loader，无法加载 NeoForge 版的 TestHelper MCP 辅助模组（详见 §7 路线）。

## 1. 目标与范围

forge_26_1_2 = **MinecraftForge 26.1.2-64.1.0**（Java 25，ForgeGradle 7），
与 NeoForge 平台（`v1_21_1` / `v26_1_2` / `v26_2`）是**不同 loader**。
本流程回答三个问题：

1. 这个版本**能不能编译、打包、自洽**（自动化门禁 L0-L3）；
2. 在真实 Forge 客户端里**功能是否正常**（运行时 E2E 清单 L4）；
3. 发布前**还差什么**（§6 发布门禁对照）。

现有测试设施对本模块的适用性：

| 设施 | 适用 forge？ | 说明 |
|---|---|---|
| `scripts/check-version.sh` | ✅ | 版本四同步（含 forge 的 `META-INF/mods.toml` 模板校验） |
| `scripts/check-animops-drift.sh` | ✅（旁路） | 只查 3 个 NeoForge 平台；forge 渲染门面**按设计不在内** |
| `:common:checkCommonArchitecture` | ✅ | 随任何 `compileJava` 自动触发 |
| `scripts/fullcheck/` + mc_tools MCP | ✅ | TestHelper 已新增 forge-26.1.2 构建目标（`src/main/java-forge`），L4 已可 MCP 黑盒自动化（见 §5.3） |
| `:forge_26_1_2:test`（本流程新增） | ✅ | JUnit 冒烟测试，不启动 MC 运行时 |

## 2. 测试分层

| 层 | 内容 | 方式 | 执行者 |
|---|---|---|---|
| L0 | clean 编译 + 架构约束 | `test-forge-2612.sh` S1 | 自动化 |
| L1 | jar 产物（文件名/非空/mods.toml 版本） | `test-forge-2612.sh` S2 | 自动化 |
| L2 | 一致性（版本四同步 / 渲染门面漂移） | `test-forge-2612.sh` S3-S4 | 自动化 |
| L3 | 平台层冒烟（入口可加载 + 1.0.7 同步物品断言） | `test-forge-2612.sh` S5 | 自动化 |
| L4 | 运行时 E2E（Forge 客户端） | §5 手动清单 | 人工 |

## 3. 前置条件

- JDK 25 toolchain（`forge_26_1_2/build.gradle` 已锁定，Gradle 自动下载/检测）；
- 仓库 Gradle wrapper 9.5.1（无需全局 Gradle）；
- **每次 Gradle 调用必须带 `-Pactive_versions=forge-26.1.2`**（每次只构建一个
  MC 版本是仓库历史限制；默认 `active_versions` 是 NeoForge 26.1.2，不带参数会
  构建错模块）；
- 首次构建需联网拉取 ForgeGradle/Forge 依赖（已构建过则走本地缓存）；
- 运行时 E2E 还需要：一份 Forge 26.1.2 客户端环境（`runClient` 首启自动生成
  `forge_26_1_2/run/`）、创造模式世界、`config/csbox/*.json` 箱子定义。

## 4. 自动化门禁（L0-L3）

```bash
./scripts/test-forge-2612.sh
```

阶段与判定：

| 阶段 | 命令 | 通过条件 |
|---|---|---|
| S1 | `./gradlew :forge_26_1_2:clean :forge_26_1_2:compileJava -Pactive_versions=forge-26.1.2` | exit 0（含 common 架构检查） |
| S2 | `./gradlew :forge_26_1_2:jar` + 产物校验 | jar 存在非空、文件名 `csgobox-forge-26.1.2-<ver>.jar`、`mods.toml` 内 `version="<ver>"` 已展开 |
| S3 | `scripts/check-version.sh` | 版本四同步 OK |
| S4 | `scripts/check-animops-drift.sh` | 3 平台渲染门面签名一致 |
| S5 | `./gradlew :forge_26_1_2:test` | JUnit 全绿（入口类可加载；`ITEM_PREMIUM_BOX`/`ITEM_ARMORY_POINT` 不泄漏；`terminal` 已转动态注册无静态字段） |

常用参数：`--skip-compile` / `--skip-jar` / `--skip-version` / `--skip-drift` /
`--skip-test`（调试单项时用）；`FORGE_TEST_TIMEOUT=3600` 覆盖 Gradle 阶段超时
（默认 1800s，首构/冷缓存可调大）。

输出与退出码（对齐 mc_tools 约定）：

- 控制台逐条 `PASS/FAIL/WARN` + 汇总；
- `build/test-reports/forge-2612.xml`（JUnit XML，可在 CI 直接消费）；
- 退出码 `0` = 全过，`1` = 有失败，`2` = 前置失败（环境/工具错误）。

## 5. 运行时 E2E 检查清单（L4，手动）

### 5.1 部署

```bash
./gradlew :forge_26_1_2:runClient -Pactive_versions=forge-26.1.2
```

> ✅ **run 任务已可用（2026-08-11 修正）**：此前「ForgeGradle 7 run 任务在
> Gradle 9 全挂」的结论系误判，真因是项目把 `settings.gradle` 的
> `foojay-resolver-convention` 锁在 0.9.0（引用 Gradle 9 已删除的
> `JvmVendorSpec.IBM_SEMERU`）；升级到 1.0.0（官方 MDK 同款）后
> `runClient`/`runServer`/`gameTestServer`/`runData` 全部可创建。
> 服务端已实测跑通（`runServer` 至 `Done`，配方/成就/动态箱子/命令正常）。

首次启动会生成 `forge_26_1_2/run/`（含 `config/`、`mods/`、`saves/`）。
如需把产物当正式环境验证，可另建 Forge 26.1.2 实例并把
`forge_26_1_2/build/libs/csgobox-forge-26.1.2-1.0.6.jar` 放入 `mods/`。
建议删掉旧的 `config/csgobox.toml` 与 `config/csbox/` 后再测，避免脏数据。

> **同步开发状态**：forge 模块自 1.0.7 线起与 `v26_1_2` 保持特性同步（同一
> `mod_version`，同步纪律见 AGENTS.md「forge_26_1_2 同步」）。同步进行中时，
> `forge_26_1_2/build.gradle` 对 common 资源/源码源集保留 1.0.7 线排除清单
> （`villager_trade`/`trade_set`/armory 配方与资产/terminal 资产与 Java 包等），
> 已同步的部分随进度删除排除项。漂移盘点：`scripts/port-forge-2612.py --dry-run`；
> 同步后必跑本文件 L0-L3 门禁。

### 5.2 用例（F1-F12）

环境：创造模式单人世界。命令速查：

| 命令 | 用途 |
|---|---|
| `/give @p csgobox:csgo_box 5` | 默认箱子（动态 item，按 `config/csbox/*.json` 文件名注册） |
| `/give @p csgobox:csgo_key0 10` | 0 级钥匙（开箱消耗 1 把） |
| `/give @p csgobox:csgo_key3 1` | 3 级钥匙（仅锻造台合成前置） |
| `/csbox help` / `/csbox list` / `/csbox info <box>` | 箱子信息 |
| `/csbox reload` / `/csbox tutorial refresh` / `/csbox errors` | 热重载/教程/错误上报 |
| `/advancement revoke @s everything` | 重置成就便于复测 |

| # | 用例 | 操作 → 预期 |
|---|---|---|
| F1 | 启动无异常 | `runClient` 日志无 `ERROR`/`Exception`；eventbus、registry 无冲突告警 |
| F2 | 动态箱子注册 | `/give @p csgobox:weapon_supply_box 5`（文件名与 `config/csbox/` 中 json 一致）→ 获得物品，图标**非紫黑** |
| F3 | 开箱主流程 | 手持箱子右键 → 开箱确认/进度屏 → 动画（CS2 风格滚动）→ 结果屏；无卡屏、无异常日志 |
| F4 | 消耗与产出 | 开箱后钥匙 `-1`；结果屏物品进入背包；关闭 GUI 回世界正常 |
| F5 | 批量开箱 | 配置 `bulkOpenCount`（如 10）→ Shift+右键 → 总览屏 → 确认屏 → 流水结果屏（1.0.7 线已恢复，随 forge 同步生效） |
| F6 | 配置热重载 | 修改 `config/csbox/*.json`（权重/分级）→ `/csbox reload` → `mc_status` 等价的开箱结果变化；`enableHotReload=true` 时文件改动自动生效 |
| F7 | 磨损耐久 | 开出有耐久物品 → 查看界面 `wear` 显示与实际扣损一致；`damageItemByWear=false` 时不扣 |
| F8 | 成就/统计 | 开箱后 `csgobox:opened_box` 自定义统计累计；成就页 CS2 Box 标签出现 |
| F9 | 教程下载 | 首次启动 `config/csbox/tutorials/` 生成教程 md；`/csbox tutorial refresh` 可重下 |
| F10 | 语言 | 中/英 locale 下 GUI、提示、物品名无乱码/无 key 原文 |
| F11 | 服务端权威 | 单机/联机下开箱结果由服务端 RNG 决定（日志 `[CS2 Box]` 输出结果与客户端一致） |
| F12 | 持久化 | 开箱所得物品重进世界仍在；`csgobox.toml` 配置项读回一致 |

关键路径（发布前必测）：**F1 → F2 → F3 → F4 → F5**，其余按改动面取舍。

### 5.3 mc_tools 黑盒自动化 E2E（2026-08-11 已跑通）

TestHelper 已提供 MinecraftForge 26.1.2 构建目标（`mc_tools` 的
`-Pactive_versions=forge-26.1.2`，源集 `src/main/java-forge`），forge 客户端内
可加载 MCP server；部署走 `deploy.sh --client forge_26_1_2`（或手动
`:forge_26_1_2:runClient`），套件用 `--port` 直连即可，无需改动用例。

```bash
# mc_tools 内
./gradlew jar -Pactive_versions=forge-26.1.2
MCP_PORT=41502 CSBOX_CLIENT=forge_26_1_2 ./scripts/deploy.sh --client forge_26_1_2
MCP_PORT=41502 ./scripts/enter_world.sh --port 41502
MCP_PORT=41502 CSBOX_CLIENT=forge-26.1.2 ./scripts/test_csbox.sh --skip-enter --port 41502
MCP_PORT=41502 python3 scripts/test_csbox_ext.py --skip-enter --port 41502
```

2026-08-11 实测（基线 1.0.6，端口 41502，`testhelper-forge-26.1.2-0.2.0.jar`）：

- `test_csbox.sh`：**11 通过 / 0 失败 / 0 警告**（T1-T9 全过）；
- `test_csbox_ext.py`：**21 通过 / 12 失败 / 1 警告**；
  - E1a/E1c FAIL = 真 bug：forge `/csbox info` 无参形式缺失（移植缺口，见报告 §5）；
  - E8a-f FAIL = 预期：1.0.6 基线屏蔽批量开箱（`ClickEvent.java` 中
    `shift=false`，1.0.7 恢复）；
  - E10a/E11a/b/d FAIL + E10d WARN = 视觉 OCR 误判（`gemma4:12b` 漏读，
    截图经复核内容正确）；
  - E2/E3/E4/E5/E6/E7/E9 全过。

详细甄别、截图存证与运行记录见
`docs/TEST-REPORT-FORGE-2612-2026-08-11.md`。

## 6. 发布门禁

对照 `docs/RELEASE.md` §3 质量门，forge 同步/发布前必须：

- [x] L0-L3 全绿（`./scripts/test-forge-2612.sh` 退出码 0）；
- [x] L4 关键路径 F1-F5 通过；
- [x] 版本四同步（S3 已自动覆盖）；
- [x] `mods.toml` 的 forge 版本区间 `[64,)`、MC 区间 `[26.1.2,26.2)` 与目标环境匹配。

> **发布记录（2026-08-12）**：L0-L3 7/7 PASS（clean 编译 / jar 校验 / 版本四同步 /
> 渲染门面漂移 / PlatformSmokeTest），L4 关键路径 11P/0F/0W（详见
> `docs/TEST-REPORT-FORGE-2612-2026-08-11.md`）；产物
> `csgobox-forge-26.1.2-1.0.6.jar` 校验通过（`assets/csgobox/items/` 5 个 1.0.6
> 基线物品定义、无 1.0.7 线资产泄漏）。模块随 **1.0.6** 纳入 git 管理并发行。

forge **不参与** 3 平台镜像纪律与 `build.yml` 构建矩阵（保持实验模块定位，
直到 §7 路线落地并决定转正）。

> **同步记录（2026-08-12，1.0.7 线首轮）**：机械移植 19 文件 + 手工适配
> （ModItems / ModBlocks / CsboxConfig / AnimRenderOps / IconListTools /
> ButtonPalette / GuiItemMove / PacketTerminalBuy / Networking 等）后
> `clean compileJava` 通过，L0-L3 门禁 7/7 PASS；`PlatformSmokeTest` 基线守卫
> 已更新为 1.0.7 同步断言（terminal / premium / armory 为预期项）。
>
> **同步记录（2026-08-12，1.0.7 线第二轮）**：Blur 软适配合入 forge 全部 6 屏
> （CsboxScreen 删除 `extractBackground` override、背景 fill 移入 `renderBg` 走
> `UiBackdrop.fill()` + `AnimRenderOps.fillGradient`；CsLookItemScreen /
> CsboxConfirmScreen / CsboxBulkOverviewScreen / CsboxBulkResultScreen 同管线；
> CsboxProgressScreen `extractBlurredBackground` 在 forge `ModList.isLoaded("blur")`
> 时走 vanilla、否则 `AnimRenderOps.renderBlurredBackground`），并顺带并入
> CsLookItemScreen 进入淡出 + `ItemDrag3D` 拖拽、CsboxProgressScreen 拒绝横幅
> （`rejected` 40 tick 关闭）。批量开箱管线对齐 v26：`PacketCsgoBulkProgress`
> 换 `BoxStripGenerator`（`BulkOpenResult` 补 `fallback` 第 8 参、finalize 补回退
> 分级修复循环，forge `PacketCsgoProgress` 新增 boxId 版 `resolveGrade`）；
> `PacketBoxBulkResult` 按 `BULK_PER_PACKET=32` 分块发送、`MAX_PENDING_BULK` 提到
> 64，客户端 `CsboxProgressScreen` 以 `drainBulkChunks()` while 循环聚合全部
> chunk 后进 `CsboxBulkResultScreen`。单开路径同步收口：`PacketCsgoProgress`
> 弃用手写 `itemList`+`AnimationStrip` 循环，改走 `GradeMapCache.get(boxId)`
> 缓存池 + `BoxStripGenerator.generate` 统一抽条（删除旧 Map 版 `resolveGrade`，
> 仅留 boxId 版）；`/csbox` 命令按 v26 收口对齐（移除 `set`/`give`/独立
> `errors`/`tutorial` 子命令，仅留裸命令帮助、`info` 含可选 box 参数并附加载
> 错误列表、`reload` 含 `tutorial` 子命令、`nbt hand` 任意玩家可用；
> `BoxItemCodec` 补 `gson()` 访问器）。`clean compileJava` 通过，L0-L3 门禁 7/7
> PASS（`build/test-reports/forge-2612.xml`）。

## 7. 已知限制与后续路线

**限制**

- **run 任务曾因 foojay 插件版本挂起（已修复，2026-08-11）**：现象是
  `NoSuchFieldError: JvmVendorSpec.IBM_SEMERU`，曾误判为 ForgeGradle 上游缺陷；
  真因是 `settings.gradle` 的 `foojay-resolver-convention 0.9.0` 字节码引用了
  Gradle 9 已删除的常量。升级到 1.0.0 后 run 任务全部可用（`runServer` 已实测
  跑通至 `Done`）。若再遇同类错误，先检查 foojay 版本。
- ~~无 MCP 黑盒自动化~~（已解决，2026-08-11）：TestHelper 已新增 MinecraftForge
  26.1.2 构建目标（`mc_tools -Pactive_versions=forge-26.1.2`，源集
  `src/main/java-forge`），forge 客户端内可加载 MCP server；`test_csbox.sh` /
  `test_csbox_ext.py` 已直接用于 forge L4（见 §5.3）。测试设施侧仍有一处已修复的
  启动竞态（config 异步加载导致 `startOnFirstTick` 崩溃），见报告 §2。
- 不在 CI：`build.yml` / `pr-checks.yml` 矩阵均未含 `forge_26_1_2`；本流程的
  脚本可先本地跑，接入 CI 时把 `scripts/test-forge-2612.sh` 挂进独立 job 即可。
- GameTest：`build.gradle` 已配置 `gameTestServer` run 且开启
  `forge.enabledGameTestNamespaces=csgobox`，但当前无 `@GameTest` 用例
  （与仓库其余平台一致）；开箱主流程是客户端 GUI + 服务端包，GameTest 覆盖价值有限。

**TestHelper 移植到 MinecraftForge — 已落地（2026-08-11）**

forge 已获得与 NeoForge 平台同等的黑盒 E2E（`mc_*` 工具 + `test_csbox*.sh` /
`test_csbox_ext.py`），L4 已转为自动化（§5.3 实测记录）。落地时按以下方案实施：

1. `mc_tools` 增加 MinecraftForge 26.1.2 构建目标：
   - wrapper 需从 8.14 升到 **≥9.3**（ForgeGradle 7 要求，CS2-Box 已用 9.5.1）；
   - `settings.gradle` 补 `maven.minecraftforge.net` 仓库；
   - 新增源集（参照 `src/main/java-new` 的 26.1.2 适配方式）。
2. 移植面很小（TestHelper 仅 4 个文件 import NeoForge）：
   - `TestHelper.java`：`net.neoforged.*` → `net.minecraftforge.fml.common.Mod` /
     `ModLoadingContext` / `MinecraftForge.EVENT_BUS`；
   - `TestHelperConfig.java`：`ModConfigSpec` → `ForgeConfigSpec`；
   - `events/TestEvents.java`：事件映射（下表）；
   - `graphics/GraphicsAutoTune.java`：`ClientTickEvent` 映射。
   - 其余（MCP server / JsonRpc / EventLog / WidgetScanner / ToolRunner 等）纯
     JVM + vanilla MC，直接复用。
3. Forge 事件 API 映射（26.x 新 eventbus，`net.minecraftforge.eventbus.api.*`）：

   | NeoForge（TestHelper 现用） | MinecraftForge 26.1.2 |
   |---|---|
   | `NeoForge.EVENT_BUS.addListener(...)` | `MinecraftForge.EVENT_BUS.register(this)` + `@SubscribeEvent`（`net.minecraftforge.eventbus.api.listener.SubscribeEvent`），或 `EventBus.addListener` |
   | `ClientStartedEvent` / `ClientStoppedEvent` | Forge 客户端生命周期事件（无对应时改在 `FMLClientSetupEvent` 启动 / 关闭钩子停服） |
   | `ClientTickEvent.Pre` | `TickEvent.ClientTickEvent`（参考 `ModEvents#serverTick` 的写法） |
   | `RenderFrameEvent.Post` | `TickEvent.RenderTickEvent` 或 Forge 渲染事件对应项 |
   | `ScreenEvent.Opening/Closing` | Forge `ScreenEvent` 对应事件 |
   | `InputEvent` / `ClientChatReceivedEvent` | Forge 输入/聊天事件对应项 |

4. 移植完成后：`test_csbox.sh` / `fullcheck` 增加 `--platform forge-26.1.2` 即可
   复用现有 E2E 用例，L4 转为自动化。

## 8. 失败排查速查

| 现象 | 处理 |
|---|---|
| S1 失败 | 看编译错误；改动涉及平台时不要依赖增量缓存，必须 `clean` |
| S2 产物缺失 | 确认 `-Pactive_versions=forge-26.1.2` 传了；看 jar task 输出路径 |
| S3 失败 | 按 `scripts/check-version.sh` 输出四处版本同步 |
| S5 失败 | 看 `forge_26_1_2/build/test-results/test/` 报告；`PlatformSmokeTest` 断言已随 1.0.7 同步更新（terminal/armory/premium 为预期项），失败说明同步回退或字段被误删 |
| runClient 起不来 | 检查 JDK 25 toolchain、删除 `run/` 后重试、看 `logs/latest.log` |
