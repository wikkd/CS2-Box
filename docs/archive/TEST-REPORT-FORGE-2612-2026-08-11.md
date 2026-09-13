> ⚠️ 已归档（历史快照）：本文档记录 forge_26_1_2 1.0.6 测试报告（2026-08-11）的当时状态，不再随项目更新；当前信息以 README.md 与 docs/ 为准。

# forge_26_1_2 · 1.0.6 测试报告（2026-08-11）

> 依据 mc_tools 最新文档（`~/Desktop/mc_tools/docs/cli.md` / `docs/scripts.md`）重启的
> 客户端运行时 E2E。基线：**v1.0.6**（`gradle.properties mod_version=1.0.6`），
> 平台：**forge_26_1_2**（MinecraftForge 26.1.2-64.1.0，Java 25），
> 主仓库 HEAD：`b60d7bd`。
> 关联：`docs/TESTING-FORGE-2612.md`（测试流程）、`docs/MANUAL-TEST-CHECKLIST-FORGE-2612.md`（手动清单）。

## 1. 测试环境

| 项 | 值 |
|---|---|
| 客户端 | `:forge_26_1_2:runClient -Pactive_versions=forge-26.1.2 -PgameDir=<repo>/forge_26_1_2/run`（长驻会话） |
| MCP | TestHelper forge-26.1.2-0.2.0（`mc_tools/src/main/java-forge`），端口 `41502`（41501 被 v26_2 客户端占用自动 +1） |
| 存档 | 单人世界「新的世界」，survival + peaceful + keepInventory，`/time set day` |
| 套件 | `scripts/test_csbox.sh`（基础 E2E）+ `scripts/test_csbox_ext.py`（扩展 E1-E11） |
| 视觉 | 本地 Ollama `gemma4:12b`（套件默认 `VISION_PRIMARY_MODEL`） |

## 2. 测试设施修复（本轮发现并修复）

**TestHelper forge 端启动竞态**：`startOnFirstTick` 在首个客户端 tick 直接读取
`ForgeConfigSpec` 配置值；Forge 配置为异步加载，客户端启动快时（首 tick 早于配置
加载完成）抛 `IllegalStateException: Cannot get config value before config is loaded`，
客户端在标题屏崩溃（crash-2026-08-11_20.25.55）。

修复（`mc_tools/src/main/java-forge/com/reclizer/testhelper/TestHelper.java`）：
`startOnFirstTick` 先检查 `CONFIG_SPEC.isLoaded()`，未加载则等待下一 tick 重试。
重建 jar（`testhelper-forge-26.1.2-0.2.0.jar`）后客户端稳定进标题屏，后续整轮
E2E 无此崩溃。

## 2.1 模组物品模型全缺失（本轮发现并修复）

**现象**：玩家贴图（用户反馈 + 截图）：快捷栏中模组物品（箱/钥匙）全部显示紫黑
棋盘格缺失贴图，开箱屏左侧 3D 箱模预览同样缺失；用户表述为「模组所添加的每样
东西都没有加载模型」。

**根因**：MC 26.x 改用新 item model definition 系统——每个物品的模型经
`assets/<ns>/items/<path>.json`（`DataComponents.ITEM_MODEL` 亦按此解析）。NeoForge
平台（`v26_1_2`）自带 `assets/csgobox/items/*.json`；而 forge 模块的
`sourceSets.main.resources` 只接了 `common/src/main/resources` 与自身 resources，
**从未包含 `items/` 目录**，导致 `csgobox:csgo_box` / `csgo_key0-3` 及动态箱
（`ITEM_MODEL=csgobox:csgo_box`）全部落到 missing model（紫黑格）。jar 与
`build/sourceSets/main` 均确认无 `items/`。

**修复**（均为 forge 模块自身改动，不动 common / 其他平台）：
- 新增 `forge_26_1_2/src/main/resources/assets/csgobox/items/` 下 5 个 1.0.6 基线
  物品定义：`csgo_box.json`、`csgo_key0-3.json`（内容与 v26_1_2 一致，
  `{"model":{"type":"minecraft:model","model":"csgobox:item/<name>"}}`）。
- `forge_26_1_2/build.gradle` 排除清单追加 2.0.0 专有定义
  `assets/csgobox/items/{terminal,armory_point}.json`，
  维持 1.0.6 特性基线。

