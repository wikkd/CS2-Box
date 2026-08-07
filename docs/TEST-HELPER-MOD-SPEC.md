# CS2-Box 测试辅助模组需求规格（TEST-HELPER-MOD-SPEC）

> 目标读者：开发「测试辅助模组」的开发者（用于驱动 CS2-Box 的自动化测试）。
> 背景：`docs/RUNTIME-UI-TESTING.md` 描述了当前「外部驱动客户端 + 截图像素断言」的工作流。实测暴露出大量**外部黑盒无法可靠观测**的问题——本模组把「眼睛」搬进游戏内部，把状态以结构化日志/聊天输出，供外部脚本消费。
> 本文档是需求规格，不是实现；实现位置建议独立模块 `testhelper/`，仅对 csgobox 依赖少量 API（或完全不依赖，用类名/注册名匹配）。

---

## 1. 痛点清单（来自真实运行会话，按严重度排序）

### P0-A 当前 GUI 是什么：全靠颜色猜
- 外部只能截帧后做颜色直方图，猜测「这是创造物品栏 CS2-Box 标签页 / 这是模组 Screen」。
- 实测：`2026-08-01_15.30.17.png` 是**原版创造物品栏 CS2-Box 标签页**，靠槽背景色 (139,139,139) 与网格 10×6 推断，耗时 20+ 轮工具调用才确认；而模组自己的 `CsboxScreen` 也大面积使用 `OverlayColor.surface()` (27,27,34)，二者靠像素区分非常脆弱。
- **要解决**：一条命令/一行日志直接输出「当前 Screen 类名 + Screen 标题 + GUI scale + 帧缓冲尺寸」。

### P0-B 手上/物品栏有什么：完全不可知
- 外部只能采样热栏槽中心几个像素，再与 `textures/item/*.png` 的调色板比对，猜「槽 2 是 csgo_box 还是钥匙」。
- 实测反复出现误判：槽 2 被当作「灰箱状物」，实际物品是深色钥匙状；主手物品是品红还是灰色靠 3D 渲染高光猜，多次结论反复横跳。
- **要解决**：输出主手/热栏/背包每个槽位的 `ItemStack` 注册名 + 数量 + NBT（如 `csgobox:csgo_box x5`）。

### P0-C 输入有没有生效：没有任何反馈
- 外部发 `key code 19`（数字 2）后，无法确认「切换了热栏槽 2」还是「按键根本没进游戏」（窗口被 OpenCode 盖住时 CGEvent 会被吃）。
- 实测：窗口 Z 序问题导致整段测试白做——按键发给了 OpenCode 而不是 MC。
- **要解决**：游戏内收到键盘/鼠标事件时**即时打印**（键码/按钮/坐标/当前 Screen），外部发完输入后 grep 日志即可确认送达。

### P0-D 点击坐标：纯几何推算，脆弱
- 外部坐标 = 窗口位置(winlist) + 28px 标题栏 + 2x 缩放 + 帧缓冲坐标，任何一个环节错就点空。
- 实测：`click 1400 479` 落在 OpenCode 上；`click 1068 479` 才是 MC 中心，但因为窗口被盖，事件被上层窗口吃掉。
- **要解决**：游戏内输出**鼠标当前帧缓冲坐标**（`mouseHandler`），以及可交互 UI 元素的建议点击点，外部只需对齐一个坐标系。

### P1-A 批量开箱等异步流程：中间态不可观测
- `PacketCsgoBulkProgress` 走异步线程池，外部无法区分「还在算」与「卡死」。
- **要解决**：开箱请求/完成/失败各打一行日志（含数量、耗时、结果物品）。

### P1-B 渲染完成时机：只能 sleep
- 打开 Screen 后外部靠 `sleep 1s` 猜渲染完成；F2 截图落盘时机靠 `[CHAT] 已将截图保存为` 日志反推。
- **要解决**：Screen open/close 事件日志 + 可选「渲染完成回调日志」（`onRender` 首帧打印一次）。

