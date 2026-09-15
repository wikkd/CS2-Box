# Visual Timeline 视觉全面测试流程 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 构建"每秒截图 + 日志事件 + 视觉描述"按秒对齐的时间线报告与排版/UI 绘制疑点分析流程。

**Architecture:** bash 编排（`record_visual_test.sh`）内嵌每秒录像循环（增量拉 `mc_logs` 事件落 JSONL + `mc_shot` 截图），测试结束由 `analyze_timeline.py` 逐张调本地 qwen3-vl 转文字描述（断点续跑）、按秒合并生成 `timeline.md`、输出疑点清单。ToolRunner 增加 `tool_action` 事件使注入操作可审计。

**Tech Stack:** bash / python3（stdlib only）/ Java 21（ToolRunner）/ 本地 Ollama qwen3-vl（11434）/ MCP-RPC（41501）

## Global Constraints

- 依赖方向不变；不改 `verify_screen.sh` / `test_csbox_ext.sh` / `EventLog.java` / `mc.sh` / `jget.sh`
- 视觉模型默认 `qwen3-vl:8b-instruct`，Ollama 地址 `http://localhost:11434/api/generate`，超时 180s，temperature 0
- 所有产物落在 `--out DIR`（默认 `/tmp/visual_timeline/`），子目录 `shots/` `events/` `descriptions/`
- 截图命名 `shots/shot_<epoch秒>.png`，描述命名 `descriptions/shot_<epoch秒>.txt`（断点续跑的关键契约）
- 事件落盘 `events/events.jsonl`：每行 = mc_logs 返回的单个条目 `{"seq","millis","type","data"}`
- 分析阶段必须可中断重跑：已完成描述文件不得重写
- Java 改动后必须 `./gradlew jar` + `scripts/deploy.sh` 重部署并冒烟验证
- mc_tools 工作目录：`/Users/shuangyuexingxun/Desktop/mc_tools`（下文所有相对路径以此为准，除非注明 CS2-Box 仓库）

---

### Task 1: ToolRunner 注入操作事件化（tool_action）

**Files:**
- Modify: `/Users/shuangyuexingxun/Desktop/mc_tools/src/main/java/com/reclizer/testhelper/tools/ToolRunner.java`
- Deploy: `/Users/shuangyuexingxun/Desktop/mc_tools/scripts/deploy.sh`

**Interfaces:**
- Consumes: 现有 `EventLog.add(type, Map<String,Object>)`（签名见 EventLog.java:37）
- Produces: 事件类型 `tool_action`，data 键固定：`tool`/`screen` 必有；`mc_click` 另有 `x`/`y`/`hit_widget`；`mc_scroll` 另有 `delta`/`screen_handled`；`mc_key` 另有 `key`/`action`/`target`；`mc_shot` 另有 `path`。后续 Task 3 时间线依赖此契约

- [ ] **Step 1: 加 eventLog 字段并接线 click/scroll/key**

在 `ToolRunner.java` 类顶部（`private final Minecraft mc;` 之后）加字段，构造函数 `this.mc = mc;` 之后赋值：

```java
    private final EventLog eventLog;

    public ToolRunner(Minecraft mc, EventLog log, MainThreadBridge bridge) {
        this.mc = mc;
        this.eventLog = log;
```

在 `click(...)`（约 417 行）两个成功返回点之前各插一条日志：

widget 命中分支（`out.addProperty("hit_widget", ...);` 之后、`return out;` 之前）：

```java
                eventLog.add("tool_action", Map.of(
                        "tool", "mc_click", "x", fbX, "y", fbY,
                        "hit_widget", w.getClass().getSimpleName(),
                        "screen", screen.getClass().getSimpleName()));
```

screen 转发分支（`out.addProperty("renderables", renderables.size());` 之后、`return out;` 之前）：

```java
        eventLog.add("tool_action", Map.of(
                "tool", "mc_click", "x", fbX, "y", fbY,
                "hit_widget", "", "screen_handled", handledByScreen,
                "screen", screen.getClass().getSimpleName()));
```

- [ ] **Step 2: scroll/key/shot 加日志**

`scroll(...)`（约 471 行）在 `screen != null` 分支、`out.addProperty("delta", delta);` 之后加：

```java
            eventLog.add("tool_action", Map.of(
                    "tool", "mc_scroll", "delta", delta,
                    "screen_handled", handled,
                    "screen", screen.getClass().getSimpleName()));
```

`key(...)`（约 490 行）：三个成功返回点（charTyped、keyPressed、escape/global、keymapping 的 hold/release/click 共 5 处 `return out;` 之前）统一在 `return out;` 处不便——改为在每个分支构造 `JsonObject out` 的 `dispatched` 赋值后插入一行事件。为最小侵入，在函数开头记录一次，最终日志 `action` 用 `params.get("mode")` 缺省 `"click"`：

