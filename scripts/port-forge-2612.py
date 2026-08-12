#!/usr/bin/env python3
"""Sync v26_1_2 (NeoForge) source -> forge_26_1_2 (MinecraftForge 26.1.2).

Sync discipline (see AGENTS.md «forge_26_1_2 同步»):
- Mechanical port only: package rename + NeoForge->Forge import/API mapping.
- Files with manual adaptations are SKIPPED by default and reported; the drift
  between the mechanical port and the current forge file must be hand-merged.
  Use --force to re-plant them (only when no local changes are intended).
- --dry-run prints the sync plan (missing / drifted / manual) without writing.

Usage:
  scripts/port-forge-2612.py            # port missing files, report drifted
  scripts/port-forge-2612.py --dry-run  # plan only, no writes
  scripts/port-forge-2612.py --force    # also re-plant manual-adaptation files
  scripts/port-forge-2612.py --skip-manual   # do not even report manual files

Exit code: 0 = nothing to do / fully synced; 1 = missing files ported or
drifted/manual files remain (dry-run reports them too).
"""
import argparse
import os
import re
import sys

SRC_ROOT = "/Users/shuangyuexingxun/Desktop/CS2-Box/v26_1_2/src/main/java/com/reclizer/csgobox/v26_1_2"
DST_ROOT = "/Users/shuangyuexingxun/Desktop/CS2-Box/forge_26_1_2/src/main/java/com/reclizer/csgobox/forge_26_1_2"

# Simple string replacements (order matters - longer patterns first)
REPLACEMENTS = [
    # Package rename
    ("com.reclizer.csgobox.v26_1_2", "com.reclizer.csgobox.forge_26_1_2"),
    # NeoForge event bus
    ("net.neoforged.bus.api.IEventBus", "net.minecraftforge.eventbus.api.bus.BusGroup"),
    ("net.neoforged.bus.api.SubscribeEvent", "net.minecraftforge.eventbus.api.listener.SubscribeEvent"),
    # FML
    ("net.neoforged.fml.ModLoadingContext", "net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext"),
    ("net.neoforged.fml.common.EventBusSubscriber", "net.minecraftforge.fml.common.Mod"),
    ("net.neoforged.fml.common.Mod", "net.minecraftforge.fml.common.Mod"),
    ("net.neoforged.fml.config.ModConfig", "net.minecraftforge.fml.config.ModConfig"),
    ("net.neoforged.fml.event.config.ModConfigEvent", "net.minecraftforge.fml.event.config.ModConfigEvent"),
    ("net.neoforged.fml.event.lifecycle.FMLClientSetupEvent", "net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent"),
    ("net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent", "net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent"),
    ("net.neoforged.fml.loading.FMLPaths", "net.minecraftforge.fml.loading.FMLPaths"),
    # NeoForge common
    ("net.neoforged.neoforge.common.ModConfigSpec", "net.minecraftforge.common.ForgeConfigSpec"),
    ("net.neoforged.neoforge.common.NeoForge", "net.minecraftforge.common.MinecraftForge"),
    # NeoForge registries
    ("net.neoforged.neoforge.registries.DeferredRegister", "net.minecraftforge.registries.DeferredRegister"),
    ("net.neoforged.neoforge.registries.NeoForgeRegistries", "net.minecraftforge.registries.ForgeRegistries"),
    ("net.neoforged.neoforge.registries.RegisterEvent", "net.minecraftforge.registries.RegisterEvent"),
    ("net.neoforged.neoforge.registries.DeferredItem", "net.minecraftforge.registries.DeferredItem"),
    # NeoForge network
    ("net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent", ""),
    ("net.neoforged.neoforge.network.registration.PayloadRegistrar", ""),
    ("net.neoforged.neoforge.network.handling.IPayloadContext", "net.minecraftforge.event.network.CustomPayloadEvent"),
    ("final IPayloadContext context", "final CustomPayloadEvent.Context context"),
    ("net.neoforged.neoforge.network.PacketDistributor", "net.minecraftforge.network.PacketDistributor"),
    # NeoForge events
    ("net.neoforged.neoforge.event.RegisterCommandsEvent", "net.minecraftforge.event.RegisterCommandsEvent"),
    ("net.neoforged.neoforge.event.entity.living.LivingDeathEvent", "net.minecraftforge.event.entity.living.LivingDeathEvent"),
    ("net.neoforged.neoforge.event.entity.player.PlayerInteractEvent", "net.minecraftforge.event.entity.player.PlayerInteractEvent"),
    ("net.neoforged.neoforge.event.entity.player.PlayerEvent", "net.minecraftforge.event.entity.player.PlayerEvent"),
    ("net.neoforged.neoforge.event.server.ServerStartingEvent", "net.minecraftforge.event.server.ServerStartingEvent"),
    ("net.neoforged.neoforge.event.server.ServerStoppingEvent", "net.minecraftforge.event.server.ServerStoppingEvent"),
    ("net.neoforged.neoforge.event.tick.ServerTickEvent", "net.minecraftforge.event.tick.ServerTickEvent"),
    ("net.neoforged.neoforge.event.entity.player.PlayerContainerEvent", "net.minecraftforge.event.entity.player.PlayerContainerEvent"),
    # NeoForge attachment (capability)
    ("net.neoforged.neoforge.attachment.AttachmentType", "net.minecraftforge.common.capabilities.Capability"),
    ("net.neoforged.neoforge.attachment.IAttachmentHolder", "net.minecraftforge.common.capabilities.ICapabilityProvider"),
    ("net.neoforged.neoforge.attachment.IAttachmentSerializer", ""),
    # NeoForge client
    ("net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent", ""),
    # Dist marker
    ("net.neoforged.api.distmarker.Dist", "net.minecraftforge.api.distmarker.Dist"),
    # API usage patterns
    ("NeoForge.EVENT_BUS", "MinecraftForge.EVENT_BUS"),
    ("ModConfigSpec", "ForgeConfigSpec"),
    # Annotation patterns
    ("@EventBusSubscriber(", "@Mod.EventBusSubscriber("),
]

