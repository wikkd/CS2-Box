#!/usr/bin/env python3
"""Merge bulk-open optimizations into every platform module.

The platform modules are NOT pure copies (v1_21_3+/v1_21_11/v26.x carry API
adaptations: getItem(i)/getItemBySlot vs getInventory().items, Identifier vs
ResourceLocation, getNonEquipmentItems). This script applies the same logical
change with each module's own inventory-access style:

For PacketCsgoBulkProgress.java:
  1. Replace countMatchingBoxes + countMatchingKeys (+ its ItemStack overload)
     with one countAvailability() that walks the inventory once and returns an
     Availability(boxes, keys) record.
  2. handleServer call site -> countAvailability(player, templateBox, getKey(...)).
  3. finalizeBulkOpen call site -> countAvailability(sp, templateBox, keyId).
  4. GradeMap.build(itemList, ...) -> GradeMapCache.get(boxId.toString(), ...).
  5. import com.reclizer.csgobox.logic.GradeMapCache;

For BoxRegistry.java:
  6. invalidate the cache on register/clear/remove so a /csbox reload can
     never serve a stale item pool.

Usage: python3 scripts/merge-bulk-optimize.py [--check]
  --check  only report which files would change / already have the change.
"""
import os
import sys

CHECK = "--check" in sys.argv

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

MODULES = [
    "v1_21_0", "v1_21_1", "v1_21_3", "v1_21_4", "v1_21_5", "v1_21_8",
    "v1_21_10", "v1_21_11", "v26_1_2", "v26_2",
]

GRADE_MAP_CACHE_IMPORT = "import com.reclizer.csgobox.logic.GradeMapCache;\n"


def src_path(module, *parts):
    return os.path.join(REPO, module, "src", "main", "java",
                        "com", "reclizer", "csgobox", module, *parts)


def rl_type(content):
    return "Identifier" if "net.minecraft.resources.Identifier" in content else "ResourceLocation"


def style_of(content):
    if "getNonEquipmentItems" in content:
        return "c"
    if "player.getInventory().getItem(" in content:
        return "b"
    return "a"


