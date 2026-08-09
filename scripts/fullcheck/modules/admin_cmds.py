"""管理命令：/csbox reload、/csbox tutorial refresh、/csbox errors。

断言基于 mc_logs 的 chat 事件（mc_tools csxlib chat_last）。
"""
import json
import time
from pathlib import Path

from .common import (Tally, RUNS_DIR,
                   write_box_config, remove_box_config)

RELOAD_BOX = "fct_reload.json"


def run(env, tally: Tally, version: str, out_dir: Path) -> None:
    c = env.client

    def chat_has(keyword, timeout=6):
        end = time.time() + timeout
        while time.time() < end:
            data = c.call_full("mc_logs", {"filter": "chat", "tail": 30}) or {}
            events = data.get("events", []) if isinstance(data, dict) else []
            for ev in reversed(events):
                msg = (ev.get("data", {}) or {}).get("msg", "")
                if keyword in msg:
                    return True
            time.sleep(0.5)
        return False

    # ---- /csbox errors（无错误时）----
    c.call("mc_exec", {"command": "/csbox errors"})
    if chat_has("当前无箱子加载错误", 8):
        tally.ok("/csbox errors 无错误输出", "")
    else:
        tally.bad("/csbox errors 无错误输出", "未在 chat 看到「当前无箱子加载错误」")

    # ---- /csbox reload：改箱子 name 后 reload 生效 ----
    box_json = {
        "name": "重载前",
        "key": "csgobox:csgo_key0",
        "drop": 1.0,
        "random": [1, 1, 1, 1, 1],
        "grade1": [{"id": "minecraft:stick", "count": 1}],
    }
    write_box_config(version, RELOAD_BOX,
                     json.dumps(box_json, ensure_ascii=False, indent=2))
    time.sleep(0.5)
    c.call("mc_exec", {"command": "/csbox reload"})
    time.sleep(1.0)
    c.call("mc_exec", {"command": "/csbox info csgobox:" + RELOAD_BOX[:-5]})
    if chat_has("重载前", 8):
        tally.ok("/csbox reload 生效", f"info 显示 name=重载前")
    else:
        tally.bad("/csbox reload 生效", "info 未显示新 name")

    box_json["name"] = "重载后"
    write_box_config(version, RELOAD_BOX,
                     json.dumps(box_json, ensure_ascii=False, indent=2))
    time.sleep(0.5)
    c.call("mc_exec", {"command": "/csbox reload"})
    time.sleep(1.0)
    c.call("mc_exec", {"command": "/csbox info csgobox:" + RELOAD_BOX[:-5]})
    if chat_has("重载后", 8):
        tally.ok("/csbox reload 热更新", "改名后 info 显示新值")
    else:
        tally.bad("/csbox reload 热更新", "info 未更新")

    # ---- /csbox tutorial refresh ----
    c.call("mc_exec", {"command": "/csbox tutorial refresh"})
    if chat_has("教程", 8) or chat_has("tutorial", 8):
        tally.ok("/csbox tutorial refresh 执行", "chat 有教程相关反馈")
    else:
        tally.bad("/csbox tutorial refresh 执行", "chat 无反馈")

    # ---- /csbox errors（有错误箱子时）----
    bad_file = "fct_bad.json"
    write_box_config(version, bad_file, "{not valid json")
    time.sleep(0.5)
    c.call("mc_exec", {"command": "/csbox reload"})
    time.sleep(1.0)
    c.call("mc_exec", {"command": "/csbox errors"})
    if chat_has("fct_bad", 8):
        tally.ok("/csbox errors 检出错误箱子", "列出 fct_bad")
    else:
        tally.bad("/csbox errors 检出错误箱子", "未列出坏文件")

    # 清理现场
    for f in (RELOAD_BOX, bad_file):
        remove_box_config(version, f)
    time.sleep(0.5)
    c.call("mc_exec", {"command": "/csbox reload"})