# Files whose forge side carries manual adaptations: the mechanical port would
# clobber them, so they are skipped by default and must be hand-merged.
MANUAL_ADAPTATION_FILES = {
    "CsgoBox.java",
    "item/ModItems.java",
    "item/ItemCsgoBox.java",
    "item/ItemCsgoKey.java",
    "packet/PacketCsgoProgress.java",
    "packet/PacketCsgoBulkProgress.java",
    "packet/PacketBoxOpenResult.java",
    "packet/PacketBoxBulkResult.java",
    "packet/PacketRequestBoxItems.java",
    "packet/PacketSyncBoxItems.java",
    "packet/PacketValidation.java",
    "capability/ModCapability.java",
    "capability/CsboxPlayerData.java",
    "config/CsboxConfig.java",
    "event/ClickEvent.java",
    "event/ModEvents.java",
    "event/LoadErrorAnnouncer.java",
    "command/CsboxCommand.java",
    "gui/CsboxScreen.java",
    "gui/CsboxProgressScreen.java",
    "gui/CsboxBulkOverviewScreen.java",
    "gui/CsboxBulkResultScreen.java",
    "gui/CsboxConfirmScreen.java",
    "gui/CsLookItemScreen.java",
    "utils/GuiItemMove.java",
    "utils/IconListTools.java",
    "utils/ButtonPalette.java",
    "utils/RenderFontTool.java",
    "box/BoxJsonLoader.java",
    "box/BoxDefaults.java",
    "box/BoxJsonSchemaValidator.java",
    "box/BoxDefinition.java",
    "box/BoxRegistry.java",
    "box/GradeGroup.java",
    "box/BulkBoxContext.java",
    "box/BulkOpenResult.java",
    "box/LoadError.java",
    "box/TutorialFetcher.java",
    "box/TutorialSources.java",
    "box/BoxItemCodec.java",
    "advancement/OpenedBoxTrigger.java",
    "advancement/ModLoadedTrigger.java",
    "sounds/ModSounds.java",
    "gui/pip/Icon3DRenderState.java",
    "gui/pip/Icon3DRenderer.java",
}


def port_content(content: str) -> str:
    for old, new in REPLACEMENTS:
        if old:
            content = content.replace(old, new)
    return content


def main() -> int:
    ap = argparse.ArgumentParser(description="Sync v26_1_2 -> forge_26_1_2 (mechanical port)")
    ap.add_argument("--dry-run", action="store_true", help="print plan, do not write")
    ap.add_argument("--force", action="store_true", help="re-plant manual-adaptation files too")
    ap.add_argument("--skip-manual", action="store_true", help="do not report manual-adaptation files")
    args = ap.parse_args()

    missing, drifted, manual, up_to_date = [], [], [], []
    for root, dirs, files in os.walk(SRC_ROOT):
        for filename in sorted(files):
            if not filename.endswith(".java"):
                continue
            src_path = os.path.join(root, filename)
            rel = os.path.relpath(src_path, SRC_ROOT)
            dst_path = os.path.join(DST_ROOT, rel)
            ported = port_content(open(src_path, encoding="utf-8").read())
            if not os.path.exists(dst_path):
                missing.append((rel, dst_path, ported))
                continue
            if rel in MANUAL_ADAPTATION_FILES:
                manual.append(rel)
                continue
            current = open(dst_path, encoding="utf-8").read()
            if current == ported:
                up_to_date.append(rel)
            else:
                drifted.append((rel, dst_path, ported))

    print(f"v26_1_2 -> forge_26_1_2 同步盘点（dry-run={args.dry_run} force={args.force}）")
    print(f"  missing(新增可机械移植): {len(missing)}")
    for rel, _, _ in missing:
        print(f"    + {rel}")
    print(f"  drifted(机械漂移,可--force重灌或手工合入): {len(drifted)}")
    for rel, _, _ in drifted:
        print(f"    ~ {rel}")
    if not args.skip_manual:
        print(f"  manual(手工适配,需人工合入): {len(manual)}")
        for rel in manual:
            print(f"    M {rel}")
    print(f"  up-to-date(与机械移植一致): {len(up_to_date)}")

    if args.dry_run:
        return 1 if (missing or drifted) else 0

    writes = 0
    for rel, dst, content in missing:
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        with open(dst, "w", encoding="utf-8") as f:
            f.write(content)
        print(f"  ported: {rel}")
        writes += 1
    if args.force:
        for rel, dst, content in drifted:
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            with open(dst, "w", encoding="utf-8") as f:
                f.write(content)
            print(f"  re-planted: {rel}")
            writes += 1
    print(f"\n{len(missing)} missing ported, {len(drifted) if args.force else 0} drifted re-planted, "
          f"{len(manual)} manual kept. writes={writes}")
    return 1 if (missing or (drifted and not args.force) or manual) else 0


if __name__ == "__main__":
    sys.exit(main())
