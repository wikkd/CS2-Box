package com.reclizer.csgobox.forge_26_2.utils;

import com.reclizer.csgobox.utils.Quat;
import com.reclizer.csgobox.forge_26_2.CsgoBox;
import com.reclizer.csgobox.forge_26_2.gui.pip.EntityIcon3DRenderState;
import com.reclizer.csgobox.forge_26_2.gui.pip.Icon3DRenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.illager.SpellcasterIllager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.Level;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.fml.ModList;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Single per-platform adaptation point for animation rendering primitives.
 * Screens and logic helpers (IconListTools / GuiItemMove) must call ONLY
 * through this class; version-varying render API lives here and nowhere else.
 * era: decoupled
 */
public final class AnimRenderOps {

    /** Set once the first supports3D() call has logged the render environment. */
    private static boolean logged3DEnvironment;

    private AnimRenderOps() {
    }

    // --- Perf: cache per-item model geometry so the open-box animation does
    // not re-resolve the item model every frame. updateForLiving() is the
    // expensive call; the bounding box it yields is pure geometry and static
    // for a given (item, component set), so caching is safe. The key excludes
    // item count (getComponentsPatch() does not carry count), so two stacks of
    // the same item with different counts share one entry. The client render
    // thread is single-threaded, so no locking is needed.
    private record ModelKey(net.minecraft.world.item.Item item, int componentHash) {
    }

    private record CachedModel(float offsetX, float offsetY,
                               TrackingItemStackRenderState trackedState,
                               float span, float cx, float cy, float cz) {
    }

