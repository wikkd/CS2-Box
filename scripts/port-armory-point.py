#!/usr/bin/env python3
"""定点合入 armory_point 注册到非基准平台（幂等，精确匹配，匹配失败即报错退出）。

EOL 平台（v1_21_0/3/4/5/8/10/11）已于 2026-08-09 归档，仅剩 v26_2。
"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

REGISTER_LINES = {
    # registerItem 模式（同 v26_1_2）
    "v26_2": '    public static final Supplier<Item> ITEM_ARMORY_POINT = ITEMS.registerItem("armory_point", p -> new Item(p.rarity(Rarity.COMMON)), p -> p);',
}

TAB_LINE = "                entries.accept(ModItems.ITEM_ARMORY_POINT.get());"
IMPORT_LINE = "import net.minecraft.world.item.Rarity;"

for platform, register_line in REGISTER_LINES.items():
    path = ROOT / platform / "src/main/java" / "com" / "reclizer" / "csgobox" / platform / "item" / "ModItems.java"
    assert path.exists(), f"missing {path}"
    src = path.read_text(encoding="utf-8")
    orig = src
    if "ITEM_ARMORY_POINT" in src:
        print(f"[skip] {platform}: already patched")
        continue
    key_line = next(l for l in src.splitlines() if "ITEM_CSGO_KEY3 = ITEMS.register" in l or "ITEM_CSGO_KEY3 = ITEMS.registerItem" in l)
    src = src.replace(key_line, key_line + "\n" + register_line, 1)
    tab_key = next(l for l in src.splitlines() if "entries.accept(ModItems.ITEM_CSGO_KEY3.get())" in l)
    src = src.replace(tab_key, tab_key + "\n" + TAB_LINE, 1)
    if "import net.minecraft.world.item.Rarity;" not in src:
        import_block, sep, rest = src.partition("import net.minecraft.world.item.ItemStack;")
        src = import_block + sep + "\n" + "import net.minecraft.world.item.Rarity;" + rest
    path.write_text(src + "" if src.endswith("\n") else src + "\n", encoding="utf-8")
    print(f"[ok] {platform}")