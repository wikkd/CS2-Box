# CS2-Box 26.1.2 GUI 修复指南

## 文档目标

本文档面向研发工程师，聚焦 `v26_1_2` 平台下 CS 开箱相关 GUI 的实际缺陷、技术债务与修复路径。分析依据包括：

- 当前代码实现：`v26_1_2/src/main/java/com/reclizer/csgobox/v26_1_2/gui/`
- GUI 渲染辅助：`v26_1_2/src/main/java/com/reclizer/csgobox/v26_1_2/utils/`
- 自定义 3D 预览渲染链路：`v26_1_2/src/main/java/com/reclizer/csgobox/v26_1_2/gui/pip/`
- 已提供的 4 张运行截图（主开箱界面、局部物品列表、未配置状态、结果查看界面）

本文档按问题驱动组织，不假设当前实现只存在“样式偏差”。从截图和代码现状看，问题已覆盖功能可用性、布局稳定性、渲染一致性和维护性四个层面。

---

## 1. 问题概述

### 1.1 `P0` 级问题：功能可用性或核心视觉表达失效

#### P0-1 结果查看界面主预览尺寸与锚点错误

现象：

- 结果界面中的主物品预览明显过小。
- 物品存在被裁切、偏移、未处于视觉中心的情况。
- 界面的大面积留白没有被主结果承接，导致“开奖结果”页失去焦点。

影响：

- 用户无法稳定查看开箱结果。
- 结果页的主视觉目标失效，降低反馈强度。
- 该问题已从“美观问题”升级为核心功能表达失败。

关联实现：

- `CsLookItemScreen`
- `GuiItemMove`
- `Icon3DRenderState`
- `Icon3DRenderer`

#### P0-2 结果页返回按钮文本可见性失效

现象：

- 截图中右下角返回按钮仅剩红色底块，文本不可见或被覆盖。

影响：

- 用户无法稳定识别按钮功能。
- 这是直接影响交互可发现性的缺陷。

关联实现：

- `CsLookItemScreen`
- `RenderFontTool`

#### P0-3 未配置状态提示覆盖主预览区

现象：

- “此箱子未配置，请联系管理员”提示以大面积黑底红字直接覆盖箱子主预览。
- 错误态与正常态视觉结构未分离。

影响：

- 主内容区被错误提示侵占。
- 错误信息虽然可见，但界面语义混乱，用户无法判断当前仍可执行哪些动作。

关联实现：

- `CsboxScreen`

### 1.2 `P1` 级问题：布局不稳定、信息层级混乱、易触发回归

#### P1-1 主开箱界面整体依赖百分比硬编码布局

现象：

- 标题、预览物品、物品网格、钥匙信息、按钮区都直接依赖 `width * N / 100`、`height * N / 100`。
- 同一 screen 内缺少“标题区 / 预览区 / 列表区 / 操作区”的容器分层。

影响：

- 不同分辨率、不同文本长度、不同物品数量下极易错位。
- 空状态、异常状态、长文案状态无法共享稳定布局规则。

关联实现：

- `CsboxScreen`
- `CsLookItemScreen`
- `CsboxProgressScreen`

#### P1-2 文本排版缺少限宽、截断和状态化策略

现象：

- 物品名称、箱子副标题、错误文案、按钮文字都依赖固定缩放或裸绘。
- 长文本时没有统一截断、省略号、换行或缩放下限策略。

影响：

- 中文长名称和配置错误提示容易溢出。
- UI 在内容密度增加时会立即失稳。

关联实现：

- `CsboxScreen`
- `CsLookItemScreen`
- `RenderFontTool`

#### P1-3 物品卡片内图标视觉基线不一致

现象：

- 护甲、靴子、工具、剑的视觉中心和落点明显不一致。
- 同行卡片的物品看起来像“浮在不同高度上”。

影响：

- 物品网格的专业感和一致性被破坏。
- 稀有度、内容密度和可读性都被削弱。

关联实现：

- `IconListTools`

### 1.3 `P2` 级问题：风格割裂、深色主题层级不足、交互语义不完整

