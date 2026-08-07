#!/usr/bin/env python3
"""test_animation_aesthetics.py — CS2-Box 聚光灯放大效果(开箱动画)审美测试。

自动流程:
  清物品 → give 箱子+钥匙 → 右键开箱 → 点开启 → 动画期间全量连拍
  → 按时间偏移挑 3 关键帧 (--f1/--f2/--f3) → 每帧 5 维度审美分析
  → 报告 report.md (含每帧判定 + 疑点图路径)

用法:
  python3 scripts/test_animation_aesthetics.py [--port N] [--out DIR]
    [--interval SEC] [--model M] [--verify-model M] [--f1 T] [--f2 T] [--f3 T]
  python3 scripts/test_animation_aesthetics.py clean [--out DIR]
    [--report] [--dry-run] [--yes]

退出码: 0=全 PASS  1=有 FAIL  2=仅 WARN (可接 CI)
         clean: 0=成功/无事可做  1=有文件删除失败
"""
import argparse
import json
import os
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
sys.path.insert(0, str(Path.home() / "Desktop/mc_tools/scripts"))

from csxlib.helpers import BoxEnv, setup_items, select_slot, wait_screen
from csxlib.mcp import McpClient
from csxlib.setup import prepare_world

try:
    import vision_review as vr
except ImportError:
    vr = None

DEFAULT_PORT = 41501
DEFAULT_INTERVAL = 0.1
DEFAULT_MODEL = "gemma4:12b"
DEFAULT_VERIFY = "qwen3-vl:8b-instruct"
AESTHETIC_ASPECTS = ["配色", "层次", "比例", "间距", "风格"]
DEFAULT_OUT = str(Path(__file__).resolve().parent.parent / "build" / "animation_aesthetics")


def ollama_json(prompt: str, model: str, img: str):
    """单模型单图 JSON 输出。"""
    import base64
    import urllib.request
    import json as _json

    b64 = base64.b64encode(Path(img).read_bytes()).decode()
    body = _json.dumps({"model": model, "prompt": prompt, "images": [b64],
                        "stream": False, "options": {"temperature": 0}}).encode()
    req = urllib.request.Request("http://localhost:11434/api/generate", data=body,
                                 headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=240) as resp:
        return _json.loads(resp.read().decode()).get("response", "")


def analyze_shot(img: str, model: str, verify_model: str, out_dir: Path):
    """单帧 5 维度审美分析 → (verdict, score, notes)。

    verdict: PASS / WARN / FAIL; score: 5 维平均; notes: 疑点描述列表。
    """
    prompt = (
        "你是资深 Minecraft 开箱动画审美审查专家。这张截图是 CS2-Box 的开箱动画画面, "
        "包含: 模糊压暗背景、居中圆形放大透镜(聚光灯放大效果)、卡片条带滚动。\n"
        "输出 JSON 数组, 每项 {\"aspect\": \"配色|层次|比例|间距|风格\", "
        "\"score\": 0-10, \"comment\": \"中文评价\"}。\n"
        "评分要点:\n"
        "配色: 卡片边框、透镜背板、聚光条带是否协调;\n"
        "层次: 放大透镜内容是否清晰突出、与背景主次分明;\n"
        "比例: 透镜圆盘大小、卡片放大倍率、条带布局是否和谐;\n"
        "间距: 卡片间距、透镜内卡片位置是否舒适;\n"
        "风格: 是否具有 CS:GO 开箱的质感、有无违和元素。\n"
        "没有问题打 8-9 分简述亮点, 有问题如实低分并具体指出。只输出 JSON。"
    )
    raw = ollama_json(prompt, model, img)
    clean = raw.strip()
    if clean.startswith("```"):
        clean = clean.split("\n", 1)[-1] if "\n" in clean else clean[3:]
        if clean.endswith("```"):
            clean = clean[:-3]
        clean = clean.strip()
    try:
        items = json.loads(clean)
    except json.JSONDecodeError:
        return "WARN", 0.0, [f"主审模型返回非 JSON: {raw[:120]}"]

    if not isinstance(items, list):
        return "WARN", 0.0, [f"主审输出结构异常: {str(items)[:120]}"]

    scores = []
    notes = []
    for it in items:
        asp = it.get("aspect", "")
        if asp not in AESTHETIC_ASPECTS:
            continue
        try:
            sc = float(it.get("score", 0))
        except (TypeError, ValueError):
            sc = 0.0
        scores.append(sc)
        if sc < 8.0:
            notes.append(f"{asp} {sc:.1f}分: {it.get('comment', '')}")
        else:
            notes.append(f"{asp} {sc:.1f}分: {it.get('comment', '')}")

    if not scores:
        return "WARN", 0.0, ["主审未输出有效维度"]

    avg = sum(scores) / len(scores)

    # FAIL (平均 <6.5 或有低分项) → 复核模型交叉确认
    low = [n for n in notes if "分: " in n and float(n.split("分:")[0].split()[-1]) < 6.5]
    if avg < 6.5 or low:
        vprompt = (
            "你是 Minecraft 开箱动画质量复核专家。分析这张截图, 重点检查:\n"
            "1. 圆形透镜(聚光灯放大)是否存在且内容清晰\n"
            "2. 是否有紫黑/粉黑贴图、渲染错误、元素遮挡\n"
            "3. 放大内容是否与整体画面协调\n"
            "输出 JSON: {\"ok\": true/false, \"issue\": \"问题描述或空串\"}。只输出 JSON。"
        )
        try:
            vraw = ollama_json(vprompt, verify_model, img)
            v = json.loads(vraw)
            if v.get("ok") is False:
                return "FAIL", avg, notes + [f"复核: {v.get('issue', '')}"]
            if v.get("ok") is True:
                return "WARN", avg, notes + ["复核确认: 主审疑点未获辅模型确认"]
        except Exception as e:
            return "WARN", avg, notes + [f"复核失败: {e}"]
        return "WARN", avg, notes

    return "PASS", avg, notes


