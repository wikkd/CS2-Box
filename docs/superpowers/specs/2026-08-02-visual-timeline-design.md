# 视觉全面测试流程（Visual Timeline）设计文档

日期：2026-08-02
状态：已批准（用户逐节确认：架构 A / 全流程每秒截图 / 逐张全描述 / qwen3-vl 本地 / 时间线+疑点分析）

## 目标

构建一套视觉全面测试流程：

1. 测试开始即按**每秒一张**截图，直到测试结束（全流程无脑每秒一张，用户明确要求）
2. 过程中持续检测日志，**知道 1 秒内发生了什么操作**（按键/点击/滚动/界面开关）
3. 输出**日志+截图+日志**按秒对齐的混合时间线
4. 测试结束后视觉模型把每张截图转成文字描述（逐张全描述）
5. 最后基于完整时间线排查问题（排版设计、UI 绘制），产出疑点清单

## 现状调研结论

- `mc_shot {path}`：客户端截图到本地（已有，TestHelper MCP 工具）
- `mc_logs {filter, tail, since_seq}`：事件日志（已有）。事件类型：`screen_open`/`screen_close`/`mouse_button`/`key_press`/`chat`/`auto_respawn`，环形缓冲 512 条，`since_seq` 支持增量游标
- **关键缺口**：ToolRunner 的 `mc_click`/`mc_scroll`/`mc_key` 直接调用 Screen 方法（`onPress`/`mouseScrolled`/`keyPressed`），**绕过** NeoForge `InputEvent` 总线 → 注入操作不会被现有事件日志记录，时间线无法还原"该秒脚本做了什么"
- 视觉模型：本地 qwen3-vl（Ollama 11434），verify_screen.sh 已有分析调用逻辑可复用
- 测试套件：`mc_tools/scripts/test_csbox_ext.sh`（E1-E11，全流程约 5-10 分钟）

## 架构（方案 A：bash 编排 + 少量 Java 日志增强）

```
record_visual_test.sh     # 一条龙编排：录像 → 跑测试 → 分析 → 生成报告
analyze_timeline.py       # 视觉批处理 + 时间线合并生成 + 疑点分析
```

### 组件 1：Java 增强（ToolRunner）— 注入操作事件化

`mc_click`/`mc_scroll`/`mc_key`/`mc_shot` 注入执行时写 `tool_action` 事件：

```java
log.add("tool_action", Map.of("tool", "mc_click", "x", guiX, "y", guiY,
        "screen", screenClass, "handled", handled));
```

- `mc_click`：x/y/button/screen/handled
- `mc_scroll`：delta/screen/screen_handled
- `mc_key`：key/action(hold/release)/screen
- `mc_shot`：path（截图动作本身可审计）
- 需重建并重新部署 testhelper（deploy.sh）

### 组件 2：录像器（record_visual_test.sh 内嵌）

```
record_visual_test.sh [--cmd "测试命令"] [--model qwen3-vl:8b-instruct]
                      [--out DIR] [--analyze-only DIR] [--skip-analyze]
```

流程：
1. 前置检查：MCP 端口可达（mc_status）、客户端在运行
2. 创建输出目录 `$OUT/`：`shots/`、`events/`、`descriptions/`
3. 启动后台录像循环（每 1 秒一轮）：
   - `mc_logs {since_seq: N}` 增量拉取 → 追加 `events/events.jsonl`（原始条目含 millis/seq/type/data）
   - `mc_shot {path: shots/shot_<epoch>.png}` → 截图
   - 异常（连接失败）记录到 `events/recorder.log`，连续失败 N 次则退出并报错
4. 执行测试命令（默认 `test_csbox_ext.sh`，退出码透传）
5. 停止录像器，写 `meta.json`（T0/T1、命令、模型、退出码、截图数/事件数）
6. 默认自动进入分析阶段（`--skip-analyze` 跳过）

### 组件 3：视觉批处理 + 时间线（analyze_timeline.py）

```
analyze_timeline.py [--model qwen3-vl:8b-instruct] [--out DIR]
```