#### P2-1 按钮色彩过于原始，缺少状态层级

现象：

- 开启按钮使用高饱和纯绿，返回按钮使用高饱和纯红。
- 与整体深灰界面风格割裂。
- 缺少 disabled、warning、hover、pressed 的一致语义系统。

影响：

- 成品感弱，像调试阶段 UI。
- 未来增加更多按钮状态时缺少可扩展性。

关联实现：

- `CsboxScreen`
- `CsLookItemScreen`

#### P2-2 深色背景仅有单层底色，缺少面板层次

现象：

- 背景主要由纯深灰和少量线条构成。
- 标题、预览、物品网格、按钮缺少面板或局部承托。

影响：

- 深色模式只有“暗”，没有层级。
- 主次关系需要完全依赖文字和坐标，鲁棒性差。

关联实现：

- `OverlayColor`
- `CsboxScreen`
- `CsLookItemScreen`
- `CsboxProgressScreen`

---

## 2. 根因分析

### 2.1 布局层根因：当前 GUI 没有容器化布局抽象，完全依赖像素百分比直算

这是当前 GUI 缺陷的最核心根因。

从 `CsboxScreen` 和 `CsLookItemScreen` 可以看出，几乎所有元素位置都以如下方式决定：

- `this.width * 25 / 100`
- `this.height * 94 / 100`
- `this.width * 37 / 100`
- `this.height * 53 / 100`

这种写法在早期快速移植时可以成立，但它有三个结构性问题：

1. 坐标是“元素级”的，不是“区域级”的。
2. 同一界面中的标题、预览、物品列表、操作区之间没有共享边界模型。
3. 状态切换时只能继续往当前坐标系上“叠加例外逻辑”，不能切换到另一套稳定版式。

直接后果：

- 空状态只能通过增加一条警告 banner 覆盖在原布局上，而不是切换到 empty state。
- 结果页复用了与主界面近似的预览坐标思路，但场景目标完全不同，导致主物品没有真正居中。
- 按钮、钥匙、分隔线之间没有统一基线，只是“看起来差不多在同一行”。

这属于架构层面的布局债务，不是单点坐标写错。

### 2.2 渲染层根因：26.1.2 GUI 管线变化后，文本与物品的层级和锚点策略未统一重建

26.1.2 下 GUI 入口已经迁移到 `extractRenderState(...)`，并且项目为保留 3D 预览能力，引入了：

- `GuiGraphicsExtractor`
- 自定义 `PictureInPictureRenderState`
- `Icon3DRenderer`

这条链路恢复了部分 3D 展示能力，但当前问题在于：

1. 主预览物品使用自定义 PIP 渲染；
2. 网格物品使用普通 2D item 渲染；
3. 文本使用 `RenderFontTool` 在 2D pose 上绘制；
4. 背景和按钮底色通过 `fill` 直接落在当前绘制顺序中。

如果这些元素没有统一的 z-order 与绘制阶段约束，就会出现：

- 文本被按钮底色覆盖；
- 警告 banner 虽然被人为抬层，但布局语义仍然错误；
- 不同渲染路径下的物品视觉包围盒不一致。

结果页按钮文案消失，就是该问题的直接表现之一：`CsLookItemScreen` 当前先绘制文字，再绘制按钮底色，说明其绘制顺序仍停留在“旧路径下可以靠 z 抬层修补”的思路上，但 26.1.2 现有实现不再天然提供这种容错。

### 2.3 文本系统根因：当前文本工具只解决“能画出来”，没有承担排版约束职责

`RenderFontTool` 当前职责主要是：

- 在给定坐标画字；
- 支持缩放；
- 返回基础宽度。

但它没有解决以下问题：

- 限宽绘制；
- 截断策略；
- 省略号策略；
- 居中时使用的是逻辑宽度还是真实缩放宽度；
- 不同状态文案的字号下限；
- 中文长文本在深色背景中的最小可读边距。

因此调用方只能各自手工计算：

- `middleOf(...)`
- `font.width(text) * scale`
- `warnWidth = font.width(...) * 1.2F`