在 `key(...)` 函数体开头（`String name = params.get("key").getAsString();` 之后）加：

```java
        eventLog.add("tool_action", Map.of(
                "tool", "mc_key", "key", name,
                "action", params.has("mode") ? params.get("mode").getAsString() : "click",
                "screen", screen == null ? "" : screen.getClass().getSimpleName()));
```

`shot(...)` 开头（`JsonObject out = new JsonObject();` 之前，`params.get("path")` 解析之后）加：

```java
        log.add("tool_action", Map.of(
                "tool", "mc_shot", "path",
                params.has("path") && params.get("path").isJsonPrimitive()
                        ? params.get("path").getAsString() : ""));
```

- [ ] **Step 3: 编译 + 单元测试**

```bash
cd /Users/shuangyuexingxun/Desktop/mc_tools && ./gradlew build 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`（含既有 31 个测试全过）。

- [ ] **Step 4: 打包 + 部署**

```bash
cd /Users/shuangyuexingxun/Desktop/mc_tools && ./gradlew jar && scripts/deploy.sh
```

Expected: `== 客户端就绪 ==`。部署后客户端在 TitleScreen。

- [ ] **Step 5: 冒烟验证 tool_action**

```bash
cd /Users/shuangyuexingxun/Desktop/mc_tools && scripts/enter_world.sh 41501 >/dev/null 2>&1
MCP_PORT=41501 scripts/mc.sh mc_logs '{"filter":"tool_action","tail":50}'
```

Expected: 若此前已跑过脚本则能看到 `tool_action` 条目；为空则手动触发一次：

```bash
MCP_PORT=41501 scripts/mc.sh mc_key '{"key":"key.keyboard.escape"}'
MCP_PORT=41501 scripts/mc.sh mc_logs '{"filter":"tool_action","tail":5}'
```

Expected: 出现 `{"tool":"mc_key",...}` 条目。若 enter_world 不可用（世界未建），至少验证 mc_key 产生 tool_action。

- [ ] **Step 6: Commit**

```bash
cd /Users/shuangyuexingxun/Desktop/mc_tools && git add src/main/java/com/reclizer/testhelper/tools/ToolRunner.java && git commit -m "feat: ToolRunner 注入操作记 tool_action 事件日志"
```

---

### Task 2: analyze_timeline.py — 逐张视觉描述批处理

**Files:**
- Create: `/Users/shuangyuexingxun/Desktop/mc_tools/scripts/analyze_timeline.py`

**Interfaces:**
- Produces: `describe_images(out_dir, model) -> None` — 扫描 `out_dir/shots/*.png`，逐张调 Ollama，写 `out_dir/descriptions/shot_<epoch>.txt`（存在即跳过）；失败重试 2 次后写 `ERROR` 行。`merge_timeline(out_dir, model) -> None`（Task 3）；`analyze_suspicions(out_dir, model) -> None`（Task 4）；`main()` 依次调用三者
- 描述文件内容：单文本块，首行 `[文字]` 提取的文字，次行 `[布局]` 布局描述。供 Task 3 直接嵌入

- [ ] **Step 1: 写脚本骨架 + 描述函数（完整代码）**

```python
#!/usr/bin/env python3
"""analyze_timeline.py — 视觉批处理 + 时间线生成 + 疑点分析。

用法:
  analyze_timeline.py [--out DIR] [--model M] [--phase describe|merge|suspect|all]
默认 --phase all（断点续跑：已存在的描述文件自动跳过）。
"""
import argparse
import base64
import glob
import json
import os
import re
import sys
import time
import urllib.request

OLLAMA_URL = "http://localhost:11434/api/generate"

DESCRIBE_PROMPT = (
    "这是 Minecraft 游戏界面截图。请输出两行:\n"
    "[文字] 精确提取截图内所有可见文字, 逐行列出, 没有则写 无\n"
    "[布局] 用中文描述整体布局: 主要 UI 元素的位置(用左上/右上/中央/底部描述)、"
    "是否有元素重叠、溢出屏幕、文字被截断、对齐异常。没有异常就写 布局正常。"
)


def ollama_text(prompt: str, model: str, img_path: str) -> str:
    img = base64.b64encode(open(img_path, "rb").read()).decode()
    body = json.dumps({
        "model": model, "prompt": prompt, "images": [img],
        "stream": False, "options": {"temperature": 0},
    }).encode()
    req = urllib.request.Request(OLLAMA_URL, data=body,
                                 headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=180) as resp:
        return json.loads(resp.read()).get("response", "")


def describe_images(out_dir: str, model: str) -> None:
    desc_dir = os.path.join(out_dir, "descriptions")
    os.makedirs(desc_dir, exist_ok=True)
    shots = sorted(glob.glob(os.path.join(out_dir, "shots", "shot_*.png")))
    for i, shot in enumerate(shots):
        base = os.path.basename(shot).replace(".png", ".txt")
        out = os.path.join(desc_dir, base)
        if os.path.exists(out):
            print(f"[skip] {base}")
            continue
        text = None
        for attempt in range(3):
            try:
                text = ollama_text(DESCRIBE_PROMPT, model, shot)
                break
            except Exception as e:
                print(f"[retry {attempt + 1}/3] {base}: {e}")
                time.sleep(2)
        if text is None:
            text = "[文字] ERROR\n[布局] 视觉模型调用失败"
        with open(out, "w", encoding="utf-8") as f:
            f.write(text)
        print(f"[ok] {base} ({i + 1}/{len(shots)})")


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default="/tmp/visual_timeline")
    ap.add_argument("--model", default="qwen3-vl:8b-instruct")
    ap.add_argument("--phase", default="all",
                    choices=["describe", "merge", "suspect", "all"])
    args = ap.parse_args()
    if args.phase in ("describe", "all"):
        describe_images(args.out, args.model)


if __name__ == "__main__":
    main()
```