### P1-C 快捷键（F2/F3）对 LWJGL 无效、fn 修饰键 AppleScript 不支持
- 已有文档记录：CGEvent 键盘对 LWJGL 无效；`key code ... using {fn}` 会报错。
- **要解决**：模组提供 `/shot` 等价命令（见 §3.3），绕开 F2。

### P1-D 视觉模型（qwen3-vl）幻觉
- 截图描述曾谎报「建筑模式快捷栏」等内容，不可作为断言依据。
- **要解决**：UI 断言一律走模组日志 + PIL 定点采样，视觉模型只做粗分类（现有方案已如此，本文档不改动）。

---

## 2. 设计原则

1. **日志是唯一事实来源**：所有状态输出到 `latest.log`（统一前缀 `[CSBOXTEST]`），外部脚本 `grep` 即可，不做截图 OCR。
2. **聊天空闲可读**：同一内容可选镜像到聊天（`sendSystemMessage`），便于人工核对。
3. **零侵入**：不修改 CS2-Box 任何类；通过 Screen 类名（`csgobox` 包名匹配）、注册名、事件订阅拿到状态。
4. **单机优先**：在单机集成服务器内跑，客户端侧命令即可，不需要权限系统。
5. **坐标约定**：一律输出**帧缓冲坐标**（`fb`，即 `window.getWidth()/getHeight()`，1708×960），外部换算屏幕坐标只做一次。

---

## 3. 功能需求

### 3.1 状态查询命令 `/cst status`
输出（日志 + 聊天）：
```
[CSBOXTEST] screen=<类名|null> title=<Screen标题> guiScale=<float> fb=<WxH> fps=<int> tick=<long>
[CSBOXTEST] mouse_fb=<x,y>
[CSBOXTEST] mainhand=<注册名> count=<n> nbt=<json|null>
[CSBOXTEST] offhand=<注册名> ...
[CSBOXTEST] hotbar=[{slot,item,count},...x9]
[CSBOXTEST] inventory_slots=<总数> used=<n>
```
- 客户端命令注册：NeoForge `RegisterClientCommandsEvent`（26.x 对应事件名以实际为准），`ClientCommandSourceStack` 下执行。
- 帧缓冲尺寸取 `Minecraft.getInstance().getWindow().getWidth()/getHeight()`。
- Screen 标题取 `screen.getTitle().getString()`（自绘 Screen 标题可能为空，类名为主）。

### 3.2 输入事件日志（P0-C 的核心）
- 订阅 `InputEvent.Key` / `InputEvent.MouseButton`（或 26.x 等效事件），每次打印：
```
[CSBOXTEST] key down code=<keycode> scan=<scancode> mods=<flags> screen=<类名>
[CSBOXTEST] mouse button=<0|1|2> action=<press|release> x=<fb> y=<fb> mods=<flags> screen=<类名>
```
- 用途：外部发输入 → `grep 'CSBOXTEST.*key'` 确认送达；顺带知道当时屏幕上是什么。
- 注意：若输入被上层窗口吃掉，游戏内根本不会打这行——这本身就是诊断信息。

### 3.3 等价命令（绕开 F2/F3 的注入难题）
- `/cst shot [name]`：调 `mc.grabPanoramica` 不行，用 `mc.getTextureManager` 无关——正确路径是客户端命令里执行 `minecraft.getMainRenderTarget()` + `Screenshot.saveScreenshot(...)`（或调 `F2` 键绑定的 `KeyMapping.click` 触发原版截图逻辑，输出到原版 screenshots 目录）。**文件命名含时间戳**，外部 `glob` 最新即可。
- `/cst pause [on|off|toggle]`：等价 Esc（`mc.setPause` 不适用客户端命令——用 `mc.options.pauseOnLostFocus` + 打开/关闭暂停菜单）。
- `/cst drop`：清空主手（测试干净起点）。