这种“调用方各算各的”模式，会让文本布局逻辑分散在多个 screen 中，且每个 screen 只覆盖自己当前的 happy path。只要换一段文本或换一个状态，排版就会崩。

### 2.4 物品网格根因：当前图标布局是固定偏移，不是基于视觉包围盒的居中

`IconListTools` 中的卡片绘制目前本质上是：

- 算一个 frame；
- 给出固定 `itemX/itemY`；
- 用固定 scale 渲染任意物品。

但 Minecraft 物品模型的视觉包围盒并不统一：

- 剑、镐、锄等细长武器视觉重心偏斜；
- 靴子、护腿、胸甲的模型占位完全不同；
- 一些物品天然在 GUI 中上下留白更多。

如果布局系统不做“视觉居中补偿”，统一 scale 只会放大不一致。截图里靴子和武器基线不一致，就是这个问题的外显结果。

这不是单个 item texture 异常，而是渲染策略本身没有做多形态适配。

### 2.5 状态管理根因：正常态、空态、异常态、结果态使用了同一套布局骨架

当前 screen 代码组织方式更接近：

- 先画正常态布局；
- 再根据状态加一些条件性元素。

而不是：

- 根据状态选择不同布局骨架；
- 再在该骨架内渲染内容。

这会导致：

- `boxEmpty` 时只是多了一条警告，不会自动收缩/替换物品网格。
- 结果页虽然场景已从“浏览箱子内容”变成“展示单个结果”，但版式仍然延续“列表页思路”。
- 功能上不同的 screen 虽然分了类，但内部版式仍以“局部修补”为主。

这属于设计层面的状态建模不足。

### 2.6 视觉系统根因：当前颜色与组件没有形成统一设计 token

目前颜色来源主要分散在：

- 稀有度颜色；
- 背景覆盖色；
- 直接硬编码的按钮色；
- 文本白色/红色。

缺少统一约束，例如：

- surface background
- panel background
- divider
- primary action
- danger action
- disabled action
- text primary / secondary / danger

结果是：

- 主界面深色气质与按钮高饱和纯色冲突；
- 错误提示色非常抢眼，但没有对应的容器系统与辅助文本层级；
- 颜色更多在“告诉代码怎么填色”，而不是“表达组件状态和信息层级”。

---

## 3. 修复重难点

文档第 2 节把根因分为五个层面。其中前两个层面的问题（布局层和渲染层）已经在近期的重构提交中分别收敛：

- 渲染层：`RenderFontTool` 已统一为 2D `pose().translate + scale` 路径，`IconListTools` 已切换到 `guiGraphics.item(...)` 2D 渲染，主预览通过自定义 `Icon3DRenderer` 走 PIP 3D 管线，避免了与网格物品的视觉不一致。
- 布局层：百分比硬编码仍是事实，但近期对 `CsboxScreen.previewTextureSize` / `previewPixelX` / `previewPixelY` 的收敛，让主预览的几何计算可复用、可被 `mouseDragged` 复用，避免了“坐标各处一算”带来的视觉与交互脱节。

本轮修复聚焦在“以上重构尚未触达、且视觉与功能表达已明显失败”的具体问题上，重难点如下：

### 3.1 P0-2：按钮文本被遮挡，根因在 `extractRenderState` 调用顺序

`CsLookItemScreen.extractRenderState` 当前调用顺序：

```java
renderLookBackground(guiGraphics);
renderLabels(guiGraphics);     // 标题、稀有度、按钮文案
renderBg(guiGraphics, mouseX, mouseY);  // 分隔线、稀有度条、主预览、按钮矩形
```

按钮矩形在 `renderBg` 中绘制，文案在 `renderLabels` 中绘制。由于 `renderBg` 在 `renderLabels` 之后调用，矩形会覆盖文案。这是 P0-2 的直接成因。

修复难点：不能简单地把按钮文案移到 `renderBg` 中，因为 `renderBg` 已经决定了按钮的 hover 态颜色（hover 时边框与内填都换），文案应该跟随这个颜色信号移动。最干净的修法是交换两者的调用顺序，并让 `renderLabels` 在视觉上层（标题、稀有度文案），而按钮文案归入按钮自身绘制的下半段。