- [ ] **Step 2: 用已有截图跑批处理验证**

```bash
mkdir -p /tmp/vt_demo/shots
cp /tmp/p1.png /tmp/vt_demo/shots/shot_1754000000.png
cp /tmp/p2.png /tmp/vt_demo/shots/shot_1754000001.png
python3 /Users/shuangyuexingxun/Desktop/mc_tools/scripts/analyze_timeline.py --out /tmp/vt_demo --phase describe
cat /tmp/vt_demo/descriptions/shot_1754000000.txt
```

Expected: `[ok] shot_1754000000.png (1/2)`；txt 含 `[文字]` 与 `[布局]` 两行。若 /tmp/p1.png 不存在则先跑 `MCP_PORT=41501 scripts/mc.sh mc_shot '{"path":"/tmp/p1.png"}'` 生成。

- [ ] **Step 3: 验证断点续跑**

```bash
python3 /Users/shuangyuexingxun/Desktop/mc_tools/scripts/analyze_timeline.py --out /tmp/vt_demo --phase describe
```

Expected: 输出 `[skip] shot_1754000000.txt` 与 `[skip] shot_1754000001.txt`，不重新调用模型。

- [ ] **Step 4: Commit**

```bash
cd /Users/shuangyuexingxun/Desktop/mc_tools && git add scripts/analyze_timeline.py && git commit -m "feat: analyze_timeline 视觉批处理 (断点续跑)"
```

---

### Task 3: analyze_timeline.py — 按秒合并生成 timeline.md

**Files:**
- Modify: `/Users/shuangyuexingxun/Desktop/mc_tools/scripts/analyze_timeline.py`

**Interfaces:**
- Consumes: `events/events.jsonl`（每行 `{"seq","millis","type","data"}`）、`shots/shot_<epoch>.png`、`descriptions/shot_<epoch>.txt`、`meta.json`（可选，含 `t0`）
- Produces: `timeline.md`。每秒区块 `## 第 N 秒 HH:MM:SS` + `**操作**`（该秒事件按 millis 升序，格式 `[type] data键=值 空格分隔`，tool_action 精简为 `[tool_action] mc_click x=... y=...`）+ `**截图**`（相对路径）+ `**视觉描述**`。头部统计：总秒数/截图数/事件数/各 screen 停留秒数

- [ ] **Step 1: 加 merge_timeline 函数（完整代码）**

在 `describe_images` 函数后、`main()` 前插入：

