#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Mechanical port v1_21_1 (NeoForge) -> fabric_1_21_1 (Fabric).

Copies the loader-neutral source tree with a package rename plus a set of
Narrow NeoForge->Fabric API text replacements. Loader-bound files (registries,
entry point, config, event classes, client handlers, Create/TACZ integration)
are EXCLUDED here and hand-adapted instead — see scripts header in
fabric_1_21_1/build.gradle and docs/PORTING-fabric.md.

Run from the repo root:  python scripts/port-fabric-1211.py [--dry-run]
"""
import re
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "v1_21_1" / "src" / "main" / "java" / "com" / "reclizer" / "csgobox" / "v1_21_1"
DST = ROOT / "fabric_1_21_1" / "src" / "main" / "java" / "com" / "reclizer" / "csgobox" / "fabric_1_21_1"

# Hand-adapted (or intentionally dropped) files — never copied mechanically.
EXCLUDE = [
    # Create deployer integration — Create has no Fabric build for 1.21.1.
    "create/DeployerBoxOpen.java",
    "create/DeployerBoxOpenRecipe.java",
    "create/DeployerBoxOpenRecipeSearch.java",
    # TACZ inspect viewport impl — TACZ has no Fabric build; the reflection
    # facade degrades to the missing-impl placeholder on its own.
    "compat/TaczInspectViewportImpl.java",
    "command/TaczList.java",
    # Capability registration — the block entity already implements
    # WorldlyContainer, which Fabric's Transfer API wraps natively.
    "block/entity/RecyclerAutomation.java",
    # Rewritten by hand below the script (loader-bound):
    "CsgoBox.java",
    "config/CsboxConfig.java",
    "config/ClothConfigHook.java",
    "config/CsboxClothConfigScreen.java",
    "event/ModEvents.java",
    "event/BoxOpenedEvent.java",
    "event/BoxOpeningEvent.java",
    "event/BoxEntityDropEvent.java",
    "event/TerminalBuyEvent.java",
    "event/TerminalBuyAttemptEvent.java",
    "event/ArmoryRecycleEvent.java",
    "event/ClickEvent.java",
    "event/ScreenBlurBoost.java",
    "event/LoadErrorAnnouncer.java",
    "event/FirstPersonInspectHandler.java",
    "item/ModItems.java",
    "item/ItemCsgoBox.java",
    "block/ModBlocks.java",
    "block/entity/ArmoryRecyclerBlockEntity.java",
    "menu/ModMenus.java",
    "sounds/ModSounds.java",
    "villager/ModVillagers.java",
    "utils/AnimRenderOps.java",
    "box/BoxItemCodec.java",
]

# Ordered text replacements. Each entry: (pattern, replacement, description).
REPLACEMENTS = [
    # Package rename (imports + package decls + FQNs).
    (r"com\.reclizer\.csgobox\.v1_21_1", "com.reclizer.csgobox.fabric_1_21_1", "package rename"),
    # --- ModList -> FabricLoader ---
    (r"ModList\.get\(\) != null && ModList\.get\(\)\.isLoaded\(", "FabricLoader.getInstance().isLoaded(", "ModList guard &&"),
    (r"ModList\.get\(\) == null \|\| !ModList\.get\(\)\.isLoaded\(", "!FabricLoader.getInstance().isLoaded(", "ModList guard || !"),
    (r"ModList\.get\(\)\.isLoaded\(", "FabricLoader.getInstance().isLoaded(", "ModList.isLoaded"),
    (r"import net\.neoforged\.fml\.ModList;", "import net.fabricmc.loader.api.FabricLoader;", "ModList import"),
    # --- FMLPaths -> FabricLoader.getConfigDir ---
    (r"FMLPaths\.CONFIGDIR\.get\(\)", "FabricLoader.getInstance().getConfigDir()", "config dir"),
    (r"import net\.neoforged\.fml\.loading\.FMLPaths;", "import net.fabricmc.loader.api.FabricLoader;", "FMLPaths import"),
    # --- PacketDistributor -> Fabric networking ---
    (r"PacketDistributor\.sendToServer\(", "ClientPlayNetworking.send(", "sendToServer"),
    (r"PacketDistributor\.sendToPlayer\(", "ServerPlayNetworking.send(", "sendToPlayer"),
    (r"import net\.neoforged\.neoforge\.network\.PacketDistributor;",
     "import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;\nimport net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;",
     "PacketDistributor import"),
    # --- IPayloadContext -> FabricPayloadContext wrapper ---
    (r"import net\.neoforged\.neoforge\.network\.handling\.IPayloadContext;",
     "import com.reclizer.csgobox.fabric_1_21_1.net.FabricPayloadContext;", "payload ctx import"),
    (r"\bIPayloadContext\b", "FabricPayloadContext", "payload ctx type"),
    # --- Mod event bus posts -> CsgoBus ---
    (r"NeoForge\.EVENT_BUS\.post\(", "CsgoBus.post(", "event bus post"),
]

# Imports appended after replacement (deduped); description -> import line.
EXTRA_IMPORTS = {
    "event bus post": "import com.reclizer.csgobox.fabric_1_21_1.event.CsgoBus;",
}


def apply_replacements(text):
    used = set()
    for pattern, repl, desc in REPLACEMENTS:
        if re.search(pattern, text):
            text = re.sub(pattern, repl, text)
            used.add(desc)
    lines = text.split("\n")
    for desc in used:
        imp = EXTRA_IMPORTS.get(desc)
        if imp and imp not in text:
            # Insert after the last import line.
            for i in range(len(lines) - 1, -1, -1):
                if lines[i].startswith("import "):
                    lines.insert(i + 1, imp)
                    break
    return "\n".join(lines)


def main():
    dry = "--dry-run" in sys.argv
    copied, replaced, skipped = 0, 0, 0
    for src_file in sorted(SRC.rglob("*.java")):
        rel = src_file.relative_to(SRC).as_posix()
        if rel in EXCLUDE:
            skipped += 1
            print(f"SKIP  {rel}  (hand-adapted)")
            continue
        out_file = DST / rel
        text = src_file.read_text(encoding="utf-8")
        new_text = apply_replacements(text)
        changed = new_text != text
        if dry:
            print(f"{'REPL ' if changed else 'COPY '} {rel}")
        else:
            out_file.parent.mkdir(parents=True, exist_ok=True)
            out_file.write_text(new_text, encoding="utf-8", newline="\n")
        if changed:
            replaced += 1
        else:
            copied += 1
    mode = "(dry-run)" if dry else ""
    print(f"\nDone {mode}: {replaced} file(s) with replacements, {copied} verbatim, {skipped} excluded.")


if __name__ == "__main__":
    main()
