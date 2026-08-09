# CS2-Box 运行时 GUI 自动化测试工作流

> 适用场景：模组自绘 GUI（`CsboxScreen` / `CsboxProgressScreen` / `CsLookItemScreen` 等）的渲染与交互回归验证。
> 与 GameTest（`docs/TESTING.md`）互补：GameTest 覆盖服务端逻辑，本工作流覆盖"玩家眼睛看到的"。
> 该流程在 macOS + NeoForge 26.1.2 单机客户端上验证，坐标与工具以 1708×960 帧缓冲 / 1512×982 屏幕为准。

## 0. 方案对比与选型结论

| 方案 | 能力 | 关键局限 | 结论 |
|---|---|---|---|
| CGEvent 鼠标 + AppleScript 键盘 + PIL 像素断言（本工作流） | 控制**现有客户端**、点击模组自绘 Screen、帧缓冲级断言 | 脆，必须确定性环境 | **采用** |
| Mineflayer 系 MCP（`mineflayer-mcp` / `awesome-mineflayer-mcp` 等 123 工具） | headless bot 移动/挖掘/容器/聊天/截图 | 独立 bot 进程，**看不到模组 Screen，无法驱动现有客户端**；需开 LAN + 第二账户 | 仅备选：世界状态交叉验证（哨兵 bot） |
| GameTest（`docs/TESTING.md`） | 方块/物品/服务端逻辑 | 无渲染断言 | 互补，回归时并行 |
| RCON | 原版命令 | 单机集成服务器默认关闭，无法点击 GUI | 弃用（chat 注入等效） |

**结论**：GUI 渲染验证只能走"外部驱动现有客户端 + 帧缓冲断言"。MCP 生态提供的是第二个玩家，不是遥控器——引入前先想清楚它是否真的在控制你要验证的窗口。

## 1. 环境准备

### 1.1 窗口布局（防 Z 序吞点击）

macOS 上多个窗口叠在游戏上方会静默吃掉鼠标事件（CGEvent 坐标正确但事件进别的窗口）。实测 Z 序：`OpenCode → 访达 → Qoder → java(最底)`，全部盖住游戏。

```bash
# 检查窗口清单与坐标
<tmp>/winlist | grep -i "java\|opencode\|finder\|qoder"
# 移走遮挡窗口（注意中文进程名）
osascript -e 'tell application "System Events" to set position of window 1 of process "OpenCode" to {-1240, 33}'
osascript -e 'tell application "System Events" to set position of window 1 of process "访达" to {-880, 62}'
osascript -e 'tell application "System Events" to set position of window 1 of process "Qoder" to {-1400, 33}'
```

- 每次注入前必须：`osascript -e 'tell application "System Events" to set frontmost of (first process whose unix id is <java_pid>) to true'`
- 游戏窗口保持在 `(641,225,854,508)`，坐标映射依赖它。

### 1.2 坐标映射（含 28px 标题栏 —— 曾踩坑）

窗口含 macOS 标题栏，客户区 = `(854, 508-28)` = `(854, 480)`，恰好是帧缓冲 1708×960 的 2:1：

```
screenX = 641 + fbX/2
screenY = 253 + fbY/2        # 253 = 225(窗口顶) + 28(标题栏)
```

| 关键点位（帧缓冲 → 屏幕） | fb | screen |
|---|---|---|
| 画面中心/准星 | (854,480) | (1068,493) |
| 开箱界面绿色按钮 | (1243,923) | (1263,715) |
| 快捷栏槽 1 中心 | (540,916) | (911,711) |
| 快捷栏槽 k 中心 | (540+80(k-1),916) | 同公式 |

### 1.3 工具原语

临时工具（`<tmp>/opencode/`）：`click`（CGEvent 鼠标）、`move`、`wheel`、`getpos`、`screeninfo`、`winlist`；PIL 环境 `pilenv/bin/python3`。建议固化到 `scripts/ui-driver/`（见 §7）。

**click 参数坑**：签名是 `click <x> <y> [clicks] [button]`，按钮必须是**第 4 参**：
`click 1068 479 1 right` ✅；`click 1068 479 right` ❌ 会把 "right" 当点击数，静默变成左键。

**键盘注入**：CGEvent 键盘事件对 LWJGL 无效（历史验证），必须用 AppleScript `key code`。常用键位：Esc=53、数字1=18、W=13、空格=49、F2=120、F3=63、Shift=56。

## 2. 确定性世界状态（最重要经验）

全帧 diff 只能检测"变化"，无法区分"光照变化"与"UI 变化"。实测日落时段每帧全屏 diff≈20760——**环境光完全污染 diff 判定**。

开测前固定世界：