```python
def fmt_event(ev: dict) -> str:
    t = ev.get("type", "?")
    d = ev.get("data", {})
    if not isinstance(d, dict):
        return f"[{t}]"
    if t == "tool_action":
        bits = [d.get("tool", "?")]
        for k in ("x", "y", "key", "delta", "action", "screen_handled", "hit_widget", "screen"):
            if k in d:
                bits.append(f"{k}={d[k]}")
        return f"[{t}] " + " ".join(bits)
    bits = []
    for k, v in d.items():
        bits.append(f"{k}={v}")
    return f"[{t}] " + " ".join(bits)


def merge_timeline(out_dir: str) -> None:
    events_path = os.path.join(out_dir, "events", "events.jsonl")
    events = []
    t0 = None
    if os.path.exists(events_path):
        with open(events_path, encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line:
                    continue
                try:
                    ev = json.loads(line)
                except json.JSONDecodeError:
                    continue
                if ev.get("millis"):
                    events.append(ev)
    meta = {}
    meta_path = os.path.join(out_dir, "meta.json")
    if os.path.exists(meta_path):
        with open(meta_path, encoding="utf-8") as f:
            meta = json.load(f)
    if events:
        t0 = meta.get("t0") or events[0]["millis"]
        events.sort(key=lambda e: e["millis"])

    shots = sorted(glob.glob(os.path.join(out_dir, "shots", "shot_*.png")))
    shot_secs = {int(re.search(r"shot_(\d+)\.png", os.path.basename(s)).group(1)): s
                 for s in shots if re.search(r"shot_(\d+)\.png", os.path.basename(s))}
    desc_dir = os.path.join(out_dir, "descriptions")

    lines = []
    lines.append("# 视觉测试时间线")
    if t0:
        start_s = int(t0) // 1000
        end_s = max(shot_secs.keys(), default=start_s)
        t1 = meta.get("t1") or (end_s * 1000)
        lines.append(f"- 时间范围: {time.strftime('%Y-%m-%d %H:%M:%S', time.localtime(start_s))}"
                     f" ~ {time.strftime('%H:%M:%S', time.localtime(int(t1) // 1000))}"
                     f"（命令: {meta.get('command', '?')}，退出码: {meta.get('exit_code', '?')}）")
        lines.append(f"- 统计: {end_s - start_s + 1} 秒 / 截图 {len(shots)} 张 / 事件 {len(events)} 条")

    # 按秒分桶: 秒 k 覆盖 [t0+k*1000, t0+(k+1)*1000)。桶键=绝对 epoch 秒
    by_second = {}
    current_screen = ""
    screen_seconds = {}
    if t0:
        for ev in events:
            if ev["millis"] < t0:
                continue  # 录像开始前的事件丢弃
            sec = int(ev["millis"] // 1000)
            by_second.setdefault(sec, []).append(ev)
            if ev["type"] == "screen_open":
                current_screen = ev["data"].get("screen_class", "")
            elif ev["type"] == "screen_close":
                current_screen = ""
        start_s = int(t0) // 1000
        for sec in sorted(by_second):
            for ev in by_second[sec]:
                if ev["type"] == "screen_open":
                    sc = ev["data"].get("screen_class", "?")
                    screen_seconds[sc] = screen_seconds.get(sc, 0) + 1
        if screen_seconds:
            lines.append("- 界面停留: "
                         + " · ".join(f"{k} {v}s" for k, v in
                                      sorted(screen_seconds.items(), key=lambda kv: -kv[1])))

    if t0:
        lines.append("")
        start_s = int(t0) // 1000
        end_s = max(max(by_second, default=start_s), max(shot_secs, default=start_s))
        for sec in range(start_s, end_s + 1):
            evs = by_second.get(sec, [])
            shot = shot_secs.get(sec)
            lines.append(f"## 第 {sec - start_s} 秒 {time.strftime('%H:%M:%S', time.localtime(sec))}")
            if evs:
                lines.append("**操作**:")
                for ev in evs:
                    lines.append(f"- {fmt_event(ev)}")
            else:
                lines.append("**操作**: (无)")
            if shot:
                rel = os.path.relpath(shot, out_dir)
                lines.append(f"**截图**: {rel}")
                desc = os.path.join(desc_dir, os.path.basename(shot).replace(".png", ".txt"))
                if os.path.exists(desc):
                    lines.append("**视觉描述**:")
                    for dl in open(desc, encoding="utf-8").read().splitlines():
                        lines.append(f"> {dl}")
            else:
                lines.append("**截图**: (缺失)")
            lines.append("")
    else:
        lines.append("(无事件与截图数据)")

    with open(os.path.join(out_dir, "timeline.md"), "w", encoding="utf-8") as f:
        f.write("\n".join(lines))
    print(f"[ok] timeline.md ({len(shots)} 截图, {len(events)} 事件)")
```

- [ ] **Step 2: 造 fake 数据验证时间线**

```bash
cd /tmp/vt_demo
T0=$(python3 -c "import time; print(int(time.time())*1000)")
mkdir -p events
python3 - "$T0" <<'PYEOF'
import json, os, sys, time
t0 = int(sys.argv[1])
evs = [
    {"seq": 1, "millis": t0 + 100, "type": "screen_open", "data": {"screen_class": "CsboxScreen", "title": "武器补给箱"}},
    {"seq": 2, "millis": t0 + 400, "type": "tool_action", "data": {"tool": "mc_key", "key": "key.mouse.right", "action": "click", "screen": "CsboxScreen"}},
    {"seq": 3, "millis": t0 + 1200, "type": "tool_action", "data": {"tool": "mc_click", "x": 1244.0, "y": 924.0, "hit_widget": "Button", "screen": "CsboxScreen"}},
    {"seq": 4, "millis": t0 + 2200, "type": "screen_open", "data": {"screen_class": "CsLookItemScreen", "title": ""}},
]
with open("events/events.jsonl", "w") as f:
    for e in evs:
        f.write(json.dumps(e) + "\n")
json.dump({"t0": t0, "command": "fake", "exit_code": 0}, open("meta.json", "w"))
PYEOF
python3 /Users/shuangyuexingxun/Desktop/mc_tools/scripts/analyze_timeline.py --out /tmp/vt_demo --phase merge
```

