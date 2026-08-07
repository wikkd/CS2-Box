# 设计：1.21.1 抽卡动画审美测试脚本

日期：2026-08-07
状态：已确认
目标平台：v1_21_1（MC 1.21.1 / NeoForge 21.1.x）
产物：`scripts/test_animation_aesthetics.py`（新增）

## 背景与需求

抽卡动画（`CsboxProgressScreen`：条带滚动 + ease-out 减速 + 聚光灯 + 放大镜镜头 + 模糊暗化背景）是 CS2 开箱体验的核心观感。现有 `scripts/record_open_animation.py` 只能连拍帧，没有审美校验；`mc_tools` 的 `verify_screen` 是文本 WANT/BAN 断言导向，不适用无文字的动画画面。

用户需求（已逐项确认）：
- 形态：启动 1.21.1 客户端自动触发开箱动画 + 本地 Ollama 视觉模型自动分析
- 分析粒度：**仅 3 个关键帧**（动画起始、减速中段、停止帧）——最快回归
- 审美维度（5 项，用户确认）：居中与对齐、间距与变形、放大镜镜头质量、背景模糊与暗化、渲染闪烁与鬼影
- 未选维度：动画流畅度曲线（时间戳统计）——不在本脚本范围

## 方案（已批准：方案 A — 自包含脚本）

复用 `record_open_animation.py` 的触发流程（`csxlib`：`BoxEnv` / `setup_items` / `select_slot` / `wait_screen` / `McpClient`），动画期间全量连拍存盘（供事后人工复查），仅 3 个关键帧做 AI 审美分析。双模型复核降误报：主审 `gemma4:12b` → FAIL 疑点由 `qwen3-vl:8b-instruct` 复核，两模型一致才定 FAIL。

## 关键帧时间轴

动画默认 145 tick ≈ 7.25 秒（`totalAnimationTicks` 默认 145，`animationSpeed=NORMAL`，`multiplier=1`）。时间轴以触发动画屏（`CsboxProgressScreen` 出现）为 T0：

| 帧 | 时间 | 内容 | 重点检查 |
|---|---|---|---|
| F1 起始帧 | T0 + 0.5s | 条带高速滚动，聚光灯/镜头全貌 | 居中、间距、镜头圆形裁剪、背景模糊 |
| F2 减速中段 | T0 + 5.5s | ease-out 后半段，滚动慢，镜头内卡片可辨 | 镜头内与条带卡片错位/双重图像、鬼影 |
| F3 停止帧 | 切到 `CsLookItemScreen` 后 0.3s | 中奖卡聚焦 | 中奖卡居中 golden line、无残留闪烁 |

时间轴参数化（`--f1` / `--f2` 为 T0 后偏移秒数；`--f3=-1` 表示不按偏移、改为自动检测切到 `CsLookItemScreen` 后 0.3s 截图），默认按上表。

## 5 维度审美 prompt（固定格式，便于解析）

每帧同一 prompt，要求模型逐维度输出 `维度名: PASS/FAIL/WARN + 一句话理由`，两行之间用空行分隔，只输出 5 行：

```
[居中] ...
[间距] ...
[镜头] ...
[背景] ...
[闪烁] ...
```

维度定义（prompt 中文描述）：
- 居中：聚光灯光晕是否以屏幕水平中线为轴；放大镜镜头是否正中；停止时中奖卡是否停在中心黄金线
- 间距：卡片间距是否均匀；有无重叠、挤压、变形、被截断的卡片
- 镜头：圆形放大镜裁剪是否干净；镜头内卡片是否与外部条带卡片位置一致（无错位、无双重图像）；镜头边缘是否有锯齿/残边
- 背景：模糊暗化背景是否正常（非全黑、非全透、无撕裂、无突兀色块）
- 闪烁：有无卡片闪烁、鬼影、残影、半透明错乱（静态帧可见的异常）

## 脚本结构

```
scripts/test_animation_aesthetics.py
├── main()              参数解析 + 流程编排，返回退出码
├── 触发开箱            （复用 record_open_animation.py 流程：setup_items → 右键 → 点开启 → 等 CsboxProgressScreen）
├── 连拍                T0 起每 interval 秒一帧存盘 shots/anim_XXX.png（interval 默认 0.1）
├── 选关键帧            按时间轴从连拍帧中挑 3 帧 + 标记
├── analyze_frame()     单帧审美分析（主模型 + 疑点复核）
└── 写报告              build/animation_aesthetics/report.md
```

参数：`--port`(41501) `--out`(build/animation_aesthetics) `--interval`(0.1) `--model`(gemma4:12b) `--verify-model`(qwen3-vl:8b-instruct) `--f1/--f2/--f3`(0.5/5.5/-1 停止帧) `--keep-frames`(全量连拍帧默认保留)

## 报告与退出码

`build/animation_aesthetics/report.md`：
- 元信息（时间、端口、模型、动画实际时长）
- 3 帧各一张小图（引用路径）+ 每维度一行状态
- FAIL 疑点附复核模型结论与对应帧图路径
- 汇总行：`PASS: n  FAIL: n  WARN: n`

退出码：0 = 全 PASS；1 = 有 FAIL；2 = 仅 WARN（可接 CI/回归门禁）。

## 不做什么（YAGNI）

- 不做全量逐帧 AI 分析（`--dense` 参数不提供，事后人工复查靠全量连拍帧）
- 不做其他平台支持（脚本写死走 v1_21_1 的模组流程；平台间动画代码同构，后续推广另议）
- 不做流畅度曲线统计（未选维度）
- 不修改模组代码（纯外部测试脚本；`mc_tools` 不落盘改动）
