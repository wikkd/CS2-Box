# AnimRenderOps 动画渲染门面设计

日期：2026-08-08
状态：已批准（待实施）
范围：10 个平台模块的动画渲染适配收敛；common 零改动；服务端/网络/配置零改动

## 战略背景（设计前提）

1. **1.21.1 是长期主力平台**，动画持续迭代（流畅度优化、新增动画、CS2 终端机新抽卡动画）。
2. **后续会持续开发新 MC 版本**——每加一个版本就要面对一轮渲染 API 断点（1.21.0 → 26.2 之间已断 4 次渲染 API）。
3. 终端机抽卡 = **GUI 屏 + 完全不同的新动画**（第七个屏族），从第一天走门面，零适配落地。

结论：动画渲染的版本适配成本是复利型支出（每次改动画/升版本都在付），值得一次性投资收敛。

## 问题定义

| 痛点 | 现状证据 |
|---|---|
| 适配成本高 | `CsboxProgressScreen` 465 行，1.21.1→1.21.10 diff 112 行（24%）；`IconListTools`/`GuiItemMove` 物品渲染核心每时代整段重写；仓库 5+ 个 merge-*.py 专门为动画功能写定点合入脚本 |
| 已知渲染缺陷 | legacy 立即模式 blit 继承全局 blend 状态（spot_glow/vignette 需手动重置 blendFuncSeparate）；blur 签名有 **4 种形态**（`partialTicks` / 无参 / `guiGraphics` / 26.x `blurBeforeThisStratum()`）；tint 参数 1.21.11 删除、26.x 无 color 参数 |
| 跨平台表现不一致 | 同动画在不同时代走 3 套互不相通的物品渲染技术，行为细节天然漂移 |
| 技术升级难 | 出货页 3D 展示目前仅 1.21.1（TACZ）/1.21.11+（PIP）有；流畅度/性能优化需 10 平台各做一遍 |

## 方案对比与结论

- **A. 每平台渲染门面（采用）**：每平台唯一 `utils/AnimRenderOps.java` 吸收全部版本差异；屏幕与助手只调门面；缺陷单点修复；技术升级单点实施。
- **B. common 动画引擎 + 平台后端（否决）**：ItemStack/字体/音效/模糊跨界泄漏，26.x `GuiGraphicsExtractor` 生命周期让"每帧渲染"接口变形，工作量 3-5 倍，且 mirror 脚本已在同步逻辑，收益重叠。
- **C. 技术收口（并入 A）**：一次性迁移技术数不改变"改一次改 N 处"的结构，只作 A 的实现子原则（门面按时代基准重写时顺带收口）。

## 架构

```
6+1 个屏（动画逻辑/布局数学，版本无关，镜像即通）
   │ 只调用
   ├─ IconListTools / GuiItemMove / RenderFontTool  ← 逻辑助手（aspect/旋转数学/文本缩放，版本无关）
   │     │ 只调用
   │     └─ utils/AnimRenderOps  ← 唯一版本差异点，每平台 1 份，5 时代变体
   │           └─ pip/Icon3DRenderer（仅 1.21.11+/26.x）、compat/TaczInspectViewport（仅 1.21.1）留在内部被间接调用
   └─ 动画数值（easedScroll/切带/聚光衰减/音效阈值）保持原样
```

关键原则：
- **布局与数学留在屏/助手（稳定），draw 调用进门面（可变）**。
- 门面守**原语级**（draw 调用），功能级逻辑（透镜切带/缓动/音效节奏）不入门面——防止抽象泄漏。
- 屏与助手**永不分支版本**；时代特有能力 = 门面能力探测（如 `supports3D()`）。
- 1.21.1 的 `TaczInspectViewport`（自驱 TACZ 状态机）是独立路径，**不并入** `renderItem3D`。

## 门面 API（10 平台同形状）

| Op | 统一语义 | 吸收的版本差异 |
|---|---|---|
| `blitTextured(GuiGraphics, ResourceLocation, int x, int y, int w, int h)` | 纹理拉伸绘制 | 4 种 blit 签名 + legacy blend 残留重置 + flush 策略 |
| `fill` / `fillGradient` | 矩形/渐变填充 | 无（统一调用面，屏内不直写） |
| `scissor` / `scissorDisable` | 矩形裁剪 | 无（统一调用面） |
| `setBlendNormal(GuiGraphics)` | 恢复标准 GUI blend | legacy 需手动重置；其余时代空操作 |
| `flush(GuiGraphics)` | 刷新缓冲 | 转发（各时代 flush 语义已一致） |
| `renderBlurredBackground(Screen, GuiGraphics, float partialTicks)` | 背景模糊 | **4 种签名形态**（legacy/mid/renderpipeline+decoupled） |
| `renderItem2D(LivingEntity, GuiGraphics, ItemStack, float x, float y, float scale)` | 2D 图标居中绘制 | 3 套物品渲染技术（BakedModel / renderItem / item()） |
| `renderItem3D(GuiGraphics, ItemStack, LivingEntity, int cx, int cy, float angleXComponent, float angleYComponent, float scale)` | 3D 旋转预览 | legacy 矩阵栈 vs PIP 渲染状态；弧度/度转换留在门面内 |
| `supports3D()` | 能力探测 | — |

