# Shader 兼容性（Iris / Oculus）

CS2-Box 在 GUI 里渲染 3D 物品预览（PIP 3D）与背景模糊。Iris（NeoForge/Fabric）与
Oculus（Forge）的 shader pack 会拦截/忽略模组的自定义渲染路径，因此本模组采用
**检测 + 降级**策略，与 Iris 官方建议一致（避免自定义 shader、提供 fallback 路径）。

## 实现（2.0.1 起）

- 每平台 `utils/AnimRenderOps.java` 新增私有 `isShaderModActive()`：
  `ModList.isLoaded("iris") || ModList.isLoaded("oculus")`（NeoForge 用
  `net.neoforged.fml.ModList.get().isLoaded`；Forge 26.x 用静态
  `net.minecraftforge.fml.ModList.isLoaded`；Forge 1.20.1 用 `ModList.get().isLoaded`）。
- `supports3D()` 改为 `!isShaderModActive()`：shader 激活时 **3D 预览降级为 2D 图标**。
- `renderItem3D` 内部在 null 检查后直接走 `renderItem2D` 回退（含 TACZ 枪械分支，
  v1_21_1 / forge_1_20_1 的枪械 3D 同样降级）。
- **背景模糊不受影响**：`renderBlurredBackground` 走的是原版
  `blurBeforeThisStratum()`（26.x）/ 原版 `Screen.renderBlurredBackground`（1.21.1）/
  no-op（1.20.1），**不是自定义 shader**。
- **`fade_in_blur` 后处理资源（common/assets/minecraft/shaders/）当前无任何 Java 引用**，
  属未接线资源（疑似历史遗留），Iris 兼容性不受其影响；是否删除留待后续审计。

## 行为矩阵

| 环境 | 3D 预览（PIP/拖拽） | 背景模糊 | 开箱动画/概率 |
|---|---|---|---|
| 无 Iris/Oculus | 3D 正常 | 原版模糊正常 | 不受影响 |
| 装 Iris/Oculus + shader pack | **降级为 2D 图标** | 原版模糊（若 shader 不覆盖 GUI）或不可见（不崩溃） | 不受影响 |
| 装 Iris/Oculus 未开 shader pack | 2D（保守降级） | 原版模糊 | 不受影响 |

## 回归测试清单

前置：至少在一个 26.x NeoForge 平台 + 一个 Forge 平台真机执行。

1. 无 shader 环境（基线）：3D 拖拽预览、开箱动画、批量屏、终端屏全部正常。
2. 装 Iris/Oculus + 代表性 shader pack（如 Complementary / BSL）：
   - [ ] 开箱动画屏可正常打开，无黑屏/崩溃
   - [ ] 3D 拖拽预览降级为 2D 图标（不渲染成黑色/空白）
   - [ ] 批量开箱总览/流水屏正常
   - [ ] 终端谈判屏（报价 3D 预览→2D）正常
   - [ ] 背景模糊不导致黑屏（允许无模糊）
   - [ ] 概率显示、开箱结果、成就触发与无 shader 一致
3. 卸载 shader pack 但保留 Iris/Oculus：确认仍走 2D（保守），无异常。
4. 无 Iris/Oculus 时确认 `supports3D()` 返回 true（不误伤正常 3D）。

## 已知边界

- Iris 官方文档结论（core-shaders.md）：模组自定义 shader 在 shader pack 激活时被忽略，
  无通用合并方案，官方建议 fallback——本方案即按此执行。
- 降级只影响「预览表现」，**不触碰服务端 RNG 与任何概率/玩法逻辑**。
- 26.x 的 PIP 在 Iris 下是否还有其它拦截点需真机验证（本仓库无法静态确认）。

## 与 Modern UI 的关系

装 Modern UI（`modernui`）**不触发** 3D 降级——其官方声明兼容 vanilla GUI 系统模组，
3D 预览保持 3D；只有 Iris/Oculus 生效时才降级 2D。两者（Modern UI + shader）同装时按
本文件规则降级。详见 [docs/MODERN-UI-COMPAT.md](./MODERN-UI-COMPAT.md)。