    private static final int MODEL_CACHE_CAP = 256;
    private static final LinkedHashMap<ModelKey, CachedModel> MODEL_CACHE = new LinkedHashMap<>(MODEL_CACHE_CAP + 1, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<ModelKey, CachedModel> eldest) {
            return size() > MODEL_CACHE_CAP;
        }
    };

    private static final ResourceManagerReloadListener MODEL_CACHE_CLEARER = new ResourceManagerReloadListener() {
        @Override
        public void onResourceManagerReload(ResourceManager resourceManager) {
            clearModelCache();
        }
    };

    private static volatile boolean modelCacheClearerRegistered = false;

    private static ModelKey modelKey(ItemStack stack) {
        return new ModelKey(stack.getItem(), stack.getComponentsPatch().hashCode());
    }

    /** Clears cached model geometry (e.g. after a resource-pack / model reload). */
    private static void clearModelCache() {
        MODEL_CACHE.clear();
        FLAT_ITEM_MODELS.clear();
    }

    private static void ensureModelCacheClearer() {
        if (!modelCacheClearerRegistered) {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) {
                ((ReloadableResourceManager) mc.getResourceManager()).registerReloadListener(MODEL_CACHE_CLEARER);
                modelCacheClearerRegistered = true;
            }
        }
    }

    /** RenderPipelines carry their own blend state — no flush/state juggling
     *  (GuiGraphicsExtractor has no flush() in 26.1.2). */
    public static void blitTextured(GuiGraphicsExtractor gg, Identifier tex, int x, int y, int w, int h) {
        blitTextured(gg, tex, x, y, w, h, w, h);
    }

    /** Variant carrying the texture's real pixel size: the convenience blit
     *  treats the source UV window as w×h, so an enlarged draw (gold_item.png
     *  32x24 at ~169x127) would push UV past 1.0. The 12-arg overload keeps
     *  UV = [0,1] and uses width/height as the free-form target size. */
    public static void blitTextured(GuiGraphicsExtractor gg, Identifier tex, int x, int y, int w, int h, int texW, int texH) {
        gg.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, 0F, 0F, w, h, texW, texH, texW, texH);
    }

    /** Sprite-sheet variant: draws a UV window (u,v,uw,vh) of a texW x texH
     *  texture with an ARGB tint (26.x blit takes the tint as its last arg;
     *  the decoupled pipeline carries the blend state itself). */
    public static void blitTextured(GuiGraphicsExtractor gg, Identifier tex, int x, int y, int w, int h,
                                    int u, int v, int uw, int vh, int texW, int texH, int tint) {
        gg.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, u, v, w, h, uw, vh, texW, texH, tint);
    }

    public static void fill(GuiGraphicsExtractor gg, int x0, int y0, int x1, int y1, int color) {
        gg.fill(x0, y0, x1, y1, color);
    }

    public static void fillGradient(GuiGraphicsExtractor gg, int x0, int y0, int x1, int y1, int c0, int c1) {
        gg.fillGradient(x0, y0, x1, y1, c0, c1);
    }

    public static void scissor(GuiGraphicsExtractor gg, int x, int y, int w, int h) {
        gg.enableScissor(x, y, x + w, y + h);
    }

    public static void scissorDisable(GuiGraphicsExtractor gg) {
        gg.disableScissor();
    }

    /** No-op: decoupled pipelines own their blend state. */
    public static void setBlendNormal(GuiGraphicsExtractor gg) {
    }

    /** No-op: GuiGraphicsExtractor has no flush() in 26.1.2. */
    public static void flush(GuiGraphicsExtractor gg) {
    }

    /** 26.x previews composite through the PIP pipeline, clipped to their
     *  own rect (the offer preview can never spill onto the corner UI), so
     *  there is no in-frame preview depth to clear. Documented no-op so the
     *  cross-platform op set stays aligned with the legacy eras. */
    public static void clearDepth(GuiGraphicsExtractor gg) {
    }

    /** 26.1.2 screens blur via the extractBlurredBackground stratum hook;
     *  the facade forwards to it. */
    public static void renderBlurredBackground(GuiGraphicsExtractor gg) {
        gg.blurBeforeThisStratum();
    }

    /** 2D item icon centred at (x, y), scaled (16px per block unit), with
     *  per-item model-bounds centering (mirrors IconListTools.renderGuiItem). */
    public static void renderItem2D(LivingEntity entity, GuiGraphicsExtractor guiGraphics, ItemStack itemStack,
                                    float pX, float pY, float scale) {
        if (itemStack == null || itemStack.isEmpty() || entity == null) return;
        int seed = (int) (entity.getUUID().getLeastSignificantBits() & 0x7FFFFFFFL);
        // Measure the model's true extents and offset the draw so every
        // item's visual centre lands on the same pixel.
        Minecraft mc = Minecraft.getInstance();
        float offsetX = 0;
        float offsetY = 0;
        if (mc != null) {
            ensureModelCacheClearer();
            ModelKey key = modelKey(itemStack);
            CachedModel cached = MODEL_CACHE.get(key);
            if (cached != null) {
                offsetX = cached.offsetX();
                offsetY = cached.offsetY();
            } else {
                try {
                    TrackingItemStackRenderState tracked = new TrackingItemStackRenderState();
                    mc.getItemModelResolver().updateForLiving(tracked, itemStack, ItemDisplayContext.GUI, entity);
                    AABB bounds = tracked.getModelBoundingBox();
                    float ox, oy;
                    if (bounds != null) {
                        // The 16x16 GUI icon pins the model origin at (8,8) with
                        // 1 block unit = 16px, so the visual centre lands at
                        // (8 + 16*centreX, 8 + 16*centreY); negate to pin (pX,pY).
                        ox = -16F * (float) ((bounds.minX + bounds.maxX) * 0.5D) - 8F;
                        oy = -16F * (float) ((bounds.minY + bounds.maxY) * 0.5D) - 8F;
                    } else {
                        // Some flat items (armour leggings etc.) report no bounds;
                        // centre the top-left-anchored 16px GUI draw instead of
                        // letting it float to the bottom-right of the target.
                        ox = -8F;
                        oy = -8F;
                    }
                    offsetX = ox;
                    offsetY = oy;
                    // The 2D draw resolves its own (glint-animated) state via
                    // guiGraphics.item(); store the REAL geometry alongside so
                    // the 3D path can reuse this cache entry without mis-fitting
                    // (span/center measured per bounds, defaults when absent).
                    float span = (bounds != null)
                            ? (float) Math.max(bounds.getXsize(), Math.max(bounds.getYsize(), bounds.getZsize()))
                            : 1.0F;
                    float cx = (bounds != null) ? (float) ((bounds.minX + bounds.maxX) * 0.5D) : 0.0F;
                    float cy = (bounds != null) ? (float) ((bounds.minY + bounds.maxY) * 0.5D) : 0.0F;
                    float cz = (bounds != null) ? (float) ((bounds.minZ + bounds.maxZ) * 0.5D) : 0.0F;
                    MODEL_CACHE.put(key, new CachedModel(ox, oy, tracked, span, cx, cy, cz));
                } catch (Throwable ignored) {
                    // Best-effort measurement; centre so icons never drift bottom-right.
                    offsetX = -8F;
                    offsetY = -8F;
                }
            }
        }
        // Anchor-relative scale: translate to the target pixel first, then
        // apply the scale so that drawing at (0,0) lands at the original (pX,pY).
        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().translate(pX, pY);
        if (scale != 1.0F) guiGraphics.pose().scale(scale, scale);
        guiGraphics.pose().translate(offsetX, offsetY);
        guiGraphics.item(entity, itemStack, 0, 0, seed);
        guiGraphics.pose().popMatrix();
    }

    /** 3D rotating item preview (drag-to-rotate). The raw quaternion from
     *  {@link ItemDrag3D} passes through unchanged — the drag scheme works in
     *  quaternion space. Decoupled path re-routes through the PIP renderer
     *  (Icon3DRenderState) to restore 1.21.1's drag-to-spin. */
    public static void renderItem3D(GuiGraphicsExtractor guiGraphics, ItemStack item, LivingEntity player,
                                    int cx, int cy, Quat rotation, float scale) {
        if (item == null || item.isEmpty() || player == null) {
            return;
        }
        // Shader-mod fallback (Iris/Oculus): their shader packs ignore custom
        // rendering paths; degrade to the 2D icon so the preview never renders
        // black/blank under shaders (see docs/SHADER-COMPAT.md).
        if (!supports3D()) {
            renderItem2D(player, guiGraphics, item, cx, cy, scale);
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }

        // Vanilla-class preview conventions (Forge 26.x carries no
        // mod-specific strategies — the mod-version matrix admits no built
        // mods here; the same-id entity rule further down covers any
        // convention-following mod generically). Spawn eggs first (26.x
        // getType is static), then humanoid armor on a shared stand, then
        // generic summon containers. All degrade to the flat icon when their
        // render state produces nothing usable.
        if (item.getItem() instanceof SpawnEggItem) {
            EntityType<?> eggType = null;
            try {
                eggType = SpawnEggItem.getType(item);
            } catch (Throwable ignored) {
                eggType = null;
            }
            if (eggType != null) {
                renderEntityPreviewForType(guiGraphics, item, player, cx, cy, rotation, scale,
                        eggType, entityDataOf(item));
                return;
            }
        }
        Equippable equippable = item.get(DataComponents.EQUIPPABLE);
        if (equippable != null && HUMANOID_ARMOR_SLOTS.contains(equippable.slot())) {
            renderArmorStandPreview(guiGraphics, item, player, cx, cy, rotation, scale, equippable.slot());
            return;
        }
        EntityType<?> summon = summonContainerEntityType(item);
        if (summon != null) {
            renderEntityPreviewForType(guiGraphics, item, player, cx, cy, rotation, scale, summon, null);
            return;
        }

        int textureSize = Math.max(1, Math.round(16.0F * scale));

        ensureModelCacheClearer();
        ModelKey key = modelKey(item);
        CachedModel cached = MODEL_CACHE.get(key);
        TrackingItemStackRenderState trackedState;
        float modelSpan;
        float modelCenterX;
        float modelCenterY;
        float modelCenterZ;
        if (cached != null && cached.trackedState() != null) {
            // Reuse cached geometry + render state. The bounding box is static
            // for a given item+components; reusing trackedState may freeze the
            // glint phase on the dragged 3D preview (cosmetic trade-off).
            trackedState = cached.trackedState();
            modelSpan = cached.span();
            modelCenterX = cached.cx();
            modelCenterY = cached.cy();
            modelCenterZ = cached.cz();
        } else {
            // TrackingItemStackRenderState registers a model identity so the PIP
            // renderer caches the texture across frames (matches OversizedItemRenderer).
            trackedState = new TrackingItemStackRenderState();
            ItemModelResolver resolver = mc.getItemModelResolver();
            resolver.updateForLiving(trackedState, item, ItemDisplayContext.GUI, player);
            AABB bounds = trackedState.getModelBoundingBox();
            if (bounds != null) {
                modelSpan = (float) Math.max(bounds.getXsize(), Math.max(bounds.getYsize(), bounds.getZsize()));
                modelCenterX = (float) ((bounds.minX + bounds.maxX) * 0.5D);
                modelCenterY = (float) ((bounds.minY + bounds.maxY) * 0.5D);
                modelCenterZ = (float) ((bounds.minZ + bounds.maxZ) * 0.5D);
            } else {
                // Flat items may report no bounds; a unit model keeps them centred.
                modelSpan = 1.0F;
                modelCenterX = 0.0F;
                modelCenterY = 0.0F;
                modelCenterZ = 0.0F;
            }
            if (cached == null) {
                float ox = (bounds != null) ? (-16F * (float) ((bounds.minX + bounds.maxX) * 0.5D) - 8F) : -8F;
                float oy = (bounds != null) ? (-16F * (float) ((bounds.minY + bounds.maxY) * 0.5D) - 8F) : -8F;
                MODEL_CACHE.put(key, new CachedModel(ox, oy, trackedState, modelSpan, modelCenterX, modelCenterY, modelCenterZ));
            }
        }

        // Same-id entity convention (generic, no per-mod code): a flat-sprite
        // item whose registry id is also an entity type draws the entity —
        // vanilla kin (minecarts, armor stand, end crystal, item frames,
        // painting, trident) and modded summonables. Food items never match:
        // raw food ids collide with their animal entities (raw
        // chicken/chicken) and the item is the truth.
        if (isFlatSpriteItem(mc, item, player)) {
            EntityType<?> convention = conventionEntityType(item);
            if (convention != null) {
                renderEntityPreviewForType(guiGraphics, item, player, cx, cy, rotation, scale, convention, null);
                return;
            }
        }

        // (cx, cy) is the TOP-LEFT of the preview square (all 26.x callers
        // pass top-left; the PIP renderer centres the model inside it).
        // Forge's Icon3DRenderState carries Euler angles (degrees) instead of
        // the common Quat; decompose in the same XYZ order the PIP renderer
        // applies (mulPose X, then Y, then Z) so the visual matches the
        // decoupled reference.
        Quaternionf jq = new Quaternionf(rotation.x(), rotation.y(), rotation.z(), rotation.w()).normalize();
        Vector3f euler = jq.getEulerAnglesXYZ(new Vector3f());
        float rotXDeg = euler.x * (180.0F / (float) Math.PI);
        float rotYDeg = euler.y * (180.0F / (float) Math.PI);
        float rotZDeg = euler.z * (180.0F / (float) Math.PI);

        Icon3DRenderState pipState = new Icon3DRenderState(
                trackedState,
                rotXDeg,
                rotYDeg,
                rotZDeg,
                textureSize,
                modelSpan,
                modelCenterX,
                modelCenterY,
                modelCenterZ,
                cx,
                cy,
                cx + textureSize,
                cy + textureSize);

        guiGraphics.getRenderState().addPicturesInPictureState(pipState);
    }

    /** Whether 3D item previews (PIP / drag-to-rotate / TACZ guns) can render
     *  in this client. Only shader-pack mods (Iris/Oculus) force the 2D
     *  fallback; Modern UI (modernui) deliberately does NOT: it officially
     *  renders vanilla-GUI-system mods unchanged, so 3D previews stay 3D
     *  under it. If a future Modern UI release is proven to break the 3D
     *  path, add it to isShaderModActive() (docs/MODERN-UI-COMPAT.md). */
    public static boolean supports3D() {
        if (!logged3DEnvironment) {
            logged3DEnvironment = true;
            CsgoBox.LOGGER.info("[csgobox] 3D preview env: supports3D={} iris={} oculus={} modernui={}",
                    !isShaderModActive(), ModList.isLoaded("iris"),
                    ModList.isLoaded("oculus"), ModList.isLoaded("modernui"));
        }
        return !isShaderModActive();
    }

    /** True when Iris (NeoForge/Fabric) or Oculus (Forge) is installed: their
     *  shader packs intercept/ignore custom GUI 3D paths, so 3D previews
     *  degrade to 2D for correctness (probabilities/gameplay unaffected).
     *  Modern UI is intentionally excluded — see supports3D(). */
    private static boolean isShaderModActive() {
        return ModList.isLoaded("iris") || ModList.isLoaded("oculus");
    }

    // ============= Generic entity previews (spawn eggs / armor / summon cards / same-id entities) =============

    /** Humanoid equipment slots the armor-stand preview covers; animal armor
     *  (BODY slot) keeps its own renderer and is not covered. */
    private static final Set<EquipmentSlot> HUMANOID_ARMOR_SLOTS =
            Set.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);

    /** Item ids whose same-named entity must never become the 3D preview:
     *  escape hatch for convention collisions (L4 finds one, add its
     *  {@code namespace:path} item id). Empty today. */
    private static final Set<String> ENTITY_CONVENTION_DENYLIST = Set.of();

    /** Parked client-side preview entities, cached per entity-type id (the
     *  model resources are heavy and renderers animate against the entity).
     *  Not added to the client world — the PIP renderer submits their render
     *  state explicitly. */
    // Strong map on purpose: a WeakHashMap keyed by the freshly-built registry-id
    // string drops every entry on the next GC, and the next frame then silently
    // recreates a fresh entity (default pose = static), so the inspect animation
    // drove an instance that was no longer the rendered one. Bounded by the
    // number of entity types ever previewed; stale-level entries are replaced on
    // first use after a world change (see entityPreviewInstance).
    private static final Map<String, Entity> ENTITY_PREVIEW_INSTANCES = new HashMap<>();

    /** Flat-sprite verdict per cached model key. 26.x bakes display
     *  transforms inside the item model resolver, so the probe runs on a
     *  FIXED-context render state (identity transform for generated sprites —
     *  a thin axis there means the item model is a plane). Cleared on
     *  resource reloads together with {@link #MODEL_CACHE}. */
    private static final Map<ModelKey, Boolean> FLAT_ITEM_MODELS = new HashMap<>();

    private static boolean isFlatSpriteItem(Minecraft mc, ItemStack item, LivingEntity player) {
        ensureModelCacheClearer();
        ModelKey key = modelKey(item);
        Boolean cached = FLAT_ITEM_MODELS.get(key);
        if (cached != null) {
            return cached;
        }
        boolean flat;
        try {
            TrackingItemStackRenderState state = new TrackingItemStackRenderState();
            mc.getItemModelResolver().updateForLiving(state, item, ItemDisplayContext.FIXED, player);
            AABB bounds = state.getModelBoundingBox();
            flat = bounds != null
                    && Math.min(bounds.getXsize(), Math.min(bounds.getYsize(), bounds.getZsize())) <= 1.0E-3F;
        } catch (Throwable ignored) {
            flat = false;
        }
        FLAT_ITEM_MODELS.put(key, flat);
        return flat;
    }

    /** Resolves the same-id entity convention: an entity type sharing the
     *  flat-sprite item's registry id means the 3D form lives on the entity
     *  (modded summonables and vanilla kin: minecarts, armor stand, end
     *  crystal, item frames, painting, trident). Food items never match (raw
     *  chicken vs. the chicken entity — the food item is the truth), and the
     *  denylist overrides everything. Null when the convention does not
     *  apply. */
    private static EntityType<?> conventionEntityType(ItemStack item) {
        Identifier itemId = BuiltInRegistries.ITEM.getKey(item.getItem());
        if (ENTITY_CONVENTION_DENYLIST.contains(itemId.toString())
                || item.get(DataComponents.FOOD) != null) {
            return null;
        }
        return BuiltInRegistries.ENTITY_TYPE.containsKey(itemId)
                ? BuiltInRegistries.ENTITY_TYPE.getValue(itemId) : null;
    }

    /** Generic summon-container convention: any item whose
     *  {@code minecraft:block_entity_data} stores an {@code EntityType}
     *  string that resolves to a registered entity (the Superb Warfare
     *  summon-crate convention; SBW itself has no Forge 26.x build, but the
     *  convention stays generic for anything reusing it under any
     *  namespace). Type-only — the block entity tag is deliberately not
     *  loaded onto the preview entity (block-entity fields are not entity
     *  save data). Null when the convention does not apply. */
    static EntityType<?> summonContainerEntityType(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        try {
            TypedEntityData<net.minecraft.world.level.block.entity.BlockEntityType<?>> data =
                    stack.get(DataComponents.BLOCK_ENTITY_DATA);
            if (data == null || !data.contains("EntityType")) {
                return null;
            }
            Identifier typeId = data.getUnsafe().getString("EntityType")
                    .map(Identifier::tryParse)
                    .orElse(null);
            return typeId != null && BuiltInRegistries.ENTITY_TYPE.containsKey(typeId)
                    ? BuiltInRegistries.ENTITY_TYPE.getValue(typeId) : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Spawn-egg entity data ({@code minecraft:entity_data}); applied onto
     *  the shared preview instance every frame so variants (sheep colour,
     *  villager profession) render like the spawned mob. */
    private static TypedEntityData<EntityType<?>> entityDataOf(ItemStack stack) {
        try {
            return stack.get(DataComponents.ENTITY_DATA);
        } catch (Throwable t) {
            return null;
        }
    }

    /** The parked client-side preview entity for an entity type, or null on
     *  failure. Cached per entity type: the model resources are heavy and
     *  the renderer animates against the entity, so a per-frame allocation
     *  would reload/replay every frame. Not added to the client world — it
     *  is rendered via the dispatcher's render-state path. Parked at the
     *  origin facing the viewer; the entity stays fully visible (invisible
     *  starves LivingEntity renderers to an empty render state) and the PIP
     *  render target has no ground plane, so there is no shadow pass to
     *  suppress. */
    private static Entity entityPreviewInstance(EntityType<?> type, Level level) {
        String cacheKey = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
        Entity cached = ENTITY_PREVIEW_INSTANCES.get(cacheKey);
        if (cached != null && cached.level() == level) {
            return cached;
        }
        try {
            Entity entity = type.create(level, EntitySpawnReason.COMMAND);
            if (entity == null) {
                return null;
            }
            entity.setPos(0.0D, 0.0D, 0.0D);
            entity.setYRot(180.0F);
            entity.setXRot(0.0F);
            ENTITY_PREVIEW_INSTANCES.put(cacheKey, entity);
            return entity;
        } catch (Throwable t) {
            CsgoBox.LOGGER.warn("Entity preview instance creation failed for {}",
                    BuiltInRegistries.ENTITY_TYPE.getKey(type), t);
            return null;
        }
    }

    /** Entity-based 3D preview through the entity PIP renderer: extract the
     *  render state, fit the entity's AABB into the preview square with the
     *  shared 0.82 rule, and degrade to the flat icon when the entity or its
     *  render state produces nothing usable. {@code spawnData} (spawn-egg
     *  {@code entity_data}) is re-applied every frame — the instance is
     *  reused per type, so per-frame application keeps two variants of one
     *  type from bleeding into each other. The AABB stands in for the
     *  v1_21_1-style vertex probe: the submit-node renderer exposes no
     *  vertex funnel, and entity AABBs match their model extents. Rotation
     *  goes through the same Quat-to-Euler decomposition the item state
     *  uses (Forge's PIP consumes degrees, see the item branch). */
    private static void renderEntityPreviewForType(GuiGraphicsExtractor gg, ItemStack stack, LivingEntity player,
                                                   int cx, int cy, Quat rotation, float scale, EntityType<?> type,
                                                   TypedEntityData<EntityType<?>> spawnData) {
        Entity entity = entityPreviewInstance(type, player.level());
        markPreviewMob(entity);
        if (entity == null) {
            renderItem2D(player, gg, stack, cx, cy, scale);
            return;
        }
        // Entity.load resets yHeadRot/yBodyRot on every call; identical
        // spawn data must not be re-applied per frame or it wipes the
        // inspect animation state (see previewSpawnDataChanged).
        if (spawnData != null
                && previewSpawnDataChanged(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString(), stack)) {
            try {
                spawnData.loadInto(entity);
                // loadInto may carry Pos/Rotation from the save tag; keep the
                // parked pose so the preview never drifts.
                entity.setPos(0.0D, 0.0D, 0.0D);
                entity.setYRot(180.0F);
                entity.setXRot(0.0F);
            } catch (Throwable t) {
                CsgoBox.LOGGER.warn("Entity preview: applying the spawn tag failed; keeping the default look", t);
            }
        }
        AABB bounds = entity.getBoundingBox();
        float span = (float) Math.max(bounds.getXsize(), Math.max(bounds.getYsize(), bounds.getZsize()));
        if (span <= 0.0F) {
            renderItem2D(player, gg, stack, cx, cy, scale);
            return;
        }
        EntityRenderState state;
        try {
            state = Minecraft.getInstance().getEntityRenderDispatcher().extractEntity(entity, 1.0F);
        } catch (Throwable t) {
            CsgoBox.LOGGER.warn("Entity preview render state extraction failed; degrading to the flat icon", t);
            state = null;
        }
        if (state == null) {
            renderItem2D(player, gg, stack, cx, cy, scale);
            return;
        }
        int textureSize = Math.max(1, Math.round(16.0F * scale));
        Quaternionf jq = new Quaternionf(rotation.x(), rotation.y(), rotation.z(), rotation.w()).normalize();
        Vector3f euler = jq.getEulerAnglesXYZ(new Vector3f());
        gg.getRenderState().addPicturesInPictureState(new EntityIcon3DRenderState(
                state,
                euler.x * (180.0F / (float) Math.PI),
                euler.y * (180.0F / (float) Math.PI),
                euler.z * (180.0F / (float) Math.PI),
                textureSize, span,
                0.0F, (float) bounds.getYsize() * 0.5F, 0.0F,
                cx, cy, cx + textureSize, cy + textureSize));
    }

    // ===================== Living-entity preview animation (inspect) =====================

    /** Walk stride the living previews step at while the inspect animation
     *  is on: vanilla feeds min(distance * 4, 1) as the target walk speed,
     *  so 0.6 reads as a steady stroll (1.0 is the renderer's clamp). */
    private static final float PREVIEW_MOB_WALK_TARGET = 0.6F;

    /** Walk/attack alternation: segments last 3-7 s (client ticks) and
     *  alternate walking and attacking, starting with a walk segment. */
    private static final int PREVIEW_MOB_PHASE_BASE = 60;
    private static final int PREVIEW_MOB_PHASE_SPREAD = 81;
    /** Vanilla swing duration for a fresh entity (6 ticks at the default
     *  attack speed; getCurrentSwingDuration() is private on 1.18.2/1.20.1,
     *  hence the constant). */
    private static final int PREVIEW_MOB_SWING_DURATION = 6;
    /** Head lunge depth while swinging (degrees, downward dip) - the
     *  visible attack for quadrupeds whose model has no arm-swing pose. */
    private static final float PREVIEW_MOB_LUNGE_DEG = 30.0F;

    /** The living entity the current look-item screen's drop renders as
     *  (null = none): set by the entity preview path on every render and
     *  cleared when a screen opens, so the inspect button only arms for mob
     *  drops. Armor stands stay posed (equipment display) and vehicles are
     *  not living, so neither arms it. */
    private static LivingEntity previewMobInstance;

    /** True while the inspect-button animation mode is on. */
    private static boolean previewMobAnim;

    /** Remembers the screen's living-entity preview (no-op otherwise). */
    private static void markPreviewMob(Entity entity) {
        if (entity instanceof LivingEntity living && !(entity instanceof ArmorStand)) {
            if (PREVIEW_MOB_MARK_LOG.add(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString())) {
                CsgoBox.LOGGER.info("[csgobox] preview mob armed: type={}",
                        BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
            }
            previewMobInstance = living;
        }
    }

    /** True when the screen's current item renders as a living entity. */
    public static boolean hasPreviewMobInstance() {
        return previewMobInstance != null;
    }

    /** Drops the marker; called when a look-item screen opens so a previous
     *  screen's mob can never arm the button for a non-mob item. */
    public static void clearPreviewMobInstance() {
        previewMobInstance = null;
    }

    /** The spawn-data payload last applied to a shared instance, per entity
     *  type id (see {@link #previewSpawnDataChanged}). */
    private static final Map<String, ItemStack> ENTITY_PREVIEW_LOADED = new HashMap<>();

    /** One-shot INFO log guards for the inspect-animation diagnostics. */
    private static final Set<String> PREVIEW_MOB_MARK_LOG = new HashSet<>();
    private static final Set<String> PREVIEW_MOB_DRIVE_LOG = new HashSet<>();

    /** True when {@code stack}'s spawn data differs from the payload last
     *  applied to the shared instance of this entity type (and records the
     *  new one). Vanilla {@code Entity.load} resets the head/body rotation
     *  from the tag's Rotation on every call, so re-applying identical data
     *  per frame would wipe the inspect animation state; identical stacks
     *  skip the reload. Different stacks (two variants of one type in one
     *  frame) still reload, keeping the per-frame variant correctness. */
    private static boolean previewSpawnDataChanged(String typeKey, ItemStack stack) {
        ItemStack loaded = ENTITY_PREVIEW_LOADED.get(typeKey);
        if (loaded != null && ItemStack.matches(loaded, stack)) {
            return false;
        }
        ENTITY_PREVIEW_LOADED.put(typeKey, stack.copy());
        return true;
    }

    /** One-shot resolve of the private per-mob animation state fields (sheep
     *  graze clock, creeper swell pair); null on failure and the action
     *  degrades to a standing pause. The enderman stare drives public API
     *  only. The IllagerSpell enum is protected on this era too: the
     *  spellcaster casting call and its two constants resolve reflectively
     *  (degrading silently in production where the names remap away). */
    private static final Field SHEEP_EAT_TICK = resolveField(Sheep.class, "eatAnimationTick");
    private static final Field CREEPER_SWELL = resolveField(Creeper.class, "swell");
    private static final Field CREEPER_OLD_SWELL = resolveField(Creeper.class, "oldSwell");
    private static final Method SPELL_CAST_METHOD = resolveSpellCastMethod();
    private static final Object SPELL_FANGS = resolveSpellConstant("FANGS");
    private static final Object SPELL_NONE = resolveSpellConstant("NONE");

    /** The IllagerSpell enum is protected on this era: the spellcaster
     *  casting call and its two constants resolve reflectively (degrading
     *  silently in production where the names remap away). */
    private static Method resolveSpellCastMethod() {
        try {
            for (Method method : SpellcasterIllager.class.getDeclaredMethods()) {
                if (method.getName().equals("setIsCastingSpell") && method.getParameterCount() == 1
                        && method.getParameterTypes()[0].isEnum()) {
                    method.setAccessible(true);
                    return method;
                }
            }
        } catch (Throwable t) {
            CsgoBox.LOGGER.warn("[csgobox] preview mob: spellcaster casting call unreachable, the action degrades", t);
        }
        return null;
    }

    /** Enum constant by name off the casting method's parameter type. */
    private static Object resolveSpellConstant(String name) {
        Method method = SPELL_CAST_METHOD;
        if (method == null) {
            return null;
        }
        try {
            for (Object constant : method.getParameterTypes()[0].getEnumConstants()) {
                if (constant instanceof Enum<?> enumConstant && enumConstant.name().equals(name)) {
                    return constant;
                }
            }
        } catch (Throwable t) {
            CsgoBox.LOGGER.warn("[csgobox] preview mob: spell constant {} unreachable, the action degrades", name);
        }
        return null;
    }

    /** Reflection call of setIsCastingSpell; silent on a failed resolve. */
    private static void setSpellQuietly(SpellcasterIllager illager, Object spell) {
        Method method = SPELL_CAST_METHOD;
        if (method != null && spell != null) {
            try {
                method.invoke(illager, spell);
            } catch (Throwable ignored) {
            }
        }
    }

    /** Special per-mob action ids for the phase pool. */
    private static final int PREVIEW_SPECIAL_GRAZE = 1;
    private static final int PREVIEW_SPECIAL_SWELL = 2;
    private static final int PREVIEW_SPECIAL_STARE = 3;
    private static final int PREVIEW_SPECIAL_SPELL = 4;

    /** Resolves a private field once for the preview drives; null on
     *  failure, never throws. */
    private static Field resolveField(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (Throwable t) {
            CsgoBox.LOGGER.warn("[csgobox] preview mob: {}.{} unreachable, its special action degrades",
                    owner.getSimpleName(), name);
            return null;
        }
    }

    /** Reflection write for the private animation-state fields; silent on a
     *  failed resolve (the action then just does not play). */
    private static void setIntQuietly(Field field, LivingEntity living, int value) {
        if (field != null) {
            try {
                field.setInt(living, value);
            } catch (Throwable ignored) {
            }
        }
    }

    /** The mob-specific special action this instance can play (0 = none):
     *  walk/attack stay universal, the special joins the phase pool per mob
     *  class. */
    private static int previewMobSpecial(LivingEntity living) {
        if (living instanceof Sheep) {
            return SHEEP_EAT_TICK != null ? PREVIEW_SPECIAL_GRAZE : 0;
        }
        if (living instanceof Creeper) {
            return CREEPER_SWELL != null && CREEPER_OLD_SWELL != null ? PREVIEW_SPECIAL_SWELL : 0;
        }
        if (living instanceof EnderMan) {
            return PREVIEW_SPECIAL_STARE;
        }
        if (living instanceof SpellcasterIllager) {
            return PREVIEW_SPECIAL_SPELL;
        }
        return 0;
    }

    /** The mainstream entity-animation libraries (GeckoLib, AzureLib): their
     *  entities override swing() to fire their own named attack animations
     *  (triggerAnim), so the attack segment calls the public vanilla swing()
     *  on them instead of driving the swing fields directly. Walk/idle need
     *  no bridge at all: GeoEntityRenderer reads walkAnimation and GeoEntity
     *  reads tickCount (both bytecode-verified), which the drive advances. */
    private static final Class<?> GECKOLIB_ANIMATABLE =
            resolveAnimatableClass("software.bernie.geckolib.animatable.GeoAnimatable");
    private static final Class<?> AZURELIB_ANIMATABLE =
            resolveAnimatableClass("mod.azure.azurelib.animatable.GeoAnimatable");

    /** Quietly resolves an animation-library marker class; null when the
     *  library is absent (the normal case - no log noise). */
    private static Class<?> resolveAnimatableClass(String name) {
        try {
            return Class.forName(name);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** True for entities from the mainstream animation libraries. */
    private static boolean isAnimatableEntity(LivingEntity living) {
        return (GECKOLIB_ANIMATABLE != null && GECKOLIB_ANIMATABLE.isInstance(living))
                || (AZURELIB_ANIMATABLE != null && AZURELIB_ANIMATABLE.isInstance(living));
    }

    /** The creeper swell ramp for a position in its segment: rise to 24
     *  (0.86 of the fuse threshold), hold, fall - the scale pump and the
     *  white overlay read it through getSwelling(); no explosion, ever. */
    private static int previewMobSwellCurve(int pos, int len) {
        if (pos < 20) {
            return pos * 24 / 20;
        }
        if (len - pos < 20) {
            return (len - pos) * 24 / 20;
        }
        return 24;
    }

    /** Inspect-button animation tick, driven by the look-item screen: every
     *  cached living preview instance walks in place (the same easing the
     *  vanilla movement code applies), slowly looks around with its head and
     *  advances its age so breathing/idle cycles run; modded animatable
     *  models keyed on tickCount keep playing too. The entity itself is
     *  never ticked: only these render-consumed fields change, so no AI,
     *  particles or sounds can leak out of a GUI preview. */
    public static void tickPreviewMobAnimations(boolean enabled) {
        previewMobAnim = enabled;
        if (!enabled) {
            return;
        }
        for (Entity entity : ENTITY_PREVIEW_INSTANCES.values()) {
            if (!(entity instanceof LivingEntity living) || entity instanceof ArmorStand) {
                continue;
            }
            if (PREVIEW_MOB_DRIVE_LOG.add(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString())) {
                CsgoBox.LOGGER.info("[csgobox] preview mob anim: type={} (first drive)",
                        BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
            }
            drivePreviewMobAnimation(living);
        }
    }

    /** Parks the living previews again (look-item screen closed): stop the
     *  stride and drop the head to neutral so the next screen opens on the
     *  frozen statue instead of a mid-step frame. */
    public static void resetPreviewMobAnimation() {
        previewMobAnim = false;
        for (Entity entity : ENTITY_PREVIEW_INSTANCES.values()) {
            if (!(entity instanceof LivingEntity living) || entity instanceof ArmorStand) {
                continue;
            }
            living.walkAnimation.stop();
            living.swinging = false;
            living.swingTime = 0;
            living.oAttackAnim = 0.0F;
            living.attackAnim = 0.0F;
        // clear the per-mob special states (frozen poses would persist)
        if (living instanceof Sheep) {
            setIntQuietly(SHEEP_EAT_TICK, living, 0);
        } else if (living instanceof Creeper) {
            setIntQuietly(CREEPER_OLD_SWELL, living, 0);
            setIntQuietly(CREEPER_SWELL, living, 0);
        } else if (living instanceof EnderMan enderman) {
            enderman.setTarget(null);
        } else if (living instanceof SpellcasterIllager spellcaster) {
            setSpellQuietly(spellcaster, SPELL_NONE);
        }
            living.yHeadRotO = living.yBodyRot;
            living.yHeadRot = living.yBodyRot;
            living.xRotO = 0.0F;
            living.setXRot(0.0F);
        }
    }

    /** Irregular but stateless schedule source: an integer mix (golden-ratio
     *  seed + xorshift rounds) that is deterministic per segment index, so
     *  the walk/attack alternation stores no schedule state. */
    private static int previewMobMix(int seed) {
        int h = seed * -1640531527;
        h ^= h >>> 16;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        return h & 0x7FFFFFFF;
    }

    /** One client tick of the inspect animation for a preview instance:
     *  variable-length segments (3-7 s) alternate walking (stride + head
     *  wander) and attacking (planted stance, vanilla swing state machine,
     *  head lunge). The phase is derived from the entity's own tick count,
     *  so no schedule state is stored; the entity is still never ticked. */
    private static void drivePreviewMobAnimation(LivingEntity living) {
        int t = living.tickCount;
        int seg = 0;
        int len;
        while (true) {
            len = PREVIEW_MOB_PHASE_BASE + previewMobMix(seg) % PREVIEW_MOB_PHASE_SPREAD;
            if (t < len) {
                break;
            }
            t -= len;
            seg++;
        }
        int special = previewMobSpecial(living);
        int roll = previewMobMix(seg) % 10;
        int phase = special == 0
                ? (roll < 5 ? 0 : 1)
                : (roll < 3 ? 0 : roll < 6 ? 1 : special);
        boolean attacking = phase == 1;
        int posInSeg = t;

        // Vanilla swing state machine, mirrored from LivingEntity.tick (the
        // preview entity is never ticked, so this is the only driver): the
        // attackAnim ramp 0 -> 5/6 -> 0 is what the humanoid attack pose and
        // the held-item layers consume.
        living.oAttackAnim = living.attackAnim;
        if (living.swinging) {
            living.swingTime++;
            if (living.swingTime >= PREVIEW_MOB_SWING_DURATION) {
                living.swingTime = 0;
                living.swinging = false;
            }
        } else {
            living.swingTime = 0;
        }
        living.attackAnim = (float) living.swingTime / (float) PREVIEW_MOB_SWING_DURATION;

        living.tickCount++;
        float clock = (float) living.tickCount * 0.03F;
        living.walkAnimation.update(phase == 0 ? PREVIEW_MOB_WALK_TARGET : 0.0F, 0.4F, living.isBaby() ? 3.0F : 1.0F);

        if (phase == 1) {
            // Attack segment: plant the stance, face the viewer, and swing at
            // a per-segment cadence. The head dips with each swing - the only
            // visible attack for quadrupeds (no arm-swing pose in vanilla).
            int cadence = 9 + previewMobMix(seg * 31 + 7) % 6;
            if (posInSeg % cadence == 0) {
                if (isAnimatableEntity(living)) {
                    // GeckoLib/AzureLib entities override swing() to fire
                    // their own named attack animations (triggerAnim) - the
                    // public vanilla call reaches the override with no
                    // per-mod code; vanilla mobs keep the field drive.
                    living.swing(InteractionHand.MAIN_HAND);
                } else if (!living.swinging || living.swingTime >= PREVIEW_MOB_SWING_DURATION / 2) {
                    living.swingTime = -1;
                    living.swinging = true;
                    living.swingingArm = InteractionHand.MAIN_HAND;
                }
            }
            living.yHeadRotO = living.yHeadRot;
            living.yHeadRot = living.yBodyRot;
            living.xRotO = living.getXRot();
            living.setXRot(PREVIEW_MOB_LUNGE_DEG * (float) Math.sin((double) living.attackAnim * Math.PI));
        } else if (phase == 0) {
            living.yHeadRotO = living.yHeadRot;
            living.yHeadRot = living.yBodyRot
                    + (float) Math.sin((double) clock) * 35.0F
                    + (float) Math.sin((double) clock * 2.7D + 1.3D) * 12.0F;
            living.xRotO = living.getXRot();
            living.setXRot((float) Math.sin((double) clock * 1.6D + 0.7D) * 12.0F);
        } else {
            // Special segment: planted stance and a neutral head; each action
            // drives its own state (sheep graze clock, creeper swell pair,
            // enderman stare flag, spellcaster casting spell).
            living.yHeadRotO = living.yHeadRot;
            living.yHeadRot = living.yBodyRot;
            living.xRotO = living.getXRot();
            living.setXRot(0.0F);
            if (phase == PREVIEW_SPECIAL_GRAZE) {
                // SheepModel sinks the head off getHeadEatPositionScale (the
                // fur layer mirrors it): ramp in 40 -> 36, hold the pose, and
                // ramp out to 0 - vanilla EatGrassGoal values, stateless.
                int eat = posInSeg < 4 ? 40 - posInSeg
                        : posInSeg < len - 4 ? 36
                        : Math.max(0, len - 1 - posInSeg);
                setIntQuietly(SHEEP_EAT_TICK, living, eat);
            } else if (phase == PREVIEW_SPECIAL_SWELL) {
                // CreeperRenderer pumps the scale and flashes the white
                // overlay off getSwelling(): rise, hold, fall - no fuse.
                setIntQuietly(CREEPER_OLD_SWELL, living, previewMobSwellCurve(Math.max(0, posInSeg - 1), len));
                setIntQuietly(CREEPER_SWELL, living, previewMobSwellCurve(posInSeg, len));
            } else if (phase == PREVIEW_SPECIAL_STARE && living instanceof EnderMan enderman) {
                // The stare pose reads model.creepy, which EndermanRenderer
                // copies from isCreepy(): the public setTarget sets the synced
                // flag (null clears it); no ticking, so no scream sound.
                enderman.setTarget(Minecraft.getInstance().player);
            } else if (phase == PREVIEW_SPECIAL_SPELL && living instanceof SpellcasterIllager spellcaster) {
                // SPELLCASTING arm pose reads isCastingSpell() from the synced
                // data; the call goes through reflection (the enum is protected).
                setSpellQuietly(spellcaster, SPELL_FANGS);
            }
        }
        // Transition-based flags clear outside their windows (idempotent
        // writes; a mob matches at most one special).
        if (living instanceof EnderMan enderman && phase != PREVIEW_SPECIAL_STARE) {
            enderman.setTarget(null);
        } else if (living instanceof SpellcasterIllager spellcaster && phase != PREVIEW_SPECIAL_SPELL) {
            setSpellQuietly(spellcaster, SPELL_NONE);
        }
    }

    /** Armor preview on the shared armor stand: equip the piece (its
     *  {@code EQUIPPABLE} slot), extract the render state, and draw. GUI
     *  never draws the worn model — it lives in the equipment layers the
     *  stand's renderer carries. Unlike the probed legacy path there is no
     *  emptiness detection: pieces whose equipment assets render nothing
     *  degrade visually to a bare stand (acceptable — Forge 26.x armor is
     *  vanilla-class today). */
    private static void renderArmorStandPreview(GuiGraphicsExtractor gg, ItemStack stack, LivingEntity player,
                                                int cx, int cy, Quat rotation, float scale, EquipmentSlot slot) {
        if (entityPreviewInstance(EntityTypes.ARMOR_STAND, player.level()) instanceof ArmorStand stand) {
            stand.setItemSlot(slot, stack);
            // The stand instance is cached per type, so the call below parks
            // and extracts the same (already equipped) stand.
            renderEntityPreviewForType(gg, stack, player, cx, cy, rotation, scale, EntityTypes.ARMOR_STAND, null);
            return;
        }
        renderItem2D(player, gg, stack, cx, cy, scale);
    }
}
