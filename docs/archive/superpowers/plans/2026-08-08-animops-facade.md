# AnimRenderOps 动画渲染门面 — 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 10 个平台的开箱/出货/批量动画渲染版本差异收敛到每平台唯一的 `utils/AnimRenderOps.java` 门面，屏幕与助手只调门面，使动画逻辑层版本无关、缺陷单点修复、新版本接入成本降为"写一个门面"。

**Architecture:** 屏（动画逻辑，版本无关）→ 逻辑助手（IconListTools/GuiItemMove/RenderFontTool，保留布局/旋转数学）→ `AnimRenderOps`（唯一版本差异点，5 时代变体 × 10 平台）。门面守原语级（draw 调用），功能级逻辑（透镜切带/缓动/音效阈值）留在屏内。TaczInspectViewport（1.21.1）为独立路径不入门面。

**Tech Stack:** Java 21/25 + NeoForge（1.21.0-26.2）、Gradle wrapper 9.5.1、bash（漂移检查脚本）。

**Spec:** `docs/superpowers/specs/2026-08-08-animops-facade-design.md`

## Global Constraints

- 每次 Gradle 调用只能构建一个 MC 版本：`./gradlew :<module>:compileJava -Pactive_versions=<v>`
- 涉及平台改动**必须 clean 编译**（增量缓存可能造假象）
- `common/` 禁止 import MC 类——门面全部在平台模块内
- 镜像纪律：门面文件有时代差异，**禁止 `mirror.sh --force` 跨时代覆盖**；仅同时代对（1.21.0↔1.21.1、26.1.2↔26.2）可镜像
- 门面文件头注释必须有 `// era: <legacy|mid|renderpipeline|pip|decoupled>`（漂移脚本依赖）
- **视觉零变化**：每个时代门面实现 = 该时代现有代码逐字搬移，不"顺手修"轴映射/数值
- 1.21.1 / forge_1_20_1 TACZ 分支（`TaczInspectViewport`）不并入 `renderItem3D`；默认 3D 展示走 `renderItem3D → renderGunModel3D`
- 不提交任何依赖 jar / 构建产物；提交信息沿用仓库风格（`feat|refactor|docs|build|chore` 前缀）

---

## Phase 1：基线门面（v1_21_1 legacy + v26_1_2 decoupled）

### Task 1.1: 创建 legacy 门面（v1_21_1）

**Files:**
- Create: `v1_21_1/src/main/java/com/reclizer/csgobox/v1_21_1/utils/AnimRenderOps.java`

**Interfaces:**
- Produces: 门面公开 API（全部 static）——`blitTextured(GuiGraphics, ResourceLocation, int x, int y, int w, int h)`、`fill(GuiGraphics, int x0, int y0, int x1, int y1, int color)`、`fillGradient(GuiGraphics, int x0, int y0, int x1, int y1, int c0, int c1)`、`scissor(GuiGraphics, int x, int y, int w, int h)`、`scissorDisable(GuiGraphics)`、`setBlendNormal(GuiGraphics)`、`flush(GuiGraphics)`、`renderBlurredBackground(Screen, GuiGraphics, float partialTicks)`、`renderItem2D(LivingEntity, GuiGraphics, ItemStack, float x, float y, float scale)`、`renderItem3D(GuiGraphics, ItemStack, LivingEntity, int cx, int cy, float angleXComponent, float angleYComponent, float scale)`、`supports3D()`（返回 true）

- [ ] **Step 1: 写门面文件**（完整代码，legacy 变体）

