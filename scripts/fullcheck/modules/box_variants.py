"""箱子 JSON 变体 + 错误自检（用户重点模块）。

合法变体：不同 key / drop / random 权重 / 有无 entity / grade 缺失 / 单条目。
错误箱子自检（以模组实际行为为准）：
- 语法错误 → 阻塞加载 + /csbox errors 列出
- 负权重 → schema 记录 + 默认权重 fallback（不阻塞）
- 不存在的 item id → Item 错误记录 + 该条跳过（部分加载）
- 全条目非法 → 箱不加载 + errors 记录
- 缺 name/key → fallback 生效（无错误记录），箱仍可用
"""
import json
import time
from pathlib import Path

from .common import (CLOSE_BTN, OPEN_BTN, Tally, RUNS_DIR,
                   write_box_config, remove_box_config)

VARIANTS = [
    ("fct_key1.json", {"name": "钥匙1箱", "key": "csgobox:csgo_key1", "drop": 1.0,
                       "random": [100], "grade1": [{"id": "minecraft:stick", "count": 1}]},
     "csgobox:csgo_key1"),
    ("fct_key2.json", {"name": "钥匙2箱", "key": "csgobox:csgo_key2", "drop": 1.0,
                       "random": [100], "grade1": [{"id": "minecraft:stick", "count": 1}]},
     "csgobox:csgo_key2"),
    ("fct_drop05.json", {"name": "低掉率箱", "key": "csgobox:csgo_key0", "drop": 0.5,
                         "random": [100], "grade1": [{"id": "minecraft:stick", "count": 1}]},
     "csgobox:csgo_key0"),
    ("fct_drop20.json", {"name": "高掉率箱", "key": "csgobox:csgo_key0", "drop": 2.0,
                         "random": [100], "grade1": [{"id": "minecraft:stick", "count": 1}]},
     "csgobox:csgo_key0"),
    ("fct_weights.json", {"name": "权重箱", "key": "csgobox:csgo_key0", "drop": 1.0,
                          "random": [1, 2, 3, 4, 5],
                          "grade1": [{"id": "minecraft:stick", "count": 1}],
                          "grade2": [{"id": "minecraft:stick", "count": 1}]},
     "csgobox:csgo_key0"),
    ("fct_noentity.json", {"name": "无实体箱", "key": "csgobox:csgo_key0", "drop": 1.0,
                           "random": [100], "grade1": [{"id": "minecraft:stick", "count": 1}]},
     "csgobox:csgo_key0"),
    ("fct_grades13.json", {"name": "少等级箱", "key": "csgobox:csgo_key0", "drop": 1.0,
                           "random": [100],
                           "grade1": [{"id": "minecraft:stick", "count": 1}],
                           "grade2": [{"id": "minecraft:stick", "count": 1}],
                           "grade3": [{"id": "minecraft:stick", "count": 1}]},
     "csgobox:csgo_key0"),
    ("fct_single.json", {"name": "单条目箱", "key": "csgobox:csgo_key0", "drop": 1.0,
                         "random": [100],
                         "grade1": [{"id": "minecraft:stick", "count": 1}],
                         "grade2": [{"id": "minecraft:stick", "count": 1}],
                         "grade3": [{"id": "minecraft:stick", "count": 1}],
                         "grade4": [{"id": "minecraft:stick", "count": 1}],
                         "grade5": [{"id": "minecraft:stick", "count": 1}]},
     "csgobox:csgo_key0"),
]

# (文件名, json 内容或文本, 期望断言)
BAD_VARIANTS = [
    ("fct_bad_syntax.json", "{this is not json", "errors 列出该文件"),
    ("fct_bad_random.json",
     {"name": "负权重箱", "key": "csgobox:csgo_key0", "random": [-1, 5, 5, 5, 5],
      "grade1": [{"id": "minecraft:stick", "count": 1}]},
     "errors 记录 Random[1]"),
    ("fct_bad_item.json",
     {"name": "混合条目箱", "key": "csgobox:csgo_key0", "drop": 1.0,
      "random": [100],
      "grade1": [{"id": "minecraft:stick", "count": 1},
                 {"id": "minecraft:not_a_real_item", "count": 1}]},
     "errors 记录 Item 错误且箱仍可开"),
    ("fct_all_bad.json",
     {"name": "全坏条目箱", "key": "csgobox:csgo_key0", "drop": 1.0,
      "random": [100],
      "grade1": [{"id": "minecraft:not_a_real_item", "count": 1}]},
     "errors 记录 all-items-failed 且箱不加载"),
    ("fct_nofield.json",
     {"drop": 1.0, "random": [1, 1, 1, 1, 1],
      "grade1": [{"id": "minecraft:stick", "count": 1}]},
     "缺 name/key 时 fallback 生效且可开（errors 无记录）"),
]


def _chat_has(c, keyword: str, timeout: float = 6) -> bool:
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