Expected: 输出 `[ok] timeline.md`；文件含 `## 第 0 秒`（操作含 screen_open + tool_action）、`## 第 1 秒`（mc_click）、`## 第 2 秒`（CsLookItemScreen）、截图与描述挂载、头部统计含 `CsboxScreen 1s`。

- [ ] **Step 3: main() 接线 merge 并复测**

把 `main()` 内 `args.phase` 分支扩展：

```python
    if args.phase in ("merge", "all"):
        merge_timeline(args.out)
```

复测 Step 2 命令，Expected 同上且 timeline.md 内容无回归（第二次跑输出一致）。

- [ ] **Step 4: Commit**

```bash
cd /Users/shuangyuexingxun/Desktop/mc_tools && git add scripts/analyze_timeline.py && git commit -m "feat: analyze_timeline 按秒合并生成 timeline.md"
```

---

### Task 4: analyze_timeline.py — 疑点分析

**Files:**
- Modify: `/Users/shuangyuexingxun/Desktop/mc_tools/scripts/analyze_timeline.py`

**Interfaces:**
- Consumes: Task 2/3 的 `shots/`、`descriptions/`、`events/events.jsonl`、`timeline.md` 内容
- Produces: 在 timeline.md 尾部追加 `## 疑点清单` 章节：`### 规则级`（截图断裂/长同屏/未处理点击）+ `### 视觉级`（每张截图 JSON 疑点聚合）

- [ ] **Step 1: 加规则级疑点函数（完整代码）**

在 `merge_timeline` 函数后、`main()` 前插入：

```python
SUSPECT_PROMPT = (
    "你是 Minecraft GUI 排版审查专家。分析这张截图, 若存在 UI 问题, 输出 JSON 数组, "
    "每项 {\"type\": \"alignment|spacing|overlap|overflow|truncation|contrast|layout\", "
    "\"severity\": \"high|medium|low\", \"description\": \"中文描述\"}。"
    "没有发现问题则输出 []。只输出 JSON, 不要其他文字。"
)


def rule_suspects(out_dir: str) -> list:
    issues = []
    shots = sorted(glob.glob(os.path.join(out_dir, "shots", "shot_*.png")))
    secs = [int(re.search(r"shot_(\d+)\.png", os.path.basename(s)).group(1)) for s in shots]
    for i in range(1, len(secs)):
        if secs[i] - secs[i - 1] > 3:
            issues.append(f"截图断裂: {secs[i-1]}s 与 {secs[i]}s 之间缺失 {secs[i]-secs[i-1]-1} 张")
    events_path = os.path.join(out_dir, "events", "events.jsonl")
    if os.path.exists(events_path):
        with open(events_path, encoding="utf-8") as f:
            lines = [l for l in f if l.strip()]
        for line in lines:
            try:
                ev = json.loads(line)
            except json.JSONDecodeError:
                continue
            d = ev.get("data", {})
            if ev.get("type") == "tool_action" and d.get("screen_handled") is False:
                issues.append(f"未处理操作: {d.get('tool')} 在 {d.get('screen')} 未被消费 (screen_handled=false)")
    return issues


def visual_suspects(out_dir: str, model: str) -> list:
    issues = []
    desc_dir = os.path.join(out_dir, "descriptions")
    for shot in sorted(glob.glob(os.path.join(out_dir, "shots", "shot_*.png"))):
        base = os.path.basename(shot).replace(".png", ".txt")
        txt_path = os.path.join(desc_dir, base)
        if not os.path.exists(txt_path):
            continue
        content = open(txt_path, encoding="utf-8").read()
        if "ERROR" in content:
            continue
        try:
            raw = ollama_text(SUSPECT_PROMPT, model, shot)
            parsed = json.loads(raw)
            for item in parsed if isinstance(parsed, list) else []:
                item["shot"] = os.path.basename(shot)
                issues.append(item)
        except Exception as e:
            print(f"[warn] 疑点分析失败 {base}: {e}")
    return issues


def analyze_suspicions(out_dir: str, model: str) -> None:
    tl_path = os.path.join(out_dir, "timeline.md")
    if not os.path.exists(tl_path):
        print("[warn] timeline.md 不存在, 先跑 merge")
        return
    rules = rule_suspects(out_dir)
    vis = visual_suspects(out_dir, model)
    lines = ["", "## 疑点清单", "### 规则级"]
    if rules:
        lines.extend(f"- {r}" for r in rules)
    else:
        lines.append("- 无")
    lines.append("### 视觉级")
    if vis:
        for it in sorted(vis, key=lambda v: {"high": 0, "medium": 1, "low": 2}.get(v.get("severity", "low"))):
            lines.append(f"- [{it.get('severity')}] {it.get('type')} {it.get('shot')}: {it.get('description')}")
    else:
        lines.append("- 无")
    with open(tl_path, "a", encoding="utf-8") as f:
        f.write("\n".join(lines))
    print(f"[ok] 疑点清单: 规则级 {len(rules)} 条, 视觉级 {len(vis)} 条")
```

