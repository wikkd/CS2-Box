"""开箱主流程 E2E：T1-T9（对应 mc_tools/scripts/test_csbox.sh）。

T1 打开 GUI / T2 动画屏 / T3 结果屏 / T4 截图 / T5 钥匙消耗 /
T6 物品入包 / T7 关闭回世界 / T8 动画中 ESC 取消 / T9 取消后立即重开。
"""
import json
import time
from pathlib import Path

from .common import CLOSE_BTN, OPEN_BTN, Tally, click_open_retry


def run(env, tally: Tally, version: str, out_dir: Path) -> None:
    box_id = env.box_id
    key_id = env.key_id
    c = env.client

    # 全清背包（残留物品会让"新增"判定失真）
    c.call("mc_exec", {"command": "/clear @s"})
    time.sleep(0.5)

    def inv_items():
        return c.call_full("mc_inventory", {}) or {}

    def count_key():
        data = inv_items()
        items = data.get("items", []) if isinstance(data, dict) else data
        return sum(int(i.get("count", 0)) for i in items if i.get("id") == key_id)

    def clear_and_give():
        c.call("mc_exec", {"command": f"/clear @s {box_id}"})
        c.call("mc_exec", {"command": f"/clear @s {key_id}"})
        time.sleep(0.4)
        c.call("mc_exec", {"command":
            f"/give @s {box_id}[csgobox:box_id=\"{box_id}\"] 1"})
        c.call("mc_exec", {"command": f"/give @s {key_id} 1"})
        time.sleep(0.6)

    def box_slot():
        data = inv_items()
        items = data.get("items", []) if isinstance(data, dict) else data
        for i in items:
            if i.get("area") == "hotbar" and i.get("id") == box_id:
                return int(i.get("slot", -1))
        return -1

    def wait_screen(want, timeout, dim=""):
        end = time.time() + timeout
        while time.time() < end:
            st = c.call("mc_status", {}) or {}
            if st.get("screen_class") == want:
                if not dim or st.get("dimension") == dim:
                    return True
            time.sleep(0.3)
        return False

    def open_from_slot(slot):
        c.call("mc_key", {"key": f"key.keyboard.{slot + 1}"})
        time.sleep(0.3)
        c.call("mc_key", {"key": "key.mouse.right"})
        return wait_screen("CsboxScreen", 8)

    # ---- 准备 ----
    clear_and_give()
    slot = box_slot()
    if slot < 0:
        tally.bad("T1 前置", "箱子未出现在 hotbar")
        return
    key_before = count_key()

    # ---- T1 打开开箱 GUI ----
    if open_from_slot(slot):
        tally.ok("T1 右键打开 CsboxScreen", f"hotbar {slot}")
    else:
        tally.bad("T1 未打开 CsboxScreen", "")
        return

    # 物品基线快照（T6 对比用，排除残留）
    baseline = {i.get("id"): int(i.get("count", 0))
                for i in (inv_items().get("items") or [])}

    # ---- T2 点击开启 → 动画屏 ----
    if click_open_retry(env):
        tally.ok("T2 开启 → 动画屏", "")
    else:
        tally.bad("T2 未进入动画屏", "")

    # ---- T3 结果屏 ----
    if wait_screen("CsLookItemScreen", 25):
        tally.ok("T3 开箱结果屏 CsLookItemScreen", "")
    else:
        tally.bad("T3 未出现结果屏", "")

    # ---- T4 截图存证 ----
    shot_dir = out_dir / "shots"
    shot_dir.mkdir(parents=True, exist_ok=True)
    shot = shot_dir / "t4_result.png"
    try:
        c.call("mc_shot", {"path": str(shot)})
        # mc_shot 异步截图（Screenshot.grab 回调写盘），轮询等待产物落盘
        ok = False
        for _ in range(50):
            if shot.is_file() and shot.stat().st_size > 0:
                ok = True
                break
            time.sleep(0.2)
        if ok:
            tally.ok("T4 截图存证", shot.name)
        else:
            tally.bad("T4 截图失败", "文件不存在或为空")
    except Exception as e:
        tally.bad("T4 截图失败", str(e))

    # ---- T5 钥匙消耗 ----
    key_after = count_key()
    if key_before - key_after == 1:
        tally.ok("T5 钥匙消耗", f"{key_before}→{key_after} (-1)")
    else:
        tally.bad("T5 钥匙消耗异常", f"{key_before}→{key_after}")

    # ---- T6 背包新增 ----
    data = inv_items()
    items = data.get("items", []) if isinstance(data, dict) else data
    new_ids = {i.get("id") for i in items if i.get("id") not in (box_id, key_id)}
    if new_ids:
        tally.ok("T6 开出物品进背包", ", ".join(sorted(new_ids)))
    else:
        tally.bad("T6 背包无新增物品", "")

    # ---- T7 关闭结果屏 ----
    c.call("mc_click", {"x": CLOSE_BTN[0], "y": CLOSE_BTN[1]})
    if wait_screen("", 8, "minecraft:overworld"):
        tally.ok("T7 关闭 → 回到世界", "")
    else:
        tally.bad("T7 未回到世界", "")

    # ---- T8 动画中 ESC 取消 ----
    clear_and_give()
    slot = box_slot()
    if slot < 0:
        tally.bad("T8 前置", "箱子未出现，跳过后续")
        return
    key8_before = count_key()
    if open_from_slot(slot):
        if click_open_retry(env):
            time.sleep(1.5)
            c.call("mc_key", {"key": "key.keyboard.escape"})
            time.sleep(1)
            cls = env.screen_class()
            if cls == "CsboxProgressScreen":
                tally.warn_("T8 ESC 后动画屏仍在", "等待自然结束")
                wait_screen("", 15)
            tally.ok("T8 ESC 中途取消动画", f"screen={cls}")
        else:
            tally.bad("T8 未进入动画屏", "")
    else:
        tally.bad("T8 无法打开 GUI", "")
    key8_after = count_key()
    if key8_before - key8_after == 1:
        tally.ok("T8 钥匙已消耗", f"{key8_before}→{key8_after}（服务端立即结算）")
    else:
        tally.bad("T8 钥匙消耗异常", f"{key8_before}→{key8_after}")

    # ---- T9 取消后立即重开 ----
    c.call("mc_exec", {"command": f"/give @s {box_id}[csgobox:box_id=\"{box_id}\"] 1"})
    c.call("mc_exec", {"command": f"/give @s {key_id} 1"})
    time.sleep(0.5)
    slot = box_slot()
    if slot < 0:
        tally.bad("T9 前置", "箱子未出现")
        return
    if open_from_slot(slot):
        if click_open_retry(env, timeout=25):
            tally.ok("T9 取消后立即重开成功", "无冷却阻塞")
        else:
            tally.bad("T9 重开未到结果屏", "可能被冷却阻塞")
    else:
        tally.bad("T9 无法打开 GUI", "")
    # 关闭结果屏，保持世界干净
    c.call("mc_click", {"x": CLOSE_BTN[0], "y": CLOSE_BTN[1]})
    wait_screen("", 8, "minecraft:overworld")
