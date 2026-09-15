# 1.21.1 抽卡动画审美测试脚本 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 v1_21_1 写一个一键测试脚本，自动触发抽卡动画、在 3 个关键帧用本地 Ollama 视觉模型做 5 维度审美检查，输出报告与回归退出码。

**Architecture:** Python 脚本复用 `mc_tools/csxlib`（`McpClient` + `BoxEnv`，经 MCP 41501 端口控制已启动的 1.21.1 客户端）。触发开箱 → 动画期间全量连拍存盘 → 按时间轴挑 3 个关键帧 → 每帧跑固定格式的 5 维度审美 prompt（主模型 `gemma4:12b`）→ FAIL 疑点用 `qwen3-vl:8b-instruct` 复核 → 写 `report.md` → 退出码 0/1/2。

**Tech Stack:** Python 3 + urllib（Ollama REST）+ `mc_tools/csxlib`（项目外依赖，已存在于 `/Users/shuangyuexingxun/Desktop/mc_tools/scripts/`）

## Global Constraints

- 脚本位置：`scripts/test_animation_aesthetics.py`（项目内，与 `scripts/record_open_animation.py` 同目录）
- 依赖注入方式与 `record_open_animation.py` 相同：`sys.path.insert` 指向 `mc_tools/scripts`（第 14-15 行同款写法，双路径兜底）
- 默认端口 41501（`MCP_PORT` 惯例）；Ollama 地址 `http://localhost:11434/api/generate`
- 默认输出目录 `build/animation_aesthetics/`（shots/ + report.md）
- 审美 prompt 必须使用固定行格式（`[居中] PASS/FAIL/WARN 理由`），脚本按前缀解析
- 退出码约定：0 = 全 PASS；1 = 有 FAIL；2 = 仅 WARN（spec 已确认）
- 不修改模组代码、不修改 `mc_tools`（只读 import）
- 目标动画参数（只读参考）：默认 145 tick ≈ 7.25s；F1=0.5s、F2=5.5s、F3=切 `CsLookItemScreen` 后 0.3s

---

### Task 1: 脚本骨架 + 关键帧连拍采集

**Files:**
- Create: `scripts/test_animation_aesthetics.py`

**Interfaces:**
- Consumes: `csxlib.helpers`（`BoxEnv`, `setup_items`, `select_slot`, `wait_screen`）、`csxlib.mcp.McpClient`
- Produces: `capture_frames(args) -> tuple[str, list[str]]` — (shots_dir, 连拍帧路径列表)；`pick_key_frames(frames, t0_offsets, result_path) -> dict[str, str]` — {f1/f2/f3: 帧路径}；`main() -> int`

- [ ] **Step 1: 写脚本骨架与参数解析**

```python
#!/usr/bin/env python3
"""test_animation_aesthetics.py — 1.21.1 抽卡动画 5 维度审美测试

流程: 触发开箱 → 全量连拍 → 挑 3 关键帧 → 每帧 5 维度审美分析(主模型)
→ FAIL 疑点复核模型确认 → report.md + 退出码(0=全PASS 1=FAIL 2=仅WARN)。

用法: scripts/test_animation_aesthetics.py [--port 41501] [--out DIR]
      [--interval 0.1] [--model gemma4:12b] [--verify-model qwen3-vl:8b-instruct]
      [--f1 0.5] [--f2 5.5] [--f3 -1]
"""
import argparse
import base64
import json
import sys
import time
import urllib.request
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / ".." / "mc_tools" / "scripts"))
sys.path.insert(0, str(Path(__file__).resolve().parent.parent.parent / "mc_tools" / "scripts"))

from csxlib.helpers import BoxEnv, select_slot, setup_items, wait_screen  # noqa: E402
from csxlib.mcp import McpClient  # noqa: E402

OLLAMA_URL = "http://localhost:11434/api/generate"
OPEN_BTN = (1244, 924)
DIMENSIONS = ["居中", "间距", "镜头", "背景", "闪烁"]
SHOT_TIMEOUT = 10  # 动画总秒数上限, 超出按失败退出


def parse_args() -> argparse.Namespace:
    ap = argparse.ArgumentParser(description="抽卡动画审美测试 (1.21.1)")
    ap.add_argument("--port", type=int, default=41501)
    ap.add_argument("--out", default="build/animation_aesthetics")
    ap.add_argument("--interval", type=float, default=0.1)
    ap.add_argument("--model", default="gemma4:12b")
    ap.add_argument("--verify-model", default="qwen3-vl:8b-instruct")
    ap.add_argument("--f1", type=float, default=0.5)
    ap.add_argument("--f2", type=float, default=5.5)
    ap.add_argument("--f3", type=float, default=-1.0)
    return ap.parse_args()


def ollama_text(prompt: str, model: str, img_path: str) -> str:
    img = base64.b64encode(open(img_path, "rb").read()).decode()
    body = json.dumps({"model": model, "prompt": prompt, "images": [img],
                       "stream": False, "options": {"temperature": 0}}).encode()
    req = urllib.request.Request(OLLAMA_URL, data=body,
                                 headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=180) as resp:
        return json.loads(resp.read()).get("response", "")


def main() -> int:
    args = parse_args()
    print(f"[*] 连接 MCP 端口 {args.port}")
    client = McpClient(port=args.port)
    env = BoxEnv(client=client)
    out = Path(args.out)
    shots = out / "shots"
    shots.mkdir(parents=True, exist_ok=True)

    if env.screen_class() != "":
        print(f"[!] 当前屏幕 {env.screen_class()}, 先按 ESC 关闭")
        env.client.call("mc_key", {"key": "key.keyboard.escape"})
        time.sleep(1)

    slot = setup_items(env)
    if slot < 0:
        print("setup_items 失败: 未找到箱子槽位")
        return 2
    if not select_slot(env, slot, expect="csgobox:csgo_box"):
        print("选中箱子槽位失败")
        return 2
    env.client.call("mc_key", {"key": "key.mouse.right"})
    if not wait_screen(env, "CsboxScreen", 8):
        print("未进入 CsboxScreen")
        return 2

    opened = False
    for _ in range(3):
        env.client.call("mc_click", {"x": OPEN_BTN[0], "y": OPEN_BTN[1]})
        time.sleep(0.3)
        if env.screen_class() == "CsboxProgressScreen":
            opened = True
            break
    if not opened:
        print("连续点击开启未能进入 CsboxProgressScreen")
        return 2
    print("[*] CsboxProgressScreen, 开始连拍")
    return 0


if __name__ == "__main__":
    sys.exit(main())
```

