"""磨损耐久：damageItemByWear=true 时开出的有耐久物品按磨损值扣耐久。

断言依据 mc_inventory 增强后的 damage/max_damage 字段：
- 有耐久物品开出 → damage ∈ [1, max-1]
- 无耐久物品（如石头）→ 无 damage 字段（不扣）
"""
import json
import time
from pathlib import Path

from .common import CLOSE_BTN, OPEN_BTN, Tally, RUNS_DIR

WEAR_BOX = "fct_wear.json"
WEAR_BOX_ID = "csgobox:fct_wear"
WEAR_BOX_JSON = {
    "name": "磨损测试箱",
    "key": "csgobox:csgo_key0",
    "drop": 1.0,
    "random": [1, 1, 1, 1, 1],
    "grade1": [{"id": "minecraft:diamond_sword", "count": 1}],
    "grade2": [{"id": "minecraft:stone", "count": 1}],
}


def _open_once(env, tally: Tally, name: str, want_id: str,
               expect_damage: bool, max_tries: int = 1) -> bool:
    """开箱并断言 want_id 的 damage 表现。开出目标条目返回 True。"""
    for _ in range(max_tries):
        c = env.client
        c.call("mc_exec", {"command": "/clear @s csgobox:csgo_box"})
        c.call("mc_exec", {"command": "/clear @s csgobox:csgo_key0"})
        time.sleep(0.4)
        c.call("mc_exec", {"command":
            f"/give @s csgobox:csgo_box[csgobox:box_id=\"{WEAR_BOX_ID}\"] 1"})
        c.call("mc_exec", {"command": "/give @s csgobox:csgo_key0 1"})
        time.sleep(0.6)
        data = c.call_full("mc_inventory", {}) or {}
        items = data.get("items", []) if isinstance(data, dict) else data
        box = next((i for i in items if i.get("area") == "hotbar"
                    and i.get("id") == "csgobox:csgo_box"), None)
        if not box:
            tally.bad(name + " 前置", "箱子未出现在 hotbar")
            return False
        c.call("mc_key", {"key": f"key.keyboard.{box['slot'] + 1}"})
        time.sleep(0.3)
        c.call("mc_key", {"key": "key.mouse.right"})
        time.sleep(1.0)
        c.call("mc_click", {"x": OPEN_BTN[0], "y": OPEN_BTN[1]})
        time.sleep(6.0)  # 动画 ~2.5s + 缓冲

        data = c.call_full("mc_inventory", {}) or {}
        items = data.get("items", []) if isinstance(data, dict) else data
        got = next((i for i in items if i.get("id") == want_id), None)
        if got is None:
            continue  # 本次开出其他条目，重试
        if expect_damage:
            dmg, max_dmg = got.get("damage"), got.get("max_damage")
            if isinstance(dmg, int) and isinstance(max_dmg, int) and 1 <= dmg < max_dmg:
                tally.ok(name, f"{want_id} damage={dmg}/{max_dmg} "
                               f"(wear={dmg / max_dmg:.3f})")
            else:
                tally.bad(name, f"{want_id} damage={dmg} max={max_dmg} 期望 (0, max)")
        else:
            if "damage" not in got:
                tally.ok(name, f"{want_id} 无 damage 字段（不扣耐久）")
            else:
                tally.bad(name, f"{want_id} 出现 damage={got.get('damage')}（不应扣损）")
        try:
            c.call("mc_click", {"x": CLOSE_BTN[0], "y": CLOSE_BTN[1]})
        except Exception:
            pass
        return True
    tally.warn_(name, f"{want_id} 在 {max_tries} 次开箱内未开出")
    return False


def run(env, tally: Tally, version: str, out_dir: Path) -> None:
    c = env.client
    csbox_dir = RUNS_DIR(version) / "config" / "csbox"

    (csbox_dir / WEAR_BOX).write_text(
        json.dumps(WEAR_BOX_JSON, ensure_ascii=False, indent=2), encoding="utf-8")
    time.sleep(0.5)
    c.call("mc_exec", {"command": "/csbox reload"})
    time.sleep(1.0)

    # 权重均等（避免 Schema 错误），多次开箱直到剑/石都验证过
    for _ in range(6):
        if _open_once(env, tally, "磨损 有耐久物品扣损",
                      "minecraft:diamond_sword", True, max_tries=6):
            break
    for _ in range(6):
        if _open_once(env, tally, "磨损 无耐久物品不扣",
                      "minecraft:stone", False, max_tries=6):
            break

    # 清理现场
    (csbox_dir / WEAR_BOX).unlink(missing_ok=True)
    time.sleep(0.5)
    c.call("mc_exec", {"command": "/csbox reload"})