```java
package com.reclizer.csgobox.v1_21_1.utils;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4fStack;

/**
 * Single per-platform adaptation point for animation rendering primitives.
 * Screens and logic helpers (IconListTools / GuiItemMove) must call ONLY
 * through this class; version-varying render API lives here and nowhere else.
 * era: legacy
 */
public final class AnimRenderOps {
    private static final PoseStack REUSABLE_POSE_STACK = new PoseStack();

    private AnimRenderOps() {
    }

    /** Immediate-mode blit. Forces SRC_ALPHA: the 8-arg blit inherits whatever
     *  blend func is current, so translucent textures (spot glow, lens
     *  vignette) would otherwise render as hard opaque discs. */
    public static void blitTextured(GuiGraphics gg, ResourceLocation tex, int x, int y, int w, int h) {
        gg.flush();
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(770, 771, 1, 771);
        gg.blit(tex, x, y, 0, 0, w, h, w, h);
    }

    public static void fill(GuiGraphics gg, int x0, int y0, int x1, int y1, int color) {
        gg.fill(x0, y0, x1, y1, color);
    }

    public static void fillGradient(GuiGraphics gg, int x0, int y0, int x1, int y1, int c0, int c1) {
        gg.fillGradient(x0, y0, x1, y1, c0, c1);
    }

    public static void scissor(GuiGraphics gg, int x, int y, int w, int h) {
        gg.enableScissor(x, y, x + w, y + h);
    }

    public static void scissorDisable(GuiGraphics gg) {
        gg.disableScissor();
    }

    public static void setBlendNormal(GuiGraphics gg) {
        gg.flush();
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(770, 771, 1, 771);
    }

    public static void flush(GuiGraphics gg) {
        gg.flush();
    }

    public static void renderBlurredBackground(Screen screen, GuiGraphics gg, float partialTicks) {
        screen.renderBlurredBackground(partialTicks);
    }

    /** 2D item icon centred at (x, y), scaled (16px per block unit). */
    public static void renderItem2D(LivingEntity entity, GuiGraphics gg, ItemStack stack, float x, float y, float scale) {
        BakedModel model = Minecraft.getInstance().getItemRenderer().getModel(stack, entity.level(), entity, 0);
        PoseStack pose = gg.pose();
        pose.pushPose();
        pose.translate(x, y, 2F);
        pose.translate(8.0F * scale, 8.0F * scale, 0.0F);
        pose.scale(1.0F, -1.0F, 0F);
        pose.scale(16.0F * scale, 16.0F * scale, 0F);
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        boolean flat = !model.usesBlockLight();
        if (flat) Lighting.setupForFlatItems();
        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.mul(pose.last().pose());
        RenderSystem.applyModelViewMatrix();
        PoseStack renderStack = REUSABLE_POSE_STACK;
        renderStack.setIdentity();
        Minecraft.getInstance().getItemRenderer().render(stack, ItemDisplayContext.GUI, false,
                renderStack, bufferSource, 15728880, OverlayTexture.NO_OVERLAY, model);
        bufferSource.endBatch();
        RenderSystem.enableDepthTest();
        if (flat) Lighting.setupFor3DItems();
        pose.popPose();
        modelViewStack.popMatrix();
        RenderSystem.applyModelViewMatrix();
    }

    /** 3D rotating item preview (drag-to-rotate). Angle params are radians;
     *  callers pass exactly what GuiItemMove.renderRotAngleX/Y produce. */
    public static void renderItem3D(GuiGraphics gg, ItemStack item, LivingEntity player,
                                    int cx, int cy, float angleXComponent, float angleYComponent, float scale) {
        if (item == null || item.isEmpty() || player == null) return;
        BakedModel model = Minecraft.getInstance().getItemRenderer().getModel(item, player.level(), player, 0);
        PoseStack pose = gg.pose();
        pose.pushPose();
        pose.translate(cx, cy, 100.0F);
        pose.translate(8.0F * scale, 8.0F * scale, 0.0F);
        pose.scale(1.0F, -1.0F, 1.0F);
        pose.mulPose(Axis.XP.rotation(angleYComponent));
        pose.mulPose(Axis.YP.rotation(angleXComponent));
        Lighting.setupForEntityInInventory();
        pose.scale(16.0F * scale, 16.0F * scale, 16.0F * scale);
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        boolean flat = !model.usesBlockLight();
        if (flat) Lighting.setupForFlatItems();
        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.mul(pose.last().pose());
        RenderSystem.applyModelViewMatrix();
        PoseStack renderStack = REUSABLE_POSE_STACK;
        renderStack.setIdentity();
        Minecraft.getInstance().getItemRenderer().render(item, ItemDisplayContext.GUI, false,
                renderStack, bufferSource, 15728880, OverlayTexture.NO_OVERLAY, model);
        bufferSource.endBatch();
        RenderSystem.enableDepthTest();
        if (flat) Lighting.setupFor3DItems();
        pose.popPose();
        modelViewStack.popMatrix();
        RenderSystem.applyModelViewMatrix();
    }

    public static boolean supports3D() {
        return true;
    }
}
```