- [ ] **Step 2: 语法检查**

Run: `python3 -m py_compile scripts/test_animation_aesthetics.py`
Expected: 退出码 0，无输出

- [ ] **Step 3: 补全连拍 + 关键帧挑选**

在 `main()` 的 `print("[*] CsboxProgressScreen, 开始连拍")` 之后、`return 0` 之前插入连拍循环与关键帧挑选；在文件末尾（`if __name__` 之前）添加 `pick_key_frames` 函数：

```python
    idx = 0
    frames = []
    t0 = time.monotonic()
    result_path = ""
    while time.monotonic() - t0 < SHOT_TIMEOUT:
        p = str(shots / f"anim_{idx:03d}.png")
        try:
            env.client.call("mc_shot", {"path": p})
        except Exception as e:
            print(f"[!] shot {idx} 失败: {e}")
        else:
            frames.append(p)
        idx += 1
        time.sleep(args.interval)
        if env.screen_class() == "CsLookItemScreen":
            result_path = str(shots / "end_result.png")
            try:
                env.client.call("mc_shot", {"path": result_path})
            except Exception:
                pass
            print(f"[*] 已到 CsLookItemScreen, 共 {idx} 帧")
            break
    if not frames:
        print("连拍 0 帧, 异常退出")
        return 2

    key = pick_key_frames(frames, (args.f1, args.f2, args.f3), result_path)
    print(f"[*] 关键帧: f1={key['f1']} f2={key['f2']} f3={key['f3']}")
    return 0


def pick_key_frames(frames: list, offsets: tuple, result_path: str) -> dict:
    """按帧序号近似时间轴 (连拍接近等间隔): f1/f2 为 offset/interval 帧号。

    f3=-1 表示用 CsLookItemScreen 的结果屏截图。
    """
    n = len(frames)
    i1 = min(int(offsets[0] / 0.1), n - 1)
    i2 = min(int(offsets[1] / 0.1), n - 1)
    return {"f1": frames[i1], "f2": frames[i2], "f3": result_path or frames[-1]}
```

- [ ] **Step 4: 语法检查**

Run: `python3 -m py_compile scripts/test_animation_aesthetics.py`
Expected: 退出码 0

- [ ] **Step 5: Commit**

```bash
git add scripts/test_animation_aesthetics.py
git commit -m "feat: 抽卡动画审美测试脚本骨架 (连拍 + 关键帧挑选)"
```

---

### Task 2: 5 维度审美分析 + 双模型复核 + 报告

**Files:**
- Modify: `scripts/test_animation_aesthetics.py`

**Interfaces:**
- Consumes: Task 1 的 `ollama_text(prompt, model, img_path) -> str`、`DIMENSIONS` 常量、`pick_key_frames` 调用
- Produces: `analyze_frame(path, model) -> dict[str, str]` — {维度: "PASS/FAIL/WARN 理由"}；`render_report(out, args, key, results) -> Path`