COUNT_METHOD_TMPL = {
    # Legacy inventory list access (v1_21_0/1/3).
    "a": """
    private record Availability(int boxes, int keys) {
    }

    private static Availability countAvailability(Player player, ItemStack box, __RL__ keyId) {
        boolean noKey = keyId == null || keyId.equals(__RL__.parse("minecraft:air"));
        boolean countKeys = !noKey && !player.getAbilities().instabuild;
        int boxes = 0;
        int keys = countKeys ? 0 : Integer.MAX_VALUE;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof ItemCsgoBox) {
                if (ItemStack.isSameItemSameComponents(stack, box)) {
                    boxes += stack.getCount();
                }
            } else if (countKeys && keyId.equals(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()))) {
                keys += stack.getCount();
            }
        }
        for (ItemStack stack : player.getInventory().armor) {
            if (stack.getItem() instanceof ItemCsgoBox) {
                if (ItemStack.isSameItemSameComponents(stack, box)) {
                    boxes += stack.getCount();
                }
            } else if (countKeys && keyId.equals(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()))) {
                keys += stack.getCount();
            }
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (stack.getItem() instanceof ItemCsgoBox) {
                if (ItemStack.isSameItemSameComponents(stack, box)) {
                    boxes += stack.getCount();
                }
            } else if (countKeys && keyId.equals(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()))) {
                keys += stack.getCount();
            }
        }
        return new Availability(boxes, keys);
    }
""",
    # Slot-index access (v1_21_4/5/8/10/11): getItem(i) + getItemBySlot.
    "b": """
    private record Availability(int boxes, int keys) {
    }

    private static Availability countAvailability(Player player, ItemStack box, __RL__ keyId) {
        boolean noKey = keyId == null || keyId.equals(__RL__.parse("minecraft:air"));
        boolean countKeys = !noKey && !player.getAbilities().instabuild;
        int boxes = 0;
        int keys = countKeys ? 0 : Integer.MAX_VALUE;
        // Main inventory: slots 0-35
        for (int i = 0; i < 36; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() instanceof ItemCsgoBox) {
                if (ItemStack.isSameItemSameComponents(stack, box)) {
                    boxes += stack.getCount();
                }
            } else if (countKeys && keyId.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))) {
                keys += stack.getCount();
            }
        }
        // Armor
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.getItem() instanceof ItemCsgoBox) {
                if (ItemStack.isSameItemSameComponents(stack, box)) {
                    boxes += stack.getCount();
                }
            } else if (countKeys && keyId.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))) {
                keys += stack.getCount();
            }
        }
        // Offhand
        ItemStack offhand = player.getItemBySlot(EquipmentSlot.OFFHAND);
        if (offhand.getItem() instanceof ItemCsgoBox) {
            if (ItemStack.isSameItemSameComponents(offhand, box)) {
                boxes += offhand.getCount();
            }
        } else if (countKeys && keyId.equals(BuiltInRegistries.ITEM.getKey(offhand.getItem()))) {
            keys += offhand.getCount();
        }
        return new Availability(boxes, keys);
    }
""",
    # Decoupled equipment access (v26_1_2/v26_2).
    "c": """
    private record Availability(int boxes, int keys) {
    }

    private static Availability countAvailability(Player player, ItemStack box, Identifier keyId) {
        boolean noKey = keyId == null || keyId.equals(Identifier.parse("minecraft:air"));
        boolean countKeys = !noKey && !player.getAbilities().instabuild;
        int boxes = 0;
        int keys = countKeys ? 0 : Integer.MAX_VALUE;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (stack.getItem() instanceof ItemCsgoBox) {
                if (ItemStack.isSameItemSameComponents(stack, box)) {
                    boxes += stack.getCount();
                }
            } else if (countKeys && keyId.equals(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()))) {
                keys += stack.getCount();
            }
        }
        // Armor + offhand as well so the consume step below does not pull
        // more than the player actually has (or vice versa).
        for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
                EquipmentSlot.FEET, EquipmentSlot.OFFHAND}) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.getItem() instanceof ItemCsgoBox) {
                if (ItemStack.isSameItemSameComponents(stack, box)) {
                    boxes += stack.getCount();
                }
            } else if (countKeys && keyId.equals(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()))) {
                keys += stack.getCount();
            }
        }
        return new Availability(boxes, keys);
    }
""",
}

CALL_1_OLD = (
    "            int availableBoxes = countMatchingBoxes(player, templateBox);\n"
    "            int availableKeys = countMatchingKeys(player, templateBox);\n"
)
CALL_1_NEW = (
    "            Availability avail = countAvailability(player, templateBox, ItemCsgoBox.getKey(templateBox));\n"
    "            int availableBoxes = avail.boxes();\n"
    "            int availableKeys = avail.keys();\n"
)
CALL_2_OLD_C = (
    "        int recheckBoxes = countMatchingBoxes(sp, templateBox);\n"
    "        int recheckKeys = countMatchingKeys(sp, templateBox);\n"
)
CALL_2_NEW_C = (
    "        Identifier keyId = ItemCsgoBox.getKey(templateBox);\n"
    "        Availability avail = countAvailability(sp, templateBox, keyId);\n"
    "        int recheckBoxes = avail.boxes();\n"
    "        int recheckKeys = avail.keys();\n"
)
CALL_2_OLD_AB = (
    "        int recheckBoxes = countMatchingBoxes(sp, templateBox);\n"
    "        int recheckKeys = countMatchingKeys(sp, keyId);\n"
)
CALL_2_NEW_AB = (
    "        Availability avail = countAvailability(sp, templateBox, keyId);\n"
    "        int recheckBoxes = avail.boxes();\n"
    "        int recheckKeys = avail.keys();\n"
)

SNAPSHOT_OLD = "GradeMap.build(itemList, stack -> !stack.isEmpty(), ItemStack::copy)"
SNAPSHOT_NEW = "GradeMapCache.get(boxId.toString(), () -> GradeMap.build(itemList, stack -> !stack.isEmpty(), ItemStack::copy))"


