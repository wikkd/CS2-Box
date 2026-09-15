# Modern UI 兼容性（modernui）

CS2-Box 在 GUI 里渲染 3D 物品预览（26.x PIP / 1.21.1 Matrix4fStack / forge_1_20_1
PoseStack + TACZ 枪械 3D）。本文件记录与 **Modern UI**（`modernui`，
[Motschen 系之外的 Modern UI for Minecraft](https://github.com/BloCamLimb/ModernUI-MC)）
的兼容策略：**装 Modern UI 时 3D 预览保持 3D，不降级**。

> 说明：2026-09 核查时 **不存在“Cocoon UI”模组**——Modrinth 上的
> [Cocoon](https://modrinth.com/plugin/cocoon) 是服务端插件兼容层，与 UI/3D 渲染无关，
> 因此不在本兼容范围内。

## 结论

| 环境 | 3D 预览（PIP/拖拽/TACZ 枪械） | 背景模糊 | 其它 UI |
|---|---|---|---|
| 无 modernui（基线） | 3D 正常 | 原版模糊 / Blur 模组接管 | 正常 |
| 装 Modern UI | **保持 3D（不降级）** | 原版模糊 / Blur 接管 | 正常 |
| Modern UI + Iris/Oculus | **降级为 2D**（shader 规则优先，见 SHADER-COMPAT.md） | 原版模糊或不可见 | 正常 |

## 为什么保持 3D

1. **官方兼容承诺**：Modern UI 的 README 明确声明其引擎面向
   “Minecraft 与基于 vanilla GUI 系统的模组”，无需改代码即可获得其现代化文本/动画
   系统（原文：*“allows Minecraft and Mods based on Vanilla GUI system to enjoy a
   modern text system … without modifying their code”*）——CS2-Box 的所有屏幕都是
   原版 `Screen` + `GuiGraphics` 体系。
2. **渲染路径分离**：Modern UI 接管/增强的是**字体与 UI 文本管线**；CS2-Box 的
   3D 物品预览走的是物品渲染引擎（26.x `submitPictureInPictureRenderState` /
   `getRenderState().addPicturesInPictureState`，legacy `ItemRenderer` + 立即模式
   buffer），与字体管线无交集。
3. **与 Blur 的既有适配独立**：Blur/背景模糊走原版背景钩子，Modern UI 不参与该路径。

因此把 `modernui` 加入 2D 降级列表会**不必要地牺牲 3D 观感**，与“尽可能保持 3D”
的目标相反。

## 实现（六平台同步，各平台 `utils/AnimRenderOps.java`）

- `supports3D()` 仍只对 shader 模组返回 false：`!isShaderModActive()`。
- `isShaderModActive()` 保持 `iris || oculus`，**不包含 modernui**；注释写明决策与
  “若未来某版本实测破坏 3D 路径，在此追加”的扩展点。
- 新增一次性诊断日志（首次调用 `supports3D()` 时输出）：

  ```
  [csgobox] 3D preview env: supports3D=true iris=false oculus=false modernui=true
  ```

  平台差异：NeoForge 26.x / 1.21.1 与 Forge 1.20.1 用 `ModList.get().isLoaded()`；
  Forge 26.x 用静态 `ModList.isLoaded()`；26.x 用 `CsgoBox.LOGGER`，legacy 用本类
  `LOGGER`（Imports/字段已按平台适配）。

- 公开 op 签名未变（`supports3D()` 仍为无参 boolean），`check-animops-drift.sh`
  不受影响。

## 已确认的 Modern UI 版本

| 平台 | 构建 | 来源 |
|---|---|---|
| NeoForge 26.1.2 | `ModernUI-Forge-26.1.2-3.13.0.4`（NeoForge loader） | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/modern-ui/files/8206272) |
| NeoForge 1.21.1 | `ModernUI-NeoForge-1.21.1-3.13.0.1` | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/modern-ui/files/8206075) |
| 26.2 / Forge 26.x / Forge 1.20.1 | 视发布情况而定 | 按需在 Modrinth/CurseForge 查 |

> Forge（非 NeoForge）各平台若没有对应 Modern UI 构建，则 `isLoaded("modernui")`
> 自然为 false，保持基线行为，无需特殊处理。

## 真机验证清单（发布前至少抽 1 个 26.x + 1 个 legacy 平台）

前置：把对应版本 Modern UI（含其前置，如 midnightlib 视版本而定）装入
`run/mods/` 后 `runClient`。

1. [ ] 启动日志出现 `[csgobox] 3D preview env: ... modernui=true` 且 `supports3D=true`
2. [ ] 开箱进度屏：滚动条带动画 + 3D 出货模型渲染正常（无黑框/翻转/闪烁）
3. [ ] 拖拽 3D 预览（`GuiItemMove`）：旋转、缩放、中心点正常
4. [ ] 批量开箱总览/流水屏正常；终端谈判屏报价 3D 预览正常
5. [ ] TACZ 枪械（v1_21_1 / forge_1_20_1）检视视口 3D 渲染正常
6. [ ] 字体渲染（Modern UI 接管后）中文/物品名无乱码、不遮挡
7. [ ] 背景模糊与 Blur 模组共存时正常（若装 Blur）
8. [ ] 无 modernui 基线不改动（回归：`supports3D()` 仍 true）

## 已知边界

- **静态分析无法替代真机验证**：Modern UI 的渲染引擎版本较多，某版本若被实测破坏
  上述 3D 路径（黑屏/不可见），按本文件“扩展点”把该 mod id 加入 `isShaderModActive()`
  即可快速恢复 2D 兜底——这是刻意保留的后门，不默认开启。
- 降级开关只影响「预览表现」，**不触碰服务端 RNG 与任何概率/玩法逻辑**（同
  SHADER-COMPAT.md 规则）。
- Modern UI + Iris/Oculus 同装时按 shader 规则优先降级 2D（shader 破坏面更大）。

## 相关文档

- `docs/SHADER-COMPAT.md` — Iris/Oculus 降级矩阵（与本策略并列）
- `docs/ARCHITECTURE.md` §渲染 — AnimRenderOps 门面与 3D 路径
- `docs/CODE-REVIEW.md` — 渲染门面审查清单