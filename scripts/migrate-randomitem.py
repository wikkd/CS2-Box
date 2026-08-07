#!/usr/bin/env python3
"""Migrate RandomItem.java usage to common logic classes across platform modules.

Idempotent: safe to re-run. Skips files already migrated.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

MODULES = [
    "v1_21_3", "v1_21_4", "v1_21_5", "v1_21_8",
    "v1_21_10", "v1_21_11", "v26_1_2", "v26_2",
]


def migrate_packet_progress(path: Path, pkg: str):
    """Migrate PacketCsgoProgress.java"""
    src = path.read_text(encoding="utf-8")
    if "com.reclizer.csgobox.logic.OddsCalculator" in src:
        print(f"  [skip] {path.name} already migrated")
        return

    # Replace import
    old_import = f"import {pkg}.utils.RandomItem;"
    new_imports = (
        f"import com.reclizer.csgobox.logic.AnimationStrip;\n"
        f"import com.reclizer.csgobox.logic.GradeMap;\n"
        f"import com.reclizer.csgobox.logic.OddsCalculator;"
    )
    # Insert new imports before the ItemCsgoBox import
    item_import = f"import {pkg}.item.ItemCsgoBox;"
    src = src.replace(
        f"{item_import}\n{old_import}",
        f"{new_imports}\n{item_import}"
    )
    # Fallback: if import order differs
    if old_import in src:
        src = src.replace(old_import, new_imports)

    # Replace gradeMap creation
    src = src.replace(
        "var gradeMap = RandomItem.precomputeGradeMap(itemList);",
        "var gradeMap = GradeMap.build(itemList, stack -> !stack.isEmpty(), ItemStack::copy);"
    )

    # Replace animation loop constants
    src = src.replace(
        "new ArrayList<>(PacketBoxOpenResult.ANIMATION_ITEM_COUNT)",
        "new ArrayList<>(AnimationStrip.ITEM_COUNT)"
    )
    src = src.replace(
        "i < PacketBoxOpenResult.ANIMATION_ITEM_COUNT",
        "i < AnimationStrip.ITEM_COUNT"
    )

    # Replace RandomItem calls in animation loop
    src = src.replace(
        "int grade = RandomItem.randomItemsGrade(rng, weights);",
        "int grade = OddsCalculator.pickGrade(rng, weights);"
    )
    src = src.replace(
        "ItemStack itemStack = RandomItem.randomItemsFromGradeMap(rng, grade, gradeMap);",
        "ItemStack itemStack = gradeMap.pickRandom(rng, grade);"
    )
    src = src.replace(
        "if (itemStack.isEmpty()) {\n                    itemStack = RandomItem.findFallbackFromGradeMap(grade, gradeMap);\n                }",
        "if (itemStack == null) {\n                    itemStack = gradeMap.findFallback(grade);\n                }\n                if (itemStack == null) {\n                    itemStack = ItemStack.EMPTY;\n                }"
    )

    # Replace winning index
    src = src.replace(
        "int winningIndex = randomWinningIndex(animationItems.size());",
        "int winningIndex = AnimationStrip.randomWinningIndex(SECURE_RANDOM, animationItems.size());"
    )
    src = src.replace(
        "winningIndex = RandomItem.clampToValidItem(animationItems, winningIndex);",
        "winningIndex = AnimationStrip.findNearestValid(animationItems, winningIndex, stack -> !stack.isEmpty());"
    )

    # Replace findFallback
    src = src.replace(
        "giveItem = RandomItem.findFallback(1, itemList);\n                if (giveItem.isEmpty()) {",
        "giveItem = GradeMap.build(itemList, stack -> !stack.isEmpty(), ItemStack::copy).findFallback(1);\n                if (giveItem == null) giveItem = ItemStack.EMPTY;\n                if (giveItem.isEmpty()) {"
    )

    # Remove randomWinningIndex method
    pattern = r"\n    private static int randomWinningIndex\(int itemCount\) \{\n        int maxIndex = itemCount - 1;\n        int min = Math\.min\(PacketBoxOpenResult\.MIN_WINNING_INDEX, maxIndex\);\n        int max = Math\.min\(PacketBoxOpenResult\.MAX_WINNING_INDEX, maxIndex\);\n        if \(max <= min\) \{\n            return min;\n        \}\n        return min \+ SECURE_RANDOM\.nextInt\(max - min \+ 1\);\n    \}\n"
    src = re.sub(pattern, "\n", src)

    path.write_text(src, encoding="utf-8")
    print(f"  [done] {path.name}")


def migrate_packet_bulk(path: Path, pkg: str):
    """Migrate PacketCsgoBulkProgress.java"""
    src = path.read_text(encoding="utf-8")
    if "com.reclizer.csgobox.logic.OddsCalculator" in src:
        print(f"  [skip] {path.name} already migrated")
        return

    # Replace import
    old_import = f"import {pkg}.utils.RandomItem;"
    new_imports = (
        f"import com.reclizer.csgobox.logic.AnimationStrip;\n"
        f"import com.reclizer.csgobox.logic.GradeMap;\n"
        f"import com.reclizer.csgobox.logic.OddsCalculator;"
    )
    # Insert before ModItems or ItemCsgoBox import
    mod_items_import = f"import {pkg}.item.ModItems;"
    if mod_items_import in src:
        src = src.replace(
            f"{mod_items_import}\n{old_import}",
            f"{new_imports}\n{mod_items_import}"
        )
    if old_import in src:
        src = src.replace(old_import, new_imports)

    # Replace BulkBoxContext creation
    src = src.replace(
        "RandomItem.precomputeGradeMap(itemList)",
        "GradeMap.build(itemList, stack -> !stack.isEmpty(), ItemStack::copy)"
    )

    # Replace animation loop constants
    src = src.replace(
        "new ArrayList<>(PacketBoxOpenResult.ANIMATION_ITEM_COUNT)",
        "new ArrayList<>(AnimationStrip.ITEM_COUNT)"
    )
    src = src.replace(
        "j < PacketBoxOpenResult.ANIMATION_ITEM_COUNT",
        "j < AnimationStrip.ITEM_COUNT"
    )

    # Replace RandomItem calls
    src = src.replace(
        "int g = RandomItem.randomItemsGrade(rng, snapshot.weights());",
        "int g = OddsCalculator.pickGrade(rng, snapshot.weights());"
    )
    src = src.replace(
        "ItemStack s = RandomItem.randomItemsFromGradeMap(rng, g, snapshot.gradeMap());",
        "ItemStack s = snapshot.gradeMap().pickRandom(rng, g);"
    )
    src = src.replace(
        "if (s.isEmpty()) {\n                        s = RandomItem.findFallbackFromGradeMap(g, snapshot.gradeMap());\n                    }",
        "if (s == null) {\n                        s = snapshot.gradeMap().findFallback(g);\n                    }\n                    if (s == null) {\n                        s = ItemStack.EMPTY;\n                    }"
    )

    # Replace winning index in bulk
    src = src.replace(
        "int winningIndex = randomWinningIndex(rng, animItems.size());",
        "int winningIndex = AnimationStrip.randomWinningIndex(rng, animItems.size());"
    )
    src = src.replace(
        "winningIndex = RandomItem.clampToValidItem(animItems, winningIndex);",
        "winningIndex = AnimationStrip.findNearestValid(animItems, winningIndex, stack -> !stack.isEmpty());"
    )

    # Replace fallback in bulk
    src = src.replace(
        "ItemStack fb = RandomItem.findFallbackFromGradeMap(1, snapshot.gradeMap());\n                    if (!fb.isEmpty()) {",
        "ItemStack fb = snapshot.gradeMap().findFallback(1);\n                    if (fb != null && !fb.isEmpty()) {"
    )

    # Remove randomWinningIndex method (bulk version takes Random param)
    pattern = r"\n    private static int randomWinningIndex\(Random rng, int itemCount\) \{\n        int maxIndex = itemCount - 1;\n        int min = Math\.min\(PacketBoxOpenResult\.MIN_WINNING_INDEX, maxIndex\);\n        int max = Math\.min\(PacketBoxOpenResult\.MAX_WINNING_INDEX, maxIndex\);\n        if \(max <= min\) \{\n            return min;\n        \}\n        return min \+ rng\.nextInt\(max - min \+ 1\);\n    \}\n"
    src = re.sub(pattern, "\n", src)

    path.write_text(src, encoding="utf-8")
    print(f"  [done] {path.name}")


def migrate_bulk_context(path: Path, pkg: str):
    """Migrate BulkBoxContext.java"""
    src = path.read_text(encoding="utf-8")
    if "com.reclizer.csgobox.logic.GradeMap" in src:
        print(f"  [skip] {path.name} already migrated")
        return

    # Determine if uses ResourceLocation or Identifier
    if "import net.minecraft.resources.ResourceLocation;" in src:
        id_import = "import net.minecraft.resources.ResourceLocation;"
        id_type = "ResourceLocation"
    else:
        id_import = "import net.minecraft.resources.Identifier;"
        id_type = "Identifier"

    new_src = f"""package {pkg}.box;