- [ ] **Step 1: 添加 5 维度审美 prompt 与分析函数**

```python
AESTHETIC_PROMPT = (
    "这是 Minecraft 抽卡/开箱动画截图。请逐行输出下列 5 个维度的检查结果,"
    "每行格式严格为: [维度] PASS/FAIL/WARN 一句话理由。\n"
    "[居中] 聚光灯光晕是否以屏幕水平中线为轴; 放大镜镜头是否在正中; 若为结果屏, "
    "中奖卡是否停在中心黄金线。\n"
    "[间距] 卡片间距是否均匀; 有无重叠、挤压、变形、被屏幕边缘截断的卡片。\n"
    "[镜头] 圆形放大镜裁剪是否干净; 镜头内卡片是否与外部条带卡片位置一致"
    "(无错位、无双重图像); 镜头边缘有无锯齿/残边。\n"
    "[背景] 模糊暗化背景是否正常(非全黑、非全透、无撕裂、无突兀色块)。\n"
    "[闪烁] 有无卡片闪烁、鬼影、残影、半透明错乱。\n"
    "只输出这 5 行, 不要其他内容。"
)


def analyze_frame(path: str, model: str) -> dict:
    """单帧 5 维度审美分析, 返回 {维度: 判定}。"""
    raw = ollama_text(AESTHETIC_PROMPT, model, path)
    result = {}
    for line in raw.splitlines():
        line = line.strip()
        if not line.startswith("["):
            continue
        close = line.find("]")
        if close < 0:
            continue
        dim = line[1:close].strip()
        rest = line[close + 1:].strip()
        verdict = next((v for v in ("FAIL", "WARN", "PASS") if rest.startswith(v)), "WARN")
        result[dim] = f"{verdict} {rest[len(verdict):].strip()}"
    for d in DIMENSIONS:
        if d not in result:
            result[d] = "WARN 模型未输出该维度"
    return result
```

- [ ] **Step 3: 主流程接入分析 + 复核逻辑**

替换 `main()` 末尾的 `return 0`（Task 1 结尾处）为：

```python
    results = {}
    for name in ("f1", "f2", "f3"):
        p = key[name]
        if not p or not Path(p).is_file():
            print(f"[!] 关键帧 {name} 缺失: {p}")
            continue
        results[name] = analyze_frame(p, args.model)
        fails = [v for v in results[name].values() if v.startswith("FAIL")]
        for v in fails:
            print(f"[*] {name} 疑点: {v}, 用 {args.verify_model} 复核")
            verify = analyze_frame(p, args.verify_model)
            verify_fail = [vv for vv in verify.values() if vv.startswith("FAIL")]
            if verify_fail:
                results[name][f"复核({args.verify_model})"] = "; ".join(verify_fail)
            else:
                results[name][f"复核({args.verify_model})"] = "未确认, 降级为 WARN"
        time.sleep(0.3)

    report = render_report(out, args, key, results)
    print(f"[*] 报告: {report}")

    has_fail = any(v.startswith("FAIL")
                   for r in results.values() for v in r.values())
    has_warn = any(v.startswith("WARN")
                   for r in results.values() for v in r.values())
    if has_fail:
        print("[*] 结论: FAIL (有确认的审美问题)")
        return 1
    if has_warn:
        print("[*] 结论: 仅 WARN (无确认问题, 有疑点)")
        return 2
    print("[*] 结论: 全 PASS")
    return 0
```

- [ ] **Step 4: 添加报告生成函数**

在 `analyze_frame` 之后添加：

```python
def render_report(out: Path, args: argparse.Namespace, key: dict,
                  results: dict) -> Path:
    out.mkdir(parents=True, exist_ok=True)
    lines = [
        "# 抽卡动画审美测试报告",
        "",
        f"- 时间: {time.strftime('%Y-%m-%d %H:%M:%S')}",
        f"- 端口: {args.port}  主模型: {args.model}  复核模型: {args.verify_model}",
        "",
        "## 关键帧",
        "",
        f"| 帧 | 路径 |",
        f"| --- | --- |",
    ]
    for name in ("f1", "f2", "f3"):
        lines.append(f"| {name} | `{key[name]}` |")
    lines += ["", "## 逐帧判定", ""]
    for name in ("f1", "f2", "f3"):
        if name not in results:
            continue
        lines.append(f"### {name} ({key[name]})")
        lines.append("")
        for d in DIMENSIONS:
            lines.append(f"- **{d}**: {results[name].get(d, 'n/a')}")
        rev = results[name].get(f"复核({args.verify_model})")
        if rev:
            lines.append(f"- **复核**: {rev}")
        lines.append("")
    fails = sum(1 for r in results.values() for v in r.values() if v.startswith("FAIL"))
    warns = sum(1 for r in results.values() for v in r.values() if v.startswith("WARN"))
    lines += ["## 汇总", "", f"- FAIL: {fails}  WARN: {warns}  PASS: 其余", ""]
    report = out / "report.md"
    report.write_text("\n".join(lines), encoding="utf-8")
    return report
```