- [ ] **Step 2: 规则级验证**

```bash
python3 /Users/shuangyuexingxun/Desktop/mc_tools/scripts/analyze_timeline.py --out /tmp/vt_demo --phase suspect 2>&1 | tail -3
tail -12 /tmp/vt_demo/timeline.md
```

Expected: 规则级输出包含"截图断裂: ... 缺失 0 张"（两张 fake 截图秒差 1，无断裂；再构造断裂：删除中间一张后再跑应出现断裂项，验证后恢复）。为确定性验证断裂，先执行：

```bash
cp /tmp/vt_demo/shots/shot_1754000001.png /tmp/vt_demo/shots/shot_1754000002.png
python3 /Users/shuangyuexingxun/Desktop/mc_tools/scripts/analyze_timeline.py --out /tmp/vt_demo --phase suspect 2>&1 | tail -3
```

Expected: `规则级 0 条`（1s 间隔无断裂）。随后：

```bash
rm /tmp/vt_demo/shots/shot_1754000002.png
```

- [ ] **Step 3: main() 接线并全链路复测**

```python
    if args.phase in ("suspect", "all"):
        analyze_suspicions(args.out, args.model)
```

```bash
python3 /Users/shuangyuexingxun/Desktop/mc_tools/scripts/analyze_timeline.py --out /tmp/vt_demo --phase all
```

Expected: describe 跳过（续跑）、merge 重写 timeline.md、suspect 追加疑点清单（视觉级依赖模型调用，存在即输出）。

- [ ] **Step 4: Commit**

```bash
cd /Users/shuangyuexingxun/Desktop/mc_tools && git add scripts/analyze_timeline.py && git commit -m "feat: analyze_timeline 疑点分析 (规则级+视觉级)"
```

---

### Task 5: record_visual_test.sh — 录像器 + 一条龙编排

**Files:**
- Create: `/Users/shuangyuexingxun/Desktop/mc_tools/scripts/record_visual_test.sh`

**Interfaces:**
- Consumes: `scripts/mc.sh`（MCP 调用）、`scripts/analyze_timeline.py`、`scripts/test_csbox_ext.sh`（默认被测命令）
- Produces: `$OUT/{meta.json, events/events.jsonl, shots/shot_<epoch>.png, recorder.log}` + （默认）`timeline.md`
- CLI: `record_visual_test.sh [--cmd "测试命令"] [--model M] [--out DIR] [--port N] [--skip-analyze] [--analyze-only DIR]`

- [ ] **Step 1: 写脚本（完整代码）**