import com.reclizer.csgobox.logic.GradeMap;
{id_import}
import net.minecraft.world.item.ItemStack;

/**
 * Server-side snapshot of the data needed to compute bulk box results off the
 * main thread. Built on the main thread from {{@link BoxDefinition}} + {{@link ItemCsgoBox}}
 * and consumed (read-only) by the {{@code computeKResults}} background task.
 */
public record BulkBoxContext(
        {id_type} boxId,
        int[] weights,
        GradeMap<ItemStack> gradeMap
) {{
    public BulkBoxContext {{
        weights = weights == null ? new int[0] : weights.clone();
        if (gradeMap == null) {{
            gradeMap = new GradeMap<>(null, stack -> !stack.isEmpty(), ItemStack::copy);
        }}
    }}
}}
"""
    path.write_text(new_src, encoding="utf-8")
    print(f"  [done] {path.name}")


def migrate_packet_result(path: Path, pkg: str):
    """Migrate PacketBoxOpenResult.java constants"""
    src = path.read_text(encoding="utf-8")
    if "AnimationStrip.ITEM_COUNT" in src:
        print(f"  [skip] {path.name} already migrated")
        return

    # Add import
    csgobox_import = f"import {pkg}.CsgoBox;"
    src = src.replace(
        csgobox_import,
        f"import com.reclizer.csgobox.logic.AnimationStrip;\n{csgobox_import}"
    )

    # Replace constants
    src = src.replace(
        "public static final int ANIMATION_ITEM_COUNT = 50;",
        "public static final int ANIMATION_ITEM_COUNT = AnimationStrip.ITEM_COUNT;"
    )
    src = src.replace(
        "public static final int MIN_WINNING_INDEX = 35;",
        "public static final int MIN_WINNING_INDEX = AnimationStrip.MIN_WINNING_INDEX;"
    )
    src = src.replace(
        "public static final int MAX_WINNING_INDEX = 44;",
        "public static final int MAX_WINNING_INDEX = AnimationStrip.MAX_WINNING_INDEX;"
    )

    path.write_text(src, encoding="utf-8")
    print(f"  [done] {path.name}")


def delete_random_item(module: str, pkg: str):
    """Delete RandomItem.java"""
    path = ROOT / module / "src/main/java" / pkg.replace(".", "/") / "utils/RandomItem.java"
    if path.exists():
        path.unlink()
        print(f"  [del]  {path.relative_to(ROOT)}")
    else:
        print(f"  [skip] RandomItem.java not found (already deleted?)")


def main():
    for module in MODULES:
        pkg = f"com.reclizer.csgobox.{module}"
        base = ROOT / module / "src/main/java" / pkg.replace(".", "/")
        print(f"\n=== {module} ===")

        progress = base / "packet/PacketCsgoProgress.java"
        bulk = base / "packet/PacketCsgoBulkProgress.java"
        context = base / "box/BulkBoxContext.java"
        result = base / "packet/PacketBoxOpenResult.java"

        if progress.exists():
            migrate_packet_progress(progress, pkg)
        if bulk.exists():
            migrate_packet_bulk(bulk, pkg)
        if context.exists():
            migrate_bulk_context(context, pkg)
        if result.exists():
            migrate_packet_result(result, pkg)
        delete_random_item(module, pkg)

    print("\n✓ Migration complete.")


if __name__ == "__main__":
    main()