def apply_bulk_edits(path):
    with open(path, encoding="utf-8") as f:
        content = f.read()
    if "countAvailability" in content:
        return False, "already applied"
    orig = content

    # Insert the common import right after the module's own CsgoBox import.
    pkg_line = "import com.reclizer.csgobox.{pkg}.CsgoBox;".format(pkg=_pkg_of(path))
    if pkg_line in content:
        content = content.replace(pkg_line, pkg_line + "\n" + GRADE_MAP_CACHE_IMPORT.rstrip("\n"), 1)
    else:
        return False, "import anchor not found"

    rl = rl_type(content)
    style = style_of(content)
    new_method = COUNT_METHOD_TMPL[style].replace("__RL__", rl)

    # 1. Replace the count-methods block, up to the computeKResults javadoc.
    start = content.index("    private static int countMatching")
    javadoc_anchor = "    /**\n     * Pure-Java RNG loop."
    javadoc_start = content.index(javadoc_anchor, start)
    content = content[:start] + new_method + "\n" + content[javadoc_start:]

    # 2. handleServer call site.
    if CALL_1_OLD not in content:
        return False, "call site 1 not found"
    content = content.replace(CALL_1_OLD, CALL_1_NEW, 1)

    # 3. finalizeBulkOpen call site (variant C first, then A/B).
    if CALL_2_OLD_C in content:
        content = content.replace(CALL_2_OLD_C, CALL_2_NEW_C, 1)
    elif CALL_2_OLD_AB in content:
        content = content.replace(CALL_2_OLD_AB, CALL_2_NEW_AB, 1)
    else:
        return False, "call site 2 not found"

    # 4. Cached snapshot.
    if SNAPSHOT_OLD not in content:
        return False, "snapshot line not found"
    content = content.replace(SNAPSHOT_OLD, SNAPSHOT_NEW, 1)

    if CHECK:
        return content != orig, "pending"
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)
    return True, "applied"


def _pkg_of(path):
    return os.path.basename(os.path.dirname(os.path.dirname(path)))


def apply_box_registry_edits(path):
    with open(path, encoding="utf-8") as f:
        content = f.read()
    if "GradeMapCache" in content:
        return False, "already applied"
    orig = content

    pkg = _pkg_of(path)
    anchor = "import com.reclizer.csgobox.{pkg}.CsgoBox;\n".format(pkg=pkg)
    if anchor in content:
        content = content.replace(anchor, anchor + GRADE_MAP_CACHE_IMPORT, 1)
    else:
        return False, "import anchor not found"

    for old, new, key in (
        ("        BOX_REGISTRY.put(definition.id(), definition);\n",
         "        BOX_REGISTRY.put(definition.id(), definition);\n"
         "        GradeMapCache.invalidate(definition.id().toString());\n", "register"),
        ("        BOX_REGISTRY.clear();\n",
         "        BOX_REGISTRY.clear();\n"
         "        GradeMapCache.invalidateAll();\n", "clear"),
        ("        BOX_REGISTRY.remove(id);\n",
         "        BOX_REGISTRY.remove(id);\n"
         "        GradeMapCache.invalidate(id.toString());\n", "remove"),
    ):
        if old not in content:
            return False, "anchor not found: " + key
        content = content.replace(old, new, 1)

    if CHECK:
        return content != orig, "pending"
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)
    return True, "applied"


def main():
    changed = 0
    for m in MODULES:
        jobs = [
            (src_path(m, "packet", "PacketCsgoBulkProgress.java"), apply_bulk_edits),
            (src_path(m, "box", "BoxRegistry.java"), apply_box_registry_edits),
        ]
        for path, fn in jobs:
            if not os.path.exists(path):
                print(f"SKIP   {path} (missing)")
                continue
            status, note = fn(path)
            if status:
                changed += 1
            tag = "CHECK" if CHECK else ("CHANGE" if status else "SAME  ")
            print(f"{tag} {note:<18} {os.path.relpath(path, REPO)}")
    print("pending files: %d" % changed if CHECK else "files changed: %d" % changed)


if __name__ == "__main__":
    main()