```
/clear @p
/give @p csgobox:weapon_supply_box
/give @p csgobox:csgo_key0
/time set day
/gamerule doDaylightCycle false
/weather clear
/gamerule doWeatherCycle false
```

- **避免 Esc**：单机 Esc 会打开暂停菜单（日志出现 `Saving and pausing game...`），暂停菜单吞掉后续所有点击。关闭 GUI 用游戏内方式（E/再次点击），或在暂停后先发一次 Esc 恢复。
- 持有物品固定：give 后 `key code 18`（数字 1）选中槽 1，用 `data get entity @p Pos` 确认状态。
- GUI 判定一律**定点像素断言**（§4），不要用全帧 diff。

## 3. 标准流程（开箱 → 检视屏验证）

以验证 `CsLookItemScreen` 底部工具栏为例：

1. **准备**：按 §2 固定世界；确认日志出现 `已将1个[武器供应箱]给予Dev` / `已将1个[铁钥匙]给予Dev`（chat 回显 = 注入成功的唯一可靠证据）。
2. **放置箱子**：手持箱子，右键准星位 `(1068,493)` → 断言画面中心出现新方块（局部 bbox diff，非全帧）。
3. **开 GUI**：右键箱子 → 断言 139 灰面板出现（§4-A）。
4. **点开启**：左键绿按钮 `(1263,715)` → 断言滚动动画开始（进度屏区域持续 diff）+ 日志服务端确认。
5. **检视屏**：动画结束后断言 `CsLookItemScreen` 底部工具栏（§4-C）与结果物品网格。
6. **收尾**：截图存档（时间戳命名已由 F2 生成），记录到回归清单。

## 4. 像素断言表

| ID | 目标 | 帧缓冲检测法 | 阈值 |
|---|---|---|---|
| A | 开箱界面面板 | `(139,139,139)` 采样 | 面板区 px > 100 |
| B | 绿色"开启"按钮 | 区域内 `g>120, r<120, b<120` @fb(1243,923)±50 | > 10 px |
| C | 检视屏工具栏 | 底部 6 图标列 + ⓘ + ✖ 的固定 x 列亮度特征（按 UI-SPEC 锁定） | 待首次成功后固化 |
| D | 快捷栏选中槽 | 槽内白框像素（选中=50，物品=12-22） | =50 |
| E | 主世界 HUD | 快捷栏 9 槽 x(500..1220) y882-950 结构 | 亮段≥9 |

注意：139 灰面板判定用 `==(139,139,139)` 需先确认无抗锯齿干扰（4px 步进采样可容忍）。

## 5. 日志断言通道

- 动作成功证据优先看 `runs/client/logs/latest.log`：
  - `[CHAT] 已将1个[...]给予Dev` —— give 成功
  - `[CHAT] Dev拥有以下实体数据：[...]` —— `data get` 回显
  - `Saving and pausing game...` —— **暂停了**，后续点击无效，先发 Esc 恢复
  - `Resizing Chunk Sections UBO` —— 正常噪音，忽略
- 时序：动作 → 日志时间戳 ≤ 数秒。超时即输入未达（查窗口遮挡/焦点）。

## 6. 问题诊断速查表（踩坑经验）

| 现象 | 根因 | 处理 |
|---|---|---|
| 点击零效果（diff≈0 或 96） | 其他窗口盖住游戏 | `winlist` 检查 → 移屏外 → `set frontmost java` |
| 全帧恒定大 diff（~20000） | 日落/天气光照变化 | 固定时间天气（§2）；改用定点断言 |
| 右键无反应 | 暂停菜单开着（Esc 所致） | 先 Esc 恢复；避免在流程中按 Esc |
| 画面亮度全黑但帧缓冲正常 | 洞穴/夜视差 | 用 `/time set day` + 移到固定测试点 |
| 视觉模型（qwen3-vl）描述不可信 | 幻觉（曾谎报"建筑模式快捷栏"） | 只用于粗场景，UI 断言一律 PIL |
| 按钮坐标点击落空 | 标题栏 28px 未计入 | 用 §1.2 公式 |
| `key code ... using {fn}` 报错 | AppleScript 不支持 fn 修饰键 | F3 调试屏非必需可跳过；或写 CGEvent 带 `.maskSecondaryFn` |
| 鼠标事件没反应但键盘有效 | CGEvent 被上层窗口吃掉（见上） | 窗口清理 |
| 截图 14.18.24.png 不存在 | 手动猜文件名 | 永远 `glob 最新` 取文件 |

## 7. 工程化增强（后续可选）