**验证**：
- 重建 jar 后 `assets/csgobox/items/` 5 文件进入产物（jar + `build/sourceSets/main`）。
- 重启客户端进世界，`/give` 发放 `csgo_box`（动态箱 `box_id=weapon_supply_box`）、
  `csgo_key0-3`、`weapon_supply_box`：快捷栏 9 格品红缺失贴图像素 **0**，
  箱/钥匙图标渲染正常；开箱屏顶部 3D 箱模棕黑质感渲染正常，全屏品红像素 **0**
  （存证 `forge_26_1_2/run/screenshots/testhelper-20260811-214526.png`、
  `testhelper-20260811-215101.png`）。
- 回归：基础套件 `test_csbox.sh` 重跑 **11P/0F/0W**，功能零影响。

## 3. 上轮 bug 修复复核（重生 UI 消失）

**根因**：`CsboxProgressScreen`/`CsLookItemScreen` 渲染时置 `options.hideGui=true`；
死亡时 vanilla `setScreen(DeathScreen)` 只调 `Screen.removed()` 不调 `onClose()`，
`hideGui` 永不恢复 → 重生后 HUD 永久消失。

**修复**（6 文件新增 `removed()` 覆写恢复 HUD，三平台同步）：
`forge_26_1_2` / `v26_1_2` 的 `gui/CsboxProgressScreen.java`、`gui/CsLookItemScreen.java`
（`options.hideGui=false`），`v26_2` 对应文件（`HudVisibility.show()`）。

**实证**（本轮重启后复核）：
- 开箱动画中 `/kill` → 重生 → HUD 完整恢复（血条/饥饿/快捷栏/准星），无残留屏。
- 存证：`~/.codex/visualizations/2026/08/11/019feeb8-25db-7280-98c0-993480e22d7a/`
  `respawn_death_run1.png`（修复前 HUD 消失）/ `fixed_respawn_hud.png`（修复后完整）。

## 4. L4 运行时 E2E 结果

### 4.1 基础套件 `test_csbox.sh` → **11 通过 / 0 失败 / 0 警告**

| 用例 | 结果 |
|---|---|
| T1 右键打开 CsboxScreen | PASS |
| T2 开启 → CsboxProgressScreen 动画 | PASS |
| T3 结果屏 CsLookItemScreen | PASS |
| T4 截图存证 | PASS（`forge_26_1_2/run/screenshots/csbox_test_forge/result.png`） |
| T5 钥匙消耗 1→0 | PASS |
| T6 开出物品进背包 | PASS |
| T7 关闭结果屏回世界 | PASS |
| T8 动画中 ESC 取消（无残留屏 + 服务端已结算 + 物品入包） | PASS ×3 |
| T9 取消后可立即重开（无冷却阻塞） | PASS |

### 4.2 扩展套件 `test_csbox_ext.py` → **21 通过 / 12 失败 / 1 警告**

| 组 | 结果 | 归类 |
|---|---|---|
| E1 命令系统 | E1a/E1c FAIL（E1b PASS） | 🔴 **真 bug（forge 移植缺口）** |
| E2 成就 first_box | 3/3 PASS | ✅ |
| E3 opened_boxes 统计 | 2/2 PASS | ✅ |
| E4 副手不触发 | PASS | ✅ |
| E5 开/关循环 10 次 | PASS | ✅ |
| E6 错误钥匙拒绝 + 锻造台配方 | 2/2 PASS | ✅ |
| E7 配置文件 | PASS | ✅ |
| E8 批量开箱 | 6/6 FAIL | ⚪ 预期（1.0.6 基线屏蔽批量开箱） |
| E9 检视屏 | 5/5 PASS | ✅ |
| E10 视觉模型校验 | E10a FAIL（E10b/c/e PASS，E10d WARN） | 🟡 视觉 OCR 误判 |
| E11 开箱屏翻页 | E11a/b/d FAIL（E11c/e PASS） | 🟡 视觉 OCR 误判 |

## 5. 失败项甄别

### 🔴 E1a / E1c — 真 bug：forge `/csbox info` 无参形式缺失

- 现象：`/csbox info`（无参）返回「未知或不完整的命令」，而 `/csbox info <box-id>` 正常。
- 根因：`forge_26_1_2/.../command/CsboxCommand.java` 的 `info` 节点**只有**
  `argument("box")` 分支，缺少 v26_1_2 基线（1.0.6）的
  `.executes(showInfoOverview)` 无参入口，且 `showInfoOverview` 方法整体缺失。
- 影响：`/csbox help` 中 `info` 用法与 v26_1_2 不一致；测试套件 E1a（无参 info 列出
  默认箱）与 E1c（reload 后无参 info）失败。
