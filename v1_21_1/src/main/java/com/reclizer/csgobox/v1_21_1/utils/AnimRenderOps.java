package com.reclizer.csgobox.v1_21_1.utils;

import com.mojang.blaze3d.platform.GlConst;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.reclizer.csgobox.utils.Quat;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.bedrock.BedrockCube;
import com.tacz.guns.client.model.bedrock.BedrockCubeBox;
import com.tacz.guns.client.model.bedrock.BedrockCubePerFace;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.SpellcasterIllager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Matrix4fStack;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.slf4j.Logger;

import it.unimi.dsi.fastutil.floats.FloatArrayList;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.ArrayList;
import java.util.Collections;
import java.util.WeakHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Single per-platform adaptation point for animation rendering primitives.
 * Screens and logic helpers (IconListTools / GuiItemMove) must call ONLY
 * through this class; version-varying render API lives here and nowhere else.
 * Mod-specific preview overrides are ordered strategies in
 * {@link ItemPreviewStrategies} whose bodies delegate back here, so the
 * era-specific pose plumbing keeps exactly one home. era: legacy
 */
public final class AnimRenderOps {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** TACZ (Timeless & Classics Guns: Zero) is an optional dependency; every
     *  TACZ class reference below must be reachable only after a ModList gate,
     *  or clients without the mod crash with NoClassDefFoundError (see the
     *  2.0.1 TerminalScreen rendering crash). */
    /** Package-private so the TACZ preview strategy gates on the same id. */
    static final String TACZ_MOD_ID = "tacz";
    /** Superb Warfare (卓越前线) item namespace. Unlike TACZ no ModList gate
     *  is needed: the 3D branch only matches a registered item's namespace,
     *  and such an item cannot exist unless the mod is loaded. No Superb
     *  Warfare class is ever referenced. */
    private static final String SUPERB_WARFARE_MOD_ID = "superbwarfare";
    /** Set once the first supports3D() call has logged the render environment. */
    private static boolean logged3DEnvironment;
    private static final PoseStack REUSABLE_POSE_STACK = new PoseStack();
    /** Scratch quaternion for the drag rotation: {@code mulPose} copies the
     *  value immediately, so a single static instance is safe on the render
     *  thread and avoids one allocation per 3D item per frame. */
    private static final Quaternionf SCRATCH_QUAT = new Quaternionf();
    private static final RandomSource SCRATCH_RANDOM = RandomSource.create();
    /** Sentinel cached for models without usable geometry (custom renderers
     *  or degenerate bounds): distinguishes "not computed" from "nothing to
     *  centre", so those models are not rescanned every frame. */
    private static final Object NO_CENTER = new Object();
    /** Largest projected size of a preview square, as a fraction of its edge:
     *  leaves margin for the drag-rotation tilt so an oversized model cannot
     *  overflow the square (same 0.82 constant as the 26.x PIP path). */
    private static final float PREVIEW_FILL = 0.82F;
    /** SBW-only fit boost on top of {@link #PREVIEW_FILL}: the measured OBB
     *  largest axis contains diagonal slack the rotated silhouette never
     *  fills, so long guns and vehicle models read too small at the shared
     *  rule (user-tuned 1.3x). Only applied when the probe produced real
     *  bounds. */
    private static final float SBW_FIT_BOOST = 1.3F;
    /** Cached model-space centre of each TACZ gun's default-pose bounding
     *  box (block units). Gun displays are singletons per gun id and are
     *  replaced on resource reload, so a WeakHashMap lets old models go. */
    private static final Map<BedrockGunModel, Vector3f> GUN_MODEL_CENTERS = new WeakHashMap<>();
    /** Cached largest-axis span (block units) of each TACZ gun model, keyed
     *  and lifecycle-managed like {@link #GUN_MODEL_CENTERS}. */
    private static final Map<BedrockGunModel, Float> GUN_MODEL_SPANS = new WeakHashMap<>();
    /** One-shot INFO per gun id (2026-11-22 fabric centre report): the
     *  computed envelope centre/span of the first preview of each gun, so a
     *  wrong-centre runtime report carries hard numbers to compare with the
     *  offline model walk. */
    private static final Set<String> GUN_PREVIEW_LOGGED = new HashSet<>();

    /** Cached centre + largest-axis span (block units, in the drag-rotation
     *  space) of each baked item model's geometry. Models are stable
     *  instances owned by the model manager and are replaced on resource
     *  reload, so a WeakHashMap lets old models go. */
    /** Package-private: shared by the preview strategies for bounds checks.
     *  Framing centre + drag hub + largest-axis span. Entity previews keep
     *  the two pivots separate (2026-11-21, Alex's Mobs): the framing centre
     *  is the envelope-box centre so the model sits symmetric in the preview
     *  square, while the drag hub is the reconstructed cube mass centre — a
     *  single point cannot serve both (the median hub sat up to ~22% of the
     *  span off the envelope centre and clipped the far side; the envelope
     *  hub rotated around thin appendages instead of the body). Paths that
     *  never pivot separately pass the centre as the hub via {@link #of}. */
    record ModelBounds(Vector3f center, Vector3f hub, float span) {
        private static ModelBounds of(Vector3f center, float span) {
            return new ModelBounds(center, center, span);
        }
    }

    private static final Map<BakedModel, Object> ITEM_MODEL_BOUNDS = new WeakHashMap<>();

    /** Cached centre + largest-axis span (block units, display-transform
     *  space) of each Superb Warfare item's FIXED-context render, measured
     *  once per baked model by probing the real render chain with a vertex
     *  recorder — GeckoLib guns draw through a custom renderer, so there are
     *  no BakedQuads to scan (the vanilla {@link #ITEM_MODEL_BOUNDS} path
     *  sees nothing) and the emitted vertices are the only ground truth.
     *  Keyed like {@link #ITEM_MODEL_BOUNDS}: models are stable instances
     *  replaced on resource reload, so a WeakHashMap lets old models go. */
    private static final Map<BakedModel, Object> FIXED_CONTEXT_BOUNDS = new WeakHashMap<>();

    /** Resolved vehicle icon textures for the Superb Warfare summon crate
     *  preview, keyed by the EntityType id path. Mirrors SBW's own decorator
     *  cache: both hit and miss are stored, so an id without an icon texture
     *  costs one resource lookup per session, not one per frame. Values are
     *  a {@link ResourceLocation} or the {@link #NO_ICON} sentinel. */
    private static final Map<String, Object> VEHICLE_ICON_CACHE = new HashMap<>();

    /** Cache sentinel for "SBW ships no icon texture for this vehicle id". */
    private static final Object NO_ICON = new Object();

    /** Shared client-side entities backing entity-based 3D previews (Superb
     *  Warfare vehicles, spawn-egg mobs, the armor stand), keyed by entity
     *  type id string. The renderer binds animation state to the entity
     *  instance, so the same instance must be reused across frames; entity
     *  types are registry constants, and a resource reload swaps renderer
     *  state harmlessly (the entity is not in the world). */
    // Strong map on purpose: a WeakHashMap keyed by the freshly-built registry-id
    // string drops every entry on the next GC, and the next frame then silently
    // recreates a fresh entity (default pose = static), so the inspect animation
    // drove an instance that was no longer the rendered one. Bounded by the
    // number of entity types ever previewed; stale-level entries are replaced on
    // first use after a world change (see entityPreviewInstance).
    private static final Map<String, Entity> ENTITY_PREVIEW_INSTANCES = new HashMap<>();

    /** Probe-measured centre + span of each preview entity type's rendered
     *  geometry (block units), keyed by EntityType — same lifecycle as
     *  {@link #FIXED_CONTEXT_BOUNDS}: registry constants live for the whole
     *  session, and a NO_CENTER sentinel suppresses re-probing renderers that
     *  emit nothing usable. */
    private static final Map<EntityType<?>, Object> ENTITY_PREVIEW_BOUNDS = new WeakHashMap<>();

    /** Probe-measured bounds of each armor piece's preview on the shared
     *  armor stand, keyed by the item's registry id — the stand's entity type
     *  is constant but its worn model varies per piece, so the entity-type
     *  key cannot be reused. Same sentinel semantics as
     *  {@link #ENTITY_PREVIEW_BOUNDS}. */
    private static final Map<String, Object> ARMOR_PREVIEW_BOUNDS = new HashMap<>();

    /** Create SchematicItem.loadSchematic(Level, ItemStack), resolved by
     *  reflection on first use (never at class-init, and only requested after
     *  the item-id gate proved the mod is loaded — zero Create compile-time
     *  references, same discipline as the SBW preview). Null after a failed
     *  resolution with the flag suppressing retries. */
    private static Method CREATE_LOAD_SCHEMATIC;
    private static boolean CREATE_LOAD_SCHEMATIC_RESOLVED;

    /** Create's own schematic preview plumbing, resolved by reflection as a
     *  group on first use after the item-id gate proved the mod is loaded
     *  (zero Create/catnip compile-time references): the fake schematic level
     *  ({@code catnip.levelWrappers.SchematicLevel} — a mutable Level the
     *  vanilla {@code StructureTemplate.placeInWorld} fills), Create's own
     *  {@code SchematicRenderer} (per-RenderType buffer cache tessellated
     *  with neighbour-face culling, plus live block-entity rendering with an
     *  errored-BE mask) and its shared output buffer ({@code DefaultSuper
     *  RenderTypeBuffer.getInstance()} whose {@code draw()} flushes the
     *  recorded geometry to screen under the caller's model-view transform).
     *  Null members after a failed resolution, with the flag suppressing
     *  retries. */
    private static Constructor<?> CREATE_SCHEMATIC_LEVEL_CTOR;
    private static Constructor<?> CREATE_RENDERER_CTOR;
    private static Method CREATE_RENDERER_UPDATE;
    private static Method CREATE_RENDERER_RENDER;
    private static Method CREATE_BUFFER_GET_INSTANCE;
    private static Method CREATE_BUFFER_DRAW;
    private static Method CREATE_LEVEL_GET_RANDOM;
    private static Method CREATE_LEVEL_GET_BLOCK_ENTITIES;
    private static boolean CREATE_RENDERER_RESOLVED;

    /** A live Create renderer wired to a fake schematic level, plus the
     *  structure's block-space centre/span for the shared auto-fit rule. */
    private record CreateSchematicBundle(Object level, Object renderer, Vector3f center, float span) {
    }

    /** The most recent schematic preview bundle, keyed by ItemStack hash (the
     *  schematic stack only carries the file name). A different stack evicts
     *  the previous bundle; the NO_CENTER sentinel caches "unrenderable"
     *  (file missing, over budget) so the degrade path does not re-read the
     *  file every frame. */
    private static Object createSchematicBundleCache;
    private static int createSchematicBundleKey;

    /** Hard cap on tessellated blocks per schematic capture: above this the
     *  preview degrades to the flat icon (a whole-city schematic would cost
     *  seconds to capture and far too much memory; replay itself stays cheap
     *  regardless of size). 32768 per maintainer decision 2026-11-14 — covers
     *  the real-world 11k-block test schematic with ample headroom; the
     *  capture is one-shot and the per-frame replay is a vertex copy, so the
     *  budget bounds memory, not frame cost. */
    private static final int MAX_CAPTURE_BLOCKS = 32768;

    /** Screen.renderBlurredBackground is protected in this MC version and
     *  depends on the screen's own {@code minecraft} instance, so the facade
     *  bridges it via reflection (invokes the very same method). */
    private static final Method SCREEN_RENDER_BLURRED_BACKGROUND;

    static {
        Method m;
        try {
            m = Screen.class.getDeclaredMethod("renderBlurredBackground", float.class);
            m.setAccessible(true);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Unable to resolve Screen.renderBlurredBackground", e);
        }
        SCREEN_RENDER_BLURRED_BACKGROUND = m;
    }

    private AnimRenderOps() {
    }

    /** Immediate-mode blit. Forces SRC_ALPHA: the 8-arg blit inherits whatever
     *  blend func is current, so translucent textures (spot glow, lens
     *  vignette) would otherwise render as hard opaque discs. */
    public static void blitTextured(GuiGraphics gg, ResourceLocation tex, int x, int y, int w, int h) {
        gg.flush();
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(770, 771, 1, 771);
        gg.blit(tex, x, y, 0, 0, w, h, w, h);
    }

    /** Variant carrying the texture's real pixel size: the convenience blit
     *  treats the source UV window as w×h, so an enlarged draw (gold_item.png
     *  32x24 at ~169x127) would push UV past 1.0. The 11-arg overload keeps
     *  UV = [0,1] and uses width/height as the free-form target size. */
    public static void blitTextured(GuiGraphics gg, ResourceLocation tex, int x, int y, int w, int h, int texW, int texH) {
        gg.flush();
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(770, 771, 1, 771);
        gg.blit(tex, x, y, w, h, 0F, 0F, texW, texH, texW, texH);
    }

    /** Sprite-sheet variant: draws a UV window (u,v,uw,vh) of a texW x texH
     *  texture with an ARGB tint applied via shader color. Legacy immediate
     *  mode does NOT auto-reset the shader color, so this facade restores
     *  (1,1,1,1) itself before returning — callers never leak the tint. */
    public static void blitTextured(GuiGraphics gg, ResourceLocation tex, int x, int y, int w, int h,
                                    int u, int v, int uw, int vh, int texW, int texH, int tint) {
        gg.flush();
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(770, 771, 1, 771);
        RenderSystem.setShaderColor(((tint >> 16) & 0xFF) / 255F,
                ((tint >> 8) & 0xFF) / 255F, (tint & 0xFF) / 255F, ((tint >> 24) & 0xFF) / 255F);
        gg.blit(tex, x, y, w, h, u, v, uw, vh, texW, texH);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
    }

    public static void fill(GuiGraphics gg, int x0, int y0, int x1, int y1, int color) {
        gg.fill(x0, y0, x1, y1, color);
    }

    public static void fillGradient(GuiGraphics gg, int x0, int y0, int x1, int y1, int c0, int c1) {
        gg.fillGradient(x0, y0, x1, y1, c0, c1);
    }

    public static void scissor(GuiGraphics gg, int x, int y, int w, int h) {
        gg.enableScissor(x, y, x + w, y + h);
    }

    public static void scissorDisable(GuiGraphics gg) {
        gg.disableScissor();
    }

    public static void setBlendNormal(GuiGraphics gg) {
        gg.flush();
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(770, 771, 1, 771);
    }

    public static void flush(GuiGraphics gg) {
        gg.flush();
    }