> 注：`renderItem3D` 的轴映射（XP←angleY、YP←angleX）**逐字保留**原 GuiItemMove 行为，不要"修正"。

- [ ] **Step 2: clean 编译验证**

Run: `./gradlew :v1_21_1:clean :v1_21_1:compileJava -Pactive_versions=1.21.1`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add v1_21_1/src/main/java/com/reclizer/csgobox/v1_21_1/utils/AnimRenderOps.java
git commit -m "feat(v1_21_1): AnimRenderOps legacy 门面（动画渲染原语唯一适配点）"
```

### Task 1.2: v1_21_1 助手委托门面（IconListTools + GuiItemMove）

**Files:**
- Modify: `v1_21_1/src/main/java/com/reclizer/csgobox/v1_21_1/utils/IconListTools.java`
- Modify: `v1_21_1/src/main/java/com/reclizer/csgobox/v1_21_1/utils/GuiItemMove.java`

**Interfaces:**
- Consumes: Task 1.1 门面 API
- Produces: 助手公开方法签名不变（`renderItemFrame` / `renderRewardCell` / `renderGuiItem` / `renderItemProgress` / `renderItemProgressFocus`、`renderItemInInventoryFollowsMouse` / `renderRotAngleX/Y`），调用方零改动

- [ ] **Step 1: IconListTools 委托**——删除类内所有渲染 API 直接调用，替换为门面调用：
  - `renderGuiItem(LivingEntity, Level, GuiGraphics, ItemStack, float, float, float)` 函数体替换为 `AnimRenderOps.renderItem2D(entity, guiGraphics, itemStack, pX, pY, scale)`；删除私有重载 `renderGuiItem(PoseStack, ...)` 与 `REUSABLE_POSE_STACK`；删除不再使用的 import（`BakedModel` / `Matrix4fStack` / `Lighting` / `MultiBufferSource` / `OverlayTexture` / `ItemDisplayContext` / `Minecraft` / `RenderSystem` / `PoseStack`）
  - 全部 `guiGraphics.blit(GOLD_ITEM_TEXTURE, ...)` → `AnimRenderOps.blitTextured(guiGraphics, GOLD_ITEM_TEXTURE, ...)`（保持原参数顺序）
  - 全部 `guiGraphics.fill(...)` → `AnimRenderOps.fill(...)`；全部 `guiGraphics.fillGradient(...)` → `AnimRenderOps.fillGradient(...)`
  - `renderRarity` 内两行同样替换
- [ ] **Step 2: GuiItemMove 委托**——`renderItemInInventoryFollowsMouse` 函数体替换为 `AnimRenderOps.renderItem3D(guiGraphics, item, player, x, y, angleXComponent, angleYComponent, scale)`；删除 `renderItemInInventory` 私有方法与 `REUSABLE_POSE_STACK`；删除不再使用的 import；`renderRotAngleX/Y` 纯数学不动
- [ ] **Step 3: clean 编译**

Run: `./gradlew :v1_21_1:clean :v1_21_1:compileJava -Pactive_versions=1.21.1`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add v1_21_1/src/main/java/com/reclizer/csgobox/v1_21_1/utils/
git commit -m "refactor(v1_21_1): IconListTools/GuiItemMove 渲染原语委托 AnimRenderOps"
```

### Task 1.3: v1_21_1 CsboxProgressScreen 收口

**Files:**
- Modify: `v1_21_1/src/main/java/com/reclizer/csgobox/v1_21_1/gui/CsboxProgressScreen.java`

**Consumes:** Task 1.1 门面 API

