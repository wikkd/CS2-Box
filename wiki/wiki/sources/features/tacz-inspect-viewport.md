---
type: source
title: TACZ 检视视口集成（设计，v1_21_1 专属）
source_path: docs/superpowers/specs/2026-08-07-tacz-inspect-viewport-design.md
date_ingested: 2026-08-10
tags: [feature, tacz, inspect, viewport, v1_21_1]
key_concepts: [platform-mirror-discipline, code-review-standards]
key_entities: [anim-render-ops, render-font-tool, csgo-box]
---

# TACZ 检视视口集成 — 设计（v1_21_1 专属）

> source: `docs/superpowers/specs/2026-08-07-tacz-inspect-viewport-design.md`

## Summary

2026-08-07 已实现。仅 `v1_21_1` 平台：开箱检视页（[[csgo-box]] 的 `CsLookItemScreen`）底部"手套"按钮作为 TACZ（永恒枪械工坊：零，非官方 1.21.1 移植）检视入口，自驱 TACZ 动画状态机在 GUI 内嵌展示 3D 枪械检视视口。其余 9 平台零改动。`compileOnly` + `ModList.isLoaded("tacz")` + JVM 类懒加载 + `try-catch` 静默降级，无 TACZ 时功能完全隐形。

## Key takeaways

- **产品决策**：仅 1.21.1（TACZ 只存在于该版本，不做跨平台镜像）；现屏内嵌切换不新开屏；无拖拽旋转（固定展示角）；降级为无响应/2D；直接集成不抽象 Provider 层。
- **API 核实**：官方 `inspect()` 只作用于主手物品，抽取奖励在背包故不可复用；所需 API（`AnimateGeoItemRenderer`/`LuaAnimationStateMachine`/`BedrockModel`/`SoundPlayManager`/`TimelessAPI`）全部 public，路线为"自定义视口 + 公共 API 自驱渲染"。
- **构建接线**：`scripts/download-tacz.sh` 幂等下载 TACZ `1.1.8-hotfix-r6`（~57MB，jar 不入库，CI 自动下载）；`compileOnly` tacz + simplebedrockmodel（从 TACZ jarjar 提取）；`local-repo/com/tacz/` 被 `.gitignore` 忽略。
- **compat 类** `TaczInspectViewport`：所有 TACZ 引用封闭其内，对外仅暴露无 TACZ 类型的静态方法（`isAvailable`/`enter`/`enterDisplay`/`triggerInspect`/`renderViewport`/`exit`），每个先 `ModList` 门槛再 `try-catch`。
- **Screen 接线**：`CsLookItemScreen` 字段 `taczViewportActive` + 守卫 `taczDisplayChecked`；手套按钮视口已激活→`triggerInspect`、未激活但可用→`enter` 兜底、不可用→不响应；`removed()` 清理 `exit()` 防泄漏。
- **已知限制（接受）**：枪包未定义 inspect 转换时显示静止；主手同 id 枪状态机共享冲突（低概率）；TACZ 版本漂移靠 try-catch 降级。

## Connections

- 概念：[[platform-mirror-discipline]] · [[code-review-standards]]
- 实体：[[anim-render-ops]] · [[render-font-tool]] · [[csgo-box]]
- 参考：[[code-review]]（§4.6 TACZ 软依赖守卫）· [[configuration]]
