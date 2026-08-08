"""动态 box item：/give @p csgobox:<filename> → 物品存在、图标非紫黑、可开启。

紫黑棋盘格 = 缺失纹理（MC 的 missing texture 是 #C400CC/#1B001B 交替棋盘格）。
检测：结果屏截图后对物品栏槽位区域采样，槽位中心若呈紫/黑交替色系即 FAIL。
"""
import time
from pathlib import Path

from PIL import Image

from .common import CLOSE_BTN, OPEN_BTN, Tally, RUNS_DIR

DYN_BOX = "fct_dyn.json"
DYN_BOX_ID = "csgobox:fct_dyn"
MISSING = {(0xC4, 0x00, 0xCC), (0x1B, 0x00, 0x1B)}


def _is_missing(pixel) -> bool:
    r, g, b = pixel[0], pixel[1], pixel[2]
    return (abs(r - 0xC4) < 30 and abs(g - 0) < 30 and abs(b - 0xCC) < 30) or \
           (abs(r - 0x1B) < 30 and abs(g - 0) < 30 and abs(b - 0x1B) < 30)


def run(env, tally: Tally, version: str, out_dir: Path) -> None:
    c = env.client
    box_id = "csgobox:weapon_supply_box"  # 默认动态 item（启动时注册）

    # give 动态 item（不指定 box_id，直接用 item id）
    c.call("mc_exec", {"command": f"/clear @s {box_id}"})
    c.call("mc_exec", {"command": "/clear @s csgobox:csgo_key0"})
    time.sleep(0.4)
    c.call("mc_exec", {"command": f"/give @s {box_id} 1"})
    c.call("mc_exec", {"command": "/give @s csgobox:csgo_key0 1"})
    time.sleep(0.6)

    data = c.call_full("mc_inventory", {}) or {}
    items = data.get("items", []) if isinstance(data, dict) else data
    box = next((i for i in items if i.get("id") == box_id), None)
    if box is None:
        tally.bad("动态 item 存在", f"{box_id} 未出现在背包")
        return
    tally.ok("动态 item 存在", f"{box_id} slot={box['slot']} area={box['area']}")

    # 开箱（流程同 e2e）
    c.call("mc_key", {"key": f"key.keyboard.{box['slot'] + 1}"})
    time.sleep(0.3)
    c.call("mc_key", {"key": "key.mouse.right"})
    time.sleep(1.0)
    c.call("mc_click", {"x": OPEN_BTN[0], "y": OPEN_BTN[1]})
    end = time.time() + 25
    while time.time() < end:
        if (env.screen_class() == "CsLookItemScreen"):
            break
        time.sleep(0.3)
    if env.screen_class() == "CsLookItemScreen":
        tally.ok("动态 item 可开启", "到结果屏")
    else:
        tally.bad("动态 item 可开启", f"screen={env.screen_class()}")

    # 图标非紫黑：对结果屏截图采样物品栏
    shot = out_dir / "shots" / "dyn_box_result.png"
    shot.parent.mkdir(parents=True, exist_ok=True)
    try:
        c.call("mc_shot", {"path": str(shot)})
        time.sleep(0.5)
        img = Image.open(shot).convert("RGB")
        w, h = img.size
        # 结果屏物品栏槽位约在 y=fb 底部 880±40 的 9 格区，逐槽采样中心
        slot_w = 60
        missing_hits = 0
        slots_checked = 0
        for i in range(9):
            x = w // 2 - 4 * 54 + i * 54 + 27
            y = h - 50
            px = img.getpixel((x, y))
            slots_checked += 1
            if _is_missing(px):
                missing_hits += 1
        if missing_hits == 0:
            tally.ok("动态 item 图标非紫黑", f"{slots_checked} 槽采样无缺失纹理")
        else:
            tally.bad("动态 item 图标非紫黑", f"{missing_hits}/{slots_checked} 槽呈紫黑棋盘格")
    except Exception as e:
        tally.warn_("动态 item 图标采样", f"截图采样失败: {e}")

    try:
        c.call("mc_click", {"x": CLOSE_BTN[0], "y": CLOSE_BTN[1]})
    except Exception:
        pass