- [ ] **Step 1: 逐处替换**（行号以当前文件为准）：
  - 帧首 `RenderSystem.setShaderColor(1,1,1,1)` + `RenderSystem.enableBlend()` + `RenderSystem.defaultBlendFunc()` 三连 → **删除**（fill 自带 RenderType blend 状态；立即模式 blit 的 blend 由 `blitTextured` 内部处理）
  - `guiGraphics.fill(0, 0, this.width, this.height, 0x8C000000)` → `AnimRenderOps.fill(guiGraphics, 0, 0, this.width, this.height, 0x8C000000)`
  - spot_glow 五连（`flush` + `enableBlend` + `blendFuncSeparate(770,771,1,771)` + 8-arg blit）→ `AnimRenderOps.blitTextured(guiGraphics, ResourceLocation.parse("csgobox:textures/screens/spot_glow.png"), (int) spotCX - glowR, (int) spotCY - glowR, glowR * 2, glowR * 2)`
  - 调暗填充 `guiGraphics.fill((int) itemX, ...)` → `AnimRenderOps.fill(...)`
  - `guiGraphics.flush()` → `AnimRenderOps.flush(guiGraphics)`
  - 透镜背板 `guiGraphics.fill(backingX0, by, backingX1, by + bh, 0xFF545454)` → `AnimRenderOps.fill(...)`
  - `guiGraphics.enableScissor(x0, by, x1, by + bh)` → `AnimRenderOps.scissor(guiGraphics, x0, by, x1 - x0, bh)`
  - `guiGraphics.disableScissor()` → `AnimRenderOps.scissorDisable(guiGraphics)`
  - vignette 五连 → `AnimRenderOps.blitTextured(guiGraphics, ResourceLocation.parse("csgobox:textures/screens/lens_vignette.png"), lensMinX, lensMinY, lensW, lensW)`
  - 金色线 `guiGraphics.fill(...)` → `AnimRenderOps.fill(...)`
  - `this.renderBlurredBackground(partialTicks)` → `AnimRenderOps.renderBlurredBackground(this, guiGraphics, partialTicks)`
  - `IconListTools.renderItemProgress` / `renderItemProgressFocus` 调用**保留不动**（已委托到门面）
- [ ] **Step 2: 删除不再使用的 import**（`RenderSystem` 若已无直接引用则删除）
- [ ] **Step 3: clean 编译**

Run: `./gradlew :v1_21_1:clean :v1_21_1:compileJava -Pactive_versions=1.21.1`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add v1_21_1/src/main/java/com/reclizer/csgobox/v1_21_1/gui/CsboxProgressScreen.java
git commit -m "refactor(v1_21_1): CsboxProgressScreen 渲染调用收口到 AnimRenderOps"
```

### Task 1.4: v1_21_1 运行时回归（手动）

**Consumes:** Task 1.1-1.3

- [ ] **Step 1: 按 docs/RELEASE.md 质量门跑 1.21.1 客户端**，逐项核对（预期视觉与重构前一致）：
  - 开箱动画：滚动缓动、聚光灯径向渐变（半透明边缘）、放大镜切带（无残影/鬼影）、vignette 圆环、金色线
  - 抽卡节奏音效（每卡片"嗒"声、8Hz 节流）
  - 批量开箱总览屏 3D 箱子拖拽旋转（rotX/rotY 手感）
  - 出货页：2D 图标；装 TACZ 时 TACZ 枪默认走自家可拖拽 3D 渲染 + 手套按钮进入/重播检视动画（TACZ 路径未被误伤）
  - ESC 退出、hideGui 恢复
- [ ] **Step 2: 记录结果**到 `docs/RUNTIME-UI-TESTING.md`（新开"AnimRenderOps 重构回归"小节，勾选列表）
- [ ] **Step 3: Commit**（若记录文档写入则提交）

### Task 1.5: 创建 decoupled 门面（v26_1_2）

**Files:**
- Create: `v26_1_2/src/main/java/com/reclizer/csgobox/v26_1_2/utils/AnimRenderOps.java`

**Interfaces:** 同 Task 1.1 API 形状（`GuiGraphicsExtractor` 替代 `GuiGraphics`；`Identifier` 替代 `ResourceLocation`；`setBlendNormal` / `flush` 为转发或空操作；`supports3D()` 返回 true）

- [ ] **Step 1: 先读 `v26_1_2/.../gui/CsboxProgressScreen.java` 与 `v26_1_2/.../utils/IconListTools.java`、`v26_1_2/.../utils/GuiItemMove.java`**，确认 26.1.2 的实际 API 形态（`GuiGraphicsExtractor.flush()` 是否存在、blur 用 `blurBeforeThisStratum()` 还是 Screen 方法、`guiGraphics.item(entity, stack, 0, 0, seed)` 与 AABB 居中代码），再写门面：
  - `blitTextured`：`gg.flush()` + `gg.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, 0F, 0F, w, h, w, h)`（无 tint 参数）
  - `renderItem2D`：AABB 居中测量 + `pushMatrix/translate/scale` + `gg.item(entity, stack, 0, 0, seed)`（照搬 v26_1_2 IconListTools.renderGuiItem 现有函数体）
  - `renderItem3D`：照搬 v26_1_2 GuiItemMove.renderItemInInventoryFollowsMouse 现有函数体（含 3D 灯光 `Lighting.Entry.ITEMS_3D`）
  - `renderBlurredBackground`：照搬 v26_1_2 ProgressScreen renderBackground 覆盖里的实现
  - `setBlendNormal`：空操作（26.x 渲染经 RenderPipeline 自带状态）
  - 文件头 `// era: decoupled`