```bash
#!/usr/bin/env bash
# record_visual_test.sh — 视觉全面测试: 每秒截图+日志增量落盘 → 跑测试 → 分析
# 用法:
#   record_visual_test.sh                       # 包 test_csbox_ext.sh, 录像+分析全自动
#   record_visual_test.sh --cmd "sleep 30"      # 跑任意命令
#   record_visual_test.sh --skip-analyze        # 只录像, 之后用 --analyze-only 分析
#   record_visual_test.sh --analyze-only DIR    # 只分析已有录像
# 退出码: 测试命令退出码透传 (analyze-only 模式: 0)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PORT="${MCP_PORT:-41501}"
OUT="${VISUAL_TIMELINE_OUT:-/tmp/visual_timeline}"
MODEL="qwen3-vl:8b-instruct"
CMD=""
SKIP_ANALYZE=0
ANALYZE_ONLY=""

while [ $# -gt 0 ]; do
  case "$1" in
    --cmd) CMD="$2"; shift 2 ;;
    --model) MODEL="$2"; shift 2 ;;
    --out) OUT="$2"; shift 2 ;;
    --port) PORT="$2"; shift 2 ;;
    --skip-analyze) SKIP_ANALYZE=1; shift ;;
    --analyze-only) ANALYZE_ONLY="$2"; shift 2 ;;
    *) echo "未知参数: $1" >&2; exit 2 ;;
  esac
done
export MCP_PORT="${PORT}"
export VISUAL_TIMELINE_OUT="${OUT}"
m() { "${SCRIPT_DIR}/mc.sh" "$@"; }

# ---- analyze-only 模式: 不做录像 ----
if [ -n "${ANALYZE_ONLY}" ]; then
  OUT="${ANALYZE_ONLY}"
  [ -d "${OUT}/shots" ] || { echo "录像目录不存在: ${OUT}" >&2; exit 2; }
  python3 "${SCRIPT_DIR}/analyze_timeline.py" --out "${OUT}" --model "${MODEL}" --phase all
  echo "分析完成: ${OUT}/timeline.md"
  exit 0
fi

# ---- 前置: 客户端可达 ----
m mc_status '{}' >/dev/null 2>&1 || { echo "客户端不可达 (MCP ${PORT}), 先 deploy.sh" >&2; exit 2; }

mkdir -p "${OUT}/shots" "${OUT}/events"
rm -f "${OUT}/recorder.log" "${OUT}/events/events.jsonl"
: > "${OUT}/recorder.log"
: > "${OUT}/events/events.jsonl"

T0_MS=$(( $(date +%s) * 1000 ))
RECORDER_PID=""

cleanup() {
  [ -n "${RECORDER_PID}" ] && kill "${RECORDER_PID}" 2>/dev/null || true
}
trap cleanup EXIT

# ---- 录像循环: 每秒 增量日志 + 截图 ----
SINCE_SEQ=0
LAST_SHOT_SEC=0
record_loop() {
  while true; do
    local t0_loop=$(date +%s)
    local r=""
    if r=$(m mc_logs "{\"since_seq\":${SINCE_SEQ}}" 2>>"${OUT}/recorder.log"); then
      local next_seq=""
      next_seq=$(printf '%s' "${r}" | python3 -c '
import json, sys
try:
    d = json.load(sys.stdin)
except Exception:
    print("")
    sys.exit(0)
last = 0
for ev in d.get("events", []):
    s = ev.get("seq", 0)
    if s > last:
        last = s
    print(json.dumps(ev))
print("__SEQ__" + str(last))
')
      # 行: 每条事件 json; 末尾 __SEQ__<n> 更新游标
      printf '%s\n' "${next_seq}" | while IFS= read -r line; do
        case "${line}" in __SEQ__*) SINCE_SEQ="${line#__SEQ__}" ;; *) [ -n "${line}" ] && echo "${line}" >> "${OUT}/events/events.jsonl" ;; esac
      done
    else
      echo "[$(date +%H:%M:%S)] mc_logs 失败" >> "${OUT}/recorder.log"
    fi
    local now=$(date +%s)
    if [ "${now}" -gt "${LAST_SHOT_SEC}" ]; then
      if m mc_shot "{\"path\":\"${OUT}/shots/shot_${now}.png\"}" >>"${OUT}/recorder.log" 2>&1; then
        LAST_SHOT_SEC="${now}"
      else
        echo "[$(date +%H:%M:%S)] mc_shot 失败" >> "${OUT}/recorder.log"
      fi
    fi
    local elapsed=$(( $(date +%s) - t0_loop ))
    [ "${elapsed}" -lt 1 ] && sleep $(( 1 - elapsed ))
  done
}

record_loop &
RECORDER_PID=$!
echo "录像开始 (输出: ${OUT})"
sleep 1

# ---- 跑测试 ----
if [ -n "${CMD}" ]; then
  eval "${CMD}"
  RC=$?
else
  "${SCRIPT_DIR}/test_csbox_ext.sh" || RC=$?
  RC=${RC:-0}
fi
T1_MS=$(( $(date +%s) * 1000 ))
echo "测试结束 (退出码: ${RC})"

kill "${RECORDER_PID}" 2>/dev/null || true
wait "${RECORDER_PID}" 2>/dev/null || true
RECORDER_PID=""

# ---- meta + 分析 ----
python3 - "${OUT}" "${T0_MS}" "${T1_MS}" "${RC}" "${CMD:-default}" <<'PYEOF'
import json, os, sys
out, t0, t1, rc, cmd = sys.argv[1:]
meta = {"t0": int(t0), "t1": int(t1), "exit_code": int(rc), "command": cmd}
shot_n = len([f for f in os.listdir(os.path.join(out, "shots")) if f.endswith(".png")])
ev_n = 0
with open(os.path.join(out, "events", "events.jsonl")) as f:
    ev_n = sum(1 for l in f if l.strip())
meta["shot_count"] = shot_n
meta["event_count"] = ev_n
json.dump(meta, open(os.path.join(out, "meta.json"), "w"), ensure_ascii=False, indent=2)
print(f"meta: {shot_n} 截图 / {ev_n} 事件")
PYEOF

if [ "${SKIP_ANALYZE}" -eq 1 ]; then
  echo "已跳过分析。之后运行: record_visual_test.sh --analyze-only ${OUT}"
else
  python3 "${SCRIPT_DIR}/analyze_timeline.py" --out "${OUT}" --model "${MODEL}" --phase all
  echo "报告: ${OUT}/timeline.md"
fi
exit "${RC}"
```

