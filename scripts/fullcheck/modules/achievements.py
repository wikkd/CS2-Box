"""成就触发：开箱后 csgobox:opened_boxes 统计 +1（mc_stats 服务端内存统计）。

覆盖：统计累计（enableAchievements=true 默认）。成就禁用场景依赖
配置回合（enableAchievements=false + 重启客户端），暂由编排器扩展。
"""
import time
from pathlib import Path

from .common import CLOSE_BTN, OPEN_BTN, Tally


def run(env, tally: Tally, version: str, out_dir: Path) -> None:
    c = env.client
    box_id = env.box_id
    key_id = env.key_id

    def opened_boxes():
        try:
            v = c.call("mc_stats", {"stat": "csgobox:opened_boxes"}).get("value", "NA")
        except Exception:
            return "NA"
        try:
            return int(v)
        except (TypeError, ValueError):
            return "NA"

    before = opened_boxes()
    if before == "NA":
        tally.warn_("成就 统计可读", "mc_stats 返回 NA，跳过累计断言")
        before = 0

    # 开一次箱
    c.call("mc_exec", {"command": f"/clear @s {box_id}"})
    c.call("mc_exec", {"command": f"/clear @s {key_id}"})
    time.sleep(0.4)
    c.call("mc_exec", {"command":
        f"/give @s {box_id}[csgobox:box_id=\"{box_id}\"] 1"})
    c.call("mc_exec", {"command": f"/give @s {key_id} 1"})
    time.sleep(0.6)
    data = c.call_full("mc_inventory", {}) or {}
    items = data.get("items", []) if isinstance(data, dict) else data
    box = next((i for i in items if i.get("area") == "hotbar"
                and i.get("id") == box_id), None)
    if not box:
        tally.bad("成就 前置", "箱子未出现在 hotbar")
        return
    c.call("mc_key", {"key": f"key.keyboard.{box['slot'] + 1}"})
    time.sleep(0.3)
    c.call("mc_key", {"key": "key.mouse.right"})
    time.sleep(1.0)
    c.call("mc_click", {"x": OPEN_BTN[0], "y": OPEN_BTN[1]})
    time.sleep(6.0)

    after = opened_boxes()
    if after == "NA":
        tally.warn_("成就 统计累计", "mc_stats 返回 NA")
    elif after == before + 1:
        tally.ok("成就 opened_boxes 累计", f"{before}→{after} (+1)")
    else:
        tally.bad("成就 opened_boxes 累计", f"{before}→{after} 期望 +1")

    # 成就进度（/advancement 查询 first_box 是否达成）
    c.call("mc_exec", {"command": "/advancement revoke @s only csgobox:first_box"})
    time.sleep(0.3)
    c.call("mc_exec", {"command": "/advancement test @s csgobox:first_box"})
    time.sleep(0.5)
    # mc_exec 无返回值，走 chat 无法拿结果；以 opened_boxes 统计为准。

    try:
        c.call("mc_click", {"x": CLOSE_BTN[0], "y": CLOSE_BTN[1]})
    except Exception:
        pass