- [ ] **Step 2: clean 编译**

Run: `./gradlew :v26_1_2:clean :v26_1_2:compileJava -Pactive_versions=26.1.2`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add v26_1_2/src/main/java/com/reclizer/csgobox/v26_1_2/utils/AnimRenderOps.java
git commit -m "feat(v26_1_2): AnimRenderOps decoupled 门面"
```

### Task 1.6: v26_1_2 助手委托 + ProgressScreen 收口

**Files:**
- Modify: `v26_1_2/src/main/java/com/reclizer/csgobox/v26_1_2/utils/IconListTools.java`
- Modify: `v26_1_2/src/main/java/com/reclizer/csgobox/v26_1_2/utils/GuiItemMove.java`
- Modify: `v26_1_2/src/main/java/com/reclizer/csgobox/v26_1_2/gui/CsboxProgressScreen.java`

**Consumes:** Task 1.5 门面

- [ ] **Step 1: 照 Task 1.2/1.3 的映射表对 v26_1_2 执行同一委托与收口**（blur 调用形态以 Task 1.5 Step 1 确认的为准；`blitGoldItemAspect` 的 aspect 数学保留在 IconListTools，只把 `blit` 调用换成 `AnimRenderOps.blitTextured`）
- [ ] **Step 2: clean 编译**

Run: `./gradlew :v26_1_2:clean :v26_1_2:compileJava -Pactive_versions=26.1.2`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add v26_1_2/src/main/java/com/reclizer/csgobox/v26_1_2/
git commit -m "refactor(v26_1_2): 助手与 ProgressScreen 收口到 AnimRenderOps"
```

### Task 1.7: v26_1_2 运行时自动回归

- [ ] **Step 1: 跑 docs/RUNTIME-UI-TESTING.md 自动化工作流**（CGEvent + PIL 帧缓冲断言，26.1.2 单机客户端）——开箱动画、透镜、3D 旋转、出货页全套通过
- [ ] **Step 2: 修复任何断言差异**（若出现，多半是 flush/blend 时序变化，回到 Task 1.5/1.6 对照原实现排查）
- [ ] **Step 3: Commit**（若有修复）

---

### ⛳ **Checkpoint 1：用户验收**

> 跑完 Phase 1 后**暂停**。用户验收 1.21.1（主力平台）与 26.1.2（自动回归）的视觉一致性与代码结构，确认后继续 Phase 2。

---

## Phase 2：时代铺开（4 变体组）

> 每个任务统一流程：① 读该时代对应文件 → ② 把当前函数体**逐字**搬入门面对应 op（`// era:` 头标注）→ ③ 助手/ProgressScreen 按 Task 1.2/1.3 映射表收口 → ④ clean 编译 → ⑤ commit。

### Task 2.1: mid 变体（v1_21_3, v1_21_5）

**Files:**
- Create: `v1_21_3/src/main/java/com/reclizer/csgobox/v1_21_3/utils/AnimRenderOps.java`、`v1_21_5/src/main/java/com/reclizer/csgobox/v1_21_5/utils/AnimRenderOps.java`
- Modify: 两个平台的 `utils/IconListTools.java`、`utils/GuiItemMove.java`、`gui/CsboxProgressScreen.java`

**Interfaces:** 同 Task 1.1 API 形状；`// era: mid`