- 修复建议（待用户确认）：按 v26_1_2 补齐 `info` 节点 `.executes(CsboxCommand::showInfoOverview)`
  与 `showInfoOverview` 方法（默认箱概览，输出 `weapon_supply_box` 等）。
- 复现：`mc_exec /csbox info` → 聊天「csbox info<--[此处]」。

### ⚪ E8a-f — 预期失败：1.0.6 基线屏蔽批量开箱

`forge_26_1_2/.../event/ClickEvent.java`：
`boolean shift = false; // 1.0.6 屏蔽批量开箱（2.0.0 恢复）`。
Shift+右键不触发批量总览屏属**设计行为**，套件 E8 断言的是 2.0.0 特性，预期 FAIL。

### 🟡 E10a / E11a / E11b / E11d — 视觉 OCR 误判（非 mod bug）

套件用 `gemma4:12b` 提取截图文字，多处漏读。截图经 `qwen3-vl` 复核内容**均正确**：

| 用例 | 套件判定 | 截图实况（qwen3-vl 复核） |
|---|---|---|
| E10a 开箱屏 | 「开启/返回」未识别 → FAIL | 按钮「开启」「返回」、标题「CS 开箱」、页码「1/3」均在，无渲染异常 |
| E11a 页码 1/3 | 未识别 → FAIL | 显示「1/3」 |
| E11b 翻到 2/3 | 未识别 → FAIL | 显示「2/3」（翻页功能正常） |
| E11c 翻到 3/3 | 识别成功 → PASS | 显示「3/3」（对照组，证明非功能问题） |
| E11d 翻回 2/3 | 未识别 → FAIL | 显示「2/3」（翻回功能正常） |

结论：翻页、页码渲染、按钮文字全部正常；失败全部为 gemma4:12b OCR 漏读。
存证：`/tmp/visual_timeline/tagged/tag_*_E10a_box_screen.png`、
`tag_*_E11a_p1.png`、`tag_*_E11b_p2.png`、`tag_*_E11d_p2_back.png`。

### 🟡 E10d — WARN（套件自认可接受）

检视屏信息面板「皮肤风格/磨损率」OCR 未完全命中，套件按 `warn`（OCR 误差可接受）处理。

## 6. 已知告警（非本轮阻塞）

- **网络通道空 payload**：每次开箱刷一条
  `ERROR Received empty payload on channel csgobox:network login index 0` +
  `WARN Unknown custom packet payload: csgobox:network`。
  功能不受影响（开箱全流程通过），疑似 `Networking.java` 登录 index 0 通道注册/握手
  问题，建议后续排查（非 1.0.6 发布阻塞）。
- **Realms**：`Failed to fetch Realms feature flags`（离线/未登录环境正常现象）。
- 材质：~~「所有模组材质未加载」~~ → 已定位并修复（见 §2.1 模型缺失修复）：
  forge 模块缺 `assets/csgobox/items/` 定义，非贴图缺失。修复后快捷栏/开箱屏
  均无紫黑格，回归 11/11 通过。

## 7. 结论

- forge 26.1.2 **1.0.6 核心开箱链路可用**：动态箱子、开箱动画、钥匙消耗、产出入包、
  取消重开、成就/统计、检视屏、翻页均正常（基础套件 11/11；扩展套件功能性用例
  E2-E9 全过）。
- **模组物品模型缺失已修复**（§2.1）：补 `items/` 定义后箱/钥匙图标与 GUI 3D 预览
  全部正常渲染，回归 11/11。
- **发布前待修 1 项**：`/csbox info` 无参形式缺失（E1a/E1c），属 1.0.6 基线移植缺口。
- 批量开箱 E8 失败为 1.0.6 屏蔽设计，非回归。
- E10/E11 视觉失败为测试设施 OCR 误判，功能正常。

## 8. 附：运行记录

- 20:29 基础套件 `test_csbox.sh`：11P/0F/0W（约 27s）
- 20:29-21:01 扩展套件 `test_csbox_ext.py`：21P/12F/1W（约 32min，E10/E11 视觉阶段受
  gemma4:12b 单图 5-10min 拖慢）
- 客户端全程存活：runClient 长驻会话，MCP 41502
- 21:39 重启客户端（模型修复后）：v26_2 客户端（41501）已退出，forge TestHelper
  改绑 **41501**；21:47 截图验证模型正常；21:54 基础套件重跑 11P/0F/0W。