1. **固化驱动脚本**：把 `<tmp>/opencode/` 的 click/move/wheel/winlist + PIL 断言脚本复制到 `scripts/ui-driver/`，参数化坐标常量。
2. **轻量 MCP 包装**：将上述脚本包装成 stdio MCP server（工具：`mc_click` / `mc_key` / `mc_shot` / `mc_assert_pixel` / `mc_grep_log`），供 opencode / Claude Desktop 复用，消除每次会话重建工具的损耗。
3. **基线截图回归**：录制"开箱→检视"黄金路径截图，发布前逐帧对比（对轻微渲染差异保留人工放行）。
4. **mineflayer 哨兵 bot**（可选）：开 LAN 后让 bot 加入，用其状态 API 交叉验证世界侧结果（物品发放、成就计数），与客户端 GUI 断言互补。
5. **多平台复用**：1.21.1 与 26.2 客户端仅坐标与版本差异，脚本参数化 `--window` 后可直接复用。

## 8. 验收清单挂钩

本工作流服务 `docs/RELEASE.md` §3 质量门"运行时回归"条目，及 `docs/TESTING.md` 手动清单（v26_1_2 GUI 验收、v1.0.5 TC-1~TC-4）。执行完成后将截图路径与断言结果追加到 `docs/MANUAL-TESTING-v1.0.5.md` 对应 TC 的备注。

## 9. AnimRenderOps 重构回归（v1.21.1 基线）

> 背景：`utils/AnimRenderOps.java` 成为各平台唯一的渲染原语适配点后，五个 Screen + 两个
> 助手（IconListTools/GuiItemMove）的渲染调用全部委托给它。**视觉行为必须与重构前逐像素
> 一致**（`era: legacy` 变体按原代码逐字搬移，轴映射/数值/顺序未动）。以下清单在
> **1.21.1 客户端**执行；其它平台只做编译级 + 漂移脚本验证（见 `scripts/check-animops-drift.sh`）。

自动化入口（前置：`./gradlew runClient -Pactive_versions=1.21.1` + helper mod + MCP 端口 41501）：

```bash
python3 scripts/record_open_animation.py            # 录制开箱动画连拍帧
python3 scripts/test_animation_aesthetics.py        # 聚光灯/透镜审美断言（exit 0=全 PASS）
```

手动勾选清单（对照 §3 标准流程执行，截图像素断言用 §4 表）：

- [ ] 开箱动画：滚动缓动节奏与重构前一致（进度屏区域持续 diff，无跳帧/停帧）
- [ ] 聚光灯径向渐变：半透明边缘正确（spot_glow 五连收口后无硬边白盘——防回归点 1）
- [ ] 放大镜切带：物品放大比例、透镜背板灰 `0xFF545454`、滚动条裁剪边界正确（scissor 收口后无越界绘制）
- [ ] 透镜 vignette：圆环边缘半透明灰（vignette 五连收口后无白环——防回归点 2）
- [ ] 金色线：`(230,255,215,0)` 颜色与 2px 宽度正确
- [ ] 出货页（CsLookItemScreen）：2D 图标渲染、稀有度描边、聚焦缩放（IconListTools 委托后无差异）
- [ ] 3D 物品拖拽（CsboxBulkOverviewScreen）：鼠标拖拽旋转手感不变（GuiItemMove 委托后 angleX/angleY 方向正确）
- [ ] 背景模糊：进度屏背景 `renderBlurredBackground` 反射桥接后仍有模糊效果（防回归点 3）
- [ ] ESC 退出、hideGui 恢复、音效节奏（每卡片"嗒"声、8Hz 节流）不变
- [ ] TACZ 环境：TACZ 枪默认 3D 展示 + 手套按钮检视动画（TaczInspectViewport 独立路径未被误伤）

执行记录：

- 2026-08-09（本次重构）：**未执行**——自动化会话无 GUI 权限（System Events 权限违例），
  清单已固化，待人工在 1.21.1 客户端勾选；编译级验证（BUILD SUCCESSFUL）与残余直调
  grep（零残留）已通过。
- 2026-08-09（收尾会话）：三平台（1.21.1 / 26.1.2 / 26.2）门面收口全部完成：
  - 5 屏 + 3 助手（IconListTools/GuiItemMove/ButtonPalette）零原始 draw 调用残留
    （`guiGraphics.blit/fill/fillGradient/flush/scissor` 全仓 grep 除门面外为空）
  - 帧首三连（setShaderColor+enableBlend+defaultBlendFunc）在 ProgressScreen /
    BulkResultScreen / CsLookItemScreen 已删除；残留 RenderSystem 仅深度开关与
    工具栏 tint 循环（有意保留）
  - 门面 13 个公开 op 三平台签名一致（`scripts/check-animops-drift.sh` 全 OK，CI 已接线）
  - 三平台 clean 编译 + common 测试通过
  - **运行时回归仍未执行**（上述勾选清单 + 批量开箱恢复 1.0.7 链路 + 音效重编码听感），
    待具备 GUI 权限的会话人工/自动化执行