**时代差异（已验证）:**
- `blitTextured`：`gg.blit(RenderType.GUI_TEXTURED, tex, x, y, 0, 0, w, h, w, h, 0xFFFFFFFF)`（带 tint；**无需** blend 重置）
- `renderItem2D/3D`：`pose.translate/scale` + `gg.renderItem(player, item, 0, 0, seed)`（搬 v1_21_3 GuiItemMove 现有体；1.21.5 的手动 RenderSystem 删除差异照搬其自身文件）
- `renderBlurredBackground`：`screen.renderBlurredBackground()`（无参）
- `setBlendNormal`：空操作
- `supports3D()`：true（pose 旋转 + renderItem 即可 3D）

- [ ] **Step 1-3**: v1_21_3 创建门面 + 助手/ProgressScreen 收口 + `./gradlew :v1_21_3:clean :v1_21_3:compileJava -Pactive_versions=1.21.3` 通过
- [ ] **Step 4-6**: v1_21_5 同样操作 + `./gradlew :v1_21_5:clean :v1_21_5:compileJava -Pactive_versions=1.21.5` 通过
- [ ] **Step 7: Commit**（两个平台各一次）

### Task 2.2: renderpipeline 变体（v1_21_8, v1_21_10）

**Files:**
- Create: `v1_21_8/src/main/java/com/reclizer/csgobox/v1_21_8/utils/AnimRenderOps.java`、`v1_21_10/src/main/java/com/reclizer/csgobox/v1_21_10/utils/AnimRenderOps.java`
- Modify: 同 Task 2.1 文件集

**Interfaces:** 同 Task 1.1 API 形状；`// era: renderpipeline`

**时代差异（已验证）:**
- `blitTextured`：`gg.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, 0F, 0F, w, h, w, h, 0xFFFFFFFF)`（带 tint）
- `renderItem2D`：AABB 居中 + `gg.renderItem(...)`（搬 v1_21_10 IconListTools.renderGuiItem 现有体——21.8+ 引入 per-item bounding box 居中）
- `renderBlurredBackground`：`screen.renderBlurredBackground(guiGraphics)`
- `setBlendNormal`：空操作

- [ ] **Step 1-3**: v1_21_8（`./gradlew :v1_21_8:clean :v1_21_8:compileJava -Pactive_versions=1.21.8`）完成 + 编译
- [ ] **Step 4-6**: v1_21_10（`./gradlew :v1_21_10:clean :v1_21_10:compileJava -Pactive_versions=1.21.10`）完成 + 编译
- [ ] **Step 7: Commit**（各一次）

### Task 2.3: pip 变体（v1_21_11）

**Files:**
- Create: `v1_21_11/src/main/java/com/reclizer/csgobox/v1_21_11/utils/AnimRenderOps.java`
- Modify: 同 Task 2.1 文件集

**Interfaces:** 同 Task 1.1 API 形状；`// era: pip`

**时代差异（已验证）:**
- `blitTextured`：`gg.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, 0F, 0F, w, h, w, h)`（**无 tint**）
- `renderItem3D`：**PIP 路径**——搬 v1_21_11 GuiItemMove 现有体（`TrackingItemStackRenderState` + `ItemModelResolver.updateForLiving` + AABB 测量 + `Icon3DRenderState` + `gg.submitPictureInPictureRenderState`），radians→degrees 转换留在门面内部
- `renderItem2D`：AABB 居中 + `gg.renderItem(...)`
- `renderBlurredBackground`：`screen.renderBlurredBackground(guiGraphics)`
- `setBlendNormal`：空操作

- [ ] **Step 1-3**: v1_21_11（`./gradlew :v1_21_11:clean :v1_21_11:compileJava -Pactive_versions=1.21.11`）完成 + 编译
- [ ] **Step 4: Commit**

### Task 2.4: v26_2（从 v26_1_2 镜像）

**Files:**
- Create: `v26_2/src/main/java/com/reclizer/csgobox/v26_2/utils/AnimRenderOps.java`
- Modify: v26_2 的 `utils/IconListTools.java`、`utils/GuiItemMove.java`、`gui/CsboxProgressScreen.java`

**Interfaces:** 同 Task 1.5 API 形状；`// era: decoupled`