### 3.2 P0-1：结果页主预览几何无容器复用

`CsboxScreen` 已经把主预览几何收敛成 `previewTextureSize` / `previewPixelX` / `previewPixelY` 三个方法，结果页 `CsLookItemScreen.renderBg` 仍然使用裸的 `this.width * 37 / 100, this.height * 30 / 100` 直绘坐标。

修复难点：

- 结果页布局与开箱页不同（标题/稀有度条在上、按钮在下，没有物品网格），不能完全照搬 `CsboxScreen` 的容器边界，但需要同样的“基于百分比生成可见区域，再把物品贴到区域中心”的逻辑。
- 主预览的 frameWidth 与 scale 系数必须和 `CsboxScreen` 保持完全一致（`width * 26 / 100` 的 frame、`* 60 / 100` 的 scale），否则物品视觉大小会与开箱页主预览不同。
- 必须确保 `mouseDragged` 中使用的命中区域与 `renderBg` 中实际渲染的位置严格对齐，否则会出现“拖到空白区域也能转 / 拖到物品上转不动”的回归。

### 3.3 P0-3：未配置态 banner 覆盖主预览

`CsboxScreen` 中 `renderBg` 始终会渲染 3D 主预览，`renderLabels` 在 `boxEmpty == true` 时再在预览区域上盖一条黑底红字 banner。两层叠加导致用户既看不清箱子，也看不清 banner。

修复难点：

- 不能直接删掉 banner，因为它是唯一能让用户理解“箱子没有配置”的反馈。
- 不能简单地把 banner 抬到屏幕顶端，因为那条 banner 文案较长，抬到顶端后会与标题冲突。
- 最合理的方案是：让 `boxEmpty` 成为 `renderBg` 的一个分支——不渲染主预览，但保留 banner，并把 banner 居中到原本预览占据的区域。这样 banner 获得了完整的视觉中心，未配置态的反馈强度反而更高。

### 3.4 P1/P2 的本轮处理策略

P1-1（百分比硬编码布局）、P1-2（缺文本限宽）、P1-3（物品视觉基线）、P2-1（按钮色系）、P2-2（缺面板层级）都属于结构性改进，需要在“现有 screen 内加入容器抽象 / 设计 token 系统”之后才能稳定落地。本轮不在此范围内。

其约束已被显式记录在第 4 节“未在本轮修复”的清单里，避免被误以为“已经做完”。

---

## 4. 修复方案

本轮修复仅覆盖三个 P0 问题。P1/P2 不在本轮范围内（见 4.4）。

### 4.1 P0-2：交换 `extractRenderState` 中 `renderBg` 与 `renderLabels` 的调用顺序

**文件**：`v26_1_2/src/main/java/com/reclizer/csgobox/v26_1_2/gui/CsLookItemScreen.java`

**改动**：

```java
@Override
public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks) {
    super.extractRenderState(guiGraphics, mouseX, mouseY, partialTicks);
    renderLookBackground(guiGraphics);
    renderBg(guiGraphics, mouseX, mouseY);   // 按钮矩形先画
    renderLabels(guiGraphics);                // 标题、稀有度、按钮文案在上层
}
```

由于标题/稀有度文案位于 y = 5% / 11%，按钮位于 y = 94%，两者在垂直方向不重叠；按钮文案只在按钮矩形内绘制，所以交换顺序对其他元素无副作用。

### 4.2 P0-1：复用 `CsboxScreen` 的预览几何策略，让结果页主预览居中

**文件**：`v26_1_2/src/main/java/com/reclizer/csgobox/v26_1_2/gui/CsLookItemScreen.java`

**改动**：

1. 抽取与 `CsboxScreen` 共享的三个几何方法（保留独立实现，避免跨 screen 静态耦合）：