## 时代变体（5 个，10 平台）

| 变体 | 平台 | blit | blend | item2D | item3D | blur |
|---|---|---|---|---|---|---|
| legacy | 1.21.0/1.21.1 | 8-arg 立即模式 | **需手动重置** | BakedModel+ItemRenderer+Matrix4fStack+Lighting | 同左 + 旋转 | `screen.renderBlurredBackground(partialTicks)` |
| mid | 1.21.3/1.21.5 | `RenderType.GUI_TEXTURED`+tint | 无需 | pose+`renderItem` | pose 旋转+`renderItem` | `screen.renderBlurredBackground()`（无参） |
| renderpipeline | 1.21.8/1.21.10 | `RenderPipelines.GUI_TEXTURED`+tint | 无需 | AABB 居中+`renderItem` | 同左+旋转 | `screen.renderBlurredBackground(guiGraphics)` |
| pip | 1.21.11 | `RenderPipelines` 无 tint | 无需 | AABB 居中+`renderItem` | **PIP Icon3DRenderState** | `screen.renderBlurredBackground(guiGraphics)` |
| decoupled | 26.1.2/26.2 | `RenderPipelines` 无 color + `Identifier` | 无需（空操作） | `pushMatrix`+`item()`+AABB 居中 | 同左 + `Lighting.Entry.ITEMS_3D` | `gg.blurBeforeThisStratum()` |

## 已知缺陷修复清单（一次性修进门面）

1. legacy blend 残留：spot_glow/lens_vignette 的 `blendFuncSeparate(770,771,1,771)` workaround → 移入 `blitTextured` 内部，屏内删除。
2. 帧首 `setShaderColor+enableBlend+defaultBlendFunc` 三连 → 屏内删除（fill 自带 RenderType 状态；立即模式 blit 的 blend 由门面内部处理）。
3. blur 签名 4 形态 → 门面包掉，屏内一行稳定调用。
4. tint/color 参数差异（1.21.11 去 tint、26.x 无 color）→ 门面内部归一化。
5. 26.2 hideGui → 已有 `HudVisibility` 包装，不动（不在门面范围）。

## 收益与风险

**收益**：渲染原语适配工作量降 60-80%；纯逻辑改动结构性零适配；缺陷单点修复；新 MC 版本接入 = "断点分析 → 门面归类/新变体 → 铺屏"固定工序；性能优化（如条带缓存）单点实施。jar 增重 KB 级，资源零新增，无外部模组冲突（KubeJS/成就/网络零影响）。

**风险与对策**：
1. 迁移回归（flush/blend 全局状态时序最敏感，历史残影 bug 根源）→ 每 phase 运行时回归，基线（1.21.1 手动 / 26.1.2 RUNTIME-UI-TESTING 自动）重点盯。
2. 门面签名无法精确统一（26.x `GuiGraphicsExtractor`）→ 漂移脚本做方法名/arity/粗类型弱校验（归一化后比对）+ era 头声明校验。
3. 抽象泄漏 → 门面守原语级纪律；时代特有能力走 `supports3D()` 式探测。
4. merge-*.py 旧脚本模式失配 → Phase 4 审计更新/退役。
5. TACZ 路径误伤 → CsLookItemScreen 收口时明确保留独立分支。

## 执行阶段

- **P0** 设计文档落盘（本文档）
- **P1 基线**：v1_21_1（legacy）+ v26_1_2（decoupled）门面 + 三助手委托 + ProgressScreen 收口 + 回归 → **Checkpoint 1 用户验收**
- **P2 时代铺开**：mid（21.3/21.5）→ renderpipeline（21.8/21.10）→ pip（21.11）→ 26.2 镜像 → **Checkpoint 2**
- **P3 全屏收口**：CsboxScreen / BulkOverview（render3DBox）/ BulkResult / LookItem（TACZ 分支保留）/ Confirm
- **P4 漂移检查 + CI**：`scripts/check-animops-drift.sh` 挂 CI；merge-*.py 审计
- **P5 后续（另立计划）**：流畅度（partialTicks 插值强化、静态条带纹理缓存）、终端机屏、3D 铺全平台、新版本接入流程文档化（PLATFORM-APIS.md）