    /** Clears the depth buffer so UI drawn after a 3D preview paints over
     *  it: in-frame 3D previews write real depth (z>0), and every later
     *  z=0 fill/text fails the depth test against those pixels (2026-11-19
     *  terminal report: the tilted offer item covered the wear bar and the
     *  inspect capsule). Flushes the shared buffer first so the clear lands
     *  between the preview's pixels and the overlay — anything still queued
     *  (the background, the watermark circle) belongs UNDER the preview and
     *  must not be re-drawn on top of it after the clear. */
    public static void clearDepth(GuiGraphics gg) {
        gg.flush();
        RenderSystem.clear(GlConst.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
    }

    public static void renderBlurredBackground(Screen screen, GuiGraphics gg, float partialTicks) {
        try {
            SCREEN_RENDER_BLURRED_BACKGROUND.invoke(screen, partialTicks);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("Unable to render blurred background", e);
        }
    }

    /** 2D item icon centred at (x, y), scaled (16px per block unit).
     *  Custom-renderer models ({@code builtin/entity} — chest-like items
     *  drawn by a BEWLR) route to the FIXED-context 3D render instead of
     *  the flat path below: the item-frame perspective is the established
     *  convention for them here. Skipped under shader mods (Iris/Oculus)
     *  — their GUI interception breaks custom 3D paths, and the flat path
     *  cannot draw them either. */
    /** Whether the item model is the vanilla barrier model — the upstream
     *  "deliberately ships no icon" marker: every quad carries the barrier
     *  sprite. Compared by sprite so parent-chain tricks survive; cached
     *  per model (the quad walk is not free). */
    private static final Map<BakedModel, Boolean> BARRIER_ICON_MODELS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static boolean isBarrierModel(BakedModel model) {
        return BARRIER_ICON_MODELS.computeIfAbsent(model, m -> {
            try {
                RandomSource random = RandomSource.create();
                List<BakedQuad> quads = new ArrayList<>(m.getQuads(null, null, random));
                for (Direction direction : Direction.values()) {
                    quads.addAll(m.getQuads(null, direction, random));
                }
                if (quads.isEmpty()) {
                    return false;
                }
                for (BakedQuad quad : quads) {
                    if (!quad.getSprite().contents().name().toString().equals("minecraft:item/barrier")) {
                        return false;
                    }
                }
                return true;
            } catch (Throwable t) {
                return false;
            }
        });
    }

    public static void renderItem2D(LivingEntity entity, GuiGraphics gg, ItemStack stack, float x, float y, float scale) {
        BakedModel model = Minecraft.getInstance().getItemRenderer().getModel(stack, entity.level(), entity, 0);
        // Mod-specific overrides first: the first matching strategy owns the
        // cell (TACZ suppress while a display is loading, SBW vehicle icon).
        // Unmatched items fall through to the generic custom-renderer / flat
        // flow below; see ItemPreviewStrategies for the chain.
        for (ItemPreviewStrategies.ItemPreviewStrategy strategy : ItemPreviewStrategies.ALL) {
            if (strategy.matches(stack) && strategy.render2D(entity, gg, stack, x, y, scale)) {
                return;
            }
        }
        if (model.isCustomRenderer() && !isShaderModActive()) {
            renderFixedContextModel3D(gg, stack, entity, x, y, Quat.IDENTITY, scale, null);
            return;
        }
        // Upstream "deliberately no icon" markers: an item model inherited
        // from the vanilla barrier model (the red prohibition sign) means
        // the mod ships no inventory icon for this item — a block's item
        // form then renders its real block model instead, matching the 3D
        // pass's G0 block-backing rule. The vanilla barrier itself keeps
        // its sign: its block model has no geometry and the resolver
        // falls back to the item model.
        if (stack.getItem() instanceof BlockItem && isBarrierModel(model)) {
            BakedModel blockModel = blockBackedModel(stack, model);
            if (blockModel != model) {
                renderFixedContextModel3D(gg, stack, entity, x, y, Quat.IDENTITY, scale, null, blockModel);
                return;
            }
        }
        renderItem2DFlat(entity, gg, stack, x, y, scale);
    }

    /** The flat icon body of {@link #renderItem2D} (never re-delegates to a
     *  3D path, so the FIXED-context failure fallback cannot loop).
     *  Package-private: the degrade tail of the entity-preview strategies. */
    static void renderItem2DFlat(LivingEntity entity, GuiGraphics gg, ItemStack stack, float x, float y, float scale) {
        BakedModel model = Minecraft.getInstance().getItemRenderer().getModel(stack, entity.level(), entity, 0);
        PoseStack pose = gg.pose();
        pose.pushPose();
        pose.translate(x, y, 2F);
        pose.translate(8.0F * scale, 8.0F * scale, 0.0F);
        // Real depth — vanilla GuiGraphics.renderItem keeps its Z scale at 16
        // (on its z=150 layer), while a Z scale of 0 here collapsed every face
        // onto one depth plane and let the model bake order win the depth test:
        // hollow models with an open front (kaleidoscope_tavern bar cabinet)
        // painted the interior backplate over the shelving and the 2D icon read
        // as a sealed cube (2026-11-18 terminal strip report). The Z scale stays
        // at 1 rather than vanilla's 16 — depth ordering only needs relative
        // order, and a ~±0.3px slab around the z=2 layer keeps every screen
        // convention intact: card fills at z=0 stay under the icon, the big 3D
        // previews at z=100+ stay over it (a full-depth slab here would dip
        // below the fills and interleave with those previews).
        pose.scale(1.0F, -1.0F, 1F);
        pose.scale(16.0F * scale, 16.0F * scale, 1F);
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        boolean flat = !model.usesBlockLight();
        if (flat) Lighting.setupForFlatItems();
        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.mul(pose.last().pose());
        RenderSystem.applyModelViewMatrix();
        PoseStack renderStack = REUSABLE_POSE_STACK;
        renderStack.setIdentity();
        Minecraft.getInstance().getItemRenderer().render(stack, ItemDisplayContext.GUI, false,
                renderStack, bufferSource, 15728880, OverlayTexture.NO_OVERLAY, model);
        bufferSource.endBatch();
        RenderSystem.enableDepthTest();
        if (flat) Lighting.setupFor3DItems();
        pose.popPose();
        modelViewStack.popMatrix();
        RenderSystem.applyModelViewMatrix();
    }

    /** 3D rotating item preview (drag-to-rotate). The raw quaternion from
     *  {@link ItemDrag3D} passes through unchanged — the drag scheme works in
     *  quaternion space. TACZ guns bypass the vanilla ItemRenderer (its GUI
     *  branch only draws the flat slot texture); see {@link #renderGunModel3D}. */
    public static void renderItem3D(GuiGraphics gg, ItemStack item, LivingEntity player,
                                    int cx, int cy, Quat rotation, float scale) {
        if (item == null || item.isEmpty() || player == null) return;
        // Shader-mod fallback (Iris/Oculus): degrade 3D previews (incl. TACZ
        // guns) to the 2D icon so nothing renders blank under shader packs
        // (see docs/SHADER-COMPAT.md).
        if (!supports3D()) {
            renderItem2D(player, gg, item, cx, cy, scale);
            return;
        }
        // Mod-specific previews: the first matching strategy owns the item
        // (TACZ gun model, SBW vehicle entity, SBW FIXED-context gun).
        // Unmatched items fall through to the vanilla baked-model path below;
        // see ItemPreviewStrategies for the chain and the TACZ class-loading
        // gate discipline.
        for (ItemPreviewStrategies.ItemPreviewStrategy strategy : ItemPreviewStrategies.ALL) {
            if (strategy.matches(item)) {
                strategy.render3D(gg, item, player, cx, cy, rotation, scale);
                return;
            }
        }
        BakedModel model = Minecraft.getInstance().getItemRenderer().getModel(item, player.level(), player, 0);
        // Custom renderers ({@code builtin/entity} BEWLR items — MG golem
        // holders drawing their stored golem, vanilla shield/trident, ...)
        // expose no quads to measure and have no fixed baked geometry: render
        // them under the FIXED context exactly like the generic 2D branch
        // does, so the probe-based auto-fit and centring apply to whatever
        // the BEWLR draws.
        if (model.isCustomRenderer()) {
            renderFixedContextModel3D(gg, item, player, cx, cy, rotation, scale, null);
            return;
        }
        // Block-backed flat sprites (mod food plates and the like): the item
        // model is a generated plane while the real 3D form only exists on the
        // placed block, so the preview renders the block's model instead.
        // Vanilla block items parent their block model and never enter this
        // fallback.
        BakedModel renderModel = blockBackedModel(item, model);
        // Same-id entity convention: a flat-sprite item whose registry id is
        // also an entity type draws the entity — modded summonables (Immersive
        // Aircraft vehicles, the Kaleidoscope Cookery scarecrow) and vanilla
        // kin (minecarts, armor stand, end crystal, item frames, painting,
        // trident) keep their 3D form out of the item model. Food items never
        // match: raw food ids collide with their animal entities (raw
        // chicken/chicken, raw cod/cod) and the item is the truth there. The
        // entity preview degrades to the flat sprite on empty probes, so a
        // bad pair costs nothing.
        EntityType<?> convention = conventionEntityType(item);
        if (PREVIEW_PATH_LOG.add(BuiltInRegistries.ITEM.getKey(item.getItem()).toString())) {
            LOGGER.info("[csgobox] preview path: item={} flat={} blockBacked={} custom={} entityCandidate={} convention={}",
                    BuiltInRegistries.ITEM.getKey(item.getItem()), isFlatSpriteModel(model),
                    renderModel != model, model.isCustomRenderer(),
                    BuiltInRegistries.ENTITY_TYPE.containsKey(BuiltInRegistries.ITEM.getKey(item.getItem())),
                    convention);
        }
        if (renderModel == model && isFlatSpriteModel(model) && convention != null) {
            renderEntityPreviewForType(gg, item, player, cx, cy, rotation, scale, convention);
            return;
        }
        // Auto-fit: a model that projects beyond the preview square (oversized
        // datapack models) is shrunk to stay inside it; normal items (span <=
        // 1 block) keep the caller's scale unchanged.
        float fitScale = fitScaleFor(itemModelSpan(renderModel), scale);
        PoseStack pose = gg.pose();
        pose.pushPose();
        pose.translate(cx, cy, 100.0F);
        pose.translate(8.0F * scale, 8.0F * scale, 0.0F);
        pose.scale(1.0F, -1.0F, 1.0F);
        SCRATCH_QUAT.set(rotation.x(), rotation.y(), rotation.z(), rotation.w()).normalize();
        pose.mulPose(SCRATCH_QUAT);
        Lighting.setupForEntityInInventory();
        pose.scale(16.0F * fitScale, 16.0F * fitScale, 16.0F * fitScale);
        // Rotate around the model's geometric centre, not the model origin:
        // block-style models span 0..1 with the origin at a corner, so an
        // origin pivot would swing the box around a point outside its body.
        // Same convention as the TACZ gun branch (and the 26.x PIP renderer):
        // translate(-centre) in block units AFTER the 16px scale.
        Vector3f center = previewPivot(renderModel, renderModel != model);
        if (center != null) {
            pose.translate(-center.x, -center.y, -center.z);
        }
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        boolean flat = !renderModel.usesBlockLight();
        if (flat) Lighting.setupForFlatItems();
        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.mul(pose.last().pose());
        RenderSystem.applyModelViewMatrix();
        PoseStack renderStack = REUSABLE_POSE_STACK;
        renderStack.setIdentity();
        // GUI is the only preview perspective: the 2026-11-14 display-probe
        // (render FIXED when the model defines display.fixed) was withdrawn
        // after L4 — hand-made Blockbench exports (our box/terminal) and
        // every block.json chain carry display.fixed for item frames, so the
        // probe flipped them to the frame perspective, oversized and
        // unrotated. Frame context stays with the dedicated strategy branches
        // (SBW gun, BEWLR reroute), which probe in the context they render.
        Minecraft.getInstance().getItemRenderer().render(item, ItemDisplayContext.GUI, false,
                renderStack, bufferSource, 15728880, OverlayTexture.NO_OVERLAY, renderModel);
        bufferSource.endBatch();
        RenderSystem.enableDepthTest();
        if (flat) Lighting.setupFor3DItems();
        pose.popPose();
        modelViewStack.popMatrix();
        RenderSystem.applyModelViewMatrix();
    }

    /** Centre of the RENDERED geometry of a baked item model, in drag-rotation
     *  space (block units), or {@code null} for models without quads. Pushes
     *  every quad vertex through the exact {@code ItemRenderer.render} chain
     *  ({@code ItemTransform.apply} + {@code -0.5} shift); the pivot is the
     *  raw-vertex-extent box centre mapped through that chain — the
     *  transformed AABB midpoint drifts off the visual centre once the GUI
     *  isometric rotation mixes asymmetric axes (2026-11-14
     *  aeronautics:wooden_propeller: ~0.1 block off-hub orbit; 2026-11-16
     *  Kaleidoscope Cookery: the interim vertex median is density-sensitive,
     *  see {@link #modelBounds}). Computed once per model. */
    private static Vector3f itemModelCenter(BakedModel model) {
        return modelBounds(model).center();
    }

    /** Largest-axis span (block units, in the drag-rotation space) of the
     *  model's rendered geometry, used by {@link #fitScaleFor} to auto-shrink
     *  oversized models so they stay inside the preview square. */
    private static float itemModelSpan(BakedModel model) {
        return modelBounds(model).span();
    }

    /** Pivot policy of the default 3D path, by measured model kind:
     *  block-backed previews (mod food plates) pivot on the base-footprint
     *  cuboid ({@link #blockBackedCenter}), generated-sprite slabs pivot on
     *  the vanilla slab's own cuboid centre ({@link #spriteCenter}), and
     *  real 3D models keep the raw-geometry box centre
     *  ({@link #itemModelCenter}).
     *  Null when the model has no measurable geometry. */
    private static Vector3f previewPivot(BakedModel model, boolean blockBacked) {
        if (blockBacked) {
            return blockBackedCenter(model);
        }
        if (isFlatSpriteModel(model)) {
            return spriteCenter(model);
        }
        return itemModelCenter(model);
    }

    /** Pivot of a generated-sprite slab. The vanilla extrusion is the FULL
     *  0..1 plate — front/back span the whole canvas (transparent texels are
     *  simply not drawn) and the side quads sit on the silhouette — so the
     *  slab's own cuboid centre is the constant cube centre (0.5, 0.5, 0.5)
     *  regardless of the sprite, mapped through the same renderer matrix as
     *  every other pivot. Vertex statistics are wrong here: the side quads
     *  sit on the silhouette, so their counts skew the per-axis medians off
     *  the visual centre (offline span simulation, 2026-11-14:
     *  create:electron_tube lands 6 px above the sprite centre). The cube
     *  centre is also the vanilla-consistent pivot — it keeps the sprite at
     *  its icon position instead of re-centring the opaque pixels. */
    private static Vector3f spriteCenter(BakedModel model) {
        return guiRenderMatrix(model).transformPosition(new Vector3f(0.5F, 0.5F, 0.5F));
    }

    /** Per-model cache of {@link #blockBackedCenter} pivots — the cuboid
     *  rule needs a full vertex pass, computed once per baked block model
     *  (same lifetime model as {@link #ITEM_MODEL_BOUNDS}). */
    private static final Map<BakedModel, Object> BLOCK_BACKED_CENTERS = new WeakHashMap<>();

    /** Base-layer membership bound for {@link #blockBackedCenter}: the
     *  lowest vertex plane is exact in the baked quads, so the epsilon only
     *  absorbs float noise. */
    private static final float BASE_LAYER_EPSILON = 1.0E-3F;

    /** Pivot of a block-backed preview (mod food plates and the like): the
     *  model body is a complex food pile whose vertex statistics swing with
     *  the serving, but the base is a fixed-size plate — so the pivot is the
     *  centre of the bounding cuboid: X/Z from the base layer's footprint
     *  (the vertices at the lowest Y), Y from the midpoint of the full
     *  height. Vertex medians land inside the food pile (offline bake of
     *  kaleidoscope_cookery:zongzi_plate servings=4: 16 rotated food cubes
     *  contribute 384 vertices against the plate's 24, dragging the median
     *  ~1.5 px off the plate centre); the cuboid rides on the plate
     *  regardless of the food arrangement. Raw-space measure mapped through
     *  the same renderer matrix as {@link #modelBounds}; cached per model.
     *  Per axis, the footprint yields to the full-extent midpoint when the
     *  base covers under half that axis's extent (contact line: tavern
     *  stepladder/tap/brass_heart/holder, 2.6-7.4 px skew) or sits over
     *  1.5 px off it (overhang the base cannot represent:
     *  clay_pot_milk_tea, cold_roasted_meat); plates keep the footprint
     *  rule unchanged. */
    private static Vector3f blockBackedCenter(BakedModel model) {
        Object cached = BLOCK_BACKED_CENTERS.get(model);
        if (cached instanceof Vector3f center) {
            return center;
        }
        if (cached == NO_CENTER) {
            return null;
        }
        FloatArrayList xs = new FloatArrayList();
        FloatArrayList ys = new FloatArrayList();
        FloatArrayList zs = new FloatArrayList();
        collectRawVertices(model, xs, ys, zs);
        if (xs.isEmpty()) {
            BLOCK_BACKED_CENTERS.put(model, NO_CENTER);
            return null;
        }
        float minY = Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (int i = 0; i < ys.size(); i++) {
            float x = xs.getFloat(i);
            float y = ys.getFloat(i);
            float z = zs.getFloat(i);
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z);
        }
        float baseMinX = Float.MAX_VALUE, baseMaxX = -Float.MAX_VALUE;
        float baseMinZ = Float.MAX_VALUE, baseMaxZ = -Float.MAX_VALUE;
        for (int i = 0; i < ys.size(); i++) {
            if (ys.getFloat(i) <= minY + BASE_LAYER_EPSILON) {
                baseMinX = Math.min(baseMinX, xs.getFloat(i));
                baseMaxX = Math.max(baseMaxX, xs.getFloat(i));
                baseMinZ = Math.min(baseMinZ, zs.getFloat(i));
                baseMaxZ = Math.max(baseMaxZ, zs.getFloat(i));
            }
        }
        // The base plane is the visual footprint only along axes it actually
        // spans AND when it sits under the visual mass. Two fallback triggers
        // per axis (2026-11-16 catalog sweep of the whole Kaleidoscope
        // family): (1) the base covers under half the model's extent —
        // wall-leaning contact lines (tavern stepladder leans at z 14..16
        // against a z 0..16 body, 7.4 px skew; tap 3.8 px; brass_heart
        // pedestal 3.2 px; holder 2.6 px; blaze_lamb_chop z 0.17); (2) the
        // base midpoint sits over 1.5 px off the full-extent midpoint —
        // overhanging parts the base cannot represent (clay_pot_milk_tea
        // spout pulls the body 2.5 px left of its foot; cold_roasted_meat
        // bone 1.6 px). Plates and furniture bases span the model under the
        // mass and keep the footprint rule unchanged.
        float centerX = (baseMaxX - baseMinX) >= (maxX - minX) * 0.5F
                && Math.abs((baseMinX + baseMaxX) * 0.5F - (minX + maxX) * 0.5F) <= 1.5F / 16F
                ? (baseMinX + baseMaxX) * 0.5F : (minX + maxX) * 0.5F;
        float centerZ = (baseMaxZ - baseMinZ) >= (maxZ - minZ) * 0.5F
                && Math.abs((baseMinZ + baseMaxZ) * 0.5F - (minZ + maxZ) * 0.5F) <= 1.5F / 16F
                ? (baseMinZ + baseMaxZ) * 0.5F : (minZ + maxZ) * 0.5F;
        Vector3f center = new Vector3f(centerX,
                (minY + maxY) * 0.5F, centerZ);
        guiRenderMatrix(model).transformPosition(center);
        BLOCK_BACKED_CENTERS.put(model, center);
        return center;
    }

    /** Auto-fit scale for a model of {@code modelSpan} block units rendered
     *  into a preview square of {@code 16 * scale} px (1 block = 16 px at
     *  scale 1): models that project beyond the square are shrunk so their
     *  largest axis fills only PREVIEW_FILL of it; everything else keeps the
     *  caller's scale. Same shrink rule as the 26.x PIP path
     *  ({@code targetPixelSize * 0.82 / modelSpan} in Icon3DRenderState). */
    static float fitScaleFor(float modelSpan, float scale) {
        if (modelSpan <= 1.0F || modelSpan <= 0.0F) {
            return scale;
        }
        return scale * PREVIEW_FILL / modelSpan;
    }

    /** Sentinel bounds for models without usable geometry (custom renderers
     *  or degenerate quads): unit span keeps the caller's scale untouched.
     *  Package-private: entity-preview strategies gate on it. */
    static final ModelBounds NO_BOUNDS = ModelBounds.of(new Vector3f(), 1.0F);

    /** The exact model matrix {@code ItemRenderer.render} applies for the GUI
     *  context: the model's gui {@link ItemTransform} (translate(translation)
     *  -> rotationXYZ -> scale -> NeoForge right_rotation rotationXYZ) plus
     *  the renderer's {@code -0.5,-0.5,-0.5} shift, so 0..1 block geometry
     *  centres at origin. Shared by the pivot measures ({@link #modelBounds},
     *  {@link #spriteCenter}, {@link #blockBackedCenter}) — a pivot is only
     *  meaningful in the same space the geometry is drawn in. */
    private static Matrix4f guiRenderMatrix(BakedModel model) {
        ItemTransform gui = model.getTransforms().gui;
        Matrix4f renderer = new Matrix4f();
        if (gui != ItemTransform.NO_TRANSFORM) {
            renderer.translate(gui.translation.x(), gui.translation.y(), gui.translation.z());
            renderer.rotate(new Quaternionf().rotationXYZ(
                    (float) Math.toRadians(gui.rotation.x()),
                    (float) Math.toRadians(gui.rotation.y()),
                    (float) Math.toRadians(gui.rotation.z())));
            renderer.scale(gui.scale.x(), gui.scale.y(), gui.scale.z());
            // NeoForge 21.x appends a fourth apply() step after scale —
            // TransformationHelper.quatFromXYZ(rightRotation, degrees), i.e.
            // rotationXYZ in radians — and parses the display "right_rotation"
            // JSON key that vanilla 1.21.1 has no field for (bytecode-checked
            // on both). A pivot matrix without this step drops a rotation the
            // render chain performs, so models with a nonzero gui
            // right_rotation mis-centre and mis-fit on this platform only.
            // The preview renders leftHand=false, so no mirroring applies.
            renderer.rotate(new Quaternionf().rotationXYZ(
                    (float) Math.toRadians(gui.rightRotation.x()),
                    (float) Math.toRadians(gui.rightRotation.y()),
                    (float) Math.toRadians(gui.rightRotation.z())));
        }
        renderer.translate(-0.5F, -0.5F, -0.5F);
        return renderer;
    }

