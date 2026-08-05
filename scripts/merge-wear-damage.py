#!/usr/bin/env python3
"""Merge the wear-based durability damage feature into legacy platform modules.

Targeted, idempotent merge: only the 5 wear-related changes per module.
Does NOT touch the user's in-progress RandomItem->logic migration or any
other unrelated differences. Fails loudly (exit 2) if any replacement does
not apply, so per-module API differences must be handled by hand.

Usage: python3 scripts/merge-wear-damage.py [module...]
Default: v1_21_3 v1_21_4 v1_21_5 v1_21_8 v1_21_10 v1_21_11
"""
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
MODULES = ["v1_21_3", "v1_21_4", "v1_21_5", "v1_21_8", "v1_21_10", "v1_21_11"]
NEW_MODULES = {"v26_2": "ModConfigSpec", "forge_26_1_2": "ForgeConfigSpec"}


def rep(text: str, old: str, new: str, count: int = 1) -> str:
    got = text.count(old)
    if got != count:
        raise ValueError(f"expected {count} occurrence(s), found {got}: {old[:60]!r}")
    return text.replace(old, new)


def merge_config(base: pathlib.Path, spec: str) -> None:
    p = base / "config/CsboxConfig.java"
    t = p.read_text(encoding="utf-8")
    t = rep(t, f"    private final {spec}.EnumValue<ErrorChatAudience> jsonErrorAudienceValue;",
            f"    private final {spec}.EnumValue<ErrorChatAudience> jsonErrorAudienceValue;\n"
            f"    private final {spec}.BooleanValue damageItemByWearValue;")
    t = rep(t, '                .defineEnum("jsonErrorAudience", ErrorChatAudience.OP_ONLY);',
            '                .defineEnum("jsonErrorAudience", ErrorChatAudience.OP_ONLY);\n'
            "        this.damageItemByWearValue = builder\n"
            '                .comment("Drawn items with durability lose durability by their wear value percentage (default on)")\n'
            '                .define("damageItemByWear", true);')
    t = rep(t, "    public ErrorChatAudience jsonErrorAudience() {\n"
               "        return jsonErrorAudienceValue.get();\n"
               "    }\n",
            "    public ErrorChatAudience jsonErrorAudience() {\n"
            "        return jsonErrorAudienceValue.get();\n"
            "    }\n"
            "\n"
            "    public boolean damageItemByWear() {\n"
            "        return damageItemByWearValue.get();\n"
            "    }\n")
    p.write_text(t, encoding="utf-8")


def merge_bulk_result(base: pathlib.Path) -> None:
    p = base / "box/BulkOpenResult.java"
    t = p.read_text(encoding="utf-8")
    t = rep(t, "        List<Integer> animationGrades\n) {",
            "        List<Integer> animationGrades,\n        float wear\n) {")
    p.write_text(t, encoding="utf-8")


def merge_progress(base: pathlib.Path, mod: str) -> None:
    p = base / "packet/PacketCsgoProgress.java"
    t = p.read_text(encoding="utf-8")
    if "import net.minecraft.core.component.DataComponents;" not in t:
        t = rep(t, f"import com.reclizer.csgobox.{mod}.item.ItemCsgoBox;",
                f"import com.reclizer.csgobox.{mod}.item.ItemCsgoBox;\n"
                "import net.minecraft.core.component.DataComponents;")
    wear_block = ("            float wear = 0F;\n"
                  "            if (CsgoBox.CONFIG.damageItemByWear() && giveItem.isDamageableItem()) {\n"
                  "                wear = rng.nextFloat();\n"
                  "                applyWearDamage(giveItem, wear);\n"
                  "            }\n"
                  "\n"
                  "            blockFurtherOpensStatic(player);")
    if "applyWearDamage(giveItem, wear);" not in t:
        t = rep(t, "            blockFurtherOpensStatic(player);", wear_block)
    method = (
        "    /**\n"
        "     * Damages a durable item stack by a fraction of its max durability\n"
        "     * proportional to the wear value (0..1). Clamped so the item never breaks\n"
        "     * (damage is at most maxDamage - 1) and never goes negative.\n"
        "     */\n"
        "    static void applyWearDamage(ItemStack stack, float wear) {\n"
        "        int maxDamage = stack.getMaxDamage();\n"
        "        if (maxDamage <= 0) {\n"
        "            return;\n"
        "        }\n"
        "        int damage = Math.max(0, Math.min(Math.round(wear * maxDamage), maxDamage - 1));\n"
        "        stack.set(DataComponents.DAMAGE, damage);\n"
        "    }\n")
    if "static void applyWearDamage" not in t:
        t = rep(t, "    private static int serverOpenCooldownTicks() {\n"
                   "        return 10;\n"
                   "    }\n",
                "    private static int serverOpenCooldownTicks() {\n"
                "        return 10;\n"
                "    }\n"
                "\n" + method)
    p.write_text(t, encoding="utf-8")