def _open_flow(env, box_id: str, key_id: str, timeout: float = 25) -> bool:
    """give 通用箱(csgo_box+box_id) + 钥匙 → 右键 → 开启 → 等结果屏。

    动态 item 仅启动时注册，新箱子 JSON 用 box_id 组件指定。
    """
    c = env.client
    c.call("mc_exec", {"command": "/clear @s csgobox:csgo_box"})
    c.call("mc_exec", {"command": f"/clear @s {key_id}"})
    time.sleep(0.4)
    c.call("mc_exec", {"command":
        f"/give @s csgobox:csgo_box[csgobox:box_id=\"{box_id}\"] 1"})
    c.call("mc_exec", {"command": f"/give @s {key_id} 1"})
    time.sleep(0.6)
    data = c.call_full("mc_inventory", {}) or {}
    items = data.get("items", []) if isinstance(data, dict) else data
    box = next((i for i in items if i.get("area") == "hotbar"
                and i.get("id") == "csgobox:csgo_box"), None)
    if not box:
        return False
    c.call("mc_key", {"key": f"key.keyboard.{box['slot'] + 1}"})
    time.sleep(0.3)
    c.call("mc_key", {"key": "key.mouse.right"})
    time.sleep(1.0)
    c.call("mc_click", {"x": OPEN_BTN[0], "y": OPEN_BTN[1]})
    end = time.time() + timeout
    while time.time() < end:
        if env.screen_class() == "CsLookItemScreen":
            break
        time.sleep(0.3)
    ok = env.screen_class() == "CsLookItemScreen"
    try:
        c.call("mc_click", {"x": CLOSE_BTN[0], "y": CLOSE_BTN[1]})
        time.sleep(0.8)
    except Exception:
        pass
    return ok


def run(env, tally: Tally, version: str, out_dir: Path) -> None:
    c = env.client
    try:
        _run_inner(env, tally, version, out_dir, c)
    finally:
        # 无论异常与否都清理现场
        for fname, *_ in VARIANTS + [(*b,) for b in BAD_VARIANTS]:
            remove_box_config(version, fname)
        time.sleep(0.5)
        try:
            c.call("mc_exec", {"command": "/csbox reload"})
        except Exception:
            pass


def _run_inner(env, tally: Tally, version: str, out_dir: Path,
               c) -> None:

    # ---- 合法变体 ----
    for fname, box_json, key_id in VARIANTS:
        write_box_config(version, fname,
                         json.dumps(box_json, ensure_ascii=False, indent=2))
        time.sleep(0.4)
        c.call("mc_exec", {"command": "/csbox reload"})
        time.sleep(1.0)
        box_id = "csgobox:" + fname[:-5]
        if _open_flow(env, box_id, key_id):
            tally.ok(f"变体 {fname} 可开", f"key={key_id.split(':')[-1]}")
        else:
            tally.bad(f"变体 {fname} 可开", "未到结果屏")

    # ---- 错误自检 ----
    for fname, payload, expect in BAD_VARIANTS:
        if isinstance(payload, str):
            write_box_config(version, fname, payload)
        else:
            write_box_config(version, fname,
                             json.dumps(payload, ensure_ascii=False, indent=2))
        time.sleep(0.4)
        c.call("mc_exec", {"command": "/csbox reload"})
        time.sleep(1.0)
        c.call("mc_exec", {"command": "/csbox errors"})
        time.sleep(0.5)
        listed = _chat_has(c, fname[:-5], 6)
        if "errors 列出" in expect:
            if listed:
                tally.ok(f"错误自检 {fname}", expect)
            else:
                tally.bad(f"错误自检 {fname}", "errors 未列出该文件")
        elif "fallback" in expect:
            if listed:
                tally.bad(f"错误自检 {fname}", "缺 name/key 不应产生错误记录")
            else:
                box_id = "csgobox:" + fname[:-5]
                if _open_flow(env, box_id, "csgobox:csgo_key0"):
                    tally.ok(f"错误自检 {fname}", expect)
                else:
                    tally.bad(f"错误自检 {fname}", "fallback 箱不可开")
        elif "不加载" in expect:
            if listed:
                tally.ok(f"错误自检 {fname}", "errors 列出该文件")
                if _open_flow(env, "csgobox:" + fname[:-5], "csgobox:csgo_key0"):
                    tally.bad(f"错误自检 {fname}", "全坏条目箱不应可开")
                else:
                    tally.ok(f"错误自检 {fname}", "全坏条目箱不加载")
            else:
                tally.bad(f"错误自检 {fname}", "errors 未列出该文件")
        else:  # 混合条目：errors 有记录且箱仍可开
            if listed:
                tally.ok(f"错误自检 {fname}", "errors 列出该文件")
            else:
                tally.bad(f"错误自检 {fname}", "errors 未列出该文件")
            if _open_flow(env, "csgobox:" + fname[:-5], "csgobox:csgo_key0"):
                tally.ok(f"错误自检 {fname}", "部分条目加载后可开")
            else:
                tally.bad(f"错误自检 {fname}", "混合条目箱不可开")

    # ---- 现场恢复 ----
    for fname, *_ in VARIANTS + [(*b,) for b in BAD_VARIANTS]:
        remove_box_config(version, fname)
    time.sleep(0.5)
    c.call("mc_exec", {"command": "/csbox reload"})
    time.sleep(1.0)
    if _open_flow(env, env.box_id, env.key_id):
        tally.ok("现场恢复 默认箱可用", "")
    else:
        tally.bad("现场恢复 默认箱可用", "默认箱不可开")