```java
private int previewTextureSize() {
    int frameWidth = this.width * 26 / 100;
    float scale = frameWidth * 60F / 100F / 16F;
    return Math.max(1, Math.round(16.0F * scale));
}

private int previewPixelX() {
    return (this.width - previewTextureSize()) / 2;
}

private int previewPixelY() {
    // 容器 = 稀有度条（16%）到分隔线（92%）之间的垂直带，居中。
    int containerTop = this.height * 16 / 100;
    int containerBottom = this.height * 92 / 100;
    return (containerTop + containerBottom - previewTextureSize()) / 2;
}
```

2. `renderBg` 中改为：

```java
GuiItemMove.renderItemInInventoryFollowsMouse(
        guiGraphics, previewPixelX(), previewPixelY(),
        this.rotX, this.rotY, openItem, this.player, scale);
```

3. `mouseDragged` 中命中区域改为同一对 `previewPixelX/Y + previewTextureSize()`，确保拖动检测与可视区域一致。

4. 删除 `renderBg` 中已不再需要的 `this.width * 37 / 100` 裸坐标。

**注意事项**：保持 frameWidth（`width * 26 / 100`）和 scale 系数（`* 60 / 100 / 16`）与 `CsboxScreen` 严格一致，避免两个屏幕之间出现“同一个物品大小不一样”的视觉跳变。

### 4.3 P0-3：未配置态跳过主预览，banner 居中到原预览区域

**文件**：`v26_1_2/src/main/java/com/reclizer/csgobox/v26_1_2/gui/CsboxScreen.java`

**改动**：

1. `renderBg` 中，在调用 `GuiItemMove.renderItemInInventoryFollowsMouse` 前增加守卫：

```java
if (!boxEmpty) {
    if (this.entity != null) {
        GuiItemMove.renderItemInInventoryFollowsMouse(
                guiGraphics, previewPixelX(), previewPixelY(),
                this.itemRotX, this.itemRotY, itemMenu, this.entity, scale);
    }
}
```

2. `renderLabels` 中的 banner 分支保持当前的中心化布局（`bgX0/bgX1` 已经按屏幕宽度居中），但 `bgY0` 从当前的 `height * 23 / 100 - 6` 改为 `height * 32 / 100 - 6`（与 `previewPixelY()` 的中心 32.5% 对齐），让 banner 直接坐落在原本主预览占据的区域。

3. 由于主预览已被跳过，banner 不再需要 `nextStratum()` 抬层；保留它也无害，作为对未来 `renderBg` 中可能新增纹理叠层的防御。

### 4.4 本轮**不**修复的问题

- **P1-1 容器化布局抽象**：属于架构层重构，会改动 `CsboxScreen` / `CsLookItemScreen` / `CsboxProgressScreen` 三个 screen 的全部坐标来源，并影响 `IconListTools` 的相对位置参数。预计工作量超过本轮迭代。
- **P1-3 物品视觉基线**：需要在 `IconListTools` 中对每类物品做视觉包围盒检测并动态调整 `itemX/itemY`；超出本轮范围。
- **P2-2 面板层级**：需要先确定 surface/panel/divider 三层颜色 token，再决定是否在 screen 背景之上叠加 panel；同上，本轮不动。

---

### 4.5 P2-1 按钮色系 token（第二轮完成）

**新增文件**：`v26_1_2/src/main/java/com/reclizer/csgobox/v26_1_2/utils/ButtonPalette.java`

`ButtonPalette` 集中所有按钮色，并提供 `drawButton(...)` 渲染辅助：

- `Style` record：六字段（`fill` / `fillHover` / `border` / `borderHover` / `textColor` / `textColorHover`）。
- `OPEN` 常量（forest-green panel，`#1F6B33` 填充 / `#2A8042` hover 填充）。
- `DANGER` 常量（brick-red panel，`#6B1F1F` 填充 / `#802A2A` hover 填充）。
- `drawButton(guiGraphics, style, x, y, w, h, hover)`：按 hover 状态绘制外边框 + 内填充，返回应使用的文字色。
- `isInside(mouseX, mouseY, x, y, w, h)`：统一的按钮命中检测。

**修改文件**：

