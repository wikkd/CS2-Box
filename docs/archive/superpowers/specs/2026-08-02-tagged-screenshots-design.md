# 标签截图 + sleep 标记（Tagged Screenshots & Sleep Events）设计文档

日期：2026-08-02
状态：已批准（用户逐节确认：mc_shot tag 参数 / mc_sleep 非阻塞记事件 / tagged 目录 + E1-E11 打标清单）

## 目标

扩展 Visual Timeline 视觉全面测试流程：

1. **标签截图**：测试脚本在关键操作调用 `mc_shot` 并打上特殊标签，事后时间线能快速定位并验证该时刻功能
2. **sleep 标记**：测试脚本关键操作间可选择性休眠（mc_sleep 记事件），时间线能区分"脚本故意等待"与"卡顿"

## 现状

- `mc_shot` MCP 工具：截图到 screenshots/ + 可选拷贝到指定 path；事件 `shot.saved`（path/feedback）+ `tool_action`（tool=mc_shot）
- 测试脚本 38 处裸 bash `sleep`（0.3/0.5/1s）——时间线无法区分故意等待与卡顿
- 录像器每秒截图存 `$OUT/shots/shot_<epoch>.png`；分析阶段描述/时间线/疑点均按 `shot_<epoch>.png` 契约
- `record_visual_test.sh` 已导出 `VISUAL_TIMELINE_OUT` 环境变量

## 设计

### 组件 1: ToolRunner — mc_shot 加 tag 参数

`mc_shot {"path":"...", "tag":"E11b_p2"}`：

- `tool_action` 事件 data 增加 `tag`（tag 非空时）
- `shot.saved` 事件 data 增加 `tag`（tag 非空时）
- 返回 out 增加 `tag` 字段
- 无 tag 时行为完全不变（向后兼容）

### 组件 2: ToolRunner — 新增 mc_sleep 工具

`mc_sleep {"seconds":2}`：

- **非阻塞**：立即返回（主线程桥 10s 超时限制，不可真阻塞）
- 记录事件 `sleep`（data: `seconds`，double）
- 校验 `0 < seconds ≤ 30`，非法抛 `INVALID_REQUEST`
- 返回 `{"recorded":true, "seconds":N}`
- 测试脚本随后执行 bash `sleep N`（真休眠由脚本负责）
- ToolCatalog 增加 spec（含 seconds 参数说明）

### 组件 3: analyze_timeline.py — 时间线显示

- `fmt_event` 扩展：
  - `sleep` 类型 → `[sleep] seconds=2.0`
  - `tool_action`/`shot.saved` 含 `tag` → 追加 `tag=E11b_p2`
- 标签截图支持（`tagged/` 目录）：
  - 文件名契约：`tagged/tag_<epoch秒>_<tag>.png`
  - 描述批处理：扫描 `shots/*.png` + `tagged/*.png`；描述文件 `descriptions/` 同名 txt（`tag_<epoch>_<tag>.txt`），断点续跑不变
  - 时间线挂载：按 epoch 秒与录像截图共存，行格式 `**标签截图**: tagged/tag_X_E11.png（E11）`
  - 疑点分析：同样覆盖 tagged/ 目录（视觉级审查含标签截图）

### 组件 4: test_csbox_ext.sh — 辅助函数 + 打标清单

脚本头部新增：

```bash
# t_shot <tag> — 关键操作标签截图 (存 $VISUAL_TIMELINE_OUT/tagged/)
t_shot() {
  local tag="$1"
  local out="${VISUAL_TIMELINE_OUT:-/tmp/visual_timeline}/tagged"
  m mc_shot "{\"path\":\"${out}/tag_$(date +%s)_${tag}.png\", \"tag\":\"${tag}\"}" >/dev/null 2>&1
}
# t_sleep <seconds> — 标记性休眠: 记 sleep 事件 + bash 真睡
t_sleep() {
  m mc_sleep "{\"seconds\":$1}" >/dev/null 2>&1 || true
  sleep "$1"
}
```

打标点（每个关键操作后一张标签截图）：

| 位置 | 标签 | 验证目的 |
|---|---|---|
| E2b 开箱完成 | `E2b_open_done` | 开箱流程走通 |
| E2c 公告检查后 | `E2c_achievement` | 成就公告 UI |
| E5 第 5 次循环 | `E5_cycle5` | 开关循环中途状态 |
| E8a 总览屏 | `E8a_overview` | 批量总览渲染 |
| E8b 确认屏 | `E8b_confirm` | 确认屏渲染 |
| E8c 进度屏 | `E8c_progress` | 进度屏渲染 |
| E8d 结果屏 | `E8d_result` | 结果屏渲染 |
| E9c 检视屏 | `E9c_look_item` | 检视屏渲染 |
| E10a 开箱屏 | `E10a_box_screen` | 开箱屏渲染（视觉校验基准） |
| E11a 第 1 页 | `E11a_p1` | 翻页初始态 |
| E11b 第 2 页 | `E11b_p2` | 翻页成功 |
| E11c 第 3 页 | `E11c_p3` | 翻页到底 |
| E11d 翻回第 2 页 | `E11d_p2_back` | 回翻 |

sleep 改造点（约 8 处，其余保留 bash sleep）：开箱动画等待（open_box 内 sleep 1）、E8 各屏切换后 sleep、E11 翻页后 `sleep 0.5` → `t_sleep`。

## 验收标准

1. `mc_shot` 带 tag → `mc_logs` 可见 `tool_action`/`shot.saved` 带 `tag` 字段
2. `mc_sleep 2` → `mc_logs` 可见 `sleep` 事件；非法参数（0/负/31）返回工具错误
3. tagged 截图进入时间线（描述+挂载+疑点覆盖），`**标签截图**` 行格式正确
4. 全套件（录像器下）E1-E11 重跑 34/34，时间线含标签与 `[sleep]` 标记
5. 抽查 E11 段：`E11a_p1`/`E11b_p2`/`E11c_p3` 三张标签截图与页码状态一致
