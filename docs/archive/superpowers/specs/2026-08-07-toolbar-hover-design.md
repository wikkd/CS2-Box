# CsLookItemScreen 底部工具栏悬停反馈设计

日期：2026-08-07
状态：已获用户批准（设计层面）

## 背景

`CsLookItemScreen`（开箱物品检视页）底部有 6 个工具栏按钮（检视武器模型/第一人称检视动画/检视人物模型/磨损度/贴纸/更改检视时的风景），当前交互仅有：

- 悬停时背景色瞬时变亮（无过渡、无提示）
- ⓘ 按钮点击切换信息面板

用户要求优化悬停交互：**tooltip 提示 + 图标反白高亮动画**，背景不动，保持 CS:GO 质感。

## 关键发现

图标贴图（`toolbar/*.png`，32×32）内容本身是**纯白色**（RGB 255,255,255），因此「反白」的正确实现是：

- 常态：`setShaderColor(0.55, 0.55, 0.55, 1)`（legacy）或 blit color `0xFF8C8C8C`（26.x），图标压暗为 55% 亮度
- 悬停：平滑提亮至纯白 `(1,1,1,1)` / `0xFFFFFFFF`
- 平台差异：1.21.x 用 `RenderSystem.setShaderColor`；26.x 用 `GuiGraphicsExtractor.blit` 的 color 参数（无 RenderSystem）

## 设计

### 1. 状态字段（11 平台同构）

```java
private int hoveredButton = -1;   // 当前悬停按钮 index，-1 = 无
private float toolbarGlow = 0F;   // 0→1 平滑系数
```

### 2. 悬停检测（renderToolbar 开头，复用传入 mouseX/mouseY）

```java
this.hoveredButton = -1;
for (int i = 0; i < icons.length; i++)
    if (isInside(mouseX, mouseY, toolbarButtonX(i), y, size, size)) this.hoveredButton = i;
```

### 3. glow 插值（现有 tick() 中追加，帧率无关）

```java
float target = this.hoveredButton >= 0 ? 1F : 0F;
this.toolbarGlow += (target - this.toolbarGlow) * 0.5F;   // ~200ms 趋近 90%
```

### 4. 图标反白（仅悬停图标，背景保持深灰不变）

legacy（1.21.x）：
```java
float b = active ? 1F : (i == this.hoveredButton ? 0.55F + 0.45F * this.toolbarGlow : 0.55F);
RenderSystem.setShaderColor(b, b, b, 1F);
guiGraphics.blit(icons[i], iconX, iconY, 0, 0, iconSize, iconSize, 32, 32);
```

26.x：
```java
int brightness = (int) (255F * (active ? 1F : (i == this.hoveredButton ? 0.55F + 0.45F * this.toolbarGlow : 0.55F)));
int color = 0xFF000000 | (brightness << 16) | (brightness << 8) | brightness;
guiGraphics.blit(RenderPipelines.GUI_TEXTURED, icons[i], iconX, iconY, 0F, 0F, iconSize, iconSize, 32, 32, color);
```

- 常态：图标 55% 亮度（融入深灰底）
- 悬停：平滑提亮至纯白
- ⓘ active（信息面板打开）：恒为全亮

### 5. Tooltip（按钮上方固定标签）

- 触发：`hoveredButton >= 0 && toolbarGlow > 0.05F`
- 位置：按钮 X 居中，`tipY = toolbarButtonY() - tooltipH - 6`
- 样式：暗色矩形 `fill(tipX, tipY, ...)`，ARGB alpha = `0xCC * toolbarGlow`，底 `0x101014`；文字 scale 0.7、色 `0xFFCCCCCC`，alpha 随 glow 淡入
- 文案：lang key `gui.csgobox.csgo_box.toolbar.<name>`，`renderText` 复用

### 6. 文案（common 共享 lang，一次改全平台生效）

```
gui.csgobox.csgo_box.toolbar.inspect → 检视武器模型 / Inspect Weapon Model
gui.csgobox.csgo_box.toolbar.gloves  → 第一人称检视动画 / First-Person Inspect Animation
gui.csgobox.csgo_box.toolbar.model   → 检视人物模型 / Inspect Character Model
gui.csgobox.csgo_box.toolbar.info    → 磨损度 / Wear Rating
gui.csgobox.csgo_box.toolbar.sticker → 贴纸 / Stickers
gui.csgobox.csgo_box.toolbar.more    → 更改检视时的风景 / Change Inspect Scenery
```

> **文案已永久确立（2026-08-20）**：以上 6 条 tooltip 文本由用户从 CS:GO 游戏内实际确认，替换早期临时文案（检视/手套/模型/信息/贴纸/更多）。后续改动须保持与 CS:GO 一致，不得回退为简写。

### 7. 平台合入策略（AGENTS.md 镜像纪律）

1. 基准：`v1_21_1` 先改（legacy 基准），编译通过
2. legacy 其余平台（v1_21_0 / v1_21_3 / v1_21_4 / v1_21_5 / v1_21_8 / v1_21_10）：同构合入（`setShaderColor` 风格）
3. new 基准：`v26_1_2`（blit color 风格），编译通过
4. v1_21_11 / v26_2 / forge_26_1_2：blit color 风格合入（注意 26.x blit 签名差异）
5. lang 只在 common 改一次
6. 每平台 `./gradlew :<module>:compileJava -Pactive_versions=<v>` 验证

## 测试

- 每平台 compileJava 通过（checkCommonArchitecture 自动挂载）
- 运行时人工验证：悬停各按钮 → 图标 120ms 内平滑变白 + 按钮上方淡入中文/英文 tooltip；移开鼠标 → 平滑恢复 55% 亮度、tooltip 淡出；ⓘ active 时图标常亮

## 不做的事（YAGNI）

- 不给 5 个死按钮（检视武器模型/第一人称检视动画/检视人物模型/贴纸/更改检视时的风景）分配功能——本次仅悬停反馈
- 不改背景 hover 变色（已移除，改为仅图标反白）
- 不加键盘快捷键