- `CsboxScreen.java`
  - 删除本地 `drawButton` 与硬编码色（`0xFF00AA00` / `0xFF00FF00` / `0xFFAA0000` / `0xFFFF0000`）。
  - 拆出 `drawOpenButton` / `drawBackButton`，各自接入 `ButtonPalette.OPEN` / `ButtonPalette.DANGER`，并用 `gx, gy` 做 hover 检测。
  - `renderCenteredText` 签名增加 `int color` 参数；`renderLabels` 计算 hover 状态并用 `Style.textColor(...)` / `textColorHover()` 渲染按钮文案。
- `CsLookItemScreen.java`
  - 删除本地 `isInside`。
  - 删除按钮硬编码色（`0xFFFF4444` / `0xFFFF0000` / `0xFFCC4444` / `0xFFAA0000`）。
  - `renderBg` 中用 `ButtonPalette.drawButton(DANGER, ..., hoverButton)` 替换原 `fill` 链。
  - `renderLabels` 签名增加 `int mouseX, int mouseY`，按钮文案走 hover-aware 文字色。
  - `renderCenteredText` 签名同步增加 `int color` 参数。
  - `mouseClicked` 改用 `ButtonPalette.isInside`。

### 4.6 P1-2 文本限宽与省略号（第二轮完成）

**修改文件**：

- `RenderFontTool.java`
  - 新增 `drawStringClamped(guiGraphics, font, String, x, y, ox, oy, scale, maxPixelWidth, color)`：若 `font.width(text) * scale > maxPixelWidth`，用二分搜索找到最长前缀并加 `"…"` 后缀。
  - 新增 `drawStringClamped(guiGraphics, font, Component, ...)` 重载，对 `Component.getString()` 走相同流程。
  - `maxPixelWidth` 单位为已缩放像素（与 `font.width * scale` 同量纲）。
- `CsboxScreen.java`
  - 物品网格物品名（scale 0.6F）：限宽到 `width * 9%`（单格视觉宽度）。
  - 箱子副标题中的箱子物品名（scale 0.8F）：限宽到 `width * 50%`（右半屏）。
- `CsLookItemScreen.java`
  - 主物品标题（scale 1.8F）：限宽到 `width * 55%`（从 `width * 45%` 锚点起算的右半屏）。

### 4.7 第二轮**不**修复的问题

- **P1-1 容器化布局抽象**：与第一轮相同的延期理由。
- **P1-3 物品视觉基线**：需要在 `IconListTools` 中对每类物品做视觉包围盒检测；3D PIP 路径已自动居中（`Icon3DRenderer` 的 `translate(0.1875, 0.1875, 0.1875)` 把模型包围盒拉回原点），2D 网格仍需 per-item 模型形状适配，超出本轮。
- **P2-2 面板层级**：本轮新增了按钮 panel，但屏幕级 surface/panel/divider 三层结构仍待后续统一设计。

---

## 5. 验证标准

### 5.1 编译验证

```bash
cd /Users/shuangyuexingxun/Desktop/CS2-Box
./gradlew :v26_1_2:compileJava
```

通过条件：

- BUILD SUCCESSFUL，无 ERROR。
- 没有新增 `WARN: [deprecation]` 或 `unchecked`（除已存在项外）。

### 5.2 代码静态核对

- `CsLookItemScreen.extractRenderState` 中 `renderBg` 调用必须在 `renderLabels` 之前。
- `CsLookItemScreen` 中 `previewPixelX/Y/previewTextureSize` 必须被 `renderBg` 与 `mouseDragged` 同时使用，二者计算的 `x/y/size` 必须完全一致。
- `CsboxScreen.renderBg` 中 `GuiItemMove.renderItemInInventoryFollowsMouse` 调用前必须有 `if (!boxEmpty)` 守卫。
- 三个被改动的 screen 内不应引入新 import（已用 import 集合已覆盖所需 API）。

### 5.3 运行时视觉验收（需要在 v26.1.2 客户端实跑）

> 这一节基于截图与代码静态分析得出。运行验证需要启动 `:v26_1_2:runClient` 实地确认；以下为预期行为。

#### 5.3.1 P0-2 验收