### 3.4 可点击元素导出（P1 增强，能省大量几何推算）
对 `Screen` 的组件树递归遍历：
```
[CSBOXTEST] widget class=<类名> name=<getMessage()|getString()> rect_fb=<x,y,w,h> click=<cx,cy>
```
- 对**原版组件**（AbstractWidget）直接反射 `getX/getY/getWidth/getHeight` + `isHovered`。
- 对**模组自绘 Screen**（无组件树）：退化为只输出 Screen 类名 + `mouse_fb`，由外部结合源码坐标使用——文档 `RUNTIME-UI-TESTING.md` 的按钮坐标表仍是权威。
- 实测意义：`CsboxBulkOverviewScreen` 的确认按钮坐标就不用再猜了。

### 3.5 模组事件埋点（P1-A）
- 订阅 CS2-Box 的关键点（按依赖方式二选一）：
  - 若依赖 csgobox：`PacketCsgoProgress` 相关类有事件/静态方法时直接接；
  - 若零依赖：hook `PacketEvent.Receive` 看类名含 `PacketCsgo*`，打印：
```
[CSBOXTEST] csgo_packet class=<名> size=<bytes> dir=<to_client|to_server>
[CSBOXTEST] csgo_progress complete=<n> total=<m> items=<首个物品注册名...>
```
- 不做内部逻辑干预（只读观测）。

### 3.6 环境一致性检查（P1 增强）
- `/cst check` 一次输出：窗口尺寸、GUI scale、`pauseOnLostFocus`、`pauseOnMinimize`、全屏状态、`overrideWidth` 等——防止外部脚本以为游戏没变、实际设置被手改过。

---

## 4. 输出规范（外部脚本消费协议）

- **前缀**：所有日志行 `[CSBOXTEST] `，聊天镜像 `[CSBOXTEST]` 前缀。
- **结构**：`key=value` 空格分隔，值含空格时用 `<>` 包裹，禁用 JSON 嵌套（避免转义地狱）。
- **坐标**：一律帧缓冲（fb）。外部换算：`screen = window_origin + fb * scale`，其中 `scale = window_height_fb / window_height_screen`（2:1 时即 fb/2）。
- **时机**：命令输出即时；事件日志无缓冲直接写。
- **不引入依赖**：单机、纯客户端、不要求对方装 csgobox（缺 csgobox 时 `/cst status` 只报 MC 原生信息）。

---

## 5. 验收清单（自测用例）

| # | 用例 | 期望 |
|---|---|---|
| 1 | 游戏内按 E 开背包 → `/cst status` | `screen=<InventoryScreen>`、`hotbar` 9 槽全列出 |
| 2 | `/give @s csgobox:csgo_box` → `/cst status` | `mainhand=csgobox:csgo_box count=1` |
| 3 | 外部发 `key code 53`(Esc) → grep 日志 | 出现 `key down code=53`，随后 `screen=<null>` |
| 4 | `/cst shot` → glob 截图目录 | 新文件出现，尺寸 = 帧缓冲 |
| 5 | 打开批量开箱确认屏 → `/cst widgets` | 输出按钮类名 + rect（若 Screen 用组件） |
| 6 | 无 csgobox 时 `/cst status` | 不崩，缺的字段留空 |

---

## 6. 建议实现顺序

1. §3.1 `/cst status` + §4 日志规范（打通「游戏内状态 → 外部」主干）
2. §3.2 输入事件日志（解决「输入有没有生效」最大的测试痛点）
3. §3.3 等价命令（shot / pause / drop）
4. §3.5 模组事件埋点（批量开箱观测）
5. §3.4 可点击元素导出 + §3.6 环境一致性检查

---

## 7. 与现有工作流的集成

- 更新 `docs/RUNTIME-UI-TESTING.md`：把「猜测当前 Screen / 猜测槽位物品」的像素步骤替换为 `/cst status` 断言；把「sleep 等渲染」替换为事件日志轮询。
- `docs/RELEASE.md` 质量门「运行时回归」条目加一条：先跑 `/cst check` 确认环境，再执行截图黄金路径。
- 脚本侧（`scripts/ui-driver/` 若固化）增加 `mc_status()` / `mc_wait_screen(<类名>)` / `mc_shot()` 三个原语，内部 = `osascript 输入 /cst ...` + `grep [CSBOXTEST]`。
