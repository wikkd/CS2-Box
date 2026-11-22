package com.reclizer.csgobox.forge_1_20_1.utils;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import com.reclizer.csgobox.api.preview.CsboxPreviewAPI;
import com.reclizer.csgobox.api.preview.CsboxPreviewProvider;
import com.reclizer.csgobox.api.preview.PreviewSource;
import com.reclizer.csgobox.api.preview.RestPose;
import com.reclizer.csgobox.preview.PreviewDeclaration;
import com.reclizer.csgobox.preview.PreviewDeclarationParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.slf4j.Logger;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side resolution hub of the preview adaptation API (2026-11-22
 * 预览适配接口 v1). Two passes feed {@link AnimRenderOps#renderItem3D}, in
 * front of the built-in strategy chain and the default path:
 *
 * <ol>
 *   <li>the code SPI ({@link CsboxPreviewAPI}) — highest-priority provider
 *       that claims the item wins;</li>
 *   <li>zero-code JSON declarations — third-party jars (and resource packs)
 *       ship {@code assets/&lt;modid&gt;/csgobox_preview/*.json}, scanned from
 *       the client resource manager across every namespace.</li>
 * </ol>
 *
 * <p>The scan lazily registers a vanilla reload listener on first use (same
 * shape as the 26.x model-cache clearer: the manager first runs at render
 * time, when the client resource manager exists), so F3+T re-scans with no
 * loader-event plumbing. Bad declarations degrade — unknown ids, bad NBT and
 * throwing providers each log once and fall through to the next layer, so
 * one broken jar can never break the preview pipeline.</p>
 */
final class CsboxPreviewManager {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Asset folder scanned in every namespace (mod jars + resource packs). */
    private static final String DIR = "csgobox_preview";

    private static volatile boolean initialized;
    /** Item id → candidates, each list already sorted by {@link PreviewDeclaration#ORDER}. */
    private static volatile Map<String, List<PreviewDeclaration>> declarations = Map.of();
    /** Providers sorted by priority (ties keep registration order); rebuilt on size change. */
    private static volatile List<CsboxPreviewProvider> providers = List.of();
    /** One-shot guards: unknown ids and throwing providers log once, not per frame. */
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private CsboxPreviewManager() {
    }

    /** SPI pass: the highest-priority provider that claims the item, or null. */
    static PreviewSource resolveProvider(ItemStack stack) {
        ensureInitialized();
        List<CsboxPreviewProvider> snapshot = providerSnapshot();
        if (snapshot.isEmpty()) {
            return null;
        }
        for (CsboxPreviewProvider provider : snapshot) {
            try {
                if (provider.matches(stack)) {
                    PreviewSource source = provider.preview(stack);
                    if (source != null) {
                        return source;
                    }
                }
            } catch (Throwable t) {
                if (WARNED.add("provider:" + provider.getClass().getName())) {
                    LOGGER.warn("[csgobox] preview provider {} threw; skipped",
                            provider.getClass().getName(), t);
                }
            }
        }
        return null;
    }

    /** JSON pass: first applicable declaration in resolution order, or null. */
    static PreviewSource resolveDeclaration(ItemStack stack) {
        ensureInitialized();
        Map<String, List<PreviewDeclaration>> map = declarations;
        if (map.isEmpty()) {
            return null;
        }
        List<PreviewDeclaration> candidates =
                map.get(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        if (candidates == null) {
            return null;
        }
        for (PreviewDeclaration declaration : candidates) {
            PreviewSource source = toSource(declaration);
            if (source != null) {
                return source;
            }
        }
        return null;
    }

    private static void ensureInitialized() {
        if (initialized) {
            return;
        }
        synchronized (CsboxPreviewManager.class) {
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
                LOGGER.warn("[csgobox] preview declaration reload listener registration failed", t);
            }
        }
    }

    private static void rebuild() {
        Map<String, List<PreviewDeclaration>> map = new HashMap<>();
        List<PreviewDeclarationParser.Diagnostic> diagnostics = new ArrayList<>();
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
                    diagnostics.add(PreviewDeclarationParser.Diagnostic.error(id.toString(), "read failed: " + t));
                    continue;
                }
                PreviewDeclaration declaration = PreviewDeclarationParser.parse(text, id.toString(), diagnostics);
                if (declaration == null) {
                    continue;
                }
                files++;
                for (String itemId : declaration.items()) {
                    map.computeIfAbsent(itemId, key -> new ArrayList<>()).add(declaration);
                }
            }
        } catch (Throwable t) {
            LOGGER.warn("[csgobox] preview declaration scan failed", t);
        }
        for (List<PreviewDeclaration> list : map.values()) {
            list.sort(PreviewDeclaration.ORDER);
        }
        for (PreviewDeclarationParser.Diagnostic diagnostic : diagnostics) {
            if (diagnostic.warning()) {
                LOGGER.info("[csgobox] preview declaration warning: {} ({})",
                        diagnostic.reason(), diagnostic.source());
            } else {
                LOGGER.warn("[csgobox] preview declaration rejected: {} ({})",
                        diagnostic.reason(), diagnostic.source());
            }
        }
        declarations = map.isEmpty() ? Map.of() : map;
        LOGGER.info("[csgobox] preview declarations: {} usable file(s), {} item(s)", files, map.size());
    }

    private static List<CsboxPreviewProvider> providerSnapshot() {
        List<CsboxPreviewProvider> live = CsboxPreviewAPI.registered();
        if (live.size() != providers.size()) {
            // Registration is add-only (addIfAbsent), so the size check is a
            // complete invalidation; the stable sort keeps registration order
            // for equal priorities.
            List<CsboxPreviewProvider> sorted = new ArrayList<>(live);
            sorted.sort(Comparator.comparingInt(CsboxPreviewProvider::priority).reversed());
            providers = sorted;
        }
        return providers;
    }

    private static PreviewSource toSource(PreviewDeclaration declaration) {
        if (declaration.isEntity()) {
            ResourceLocation id = ResourceLocation.tryParse(declaration.entityId());
            if (id == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
                warnOnce("entity:" + declaration.entityId(),
                        "[csgobox] preview declaration entity not found: " + declaration.entityId()
                                + " (" + declaration.source() + ")");
                return null;
            }
            return PreviewSource.entity(BuiltInRegistries.ENTITY_TYPE.get(id), spawnTagOf(declaration),
                    restPoseOf(declaration), declaration.fitBoost());
        }
        ResourceLocation id = ResourceLocation.tryParse(declaration.blockId());
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
            warnOnce("block:" + declaration.blockId(),
                    "[csgobox] preview declaration block not found: " + declaration.blockId()
                            + " (" + declaration.source() + ")");
            return null;
        }
        BlockState state = BuiltInRegistries.BLOCK.get(id).defaultBlockState();
        for (Map.Entry<String, String> entry : declaration.blockProperties().entrySet()) {
            state = applyBlockProperty(state, entry.getKey(), entry.getValue(), declaration);
        }
        return PreviewSource.block(state, restPoseOf(declaration), declaration.fitBoost());
    }

    private static BlockState applyBlockProperty(BlockState state, String name, String value,
                                                 PreviewDeclaration declaration) {
        Property<?> property = state.getBlock().getStateDefinition().getProperty(name);
        if (property == null) {
            warnOnce("prop:" + declaration.blockId() + ":" + name,
                    "[csgobox] preview declaration unknown block property " + name + " on "
                            + declaration.blockId() + " (" + declaration.source() + ")");
            return state;
        }
        return applyPropertyValue(state, property, value, declaration);
    }

    private static <T extends Comparable<T>> BlockState applyPropertyValue(BlockState state, Property<T> property,
                                                                           String value,
                                                                           PreviewDeclaration declaration) {
        Optional<T> parsed = property.getValue(value);
        if (parsed.isEmpty()) {
            warnOnce("propval:" + declaration.blockId() + ":" + property.getName() + ":" + value,
                    "[csgobox] preview declaration invalid value " + value + " for property "
                            + property.getName() + " on " + declaration.blockId());
            return state;
        }
        return state.setValue(property, parsed.get());
    }

    private static CompoundTag spawnTagOf(PreviewDeclaration declaration) {
        if (declaration.spawnDataJson() == null) {
            return null;
        }
        try {
            CompoundTag tag = TagParser.parseTag(declaration.spawnDataJson());
            // Vanilla egg data always carries "id"; declarations may omit it
            // because the target type is declared separately.
            if (!tag.contains("id")) {
                tag.putString("id", declaration.entityId());
            }
            return tag;
        } catch (CommandSyntaxException e) {
            warnOnce("spawn:" + declaration.entityId() + ":" + declaration.source(),
                    "[csgobox] preview declaration spawn_data is not valid NBT (" + declaration.source() + ")");
            return null;
        }
    }

    private static RestPose restPoseOf(PreviewDeclaration declaration) {
        if (declaration.yawDeg() == 0F && declaration.pitchDeg() == 0F && declaration.rollDeg() == 0F) {
            return RestPose.NONE;
        }
        return new RestPose(declaration.yawDeg(), declaration.pitchDeg(), declaration.rollDeg());
    }

    private static void warnOnce(String key, String message) {
        if (WARNED.add(key)) {
            LOGGER.warn(message);
        }
    }
}