- [ ] 在持有任意 `csgo_box` 并触发开箱动画后，等待滚动结束进入结果查看界面。
- [ ] **预期**：右下角返回按钮的白色加粗文案清晰可见，不被矩形覆盖。
- [ ] **若 FAIL**：`renderBg` 与 `renderLabels` 顺序仍错，或按钮文案再次移到 `renderLabels` 之内。

#### 5.3.2 P0-1 验收

- [ ] 在结果查看界面观察主物品预览。
- [ ] **预期**：物品在屏幕水平方向严格居中；垂直方向位于稀有度条（屏幕约 16%）与分隔线（约 92%）之间的几何中心。
- [ ] **预期**：物品占用的像素宽度约为屏幕宽度的 15.6%（与开箱页主预览一致：`26% * 60%`）。
- [ ] **预期**：在物品范围内按住左键拖动可旋转物品；在物品外拖动不触发旋转。
- [ ] **若 FAIL**：`previewPixelX/Y` 计算有误，或 `mouseDragged` 仍使用旧的 `width * 37%` 命中区域。

#### 5.3.3 P0-3 验收

- [ ] 加载一个未配置的箱子（让 `BoxJsonLoader` 跳过该箱子，或临时移除对应 JSON）。
- [ ] 触发该箱子的开箱界面。
- [ ] **预期**：屏幕中央出现黑底红字的“此箱子未配置，请联系管理员”提示；不再渲染 3D 箱子预览；提示占据原本预览所在的几何中心区域。
- [ ] **若 FAIL**：banner 仍覆盖在 3D 预览之上；或主预览仍在 boxEmpty 时被渲染。

### 5.4 回归保护

- 已配置的箱子（`weapon_supply_box.json` 等）开箱体验必须与本轮修改前一致：
  - 物品网格 2 行 × 10 列布局不变。
  - 主预览的拖动旋转行为不变。
  - “开启”/“返回”按钮的位置、颜色、可点击区域不变。
- `CsboxProgressScreen` 的滚动动画时序（`easedScroll` + `targetScroll` 计算）不变。

### 5.5 第二轮验证（按钮色系 + 文本限宽）

#### 5.5.1 P2-1 按钮色系验收

- [ ] 在 `CsboxScreen` 上分别 hover“开启”和“返回”按钮。
- [ ] **预期**：hover 时按钮 fill 和 border 都变亮，文案文字色变亮（`textColorHover`）。
- [ ] **预期**：按钮色与深灰背景（`OverlayColor.getBackgroundColor() = #333333`）协调，不再是刺眼的纯绿/纯红。
- [ ] **若 FAIL**：`ButtonPalette.drawButton` 调用缺 hover 参数；或 `renderLabels` 中 `buttonTextColor` 未同步切换。

#### 5.5.2 P1-2 文本限宽验收

- [ ] 在 `CsboxScreen` 上把 `showItemNames = true`，并把网格中放一个超长名称的箱子（比如把其中一个物品替换为 verbose 名称）。
- [ ] **预期**：物品名以 `"…"` 结尾，单格内不越界。
- [ ] **若 FAIL**：`maxPixelWidth` 算错（应等于视觉宽度 `width * 9%`，而非 `width * 9% / scale`），或 `drawStringClamped` 没被调用。
- [ ] 在 `CsLookItemScreen` 上设置一个超长名称的开箱奖励。
- [ ] **预期**：标题以 `"…"` 结尾，不超过屏幕右边缘。
- [ ] **若 FAIL**：同上。

### 5.6 非目标

以下行为在第二轮**仍**不做验证也不修改：

- 屏幕级 surface / panel / divider 三层结构的统一设计 token。
- 容器化布局抽象（P1-1）。
- 物品图标在不同模型形状下的视觉基线对齐（P1-3）。
- 按钮的 disabled / pressed 态（P2-1 当前只有 hover）。
- 警告 banner（`gui.csgobox.csgo_box.label_not_configured`）的限宽处理：第一轮已让 banner 居中到原预览区域，但横幅本身在窄窗口下仍可能溢出。该问题属于 P2-2 面板层级，超出本轮范围。