    /** Drag pivot + largest-axis span of the RENDERED geometry of a baked item
     *  model, in drag-rotation space (block units). Pushes every quad vertex
     *  through the exact {@code ItemRenderer.render} chain ({@code
     *  ItemTransform.apply} + {@code -0.5} shift); the pivot is the raw
     *  vertex-extent box centre mapped through that chain — the transformed
     *  AABB midpoint drifts for asymmetric models under the GUI isometric
     *  rotation (2026-11-14 aeronautics:wooden_propeller), while the span
     *  stays the AABB largest axis for the auto-fit. Computed once per model. */
    private static ModelBounds modelBounds(BakedModel model) {
        Object cached = ITEM_MODEL_BOUNDS.get(model);
        if (cached instanceof ModelBounds bounds) {
            return bounds;
        }
        if (cached == NO_CENTER) {
            return NO_BOUNDS;
        }
        // Custom renderers (TACZ guns routed elsewhere, special items) have no
        // quads to measure; cache the sentinel so they are not rescanned.
        if (model.isCustomRenderer()) {
            ITEM_MODEL_BOUNDS.put(model, NO_CENTER);
            return NO_BOUNDS;
        }
        Matrix4f renderer = guiRenderMatrix(model);
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        Vector3f p = new Vector3f();
        // Raw vertex coordinates feed the pivot box centre below: the
        // transformed AABB midpoint drifts off the visual centre for models
        // whose geometry is asymmetric about their origin — the GUI isometric
        // rotation mixes the uneven axes, so the midpoint of the rotated
        // cloud no longer sits on the body (2026-11-14:
        // aeronautics:wooden_propeller blades span x -13..29 over an
        // asymmetric seat, pivot drifted ~0.1 block off the hub and the
        // inspect-screen rotation visibly orbited). 2026-11-16 (Kaleidoscope
        // Cookery pack): the interim per-axis vertex MEDIAN is
        // density-sensitive — decorative zero-thickness planes (trash_can
        // handle and inner panels) and piled servings skew it 2-5 px off the
        // visual centre (trash_can z median 5.0 vs box centre 8.0), and hand
        // models list arbitrary face subsets so the skew is not predictable.
        // The raw-vertex-extent box centre is density-independent, equals the
        // midpoint for symmetric models, and rides the hub for the propeller
        // (its blade extents bracket the hub symmetrically).
        FloatArrayList rawX = new FloatArrayList();
        FloatArrayList rawY = new FloatArrayList();
        FloatArrayList rawZ = new FloatArrayList();
        collectRawVertices(model, rawX, rawY, rawZ);
        float rMinX = Float.MAX_VALUE, rMinY = Float.MAX_VALUE, rMinZ = Float.MAX_VALUE;
        float rMaxX = -Float.MAX_VALUE, rMaxY = -Float.MAX_VALUE, rMaxZ = -Float.MAX_VALUE;
        for (int i = 0; i < rawX.size(); i++) {
            float rx = rawX.getFloat(i), ry = rawY.getFloat(i), rz = rawZ.getFloat(i);
            rMinX = Math.min(rMinX, rx);
            rMinY = Math.min(rMinY, ry);
            rMinZ = Math.min(rMinZ, rz);
            rMaxX = Math.max(rMaxX, rx);
            rMaxY = Math.max(rMaxY, ry);
            rMaxZ = Math.max(rMaxZ, rz);
            p.set(rx, ry, rz);
            renderer.transformPosition(p);
            minX = Math.min(minX, p.x);
            minY = Math.min(minY, p.y);
            minZ = Math.min(minZ, p.z);
            maxX = Math.max(maxX, p.x);
            maxY = Math.max(maxY, p.y);
            maxZ = Math.max(maxZ, p.z);
        }
        if (rawX.isEmpty() || !(minX < maxX && minY < maxY && minZ < maxZ)) {
            ITEM_MODEL_BOUNDS.put(model, NO_CENTER);
            return NO_BOUNDS;
        }
        // Pivot on the raw-vertex-extent box centre (see above); largest axis
        // of the rendered bounding box stays the auto-fit metric (block units).
        Vector3f center = new Vector3f((rMinX + rMaxX) * 0.5F,
                (rMinY + rMaxY) * 0.5F, (rMinZ + rMaxZ) * 0.5F);
        renderer.transformPosition(center);
        ModelBounds bounds = ModelBounds.of(center,
                Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ)));
        ITEM_MODEL_BOUNDS.put(model, bounds);
        return bounds;
    }

    /** Per-axis median of a coordinate sample (mean of the two middle values
     *  on even counts). The fallback drag hub when an entity probe yields no
     *  reconstructable cube (see {@link #entityProbeHub}); item models use
     *  the raw envelope-box centre instead (hollow shells drag the median
     *  onto a wall). */
    private static float median(FloatArrayList values) {
        float[] sorted = values.toFloatArray();
        Arrays.sort(sorted);
        int n = sorted.length;
        return (n & 1) == 1 ? sorted[n / 2] : (sorted[n / 2 - 1] + sorted[n / 2]) * 0.5F;
    }

    /** Collects every quad vertex position of the model into per-axis lists
     *  (raw baked coords; block-format quads: 8 ints per vertex, position in
     *  slots 0-2). */
    private static void collectRawVertices(BakedModel model, FloatArrayList xs,
                                           FloatArrayList ys, FloatArrayList zs) {
        RandomSource random = RandomSource.create();
        for (Direction direction : Direction.values()) {
            collectQuadVertices(model.getQuads(null, direction, random), xs, ys, zs);
        }
        collectQuadVertices(model.getQuads(null, null, random), xs, ys, zs);
    }

    private static void collectQuadVertices(List<BakedQuad> quads, FloatArrayList xs,
                                            FloatArrayList ys, FloatArrayList zs) {
        for (BakedQuad quad : quads) {
            int[] vertices = quad.getVertices();
            for (int i = 0; i < 4; i++) {
                xs.add(Float.intBitsToFloat(vertices[i * 8]));
                ys.add(Float.intBitsToFloat(vertices[i * 8 + 1]));
                zs.add(Float.intBitsToFloat(vertices[i * 8 + 2]));
            }
        }
    }

    /** Flat-sprite verdict per baked item model, cached like
     *  {@link #ITEM_MODEL_BOUNDS}: true when the model's raw geometry is a
     *  stack of quads all planar along ONE shared axis — the generated
     *  {@code item/generated} sprite shape at any layer count — versus real
     *  3D geometry whose quads span multiple planar axes. 2026-11-14: the
     *  previous "one fully degenerate axis" test never fired, because
     *  vanilla generated sprites are not coplanar (each layer bakes a
     *  front+back quad pair 1/16 apart) — see {@link #measureFlatSprite}. */
    private static final Map<BakedModel, Boolean> FLAT_ITEM_MODELS = new WeakHashMap<>();

    /** True when the model's raw geometry is a generated-sprite slab (see
     *  {@link #measureFlatSprite}); custom renderers (no measurable quads)
     *  always answer false so they keep their dedicated handling. */
    private static boolean isFlatSpriteModel(BakedModel model) {
        if (model.isCustomRenderer()) {
            return false;
        }
        return FLAT_ITEM_MODELS.computeIfAbsent(model, AnimRenderOps::measureFlatSprite);
    }

    /** Whole-model slab shape of the model (raw, pre-display-transform):
     *  true when the entire geometry lies inside a 1/16-thick slab extruded
     *  along Z — the vanilla {@code item/generated} sprite shape. The
     *  generator ({@code ItemModelGenerator}) bakes every sprite layer as an
     *  EXTRUDED silhouette: front/back faces at z 7.5/8.5 (MIN_Z/MAX_Z
     *  bytecode constants) plus per-span edge faces on X/Y planes
     *  ({@code createSideElements}) — so both a zero-thickness test AND a
     *  "all quads share one normal axis" test misfire (the edge faces poison
     *  the shared axis; 2026-11-14 L4 logs: warship/zinc_ingot flat=false
     *  with per-quad verdict). The slab verdict is stable: the extrusion
     *  axis is always Z, edge faces never exceed the slab, and real 3D
     *  models (cubes: z span 1; propeller: z span >0.8) overshoot the
     *  1/16 bound. The wide-axis guard keeps a degenerate 1/16 cube from
     *  counting as a sprite. */
    private static boolean measureFlatSprite(BakedModel model) {
        RandomSource random = RandomSource.create();
        float[] minMax = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE,
                -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        boolean any = false;
        for (Direction direction : Direction.values()) {
            for (BakedQuad quad : model.getQuads(null, direction, random)) {
                accumulateQuad(quad.getVertices(), minMax);
                any = true;
            }
        }
        for (BakedQuad quad : model.getQuads(null, null, random)) {
            accumulateQuad(quad.getVertices(), minMax);
            any = true;
        }
        if (!any) {
            return false;
        }
        float spanX = minMax[3] - minMax[0];
        float spanY = minMax[4] - minMax[1];
        float spanZ = minMax[5] - minMax[2];
        return spanZ <= SPRITE_SLAB_MAX && (spanX > SPRITE_SLAB_MAX || spanY > SPRITE_SLAB_MAX);
    }

    /** The generated-sprite slab thickness bound: the vanilla extrusion is
     *  exactly 1/16 (z 7.5 to 8.5) with a little float headroom. */
    private static final float SPRITE_SLAB_MAX = 1.0F / 16F + 1.0E-3F;

    /** Merge one quad's four vertex positions into the shared
     *  {minX,minY,minZ,maxX,maxY,maxZ} accumulator (block-format quads: 8
     *  ints per vertex, position in slots 0-2). */
    private static void accumulateQuad(int[] vertices, float[] minMax) {
        for (int i = 0; i < 4; i++) {
            float x = Float.intBitsToFloat(vertices[i * 8]);
            float y = Float.intBitsToFloat(vertices[i * 8 + 1]);
            float z = Float.intBitsToFloat(vertices[i * 8 + 2]);
            minMax[0] = Math.min(minMax[0], x);
            minMax[3] = Math.max(minMax[3], x);
            minMax[1] = Math.min(minMax[1], y);
            minMax[4] = Math.max(minMax[4], y);
            minMax[2] = Math.min(minMax[2], z);
            minMax[5] = Math.max(minMax[5], z);
        }
    }

    /** Block-model fallback for flat-sprite block items: when the item is a
     *  {@link BlockItem} whose item model is a generated plane, its 3D form
     *  exists only on the placed block — return the block model so previews
     *  match the placed look (mod food plates, baskets). Portion-counter
     *  amount-style integer properties are maxed — and {@code bites}, which
     *  counts bites taken, is reset to the fresh state — so the preview
     *  shows the full/served state rather than the freshly placed empty
     *  one. The original model is returned unchanged when the item is
     *  not block-backed, its model is not flat, or the block has no
     *  measurable geometry. */
    private static BakedModel blockBackedModel(ItemStack item, BakedModel fallback) {
        if (!(item.getItem() instanceof BlockItem blockItem) || !isFlatSpriteModel(fallback)) {
            return fallback;
        }
        try {
            BlockState state = blockItem.getBlock().defaultBlockState();
            for (Property<?> property : state.getProperties()) {
                if (property instanceof IntegerProperty intProperty) {
                    List<Integer> values = List.copyOf(intProperty.getPossibleValues());
                    // Fullness semantics from the Kaleidoscope Cookery pack
                    // (2026-11-16): amount counters peak at max, but {@code
                    // bites} counts bites TAKEN (vanilla cake convention) —
                    // max shows the nearly-eaten dish (candied_potato_3: 1
                    // element vs _0's 16) and its sparse geometry then fed
                    // the pivot. Amount props go max, bites goes back to the
                    // fresh state.
                    state = state.setValue(intProperty, property.getName().equals("bites")
                            ? values.get(0) : values.get(values.size() - 1));
                }
            }
            BakedModel blockModel = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
            if (blockModel == null || blockModel.isCustomRenderer()
                    || modelBounds(blockModel) == NO_BOUNDS) {
                return fallback;
            }
            return blockModel;
        } catch (Throwable t) {
            LOGGER.warn("Block-backed 3D preview fallback failed for {}",
                    BuiltInRegistries.ITEM.getKey(item.getItem()), t);
            return fallback;
        }
    }

    /** Item ids whose same-named entity must never become the 3D preview:
     *  escape hatch for convention collisions that render something
     *  misleading (L4 finds one, add its {@code namespace:path} item id).
     *  Empty today. */
    private static final Set<String> ENTITY_CONVENTION_DENYLIST = Set.of();

    /** One-shot preview-path diagnostic per item id (rendered in
     *  {@link #renderItem3D}'s default path): records the gate discriminators
     *  (flat / block-backed / custom renderer / entity candidate) exactly
     *  once per item so a wrong or missing preview can be attributed from
     *  the log alone. */
    private static final Set<String> PREVIEW_PATH_LOG = new HashSet<>();

    /** One-shot log guard per entity type for {@link #entityPreviewInstance}:
     *  vanilla answers {@code EntityType.create(Level)} with a silent null
     *  (feature-flag gate) and the flat-sprite degrade is otherwise
     *  completely log-free. */
    private static final Set<String> ENTITY_PREVIEW_NULL_LOG = new HashSet<>();

    /** Resolves the same-id entity convention for the 3D default path: an
     *  entity type sharing the flat-sprite item's registry id means the 3D
     *  form lives on the entity — modded summonables (Immersive Aircraft
     *  vehicles, Kaleidoscope Cookery's scarecrow) and vanilla kin
     *  (minecarts, armor stand, end crystal, item frames, painting,
     *  trident). Food items never match (raw chicken vs. the chicken entity —
     *  the food item is the truth), and the denylist overrides everything.
     *  Null when the convention does not apply. */
    private static EntityType<?> conventionEntityType(ItemStack item) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item.getItem());
        if (ENTITY_CONVENTION_DENYLIST.contains(itemId.toString())
                || item.get(DataComponents.FOOD) != null) {
            return null;
        }
        return BuiltInRegistries.ENTITY_TYPE.containsKey(itemId)
                ? BuiltInRegistries.ENTITY_TYPE.get(itemId) : null;
    }

    /** Generic summon-container convention: any item whose
     *  {@code minecraft:block_entity_data} stores an {@code EntityType}
     *  string that resolves to a registered entity — Superb Warfare's summon
     *  crates and addons reusing the convention under their own namespaces
     *  (which the SBW-specific gate rejects). Type-only: the block entity
     *  tag is deliberately not loaded onto the preview entity (block-entity
     *  fields are not entity save data). Package-private: the generic summon
     *  strategy gates on it; the SBW-specific strategy runs first and keeps
     *  its tuned icon degrade chain. */
    static EntityType<?> summonContainerEntityType(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        try {
            CompoundTag tag = superWarfareContainerTag(stack);
            if (tag == null || !tag.contains("EntityType", 8)) {
                return null;
            }
            ResourceLocation typeId = ResourceLocation.tryParse(tag.getString("EntityType"));
            return typeId != null && BuiltInRegistries.ENTITY_TYPE.containsKey(typeId)
                    ? BuiltInRegistries.ENTITY_TYPE.get(typeId) : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** True for any Superb Warfare (卓越前线) item. The namespace check alone
     *  proves the optional mod is loaded, and no Superb Warfare class is
     *  touched, so unlike the TACZ gate there is nothing to gate against.
     *  Package-private: the SBW gun preview strategy gates on it. */
    static boolean isSuperbWarfareItem(ItemStack stack) {
        return !stack.isEmpty() && SUPERB_WARFARE_MOD_ID.equals(
                BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace());
    }

    /** True for a Superb Warfare summon container whose block_entity_data
     *  carries a {@code superbwarfare:} EntityType: the crate summons a
     *  vehicle when placed (ContainerBlockItem reads BLOCK_ENTITY_DATA's
     *  "EntityType" string), so the preview shows that vehicle (3D model,
     *  degrading to SBW's inventory icon). Vanilla-only types — no SBW class
     *  touched; the EntityType
     *  registry lookup returning null also fails closed to the crate icon.
     *  Package-private: the SBW vehicle preview strategy gates on it. */
    static boolean isSuperbWarfareVehicleContainer(ItemStack stack) {
        if (stack.isEmpty() || !SUPERB_WARFARE_MOD_ID.equals(
                BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace())) {
            return false;
        }
        CompoundTag tag = superWarfareContainerTag(stack);
        if (tag == null || !tag.contains("EntityType", 8)) {
            return false;
        }
        String id = tag.getString("EntityType");
        ResourceLocation typeId = ResourceLocation.tryParse(id);
        return typeId != null
                && id.startsWith(SUPERB_WARFARE_MOD_ID + ":")
                && BuiltInRegistries.ENTITY_TYPE.containsKey(typeId);
    }

    /** The container NBT of a Superb Warfare summon crate: the modern
     *  {@code minecraft:block_entity_data} component. Callers gate on
     *  "EntityType" presence themselves. Package-private: read by the SBW
     *  vehicle preview strategy's 2D icon path. */
    static CompoundTag superWarfareContainerTag(ItemStack stack) {
        var customData = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        return customData == null ? null : customData.copyTag();
    }

    /** Superb Warfare vehicle preview. Primary path: render the referenced
     *  vehicle's real 3D bedrock model — a client-side entity of the crate's
     *  EntityType is created once (cached) and dispatched through the vanilla
     *  EntityRenderDispatcher, so SBW's own GeoVehicleRenderer draws the
     *  model with no SBW class referenced; size/centring come from the
     *  probe-measured bounds ({@link #entityPreviewBounds}) via the shared
     *  fitScaleFor rule (L4 log evidence: modelEntries=1, vertices=true for
     *  freshly created entities). When the entity produces nothing usable
     *  (model resources missing) or the render fails, degrade to SBW's own
     *  inventory display method (verified in the 0.8.9.2 jar bytecode): the
     *  crate's BEWLR draws nothing in GUI and its IItemDecorator blits
     *  {@code superbwarfare:textures/vehicle_icon/container/<path>.png} over
     *  the slot — the preview blits the same texture. An id without an icon
     *  texture falls back to the crate's own FIXED-context 3D content (what
     *  the BEWLR draws for a bare crate). Package-private: the SBW vehicle
     *  preview strategy's 3D body. */
    static void renderSuperbWarfareVehicle(GuiGraphics gg, ItemStack item, LivingEntity player,
                                                   int cx, int cy, Quat rotation, float scale) {
        Entity vehicle = superbWarfareVehicleInstance(item, player);
        if (vehicle != null) {
            ModelBounds bounds = entityPreviewBounds(vehicle);
            if (bounds != NO_BOUNDS && renderEntityPreview(gg, vehicle, bounds, cx, cy, rotation, scale)) {
                return;
            }
        }
        CompoundTag tag = superWarfareContainerTag(item);
        ResourceLocation icon = tag == null ? null : superbWarfareVehicleIcon(tag.getString("EntityType"));
        if (icon != null) {
            renderVehicleIcon(gg, icon, cx, cy, scale);
            return;
        }
        renderFixedContextModel3D(gg, item, player, cx, cy, rotation, scale, null);
    }

    /** The real 3D entity draw: same pose convention as every other 3D
     *  branch (cell top-left origin, drag quaternion, centre pivot), scaled
     *  by the probe-measured bounds so oversized entities stay inside the
     *  preview square, with the same {@link #SBW_FIT_BOOST} the gun branch
     *  uses (the caller guarantees real bounds here). Returns false when the
     *  render throws, so the caller can degrade to the icon path. */
    static boolean renderEntityPreview(GuiGraphics gg, Entity entity, ModelBounds bounds,
                                       int cx, int cy, Quat rotation, float scale) {
        float fitScale = fitScaleFor(bounds.span(), scale);
        if (bounds.span() > PREVIEW_FILL) {
            // The shared helper passes spans <= 1 block through at the caller's
            // scale (an item's natural size), but the vehicle boost below then
            // pushes exactly-square models past the preview square — the
            // ferrouslime probe measured span exactly 1.0 and rendered ~130%
            // of the square (2026-11-21). Target the same fill the shrink path
            // uses, so every entity caps at PREVIEW_FILL * SBW_FIT_BOOST.
            fitScale = scale * PREVIEW_FILL / bounds.span();
        }
        fitScale *= SBW_FIT_BOOST;
        PoseStack pose = gg.pose();
        pose.pushPose();
        pose.translate(cx, cy, 100.0F);
        pose.translate(8.0F * scale, 8.0F * scale, 0.0F);
        pose.scale(1.0F, -1.0F, 1.0F);
        // Framing and drag hub are decoupled (2026-11-21, Alex's Mobs): the
        // envelope-box centre must sit on the viewport centre (a single mass
        // pivot ran up to ~22% of the span off-centre and clipped the far
        // side of the preview square), while the drag rotation pivots on the
        // reconstructed cube mass centre. The offset is applied BEFORE the
        // drag quaternion, so it shifts the framing without rotating with
        // the drag — at rest the model sits symmetric, and dragging turns
        // the body in place.
        float s16 = 16.0F * fitScale;
        Vector3f center = bounds.center();
        Vector3f hub = bounds.hub();
        pose.translate(s16 * (hub.x - center.x), s16 * (hub.y - center.y),
                s16 * (hub.z - center.z));
        SCRATCH_QUAT.set(rotation.x(), rotation.y(), rotation.z(), rotation.w()).normalize();
        pose.mulPose(SCRATCH_QUAT);
        Lighting.setupForEntityInInventory();
        pose.scale(s16, s16, s16);
        pose.translate(-hub.x, -hub.y, -hub.z);
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.mul(pose.last().pose());
        RenderSystem.applyModelViewMatrix();
        PoseStack renderStack = REUSABLE_POSE_STACK;
        renderStack.setIdentity();
        try {
            renderPreviewEntity(entity, renderStack, bufferSource);
            bufferSource.endBatch();
            return true;
        } catch (Throwable t) {
            LOGGER.warn("Entity 3D preview render failed; degrading to the icon path", t);
            return false;
        } finally {
            modelViewStack.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.enableDepthTest();
            pose.popPose();
        }
    }

    /** Entity-based 3D preview for an arbitrary EntityType (spawn eggs,
     *  id-convention vehicles): park a shared client entity, probe its
     *  geometry, draw, and degrade to the item's flat icon when the entity
     *  produces nothing usable or the render fails. */
    static void renderEntityPreviewForType(GuiGraphics gg, ItemStack stack, LivingEntity player,
                                           int cx, int cy, Quat rotation, float scale, EntityType<?> type) {
        renderEntityPreviewForType(gg, stack, player, cx, cy, rotation, scale, type, null);
    }

    /** Overload carrying an entity save tag (spawn-egg {@code entity_data}):
     *  vanilla applies that tag to the spawned mob ({@code EntityType.spawn}
     *  loads it), so the preview matches by re-loading it onto the shared
     *  instance every frame — the instance is reused per type, so per-frame
     *  re-application keeps two variants of one type from bleeding into each
     *  other. Bounds stay cached per type: variant geometry differences
     *  (sheep colour, villager profession) never change the largest axis. */
    static void renderEntityPreviewForType(GuiGraphics gg, ItemStack stack, LivingEntity player,
                                           int cx, int cy, Quat rotation, float scale, EntityType<?> type,
                                           CustomData spawnData) {
        Entity entity = entityPreviewInstance(type, player.level());
        markPreviewMob(entity);
        if (entity != null) {
        // Entity.load resets yHeadRot/yBodyRot on every call; identical
        // spawn data must not be re-applied per frame or it wipes the
        // inspect animation state (see previewSpawnDataChanged).
            if (spawnData != null && !spawnData.isEmpty()
                    && previewSpawnDataChanged(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString(), stack)) {
                try {
                    entity.load(spawnData.copyTag());
                    // load() may carry Pos/Rotation from the save tag; keep the
                    // parked pose so the preview never drifts.
                    entity.setPos(0.0D, 64.0D, 0.0D);
                    entity.setYRot(180.0F);
                    entity.setXRot(0.0F);
                } catch (Throwable t) {
                    LOGGER.warn("Entity preview: applying the spawn tag failed; keeping the default look", t);
                }
            }
            ModelBounds bounds = entityPreviewBounds(entity);
            if (bounds != NO_BOUNDS && renderEntityPreview(gg, entity, bounds, cx, cy, rotation, scale)) {
                return;
            }
        }
        renderItem2DFlat(player, gg, stack, cx, cy, scale);
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
                LOGGER.info("[csgobox] preview mob armed: type={}",
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
     *  degrades to a standing pause. The enderman stare and spellcaster
     *  casting drive public API only. */
    private static final Field SHEEP_EAT_TICK = resolveField(Sheep.class, "eatAnimationTick");
    private static final Field CREEPER_SWELL = resolveField(Creeper.class, "swell");
    private static final Field CREEPER_OLD_SWELL = resolveField(Creeper.class, "oldSwell");

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
            LOGGER.warn("[csgobox] preview mob: {}.{} unreachable, its special action degrades",
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

    /** The IllagerSpell enum is protected on this era (vanilla tightened the
     *  internal API): the casting call and the two constants resolve
     *  reflectively - the Forge jars keep it public, hence the split. */
    private static final Method SPELL_CAST_METHOD = resolveSpellCastMethod();
    private static final Object SPELL_FANGS = resolveSpellConstant("FANGS");
    private static final Object SPELL_NONE = resolveSpellConstant("NONE");

    /** Resolves setIsCastingSpell by shape (name + single enum parameter);
     *  null on failure and the spell action degrades. */
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
            LOGGER.warn("[csgobox] preview mob: spellcaster casting call unreachable, the action degrades", t);
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
            LOGGER.warn("[csgobox] preview mob: spell constant {} unreachable, the action degrades", name);
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
                LOGGER.info("[csgobox] preview mob anim: type={} (first drive)",
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
            living.walkAnimation.setSpeed(0.0F);
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
        living.walkAnimation.update(phase == 0 ? PREVIEW_MOB_WALK_TARGET : 0.0F, 0.4F);

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
                // data; setIsCastingSpell is public and writes both sides.
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

    /** Armor preview on the shared invisible armor stand: equip the piece
     *  (its {@code ArmorItem} slot), probe with it worn (bounds cached per
     *  item id), draw, and degrade to the flat icon. GUI never draws the
     *  worn model — it lives in the humanoid armor layers, which the stand's
     *  renderer carries; pieces whose mod registers no armor-stand layer
     *  render nothing and degrade via the empty probe. */
    static void renderArmorStandPreview(GuiGraphics gg, ItemStack stack, LivingEntity player,
                                        int cx, int cy, Quat rotation, float scale) {
        EquipmentSlot slot = ((ArmorItem) stack.getItem()).getEquipmentSlot();
        Entity entity = entityPreviewInstance(EntityType.ARMOR_STAND, player.level());
        if (entity instanceof ArmorStand stand) {
            // The stand instance is cached and shared across inspections:
            // wipe every equipment slot first, or the previously inspected
            // pieces keep rendering alongside the new one.
            for (EquipmentSlot clearSlot : EquipmentSlot.values()) {
                stand.setItemSlot(clearSlot, ItemStack.EMPTY);
            }
            stand.setItemSlot(slot, stack);
            ModelBounds bounds = armorPreviewBounds(stack, stand);
            if (bounds != NO_BOUNDS && renderEntityPreview(gg, stand, bounds, cx, cy, rotation, scale)) {
                return;
            }
        }
        renderItem2DFlat(player, gg, stack, cx, cy, scale);
    }

    // ===================== Create schematic (蓝图) structure preview =====================

    /** Create schematic 3D preview: the structure lives in a local .nbt file
     *  that {@code SchematicItem.loadSchematic} reads (reflected, so no Create
     *  class is compiled against), and the whole preview pipeline is Create's
     *  own — a fake {@code SchematicLevel} filled via vanilla {@code placeIn
     *  World}, Create's {@code SchematicRenderer} (neighbour-face-culled
     *  chunk-style tessellation cached per RenderType, plus live block-entity
     *  rendering with an errored-BE mask), drawn through the shared {@code
     *  DefaultSuperRenderTypeBuffer} whose {@code draw()} flushes straight to
     *  screen under our model-view transform, so the caller's pose scaling
     *  and {@link #SBW_FIT_BOOST} apply unchanged. Unrenderable cases (Create
     *  absent, file missing, empty, over budget) degrade to the flat icon;
     *  the sentinel caches that so the file is not re-read every frame. */
    static void renderCreateSchematicPreview(GuiGraphics gg, ItemStack stack, LivingEntity player,
                                             int cx, int cy, Quat rotation, float scale) {
        CreateSchematicBundle bundle = createSchematicBundle(stack, player);
        if (bundle == null) {
            renderItem2DFlat(player, gg, stack, cx, cy, scale);
            return;
        }
        float fitScale = fitScaleFor(bundle.span(), scale);
        fitScale *= SBW_FIT_BOOST;
        PoseStack pose = gg.pose();
        pose.pushPose();
        pose.translate(cx, cy, 100.0F);
        pose.translate(8.0F * scale, 8.0F * scale, 0.0F);
        pose.scale(1.0F, -1.0F, 1.0F);
        SCRATCH_QUAT.set(rotation.x(), rotation.y(), rotation.z(), rotation.w()).normalize();
        pose.mulPose(SCRATCH_QUAT);
        Lighting.setupForEntityInInventory();
        pose.scale(16.0F * fitScale, 16.0F * fitScale, 16.0F * fitScale);
        Vector3f center = bundle.center();
        pose.translate(-center.x, -center.y, -center.z);
        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.mul(pose.last().pose());
        RenderSystem.applyModelViewMatrix();
        try {
            // Create renders the structure in block coordinates (0..size); an
            // identity pose hands the placement to our model-view transform.
            PoseStack identity = REUSABLE_POSE_STACK;
            identity.setIdentity();
            Object buffer = CREATE_BUFFER_GET_INSTANCE.invoke(null);
            CREATE_RENDERER_RENDER.invoke(bundle.renderer(), identity, buffer);
            CREATE_BUFFER_DRAW.invoke(buffer);
        } catch (Throwable t) {
            LOGGER.warn("Create schematic preview render failed; degrading to the icon path", t);
        } finally {
            modelViewStack.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.enableDepthTest();
            pose.popPose();
        }
    }

    // ===================== Prefab blueprint (蓝图) structure preview =====================

    /** Prefab blueprint 3D preview: mirrors the mod's own ghost-preview data
     *  path (the reflective compute lives in {@link PrefabStructurePreview})
     *  and renders the resolved block map per frame through the vanilla
     *  {@code BlockRenderDispatcher} over a tiny {@link BlockAndTintGetter}
     *  backed by the baked map — full-bright light, no directional shade and
     *  untinted colors, the same flat look item icons get. Block entities
     *  (chests/beds/etc.) draw through their own renderers with the client
     *  level re-parented; any renderer that throws is blacklisted for the
     *  session, mirroring the Create preview's errored-BE mask. Unresolvable
     *  bundles (budget/missing structures) degrade to the flat icon inside
     *  the bundle cache. */
    static void renderPrefabStructurePreview(GuiGraphics gg, ItemStack stack, LivingEntity player,
                                             int cx, int cy, Quat rotation, float scale) {
        PrefabStructurePreview.StructureData bundle = PrefabStructurePreview.bundle(stack, player);
        if (bundle == null) {
            renderItem2DFlat(player, gg, stack, cx, cy, scale);
            return;
        }
        float fitScale = fitScaleFor(bundle.span(), scale);
        PoseStack pose = gg.pose();
        pose.pushPose();
        pose.translate(cx, cy, 100.0F);
        pose.translate(8.0F * scale, 8.0F * scale, 0.0F);
        pose.scale(1.0F, -1.0F, 1.0F);
        SCRATCH_QUAT.set(rotation.x(), rotation.y(), rotation.z(), rotation.w()).normalize();
        pose.mulPose(SCRATCH_QUAT);
        Lighting.setupForEntityInInventory();
        pose.scale(16.0F * fitScale, 16.0F * fitScale, 16.0F * fitScale);
        Vector3f center = bundle.center();
        pose.translate(-center.x, -center.y, -center.z);
        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.mul(pose.last().pose());
        RenderSystem.applyModelViewMatrix();
        try {
            FAKE_STRUCTURE_LEVEL.attach(bundle);
            PoseStack renderStack = REUSABLE_POSE_STACK;
            renderStack.setIdentity();
            Minecraft minecraft = Minecraft.getInstance();
            BlockRenderDispatcher dispatcher = minecraft.getBlockRenderer();
            MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();
            for (Map.Entry<BlockPos, BlockState> entry : bundle.blocks().entrySet()) {
                BlockPos pos = entry.getKey();
                BlockState state = entry.getValue();
                RenderType layer = ItemBlockRenderTypes.getChunkRenderType(state);
                renderStack.pushPose();
                renderStack.translate(pos.getX(), pos.getY(), pos.getZ());
                SCRATCH_RANDOM.setSeed(pos.asLong());
                dispatcher.renderBatched(state, pos, FAKE_STRUCTURE_LEVEL, renderStack,
                        buffer.getBuffer(layer), false, SCRATCH_RANDOM, ModelData.EMPTY, layer);
                renderStack.popPose();
            }
            BlockEntityRenderDispatcher beDispatcher = minecraft.getBlockEntityRenderDispatcher();
            for (Map.Entry<BlockPos, BlockEntity> entry : bundle.blockEntities().entrySet()) {
                BlockEntity blockEntity = entry.getValue();
                BlockEntityRenderer<BlockEntity> renderer = beDispatcher.getRenderer(blockEntity);
                if (renderer == null || ERRORED_BE_TYPES.contains(blockEntity.getType())) {
                    continue;
                }
                renderStack.pushPose();
                renderStack.translate(entry.getKey().getX(), entry.getKey().getY(), entry.getKey().getZ());
                try {
                    renderer.render(blockEntity, 0.0F, renderStack, buffer, 15728880, OverlayTexture.NO_OVERLAY);
                } catch (Throwable t) {
                    ERRORED_BE_TYPES.add(blockEntity.getType());
                    LOGGER.warn("[csgobox] prefab blueprint preview: block entity renderer errored, blacklisting {}",
                            BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType()), t);
                } finally {
                    renderStack.popPose();
                }
            }
            buffer.endBatch();
        } catch (Throwable t) {
            LOGGER.warn("Prefab blueprint preview render failed; degrading to the icon path", t);
        } finally {
            modelViewStack.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.enableDepthTest();
            pose.popPose();
        }
    }

    /** Block entities whose renderer threw at least once this session. */
    private static final Set<BlockEntityType<?>> ERRORED_BE_TYPES = new HashSet<>();

    /** Minimal {@link BlockAndTintGetter} over the baked block map: full
     *  bright light, no directional shade, untinted block colors — the whole
     *  structure gets the flat-icon look. The light-engine accessor returns
     *  null on purpose; every engine-backed default ({@code getBrightness}/
     *  {@code getRawBrightness}/{@code canSeeSky}) is overridden here, so the
     *  engine is only reachable from code paths this preview never triggers. */
    private static final class PrefabStructureLevel implements BlockAndTintGetter {
        private Map<BlockPos, BlockState> blocks = Map.of();
        private Map<BlockPos, BlockEntity> blockEntities = Map.of();

        PrefabStructureLevel(PrefabStructurePreview.StructureData bundle) {
            attach(bundle);
        }

        void attach(PrefabStructurePreview.StructureData bundle) {
            this.blocks = bundle.blocks();
            this.blockEntities = bundle.blockEntities();
        }

        @Override
        public int getHeight() {
            return 4096;
        }

        @Override
        public int getMinBuildHeight() {
            return -2048;
        }

        @Override
        public int getMaxBuildHeight() {
            return 2047;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            BlockState state = this.blocks.get(pos);
            return state != null ? state : Blocks.AIR.defaultBlockState();
        }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return this.blockEntities.get(pos);
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return this.getBlockState(pos).getFluidState();
        }

        @Override
        public float getShade(Direction direction, boolean shaded) {
            return 1.0F;
        }

        @Override
        public LevelLightEngine getLightEngine() {
            return null;
        }

        @Override
        public int getBrightness(LightLayer layer, BlockPos pos) {
            return 15;
        }

        @Override
        public int getRawBrightness(BlockPos pos, int amount) {
            return 15;
        }

        @Override
        public int getBlockTint(BlockPos pos, ColorResolver resolver) {
            return -1;
        }
    }

    /** The render-thread fake level; bundles are swapped per frame (render
     *  thread only — no visibility concerns). */
    private static final PrefabStructureLevel FAKE_STRUCTURE_LEVEL =
            new PrefabStructureLevel(new PrefabStructurePreview.StructureData(
                    Map.of(), Map.of(), new Vector3f(), 1.0F));

    /** Cached bundle lookup; {@code null} (with the sentinel cached) marks
     *  "unrenderable, degrade". */
    private static CreateSchematicBundle createSchematicBundle(ItemStack stack, LivingEntity player) {
        Integer key = stack.hashCode();
        if (createSchematicBundleCache instanceof CreateSchematicBundle bundle && createSchematicBundleKey == key) {
            return bundle;
        }
        if (createSchematicBundleCache == NO_CENTER && createSchematicBundleKey == key) {
            return null;
        }
        CreateSchematicBundle bundle = buildCreateSchematicBundle(stack, player);
        createSchematicBundleCache = bundle == null ? NO_CENTER : bundle;
        createSchematicBundleKey = key;
        return bundle;
    }

    /** Load the schematic, guard the block budget, then wire Create's own
     *  preview pipeline — the exact sequence Create's SchematicHandler runs:
     *  a fake {@code SchematicLevel} filled by vanilla {@code placeInWorld}
     *  (flags = UPDATE_CLIENTS), its block entities re-parented to the fake
     *  level, and a {@code SchematicRenderer} marked dirty so the first frame
     *  tessellates with neighbour-face culling. */
    private static CreateSchematicBundle buildCreateSchematicBundle(ItemStack stack, LivingEntity player) {
        Method loader = CREATE_LOAD_SCHEMATIC;
        if (loader == null) {
            if (CREATE_LOAD_SCHEMATIC_RESOLVED) {
                return null;
            }
            CREATE_LOAD_SCHEMATIC_RESOLVED = true;
            try {
                // The strategy gate (create:schematic item exists) proves the
                // mod is on the classpath, so this class load is safe.
                Class<?> owner = Class.forName("com.simibubi.create.content.schematics.SchematicItem");
                Method m = owner.getDeclaredMethod("loadSchematic", Level.class, ItemStack.class);
                m.setAccessible(true);
                CREATE_LOAD_SCHEMATIC = m;
                loader = m;
            } catch (Throwable t) {
                LOGGER.warn("Create schematic preview unavailable: loadSchematic not resolved", t);
                return null;
            }
        }
        if (!resolveCreateRenderer()) {
            return null;
        }
        StructureTemplate template;
        try {
            template = (StructureTemplate) loader.invoke(null, player.level(), stack);
        } catch (Throwable t) {
            LOGGER.warn("Create schematic preview load failed", t);
            return null;
        }
        if (template == null) {
            return null;
        }
        Vec3i size = template.getSize();
        // Round-trip through the vanilla structure format for the block-count
        // budget; the renderer itself reads the template via placeInWorld.
        CompoundTag saved = template.save(new CompoundTag());
        if (!saved.contains("blocks", Tag.TAG_LIST) || !saved.contains("palette", Tag.TAG_LIST)) {
            return null;
        }
        ListTag paletteTags = saved.getList("palette", Tag.TAG_COMPOUND);
        ListTag blockTags = saved.getList("blocks", Tag.TAG_COMPOUND);
        if (paletteTags.isEmpty() || blockTags.isEmpty()) {
            LOGGER.info("Create schematic preview: empty structure (missing file or blank schematic); degrading to the flat icon");
            return null;
        }
        if (blockTags.size() > MAX_CAPTURE_BLOCKS) {
            LOGGER.warn("Create schematic preview: {} blocks exceeds the {} capture budget; degrading to the flat icon",
                    blockTags.size(), MAX_CAPTURE_BLOCKS);
            return null;
        }
        try {
            Object level = CREATE_SCHEMATIC_LEVEL_CTOR.newInstance(player.level());
            template.placeInWorld((ServerLevelAccessor) level, BlockPos.ZERO, BlockPos.ZERO,
                    new StructurePlaceSettings(), (RandomSource) CREATE_LEVEL_GET_RANDOM.invoke(level), Block.UPDATE_CLIENTS);
            for (Object beObj : (Iterable<?>) CREATE_LEVEL_GET_BLOCK_ENTITIES.invoke(level)) {
                ((BlockEntity) beObj).setLevel((Level) level);
            }
            Object renderer = CREATE_RENDERER_CTOR.newInstance(level);
            CREATE_RENDERER_UPDATE.invoke(renderer);
            Vector3f center = new Vector3f(size.getX() / 2.0F, size.getY() / 2.0F, size.getZ() / 2.0F);
            float span = Math.max(size.getX(), Math.max(size.getY(), size.getZ()));
            return new CreateSchematicBundle(level, renderer, center, span);
        } catch (Throwable t) {
            LOGGER.warn("Create schematic preview pipeline failed", t);
            return null;
        }
    }

    /** Reflectively resolve Create's renderer plumbing (see the field docs).
     *  All classes live in Create/Ponder-catnip and are only requested after
     *  the strategy gate proved the mod is loaded. */
    private static boolean resolveCreateRenderer() {
        if (CREATE_RENDERER_RESOLVED) {
            return CREATE_SCHEMATIC_LEVEL_CTOR != null;
        }
        CREATE_RENDERER_RESOLVED = true;
        try {
            Class<?> schematicLevel = Class.forName("net.createmod.catnip.levelWrappers.SchematicLevel");
            Class<?> renderer = Class.forName("com.simibubi.create.content.schematics.client.SchematicRenderer");
            Class<?> buffer = Class.forName("net.createmod.catnip.render.DefaultSuperRenderTypeBuffer");
            // render declares the SuperRenderTypeBuffer INTERFACE as its
            // second parameter; getMethod matches declared types exactly, so
            // looking it up with the DefaultSuperRenderTypeBuffer impl class
            // fails NoSuchMethodException even though the instance fits
            // (2026-11-14 L4 log).
            Class<?> bufferInterface = Class.forName("net.createmod.catnip.render.SuperRenderTypeBuffer");
            CREATE_SCHEMATIC_LEVEL_CTOR = schematicLevel.getConstructor(Level.class);
            CREATE_RENDERER_CTOR = renderer.getConstructor(schematicLevel);
            CREATE_RENDERER_UPDATE = renderer.getMethod("update");
            CREATE_RENDERER_RENDER = renderer.getMethod("render", PoseStack.class, bufferInterface);
            CREATE_BUFFER_GET_INSTANCE = buffer.getMethod("getInstance");
            CREATE_BUFFER_DRAW = buffer.getMethod("draw");
            CREATE_LEVEL_GET_RANDOM = schematicLevel.getMethod("getRandom");
            CREATE_LEVEL_GET_BLOCK_ENTITIES = schematicLevel.getMethod("getBlockEntities");
            return true;
        } catch (Throwable t) {
            LOGGER.warn("Create schematic preview unavailable: renderer plumbing not resolved", t);
            return false;
        }
    }

    /** The (shared) client-side vehicle entity for a summon crate, or null
     *  when the EntityType is gone. See {@link #entityPreviewInstance} for
     *  the instance lifecycle. */
    static Entity superbWarfareVehicleInstance(ItemStack stack, LivingEntity player) {
        CompoundTag tag = superWarfareContainerTag(stack);
        if (tag == null || !tag.contains("EntityType", 8)) {
            return null;
        }
        ResourceLocation typeId = ResourceLocation.tryParse(tag.getString("EntityType"));
        // DefaultedRegistry.get falls back to the pig for unknown ids, so
        // existence must go through containsKey.
        if (typeId == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(typeId)) {
            return null;
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(typeId);
        return type == null ? null : entityPreviewInstance(type, player.level());
    }

    /** The parked client-side preview entity for an entity type, or null on
     *  failure. The instance is cached per entity type: the model files are
     *  heavy and the renderer animates against the entity, so a per-frame
     *  allocation would reload/replay every frame. Not added to the client
     *  world — it is rendered explicitly via the dispatcher. Parked at a
     *  fixed in-world position (the dispatcher renders at (0,0,0) in preview
     *  space; the entity's own yRot/animation state is what the renderer
     *  consumes) with a neutral yaw. The entity stays fully visible:
     *  LivingEntity renderers skip the whole model for invisible entities,
     *  which starved the probe to zero vertices on every vanilla mob (SBW's
     *  GeckoLib renderers ignore the flag, which is why only vehicles ever
     *  previewed). The shadow pass is suppressed per render instead — see
     *  {@link #renderPreviewEntity}. */
    static Entity entityPreviewInstance(EntityType<?> type, Level level) {
        String cacheKey = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
        Entity cached = ENTITY_PREVIEW_INSTANCES.get(cacheKey);
        if (cached != null && cached.level() == level) {
            return cached;
        }
        try {
            Entity entity = type.create(level);
            if (entity == null) {
                if (ENTITY_PREVIEW_NULL_LOG.add(cacheKey)) {
                    LOGGER.warn("[csgobox] entity preview: type.create(level) returned null for {} (feature-flag gated or factory failure)", cacheKey);
                }
                return null;
            }
            entity.setPos(0.0D, 64.0D, 0.0D);
            entity.setYRot(180.0F);
            entity.setXRot(0.0F);
            ENTITY_PREVIEW_INSTANCES.put(cacheKey, entity);
            return entity;
        } catch (Throwable t) {
            LOGGER.warn("Entity preview instance creation failed for {}",
                    BuiltInRegistries.ENTITY_TYPE.getKey(type), t);
            return null;
        }
    }

    /** Centre + largest-axis span (block units) of a preview entity's
     *  rendered geometry, measured by probing one real entity render into
     *  the vertex recorder (entity renderers emit through the same
     *  VertexConsumer funnel as item renderers). Cached per entity type like
     *  the gun probes; a failed or empty probe keeps the raw preview scale.
     *  One-shot INFO log per entity type keeps runtime evidence for fresh
     *  preview entities. */
    static ModelBounds entityPreviewBounds(Entity entity) {
        Object cached = ENTITY_PREVIEW_BOUNDS.get(entity.getType());
        if (cached instanceof ModelBounds bounds) {
            return bounds;
        }
        if (cached == NO_CENTER) {
            return NO_BOUNDS;
        }
        ModelBounds bounds = probeEntityBounds(entity);
        boolean any = bounds != NO_BOUNDS;
        LOGGER.info("[csgobox] entity preview probe: type={} vertices={} span={}",
                BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()),
                any, any ? bounds.span() : "none");
        ENTITY_PREVIEW_BOUNDS.put(entity.getType(), any ? bounds : NO_CENTER);
        return bounds;
    }

    /** Bounds of an armor piece worn by the shared preview stand, cached per
     *  item id (the stand's entity type is constant but its worn model varies
     *  per piece). The stand must already be equipped when probing. */
    private static ModelBounds armorPreviewBounds(ItemStack stack, ArmorStand stand) {
        String cacheKey = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        Object cached = ARMOR_PREVIEW_BOUNDS.get(cacheKey);
        if (cached instanceof ModelBounds bounds) {
            return bounds;
        }
        if (cached == NO_CENTER) {
            return NO_BOUNDS;
        }
        ModelBounds bounds = probeEntityBounds(stand);
        ARMOR_PREVIEW_BOUNDS.put(cacheKey, bounds == NO_BOUNDS ? NO_CENTER : bounds);
        return bounds;
    }

    /** The dispatcher's private shadow switch ({@code shouldRenderShadow}),
     *  resolved once via reflection. The shadow pass must be suppressed
     *  around preview renders because the preview entities are fully visible:
     *  the previous invisible trick starved LivingEntity renderers to zero
     *  vertices (the whole model is skipped when invisible), while SBW's
     *  GeckoLib renderers ignore the flag and kept drawing. Null when
     *  reflection fails — previews then keep the shadow pass (cosmetic only). */
    private static final Field ENTITY_SHADOW_FLAG = resolveEntityShadowFlag();

    private static Field resolveEntityShadowFlag() {
        try {
            Field field = EntityRenderDispatcher.class.getDeclaredField("shouldRenderShadow");
            field.setAccessible(true);
            return field;
        } catch (Throwable t) {
            LOGGER.warn("Entity preview: cannot reach the dispatcher shadow flag; previews keep the shadow pass", t);
            return null;
        }
    }

    /** Renders a preview entity through the entity dispatcher with the shadow
     *  pass temporarily disabled (save/restore around the call, render thread
     *  only — the dispatcher is a global singleton). */
    private static void renderPreviewEntity(Entity entity, PoseStack stack, MultiBufferSource source) {
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        boolean saved = true;
        boolean toggled = false;
        if (ENTITY_SHADOW_FLAG != null) {
            try {
                saved = ENTITY_SHADOW_FLAG.getBoolean(dispatcher);
                ENTITY_SHADOW_FLAG.setBoolean(dispatcher, false);
                toggled = true;
            } catch (Throwable ignored) {
                toggled = false;
            }
        }
        try {
            dispatcher.render(entity, 0.0D, 0.0D, 0.0D, 0.0F, 1.0F, stack, source, 15728880);
        } finally {
            if (toggled) {
                try {
                    ENTITY_SHADOW_FLAG.setBoolean(dispatcher, saved);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /** Entity-probe bounds from the last probe render: the framing centre is
     *  the raw envelope-box centre (symmetric margins in the preview square),
     *  and the drag hub is the volume-weighted cube mass centre via
     *  {@link #volumeWeightedCubeCenter}, falling back to the per-axis median
     *  when no cube can be reconstructed. The two pivots are separate because
     *  one point cannot serve both: the median weights every emitted cube
     *  equally, so the 5-9 detail cubes of an Alex's Mobs head zone (2026-11-21
     *  gorilla report; offline rebakes of gorilla/tiger/orca/elephant put the
     *  median 10-22% of the span off the torso) pulled the pivot onto the
     *  head, while the envelope centre follows sparse appendages instead
     *  (2026-11-17 winefox maid: tail and legs dragged it ~19% of the model
     *  height below the dense body mass). The span stays the AABB largest
     *  axis for the auto-fit. A non-null {@code hubOverride} replaces the
     *  vertex-derived hub entirely (maid doll previews, see
     *  {@link #entityHitboxHub}). */
    private static ModelBounds entityProbeBoundsFromVertices(Vector3f hubOverride) {
        return new ModelBounds(
                new Vector3f((FIXED_CONTEXT_PROBE.minX + FIXED_CONTEXT_PROBE.maxX) * 0.5F,
                        (FIXED_CONTEXT_PROBE.minY + FIXED_CONTEXT_PROBE.maxY) * 0.5F,
                        (FIXED_CONTEXT_PROBE.minZ + FIXED_CONTEXT_PROBE.maxZ) * 0.5F),
                hubOverride != null ? hubOverride : entityProbeHub(),
                Math.max(FIXED_CONTEXT_PROBE.maxX - FIXED_CONTEXT_PROBE.minX,
                        Math.max(FIXED_CONTEXT_PROBE.maxY - FIXED_CONTEXT_PROBE.minY,
                                FIXED_CONTEXT_PROBE.maxZ - FIXED_CONTEXT_PROBE.minZ)));
    }

    /** The drag hub for an entity probe: reconstructed cube mass centre, or
     *  the per-axis median when the stream yields no box (exotic renderers
     *  keep the pre-2026-11-21 pivot behaviour). */
    private static Vector3f entityProbeHub() {
        Vector3f hub = volumeWeightedCubeCenter();
        return hub != null ? hub
                : new Vector3f(median(FIXED_CONTEXT_PROBE.vx),
                        median(FIXED_CONTEXT_PROBE.vy),
                        median(FIXED_CONTEXT_PROBE.vz));
    }

    /** Relative shape tolerance for the cube reconstruction below: corner
     *  sets from one transformed cube agree far tighter than this, while
     *  vertices of different cubes differ by whole cube sizes. */
    private static final float CUBE_SHAPE_TOL = 0.02F;

    /** Volume-weighted centroid of the cubes reconstructed from the raw
     *  vertex stream (2026-11-21, Alex's Mobs entity previews): cube volume
     *  resists both known pivot failure modes at once — detail cubes (dense
     *  head zones) and thin appendages (long tails) weigh little, the torso
     *  dominates. The stream carries no cube identity, so cubes are rebuilt
     *  structurally: 4 consecutive samples form one quad, six consecutive
     *  quads whose 8 distinct corners share one circumsphere and pass the
     *  additive edge test below form one rectangular box (any orientation —
     *  bones rest-rotate and animate; corners dedupe exactly, which holds
     *  for vanilla/GeckoLib/Citadel cube emission). Lone quads (shadow
     *  planes, held items) and exotic geometry never complete a box and are
     *  skipped; {@code null} when no box is found so the caller can fall
     *  back to the per-axis median. */
    private static Vector3f volumeWeightedCubeCenter() {
        int quads = FIXED_CONTEXT_PROBE.vx.size() / 4;
        if (quads < 6) {
            return null;
        }
        float[] px = FIXED_CONTEXT_PROBE.vx.elements();
        float[] py = FIXED_CONTEXT_PROBE.vy.elements();
        float[] pz = FIXED_CONTEXT_PROBE.vz.elements();
        double accX = 0.0D;
        double accY = 0.0D;
        double accZ = 0.0D;
        double accVol = 0.0D;
        int cubes = 0;
        int q = 0;
        while (q + 6 <= quads) {
            float[] box = rectangularBoxCenterAndVolume(px, py, pz, q * 4);
            if (box != null) {
                accX += (double) box[0] * box[3];
                accY += (double) box[1] * box[3];
                accZ += (double) box[2] * box[3];
                accVol += box[3];
                cubes++;
                q += 6;
            } else {
                // resync one quad: lone quads and partial windows realign on
                // the next cube boundary
                q++;
            }
        }
        if (cubes == 0 || accVol <= 0.0D) {
            return null;
        }
        return new Vector3f((float) (accX / accVol), (float) (accY / accVol),
                (float) (accZ / accVol));
    }

    /** Centre + volume ({@code x, y, z, volume}) of one rectangular box
     *  spanning the 6 consecutive quads starting at sample index
     *  {@code start}, or {@code null} when those 24 samples are not a box:
     *  wrong unique-corner count, corners off the circumsphere, edges not
     *  additive, or degenerate extent. All checks are shape tests (rotation
     *  and uniform scale invariant), never position tests. */
    private static float[] rectangularBoxCenterAndVolume(float[] px, float[] py, float[] pz,
                                                         int start) {
        // exact-corner dedupe (shared corners are emitted bitwise-identical)
        float[] ux = new float[8];
        float[] uy = new float[8];
        float[] uz = new float[8];
        int n = 0;
        for (int i = 0; i < 24; i++) {
            float x = px[start + i];
            float y = py[start + i];
            float z = pz[start + i];
            int j = 0;
            while (j < n && (ux[j] != x || uy[j] != y || uz[j] != z)) {
                j++;
            }
            if (j == n) {
                if (n == 8) {
                    return null;
                }
                ux[n] = x;
                uy[n] = y;
                uz[n] = z;
                n++;
            }
        }
        if (n != 8) {
            return null;
        }
        float cx = 0.0F;
        float cy = 0.0F;
        float cz = 0.0F;
        for (int i = 0; i < 8; i++) {
            cx += ux[i];
            cy += uy[i];
            cz += uz[i];
        }
        cx *= 0.125F;
        cy *= 0.125F;
        cz *= 0.125F;
        // circumsphere: all corners equidistant from the centroid (forces
        // pairwise-orthogonal edges for a parallelepiped)
        float rMaxSq = 0.0F;
        float rMinSq = Float.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            float d = distSq(ux[i] - cx, uy[i] - cy, uz[i] - cz);
            rMaxSq = Math.max(rMaxSq, d);
            rMinSq = Math.min(rMinSq, d);
        }
        if (rMinSq <= 1.0e-12F) {
            return null;
        }
        if ((float) Math.sqrt(rMaxSq) - (float) Math.sqrt(rMinSq)
                > CUBE_SHAPE_TOL * (float) Math.sqrt(rMaxSq)) {
            return null;
        }
        // body diagonal: p0 and its antipode; the 6 remaining points give the
        // candidate edge/sum vectors from p0
        int pm = -1;
        float bestSq = -1.0F;
        for (int i = 1; i < 8; i++) {
            float d = distSq(ux[i] - ux[0], uy[i] - uy[0], uz[i] - uz[0]);
            if (d > bestSq) {
                bestSq = d;
                pm = i;
            }
        }
        if (bestSq <= 1.0e-12F) {
            return null;
        }
        float ax = 2.0F * cx - ux[0];
        float ay = 2.0F * cy - uy[0];
        float az = 2.0F * cz - uz[0];
        if (distSq(ux[pm] - ax, uy[pm] - ay, uz[pm] - az) > CUBE_SHAPE_TOL * CUBE_SHAPE_TOL * bestSq) {
            return null;
        }
        float[] vx = new float[6];
        float[] vy = new float[6];
        float[] vz = new float[6];
        float[] vl = new float[6];
        int m = 0;
        for (int i = 1; i < 8; i++) {
            if (i == pm) {
                continue;
            }
            vx[m] = ux[i] - ux[0];
            vy[m] = uy[i] - uy[0];
            vz[m] = uz[i] - uz[0];
            vl[m] = (float) Math.sqrt(distSq(vx[m], vy[m], vz[m]));
            m++;
        }
        // insertion sort by length (6 elements)
        for (int i = 1; i < 6; i++) {
            float l = vl[i];
            float x = vx[i];
            float y = vy[i];
            float z = vz[i];
            int j = i - 1;
            while (j >= 0 && vl[j] > l) {
                vl[j + 1] = vl[j];
                vx[j + 1] = vx[j];
                vy[j + 1] = vy[j];
                vz[j + 1] = vz[j];
                j--;
            }
            vl[j + 1] = l;
            vx[j + 1] = x;
            vy[j + 1] = y;
            vz[j + 1] = z;
        }
        if (vl[0] <= 1.0e-5F || vl[1] <= 1.0e-5F) {
            return null;
        }
        // u1, u2 = the two shortest; u1+u2 is present among the remaining
        // four; the third edge is the shortest of the last three (edge sums
        // of orthogonal edges are strictly longer than their summands)
        float sumTol = CUBE_SHAPE_TOL * (vl[0] + vl[1]);
        int sumIdx = -1;
        float sX = vx[0] + vx[1];
        float sY = vy[0] + vy[1];
        float sZ = vz[0] + vz[1];
        for (int i = 2; i < 6; i++) {
            if (Math.abs(vx[i] - sX) <= sumTol && Math.abs(vy[i] - sY) <= sumTol
                    && Math.abs(vz[i] - sZ) <= sumTol) {
                sumIdx = i;
                break;
            }
        }
        if (sumIdx < 0) {
            return null;
        }
        int u3 = -1;
        for (int i = 2; i < 6; i++) {
            if (i == sumIdx) {
                continue;
            }
            if (u3 < 0 || vl[i] < vl[u3]) {
                u3 = i;
            }
        }
        if (u3 < 0 || vl[u3] <= 1.0e-5F) {
            return null;
        }
        // the two leftovers must be u1+u3 and u2+u3
        float[] sumsX = {vx[0] + vx[u3], vx[1] + vx[u3]};
        float[] sumsY = {vy[0] + vy[u3], vy[1] + vy[u3]};
        float[] sumsZ = {vz[0] + vz[u3], vz[1] + vz[u3]};
        float sumTol3 = CUBE_SHAPE_TOL * (vl[0] + vl[u3] + vl[1]);
        boolean[] hit = new boolean[2];
        for (int i = 2; i < 6; i++) {
            if (i == sumIdx || i == u3) {
                continue;
            }
            for (int s = 0; s < 2; s++) {
                if (!hit[s] && Math.abs(vx[i] - sumsX[s]) <= sumTol3
                        && Math.abs(vy[i] - sumsY[s]) <= sumTol3
                        && Math.abs(vz[i] - sumsZ[s]) <= sumTol3) {
                    hit[s] = true;
                }
            }
        }
        if (!hit[0] || !hit[1]) {
            return null;
        }
        // orthogonality (implied by the circumsphere test; checked again as
        // the volume formula's premise)
        if (Math.abs(vx[0] * vx[1] + vy[0] * vy[1] + vz[0] * vz[1])
                > CUBE_SHAPE_TOL * vl[0] * vl[1]
                || Math.abs(vx[0] * vx[u3] + vy[0] * vy[u3] + vz[0] * vz[u3])
                        > CUBE_SHAPE_TOL * vl[0] * vl[u3]
                || Math.abs(vx[1] * vx[u3] + vy[1] * vy[u3] + vz[1] * vz[u3])
                        > CUBE_SHAPE_TOL * vl[1] * vl[u3]) {
            return null;
        }
        float vol = Math.abs(
                vx[0] * (vy[1] * vz[u3] - vz[1] * vy[u3])
                        + vy[0] * (vz[1] * vx[u3] - vx[1] * vz[u3])
                        + vz[0] * (vx[1] * vy[u3] - vy[1] * vx[u3]));
        if (vol <= 1.0e-7F) {
            return null;
        }
        return new float[] {cx, cy, cz, vol};
    }

    private static float distSq(float dx, float dy, float dz) {
        return dx * dx + dy * dy + dz * dz;
    }

    /** One uncached probe of an entity's rendered geometry (block units):
     *  entity renderers emit through the same VertexConsumer funnel as item
     *  renderers, so the recorder catches the real draw. */
    private static ModelBounds probeEntityBounds(Entity entity) {
        return probeEntityBounds(entity, null);
    }

    /** Hub-override variant: {@code hubOverride} (preview space — the parked
     *  entity's feet sit at the origin) replaces the vertex-derived drag hub
     *  for model families whose rendered geometry lies about the body centre,
     *  e.g. the TLM posé dolls (see {@link #entityHitboxHub}). */
    private static ModelBounds probeEntityBounds(Entity entity, Vector3f hubOverride) {
        FIXED_CONTEXT_PROBE.reset();
        boolean rendered = true;
        try {
            PoseStack probeStack = new PoseStack();
            renderPreviewEntity(entity, probeStack, FIXED_CONTEXT_PROBE);
        } catch (Throwable t) {
            LOGGER.warn("Entity preview size probe failed; keeping the raw preview scale", t);
            rendered = false;
        }
        return rendered && FIXED_CONTEXT_PROBE.any
                ? entityProbeBoundsFromVertices(hubOverride) : NO_BOUNDS;
    }

    /** Drag hub pinned to the entity's collision-box cube centre (preview
     *  space: the dispatcher renders the parked entity with its feet at the
     *  origin, so the box centre is x/z=0, y=height/2). The TLM maid previews
     *  use this (2026-11-21 maintainer call): every maid shares one humanoid
     *  hitbox, and the posé dolls' vertex clouds — dense hair/hat cubes,
     *  skirt volume — drag both the reconstructed-cube hub and its median
     *  fallback off the body. */
    private static Vector3f entityHitboxHub(Entity entity) {
        return new Vector3f(0.0F, entity.getBbHeight() * 0.5F, 0.0F);
    }

    /** The vehicle icon texture for a Superb Warfare EntityType id, or null
     *  when SBW ships none. Path mirrors SBW's ContainerItemDecorator: the
     *  entity id's path under {@code textures/vehicle_icon/container/},
     *  existence-checked against the resource manager (the gate upstream
     *  guarantees the superbwarfare namespace) and cached per vehicle path,
     *  misses included. Package-private: resolved by the SBW vehicle preview
     *  strategy's 2D icon path. */
    static ResourceLocation superbWarfareVehicleIcon(String entityId) {
        ResourceLocation typeId = ResourceLocation.tryParse(entityId);
        if (typeId == null) {
            return null;
        }
        String path = typeId.getPath();
        Object cached = VEHICLE_ICON_CACHE.get(path);
        if (cached != null) {
            return cached == NO_ICON ? null : (ResourceLocation) cached;
        }
        ResourceLocation icon = ResourceLocation.fromNamespaceAndPath(SUPERB_WARFARE_MOD_ID,
                "textures/vehicle_icon/container/" + path + ".png");
        boolean present;
        try {
            present = Minecraft.getInstance().getResourceManager().getResource(icon).isPresent();
        } catch (Throwable t) {
            present = false;
        }
        VEHICLE_ICON_CACHE.put(path, present ? icon : NO_ICON);
        return present ? icon : null;
    }

    /** The vehicle icon drawn like any other 2D preview icon: the full cell
     *  rect (SBW's decorator stretches the texture into the whole 16x16
     *  slot; the preview cell is 16*scale px, same geometry as
     *  renderItem2D). Serves both the 2D grids and the 3D branch's icon
     *  fallback; drag rotation is ignored, like the flat icon path.
     *  Package-private: drawn by the SBW vehicle preview strategy. */
    static void renderVehicleIcon(GuiGraphics gg, ResourceLocation icon, float cx, float cy, float scale) {
        int size = Math.max(1, Math.round(16.0F * scale));
        blitTextured(gg, icon, Math.round(cx), Math.round(cy), size, size);
    }

    /** Superb Warfare 3D preview: same pose convention as the vanilla branch,
     *  but the render runs under {@code ItemDisplayContext.FIXED} — the
     *  item-frame context. The mod's separate-transforms model routes the gui
     *  perspective to a flat 2D icon and only the other perspectives carry
     *  the 3D gun (a GeckoLib custom renderer behind runtime display
     *  settings), so FIXED is the cheapest context with a calibrated
     *  one-block transform. The custom renderer exposes no quads, so the
     *  size is measured by probing the render itself ({@link
     *  #fixedContextBounds}): oversized guns shrink to the preview square via
     *  {@link #fitScaleFor} and rotate around their rendered centre, exactly
     *  like the vanilla and TACZ branches. A renderer failure degrades to the
     *  2D icon. {@code restPose} is an optional model-space pose applied under
     *  the drag quaternion (per-mod rest orientations, e.g. the Sophisticated
     *  Backpacks pocket-face flip; null = none). Package-private: the SBW gun
     *  and SBP shell preview strategies' 3D body, and the generic
     *  custom-renderer branch of renderItem2D. */
    static void renderFixedContextModel3D(GuiGraphics gg, ItemStack item, LivingEntity player,
                                                  float cx, float cy, Quat rotation, float scale,
                                                  Quaternionf restPose) {
        renderFixedContextModel3D(gg, item, player, cx, cy, rotation, scale, restPose, null);
    }

    /** Model-override variant: renders the given baked model instead of the
     *  item's own (the 2D barrier-icon rule feeds the block model here). */
    static void renderFixedContextModel3D(GuiGraphics gg, ItemStack item, LivingEntity player,
                                                  float cx, float cy, Quat rotation, float scale,
                                                  Quaternionf restPose, BakedModel modelOverride) {
        BakedModel model = modelOverride != null ? modelOverride
                : Minecraft.getInstance().getItemRenderer().getModel(item, player.level(), player, 0);
        ModelBounds bounds = fixedContextBounds(item, model);
        float fitScale = fitScaleFor(bounds.span(), scale);
        if (bounds != NO_BOUNDS) {
            fitScale *= SBW_FIT_BOOST;
        }
        PoseStack pose = gg.pose();
        pose.pushPose();
        pose.translate(cx, cy, 100.0F);
        pose.translate(8.0F * scale, 8.0F * scale, 0.0F);
        pose.scale(1.0F, -1.0F, 1.0F);
        SCRATCH_QUAT.set(rotation.x(), rotation.y(), rotation.z(), rotation.w()).normalize();
        pose.mulPose(SCRATCH_QUAT);
        if (restPose != null) {
            // Optional rest pose (see the shell strategy): conjugated UNDER
            // the drag quat (model space) so dragging keeps rotating around
            // the screen axes; envelope centres are symmetric under it, so
            // fit and centring are untouched.
            pose.mulPose(restPose);
        }
        Lighting.setupForEntityInInventory();
        pose.scale(16.0F * fitScale, 16.0F * fitScale, 16.0F * fitScale);
        // Rotate around the model's geometric centre, not the model origin:
        // the fixed display transforms park the geometry far from the origin,
        // so an origin pivot would swing the gun around a point outside its
        // body. Same convention as the vanilla and TACZ branches.
        Vector3f center = bounds.center();
        pose.translate(-center.x, -center.y, -center.z);
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.mul(pose.last().pose());
        RenderSystem.applyModelViewMatrix();
        PoseStack renderStack = REUSABLE_POSE_STACK;
        renderStack.setIdentity();
        boolean rendered = true;
        try {
            Minecraft.getInstance().getItemRenderer().render(item, ItemDisplayContext.FIXED, false,
                    renderStack, bufferSource, 15728880, OverlayTexture.NO_OVERLAY, model);
            bufferSource.endBatch();
        } catch (Throwable t) {
            LOGGER.warn("Superb Warfare 3D preview render failed; falling back to the flat icon", t);
            rendered = false;
        } finally {
            modelViewStack.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.enableDepthTest();
            pose.popPose();
        }
        if (!rendered) {
            // Flat body, not renderItem2D: for builtin/entity models that
            // would re-delegate here and loop.
            renderItem2DFlat(player, gg, item, cx, cy, scale);
        }
    }

    /** Offline probe sink for {@link #fixedContextBounds}: hands the probe
     *  render one vertex recorder regardless of render type and discards
     *  everything else, so the measurement draws nothing and touches no
     *  shared GPU batch. Vanilla-default VertexConsumer flows (foil glint,
     *  11-arg addVertex) all funnel into these abstract methods. */
    private static final class FixedContextProbe implements MultiBufferSource, VertexConsumer {
        private float minX, minY, minZ, maxX, maxY, maxZ;
        private boolean any;
        /** Raw vertex cloud for the drag hub (cube reconstruction, see
         *  {@link #entityProbeHub}); transient, one probe render. */
        private final FloatArrayList vx = new FloatArrayList();
        private final FloatArrayList vy = new FloatArrayList();
        private final FloatArrayList vz = new FloatArrayList();

        /** Sibling view of a primary probe (see {@link #getBuffer}): every
         *  record routes into the primary's shared cloud. */
        private final FixedContextProbe shared;

        private FixedContextProbe() {
            this.shared = null;
        }

        private FixedContextProbe(FixedContextProbe shared) {
            this.shared = shared;
        }

        void reset() {
            minX = minY = minZ = Float.MAX_VALUE;
            maxX = maxY = maxZ = -Float.MAX_VALUE;
            any = false;
            vx.clear();
            vy.clear();
            vz.clear();
        }

        /** Distinct consumer per getBuffer call: VertexMultiConsumer
         *  compositing (the held-item glint path — cataclysm the_harbinger,
         *  2026-11-21) throws "Duplicate delegates" when both delegates are
         *  the same instance, which killed the whole probe. Every view
         *  records into this primary's shared cloud, so the measured
         *  geometry is unchanged. */
        @Override
        public VertexConsumer getBuffer(RenderType renderType) {
            if (shared != null) {
                return shared.getBuffer(renderType);
            }
            return new FixedContextProbe(this);
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            if (shared != null) {
                return shared.addVertex(x, y, z);
            }
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
            vx.add(x);
            vy.add(y);
            vz.add(z);
            any = true;
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }
    }

    private static final FixedContextProbe FIXED_CONTEXT_PROBE = new FixedContextProbe();

    /** Centre + largest-axis span (block units) of a Superb Warfare item's
     *  rendered FIXED-context geometry, computed once per baked model by
     *  running the real render chain into {@link #FIXED_CONTEXT_PROBE} on a
     *  throwaway stack: the vertices reach the recorder in display-transform
     *  space (fixed ItemTransform applied, bones resolved, model units
     *  already divided down to blocks) — exactly the space the preview pose
     *  scales, so centre and span are directly usable by {@link
     *  #fitScaleFor}. A failed or empty probe caches the sentinel and keeps
     *  the historical raw-scale behaviour. */
    private static ModelBounds fixedContextBounds(ItemStack item, BakedModel model) {
        Object cached = FIXED_CONTEXT_BOUNDS.get(model);
        if (cached instanceof ModelBounds bounds) {
            return bounds;
        }
        if (cached == NO_CENTER) {
            return NO_BOUNDS;
        }
        FIXED_CONTEXT_PROBE.reset();
        boolean rendered = true;
        try {
            PoseStack probeStack = new PoseStack();
            Minecraft.getInstance().getItemRenderer().render(item, ItemDisplayContext.FIXED, false,
                    probeStack, FIXED_CONTEXT_PROBE, 15728880, OverlayTexture.NO_OVERLAY, model);
        } catch (Throwable t) {
            LOGGER.warn("Superb Warfare 3D preview size probe failed; keeping the raw preview scale", t);
            rendered = false;
        }
        ModelBounds bounds = NO_BOUNDS;
        if (rendered && FIXED_CONTEXT_PROBE.any) {
            bounds = ModelBounds.of(
                    new Vector3f(
                            (FIXED_CONTEXT_PROBE.minX + FIXED_CONTEXT_PROBE.maxX) * 0.5F,
                            (FIXED_CONTEXT_PROBE.minY + FIXED_CONTEXT_PROBE.maxY) * 0.5F,
                            (FIXED_CONTEXT_PROBE.minZ + FIXED_CONTEXT_PROBE.maxZ) * 0.5F),
                    Math.max(FIXED_CONTEXT_PROBE.maxX - FIXED_CONTEXT_PROBE.minX,
                            Math.max(FIXED_CONTEXT_PROBE.maxY - FIXED_CONTEXT_PROBE.minY,
                                    FIXED_CONTEXT_PROBE.maxZ - FIXED_CONTEXT_PROBE.minZ)));
        }
        FIXED_CONTEXT_BOUNDS.put(model, bounds == NO_BOUNDS ? NO_CENTER : bounds);
        return bounds;
    }

    /** TACZ gun 3D render: same pose convention as the vanilla branch, but
     *  the model is a {@link BedrockGunModel} drawn on an identity stack via
     *  RenderSystem's model-view matrix — the vanilla ItemRenderer path only
     *  draws the flat slot texture. Package-private: the TACZ gun preview
     *  strategy's 3D body. */
    static void renderGunModel3D(GuiGraphics gg, ItemStack item, int cx, int cy,
                                         Quat rotation, float scale,
                                         BedrockGunModel gunModel, GunDisplayInstance display) {
        // The bedrock model is a singleton shared with TACZ's own renderers
        // (hand, ground, TACZ UI): their animation state machine writes bone
        // offsets/quaternions/scales in place and only resets them after each
        // of its own renders. Measuring the envelope on such a residue caches
        // a posed centre that then disagrees with every later frame (our
        // renders run on the cleaned state and re-clean after drawing) — the
        // gun visibly sits off-centre (2026-11-22 fabric report). Reset to
        // the default pose first so the cached envelope always matches what
        // this render — and every later one — draws.
        gunModel.cleanAnimationTransform();
        // Auto-fit: gun models routinely span 2-3 blocks; shrink so the
        // largest axis stays inside the preview square (normal guns keep
        // their tuned look via the same PREVIEW_FILL rule as vanilla items).
        float fitScale = fitScaleFor(gunModelSpan(gunModel), scale);
        Vector3f logCenter = gunModelCenter(gunModel);
        IGun gunApi = IGun.getIGunOrNull(item);
        String gunId = gunApi != null && gunApi.getGunId(item) != null
                ? gunApi.getGunId(item).toString() : "unknown";
        if (GUN_PREVIEW_LOGGED.add(gunId)) {
            LOGGER.info("[csgobox] tacz gun preview: gun={} center=({}, {}, {}) span={} fitScale={}",
                    gunId,
                    String.format("%.3f", logCenter.x), String.format("%.3f", logCenter.y),
                    String.format("%.3f", logCenter.z),
                    String.format("%.3f", GUN_MODEL_SPANS.getOrDefault(gunModel, 0.0F)),
                    String.format("%.3f", fitScale));
        }
        PoseStack pose = gg.pose();
        pose.pushPose();
        pose.translate(cx + 8.0F * scale, cy + 8.0F * scale, 100.0F);
        pose.scale(1.0F, -1.0F, 1.0F);
        // Mirror-conjugated drag (negate the quat's x/z components) to pair
        // with the cancelling flip below: without it every drag would spin
        // the gun inverted relative to every other preview item.
        SCRATCH_QUAT.set(-rotation.x(), rotation.y(), -rotation.z(), rotation.w()).normalize();
        pose.mulPose(SCRATCH_QUAT);
        // Cancel the GUI y-flip for the bedrock mesh. Vanilla-baked item quads
        // and entity models arrive pre-flipped for the GUI convention, but
        // TACZ's bedrock gun content does not — the outer flip alone rendered
        // the vertical mirror of TACZ's item-frame pose (grip up, 2026-11-21
        // report). With the flip pair the net image is exactly the walk's own
        // result: grip down, item-frame orientation.
        pose.scale(1.0F, -1.0F, 1.0F);
        // TACZ lays the gun out per display context with a "positioning node"
        // bone inside the bedrock model: GunItemRendererWrapper's
        // applyPositioningNodeTransform walks that bone path (leaf -> root)
        // stacking its rotations for the FIXED/item-frame perspective — that
        // is what makes guns sit horizontal in item frames. Drawing the model
        // directly skipped it, so the 3D preview showed the raw authored pose
        // (muzzle up, 2026-11-20 report). Replicate the same rotation walk
        // under the drag rotation; the walk's translations and the display's
        // fixed scale are skipped — this preview re-centres on the envelope
        // centre and rescales via fitScaleFor, so only the orientation
        // matters (a translation never contributes rotation, and the uniform
        // fixed scale is absorbed by the fit).
        applyGunOriginPathRotation(pose, gunModel);
        pose.scale(16.0F * fitScale, 16.0F * fitScale, 16.0F * fitScale);
        // Rotate around the model's geometric centre, not the model origin:
        // the gun's root node sits far from its geometry, so a pivot at the
        // origin would swing the gun around a point outside the model.
        Vector3f center = gunModelCenter(gunModel);
        pose.translate(-center.x, -center.y, -center.z);
        Lighting.setupForEntityInInventory();
        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.mul(pose.last().pose());
        RenderSystem.applyModelViewMatrix();
        REUSABLE_POSE_STACK.setIdentity();
        RenderType renderType = display.enablesTransparency()
                ? RenderType.entityTranslucent(display.getModelTexture())
                : RenderType.entityCutout(display.getModelTexture());
        try {
            gunModel.render(REUSABLE_POSE_STACK, item, ItemDisplayContext.GUI, renderType,
                    0xF000F0, OverlayTexture.NO_OVERLAY);
            gunModel.cleanAnimationTransform();
            Minecraft.getInstance().renderBuffers().bufferSource().endBatch();
        } catch (Throwable t) {
            LOGGER.warn("TACZ gun model render failed in 3D preview", t);
        } finally {
            modelViewStack.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.enableDepthTest();
            pose.popPose();
        }
    }

    /** Stacks the gun's FIXED-context positioning-node bone rotations
     *  (leaf -> root, negative-axis quaternions) exactly like TACZ's
     *  {@code GunItemRendererWrapper.applyPositioningNodeTransform} does for
     *  the item-frame perspective. The walk's translations are skipped: they
     *  only place the model relative to the node origin, which this preview
     *  replaces with its own envelope-centre centring — and a translation
     *  never contributes rotation. Guns whose bedrock model defines no
     *  positioning node keep the raw authored pose (same as TACZ's own
     *  null guard). */
    private static void applyGunOriginPathRotation(PoseStack pose, BedrockGunModel gunModel) {
        List<BedrockPart> originPath = gunModel.getFixedOriginPath();
        if (originPath == null) {
            return;
        }
        for (int i = originPath.size() - 1; i >= 0; i--) {
            BedrockPart bone = originPath.get(i);
            pose.mulPose(Axis.XN.rotation(bone.xRot));
            pose.mulPose(Axis.YN.rotation(bone.yRot));
            pose.mulPose(Axis.ZN.rotation(bone.zRot));
        }
    }

    /** Model-space centre of the gun's default-pose bounding box (block
     *  units), computed once per model by walking the part tree with the base
     *  bone rotations ({@code cleanAnimationTransform} does not reset these). */
    public static Vector3f gunModelCenter(BedrockGunModel model) {
        return gunBounds(model).center();
    }

    /** Largest-axis span (block units) of the gun's default-pose bounding
     *  box, used by {@link #fitScaleFor} to auto-shrink oversized gun models
     *  so they stay inside the preview square. */
    private static float gunModelSpan(BedrockGunModel model) {
        return gunBounds(model).span();
    }

    /** Centre + span of the gun's default-pose bounding box, computed once
     *  per model by walking the part tree with the base bone rotations
     *  ({@code cleanAnimationTransform} does not reset these). */
    private static ModelBounds gunBounds(BedrockGunModel model) {
        Vector3f cached = GUN_MODEL_CENTERS.get(model);
        if (cached instanceof Vector3f) {
            return ModelBounds.of(cached, GUN_MODEL_SPANS.getOrDefault(model, 1.0F));
        }
        float[] min = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
        float[] max = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        BedrockPart root = model.getRootNode();
        if (root != null) {
            collectPartBounds(root, new Matrix4f(), min, max);
        }
        ModelBounds bounds;
        if (min[0] == Float.MAX_VALUE) {
            bounds = NO_BOUNDS;
        } else {
            bounds = ModelBounds.of(
                    new Vector3f((min[0] + max[0]) / 2, (min[1] + max[1]) / 2, (min[2] + max[2]) / 2),
                    Math.max(max[0] - min[0], Math.max(max[1] - min[1], max[2] - min[2])));
        }
        GUN_MODEL_CENTERS.put(model, bounds.center());
        GUN_MODEL_SPANS.put(model, bounds.span());
        return bounds;
    }

    private static void collectPartBounds(BedrockPart part, Matrix4f parent, float[] min, float[] max) {
        if (!part.visible) {
            return;
        }
        // Mirrors BedrockPart.translateAndRotateAndScale with the animation
        // state cleared (offset/quaternion/scales are identity by default).
        Matrix4f m = new Matrix4f(parent);
        // Two consecutive translates compose into one; /16 is exact (power of
        // two), so pre-division is lossless.
        m.translate(part.offsetX + part.x / 16.0F,
                part.offsetY + part.y / 16.0F,
                part.offsetZ + part.z / 16.0F);
        if (part.zRot != 0.0F) {
            m.rotateZ(part.zRot);
        }
        if (part.yRot != 0.0F) {
            m.rotateY(part.yRot);
        }
        if (part.xRot != 0.0F) {
            m.rotateX(part.xRot);
        }
        // JOML 1.10.5 Matrix4f.rotate has no identity short-circuit and always
        // builds a quat->matrix product; the quaternion is identity in every
        // default-pose model (constructor and cleanAnimationTransform reset
        // it), so skip the full multiply when it is.
        if (part.additionalQuaternion.w != 1.0F || part.additionalQuaternion.x != 0.0F
                || part.additionalQuaternion.y != 0.0F || part.additionalQuaternion.z != 0.0F) {
            m.rotate(part.additionalQuaternion);
        }
        m.scale(part.xScale, part.yScale, part.zScale);
        for (BedrockCube cube : part.cubes) {
            // Cube coordinates are in 1/16-block pixels; all 8 corners are
            // needed because the base bone rotations turn boxes into OBBs.
            if (cube instanceof BedrockCubeBox box) {
                addBoxCorners(m, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, min, max);
            } else if (cube instanceof BedrockCubePerFace face) {
                addBoxCorners(m, face.minX, face.minY, face.minZ, face.maxX, face.maxY, face.maxZ, min, max);
            }
        }
        for (BedrockPart child : part.children) {
            collectPartBounds(child, m, min, max);
        }
    }

    private static void addBoxCorners(Matrix4f m, float minX, float minY, float minZ,
                                      float maxX, float maxY, float maxZ, float[] min, float[] max) {
        float mnX = minX / 16.0F, mnY = minY / 16.0F, mnZ = minZ / 16.0F;
        float mxX = maxX / 16.0F, mxY = maxY / 16.0F, mxZ = maxZ / 16.0F;
        Vector4f v = new Vector4f();
        addCorner(m, mnX, mnY, mnZ, v, min, max);
        addCorner(m, mnX, mnY, mxZ, v, min, max);
        addCorner(m, mnX, mxY, mnZ, v, min, max);
        addCorner(m, mnX, mxY, mxZ, v, min, max);
        addCorner(m, mxX, mnY, mnZ, v, min, max);
        addCorner(m, mxX, mnY, mxZ, v, min, max);
        addCorner(m, mxX, mxY, mnZ, v, min, max);
        addCorner(m, mxX, mxY, mxZ, v, min, max);
    }

    private static void addCorner(Matrix4f m, float x, float y, float z, Vector4f v, float[] min, float[] max) {
        v.set(x, y, z, 1.0F);
        m.transform(v);
        if (v.x < min[0]) min[0] = v.x;
        if (v.y < min[1]) min[1] = v.y;
        if (v.z < min[2]) min[2] = v.z;
        if (v.x > max[0]) max[0] = v.x;
        if (v.y > max[1]) max[1] = v.y;
        if (v.z > max[2]) max[2] = v.z;
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
            LOGGER.info("[csgobox] 3D preview env: supports3D={} iris={} oculus={} modernui={}",
                    !isShaderModActive(), ModList.get().isLoaded("iris"),
                    ModList.get().isLoaded("oculus"), ModList.get().isLoaded("modernui"));
        }
        return !isShaderModActive();
    }

    /** True when Iris (NeoForge/Fabric) or Oculus (Forge) is installed: their
     *  shader packs intercept/ignore custom GUI 3D paths, so 3D previews
     *  degrade to 2D for correctness (probabilities/gameplay unaffected).
     *  Modern UI is intentionally excluded — see supports3D(). */
    /** Package-private: the SBP shell strategy's 2D pass mirrors the generic
     *  custom-renderer guard (shader packs keep the flat icon). */
    static boolean isShaderModActive() {
        return ModList.get().isLoaded("iris") || ModList.get().isLoaded("oculus");
    }

    // ===================== SlashBlade rack-form preview (挂刀架形态) =====================

    /** SlashBlade (拔刀剑: Resharped) item namespace. The namespace check
     *  alone proves the optional mod is loaded; no SlashBlade class is ever
     *  referenced at compile time — the blade gate resolves the base item
     *  class reflectively (cached), and the preview below speaks to the mod
     *  through vanilla APIs and two public fields only. */
    private static final String SLASHBLADE_MOD_ID = "slashblade";

    /** The weapon-rack entity ({@code BladeStandEntity}, 刀挂台) and the
     *  floor-stand item ({@code BLADESTAND_1}) the preview mounts blades on.
     *  The entity id derivation is jar-verified (2.1.9 and 2.2.1): the mod
     *  builds entity ids via {@code CaseFormat(UPPER_CAMEL → LOWER_UNDERSCORE)
     *  (getSimpleName()).replace("entity_", "")} — {@code BladeStandEntity}
     *  carries no {@code entity_} substring, so the id is the plain
     *  snake-case form, matching the {@code entity.slashblade.
     *  blade_stand_entity} lang key. */
    private static final ResourceLocation SLASHBLADE_STAND_TYPE =
            ResourceLocation.fromNamespaceAndPath(SLASHBLADE_MOD_ID, "blade_stand_entity");
    private static final ResourceLocation SLASHBLADE_STAND_ITEM =
            ResourceLocation.fromNamespaceAndPath(SLASHBLADE_MOD_ID, "bladestand_1");
    /** The wall-mount stand ({@code BLADESTAND_1_W}): its renderer branch
     *  (YP 180, skipping the floor branches' XP(-90)) keeps the common tail
     *  XP(90) uncancelled and stands the blade upright — the 立式 style of
     *  the random pair. The floor stands' XP(-90) cancels that tail, which
     *  is the horizontal rack pose (挂式). */
    private static final ResourceLocation SLASHBLADE_STAND_ITEM_WALL =
            ResourceLocation.fromNamespaceAndPath(SLASHBLADE_MOD_ID, "bladestand_1w");

    /** {@code mods.flammpfeil.slashblade.item.ItemSlashBlade}, resolved once
     *  after the namespace gate proved the mod is on the classpath (the
     *  detuned variant and every named blade extend it). Null when
     *  unresolvable: the gate then never matches. */
    private static Class<?> SLASHBLADE_BLADE_CLASS;
    private static boolean SLASHBLADE_BLADE_CLASS_RESOLVED;

    /** {@code BladeStandEntity.currentType} (the rack model branch selector)
     *  and {@code currentTypeStack} (the mounted stack) — both public fields
     *  on the mod's entity (javap-verified on 2.1.9/2.2.1), reached by
     *  reflection so this file stays SlashBlade-class-free at compile time.
     *  Null after a failed resolve and the preview degrades to the flat
     *  icon. */
    private static Field SLASHBLADE_STAND_TYPE_FIELD;
    private static Field SLASHBLADE_STAND_STACK_FIELD;
    private static boolean SLASHBLADE_STAND_FIELDS_RESOLVED;

    /** Probe-measured bounds of each blade variant on the shared rack stand,
     *  keyed by item id + hover name: the 24 named blades share one item id
     *  ({@code slashblade:slashblade}) and differ by their {@code
     *  blade_state} identity, which the hover name reflects — the per-type
     *  entity cache cannot be reused because blade geometry varies per
     *  variant. Same sentinel semantics as {@link #ARMOR_PREVIEW_BOUNDS}. */
    private static final Map<String, Object> SLASHBLADE_STAND_BOUNDS = new HashMap<>();

    /** One-shot INFO guard per blade variant for the rack-preview probe. */
    private static final Set<String> SLASHBLADE_MOUNT_LOG = new HashSet<>();

    /** Variant key ({@link #slashBladeVariantKey}) of the blade the current
     *  3D preview last rendered (null = none) — arms the inspect-button
     *  style toggle (see {@link SlashBladePreview}). */
    private static String slashBladePreviewKey;

    /** Display style of the previewed blade: false = floor rack (horizontal,
     *  挂式), true = wall mount (vertical, 立式). Re-randomized whenever the
     *  previewed variant changes — a screen open clears the marker first
     *  (see {@link #clearSlashBladePreviewInstance}), so every fresh look
     *  re-rolls the dice; the inspect button flips it in between. */
    private static boolean slashBladeUpright;

    /** True for SlashBlade blade items: the namespace gate proves the mod is
     *  loaded (no isModLoaded round-trip needed), then the cached reflective
     *  instanceof keeps the rack items (HangingEntityItem) and proud-soul
     *  materials (plain Item) out of the rack preview. Package-private: the
     *  SlashBlade preview strategy gates on it. */
    static boolean isSlashBladeBlade(ItemStack stack) {
        if (stack.isEmpty()
                || !SLASHBLADE_MOD_ID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace())) {
            return false;
        }
        if (!SLASHBLADE_BLADE_CLASS_RESOLVED) {
            SLASHBLADE_BLADE_CLASS_RESOLVED = true;
            try {
                SLASHBLADE_BLADE_CLASS = Class.forName("mods.flammpfeil.slashblade.item.ItemSlashBlade");
            } catch (Throwable t) {
                SLASHBLADE_BLADE_CLASS = null;
            }
        }
        return SLASHBLADE_BLADE_CLASS != null && SLASHBLADE_BLADE_CLASS.isInstance(stack.getItem());
    }

    /** SlashBlade blade 3D preview in the weapon-rack form (挂架形态): the
     *  mod's BEWLR only draws the real 3D blade model when the stack is
     *  mounted in its {@code BladeStandEntity} — every other FIXED context
     *  draws the flat icon (renderBlade bytecode: the {@code isFramed &&
     *  getFrame() instanceof BladeStandEntity} gate routes to
     *  {@code renderModel}; everything else renders the icon). So park a
     *  shared client-side rack entity, mount the blade through its two
     *  public fields ({@code currentTypeStack} = the blade — the rack
     *  renderer re-applies {@code setEntityRepresentation(this)} on its
     *  EMPTY rebuild branch, opening the gate; {@code currentType} = a stand
     *  item — the pose branch selector: {@code bladestand_1w} stands the
     *  blade upright, the floor stands keep the horizontal rack pose), and
     *  let the mod's own {@code BladeStandEntityRenderer} run its chain
     *  (anchor translate, 45° rotation steps, ×2 scale). The ItemFrame's
     *  own item stays EMPTY so the renderer's second renderItem — the rack
     *  body — hits its empty-stack early return (javap both mod lines) and
     *  only the blade enters the preview; probe-based auto-fit, centring
     *  and {@link #SBW_FIT_BOOST} apply on top. Zero SlashBlade class
     *  references: the entity comes from the vanilla registry, the fields
     *  are written reflectively. Degrades to the flat icon when the entity,
     *  the fields or the render are unavailable. Package-private: the
     *  SlashBlade preview strategy's 3D body. */
    static void renderSlashBladeRackPreview(GuiGraphics gg, ItemStack item, LivingEntity player,
                                            int cx, int cy, Quat rotation, float scale) {
        Entity stand = slashBladeStandInstance(player.level());
        if (stand != null) {
            markSlashBladePreview(slashBladeVariantKey(item));
            if (mountSlashBladeOnStand(stand, item, slashBladeUpright)) {
                ModelBounds bounds = slashBladeStandBounds(item, stand, slashBladeUpright);
                if (bounds != NO_BOUNDS && renderEntityPreview(gg, stand, bounds, cx, cy, rotation, scale)) {
                    return;
                }
            }
        }
        renderItem2DFlat(player, gg, item, cx, cy, scale);
    }

    /** Cache/identity key of a blade variant: the named blades share one
     *  item id and differ by their {@code blade_state} identity, which the
     *  hover name reflects. */
    private static String slashBladeVariantKey(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()
                + "#" + stack.getHoverName().getString();
    }

    /** Arms the inspect-button style toggle for {@code key}: re-randomizes
     *  the display style whenever the previewed variant changes. */
    private static void markSlashBladePreview(String key) {
        if (!key.equals(slashBladePreviewKey)) {
            slashBladePreviewKey = key;
            slashBladeUpright = ThreadLocalRandom.current().nextBoolean();
        }
    }

    /** True while the current 3D preview renders a SlashBlade blade.
     *  Package-private: the {@link SlashBladePreview} facade relays it to
     *  the look screens (the facade also keeps this platform-only public
     *  surface out of the drift-checked AnimRenderOps op table). */
    static boolean hasSlashBladePreviewInstance() {
        return slashBladePreviewKey != null;
    }

    /** Drops the blade marker; called when a look screen opens so the next
     *  preview re-rolls its random style. */
    static void clearSlashBladePreviewInstance() {
        slashBladePreviewKey = null;
    }

    /** Flips the previewed blade between the floor rack (horizontal, 挂式)
     *  and the wall mount (vertical, 立式) — the inspect button's toggle. */
    static void toggleSlashBladeStyle() {
        slashBladeUpright = !slashBladeUpright;
    }

    /** The parked client-side rack entity for the rack-form preview, or null
     *  when the entity id is gone. Cached in the shared entity preview pool
     *  (one rack instance ever; the mounted stack is re-applied every
     *  frame). */
    private static Entity slashBladeStandInstance(Level level) {
        // DefaultedRegistry.get falls back to the pig for unknown ids, so
        // existence must go through containsKey.
        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(SLASHBLADE_STAND_TYPE)) {
            return null;
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(SLASHBLADE_STAND_TYPE);
        return type == null ? null : entityPreviewInstance(type, level);
    }

    /** Mounts {@code stack} on the parked rack through the two public fields
     *  (resolved once, reflectively): {@code currentTypeStack} = a copy of
     *  the blade, {@code currentType} = the stand item of the requested
     *  style ({@code upright} = wall mount / 立式, else the floor rack /
     *  挂式). The copy carries the entity representation itself: the rack
     *  renderer re-applies {@code setEntityRepresentation} only when its own
     *  {@code currentTypeStack} is EMPTY (the rebuild branch), so a directly
     *  mounted non-empty stack without it never opens the TEISR renderModel
     *  gate. The ItemFrame's own item is forced EMPTY: doRender's second
     *  renderItem would draw it as the rack body, and its empty-stack guard
     *  (javap: isEmpty → early return, both mod lines) skips that draw —
     *  only the blade renders. False when the fields are unreachable or the
     *  stand item is gone. */
    private static boolean mountSlashBladeOnStand(Entity stand, ItemStack stack, boolean upright) {
        if (!SLASHBLADE_STAND_FIELDS_RESOLVED) {
            SLASHBLADE_STAND_FIELDS_RESOLVED = true;
            try {
                Class<?> cls = Class.forName("mods.flammpfeil.slashblade.entity.BladeStandEntity");
                SLASHBLADE_STAND_TYPE_FIELD = cls.getField("currentType");
                SLASHBLADE_STAND_STACK_FIELD = cls.getField("currentTypeStack");
            } catch (Throwable t) {
                LOGGER.warn("SlashBlade rack preview: BladeStandEntity fields unreachable; degrading to the flat icon", t);
                SLASHBLADE_STAND_TYPE_FIELD = null;
                SLASHBLADE_STAND_STACK_FIELD = null;
            }
        }
        if (SLASHBLADE_STAND_TYPE_FIELD == null || SLASHBLADE_STAND_STACK_FIELD == null) {
            return false;
        }
        try {
            // DefaultedRegistry.get falls back to air for unknown ids — the
            // containsKey guard keeps an absent stand item out of the mount.
            ResourceLocation standId = upright ? SLASHBLADE_STAND_ITEM_WALL : SLASHBLADE_STAND_ITEM;
            Item standItem = BuiltInRegistries.ITEM.containsKey(standId)
                    ? BuiltInRegistries.ITEM.get(standId) : null;
            if (standItem == null) {
                return false;
            }
            ItemStack copy = stack.copy();
            copy.setEntityRepresentation(stand);
            SLASHBLADE_STAND_TYPE_FIELD.set(stand, standItem);
            SLASHBLADE_STAND_STACK_FIELD.set(stand, copy);
            // The rack body draw is the renderer's second renderItem over
            // the ItemFrame's own item; an empty item hits its isEmpty
            // early return (javap both mod lines) so only the blade draws
            // (maintainer call 2026-11-17: blade only, no rack body). The
            // pose branch reads the currentType field, not this item.
            if (stand instanceof ItemFrame frame) {
                frame.setItem(ItemStack.EMPTY);
            }
            return true;
        } catch (Throwable t) {
            LOGGER.warn("SlashBlade rack preview: mounting the blade failed; degrading to the flat icon", t);
            return false;
        }
    }

    /** Bounds of a blade variant mounted on the shared rack stand, cached per
     *  variant + display style (the horizontal rack pose and the upright wall
     *  pose bound differently). The stand must already be mounted when
     *  probing. */
    private static ModelBounds slashBladeStandBounds(ItemStack stack, Entity stand, boolean upright) {
        String cacheKey = slashBladeVariantKey(stack) + "#" + (upright ? "w" : "r");
        Object cached = SLASHBLADE_STAND_BOUNDS.get(cacheKey);
        if (cached instanceof ModelBounds bounds) {
            return bounds;
        }
        if (cached == NO_BOUNDS) {
            return NO_BOUNDS;
        }
        ModelBounds bounds = probeEntityBounds(stand);
        SLASHBLADE_STAND_BOUNDS.put(cacheKey, bounds == NO_BOUNDS ? NO_BOUNDS : bounds);
        if (SLASHBLADE_MOUNT_LOG.add(cacheKey)) {
            LOGGER.info("[csgobox] slashblade rack preview: mounted {} span={}",
                    cacheKey, bounds == NO_BOUNDS ? "none" : bounds.span());
        }
        return bounds;
    }

    /** Preview player of the SlashBlade combo-motion inspect (see
     *  {@link SlashBladeComboInspect}): a dedicated carrier built from the
     *  inspecting player's profile so the figure wears their skin. Never
     *  added to the world: the mod's blade layer reads only the rendered
     *  entity's main-hand state and follows game time by itself. */
    private static final String SLASHBLADE_COMBO_PLAYER_CACHE_KEY = "csgobox:slashblade_player";

    /** Framing of the combo preview. The figure is probed once in its rest
     *  pose (see {@link #slashBladeComboBounds}); that envelope only holds
     *  the carried blade, so the frame is widened by the first constant and
     *  its centre lifted by the second (blocks) — the swing arc reaches
     *  past the figure on every side and highest overhead — so the whole
     *  chain stays inside the preview square at the cost of a smaller
     *  figure. Framing values to be confirmed in game. */
    private static final float SLASHBLADE_COMBO_FRAME = 1.8F;
    private static final float SLASHBLADE_COMBO_CENTER_LIFT = 0.2F;

    /** Rest-pose bounds of the combo preview player (re-probed when the
     *  carrier is rebuilt, e.g. after a world change). */
    private static Entity slashBladeComboBoundsCarrier;
    private static ModelBounds slashBladeComboBoundsCache;

    /** SlashBlade blade 3D preview in the combo-motion form: the preview
     *  player wields a copy of the blade and the mod's own render layer
     *  swings it through the blade's attack chain
     *  ({@link SlashBladeComboInspect} performs the clicks). Falls back to
     *  the rack form when the combo motion is off or broke, and to the flat
     *  icon when the carrier is unavailable. */
    static void renderSlashBladeComboPreview(GuiGraphics gg, ItemStack item, LivingEntity player,
                                             int cx, int cy, Quat rotation, float scale) {
        LivingEntity carrier = slashBladeComboPlayer(player.level());
        if (carrier != null && SlashBladeComboInspect.drive(carrier, item)) {
            markSlashBladePreview(slashBladeVariantKey(item));
            ModelBounds bounds = slashBladeComboBounds(carrier);
            if (bounds != NO_BOUNDS && renderEntityPreview(gg, carrier, bounds, cx, cy, rotation, scale)) {
                return;
            }
        }
        renderSlashBladeRackPreview(gg, item, player, cx, cy, rotation, scale);
    }

    private static LivingEntity slashBladeComboPlayer(Level level) {
        Entity cached = ENTITY_PREVIEW_INSTANCES.get(SLASHBLADE_COMBO_PLAYER_CACHE_KEY);
        if (cached instanceof LivingEntity living && cached.level() == level) {
            return living;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !(level instanceof ClientLevel clientLevel)) {
            return null;
        }
        try {
            PreviewPlayer carrier = new PreviewPlayer(clientLevel, mc.player.getGameProfile());
            carrier.setPos(0.0D, 64.0D, 0.0D);
            carrier.setYRot(180.0F);
            carrier.setXRot(0.0F);
            ENTITY_PREVIEW_INSTANCES.put(SLASHBLADE_COMBO_PLAYER_CACHE_KEY, carrier);
            slashBladeComboBoundsCarrier = null;
            LOGGER.info("[csgobox] slashblade combo preview: player carrier ready");
            return carrier;
        } catch (Throwable t) {
            LOGGER.warn("SlashBlade combo preview player creation failed", t);
            return null;
        }
    }

    /** Frame of the combo preview, probed while the blade sits in its rest
     *  pose (the first frame after the install — the opening swing starts
     *  {@code IDLE_MS} later) and widened per the framing constants. */
    private static ModelBounds slashBladeComboBounds(Entity carrier) {
        if (slashBladeComboBoundsCarrier != carrier) {
            slashBladeComboBoundsCarrier = carrier;
            ModelBounds probed = probeEntityBounds(carrier);
            if (probed == NO_BOUNDS) {
                slashBladeComboBoundsCache = NO_BOUNDS;
                LOGGER.info("[csgobox] slashblade combo preview: rest-pose probe empty; keeping the raw preview scale");
            } else {
                Vector3f center = probed.center();
                slashBladeComboBoundsCache = new ModelBounds(
                        new Vector3f(center.x, center.y + SLASHBLADE_COMBO_CENTER_LIFT, center.z),
                        probed.hub(), probed.span() * SLASHBLADE_COMBO_FRAME);
                LOGGER.info("[csgobox] slashblade combo preview: rest-pose span={} frame span={}",
                        probed.span(), slashBladeComboBoundsCache.span());
            }
        }
        return slashBladeComboBoundsCache;
    }

    private static final class PreviewPlayer extends RemotePlayer {
        private PreviewPlayer(ClientLevel level, GameProfile profile) {
            super(level, profile);
        }

        @Override
        public boolean shouldShowName() {
            return false;
        }
    }

    // ===================== Touhou Little Maid soul slab preview =====================

    /** Touhou Little Maid (车万女仆) soul slab 3D preview. The slab's own look
     *  is a flat {@code item/generated} icon and the maid models only draw
     *  through TLM's {@code EntityMaidRenderer} (a vanilla {@code MobRenderer}
     *  subclass registered for the {@code touhou_little_maid:maid} entity), so
     *  the 3D pass parks a shared client-side maid entity and lets the mod's
     *  own renderer draw the model — zero TLM classes referenced; the item-id
     *  gate alone proves the mod is loaded. The INIT slab carries no model of
     *  its own: TLM rolls a random maid model at spawn time
     *  ({@code EntityMaid#finalizeSpawn}, skip-random over the installed model
     *  id set) and the preview mirrors that — one random id per session, re-
     *  rolled when a look screen opens (see {@link #clearMaidPreviewPick}).
     *  The stored-maid slab ({@code smart_slab_has_maid}) instead loads its
     *  {@code maid_info} save tag so the preview shows the exact stored maid.
     *  Bounds are cached per model id: maid model sizes vary wildly (chibi,
     *  yukkuri, humanoid), so the per-type entity bounds cache cannot be
     *  reused. Degrades to the flat icon whenever the entity, the model set,
     *  or the render itself is unavailable. */
    private static final String MAID_MOD_ID = "touhou_little_maid";
    private static final ResourceLocation MAID_TYPE_ID =
            ResourceLocation.fromNamespaceAndPath(MAID_MOD_ID, "maid");
    private static final String MAID_SLAB_INIT_PATH = "smart_slab_init";
    private static final String MAID_SLAB_HAS_MAID_PATH = "smart_slab_has_maid";
    /** The maid's model id save key: lowercase {@code model_id} on this 1.21.1
     *  line (EntityMaid.MODEL_ID_TAG_NAME); the 1.20.1 Forge line uses the
     *  camel-case {@code ModelId}. */
    private static final String MAID_NBT_MODEL_KEY = "model_id";
    /** YSM-stored maids need the YSM renderer; without the YSM mod installed
     *  the shared renderer would fall back to whatever model it drew last, so
     *  such stacks degrade to the flat icon. */
    private static final String MAID_NBT_YSM_KEY = "YsmModelId";
    /** TLM's client model registry: the {@code MAID_MODELS} static on
     *  {@code client.resource.CustomPackLoader} (a {@code MaidModels}
     *  singleton). Resolved reflectively once, cached; unreachable registry
     *  means no random pick and the flat icon degrade. */
    private static final String MAID_PACK_LOADER_CLASS =
            "com.github.tartaricacid.touhoulittlemaid.client.resource.CustomPackLoader";
    private static Object MAID_MODELS_INSTANCE;
    private static Method MAID_MODEL_SET_METHOD;
    private static Method MAID_CONTAINS_INFO_METHOD;
    private static boolean MAID_MODEL_METHODS_RESOLVED;
    /** The random maid model of the current preview session (null = roll on
     *  next use). Cleared by the look screen's init hook. */
    private static String maidSessionPick;
    private static String maidAppliedPick;
    private static ItemStack maidLoadedStack;
    private static final Map<String, Object> MAID_PREVIEW_BOUNDS = new HashMap<>();
    private static final Set<String> MAID_PREVIEW_LOG = new HashSet<>();
    /** One-shot guards for the flat-icon degrade reasons (a degrade would
     *  otherwise be completely silent — the 2026-11-17 L4 gap). */
    private static final Set<String> MAID_DEGRADE_LOG = new HashSet<>();

    /** Namespace + path gate for the two soul slabs. Item presence alone
     *  proves the mod is loaded — no isModLoaded call. Package-private: the
     *  maid preview strategy's matcher. */
    static boolean isMaidSlabItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return MAID_MOD_ID.equals(id.getNamespace())
                && (MAID_SLAB_INIT_PATH.equals(id.getPath()) || MAID_SLAB_HAS_MAID_PATH.equals(id.getPath()));
    }

    /** 3D body of the maid slab preview strategy (see the block comment at
     *  {@link #MAID_MOD_ID} for the shape). Package-private: the strategy
     *  chain's 3D delegate. */
    static void renderMaidSlabPreview(GuiGraphics gg, ItemStack item, LivingEntity player,
                                      int cx, int cy, Quat rotation, float scale) {
        boolean storedMaid = BuiltInRegistries.ITEM.getKey(item.getItem()).getPath().equals(MAID_SLAB_HAS_MAID_PATH);
        String degrade = null;
        Entity maid = null;
        // DefaultedRegistry.get falls back to the pig for unknown ids, so
        // existence must go through containsKey.
        if (BuiltInRegistries.ENTITY_TYPE.containsKey(MAID_TYPE_ID)) {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(MAID_TYPE_ID);
            maid = type == null ? null : entityPreviewInstance(type, player.level());
        }
        if (maid == null) {
            degrade = "maid entity type unavailable";
        } else {
            CompoundTag maidTag = null;
            String pick = null;
            if (storedMaid) {
                maidTag = storedMaidTag(item);
                if (maidTag == null) {
                    degrade = "stored slab carries no maid_info component (no maid captured into this stack)";
                } else if (!maidTag.getString(MAID_NBT_YSM_KEY).isEmpty()) {
                    degrade = "YSM-stored maid but the YSM mod is not installed";
                } else {
                    pick = maidTag.getString(MAID_NBT_MODEL_KEY);
                    if (pick.isEmpty()) {
                        degrade = "stored maid_info has no model id";
                    }
                }
            } else {
                pick = maidSessionPick();
                if (pick == null) {
                    degrade = "random maid pick unavailable (TLM client model registry unreachable or empty)";
                } else {
                    maidTag = new CompoundTag();
                    maidTag.putString(MAID_NBT_MODEL_KEY, pick);
                }
            }
            if (degrade == null) {
                // Arm the inspect button only once a real maid look is on
                // show; a flat-degraded slab has no mob to inspect.
                markPreviewMob(maid);
                applyMaidSaveTag(maid, item, maidTag, pick);
                ModelBounds bounds = maidPreviewBounds(pick, maid);
                if (bounds != NO_BOUNDS && renderEntityPreview(gg, maid, bounds, cx, cy, rotation, scale)) {
                    return;
                }
                degrade = "model probe or render unavailable";
            }
        }
        if (MAID_DEGRADE_LOG.add(degrade)) {
            LOGGER.info("[csgobox] maid slab preview: flat degrade - {}", degrade);
        }
        renderItem2DFlat(player, gg, item, cx, cy, scale);
    }

    /** Drops the session's random maid pick so the next INIT-slab preview
     *  rolls a fresh random maid (mirrors TLM's own random spawn semantics).
     *  Package-private: relayed through the {@code MaidSlabPreview} facade. */
    static void clearMaidPreviewPick() {
        maidSessionPick = null;
    }

    /** The stored-maid save tag of a {@code smart_slab_has_maid} stack, read
     *  through the mod's {@code touhou_little_maid:maid_info} data component
     *  (a vanilla {@code CustomData}) without touching TLM classes. Null when
     *  the component is absent or empty. */
    private static CompoundTag storedMaidTag(ItemStack stack) {
        CustomData data = maidInfoData(stack);
        return data == null ? null : data.copyTag();
    }

    /** The raw {@code touhou_little_maid:maid_info} component value. Shared
     *  by the stored-maid slab and the garage kit (1.21 keeps both the captured
     *  maid and the kit's entity save in this one component, per TLM's
     *  {@code ItemGarageKit.getMaidData}). Null when absent or empty. */
    private static CustomData maidInfoData(ItemStack stack) {
        ResourceLocation infoId = ResourceLocation.fromNamespaceAndPath(MAID_MOD_ID, "maid_info");
        if (!BuiltInRegistries.DATA_COMPONENT_TYPE.containsKey(infoId)) {
            return null;
        }
        DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.get(infoId);
        Object value = type == null ? null : stack.get(type);
        if (value instanceof CustomData data && !data.isEmpty()) {
            return data;
        }
        return null;
    }

    /** The session's random maid model id, rolled on first use from TLM's
     *  client model id set — the same skip-random idiom its own spawns use.
     *  Null (and retried next frame) when the registry is unreachable or no
     *  valid model is installed. */
    private static String maidSessionPick() {
        if (maidSessionPick != null) {
            return maidSessionPick;
        }
        Set<String> ids = maidModelIdSet();
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        Optional<String> rolled = ids.stream()
                .filter(AnimRenderOps::maidContainsInfo)
                .skip(ThreadLocalRandom.current().nextInt(ids.size()))
                .findFirst();
        if (rolled.isEmpty()) {
            // skip landed past the filtered tail — fall back to any valid id
            rolled = ids.stream().filter(AnimRenderOps::maidContainsInfo).findFirst();
        }
        maidSessionPick = rolled.orElse(null);
        return maidSessionPick;
    }

    /** The client model registry reflectively: {@code CustomPackLoader.
     *  MAID_MODELS.getModelIdSet()} + {@code containsInfo(String)} for pick
     *  validation. One resolve; a failure is remembered and the preview
     *  degrades to the flat icon. */
    private static Set<String> maidModelIdSet() {
        if (!MAID_MODEL_METHODS_RESOLVED) {
            MAID_MODEL_METHODS_RESOLVED = true;
            try {
                Class<?> loader = Class.forName(MAID_PACK_LOADER_CLASS);
                MAID_MODELS_INSTANCE = loader.getField("MAID_MODELS").get(null);
                MAID_MODEL_SET_METHOD = MAID_MODELS_INSTANCE.getClass().getMethod("getModelIdSet");
                MAID_CONTAINS_INFO_METHOD = MAID_MODELS_INSTANCE.getClass().getMethod("containsInfo", String.class);
            } catch (Throwable t) {
                LOGGER.warn("Maid slab preview: TLM client model registry unreachable; degrading to the flat icon", t);
            }
        }
        if (MAID_MODELS_INSTANCE == null || MAID_MODEL_SET_METHOD == null) {
            return null;
        }
        try {
            Object result = MAID_MODEL_SET_METHOD.invoke(MAID_MODELS_INSTANCE);
            return result instanceof Set<?> set ? (Set<String>) set : null;
        } catch (Throwable t) {
            LOGGER.warn("Maid slab preview: reading the model id set failed", t);
            return null;
        }
    }

    /** True when the pick has a registered client model info; on a reflection
     *  failure the id is accepted (the renderer resolves or keeps its own
     *  fallback) so an unreachable validator cannot starve the pick. */
    private static boolean maidContainsInfo(String modelId) {
        if (MAID_CONTAINS_INFO_METHOD == null || MAID_MODELS_INSTANCE == null) {
            return true;
        }
        try {
            return (Boolean) MAID_CONTAINS_INFO_METHOD.invoke(MAID_MODELS_INSTANCE, modelId);
        } catch (Throwable t) {
            return true;
        }
    }

    /** Loads the maid save tag onto the shared instance once per (pick,
     *  stack) pair — identical to the vanilla spawn-data discipline: reloads
     *  on a re-roll or a different stored-maid stack, skips per-frame repeats
     *  so the inspect animation state survives. The parked pose is restored
     *  because {@code Entity.load} may carry Pos/Rotation from a save tag. */
    private static void applyMaidSaveTag(Entity maid, ItemStack item, CompoundTag tag, String pick) {
        boolean stackChanged = maidLoadedStack == null || !ItemStack.matches(item, maidLoadedStack);
        boolean pickChanged = !pick.equals(maidAppliedPick);
        if (!stackChanged && !pickChanged) {
            return;
        }
        maidLoadedStack = item.copy();
        maidAppliedPick = pick;
        try {
            maid.load(tag);
            maid.setPos(0.0D, 64.0D, 0.0D);
            maid.setYRot(180.0F);
            maid.setXRot(0.0F);
        } catch (Throwable t) {
            LOGGER.warn("Maid slab preview: applying the maid save tag failed; keeping the default look", t);
        }
    }

    /** Bounds of a maid model, cached per model id (sizes vary wildly between
     *  model packs). The maid must already carry the model id when probing.
     *  The drag hub is pinned to the maid's collision-box cube centre (see
     *  {@link #entityHitboxHub}) — posé doll geometry pulls the vertex-derived
     *  hub off the body; the framing centre stays the envelope centre. */
    private static ModelBounds maidPreviewBounds(String pick, Entity maid) {
        Object cached = MAID_PREVIEW_BOUNDS.get(pick);
        if (cached instanceof ModelBounds bounds) {
            return bounds;
        }
        if (cached == NO_BOUNDS) {
            return NO_BOUNDS;
        }
        ModelBounds bounds = probeEntityBounds(maid, entityHitboxHub(maid));
        MAID_PREVIEW_BOUNDS.put(pick, bounds == NO_BOUNDS ? NO_BOUNDS : bounds);
        if (MAID_PREVIEW_LOG.add(pick)) {
            LOGGER.info("[csgobox] maid preview: model={} span={}",
                    pick, bounds == NO_BOUNDS ? "none" : bounds.span());
        }
        return bounds == NO_BOUNDS ? NO_BOUNDS : bounds;
    }

    // ===================== Touhou Little Maid garage kit / chair preview =====================

    /** Touhou Little Maid (车万女仆) garage kits (手办) and chairs (坐垫). Both
     *  items are {@code builtin/entity} BEWLR items whose generic 2D/3D reroute
     *  draws the mod's own fixed-context icon — small, framed like a vanilla
     *  slot icon, and sized off one probe shared by every maid/cushion variant.
     *  The dedicated previews park the mod's own entity (a maid for garage
     *  kits, the chair entity for cushions) and render it through the shared
     *  entity preview path, so the figure fills the preview square like the
     *  soul slabs do, with bounds cached per model id (cushion/maid packs vary
     *  wildly in size). Zero TLM classes referenced — the item-id gate proves
     *  the mod is loaded, the entity save tags drive the content, and the
     *  renderers are resolved by the vanilla dispatcher.
     *
     *  <p>Storage on this 1.21.1 line (TLM's data components): the garage kit
     *  keeps its entity save in the same {@code touhou_little_maid:maid_info}
     *  component the stored-maid slab uses ({@code ItemGarageKit.getMaidData},
     *  inner keys {@code id} / {@code model_id}), with a bare stack falling
     *  back to the default maid (hakurei reimu); the chair's model id lives in
     *  the top-level {@code touhou_little_maid:model_id} string component,
     *  defaulting to {@code cushion}. The chair entity's save key is the same
     *  lowercase {@code model_id} ({@code EntityChair.readAdditionalSaveData}).
     *  See {@link #renderTlmGarageKitPreview} / {@link #renderTlmChairPreview}.</p> */
    private static final String MAID_GARAGE_KIT_PATH = "garage_kit";
    private static final String MAID_CHAIR_PATH = "chair";
    private static final String MAID_ENTITY_ID_KEY = "id";
    private static final String MAID_DEFAULT_TYPE_ID = MAID_MOD_ID + ":maid";
    private static final String MAID_DEFAULT_MODEL_ID = "touhou_little_maid:hakurei_reimu";
    /** The chair item's model-id component ({@code InitDataComponent.
     *  MODEL_ID_TAG}) — a top-level string, separate from the maid_info
     *  component. */
    private static final String MAID_CHAIR_MODEL_COMPONENT = "model_id";
    private static final ResourceLocation MAID_CHAIR_TYPE_ID =
            ResourceLocation.fromNamespaceAndPath(MAID_MOD_ID, "chair");
    private static final String MAID_CHAIR_DEFAULT_MODEL_ID = "touhou_little_maid:cushion";
    private static final Map<String, ModelBounds> CHAIR_PREVIEW_BOUNDS = new HashMap<>();
    private static final Set<String> CHAIR_PREVIEW_LOG = new HashSet<>();
    /** One-shot guards for the flat-icon degrade reasons (both previews would
     *  otherwise degrade completely silently). */
    private static final Set<String> MAID_DECOR_DEGRADE_LOG = new HashSet<>();
    /** Chair load guard: reloads on a different stack or model id, skips
     *  per-frame repeats so the parked pose survives (same discipline as
     *  {@link #applyMaidSaveTag}). */
    private static ItemStack chairLoadedStack;
    private static String chairAppliedModel;

    /** Namespace + path gate for the garage kit item. Package-private: the
     *  strategy chain's garage kit match. */
    static boolean isTlmGarageKitItem(ItemStack stack) {
        return maidItemWithPath(stack, MAID_GARAGE_KIT_PATH);
    }

    /** Namespace + path gate for the chair item. Package-private: the
     *  strategy chain's chair match. */
    static boolean isTlmChairItem(ItemStack stack) {
        return maidItemWithPath(stack, MAID_CHAIR_PATH);
    }

    private static boolean maidItemWithPath(ItemStack stack, String path) {
        if (stack.isEmpty()) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return MAID_MOD_ID.equals(id.getNamespace()) && path.equals(id.getPath());
    }

    /** 3D body of the garage kit preview strategy (see the block comment at
     *  {@link #MAID_GARAGE_KIT_PATH} for the shape). Maid content renders
     *  through the slab pipeline (per-model-id bounds, inspect button armed);
     *  any other stored entity goes through the generic entity preview with
     *  the maid_info payload as its spawn data. */
    static void renderTlmGarageKitPreview(GuiGraphics gg, ItemStack item, LivingEntity player,
                                          int cx, int cy, Quat rotation, float scale) {
        CustomData stored = maidInfoData(item);
        CompoundTag entityInfo = stored != null ? stored.copyTag() : garageKitDefaults();
        String degrade = null;
        String typeId = entityInfo.getString(MAID_ENTITY_ID_KEY);
        ResourceLocation typeRl = typeId.isEmpty() ? null : ResourceLocation.tryParse(typeId);
        EntityType<?> type = typeRl != null && BuiltInRegistries.ENTITY_TYPE.containsKey(typeRl)
                ? BuiltInRegistries.ENTITY_TYPE.get(typeRl) : null;
        Entity entity = type == null ? null : entityPreviewInstance(type, player.level());
        if (type == null) {
            degrade = "entity type not registered: " + (typeId.isEmpty() ? "<none>" : typeId);
        } else if (entity == null) {
            degrade = "preview entity instance unavailable";
        } else if (MAID_TYPE_ID.equals(typeRl)) {
            degrade = renderGarageKitMaid(gg, item, player, cx, cy, rotation, scale, entity, entityInfo);
            if (degrade == null) {
                return;
            }
        } else {
            // Non-maid figurine (any entity a kit was crafted from): the
            // generic path applies the maid_info payload and renders it.
            renderEntityPreviewForType(gg, item, player, cx, cy, rotation, scale, type, stored);
            return;
        }
        if (MAID_DECOR_DEGRADE_LOG.add(degrade)) {
            LOGGER.info("[csgobox] garage kit preview: flat degrade - {}", degrade);
        }
        renderItem2DFlat(player, gg, item, cx, cy, scale);
    }

    /** Maid-content body of the garage kit preview. Returns null on success,
     *  else the degrade reason (flat icon follows). */
    private static String renderGarageKitMaid(GuiGraphics gg, ItemStack item, LivingEntity player,
                                              int cx, int cy, Quat rotation, float scale,
                                              Entity maid, CompoundTag entityInfo) {
        String pick = entityInfo.getString(MAID_NBT_MODEL_KEY);
        if (pick.isEmpty()) {
            return "maid entity info has no model id";
        }
        if (!entityInfo.getString(MAID_NBT_YSM_KEY).isEmpty()) {
            return "YSM-stored maid but the YSM mod is not installed";
        }
        // Arm the inspect button once a real maid look is on show.
        markPreviewMob(maid);
        applyMaidSaveTag(maid, item, entityInfo, pick);
        ModelBounds bounds = maidPreviewBounds(pick, maid);
        if (bounds == NO_BOUNDS || !renderEntityPreview(gg, maid, bounds, cx, cy, rotation, scale)) {
            return "model probe or render unavailable";
        }
        return null;
    }

    /** TLM's own default garage kit payload ({@code ItemGarageKit.
     *  getDefaultData} — a bare stack renders the default maid). */
    private static CompoundTag garageKitDefaults() {
        CompoundTag defaults = new CompoundTag();
        defaults.putString(MAID_ENTITY_ID_KEY, MAID_DEFAULT_TYPE_ID);
        defaults.putString(MAID_NBT_MODEL_KEY, MAID_DEFAULT_MODEL_ID);
        return defaults;
    }

    /** 3D body of the chair preview strategy (see the block comment at
     *  {@link #MAID_CHAIR_PATH} for the shape). */
    static void renderTlmChairPreview(GuiGraphics gg, ItemStack item, LivingEntity player,
                                      int cx, int cy, Quat rotation, float scale) {
        String modelId = chairModelId(item);
        String degrade = null;
        Entity chair = null;
        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(MAID_CHAIR_TYPE_ID)) {
            degrade = "chair entity type unavailable";
        } else {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(MAID_CHAIR_TYPE_ID);
            chair = type == null ? null : entityPreviewInstance(type, player.level());
            if (chair == null) {
                degrade = "chair entity instance unavailable";
            }
        }
        if (degrade == null) {
            applyChairModelTag(chair, item, modelId);
            ModelBounds bounds = chairPreviewBounds(modelId, chair);
            if (bounds == NO_BOUNDS || !renderEntityPreview(gg, chair, bounds, cx, cy, rotation, scale)) {
                degrade = "model probe or render unavailable";
            }
        }
        if (degrade != null) {
            if (MAID_DECOR_DEGRADE_LOG.add(degrade)) {
                LOGGER.info("[csgobox] chair preview: flat degrade - {}", degrade);
            }
            renderItem2DFlat(player, gg, item, cx, cy, scale);
        }
    }

    /** The chair item's model id: the top-level {@code model_id} string
     *  component, falling back to TLM's default cushion when absent. */
    private static String chairModelId(ItemStack stack) {
        ResourceLocation idRl = ResourceLocation.fromNamespaceAndPath(MAID_MOD_ID, MAID_CHAIR_MODEL_COMPONENT);
        if (BuiltInRegistries.DATA_COMPONENT_TYPE.containsKey(idRl)) {
            DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.get(idRl);
            Object value = type == null ? null : stack.get(type);
            if (value instanceof String modelId && !modelId.isEmpty()) {
                return modelId;
            }
        }
        return MAID_CHAIR_DEFAULT_MODEL_ID;
    }

    /** Loads the chair's model id onto the shared instance once per (stack,
     *  model id) pair — {@code Entity.load} with the entity's own save key
     *  (lowercase {@code model_id} on this line). The parked pose is restored
     *  after the load for the same reason as the maid path. */
    private static void applyChairModelTag(Entity chair, ItemStack item, String modelId) {
        boolean stackChanged = chairLoadedStack == null || !ItemStack.matches(item, chairLoadedStack);
        boolean modelChanged = !modelId.equals(chairAppliedModel);
        if (!stackChanged && !modelChanged) {
            return;
        }
        chairLoadedStack = item.copy();
        chairAppliedModel = modelId;
        try {
            CompoundTag tag = new CompoundTag();
            tag.putString(MAID_NBT_MODEL_KEY, modelId);
            chair.load(tag);
            chair.setPos(0.0D, 64.0D, 0.0D);
            chair.setYRot(180.0F);
            chair.setXRot(0.0F);
        } catch (Throwable t) {
            LOGGER.warn("Chair preview: applying the model id failed; keeping the default look", t);
        }
    }

    /** Bounds of a chair model, cached per model id (cushion packs vary in
     *  size). The chair must already carry the model id when probing. */
    private static ModelBounds chairPreviewBounds(String modelId, Entity chair) {
        Object cached = CHAIR_PREVIEW_BOUNDS.get(modelId);
        if (cached instanceof ModelBounds bounds) {
            return bounds;
        }
        if (cached == NO_BOUNDS) {
            return NO_BOUNDS;
        }
        ModelBounds bounds = probeEntityBounds(chair);
        CHAIR_PREVIEW_BOUNDS.put(modelId, bounds == NO_BOUNDS ? NO_BOUNDS : bounds);
        if (CHAIR_PREVIEW_LOG.add(modelId)) {
            LOGGER.info("[csgobox] chair preview: model={} span={}",
                    modelId, bounds == NO_BOUNDS ? "none" : bounds.span());
        }
        return bounds == NO_BOUNDS ? NO_BOUNDS : bounds;
    }
}