- [ ] **Step 1: 用 mirror 拷贝门面**：`scripts/mirror.sh new src/main/java/com/reclizer/csgobox/v26_1_2/utils/AnimRenderOps.java`（同时代对，无适配差异，可整文件镜像）
- [ ] **Step 2-3**: 收口三个文件（按 Task 1.2/1.3 映射表；v26_2 与 26.1.2 的 `GuiGraphicsExtractor` 类型一致）+ `./gradlew :v26_2:clean :v26_2:compileJava -Pactive_versions=26.2` 通过
- [ ] **Step 4: Commit**

---

### ⛳ **Checkpoint 2：Phase 2 验收**

> 抽查 1.21.3 / 1.21.11 / 26.2 各一次运行时开箱动画回归。

---

## Phase 3：全屏收口（其余 5 屏）

> 每个任务：把屏内全部渲染 API 调用替换为门面调用（映射表同 Task 1.3 的替代模式），编译，commit。屏内动画数值/布局数学不动。每屏在基线（v1_21_1 与 v26_1_2）先做，再按时代差异点逐平台收口。

### Task 3.1: CsboxScreen（工具栏/图标 blit，~10 处调用/平台）

- [ ] v1_21_1 + v26_1_2 收口（`blitTextured`/`fill`/`fillGradient`），clean 编译
- [ ] 其余 8 平台按时代差异铺开（每平台 clean 编译）
- [ ] Commit（可按平台分批）

### Task 3.2: CsboxBulkOverviewScreen（render3DBox → `AnimRenderOps.renderItem3D`）

- [ ] 基线两平台收口：`GuiItemMove.renderItemInInventoryFollowsMouse(...)` → `AnimRenderOps.renderItem3D(...)`（**参数不变**：1.21.5 传中心点、v26_2 传左上角 + textureSize——保持各平台现状语义，**不改调用数值**）；背景 `fillGradient` → 门面
- [ ] 其余 8 平台按时代铺开 + clean 编译
- [ ] Commit

### Task 3.3: CsboxBulkResultScreen（~11 处/平台，文本+图标）

- [ ] 基线收口（`fill`/`fillGradient`/`blitTextured`；`RenderFontTool` 调用保留）→ 8 平台铺开 → clean 编译 → Commit
- [ ] 注：`RenderFontTool` 不入门面（drawString 各平台签名一致，已是稳定助手）

### Task 3.4: CsLookItemScreen（含 TACZ 分支，仅 v1_21_1 / forge_1_20_1 特殊）

- [ ] 基线收口：2D 图标与 info panel 的渲染调用 → 门面
- [ ] **1.21.1 保留**：`TaczInspectViewport.isAvailable/enter/renderViewport/triggerInspect/exit` 调用链**原样不动**（TACZ 视口是独立路径，非门面 `renderItem3D`；默认 3D 展示走 `renderItem3D → renderGunModel3D`）
- [ ] 其余平台按时代铺开 + clean 编译 → Commit

### Task 3.5: CsboxConfirmScreen（~3 处/平台）

- [ ] 基线收口 → 8 平台铺开 → clean 编译 → Commit

---

## Phase 4：漂移检查 + CI

### Task 4.1: 漂移检查脚本

**Files:**
- Create: `scripts/check-animops-drift.sh`（仿 check-version.sh 风格，纯文本解析）

**完整脚本：**

