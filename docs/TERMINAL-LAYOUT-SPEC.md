# TERMINAL-LAYOUT-SPEC — 终端机排版对照规范

> 单一事实源：终端机（TerminalScreen）的排版参数全部从 HTML 原型
> `design/terminal-chat.html` 提取。**实现与像素断言（TL 系列）都引用本表**，
> 禁止在代码中另立数值。
> 原型画布：`stage` 布局左栏 470px + gap 14 + 右栏 872px ≈ **1356px 宽 / 770px 高**。
> Java 帧缓冲：1708×960（26.1.2 dev 客户端），区域用分数映射（`px()/py()`），
> 与原型画布 1:1 对齐；**画布换算系数 = 1708/1356 ≈ 1.26**（本规范不放大字号，
> 字号以原型 px 为准，见 §1 换算）。

## 1. 字号对照表（font-size）

MC 默认字体字形 8px（1.0F scale = 8px 字形高）。换算公式：`scale = HTML_px / 8`。

| HTML 字号 | 用途（CSS 选择器） | 目标 scale | 现状（代码） | 差距 |
|---|---|---|---|---|
| 9px | `.r8-wear-tab`（磨损角标） | 1.125F | 0.95F（OfferRegion:140） | 偏小 |
| 10px | `.info`（ⓘ 圆点） | 1.25F | 1.0F（ActionBar:67） | 偏小 |
| 11px | `.sys-reject`（系统消息）、tooltip、hint | 1.375F | 1.3F（ChatRegion:195）、1.1F（ActionBar:189） | 偏小 |
| 12px | `.title-strip`、`.offer-info`、`.action-top`、`.cap-label/.opt`、`.r8-inspect`、`.r8-meta` | 1.5F | 1.2F~1.45F 散落 | 偏小 |
| 13px | `.bubble`、`.pill`、`.r8-rarity`、`.r8-wear` | 1.625F | 1.45F（ChatRegion:124）、1.1F（ActionBar:152） | 偏小 |
| 15px | `.r8-name`、`.r8-wearval`、`.xp-name` | 1.875F | 1.35F（OfferRegion:117）、1.6F（OfferRegion:163） | 偏小 |
| 21px | `.digits`（倒计时） | 2.625F | 1.8F（BottomRow:69） | 偏小 |

> 注：现状值普遍偏小约 20~40%——这就是「布局/视觉还原度」迭代要修正的
> 核心差距。改字号 scale 后同步检查 §4 容器尺寸（padding 容纳）。
## 2. letter-spacing 对照表（P2）

HTML 五档字距。`drawSpacedText` 逐字符绘制：字符间插入间距
`spacing_px`（帧缓冲像素，已含 scale 后的视觉宽度）。

| 字距 | 用途 | 目标 spacing_px（帧缓冲） |
|---|---|---|
| 0.5px | `.sys-reject`、`.cap-menu .opt`、`.digits`、`.r8-wear-tab` | 0.5 |
| 1px | `.action-top`、`.r8-meta` | 1 |
| 2px | `.title-strip`、`.pill`、`.r8-inspect`、`.r8-wear` | 2 |
| 3px | `.r8-rarity`、`.r8-wearval`、`.xp-name` | 3 |
| 4px | `.r8-name` | 4 |

> 字距与字号独立：字距是字符间固定像素间隔（不随 scale 放大）。
> 实现：`drawSpacedText(gg, font, text, x, y, spacingPx, scale, color)`。

## 3. 行高规则（P4）

| 元素 | HTML | 目标行高（帧缓冲 px） |
|---|---|---|
| 聊天气泡文本 | `line-height: 1.55` @13px | 13×1.55 ≈ 20px |
| 报价卡 offer-info | `line-height: 1.55` @12px | 12×1.55 ≈ 19px |
| tooltip | `line-height: 1.5` @11px | 11×1.5 ≈ 17px |

Java 现状（错误）：ChatRegion:133 `(textW > bw-20 ? 26 : 13) + 14` 硬编码；
OfferRegion 行距 `ROW_H = 15`。目标：气泡行高 20px（单行），双行 40px + padding；
报价卡 4 行行距 19px。

## 4. 容器 padding / gap 对照

