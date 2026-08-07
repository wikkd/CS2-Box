#!/usr/bin/env python3
"""Port v26_1_2 (NeoForge) source to forge_26_1_2 (MinecraftForge 26.1.2).

Mechanical transformations:
- Package rename: com.reclizer.csgobox.v26_1_2 -> com.reclizer.csgobox.forge_26_1_2
- NeoForge imports -> Forge imports
- NeoForge API patterns -> Forge API patterns
"""
import os
import re
import shutil

SRC_ROOT = "/Users/shuangyuexingxun/Desktop/CS2-Box/v26_1_2/src/main/java/com/reclizer/csgobox/v26_1_2"
DST_ROOT = "/Users/shuangyuexingxun/Desktop/CS2-Box/forge_26_1_2/src/main/java/com/reclizer/csgobox/forge_26_1_2"

# Simple string replacements (order matters - longer patterns first)
REPLACEMENTS = [
    # Package rename
    ("com.reclizer.csgobox.v26_1_2", "com.reclizer.csgobox.forge_26_1_2"),
    # NeoForge event bus
    ("net.neoforged.bus.api.IEventBus", "net.minecraftforge.eventbus.api.IEventBus"),
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
    # NeoForge network
    ("net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent", ""),
    ("net.neoforged.neoforge.network.handling.IPayloadContext", "net.minecraftforge.network.NetworkEvent"),
    ("net.neoforged.neoforge.network.PacketDistributor", "net.minecraftforge.network.PacketDistributor"),
    # NeoForge events
    ("net.neoforged.neoforge.event.RegisterCommandsEvent", "net.minecraftforge.event.RegisterCommandsEvent"),
    ("net.neoforged.neoforge.event.entity.living.LivingDeathEvent", "net.minecraftforge.event.entity.living.LivingDeathEvent"),
    ("net.neoforged.neoforge.event.entity.player.PlayerInteractEvent", "net.minecraftforge.event.entity.player.PlayerInteractEvent"),
    ("net.neoforged.neoforge.event.entity.player.PlayerEvent", "net.minecraftforge.event.entity.player.PlayerEvent"),
    ("net.neoforged.neoforge.event.server.ServerStartingEvent", "net.minecraftforge.event.server.ServerStartingEvent"),
    ("net.neoforged.neoforge.event.server.ServerStoppingEvent", "net.minecraftforge.event.server.ServerStoppingEvent"),
    ("net.neoforged.neoforge.event.tick.ServerTickEvent", "net.minecraftforge.event.tick.ServerTickEvent"),
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

def port_file(src_path, dst_path):
    """Read a source file, apply transformations, write to destination."""
    with open(src_path, 'r', encoding='utf-8') as f:
        content = f.read()

    for old, new in REPLACEMENTS:
        if old:  # skip empty replacements
            content = content.replace(old, new)

    os.makedirs(os.path.dirname(dst_path), exist_ok=True)
    with open(dst_path, 'w', encoding='utf-8') as f:
        f.write(content)
    print(f"  Ported: {os.path.relpath(dst_path, DST_ROOT)}")

def main():
    print(f"Porting from: {SRC_ROOT}")
    print(f"         to: {DST_ROOT}")
    print()

    file_count = 0
    for root, dirs, files in os.walk(SRC_ROOT):
        for filename in sorted(files):
            if not filename.endswith('.java'):
                continue
            src_path = os.path.join(root, filename)
            rel_path = os.path.relpath(src_path, SRC_ROOT)
            dst_path = os.path.join(DST_ROOT, rel_path)
            port_file(src_path, dst_path)
            file_count += 1

    print(f"\nDone! Ported {file_count} files.")
    print("\nNOTE: The following files need MANUAL adaptation:")
    print("  - CsgoBox.java (entry point: constructor, event bus, networking)")
    print("  - capability/ModCapability.java (AttachmentType -> Forge Capability)")
    print("  - packet/*.java (CustomPacketPayload -> SimpleChannel)")
    print("  - item/ModItems.java (DeferredRegister API differences)")
    print("  - gui/*.java (decoupled rendering -> GuiGraphics)")

if __name__ == '__main__':
    main()
