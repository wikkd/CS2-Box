# CS2-Box × Blur（Motschen/Blur）背景模糊适配设计

日期：2026-08-10
状态：已批准（随本设计文档落地）
范围：3 平台（v1_21_1 / v26_1_2 / v26_2）GUI 背景适配；common 仅 OverlayColor 新增一色；服务端/网络零改动

## 背景

[Motschen/Blur](https://github.com/Motschen/Blur)（mod id `blur`）把 vanilla 的菜单背景模糊替换为可配置的动画模糊 + 渐变。它**不提供任何 mod API**，完全靠 mixin 钩住 vanilla 背景渲染路径：

- `Screen.extractBlurredBackground`（26.x）/ `renderBlurredBackground`（legacy）——模糊层
- `Screen.extractTransparentBackground` / `extractMenuBackground`（26.x）/ `renderTransparentBackground` / `renderMenuBackground`（legacy）——半透明渐变/菜单纹理
- `Options.getMenuBackgroundBlurriness()` 返回值 × 动画系数（`MixinOptions`），驱动模糊半径淡入/淡出

关键机制：**只有屏幕走 vanilla 背景调用路径，Blur 才生效**；屏幕一旦自己 override 掉这些方法且不调 super，Blur 的钩子完全不触发。

## 现状问题

| 屏幕 | 背景实现 | 装 Blur 后的问题 |
|---|---|---|
| CsboxScreen 主屏 | override `extractBackground`：in-level 只画不透明 `0xFF2a2a33` | Blur 钩子被绕过，完全不生效 |
| CsboxProgressScreen 进度屏 | override `extractBlurredBackground`：无条件模糊 + 半透明 `0x8C000000` | 26.x：override 绕过 mixin，Blur 淡入动画/半径设置不生效；legacy 反射调用真实方法，天然生效 |
| CsLookItemScreen / BulkOverview / BulkResult / Confirm | vanilla 路径（模糊+dirt 纹理）+ 不透明 `0xFF2a2a33` 覆盖 | Blur 在下方渲染但被完全盖住（无效渲染） |
| TerminalScreen | vanilla 路径 + 不透明 `0xFF08090A` | 同上，但保留不透明风格（设计决策） |

## 目标

1. 半透明背景处与 Blur 良好协作：模糊带淡入动画、尊重用户 blurriness 设置（**软适配，不强制加载 blur**，无依赖声明、除一处 `ModList.isLoaded` 外无 Blur 引用）。
2. 5 屏（主屏/出货/批量总览/批量结果/确认屏）背景从"不透明"改为"半透明主题灰"（新配置项，默认半透明），模糊透出。
3. 无 Blur 时行为退化到 vanilla：遵循 `menuBackgroundBlurriness` 选项（进度屏"强制模糊"特性保留）。

## 方案

**核心思路：让屏幕回归 vanilla 背景管线（Blur 钩子自然生效），CS2-Box 覆盖层改半透明。**

### 1. 配置（三平台 `config/CsboxConfig.java`，COMMON config）

新增 `ui` 配置节：

```java
builder.comment("UI settings").push("ui");
this.backgroundStyleValue = builder
        .comment("Screen background style: TRANSLUCENT = blurred world shows through (default), OPAQUE = solid dark panels")
        .defineEnum("backgroundStyle", BackgroundStyle.TRANSLUCENT);
builder.pop();
```

- `enum BackgroundStyle { OPAQUE, TRANSLUCENT }`
- 访问器 `backgroundStyle()`（与现有 `showItemNames()` 等模式一致）

### 2. common `OverlayColor`

新增 `getBackgroundTranslucent()` → `ColorTools.withAlpha(getBackgroundColor(), 0x8C)` = `0x8C2a2a33`（主题灰 @ alpha 140，与进度屏 0x8C 同透明度）。

### 3. 屏幕改动（每平台）

- **CsboxScreen**：删除 `extractBackground` override（回归 vanilla 管线）；全屏 fill 移入 `renderBg` 顶部（保持 in-level 守卫），颜色改配置驱动。
- **CsLookItemScreen / BulkOverview / BulkResult / Confirm**：全屏 fill 颜色改配置驱动（位置/守卫不变）。
- **CsboxProgressScreen（仅 26.x）**：`extractBlurredBackground` override 加 Blur 分支：

```java
protected void extractBlurredBackground(GuiGraphicsExtractor g) {
    if (ModList.get().isLoaded("blur")) {
        super.extractBlurredBackground(g);   // Blur 淡入动画 + 用户半径
    } else {
        AnimRenderOps.renderBlurredBackground(g); // 维持"无视选项强制模糊"现状
    }
}
```

- **TerminalScreen**：零改动（不透明风格保留）。

### 4. 已知取舍（已确认）

- 无 Blur 时 5 屏遵循 vanilla blurriness 选项（设 0 则无模糊）；进度屏强制模糊不受影响。
- 装 Blur 后其用户自配渐变叠在 CS2-Box 半透明层下方（Blur 对所有菜单的通用行为，玩家可自调 `gradientStartAlpha`）；不反射探测 Blur 内部状态。

## 实现纪律

- 26.x 先改 v26_1_2，gui 文件经 diff 确认镜像一致后合入 v26_2；legacy 独立适配（`renderBackground` 形态）。
- 无新增 AnimRenderOps op → 漂移检查不受影响。
- 改动涉及平台代码 → 三平台 `clean` 编译验证。

## 验证

- 三平台 clean 编译：`./gradlew :<m>:clean :<m>:compileJava -Pactive_versions=<v>`
- `scripts/check-animops-drift.sh` 通过
- `:common:test` 通过
- 运行时清单（docs/RUNTIME-UI-TESTING.md 增补）：Blur 装/不装 × OPAQUE/TRANSLUCENT × 7 屏