| 元素 | HTML | 目标（帧缓冲 px） |
|---|---|---|
| 气泡内边距 | `padding: 8px 12px` | 上下 8、左右 12 |
| 聊天气泡最大宽 | `max-width: 82%` | 区域宽 × 0.82 |
| 消息间距 | `.msg gap: 9px; margin-bottom: 13px` | 条目间距 9（avatar 右缘到气泡），条目底部 13 |
| 打字点间距 | `.bubble.typing gap: 5px` | 点间距 5 |
| 系统消息 | `.sys-reject margin: 2px 0 13px` | 上下 2 / 下 13 |
| 头像 | `.avatar 34×34` | 34×34（现状一致） |
| 报价卡 thumb | `.thumb 96px` | 96×60（现状一致） |
| offer-info padding | `6px 10px 7px` | 上 6 左右 10 下 7 |
| 报价卡宽度 | `.offer-card 262px` | 262（现状一致） |
| 报价行间距 | `.offer-info line-height 1.55` | 19px（现状 15px 过密） |
| 操作条 padding | `.action-bar 8px 12px 11px` | 上 8 左右 12 下 11 |
| 操作条 gap | `.action-bottom gap: 10px` | 胶囊间距 10 |
| 胶囊 padding | `.pill 7px 20px` | 上下 7、左右 20 |
| 胶囊圆角 | `border-radius: 999px` | 胶囊高/2 |
| cap 下拉 min-width | `.cap-menu 122px` | 122 |
| 下拉行高 | `.opt padding 5px 10px` | 行高 ≈ 22px（现状一致） |
| 倒计时面板 | `.count-panel 154px` | 区域宽 × 0.111（现状 0.20 过宽，见 §5） |
| slot 面板 | `.slot-panel 102px` | 区域宽 × 0.073 |
| xp 面板 | `.xp-panel 595px` | 区域宽 × 0.428 |
| xp-body gap | `gap: 18px; padding: 0 18px` | 间距 18、左右 18 |
| xp-dots | `gap: 13px; padding: 6px 14px` | 组距 13、内边距 6/14 |
| dot 尺寸 | `.dot 10×10` | 10（现状 6 过小） |
| dot-group gap | `gap: 5px` | 点距 5（现状 14 过宽） |
## 5. 区域布局分数（TerminalScreen 现状，与原型对齐）

| 区域 | 原型编号 | Java 分数（现状） | 备注 |
|---|---|---|---|
| 左栏聊天 | 4+5 | x 0.020~0.358, y 0.122~0.873 | 对齐 |
| 操作条 | 6 | x 0.020~0.358, y 0.883~0.968 | 对齐 |
| 右栏报价 | 7+8 | x 0.370~0.998, y 0.122~0.853 | 对齐 |
| 底行三格 | 9+10+11 | x 0.370~0.998, y 0.863~0.968 | 对齐 |

底行内部三格（BottomRow 现状 vs 原型）：9 号倒计时面板宽取
`max(0.111 区域宽, 倒计时文本实际宽度 + 2×边框)` —— MC 默认字体数字宽度
(6px/字符 @scale1) 大于 Consolas 等宽，21px 字号(2.625F) 渲染
"02:23:57:45"(11 字符) 需 ≈173px，故 0.111(≈119px) 不够；当前 0.20(≈215px)
可容纳且不溢出。等宽字体还原前以文本宽度为准。10 号 slot 面板 0.073、
11 号 xp 面板 0.428。三格起点 x 偏移对齐 HTML `bottom-row gap: 14px`。

## 6. TL 像素断言基准（运行时 TL1~TL4 引用）

| ID | 断言 | 基准（帧缓冲 1708×960，26.1.2） |
|---|---|---|
| TL1 | 胶囊/系统消息/检视胶囊文本中心 vs 容器中心 | 误差 ≤ 2px |
| TL2 | 气泡文本右缘 < 气泡右内缘（气泡右内缘 = 气泡右 x - 12 padding） | 无溢出 |
| TL3 | 倒计时数字 10 秒内列位零漂移 | 同列数字 x 差 ≤ 1px |
| TL4 | 中英双语溢出无新增 | zh/en 各一轮，TL2 同过 |

> 断言前先固定世界（`docs/RUNTIME-UI-TESTING.md` §2）：
> `/time set day` + `doDaylightCycle false` + `weather clear` + `doWeatherCycle false`。

## 7. 实现纪律

- **font.width() 必须乘 scale** 才能用于居中/宽度计算（P1）：实际渲染宽
  = `Math.round(font.width(text) * scale)`。
- 新增字距调用只走 `RenderFontTool.drawSpacedText`（三平台同步，
  不属 AnimRenderOps 13-op 冻结范围，但需镜像纪律 + 每平台编译）。
- 排版参数只读本表，禁止在 4 区域散落 magic 数字。
- 改动以 v26_1_2 为基准 → 定点合入 v26_2 / v1_21_1（禁整文件覆盖）。
