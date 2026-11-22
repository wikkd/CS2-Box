package com.reclizer.csgobox.forge_1_20_1.utils;

import com.mojang.logging.LogUtils;
import com.reclizer.csgobox.inspectaudio.InspectAudioDeclaration;
import com.reclizer.csgobox.inspectaudio.InspectAudioDeclarationParser;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side JSON declaration pass of the inspect-audio API (2026-11-22
 * 检视音频接口 v1). The zero-code channel between the code SPI
 * ({@code com.reclizer.csgobox.api.audio.CsboxInspectAudioAPI}) and the
 * built-in RecordItem path inside {@link DiscInspect}: third-party jars and
 * resource packs ship {@code assets/&lt;modid&gt;/csgobox_inspect_audio/*.json}
 * declaring item→sound, scanned from the client resource manager across
 * every namespace.
 *
 * <p>Resolution per click: providers first, then this declaration layer, then
 * the built-in RecordItem sound. The scan lazily registers a vanilla reload
 * listener on first use (same shape as {@link CsboxPreviewManager}: the
 * manager first runs at resolve time, when the client resource manager
 * exists), so F3+T re-scans with no loader-event plumbing. Unknown sound ids
 * log once and the candidate is skipped in favor of the next declaration, so
 * one broken pack can never break the inspect audio pipeline.</p>
 */
final class CsboxInspectAudioManager {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Asset folder scanned in every namespace (mod jars + resource packs). */
    private static final String DIR = "csgobox_inspect_audio";

    private static volatile boolean initialized;
    /** Item id → candidates, each list already sorted by {@link InspectAudioDeclaration#ORDER}. */
    private static volatile Map<String, List<InspectAudioDeclaration>> declarations = Map.of();
    /** One-shot guards: unknown sound ids log once, not per click. */
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private CsboxInspectAudioManager() {
    }

    /** JSON pass: the first declared sound for the item in resolution order,
     *  or {@code null} (the caller falls through to the built-in RecordItem
     *  path). */
    static SoundEvent firstDeclaredSound(ItemStack stack) {
        ensureInitialized();
        Map<String, List<InspectAudioDeclaration>> map = declarations;
        if (map.isEmpty()) {
            return null;
        }
        List<InspectAudioDeclaration> candidates =
                map.get(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        if (candidates == null) {
            return null;
        }
        for (InspectAudioDeclaration declaration : candidates) {
            SoundEvent sound = toSound(declaration);
            if (sound != null) {
                return sound;
            }
        }
        return null;
    }

    private static void ensureInitialized() {
        if (initialized) {
            return;
        }
        synchronized (CsboxInspectAudioManager.class) {
            if (initialized) {
                return;
            }
            initialized = true;
            rebuild();
            // Rescan on every client resource reload (F3+T, mod list changes).
            try {
                ResourceManager rm = Minecraft.getInstance().getResourceManager();
                if (rm instanceof ReloadableResourceManager reloadable) {
                    // 1.20.1 registerReloadListener takes PreparableReloadListener
                    // (multi-method, lambda-hostile); the anonymous
                    // ResourceManagerReloadListener inherits the vanilla default
                    // reload() that just runs onResourceManagerReload.
                    reloadable.registerReloadListener(new ResourceManagerReloadListener() {
                        @Override
                        public void onResourceManagerReload(ResourceManager resourceManager) {
                            rebuild();
                        }
                    });
                }
            } catch (Throwable t) {
                LOGGER.warn("[csgobox] inspect-audio declaration reload listener registration failed", t);
            }
        }
    }

    private static void rebuild() {
        Map<String, List<InspectAudioDeclaration>> map = new HashMap<>();
        List<InspectAudioDeclarationParser.Diagnostic> diagnostics = new ArrayList<>();
        int files = 0;
        try {
            Map<ResourceLocation, Resource> found = Minecraft.getInstance().getResourceManager()
                    .listResources(DIR, id -> id.getPath().startsWith(DIR + "/") && id.getPath().endsWith(".json"));
            for (Map.Entry<ResourceLocation, Resource> entry : found.entrySet()) {
                ResourceLocation id = entry.getKey();
                String text;
                try (InputStream in = entry.getValue().open()) {
                    text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                } catch (Throwable t) {
                    diagnostics.add(InspectAudioDeclarationParser.Diagnostic.error(id.toString(), "read failed: " + t));
                    continue;
                }
                InspectAudioDeclaration declaration = InspectAudioDeclarationParser.parse(text, id.toString(), diagnostics);
                if (declaration == null) {
                    continue;
                }
                files++;
                for (String itemId : declaration.items()) {
                    map.computeIfAbsent(itemId, key -> new ArrayList<>()).add(declaration);
                }
            }
        } catch (Throwable t) {
            LOGGER.warn("[csgobox] inspect-audio declaration scan failed", t);
        }
        for (List<InspectAudioDeclaration> list : map.values()) {
            list.sort(InspectAudioDeclaration.ORDER);
        }
        for (InspectAudioDeclarationParser.Diagnostic diagnostic : diagnostics) {
            if (diagnostic.warning()) {
                LOGGER.info("[csgobox] inspect-audio declaration warning: {} ({})",
                        diagnostic.reason(), diagnostic.source());
            } else {
                LOGGER.warn("[csgobox] inspect-audio declaration rejected: {} ({})",
                        diagnostic.reason(), diagnostic.source());
            }
        }
        declarations = map.isEmpty() ? Map.of() : map;
        LOGGER.info("[csgobox] inspect-audio declarations: {} usable file(s), {} item(s)", files, map.size());
    }

    private static SoundEvent toSound(InspectAudioDeclaration declaration) {
        ResourceLocation id = ResourceLocation.tryParse(declaration.soundId());
        if (id == null || !BuiltInRegistries.SOUND_EVENT.containsKey(id)) {
            warnOnce("sound:" + declaration.soundId(),
                    "[csgobox] inspect-audio declaration sound not found: " + declaration.soundId()
                            + " (" + declaration.source() + ")");
            return null;
        }
        return BuiltInRegistries.SOUND_EVENT.get(id);
    }

    private static void warnOnce(String key, String message) {
        if (WARNED.add(key)) {
            LOGGER.warn(message);
        }
    }
}