1. **逐张描述**：对每张 `shots/shot_<epoch>.png`：
   - 已存在 `descriptions/shot_<epoch>.txt` 则跳过（断点续跑）
   - 调本地 Ollama `/api/generate`（复用 verify_screen.sh 的 base64 逻辑，超时 180s）
   - 描述 prompt：提取可见文字 + 布局描述（元素位置/对齐/是否重叠/溢出/截断）
   - 失败重试 2 次，仍失败写 `ERROR` 占位
2. **合并生成 timeline.md**：
   - 读 `events.jsonl` + `shots/` + `descriptions/`，按秒分桶（以 T0 为 0 秒）
   - 每秒区块：`## 第 N 秒 HH:MM:SS` + `**操作**`（该秒全部事件，按毫秒排序）+ `**截图**`（路径）+ `**视觉描述**`
   - 无事件秒标注"无操作"；无截图秒标注"截图缺失"
   - 头部汇总：总秒数、截图数、事件数、各 screen 停留秒数统计
3. **疑点分析**（报告尾部章节）：
   - 规则级：截图时间戳断裂（>3s 无截图）、同屏连续 >30s 无操作、tool_action 的 handled=false 等
   - 视觉级：对每张截图跑结构化 prompt（对齐/间距/重叠/溢出/截断/对比度/层级，输出 JSON 疑点清单），聚合去重，按严重度排序
   - 视觉疑点分析同样支持断点续跑

## 时间线格式（timeline.md）

```markdown
# 视觉测试时间线 · <日期> <T0> ~ <T1>（<命令>）
- 总计: N 秒 / 截图 N 张 / 事件 N 条
- 界面停留: CsboxScreen 42s · CsLookItemScreen 25s · ...
## 第 0 秒 15:30:00
**操作**: (无)
**截图**: shots/shot_1754125200.png
**视觉描述**: 主世界画面，天空晴朗……
## 第 3 秒 15:30:03
**操作**: [tool_action] mc_key key.keyboard.escape → handled
          [screen_open] CsboxScreen title=武器补给箱
**截图**: shots/shot_1754125203.png
**视觉描述**: 开箱界面，标题"武器补给箱"，……
```

## 性能与鲁棒性

- E1-E11 全流程约 5-10 分钟 = 300-600 张图；qwen3-vl 本地每张 5-15s → 分析阶段可能 30-60 分钟，**必须断点续跑**
- 截图命名用 epoch 秒，与事件毫秒时间戳按秒对齐合并，天然容忍时序抖动
- 录像器与测试解耦：测试崩溃/中断，已录数据不丢，可 `--analyze-only` 单独跑分析
- 事件落盘用 JSONL 追加，分析阶段才合并，录像阶段零解析成本

## 文件清单

| 文件 | 动作 | 说明 |
|---|---|---|
| `mc_tools/src/main/java/com/reclizer/testhelper/tools/ToolRunner.java` | 改 | click/scroll/key/shot 写 tool_action 事件 |
| `mc_tools/scripts/record_visual_test.sh` | 新增 | 一条龙编排 + 录像器循环 |
| `mc_tools/scripts/analyze_timeline.py` | 新增 | 视觉批处理 + 时间线生成 + 疑点分析 |
| `mc_tools/scripts/verify_screen.sh` | 不动 | 复用的是其 Ollama 调用方式（参考实现） |
| `mc_tools/scripts/test_csbox_ext.sh` | 不动 | 作为默认被测命令 |

## 验收标准

1. `record_visual_test.sh`（默认包 E1-E11）跑通，产出 timeline.md：按秒分节，操作/截图/描述齐全
2. mc_logs 能查到 `tool_action` 事件（重部署后）
3. 逐张描述含文字 + 布局信息；断点续跑有效（kill 后重跑跳过已完成）
4. 疑点分析产出清单（至少规则级命中项，如 E5 循环的多次 GUI 开合）
5. 全流程截图覆盖测试全程（无 >3s 断裂）