def do_clean(args) -> int:
    """clean 子命令: 清理输出目录下的测试照片 (默认保留 report.md)。

    --dry-run 只预览不删; --report 连同 report.md 一起删; --yes 跳过确认。
    目录不存在或无匹配文件 → 提示并返回 0 (幂等); 有删除失败 → 返回 1。
    """
    out_dir = Path(args.out).resolve()
    if not out_dir.is_dir():
        print(f"无测试产物可清理: {out_dir} 不存在")
        return 0

    targets: list[Path] = []
    shots_dir = out_dir / "shots"
    if shots_dir.is_dir():
        targets = sorted(shots_dir.glob("*.png"))
    if args.report and (out_dir / "report.md").is_file():
        targets.append(out_dir / "report.md")

    if not targets:
        what = "shots/*.png" + (", report.md" if args.report else "")
        print(f"无测试产物可清理: {out_dir} (无 {what})")
        return 0

    print(f"将清理 {len(targets)} 个文件:")
    for p in targets:
        print(f"  {os.path.relpath(p)}")

    if args.dry_run:
        print("[dry-run] 未删除任何文件")
        return 0

    if not args.yes:
        try:
            ans = input(f"确认删除以上 {len(targets)} 个文件? [y/N] ").strip().lower()
        except EOFError:
            ans = ""
        if ans != "y":
            print("已取消")
            return 0

    failed = 0
    for p in targets:
        try:
            p.unlink()
        except OSError as e:
            failed += 1
            print(f"删除失败: {p}: {e}")
    print(f"清理完成: 删除 {len(targets) - failed} 个, 失败 {failed} 个")
    return 1 if failed else 0


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description="CS2-Box 聚光灯放大效果审美测试")
    ap.add_argument("--port", type=int, default=int(os.environ.get("MCP_PORT", DEFAULT_PORT)))
    ap.add_argument("--out", type=str, default=DEFAULT_OUT)
    ap.add_argument("--interval", type=float, default=DEFAULT_INTERVAL)
    ap.add_argument("--model", type=str, default=DEFAULT_MODEL)
    ap.add_argument("--verify-model", type=str, default=DEFAULT_VERIFY)
    ap.add_argument("--f1", type=float, default=0.5, help="关键帧1: T0+秒 (动画起始)")
    ap.add_argument("--f2", type=float, default=5.5, help="关键帧2: T0+秒 (减速中段)")
    ap.add_argument("--f3", type=float, default=-1, help="关键帧3: -1=自动等结果屏")
    sub = ap.add_subparsers(dest="command")
    clean_p = sub.add_parser(
        "clean", help="清理测试产物 (默认只删 shots/*.png, 保留 report.md)")
    clean_p.add_argument("--out", type=str, default=DEFAULT_OUT,
                         help="清理的输出目录 (默认 %(default)s)")
    clean_p.add_argument("--report", action="store_true",
                         help="连同 report.md 一起删")
    clean_p.add_argument("--dry-run", action="store_true",
                         help="只打印待删清单, 不实际删除")
    clean_p.add_argument("--yes", action="store_true", help="跳过删除前确认")
    args = ap.parse_args(argv)

    if args.command == "clean":
        return do_clean(args)

    out_dir = Path(args.out).resolve()
    shots_dir = out_dir / "shots"
    shots_dir.mkdir(parents=True, exist_ok=True)
    report_lines: list[str] = []
    exit_code = 0

    def log(msg: str):
        print(msg)
        report_lines.append(msg)

    client = McpClient(port=args.port)
    env = BoxEnv(client=client)
    try:
        client.call("mc_status", {})
    except Exception as e:
        log(f"FAIL 客户端不可达 (MCP {args.port}): {e}")
        log("前置: 先启动 ./gradlew :v1_21_1:runClient -Pactive_versions=1.21.1")
        _write_report(out_dir, report_lines)
        return 2

    # ---- 0. 环境准备 ----
    prepare_world(env, log=log)

    # ---- 0.5 关闭遗留屏幕 (如上次运行停在 CsLookItemScreen) ----
    cur = env.screen_class()
    if cur:
        log(f"当前屏 {cur}, ESC 关闭")
        env.client.call("mc_key", {"key": "key.keyboard.escape"})
        time.sleep(1)
        if env.screen_class():
            log("WARN ESC 后仍有屏幕: " + env.screen_class())

    # ---- 1. 清物品 + 发箱子钥匙 ----
    env.exec_cmd("/clear @s")
    env.exec_cmd("/clear @s csgobox:csgo_box")
    env.exec_cmd("/clear @s csgobox:csgo_key0")
    time.sleep(0.3)
    env.exec_cmd('/give @s csgobox:csgo_box[csgobox:box_id="csgobox:weapon_supply_box"] 1')
    env.exec_cmd("/give @s csgobox:csgo_key0 1")
    time.sleep(0.5)

    slot = env.client.call_full("mc_inventory", {})
    slot_num = -1
    items = slot.get("items", []) if isinstance(slot, dict) else slot
    for i in items:
        if i.get("area") == "hotbar" and "csgo_box" in str(i.get("id", "")):
            slot_num = i["slot"]
            break
    if slot_num < 0:
        slot_num = 0  # fallback: 默认槽 1
    if not select_slot(env, slot_num, expect="csgobox:csgo_box"):
        log("WARN 未能确认选中箱子 (继续, 槽位事件仍会触发)")

    # ---- 2. 开箱到动画屏 ----
    env.client.call("mc_key", {"key": "key.mouse.right"})
    if not wait_screen(env, "CsboxScreen", 8, runner=None):
        log("FAIL 未进入开箱预览屏 (CsboxScreen)")
        _write_report(out_dir, report_lines)
        return 1
    opened = False
    for _ in range(3):
        env.client.call("mc_click", {"x": env.open_btn[0], "y": env.open_btn[1]})
        if wait_screen(env, "CsboxProgressScreen", 5, runner=None):
            opened = True
            break
        env.t_sleep(1)
    if not opened:
        log("FAIL 未进入动画屏 (CsboxProgressScreen)")
        _write_report(out_dir, report_lines)
        return 1

    log("动画已开始, 开始连拍...")
    t0 = time.monotonic()
    burst: list[tuple[float, Path]] = []
    max_wait = 30.0
    while time.monotonic() - t0 < max_wait:
        st = env.status()
        cls = st.get("screen_class", "")
        if cls != "CsboxProgressScreen":
            break
        ts = time.monotonic() - t0
        p = shots_dir / f"burst_{int(ts * 1000):06d}.png"
        try:
            env.client.call("mc_shot", {"path": str(p)})
            # mc_shot 是异步保存 (Screenshot 回调里拷贝到目标路径),
            # 轮询等待文件出现, 超时按丢帧处理
            shot_deadline = time.monotonic() + 2.0
            while time.monotonic() < shot_deadline and not p.is_file():
                time.sleep(0.05)
            if p.is_file():
                burst.append((ts, p))
            else:
                print(f"[!] 帧 {ts:.2f}s 保存超时 (异步拷贝未完成)")
        except Exception:
            pass
        time.sleep(args.interval)

    if not burst:
        log("FAIL 未捕获任何动画帧")
        _write_report(out_dir, report_lines)
        return 1
    log(f"共捕获 {len(burst)} 帧 (最后屏幕: {env.screen_class()})")

    # ---- 3. 挑关键帧 ----
    key_frames: list[tuple[str, Path]] = []
    picks = [("f1", args.f1), ("f2", args.f2), ("f3", args.f3)]
    for name, offset in picks:
        if offset is None:
            continue
        if offset < 0:
            p = burst[-1][1]
            label = f"{name}_end"
        else:
            best = min(burst, key=lambda b: abs(b[0] - offset))
            p = best[1]
            label = f"{name}_{best[0]:.2f}s"
        key_frames.append((label, p))
        log(f"关键帧 {label}: {p}")

    # ---- 4. 每帧 5 维度审美分析 ----
    results = []
    for label, p in key_frames:
        verdict, avg, notes = analyze_shot(str(p), args.model, args.verify_model, out_dir)
        results.append((label, p, verdict, avg, notes))
        for n in notes:
            log(f"  [{label}] {n}")
        log(f"  [{label}] 判定={verdict} 均分={avg:.1f}")
        if verdict == "FAIL":
            exit_code = max(exit_code, 1)
        elif verdict == "WARN" and exit_code == 0:
            exit_code = 2

    # ---- 5. 报告 ----
    log("")
    log("===== 报告 =====")
    for label, p, verdict, avg, notes in results:
        log(f"### {label} — {verdict} (均分 {avg:.1f})")
        for n in notes:
            log(f"- {n}")
        log(f"- 截图: {p}")
    log("")
    log(f"退出码: {exit_code} ({'全 PASS' if exit_code == 0 else '有 FAIL' if exit_code == 1 else '仅 WARN'})")
    _write_report(out_dir, report_lines)
    return exit_code


def _write_report(out_dir: Path, lines: list[str]):
    (out_dir / "report.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


if __name__ == "__main__":
    sys.exit(main())