```bash
#!/usr/bin/env bash
# Check that every platform's AnimRenderOps exposes the same public method
# shape (name + arity + type family) and declares the correct era.
# Usage: scripts/check-animops-drift.sh
# Exit 0 when in sync, 1 on drift. Does NOT modify any file.

set -uo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

# platform -> expected era
declare -A ERA=(
  [v1_21_0]=legacy   [v1_21_1]=legacy
  [v1_21_3]=mid      [v1_21_5]=mid
  [v1_21_8]=renderpipeline [v1_21_10]=renderpipeline
  [v1_21_11]=pip
  [v26_1_2]=decoupled [v26_2]=decoupled
)

norm() {
  # normalize: keep "public static" method signatures, map GUI type families,
  # strip generics; output sorted
  grep -o 'public static [a-zA-Z<>?]* [a-zA-Z]*(\|[a-zA-Z][a-zA-Z0-9_, <>?]*)*' "$1" \
    | sed -E 's/<[^>]*>//g' \
    | sed -E 's/GuiGraphicsExtractor/GuiGraphics/g' \
    | sed -E 's/Identifier/ResourceLocation/g' \
    | sed -E 's/[[:space:]]+/ /g' \
    | sort
}

fail=0
REF="$(norm v1_21_1/src/main/java/com/reclizer/csgobox/v1_21_1/utils/AnimRenderOps.java)"
[ -z "$REF" ] && { echo "FAIL: reference AnimRenderOps not found/empty"; exit 2; }
echo "reference: v1_21_1 ($(echo "$REF" | wc -l | tr -d ' ') ops)"

for mod in "${!ERA[@]}"; do
  f="$mod/src/main/java/com/reclizer/csgobox/$mod/utils/AnimRenderOps.java"
  [ -f "$f" ] || { echo "FAIL $mod: missing $f"; fail=1; continue; }
  got="$(norm "$f")"
  if [ "$got" = "$REF" ]; then
    echo "OK   $mod (era: ${ERA[$mod]})"
  else
    echo "FAIL $mod: signature drift vs v1_21_1"
    diff <(echo "$REF") <(echo "$got") | head -10
    fail=1
  fi
  grep -q "era: ${ERA[$mod]}" "$f" || { echo "FAIL $mod: era header != ${ERA[$mod]}"; fail=1; }
done

exit $fail
```

- [ ] **Step 1: 写脚本 + `chmod +x scripts/check-animops-drift.sh`**
- [ ] **Step 2: 运行验证**：`scripts/check-animops-drift.sh` —— 10 个平台全 OK（在 Phase 1-3 完成后）
- [ ] **Step 3: Commit**

### Task 4.2: CI 接线

**Files:**
- Modify: `.github/workflows/build.yml`

- [ ] **Step 1: common-test job 在 `scripts/check-version.sh` 之后追加**：

```yaml
      - name: Check AnimRenderOps drift
        run: scripts/check-animops-drift.sh
```

- [ ] **Step 2: 本地跑通脚本后 Commit**

### Task 4.3: merge-*.py 审计与退役

- [ ] **Step 1: 逐个对 v1_21_1 现状试运行**：`merge-magnifier-lens.py`、`merge-circular-lens.py`、`merge-bulk-optimize.py`、`merge-reward-cell.py`、`merge-wear-damage.py`、`merge-pagination.py`、`merge-cooldown-fix.py`——能幂等匹配（输出"已应用"）的保留；`OLD_BLOCK` 匹配失败（因收口后代码已变）的加一行头注"已由 AnimRenderOps 重构取代，保留仅作历史参考"
- [ ] **Step 2: 更新 AGENTS.md 关键文件清单**：`utils/AnimRenderOps.java` 列为"动画渲染唯一适配点"
- [ ] **Step 3: Commit**

---

## Phase 5：后续（不在本计划，另立计划）

- 流畅度：partialTicks 插值强化、静态条带纹理缓存（性能大头）
- 终端机屏：按门面模式落地新屏 + 新原语扩展（守原语级 + 逃生舱策略）
- 出货页 3D 铺全平台（`supports3D()` 探测驱动）
- 新 MC 版本接入流程：断点分析 → 门面时代归类 → 铺屏（写入 docs/PLATFORM-APIS.md）

---

## Self-Review

- **Spec 覆盖**：门面 ops（✔ 1.1/1.5）、助手委托（✔ 1.2/1.6）、ProgressScreen 收口（✔ 1.3/1.6）、5 时代变体（✔ 1.1 legacy / 1.5 decoupled / 2.1 mid / 2.2 renderpipeline / 2.3 pip / 2.4 镜像）、缺陷修复（blend 残留 → blitTextured 内部；blur 4 形态 → renderBlurredBackground；tint 归一化 → 各变体 blitTextured）、漂移检查（✔ 4.1）、CI（✔ 4.2）、merge 脚本退役（✔ 4.3）、TACZ 保留（✔ 3.4）、26.x 生命周期不动（Global Constraints）
- **占位符扫描**：无 TBD；Task 1.5/2.x 的"照搬现有函数体"是精确指令（指明源文件与源函数），非占位
- **类型一致性**：门面 API 形状在全部任务中一致（`GuiGraphics`↔`GuiGraphicsExtractor` 仅 decoupled 变体内部，漂移脚本已归一化）
