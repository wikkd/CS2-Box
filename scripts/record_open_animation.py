#!/usr/bin/env python3
"""record_open_animation.py — 录制开箱动画连拍帧

流程: 清物品 → give 箱子+钥匙 → 右键 → CsboxScreen → 点开启 →
动画期间每 INTERVAL 秒连拍 → 结束帧(CsLookItemScreen)。

用法: scripts/record_open_animation.py [--interval 0.1] [--out DIR] [--port 41501]
"""
import argparse
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / ".." / "mc_tools" / "scripts"))
sys.path.insert(0, str(Path(__file__).resolve().parent.parent.parent / "mc_tools" / "scripts"))

from csxlib.helpers import BoxEnv, box_slot, select_slot, setup_items, wait_screen  # noqa: E402
from csxlib.mcp import McpClient  # noqa: E402

OPEN_BTN = (1243, 923)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--interval", type=float, default=0.1)
    ap.add_argument("--out", default="/Users/shuangyuexingxun/Desktop/CS2-Box/build/animation_frames")
    ap.add_argument("--port", type=int, default=41501)
    args = ap.parse_args()

    client = McpClient(port=args.port)
    env = BoxEnv(client=client)
    out = Path(args.out)
    shots = out / "shots"
    shots.mkdir(parents=True, exist_ok=True)

    if env.screen_class() != "":
        print(f"[!] 当前屏幕 {env.screen_class()}, 先关闭")
        env.client.call("mc_key", {"key": "key.keyboard.escape"})
        time.sleep(1)

    slot = setup_items(env)
    if slot < 0:
        print("setup_items 失败: 未找到箱子槽位")
        return 2
    print(f"[*] 箱子槽位 {slot}")

    if not select_slot(env, slot, expect="csgobox:csgo_box"):
        print("选中箱子槽位失败")
        return 2
    env.client.call("mc_key", {"key": "key.mouse.right"})
    if not wait_screen(env, "CsboxScreen", 8):
        print("未进入 CsboxScreen")
        return 2
    print("[*] CsboxScreen, 点开启 (带重试)")
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

    idx = 0
    t_end = time.monotonic() + 12
    while time.monotonic() < t_end:
        try:
            env.client.call("mc_shot", {"path": str(shots / f"anim_{idx:03d}.png")})
        except Exception as e:
            print(f"[!] shot {idx} 失败: {e}")
        idx += 1
        time.sleep(args.interval)
        if env.screen_class() == "CsLookItemScreen":
            print("[*] 已到 CsLookItemScreen, 停止连拍")
            try:
                env.client.call("mc_shot", {"path": str(shots / "end_result.png")})
            except Exception:
                pass
            break
    print(f"[*] 完成, 共 {idx} 帧 -> {shots}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