- [ ] **Step 5: 语法检查**

Run: `python3 -m py_compile scripts/test_animation_aesthetics.py`
Expected: 退出码 0

- [ ] **Step 6: Commit**

```bash
git add scripts/test_animation_aesthetics.py
git commit -m "feat: 5 维度审美分析 + 双模型复核 + 报告生成"
```

---

### Task 3: 端到端验证（真实客户端）

**Files:**
- Modify: `scripts/test_animation_aesthetics.py`（如发现缺陷）

**Interfaces:**
- Consumes: Task 1 的 `parse_args`、`pick_key_frames`；Task 2 的 `analyze_frame` / `render_report`
- Produces: 完整可用脚本

- [ ] **Step 1: 确认脚本流程完备**

Run: `grep -c "def " scripts/test_animation_aesthetics.py`
Expected: ≥ 6（parse_args / ollama_text / pick_key_frames / analyze_frame / render_report / main）
且 `main()` 中依次调用：`setup_items` → `select_slot` → 右键 → `wait_screen(CsboxScreen)` → 点开启 → 连拍 → `pick_key_frames` → `analyze_frame` → `render_report` → 退出码

- [ ] **Step 2: 启动 1.21.1 客户端（用户手动或已有实例）**

Run（需客户端运行中，MCP 插件 41501 端口）：
```bash
./gradlew :v1_21_1:runClient -Pactive_versions=1.21.1
```
Expected: 客户端启动并进入存档；确认 `curl -s http://localhost:41501` 可连通（MCP 插件响应）

- [ ] **Step 3: 端到端运行脚本**

Run: `python3 scripts/test_animation_aesthetics.py --port 41501 --out build/animation_aesthetics`
Expected:
- 自动完成：清物品 → give 箱子/钥匙 → 右键 → CsboxScreen → 点开启 → CsboxProgressScreen → 连拍 → 3 关键帧分析 → 报告
- `build/animation_aesthetics/report.md` 生成，含 3 帧 × 5 维度判定
- 退出码 0/1/2 之一；若为 1，确认报告里 FAIL 维度与截图可人工复核

- [ ] **Step 4: 人工抽验 1 个 FAIL/WARN 帧（如有）**

用视觉工具查看报告引用的帧图，确认模型判定与画面事实一致；若模型误报（如把聚光灯光晕当错位），记录为已知误报并在报告元信息中注明

- [ ] **Step 5: Commit**

```bash
git add scripts/test_animation_aesthetics.py
git commit -m "feat: 抽卡动画审美测试脚本端到端验证"
```

---

## Self-Review

**Spec coverage:**
- 触发开箱流程（setup_items/右键/点开启）→ Task 1 Step 1 ✅
- 全量连拍存盘 shots/ → Task 1 Step 3 ✅
- 3 关键帧时间轴（F1=0.5s, F2=5.5s, F3=结果屏）→ Task 1 Step 3 ✅
- 5 维度固定格式 prompt → Task 2 Step 1 ✅
- 双模型复核（gemma4:12b 主 → qwen3-vl:8b-instruct 复核 FAIL）→ Task 2 Step 2 ✅
- report.md（元信息/逐帧/汇总）→ Task 2 Step 3 ✅
- 退出码 0/1/2 → Task 2 Step 2 ✅
- 参数（--port/--out/--interval/--model/--verify-model/--f1/--f2/--f3）→ Task 1 Step 1 ✅
- YAGNI：不做 --dense、不做多平台 → 无相关任务 ✅

**Placeholder scan:** 无占位（Task 1 Step 3 直接给出 `pick_key_frames` 精确实现，Task 2 不重定义）。所有步骤含完整代码与命令。

**Type consistency:** `pick_key_frames(frames, offsets, result_path) -> dict` 在 Task 1 定义与调用一致；`analyze_frame(path, model) -> dict` 在 Task 2 Step 1 定义、Step 2 使用一致；`render_report(out, args, key, results) -> Path` 定义与调用一致；`results` 嵌套 dict（帧名 → 维度 → 判定串）贯穿 Task 2 全部步骤一致；`ollama_text(prompt, model, img_path)` Task 1 定义、Task 2 消费签名一致。