def merge_bulk_progress(base: pathlib.Path, inline_wear: bool) -> None:
    p = base / "packet/PacketCsgoBulkProgress.java"
    t = p.read_text(encoding="utf-8")
    if inline_wear:
        t = rep(t, "out.add(new BulkOpenResult(giveItem, finalGrade, seed, winningIndex, animItems, animGrades));",
                "out.add(new BulkOpenResult(giveItem, finalGrade, seed, winningIndex, animItems, animGrades, rng.nextFloat()));")
        t = rep(t, "out.add(new BulkOpenResult(s, Mth.clamp(g, 1, 5), 0L, -1, List.of(), List.of()));",
                "out.add(new BulkOpenResult(s, Mth.clamp(g, 1, 5), 0L, -1, List.of(), List.of(), rng.nextFloat()));")
    else:
        t = rep(t, "out.add(new BulkOpenResult(giveItem, finalGrade, seed, winningIndex, animItems, animGrades));",
                "float wear = rng.nextFloat();\n"
                "                out.add(new BulkOpenResult(giveItem, finalGrade, seed, winningIndex, animItems, animGrades, wear));")
        t = rep(t, "out.add(new BulkOpenResult(s, Mth.clamp(g, 1, 5), 0L, -1, List.of(), List.of()));",
                "float wear = rng.nextFloat();\n"
                "                out.add(new BulkOpenResult(s, Mth.clamp(g, 1, 5), 0L, -1, List.of(), List.of(), wear));")
    damage_loop = (
        "\n        // Wear-based durability damage, applied on the main thread. The first\n"
        "        // box's animation strip shares the winner stack, so damage it too for a\n"
        "        // consistent reveal.\n"
        "        if (CsgoBox.CONFIG.damageItemByWear()) {\n"
        "            for (BulkOpenResult r : truncated) {\n"
        "                if (r.wear() > 0F && r.resultItem().isDamageableItem()) {\n"
        "                    PacketCsgoProgress.applyWearDamage(r.resultItem(), r.wear());\n"
        "                    if (!r.animationItems().isEmpty()\n"
        "                            && r.winningIndex() >= 0\n"
        "                            && r.winningIndex() < r.animationItems().size()) {\n"
        "                        ItemStack animWinner = r.animationItems().get(r.winningIndex());\n"
        "                        if (!animWinner.isEmpty() && animWinner.isDamageableItem()) {\n"
        "                            PacketCsgoProgress.applyWearDamage(animWinner, r.wear());\n"
        "                        }\n"
        "                    }\n"
        "                }\n"
        "            }\n"
        "        }\n")
    if "applyWearDamage(r.resultItem(), r.wear())" not in t:
        t = rep(t, "        List<BulkOpenResult> truncated = results.subList(0, actualK);",
                "        List<BulkOpenResult> truncated = results.subList(0, actualK);\n" + damage_loop)
    p.write_text(t, encoding="utf-8")


def merge_look_screen(base: pathlib.Path) -> None:
    p = base / "gui/CsLookItemScreen.java"
    t = p.read_text(encoding="utf-8")
    if "getDamageValue() > 0" not in t:
        t = rep(t, "        this.wearValue = rnd.nextFloat();",
                "        if (!this.openItem.isEmpty() && this.openItem.isDamageableItem() && this.openItem.getDamageValue() > 0) {\n"
                "            int maxDamage = this.openItem.getMaxDamage();\n"
                "            this.wearValue = maxDamage > 0 ? (float) this.openItem.getDamageValue() / maxDamage : rnd.nextFloat();\n"
                "        } else {\n"
                "            this.wearValue = rnd.nextFloat();\n"
                "        }")
    p.write_text(t, encoding="utf-8")


def main() -> None:
    mods = sys.argv[1:] or (MODULES + list(NEW_MODULES))
    failed = False
    for m in mods:
        base = ROOT / m / "src/main/java/com/reclizer/csgobox" / m
        if not base.exists():
            print(f"SKIP {m}: path {base} missing")
            continue
        try:
            spec = NEW_MODULES.get(m, "ModConfigSpec")
            merge_config(base, spec)
            merge_bulk_result(base)
            merge_progress(base, m)
            merge_bulk_progress(base, inline_wear=bool(NEW_MODULES.get(m)))
            merge_look_screen(base)
            print(f"OK   {m}")
        except Exception as e:
            failed = True
            print(f"FAIL {m}: {e}")
    sys.exit(2 if failed else 0)


if __name__ == "__main__":
    main()