注意：`record_loop` 子 shell 中 `SINCE_SEQ` 的更新通过管道 `while` 在子进程完成——**该写法在 bash 中 `while` 管道内的变量赋值不传回子 shell**，但这里 `record_loop` 本身是后台子进程，赋值在其自身 shell 内生效，可正常工作；`LAST_SHOT_SEC` 同理。若 `local` 报错（zsh 兼容性），将函数体首行 `local t0_loop` 改为普通变量。

- [ ] **Step 2: 短命令端到端验证**

```bash
cd /Users/shuangyuexingxun/Desktop/mc_tools && scripts/record_visual_test.sh --cmd "sleep 15" --out /tmp/vt_e2e --skip-analyze
ls /tmp/vt_e2e/shots/ | wc -l
cat /tmp/vt_e2e/meta.json
```

Expected: 截图 ≥14 张（1s 间隔 15s 录制）、meta.json 含 t0/t1/exit_code=0/shot_count/event_count、recorder.log 无连续报错。

- [ ] **Step 3: 断点续跑 + 全链路**

```bash
cd /Users/shuangyuexingxun/Desktop/mc_tools && scripts/record_visual_test.sh --analyze-only /tmp/vt_e2e 2>&1 | tail -3
head -30 /tmp/vt_e2e/timeline.md
```

Expected: describe 逐张处理（或跳过已完成）、`timeline.md` 头部统计与按秒区块、`疑点清单` 章节存在。视觉描述部分只验证格式（模型输出非空）。

- [ ] **Step 4: Commit**

```bash
cd /Users/shuangyuexingxun/Desktop/mc_tools && git add scripts/record_visual_test.sh && git commit -m "feat: record_visual_test 录像器+一条龙编排"
```

---

### Task 6: 完整 E1-E11 验收

**Files:**
- 无新文件；运行 `record_visual_test.sh` 默认命令（包 E1-E11）

**Interfaces:**
- Consumes: Task 1-5 全部产物

- [ ] **Step 1: 全流程录像（跳过分析，先拿数据）**

```bash
cd /Users/shuangyuexingxun/Desktop/mc_tools && scripts/record_visual_test.sh --out /tmp/vt_full --skip-analyze
```

Expected: 测试套件输出 `34 通过 / 0 失败 / 0 警告`；meta.json 的 exit_code=0；shot_count ≥ 300（E1-E11 约 5-10 分钟）。若测试因环境失败，先修环境再重跑。

- [ ] **Step 2: 全流程分析（后台跑，可中断续跑）**

```bash
cd /Users/shuangyuexingxun/Desktop/mc_tools && nohup scripts/record_visual_test.sh --analyze-only /tmp/vt_full > /tmp/vt_analyze.log 2>&1 &
tail -f /tmp/vt_analyze.log
```

Expected: 逐张 `[ok] shot_xxx.png (n/N)` 进度；结束后 `报告: /tmp/vt_full/timeline.md`。

- [ ] **Step 3: 验收时间线质量**

```bash
grep -c "^## 第" /tmp/vt_full/timeline.md
grep -c "tool_action" /tmp/vt_full/timeline.md
grep -A5 "疑点清单" /tmp/vt_full/timeline.md | head -20
```

Expected: `## 第 N 秒` 区块数 ≈ 截图数（含无事件秒）；tool_action 出现（E 系列操作可审计）；疑点清单非空（至少规则级，如 E5 循环中的快速开关无断裂）。

人工抽查 3 个代表性秒段（开箱屏打开瞬间、翻页瞬间、检视屏）确认：操作-截图-描述三者时间对齐、描述内容与截图相符。

- [ ] **Step 4: 提交最终产物说明**

```bash
cd /Users/shuangyuexingxun/Desktop/mc_tools && git add docs/CHANGELOG.md && git commit -m "docs: Visual Timeline 视觉全面测试流程 (E1-E11 全流程验收)"
```

若 CHANGELOG.md 本次无新增条目，则跳过此步（不创建空提交）。
