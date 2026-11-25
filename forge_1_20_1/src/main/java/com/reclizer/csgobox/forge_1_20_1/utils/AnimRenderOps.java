package com.reclizer.csgobox.forge_1_20_1.utils;

import com.mojang.blaze3d.platform.GlConst;
import com.mojang.math.Axis;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.reclizer.csgobox.api.preview.PreviewSource;
import com.reclizer.csgobox.api.preview.RestPose;
import com.reclizer.csgobox.utils.Quat;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.bedrock.BedrockCube;
import com.tacz.guns.client.model.bedrock.BedrockCubeBox;
import com.tacz.guns.client.model.bedrock.BedrockCubePerFace;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.resource.GunDisplayInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.InteractionHand;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import com.mojang.authlib.GameProfile;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;
import top.theillusivec4.curios.api.type.ISlotType;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.SpellcasterIllager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.ticks.BlackholeTickAccess;
import net.minecraft.world.ticks.LevelTickAccess;
// 检视页方块特效（2026-11-20）：BE 动画 + 粒子
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.Tesselator;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Scoreboard;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.UUID;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.fml.ModList;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.slf4j.Logger;

import it.unimi.dsi.fastutil.floats.FloatArrayList;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Single per-platform adaptation point for animation rendering primitives.
 * Screens and logic helpers (IconListTools / GuiItemMove) must call ONLY
 * through this class; version-varying render API lives here and nowhere else.
 * era: legacy (1.20.1)
 */
public final class AnimRenderOps {

    /** TACZ is a compileOnly optional dependency: every TACZ class touch in
     *  this file must sit behind an {@code isLoaded} gate first, so the JVM
     *  never resolves IGun/TimelessAPI when the mod is absent at runtime
     *  (same discipline as BoxItemCodec / TaczInspectViewportImpl). */
    /** Package-private so the TACZ preview strategy gates on the same id. */
    static final String TACZ_MOD_ID = "tacz";
    /** Superb Warfare (卓越前线) item namespace. Unlike TACZ no ModList gate
     *  is needed: the 3D branch only matches a registered item's namespace,
     *  and such an item cannot exist unless the mod is loaded. No Superb
     *  Warfare class is ever referenced. */
    private static final String SUPERB_WARFARE_MOD_ID = "superbwarfare";

    private static final Logger LOGGER = LogUtils.getLogger();
    /** Set once the first supports3D() call has logged the render environment. */
    private static boolean logged3DEnvironment;
    /** Identity render stack for model rendering: bone transforms accumulate
     *  here while the outer placement lives in RenderSystem's model-view
     *  matrix. Safe to reuse on the render thread. */
    private static final PoseStack REUSABLE_POSE_STACK = new PoseStack();
    /** Scratch quaternion for the drag rotation: {@code mulPose} copies the
     *  value immediately, so a single static instance is safe on the render
     *  thread and avoids one allocation per 3D item per frame. */
    private static final Quaternionf SCRATCH_QUAT = new Quaternionf();
    private static final RandomSource SCRATCH_RANDOM = RandomSource.create();
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
    // ===================== Create schematic (Create 蓝图) structure preview =====================

    /** Create SchematicItem.loadSchematic(Level, ItemStack), resolved by
     *  reflection on first use (never at class-init, and only requested after
     *  the item-id gate proved the mod is loaded — zero Create/catnip
     *  compile-time references, same discipline as the SBW preview). On this
     *  era the stack carries the schematic as top-level {@code Owner}/{@code
     *  File} tags (no data components), and the file must end in {@code
     *  .nbt} or loadSchematic returns an empty template (Create 6.0.8
     *  bytecode, 2026-11-20). Null after a failed resolution with the flag
     *  suppressing retries. */
    private static Method CREATE_LOAD_SCHEMATIC;
    private static boolean CREATE_LOAD_SCHEMATIC_RESOLVED;

    /** Create's own schematic preview plumbing, resolved by reflection as a
     *  group on first use after the item-id gate proved the mod is loaded:
     *  the fake schematic level ({@code catnip.levelWrappers.SchematicLevel}
     *  — a mutable Level the vanilla {@code StructureTemplate.placeInWorld}
     *  fills), Create's own {@code SchematicRenderer} (per-RenderType buffer
     *  cache tessellated with neighbour-face culling, plus live block-entity
     *  rendering with an errored-BE mask) and its shared output buffer
     *  ({@code DefaultSuperRenderTypeBuffer.getInstance()} whose
     *  {@code draw()} flushes the recorded geometry to screen under the
     *  caller's model-view transform). The catnip classes ride inside the
     *  jar-jar'd Ponder bundle on this era too (2026-11-20 archaeology), so
     *  every name resolves whenever Create loads. Null members after a
     *  failed resolution, with the flag suppressing retries. */
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
    /** Cached centre + largest-axis span (block units, in the drag-rotation
     *  space) of each baked item model's geometry. Models are stable
     *  instances owned by the model manager and are replaced on resource
     *  reload, so a WeakHashMap lets old models go. */
    /** Framing centre + drag hub + largest-axis span. Entity previews keep
     *  the two pivots separate (2026-11-21, Alex's Mobs): the framing centre
     *  is the envelope-box centre so the model sits symmetric in the preview
     *  square, while the drag hub is the reconstructed cube mass centre — a
     *  single point cannot serve both (the median hub sat up to ~22% of the
     *  span off the envelope centre and clipped the far side; the envelope
     *  hub rotated around thin appendages instead of the body). Paths that
     *  never pivot separately pass the centre as the hub via {@link #of}. */
    private record ModelBounds(Vector3f center, Vector3f hub, float span) {
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

    /** Shared client-side vehicle entities backing the Superb Warfare summon
     *  crate 3D preview, keyed by entity type id string. The renderer binds
     *  animation state to the entity instance, so the same instance must be
     *  reused across frames; entity types are registry constants, and a
     *  resource reload swaps renderer state harmlessly (the entity is not in
     *  the world). */
    private static final Map<String, Entity> VEHICLE_PREVIEW_INSTANCES = new WeakHashMap<>();

    /** Probe-measured centre + span of each Superb Warfare vehicle entity
     *  type's rendered geometry (block units), keyed by EntityType — same
     *  lifecycle as {@link #FIXED_CONTEXT_BOUNDS}: registry constants live
     *  for the whole session, and a NO_CENTER sentinel suppresses re-probing
     *  renderers that emit nothing usable. */
    private static final Map<EntityType<?>, Object> VEHICLE_ENTITY_BOUNDS = new WeakHashMap<>();

    private AnimRenderOps() {
    }

    /** Immediate-mode blit. Forces SRC_ALPHA: the convenience blit inherits
     *  whatever blend func is current, so translucent textures (spot glow,
     *  lens vignette) would otherwise render as hard opaque discs. */
    public static void blitTextured(GuiGraphics gg, ResourceLocation tex, int x, int y, int w, int h) {
        blitTextured(gg, tex, x, y, w, h, w, h);
    }

    /** Variant carrying the texture's real pixel size: the convenience blit
     *  treats the source UV window as w×h, so an enlarged draw (gold_item.png
     *  32x24 at ~169x127) would push UV past 1.0. The whole texture maps
     *  onto the free-form w×h target rect with UV kept in [0,1]. */
    public static void blitTextured(GuiGraphics gg, ResourceLocation tex, int x, int y, int w, int h, int texW, int texH) {
        gg.flush();
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(770, 771, 1, 771);
        gg.blit(tex, x, y, w, h, 0F, 0F, texW, texH, texW, texH);
    }

    /** Sprite-sheet variant: draws a w×h rect with the UV window (u,v,uw,vh)
     *  of a texW x texH texture, ARGB tint applied via shader color. The
     *  shader color is NOT auto-reset by the GUI pipeline, so this facade
     *  restores (1,1,1,1) itself before returning — callers never leak tint. */
    public static void blitTextured(GuiGraphics gg, ResourceLocation tex, int x, int y, int w, int h,
                                    int u, int v, int uw, int vh, int texW, int texH, int tint) {
        gg.flush();
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(770, 771, 1, 771);
        RenderSystem.setShaderColor(((tint >> 16) & 0xFF) / 255F,
                ((tint >> 8) & 0xFF) / 255F, (tint & 0xFF) / 255F, ((tint >> 24) & 0xFF) / 255F);
        gg.blit(tex, x, y, w, h, (float) u, (float) v, uw, vh, texW, texH);
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

    public static void renderBlurredBackground(GuiGraphics gg) {
        // 1.20.1 has no menu backdrop blur API (added in 1.20.2) — no-op.
    }

    /** 2D item icon with its top-left corner at (x, y), scaled (16px per
     *  block unit). Drawn through the shared buffer source and submitted
     *  immediately (endBatch) so an active scissor rect clips the icon. */
    /** 2D item icon centred at (x, y), scaled (16px per block unit).
     *  Custom-renderer models ({@code builtin/entity} — chest-like items
     *  drawn by a BEWLR) route to the FIXED-context 3D render instead of
     *  the flat path below: the item-frame perspective is the established
     *  convention for them here. Skipped under shader mods (Iris/Oculus)
     *  — their GUI interception breaks custom 3D paths, and the flat path
     *  cannot draw them either. */
    public static void renderItem2D(LivingEntity entity, GuiGraphics gg, ItemStack stack, float x, float y, float scale) {
        // Mod-specific overrides first: the first matching strategy owns the
        // cell (TACZ suppress while a display is loading, SBW vehicle icon).
        // Unmatched items fall through to the generic custom-renderer / flat
        // flow below; see ItemPreviewStrategies for the chain.
        for (ItemPreviewStrategies.ItemPreviewStrategy strategy : ItemPreviewStrategies.ALL) {
            if (strategy.matches(stack) && strategy.render2D(entity, gg, stack, x, y, scale)) {
                return;
            }
        }
        BakedModel model = Minecraft.getInstance().getItemRenderer().getModel(stack, entity.level(), entity, 0);
        if (model.isCustomRenderer() && !isShaderModActive()) {
            renderFixedContextModel3D(gg, stack, entity, x, y, Quat.IDENTITY, scale, null);
            return;
        }
        renderItem2DFlat(entity, gg, stack, x, y, scale);
    }

    /** The flat icon body of {@link #renderItem2D} (never re-delegates to a
     *  3D path, so the FIXED-context failure fallback cannot loop). */
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
        PoseStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushPose();
        modelViewStack.mulPoseMatrix(pose.last().pose());
        RenderSystem.applyModelViewMatrix();
        PoseStack renderStack = REUSABLE_POSE_STACK;
        renderStack.setIdentity();
        Minecraft.getInstance().getItemRenderer().render(stack, ItemDisplayContext.GUI, false,
                renderStack, bufferSource, 15728880, OverlayTexture.NO_OVERLAY, model);
        bufferSource.endBatch();
        RenderSystem.enableDepthTest();
        if (flat) Lighting.setupFor3DItems();
        pose.popPose();
        modelViewStack.popPose();
        RenderSystem.applyModelViewMatrix();
    }

    /** 3D rotating item preview (drag-to-rotate). The raw quaternion from
     *  {@code ItemDrag3D} passes through unchanged — the drag scheme works in
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
        // Third-party code SPI (CsboxPreviewAPI): a registered provider
        // deliberately takes over its own items before anything built-in
        // runs (docs/PREVIEW-API.md).
        PreviewSource provided = CsboxPreviewManager.resolveProvider(item);
        if (provided != null) {
            renderPreviewSource(gg, item, player, cx, cy, rotation, scale, provided);
            return;
        }
        // Mod-specific previews: the first matching strategy owns the item
        // (TACZ gun model, SBW vehicle/gun, SBP shell rest pose, spawn eggs,
        // armor stands, summon containers, Create/Prefab blueprints,
        // SlashBlade racks, TLM soul slabs/garage kits/chairs). Unmatched
        // items fall through to the vanilla baked-model path below; see
        // ItemPreviewStrategies for the chain and the TACZ class-loading
        // gate discipline.
        for (ItemPreviewStrategies.ItemPreviewStrategy strategy : ItemPreviewStrategies.ALL) {
            if (strategy.matches(item)) {
                strategy.render3D(gg, item, player, cx, cy, rotation, scale);
                return;
            }
        }
        // Zero-code JSON declarations (assets/<mod>/csgobox_preview/) come
        // after the built-ins: the shipped integrations know their mods
        // better than a generic declaration would.
        PreviewSource declared = CsboxPreviewManager.resolveDeclaration(item);
        if (declared != null) {
            renderPreviewSource(gg, item, player, cx, cy, rotation, scale, declared);
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        BakedModel model = Minecraft.getInstance().getItemRenderer().getModel(item, player.level(), player, 0);
        // Custom renderers (builtin/entity BEWLR items — vanilla shield,
        // MG golem holders drawing their stored golem, ...) expose no quads
        // to measure and have no fixed baked geometry: render under the
        // FIXED context exactly like the generic 2D branch does, so the
        // probe-based auto-fit and centring apply to whatever the BEWLR
        // draws.
        if (model.isCustomRenderer()) {
            renderFixedContextModel3D(gg, item, player, cx, cy, rotation, scale, null);
            return;
        }
        // Block-backed flat sprites (mod food plates and the like): the item
        // model is a generated plane while the real 3D form only exists on
        // the placed block, so the preview renders the block's model instead.
        // Vanilla block items parent their block model and never enter this
        // fallback.
        BakedModel renderModel = blockBackedModel(item, model);
        // Same-id entity convention (generic, no per-mod code): a flat-sprite
        // item whose registry id is also an entity type draws the entity —
        // vanilla kin (minecarts, armor stand, end crystal, item frames,
        // painting, trident) and modded summonables. Food items never match:
        // raw food ids collide with their animal entities (raw
        // chicken/chicken) and the item is the truth there.
        if (renderModel == model && isFlatSpriteModel(model)) {
            EntityType<?> convention = conventionEntityType(item);
            if (convention != null) {
                renderEntityPreviewForType(gg, item, player, cx, cy, rotation, scale, convention, null);
                return;
            }
        }
        // Auto-fit: a model that projects beyond the preview square (oversized
        // datapack models) is shrunk to stay inside it; normal items (span <=
        // 1 block) keep the caller's scale unchanged. On the inspect page the
        // block's own BlockEntity renderer (enchanting book, spawner cage, ...)
        // is probed offline and unioned into the envelope, so the drawn extent
        // — model + BE visuals — drives both the fit and the pivot.
        ModelBounds effectBounds = inspectEffectBounds(mc, item, renderModel);
        if (renderModel == model) {
            BakedModel emptyBlockModel = blockBackedModel(item, model, true);
            if (emptyBlockModel != renderModel) {
                ModelBounds berBounds = inspectEffectBounds(mc, item, emptyBlockModel);
                if (berBounds != NO_BOUNDS) {
                    // Empty block model + drawing BER (tavern chalkboard: the
                    // block model has zero quads, every visual lives in the
                    // BlockEntityRenderer). The flat icon kept by the G0 guard
                    // would double the render under the union pivot (icon
                    // drifting off-centre next to the board) — the BER IS the
                    // block's visual, so render it alone and fit off the BER
                    // envelope.
                    renderModel = emptyBlockModel;
                    effectBounds = berBounds;
                }
            }
        }
        float fitScale = fitScaleFor(
                effectBounds != NO_BOUNDS ? effectBounds.span() : itemModelSpan(renderModel), scale);
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
        // Same convention as the TACZ gun branch: translate(-centre) in block
        // units AFTER the 16px scale.
        Vector3f center = effectBounds != NO_BOUNDS
                ? effectBounds.center()
                : previewPivot(renderModel, renderModel != model);
        if (center != null) {
            pose.translate(-center.x, -center.y, -center.z);
        }
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        boolean flat = !renderModel.usesBlockLight();
        if (flat) Lighting.setupForFlatItems();
        PoseStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushPose();
        modelViewStack.mulPoseMatrix(pose.last().pose());
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
        mc.getItemRenderer().render(item, ItemDisplayContext.GUI, false,
                renderStack, bufferSource, 15728880, OverlayTexture.NO_OVERLAY, renderModel);
        // 检视页方块特效：BER 动画进主 batch（identity pose → 预览变换在
        // modelview 上，与物品 quads 同空间），粒子在 batch 落屏后 tesselator
        // 直绘（同样吃到 modelview）。非检视页（标志未设）零改动。
        boolean inspectEffects = INSPECT_ACTIVE && !INSPECT_DISABLED && INSPECT_STATE != null;
        if (inspectEffects) {
            renderInspectBlockEntity(mc, renderStack, bufferSource, renderModel);
        }
        bufferSource.endBatch();
        RenderSystem.enableDepthTest();
        if (inspectEffects) {
            renderInspectParticles(mc);
        }
        if (flat) Lighting.setupFor3DItems();
        pose.popPose();
        modelViewStack.popPose();
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
     *  ({@link #itemModelCenter}). Null when the model has no measurable
     *  geometry. */
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
     *  or degenerate quads): unit span keeps the caller's scale untouched. */
    private static final ModelBounds NO_BOUNDS = ModelBounds.of(new Vector3f(), 1.0F);

    /** The exact model matrix {@code ItemRenderer.render} applies for the GUI
     *  context: the model's gui {@link ItemTransform} (translate(translation)
     *  -> rotationXYZ -> scale) plus the renderer's {@code -0.5,-0.5,-0.5}
     *  shift, so 0..1 block geometry centres at origin. Shared by the pivot
     *  measures ({@link #modelBounds}, {@link #spriteCenter},
     *  {@link #blockBackedCenter}) — a pivot is only meaningful in the same
     *  space the geometry is drawn in. */
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

    /** True for any Superb Warfare (卓越前线) item. The namespace check alone
     *  proves the optional mod is loaded, and no Superb Warfare class is
     *  touched, so unlike the TACZ gate there is nothing to gate against. */
    static boolean isSuperbWarfareItem(ItemStack stack) {
        return !stack.isEmpty() && SUPERB_WARFARE_MOD_ID.equals(
                BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace());
    }

    /** True for a Superb Warfare summon container whose legacy NBT carries a
     *  {@code superbwarfare:} EntityType: the crate summons a vehicle when
     *  placed (ContainerBlockItem reads BlockItem.getBlockEntityData — the
     *  1.20.1 {@code tag.BlockEntityTag} compound — for its "EntityType"
     *  string), so the preview shows that vehicle (3D model, degrading to
     *  SBW's inventory icon). Vanilla-only types — no SBW class touched; an
     *  unresolvable id also fails closed to the crate icon
     *  (DefaultedRegistry.get falls back to the pig, so existence must go
     *  through containsKey). */
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

    /** The container NBT of a Superb Warfare summon crate on 1.20.1: the
     *  legacy {@code tag.BlockEntityTag} compound (what
     *  {@code BlockItem.getBlockEntityData} reads). Callers gate on
     *  "EntityType" presence themselves. */
    static CompoundTag superWarfareContainerTag(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? null : tag.getCompound("BlockEntityTag");
    }

    /** Superb Warfare vehicle preview. Primary path: render the referenced
     *  vehicle's real 3D bedrock model — a client-side entity of the crate's
     *  EntityType is created once (cached) and dispatched through the vanilla
     *  EntityRenderDispatcher, so SBW's own vehicle renderer draws the
     *  model with no SBW class referenced; size/centring come from the
     *  probe-measured bounds ({@link #vehicleEntityBounds}) via the shared
     *  fitScaleFor rule (L4 log evidence: modelEntries=1, vertices=true for
     *  freshly created entities). When the entity produces nothing usable
     *  (model resources missing) or the render fails, degrade to SBW's own
     *  inventory display method (verified in the 0.8.9.2 jar bytecode): the
     *  crate's BEWLR draws nothing in GUI and its item decorator blits
     *  {@code superbwarfare:textures/vehicle_icon/container/<path>.png} over
     *  the slot — the preview blits the same texture. An id without an icon
     *  texture falls back to the crate's own FIXED-context 3D content (what
     *  the BEWLR draws for a bare crate). */
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

    /** The real 3D vehicle draw: same pose convention as every other 3D
     *  branch (cell top-left origin, drag quaternion, centre pivot), scaled
     *  by the probe-measured bounds so oversized vehicles stay inside the
     *  preview square, with the same {@link #SBW_FIT_BOOST} the gun branch
     *  uses (the caller guarantees real bounds here). Returns false when the
     *  render throws, so the caller can degrade to the icon path. */
    private static boolean renderEntityPreview(GuiGraphics gg, Entity vehicle, ModelBounds bounds,
                                           int cx, int cy, Quat rotation, float scale) {
        return renderEntityPreview(gg, vehicle, bounds, cx, cy, rotation, scale, 1.0F);
    }

    /** fitBoost overload for the declared/SPI previews (docs/PREVIEW-API.md):
     *  multiplies the auto-fit result alongside {@link #SBW_FIT_BOOST}; 1.0F
     *  keeps the entity-fit semantics the built-in branches rely on. */
    private static boolean renderEntityPreview(GuiGraphics gg, Entity vehicle, ModelBounds bounds,
                                           int cx, int cy, Quat rotation, float scale, float fitBoost) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return false;
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
        fitScale *= SBW_FIT_BOOST * fitBoost;
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
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        PoseStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushPose();
        modelViewStack.mulPoseMatrix(pose.last().pose());
        RenderSystem.applyModelViewMatrix();
        PoseStack renderStack = REUSABLE_POSE_STACK;
        renderStack.setIdentity();
        try {
            renderPreviewEntity(vehicle, renderStack, bufferSource);
            bufferSource.endBatch();
            return true;
        } catch (Throwable t) {
            LOGGER.warn("Entity 3D preview render failed; degrading to the icon path", t);
            return false;
        } finally {
            modelViewStack.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.enableDepthTest();
            pose.popPose();
        }
    }

    /** The (shared, per preview context) client-side vehicle entity for a
     *  summon crate, or null when the EntityType is gone. The instance is
     *  cached per entity type: the geo model files are heavy (hundreds of
     *  KB) and the renderer animates against the entity, so a per-frame
     *  allocation would reload/replay every frame. Not added to the client
     *  world — it is rendered explicitly via the dispatcher. */
    private static Entity superbWarfareVehicleInstance(ItemStack stack, LivingEntity player) {
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
        if (type == null) {
            return null;
        }
        String cacheKey = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
        Entity cached = VEHICLE_PREVIEW_INSTANCES.get(cacheKey);
        if (cached != null && cached.level() == player.level()) {
            return cached;
        }
        try {
            Entity vehicle = type.create(player.level());
            if (vehicle == null) {
                return null;
            }
            // Park at a fixed in-world position: the dispatcher renders it at
            // (0,0,0) in preview space, but the entity's own yRot/animation
            // state is what the mod's renderer consumes. A neutral yaw keeps
            // the bedrock model facing the same way every preview. Invisible
            // suppresses the dispatcher's shadow pass for this shared
            // instance without touching its global shouldRenderShadow flag
            // (no public getter to save/restore it) — the geo renderer ignores
            // the invisible flag, so the model itself still draws.
            vehicle.setPos(0.0D, 64.0D, 0.0D);
            vehicle.setYRot(180.0F);
            vehicle.setXRot(0.0F);
            vehicle.setInvisible(true);
            VEHICLE_PREVIEW_INSTANCES.put(cacheKey, vehicle);
            return vehicle;
        } catch (Throwable t) {
            LOGGER.warn("Superb Warfare vehicle preview entity creation failed", t);
            return null;
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

    /** Centre + largest-axis span (block units) of an entity's rendered
     *  geometry, measured by probing one real entity render into the vertex
     *  recorder (entity renderers emit through the same VertexConsumer
     *  funnel as item renderers). Cached per entity type like the gun
     *  probes; a failed or empty probe keeps the raw preview scale. */
    private static ModelBounds entityPreviewBounds(Entity vehicle) {
        Object cached = VEHICLE_ENTITY_BOUNDS.get(vehicle.getType());
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
            // through the shadow-suppressed wrapper: a dispatcher shadow quad
            // is a flat 4-vertex plane at the feet that would sit in the
            // vertex stream (and slightly inflate the XZ envelope of small
            // creatures) alongside the real model geometry
            renderPreviewEntity(vehicle, probeStack, FIXED_CONTEXT_PROBE);
        } catch (Throwable t) {
            LOGGER.warn("Entity preview size probe failed; keeping the raw preview scale", t);
            rendered = false;
        }
        ModelBounds bounds = rendered && FIXED_CONTEXT_PROBE.any
                ? entityProbeBoundsFromVertices(null) : NO_BOUNDS;
        // One-shot diagnostic per entity type: keeps runtime evidence of the
        // vertex state for fresh preview entities.
        LOGGER.info("[csgobox] entity preview probe: type={} vertices={} span={}",
                BuiltInRegistries.ENTITY_TYPE.getKey(vehicle.getType()),
                FIXED_CONTEXT_PROBE.any, bounds == NO_BOUNDS ? "none" : bounds.span());
        VEHICLE_ENTITY_BOUNDS.put(vehicle.getType(), bounds == NO_BOUNDS ? NO_CENTER : bounds);
        return bounds;
    }

    /** The vehicle icon texture for a Superb Warfare EntityType id, or null
     *  when SBW ships none. Path mirrors SBW's ContainerItemDecorator: the
     *  entity id's path under {@code textures/vehicle_icon/container/},
     *  existence-checked against the resource manager (the gate upstream
     *  guarantees the superbwarfare namespace) and cached per vehicle path,
     *  misses included. */
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
        ResourceLocation icon;
        try {
            icon = new ResourceLocation(SUPERB_WARFARE_MOD_ID,
                    "textures/vehicle_icon/container/" + path + ".png");
        } catch (Throwable t) {
            VEHICLE_ICON_CACHE.put(path, NO_ICON);
            return null;
        }
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
     *  fallback; drag rotation is ignored, like the flat icon path. */
    static void renderVehicleIcon(GuiGraphics gg, ResourceLocation icon, float cx, float cy, float scale) {
        int size = Math.max(1, Math.round(16.0F * scale));
        blitTextured(gg, icon, Math.round(cx), Math.round(cy), size, size);
    }

    // ===================== Create schematic (Create 蓝图) structure preview =====================

    /** The item-id gate: {@code create:schematic}. The registry lookup only
     *  matches a registered item's namespace, and such an item cannot exist
     *  unless the mod is loaded — no Create class is ever named here; the
     *  reflective plumbing below is requested afterwards, once the gate
     *  proved the mod is on the classpath. */
    static boolean isCreateSchematicItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return "create".equals(itemId.getNamespace()) && "schematic".equals(itemId.getPath());
    }

    /** Create schematic 3D preview: the blueprint item stores a local
     *  {@code .nbt} structure file; the preview loads it through Create's own
     *  {@code loadSchematic} and renders the whole building with Create's own
     *  pipeline (fake SchematicLevel + SchematicRenderer + shared buffer),
     *  under the same pose convention as every other 3D branch. Unrenderable
     *  cases (Create absent, file missing, empty, over budget) degrade to
     *  the flat icon; the sentinel caches that so the file is not re-read
     *  every frame. */
    static void renderCreateSchematicPreview(GuiGraphics gg, ItemStack item, LivingEntity player,
                                                     int cx, int cy, Quat rotation, float scale) {
        CreateSchematicBundle bundle = createSchematicBundle(item, player);
        if (bundle == null) {
            renderItem2DFlat(player, gg, item, cx, cy, scale);
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
        PoseStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushPose();
        modelViewStack.mulPoseMatrix(pose.last().pose());
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
            modelViewStack.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.enableDepthTest();
            pose.popPose();
        }
    }

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
                // The item-id gate proved the mod is on the classpath, so this
                // class load is safe.
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
     *  All classes live in Create/the jar-jar'd Ponder bundle and are only
     *  requested after the item-id gate proved the mod is loaded. */
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
            // (2026-11-14 L4 log on the 1.21.1 twin).
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

    /** Superb Warfare 3D preview: same pose convention as the vanilla branch,
     *  but the render runs under {@code ItemDisplayContext.FIXED} — the
     *  item-frame context. The mod's separate-transforms model routes the gui
     *  perspective to a flat 2D icon and only the other perspectives carry
     *  the 3D gun (a GeckoLib custom renderer behind the per-gun display
     *  settings models), so FIXED is the cheapest context with a calibrated
     *  one-block transform. The custom renderer exposes no quads, so the
     *  size is measured by probing the render itself ({@link
     *  #fixedContextBounds}): oversized guns shrink to the preview square via
     *  {@link #fitScaleFor} and rotate around their rendered centre, exactly
     *  like the vanilla and TACZ branches. A renderer failure degrades to the
     *  2D icon. {@code restPose} is an optional model-space pose applied under
     *  the drag quaternion (per-mod rest orientations, e.g. the Sophisticated
     *  Backpacks pocket-face flip; null = none). Package-private: the SBW gun
     *  and SBP shell preview strategies' 3D body, and the generic
     *  custom-renderer branches of renderItem2D/renderItem3D. */
    static void renderFixedContextModel3D(GuiGraphics gg, ItemStack item, LivingEntity player,
                                          float cx, float cy, Quat rotation, float scale,
                                          Quaternionf restPose) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        BakedModel model = mc.getItemRenderer().getModel(item, player.level(), player, 0);
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
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        PoseStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushPose();
        modelViewStack.mulPoseMatrix(pose.last().pose());
        RenderSystem.applyModelViewMatrix();
        PoseStack renderStack = REUSABLE_POSE_STACK;
        renderStack.setIdentity();
        boolean rendered = true;
        try {
            mc.getItemRenderer().render(item, ItemDisplayContext.FIXED, false,
                    renderStack, bufferSource, 15728880, OverlayTexture.NO_OVERLAY, model);
            bufferSource.endBatch();
        } catch (Throwable t) {
            LOGGER.warn("Superb Warfare 3D preview render failed; falling back to the flat icon", t);
            rendered = false;
        } finally {
            modelViewStack.popPose();
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

    /** Third-party preview dispatch (2026-11-22 预览适配接口 v1): draws a
     *  PreviewSource from the code SPI (CsboxPreviewAPI) or a JSON declaration
     *  (assets/&lt;mod&gt;/csgobox_preview/) through the same pipeline the
     *  built-in strategies use — probes, auto-fit, pivot and the flat degrade
     *  all apply. rest_pose composes into the drag quaternion in model space
     *  (the same space {@link #renderFixedContextModel3D} applies its restPose
     *  in); fit_boost multiplies the auto-fit result, never the cell
     *  centring. Package-private: called from renderItem3D only. */
    static void renderPreviewSource(GuiGraphics gg, ItemStack stack, LivingEntity player,
                                    int cx, int cy, Quat rotation, float scale, PreviewSource source) {
        if (source.isRaw()) {
            source.rawRenderer().render(gg, stack, player, cx, cy, rotation, scale);
            return;
        }
        Quat rotationWithRest = source.restPose().isNone()
                ? rotation
                : Quat.mul(rotation, restPoseQuat(source.restPose()));
        if (source.isEntity()) {
            renderEntityPreviewForType(gg, stack, player, cx, cy, rotationWithRest, scale,
                    source.entityType(), source.spawnData(), source.fitBoost());
            return;
        }
        if (source.isBlock()) {
            renderBlockStatePreview(gg, stack, player, cx, cy, rotationWithRest, scale,
                    source.blockState(), source.fitBoost());
        }
    }

    /** Model-space quaternion of a declared rest pose (degrees): yaw around
     *  Y, then pitch around X, then roll around Z (the rotationYXZ
     *  decomposition); a pure-yaw pose behaves exactly like the SBP shell's
     *  FRONT_REST_YAW precedent. Composed under the drag rotation by the
     *  caller, so dragging keeps rotating around the screen axes. */
    private static Quat restPoseQuat(RestPose pose) {
        Quat yaw = Quat.fromAxisAngle(0, 1, 0, (float) Math.toRadians(pose.yawDeg()));
        Quat pitch = Quat.fromAxisAngle(1, 0, 0, (float) Math.toRadians(pose.pitchDeg()));
        Quat roll = Quat.fromAxisAngle(0, 0, 1, (float) Math.toRadians(pose.rollDeg()));
        return Quat.norm(Quat.mul(Quat.mul(yaw, pitch), roll));
    }

    /** Block-state 3D preview (declared "mode":"block" / PreviewSource.block):
     *  renders the block's baked model through the same pose chain as the
     *  default path, pivoting on the base-footprint cuboid
     *  ({@link #blockBackedCenter}) and auto-fitting on the measured span.
     *  The caller's ItemStack is only the degrade target: a custom-renderer
     *  or unmeasurable block model falls back to the flat icon instead of
     *  guessing. fitBoost multiplies the auto-fit result. */
    static void renderBlockStatePreview(GuiGraphics gg, ItemStack stack, LivingEntity player,
                                        int cx, int cy, Quat rotation, float scale,
                                        BlockState state, float fitBoost) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        BakedModel blockModel;
        try {
            blockModel = mc.getBlockRenderer().getBlockModel(state);
        } catch (Throwable t) {
            LOGGER.warn("[csgobox] declared block preview failed to resolve a model", t);
            blockModel = null;
        }
        if (blockModel == null || blockModel.isCustomRenderer() || modelBounds(blockModel) == NO_BOUNDS) {
            renderItem2DFlat(player, gg, stack, cx, cy, scale);
            return;
        }
        ModelBounds bounds = modelBounds(blockModel);
        float fitScale = fitScaleFor(bounds.span(), scale) * fitBoost;
        PoseStack pose = gg.pose();
        pose.pushPose();
        pose.translate(cx, cy, 100.0F);
        pose.translate(8.0F * scale, 8.0F * scale, 0.0F);
        pose.scale(1.0F, -1.0F, 1.0F);
        SCRATCH_QUAT.set(rotation.x(), rotation.y(), rotation.z(), rotation.w()).normalize();
        pose.mulPose(SCRATCH_QUAT);
        Lighting.setupForEntityInInventory();
        pose.scale(16.0F * fitScale, 16.0F * fitScale, 16.0F * fitScale);
        Vector3f center = blockBackedCenter(blockModel);
        if (center != null) {
            pose.translate(-center.x, -center.y, -center.z);
        }
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        boolean flat = !blockModel.usesBlockLight();
        if (flat) Lighting.setupForFlatItems();
        PoseStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushPose();
        modelViewStack.mulPoseMatrix(pose.last().pose());
        RenderSystem.applyModelViewMatrix();
        PoseStack renderStack = REUSABLE_POSE_STACK;
        renderStack.setIdentity();
        boolean rendered = true;
        try {
            mc.getItemRenderer().render(stack, ItemDisplayContext.GUI, false,
                    renderStack, bufferSource, 15728880, OverlayTexture.NO_OVERLAY, blockModel);
            bufferSource.endBatch();
        } catch (Throwable t) {
            LOGGER.warn("Declared block preview render failed; falling back to the flat icon", t);
            rendered = false;
        } finally {
            modelViewStack.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.enableDepthTest();
            pose.popPose();
        }
        if (flat) Lighting.setupFor3DItems();
        if (!rendered) {
            renderItem2DFlat(player, gg, stack, cx, cy, scale);
        }
    }

    /** Offline probe sink for {@link #fixedContextBounds}: hands the probe
     *  render one vertex recorder regardless of render type and discards
     *  everything else, so the measurement draws nothing and touches no
     *  shared GPU batch. Vanilla-default VertexConsumer flows (foil glint,
     *  14-arg vertex) all funnel into these abstract methods. */
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
        public VertexConsumer vertex(double x, double y, double z) {
            if (shared != null) {
                return shared.vertex(x, y, z);
            }
            float fx = (float) x, fy = (float) y, fz = (float) z;
            minX = Math.min(minX, fx);
            minY = Math.min(minY, fy);
            minZ = Math.min(minZ, fz);
            maxX = Math.max(maxX, fx);
            maxY = Math.max(maxY, fy);
            maxZ = Math.max(maxZ, fz);
            vx.add(fx);
            vy.add(fy);
            vz.add(fz);
            any = true;
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            return this;
        }

        @Override
        public void endVertex() {
        }

        @Override
        public void defaultColor(int red, int green, int blue, int alpha) {
        }

        @Override
        public void unsetDefaultColor() {
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

    /** Axis-aligned box of an item's rendered geometry in a display context
     *  ({@code {minX, minY, minZ, maxX, maxY, maxZ}}, block units): the same
     *  probe render as {@link #fixedContextBounds}, uncached, in the
     *  display-transform space that context's {@code ItemRenderer.render}
     *  call consumes — so a caller that poses the item with its own matrix
     *  afterwards (Epic Fight hangs the held item on a joint) can carry the
     *  eight corners through that matrix. Null when the probe drew nothing
     *  or threw. Render thread only (the probe sink is shared). */
    static float[] displayContextBox(ItemStack item, BakedModel model, ItemDisplayContext context,
                                     boolean leftHand) {
        FIXED_CONTEXT_PROBE.reset();
        try {
            Minecraft.getInstance().getItemRenderer().render(item, context, leftHand,
                    new PoseStack(), FIXED_CONTEXT_PROBE, 0, OverlayTexture.NO_OVERLAY, model);
        } catch (Throwable t) {
            return null;
        }
        if (!FIXED_CONTEXT_PROBE.any) {
            return null;
        }
        return new float[]{FIXED_CONTEXT_PROBE.minX, FIXED_CONTEXT_PROBE.minY, FIXED_CONTEXT_PROBE.minZ,
                FIXED_CONTEXT_PROBE.maxX, FIXED_CONTEXT_PROBE.maxY, FIXED_CONTEXT_PROBE.maxZ};
    }

    /** TACZ gun 3D render: same pose convention as the vanilla branch, but
     *  the model is a {@link BedrockGunModel} drawn on an identity stack via
     *  RenderSystem's model-view matrix — the vanilla ItemRenderer path only
     *  draws the flat slot texture. */
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
        PoseStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushPose();
        modelViewStack.mulPoseMatrix(pose.last().pose());
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
            modelViewStack.popPose();
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
        // JOML Matrix4f.rotate has no identity short-circuit and always
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

    // ============= Generic entity previews (spawn eggs / armor / summon cards / same-id entities) =============

    /** Item ids whose same-named entity must never become the 3D preview:
     *  escape hatch for convention collisions (L4 finds one, add its
     *  {@code namespace:path} item id). Empty today. */
    private static final Set<String> ENTITY_CONVENTION_DENYLIST = Set.of();

    /** Parked client-side preview entities for the generic conventions,
     *  cached per entity-type id (separate from the SBW vehicle cache —
     *  vehicles keep their tuned parking). Not added to the client world —
     *  rendered explicitly via the dispatcher. */
    // Strong map on purpose: a WeakHashMap keyed by the freshly-built registry-id
    // string drops every entry on the next GC, and the next frame then silently
    // recreates a fresh entity (default pose = static), so the inspect animation
    // drove an instance that was no longer the rendered one. Bounded by the
    // number of entity types ever previewed; stale-level entries are replaced on
    // first use after a world change (see entityPreviewInstance).
    private static final Map<String, Entity> ENTITY_PREVIEW_INSTANCES = new HashMap<>();

    /** Flat-sprite verdict per baked item model, cached like
     *  {@code ITEM_MODEL_BOUNDS}: true when the model's raw geometry is a
     *  1/16-thick slab extruded along Z — the generated {@code item/generated}
     *  sprite shape at any layer count — versus real 3D geometry that spans
     *  beyond the slab. 2026-11-14: the earlier "zero-thickness" and "one
     *  shared normal axis" tests never fired (the generator bakes an extruded
     *  silhouette with edge faces); see {@code measureFlatSprite}. */
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
     *  match the placed look (mod food plates, baskets). Amount-style
     *  integer properties are maxed — and {@code bites}, which counts bites
     *  taken, is reset to the fresh state — so the preview shows the
     *  full/served state rather than the freshly placed empty one. The
     *  original model is returned unchanged when the item is not
     *  block-backed, its model is not flat, or the block has no measurable
     *  geometry. */
    private static BakedModel blockBackedModel(ItemStack item, BakedModel fallback) {
        return blockBackedModel(item, fallback, false);
    }

    /** {@code allowEmpty} resolves an empty block model (zero quads — visuals
     *  live in the block's BlockEntityRenderer) instead of keeping the flat
     *  icon: the inspect-BER path uses it to suppress the icon and let the
     *  BER drive the preview. */
    private static BakedModel blockBackedModel(ItemStack item, BakedModel fallback, boolean allowEmpty) {
        if (!(item.getItem() instanceof BlockItem blockItem) || !isFlatSpriteModel(fallback)) {
            return fallback;
        }
        try {
            net.minecraft.world.level.block.state.BlockState state =
                    blockItem.getBlock().defaultBlockState();
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
            if (blockModel == null || blockModel.isCustomRenderer()) {
                return fallback;
            }
            if (modelBounds(blockModel) == NO_BOUNDS) {
                return allowEmpty ? blockModel : fallback;
            }
            return blockModel;
        } catch (Throwable t) {
            LOGGER.warn("Block-backed 3D preview fallback failed for {}",
                    BuiltInRegistries.ITEM.getKey(item.getItem()), t);
            return fallback;
        }
    }

    /** Resolves the same-id entity convention: an entity type sharing the
     *  flat-sprite item's registry id means the 3D form lives on the entity
     *  (modded summonables and vanilla kin: minecarts, armor stand, end
     *  crystal, item frames, painting, trident). Food items never match (raw
     *  chicken vs. the chicken entity — the food item is the truth; 1.20.1
     *  has no components, so the guard is the item's own {@code isEdible}),
     *  and the denylist overrides everything. Null when the convention does
     *  not apply. */
    private static EntityType<?> conventionEntityType(ItemStack item) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item.getItem());
        if (ENTITY_CONVENTION_DENYLIST.contains(itemId.toString())
                || item.getItem().isEdible()) {
            return null;
        }
        return BuiltInRegistries.ENTITY_TYPE.containsKey(itemId)
                ? BuiltInRegistries.ENTITY_TYPE.get(itemId) : null;
    }

    /** Generic summon-container convention: any item whose
     *  {@code tag.BlockEntityTag} (Forge's BlockItem BE data storage on
     *  1.20.1) stores an {@code EntityType} string that resolves to a
     *  registered entity — the Superb Warfare summon-crate convention, also
     *  matched for addon mods reusing it under their own namespaces. The
     *  tag is deliberately not loaded onto the preview entity (block-entity
     *  fields are not entity save data). Null when the convention does not
     *  apply. */
    static EntityType<?> summonContainerEntityType(ItemStack stack) {
        if (stack.isEmpty() || !stack.hasTag()) {
            return null;
        }
        try {
            CompoundTag beTag = stack.getTag().getCompound("BlockEntityTag");
            if (beTag.isEmpty() || !beTag.contains("EntityType", 8)) {
                return null;
            }
            ResourceLocation typeId = ResourceLocation.tryParse(beTag.getString("EntityType"));
            return typeId != null && BuiltInRegistries.ENTITY_TYPE.containsKey(typeId)
                    ? BuiltInRegistries.ENTITY_TYPE.get(typeId) : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Spawn-egg {@code EntityTag} (1.20.1 NBT storage of the egg's mob and
     *  its variant data); re-applied onto the shared preview instance every
     *  frame so two variants of one type never bleed into each other. */
    static CompoundTag spawnEntityTag(ItemStack stack) {
        try {
            if (stack.hasTag() && stack.getTag().contains("EntityTag", 10)) {
                CompoundTag tag = stack.getTag().getCompound("EntityTag");
                return tag.isEmpty() ? null : tag;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** The parked client-side preview entity for an entity type, or null on
     *  failure. Cached per entity type: the model resources are heavy and
     *  the renderer animates against the entity, so a per-frame allocation
     *  would reload/replay every frame. Not added to the client world. The
     *  entity stays fully visible: LivingEntity renderers skip the whole
     *  model for invisible entities, which starved the probe to zero
     *  vertices on every vanilla mob — the shadow pass is suppressed per
     *  render instead (see {@link #renderPreviewEntity}). */
    private static Entity entityPreviewInstance(EntityType<?> type, Level level) {
        String cacheKey = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
        Entity cached = ENTITY_PREVIEW_INSTANCES.get(cacheKey);
        if (cached != null && cached.level() == level) {
            return cached;
        }
        try {
            Entity entity = type.create(level);
            if (entity == null) {
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

    /** Entity-based 3D preview for an arbitrary EntityType (spawn eggs,
     *  summon cards, same-id entities): park a shared client entity, apply
     *  {@code spawnTag} every frame (kept after {@code load} so the parked
     *  pose never drifts), probe its geometry, draw, and degrade to the flat
     *  icon when the entity produces nothing usable or the render fails.
     *  Bounds stay cached per type: variant geometry differences never
     *  change the largest axis. */
    static void renderEntityPreviewForType(GuiGraphics gg, ItemStack stack, LivingEntity player,
                                           int cx, int cy, Quat rotation, float scale, EntityType<?> type,
                                           CompoundTag spawnTag) {
        renderEntityPreviewForType(gg, stack, player, cx, cy, rotation, scale, type, spawnTag, 1.0F);
    }

    /** fitBoost overload for the declared/SPI previews (docs/PREVIEW-API.md):
     *  multiplies the auto-fit result — never the cell centring; 1.0F keeps
     *  the pipeline exactly as the built-in branches drive it. */
    static void renderEntityPreviewForType(GuiGraphics gg, ItemStack stack, LivingEntity player,
                                           int cx, int cy, Quat rotation, float scale, EntityType<?> type,
                                           CompoundTag spawnTag, float fitBoost) {
        Entity entity = entityPreviewInstance(type, player.level());
        markPreviewMob(entity);
        if (entity != null) {
        // Entity.load resets yHeadRot/yBodyRot on every call; identical
        // spawn data must not be re-applied per frame or it wipes the
        // inspect animation state (see previewSpawnDataChanged).
            if (spawnTag != null
                    && previewSpawnDataChanged(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString(), stack)) {
                try {
                    entity.load(spawnTag);
                    entity.setPos(0.0D, 64.0D, 0.0D);
                    entity.setYRot(180.0F);
                    entity.setXRot(0.0F);
                } catch (Throwable t) {
                    LOGGER.warn("Entity preview: applying the spawn tag failed; keeping the default look", t);
                }
            }
            ModelBounds bounds = entityPreviewBounds(entity);
            if (bounds != NO_BOUNDS && renderEntityPreview(gg, entity, bounds, cx, cy, rotation, scale, fitBoost)) {
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
        // 1.20.1: the model-view stack is a PoseStack (pushPose/mulPoseMatrix),
        // unlike the 1.21.1 Matrix4fStack.
        PoseStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushPose();
        modelViewStack.mulPoseMatrix(pose.last().pose());
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
            modelViewStack.popPose();
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

    /** Armor preview on the shared preview player: equip the piece (its
     *  {@code ArmorItem} slot) and render the player wearing it — the worn
     *  look on the viewer's own skin (2026-11-22: replaced the invisible
     *  armor stand; armor layers registered on the player renderer, the
     *  common shape for modded armors, now come along). Degrades to the
     *  flat icon when the carrier or the install fails. Animal armor
     *  (wolf/horse) is not an {@code ArmorItem} and stays uncovered. */
    static void renderArmorPlayerPreview(GuiGraphics gg, ItemStack stack, LivingEntity player,
                                         int cx, int cy, Quat rotation, float scale) {
        LivingEntity carrier = curiosPlayerInstance(player.level());
        if (carrier != null && armorInstallOnPreview(carrier, stack)) {
            ModelBounds bounds = armorPreviewBounds(stack, carrier);
            if (bounds != NO_BOUNDS && renderEntityPreview(gg, carrier, bounds, cx, cy, rotation, scale)) {
                return;
            }
        }
        renderItem2DFlat(player, gg, stack, cx, cy, scale);
    }

    /** Equips {@code stack} into its {@code ArmorItem} slot on the shared
     *  carrier: every equipment slot is wiped first (the carrier is shared
     *  across inspections — stale pieces must not linger), then the curios
     *  slots too (that wipe is kept behind the mod gate — curios classes
     *  must load only on gated paths). */
    private static boolean armorInstallOnPreview(LivingEntity carrier, ItemStack stack) {
        try {
            EquipmentSlot slot = ((ArmorItem) stack.getItem()).getEquipmentSlot();
            for (EquipmentSlot clearSlot : EquipmentSlot.values()) {
                carrier.setItemSlot(clearSlot, ItemStack.EMPTY);
            }
            if (ModList.get().isLoaded("curios")) {
                curiosClearWearables(carrier);
            }
            carrier.setItemSlot(slot, stack);
            return true;
        } catch (Throwable t) {
            LOGGER.warn("Armor preview install failed for {}",
                    BuiltInRegistries.ITEM.getKey(stack.getItem()), t);
            return false;
        }
    }

    /** Bounds of an armor piece worn by the shared preview player, cached
     *  per item id (the carrier is constant but its worn model varies per
     *  piece). The carrier must already be equipped when probing. */
    private static final Map<String, Object> ARMOR_PREVIEW_BOUNDS = new HashMap<>();

    private static ModelBounds armorPreviewBounds(ItemStack stack, Entity carrier) {
        String cacheKey = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        Object cached = ARMOR_PREVIEW_BOUNDS.get(cacheKey);
        if (cached instanceof ModelBounds bounds) {
            return bounds;
        }
        if (cached == NO_BOUNDS) {
            return NO_BOUNDS;
        }
        ModelBounds bounds = probeEntityBounds(carrier);
        if (bounds == NO_BOUNDS) {
            LOGGER.info("[csgobox] armor preview: {} probe empty; staying on the flat icon", cacheKey);
        } else {
            LOGGER.info("[csgobox] armor preview: {} span={} (player-carrier fit)", cacheKey, bounds.span());
        }
        ARMOR_PREVIEW_BOUNDS.put(cacheKey, bounds);
        return bounds;
    }

    /** Boat preview on the shared boat/chest-boat instances: the item ids
     *  carry the wood prefix (oak_boat) while the entity ids are plain
     *  boat/chest_boat and the wood lives in the boat's {@link Boat.Type}
     *  variant — bamboo rafts and pale oak share ids with their entities
     *  and ride the plain same-id convention instead. */
    static void renderBoatPreview(GuiGraphics gg, ItemStack stack, LivingEntity player,
                                  int cx, int cy, Quat rotation, float scale) {
        Boat.Type type = boatTypeOf(stack);
        Entity entity = entityPreviewInstance(
                chestBoat(stack) ? EntityType.CHEST_BOAT : EntityType.BOAT, player.level());
        if (entity instanceof Boat boat && type != null) {
            // The instance is shared across every boat item: re-apply the
            // wood per render or one boat would paint all the others.
            boat.setVariant(type);
            ModelBounds bounds = boatPreviewBounds(stack, boat);
            if (bounds != NO_BOUNDS && renderEntityPreview(gg, boat, bounds, cx, cy, rotation, scale)) {
                return;
            }
        }
        renderItem2DFlat(player, gg, stack, cx, cy, scale);
    }

    /** The wood {@link Boat.Type} of a boat item id ({@code oak_boat} →
     *  OAK), or null for unknown woods — modded boats degrade to the flat
     *  icon rather than render the wrong wood. */
    private static Boat.Type boatTypeOf(ItemStack stack) {
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        String wood;
        if (path.endsWith("_chest_boat")) {
            wood = path.substring(0, path.length() - "_chest_boat".length());
        } else if (path.endsWith("_boat")) {
            wood = path.substring(0, path.length() - "_boat".length());
        } else {
            return null;
        }
        return Boat.Type.CODEC.byName(wood, null);
    }

    private static boolean chestBoat(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().endsWith("_chest_boat");
    }

    /** Bounds of a boat variant on the shared preview boat, cached per item
     *  id (the entity type is constant but its model varies per wood). The
     *  boat must already carry its variant when probing. */
    private static final Map<String, Object> BOAT_PREVIEW_BOUNDS = new HashMap<>();

    private static ModelBounds boatPreviewBounds(ItemStack stack, Boat boat) {
        String cacheKey = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        Object cached = BOAT_PREVIEW_BOUNDS.get(cacheKey);
        if (cached instanceof ModelBounds bounds) {
            return bounds;
        }
        if (cached == NO_BOUNDS) {
            return NO_BOUNDS;
        }
        ModelBounds bounds = probeEntityBounds(boat);
        BOAT_PREVIEW_BOUNDS.put(cacheKey, bounds == NO_BOUNDS ? NO_BOUNDS : bounds);
        return bounds;
    }

    // ===================== Curios wearable on the player (inspect) =====================

    /** Shared client-side carrier that previews curios wearables: the item is
     *  equipped into its curios inventory and the player renderer's Curios
     *  layer draws it at its worn position — exactly what the player sees in
     *  the world. The carrier is a RemotePlayer subclass so curios' own
     *  client renderers run untouched (zero curio-mod classes referenced from
     *  our side); the name plate is suppressed via the entity-level flag. */
    private static final String CURIOS_PLAYER_CACHE_KEY = "csgobox:curios_player";

    /** Items whose curios body renderer is registered — probe result cached
     *  per item id (matching runs every preview frame). Strong map: a weak
     *  one silently rebuilt entries and spawned fresh carriers per GC
     *  (2026-11-14 lesson). */
    private static final Map<String, Boolean> CURIOS_WEARABLE_MATCH = new HashMap<>();

    /** First curios slot slug that accepts an item, cached per item id. */
    private static final Map<String, Object> CURIOS_WEARABLE_SLUG = new HashMap<>();

    /** Player-carrier preview bounds cached per item id (the carrier is
     *  constant but the worn piece varies the envelope). */
    private static final Map<String, Object> CURIOS_PREVIEW_BOUNDS = new HashMap<>();

    /** True when the stack is a curios the player would visibly wear: the
     *  ICurio capability is present, some curios slot accepts the item, and
     *  its mod registered a curios body renderer (the only way curios 5.x
     *  draws on the body — items without one would preview as a bare player,
     *  which reads as "not equipped"). Caller must gate on
     *  {@code ModList.isLoaded("curios")} first; every Curios class reference
     *  sits behind that gate. */
    static boolean isCuriosBodyWearable(ItemStack stack) {
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        Boolean cached = CURIOS_WEARABLE_MATCH.get(id);
        if (cached != null) {
            return cached;
        }
        boolean wearable = curiosProbeWearable(stack);
        CURIOS_WEARABLE_MATCH.put(id, wearable);
        return wearable;
    }

    private static boolean curiosProbeWearable(ItemStack stack) {
        try {
            if (!CuriosApi.getCurio(stack).isPresent()) {
                return false;
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) {
                return false;
            }
            Map<String, ISlotType> slots = CuriosApi.getItemStackSlots(stack, mc.level);
            if (slots.isEmpty()) {
                return false;
            }
            return CuriosRendererRegistry.getRenderer(stack.getItem()).isPresent();
        } catch (Throwable t) {
            LOGGER.warn("Curios wearable probe failed for {}",
                    BuiltInRegistries.ITEM.getKey(stack.getItem()), t);
            return false;
        }
    }

    /** 3D preview of a curios wearable: equip the shared preview player and
     *  render it — the worn look, not the floating item. Degrades to the
     *  flat icon when the carrier or the slot install fails. */
    static void renderCuriosPlayerPreview(GuiGraphics gg, ItemStack stack, LivingEntity player,
                                          int cx, int cy, Quat rotation, float scale) {
        LivingEntity carrier = curiosPlayerInstance(player.level());
        String slug = curiosWearableSlug(stack);
        if (carrier == null || slug == null || !curiosInstallOnPreview(carrier, stack, slug)) {
            renderItem2DFlat(player, gg, stack, cx, cy, scale);
            return;
        }
        ModelBounds bounds = curiosPreviewBounds(stack, carrier);
        if (bounds != NO_BOUNDS && renderEntityPreview(gg, carrier, bounds, cx, cy, rotation, scale)) {
            return;
        }
        renderItem2DFlat(player, gg, stack, cx, cy, scale);
    }

    /** The shared preview player, cached like the other preview entities.
     *  Built from the local player's game profile so the preview shows the
     *  viewer's own skin; curios slot handlers are built client-side via
     *  {@code reset()} (real players receive them through server sync, a
     *  locally constructed one must assemble them from the slot registry). */
    private static LivingEntity curiosPlayerInstance(Level level) {
        Entity cached = ENTITY_PREVIEW_INSTANCES.get(CURIOS_PLAYER_CACHE_KEY);
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
            CuriosApi.getCuriosInventory(carrier).resolve().ifPresent(ICuriosItemHandler::reset);
            ENTITY_PREVIEW_INSTANCES.put(CURIOS_PLAYER_CACHE_KEY, carrier);
            LOGGER.info("[csgobox] curios preview: player carrier ready");
            return carrier;
        } catch (Throwable t) {
            LOGGER.warn("Curios preview player creation failed", t);
            return null;
        }
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

    /** Installs {@code stack} at slot 0 of {@code slug}: the carrier is
     *  shared across every inspected item, so every curios slot (functional
     *  and cosmetic) plus all equipment slots are wiped first or the
     *  previous piece keeps rendering alongside the new one. */
    private static boolean curiosInstallOnPreview(LivingEntity carrier, ItemStack stack, String slug) {
        try {
            curiosClearWearables(carrier);
            for (EquipmentSlot clearSlot : EquipmentSlot.values()) {
                carrier.setItemSlot(clearSlot, ItemStack.EMPTY);
            }
            Optional<ICuriosItemHandler> inv = CuriosApi.getCuriosInventory(carrier).resolve();
            if (inv.isEmpty()) {
                return false;
            }
            inv.get().setEquippedCurio(slug, 0, stack);
            return true;
        } catch (Throwable t) {
            LOGGER.warn("Curios preview install failed for slug={}", slug, t);
            return false;
        }
    }

    /** Wipes every curios slot (functional and cosmetic) on the carrier;
     *  callers must be on curios-gated paths — this body references curios
     *  classes directly. */
    private static void curiosClearWearables(LivingEntity carrier) {
        Optional<ICuriosItemHandler> inv = CuriosApi.getCuriosInventory(carrier).resolve();
        if (inv.isEmpty()) {
            return;
        }
        for (ICurioStacksHandler stacksHandler : inv.get().getCurios().values()) {
            IDynamicStackHandler stacks = stacksHandler.getStacks();
            for (int i = 0; i < stacks.getSlots(); i++) {
                stacks.setStackInSlot(i, ItemStack.EMPTY);
            }
            IDynamicStackHandler cosmetic = stacksHandler.getCosmeticStacks();
            for (int i = 0; i < cosmetic.getSlots(); i++) {
                cosmetic.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
    }

    /** First curios slot slug that accepts the item (alphabetical for
     *  determinism; multi-slot items preview in their first one). */
    private static String curiosWearableSlug(ItemStack stack) {
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        Object cached = CURIOS_WEARABLE_SLUG.get(id);
        if (cached instanceof String slug) {
            return slug;
        }
        Minecraft mc = Minecraft.getInstance();
        String slug = null;
        if (mc.level != null) {
            Map<String, ISlotType> slots = CuriosApi.getItemStackSlots(stack, mc.level);
            slug = slots.keySet().stream().sorted().findFirst().orElse(null);
        }
        CURIOS_WEARABLE_SLUG.put(id, slug);
        return slug;
    }

    /** Bounds of the player carrier wearing {@code stack}, cached per item
     *  id — a one-shot INFO records the fit the first time each piece is
     *  probed (same diagnostic shape as the entity previews). */
    private static ModelBounds curiosPreviewBounds(ItemStack stack, Entity carrier) {
        String cacheKey = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        Object cached = CURIOS_PREVIEW_BOUNDS.get(cacheKey);
        if (cached instanceof ModelBounds bounds) {
            return bounds;
        }
        if (cached == NO_BOUNDS) {
            return NO_BOUNDS;
        }
        ModelBounds bounds = probeEntityBounds(carrier);
        if (bounds == NO_BOUNDS) {
            LOGGER.info("[csgobox] curios preview: {} probe empty; staying on the flat icon", cacheKey);
        } else {
            LOGGER.info("[csgobox] curios preview: {} span={} (player-carrier fit)", cacheKey, bounds.span());
        }
        CURIOS_PREVIEW_BOUNDS.put(cacheKey, bounds);
        return bounds;
    }

    /** Raw (uncached) probe of an entity's rendered geometry into the shared
     *  vertex recorder — the per-type cache lives in
     *  {@link #entityPreviewBounds} the per-item armor cache in
     *  {@link #armorPreviewBounds} and the per-item boat cache in
     *  {@link #boatPreviewBounds}; all funnel here. */
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
        ModelBounds bounds = NO_BOUNDS;
        if (rendered && FIXED_CONTEXT_PROBE.any) {
            bounds = entityProbeBoundsFromVertices(hubOverride);
        }
        return bounds;
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

    /** The dispatcher's private shadow switch ({@code shouldRenderShadow}),
     *  resolved once via reflection. The shadow pass must be suppressed
     *  around preview renders because the preview entities are fully
     *  visible; null when reflection fails — previews then keep the shadow
     *  pass (cosmetic only). */
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

    // ===================== SlashBlade rack-form preview (挂刀架形态) =====================

    /** SlashBlade (拔刀剑: Resharped) item namespace. The namespace check
     *  alone proves the optional mod is loaded; no SlashBlade class is ever
     *  referenced at compile time — the blade gate resolves the base item
     *  class reflectively (cached), and the preview below speaks to the mod
     *  through vanilla APIs and two public fields only. */
    private static final String SLASHBLADE_MOD_ID = "slashblade";

    /** The weapon-rack entity ({@code BladeStandEntity}, 刀挂台) and the
     *  floor-stand item ({@code BLADESTAND_1}) the preview mounts blades on.
     *  The entity id derivation is jar-verified: the mod builds entity ids
     *  via {@code CaseFormat(UPPER_CAMEL → LOWER_UNDERSCORE)(getSimpleName())
     *  .replace("entity_", "")} — {@code BladeStandEntity} carries no
     *  {@code entity_} substring, so the id is the plain snake-case form,
     *  matching the {@code entity.slashblade.blade_stand_entity} lang key. */
    private static final ResourceLocation SLASHBLADE_STAND_TYPE =
            new ResourceLocation(SLASHBLADE_MOD_ID, "blade_stand_entity");
    private static final ResourceLocation SLASHBLADE_STAND_ITEM =
            new ResourceLocation(SLASHBLADE_MOD_ID, "bladestand_1");
    /** The wall-mount stand ({@code BLADESTAND_1_W}): its renderer branch
     *  (YP 180, skipping the floor branches' XP(-90)) keeps the common tail
     *  XP(90) uncancelled and stands the blade upright — the 立式 style of
     *  the random pair. The floor stands' XP(-90) cancels that tail, which
     *  is the horizontal rack pose (挂式). */
    private static final ResourceLocation SLASHBLADE_STAND_ITEM_WALL =
            new ResourceLocation(SLASHBLADE_MOD_ID, "bladestand_1w");

    /** {@code mods.flammpfeil.slashblade.item.ItemSlashBlade}, resolved once
     *  after the namespace gate proved the mod is on the classpath (the
     *  detuned variant and every named blade extend it — javap-verified).
     *  Null when unresolvable: the gate then never matches. */
    private static Class<?> SLASHBLADE_BLADE_CLASS;
    private static boolean SLASHBLADE_BLADE_CLASS_RESOLVED;

    /** {@code BladeStandEntity.currentType} (the rack model branch selector)
     *  and {@code currentTypeStack} (the mounted stack) — both public fields
     *  on the mod's entity (javap-verified), reached by reflection so this
     *  file stays SlashBlade-class-free at compile time. Null after a failed
     *  resolve and the preview degrades to the flat icon. */
    private static Field SLASHBLADE_STAND_TYPE_FIELD;
    private static Field SLASHBLADE_STAND_STACK_FIELD;
    private static boolean SLASHBLADE_STAND_FIELDS_RESOLVED;

    /** Probe-measured bounds of each blade variant on the shared rack stand,
     *  keyed by item id + hover name: the 24 named blades share one item id
     *  ({@code slashblade:slashblade}) and differ by their {@code
     *  bladeState} identity, which the hover name reflects — the per-type
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
     *  materials (plain Item) out of the rack preview. */
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
     *  the fields or the render are unavailable. */
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
     *  item id and differ by their {@code bladeState} identity, which the
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
     *  {@link SlashBladeComboInspect}): a dedicated carrier — the curios one
     *  carries its slot handlers and is shared with the armor preview —
     *  built from the inspecting player's profile so the figure wears their
     *  skin. Never added to the world: the mod's blade layer reads only the
     *  rendered entity's main-hand state and follows game time by itself. */
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
     *  {@code MaidInfo} save tag so the preview shows the exact stored maid.
     *  Bounds are cached per model id: maid model sizes vary wildly (chibi,
     *  yukkuri, humanoid), so the per-type entity bounds cache cannot be
     *  reused. Degrades to the flat icon whenever the entity, the model set,
     *  or the render itself is unavailable. */
    private static final String MAID_MOD_ID = "touhou_little_maid";
    private static final ResourceLocation MAID_TYPE_ID =
            new ResourceLocation(MAID_MOD_ID, "maid");
    private static final String MAID_SLAB_INIT_PATH = "smart_slab_init";
    private static final String MAID_SLAB_HAS_MAID_PATH = "smart_slab_has_maid";
    /** The maid's model id save key: camel-case {@code ModelId} on this 1.20.1
     *  line (EntityMaid.MODEL_ID_TAG); the 1.21.1 NeoForge line uses the
     *  lowercase {@code model_id}. */
    private static final String MAID_NBT_MODEL_KEY = "ModelId";
    /** YSM-stored maids need the YSM renderer; without the YSM mod installed
     *  the shared renderer would fall back to whatever model it drew last, so
     *  such stacks degrade to the flat icon. */
    private static final String MAID_NBT_YSM_KEY = "YsmModelId";
    /** The stored-maid slab's save tag: TLM keeps the full
     *  {@code maid.saveWithoutId} payload in the item's root NBT under
     *  {@code MaidInfo} (no data-component system on 1.20.1). */
    private static final String MAID_INFO_TAG_KEY = "MaidInfo";
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
     *  proves the mod is loaded — no isModLoaded round-trip. Package-private:
     *  the renderItem3D chain's maid gate. */
    static boolean isMaidSlabItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return MAID_MOD_ID.equals(id.getNamespace())
                && (MAID_SLAB_INIT_PATH.equals(id.getPath()) || MAID_SLAB_HAS_MAID_PATH.equals(id.getPath()));
    }

    /** 3D body of the maid slab preview (see the block comment at
     *  {@link #MAID_MOD_ID} for the shape). Package-private: the renderItem3D
     *  chain's maid branch. */
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
     *  from the item's root NBT ({@code MaidInfo} compound). Null when the
     *  tag is absent or empty. */
    private static CompoundTag storedMaidTag(ItemStack stack) {
        if (!stack.hasTag()) {
            return null;
        }
        CompoundTag tag = stack.getTag().getCompound(MAID_INFO_TAG_KEY);
        return tag.isEmpty() ? null : tag;
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
        if (!rolled.isPresent()) {
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
     *  items are {@code builtin/entity} BEWLR items whose 2D/3D generic reroute
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
     *  <p>Storage, per era: 1.20.1 keeps the garage kit's full entity save
     *  payload in the root NBT under {@code EntityInfo} ({@code id} +
     *  {@code ModelId}, TLM's {@code ItemGarageKit.getMaidData}) with a bare
     *  stack falling back to its default maid (hakurei reimu); the chair keeps
     *  {@code ModelId} on the root NBT, defaulting to {@code cushion}. See
     *  {@link #renderTlmGarageKitPreview} / {@link #renderTlmChairPreview}.</p> */
    private static final String MAID_GARAGE_KIT_PATH = "garage_kit";
    private static final String MAID_CHAIR_PATH = "chair";
    private static final String MAID_GARAGE_KIT_TAG_KEY = "EntityInfo";
    private static final String MAID_ENTITY_ID_KEY = "id";
    private static final String MAID_DEFAULT_TYPE_ID = MAID_MOD_ID + ":maid";
    private static final String MAID_DEFAULT_MODEL_ID = "touhou_little_maid:hakurei_reimu";
    private static final ResourceLocation MAID_CHAIR_TYPE_ID =
            new ResourceLocation(MAID_MOD_ID, "chair");
    private static final String MAID_CHAIR_DEFAULT_MODEL_ID = "touhou_little_maid:cushion";
    /** The chair's entity save key equals the item NBT key on this line
     *  ({@code EntityChair.MODEL_ID_TAG}). */
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
     *  renderItem3D chain's garage kit gate. */
    static boolean isTlmGarageKitItem(ItemStack stack) {
        return maidItemWithPath(stack, MAID_GARAGE_KIT_PATH);
    }

    /** Namespace + path gate for the chair item. Package-private: the
     *  renderItem3D chain's chair gate. */
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

    /** 3D body of the garage kit preview (see the block comment at
     *  {@link #MAID_GARAGE_KIT_PATH} for the shape). Maid content renders
     *  through the slab pipeline (per-model-id bounds, inspect button armed);
     *  any other stored entity goes through the generic entity preview with
     *  the whole save payload as its spawn tag. */
    static void renderTlmGarageKitPreview(GuiGraphics gg, ItemStack item, LivingEntity player,
                                          int cx, int cy, Quat rotation, float scale) {
        CompoundTag entityInfo = garageKitEntityInfo(item);
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
            // generic path applies the full save payload and renders it.
            renderEntityPreviewForType(gg, item, player, cx, cy, rotation, scale, type, entityInfo);
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

    /** The garage kit's entity save payload: the {@code EntityInfo} root-NBT
     *  compound when present, else TLM's own default-maid shape
     *  ({@code ItemGarageKit.getDefaultData} — maid / hakurei reimu). */
    private static CompoundTag garageKitEntityInfo(ItemStack stack) {
        CompoundTag tag = stack.hasTag() ? stack.getTag().getCompound(MAID_GARAGE_KIT_TAG_KEY) : new CompoundTag();
        if (!tag.isEmpty()) {
            return tag;
        }
        CompoundTag defaults = new CompoundTag();
        defaults.putString(MAID_ENTITY_ID_KEY, MAID_DEFAULT_TYPE_ID);
        defaults.putString(MAID_NBT_MODEL_KEY, MAID_DEFAULT_MODEL_ID);
        return defaults;
    }

    /** 3D body of the chair preview (see the block comment at
     *  {@link #MAID_CHAIR_PATH} for the shape). */
    static void renderTlmChairPreview(GuiGraphics gg, ItemStack item, LivingEntity player,
                                      int cx, int cy, Quat rotation, float scale) {
        String modelId = item.hasTag() && item.getTag().contains(MAID_NBT_MODEL_KEY)
                ? item.getTag().getString(MAID_NBT_MODEL_KEY)
                : MAID_CHAIR_DEFAULT_MODEL_ID;
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

    /** Loads the chair's model id onto the shared instance once per (stack,
     *  model id) pair — {@code Entity.load} with the entity's own save key.
     *  The parked pose is restored after the load for the same reason as the
     *  maid path. */
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

    // ===== 检视页方块特效（2026-11-20）：BE 动画 + 粒子 =====
    // 点击检视页（CsLookItemScreen）的 G0 默认路径增强层，补上烘焙模型画不出来的
    // 两块内容。只在检视页（begin/end 包裹）生效；拖拽页/网格/终端机路径不设
    // active 标志，行为逐帧不变。
    //   1. 方块动画：EntityBlock 建 BE（默认态）+ client ticker 每 50ms 推进
    //      （与游戏 tick 同拍），BER 每帧经 identity pose 渲染——预览变换整体在
    //      modelViewStack 上，与物品 quads 同一空间，跟随拖拽旋转。
    //   2. 粒子：假世界 addParticle 全捕获（BE ticker 与 block.animateTick 的
    //      level.addParticle 都落进来），反射 ParticleEngine.makeParticle
    //      （按形状匹配，SRG 生产运行时免疫）造粒子实例——不进世界粒子引擎、
    //      不污染场景；四标准 ParticleRenderType 分组 tesselator 直绘，
    //      modelview 仍带预览变换 → 粒子随拖拽一起转。
    //   3. 包络：BER 顶点离线探测并入烘焙模型 raw 顶点 → fit/枢轴随真实绘制
    //      范围（附魔台的书浮出模型顶，必须并入包络）。探针 span 超限
    //      （信标光柱一类无界 BER）→ BER 整段跳过 + 一次性日志。
    //   4. 降级：任何 Throwable → 本会话禁用 + 一次性 warn；模型渲染不受影响。
    // 已知边界：BE 用 defaultBlockState（物品 NBT 变体如饰纹陶罐 sherds 不进
    // BE）；声音一律静音；BER 内部走全局 bufferSource 的模组（TACZ 同类）探针
    // 抓不到 → 走零顶点跳过分支回落模型自身包络。
    private static final int INSPECT_LIGHT = 15728880;
    private static final long INSPECT_TICK_NANOS = 50_000_000L;
    private static final float INSPECT_BER_SPAN_LIMIT = 3.5F;
    private static final BlockPos INSPECT_POS = BlockPos.ZERO;
    private static final ParticleRenderType[] INSPECT_PARTICLE_SHEETS = {
            ParticleRenderType.TERRAIN_SHEET, ParticleRenderType.PARTICLE_SHEET_OPAQUE,
            ParticleRenderType.PARTICLE_SHEET_LIT, ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT};
    private static final Scoreboard INSPECT_SCOREBOARD = new Scoreboard();
    private static boolean INSPECT_ACTIVE;
    private static boolean INSPECT_DISABLED;
    private static ItemStack INSPECT_ITEM = ItemStack.EMPTY;
    private static BlockState INSPECT_STATE;
    private static BlockEntity INSPECT_BE;
    private static BlockEntityTicker<BlockEntity> INSPECT_TICKER;
    /** Per-model verdict of {@link #computeInspectEffectBounds}: does the
     *  block entity renderer add visual mass the baked model lacks (models
     *  with no cache entry are treated as "no"). */
    private static final Map<BakedModel, Boolean> INSPECT_BER_ADDS_MASS = new HashMap<>();
    private static long INSPECT_LAST_TICK_NANOS;
    private static long INSPECT_TICK_COUNT;
    private static final RandomSource INSPECT_RANDOM = RandomSource.create();
    private static final Camera INSPECT_CAMERA = new Camera();
    private static boolean INSPECT_CAMERA_SET;
    private static final List<InspectLiveParticle> INSPECT_PARTICLES = new ArrayList<>();
    private static final List<InspectParticleSpawn> INSPECT_PENDING = new ArrayList<>();
    private static final Map<BakedModel, ModelBounds> INSPECT_EFFECT_BOUNDS = new HashMap<>();
    private static Method INSPECT_MAKE_PARTICLE;
    private static boolean INSPECT_MAKE_PARTICLE_SEARCHED;
    private static InspectEffectLevel INSPECT_LEVEL;

    /** 检视特效待生成粒子：捕获的 addParticle 调用，等下一拍落地。 */
    private record InspectParticleSpawn(ParticleOptions options, double x, double y, double z,
                                        double vx, double vy, double vz) {}

    /** 存活粒子槽：{@code Particle.removed}/{@code age} 是 protected（无公共
     *  读口），死亡判定用自计拍数 vs {@link Particle#getLifetime()}——与本会话
     *  20Hz 拍同频推进，语义等价；提前自毁的少数粒子按寿命收尾（幽灵帧上界 =
     *  剩余寿命）。 */
    private static final class InspectLiveParticle {
        final Particle particle;
        int ageTicks;

        InspectLiveParticle(Particle particle) {
            this.particle = particle;
        }
    }

    /** 检视页 begin（CsLookItemScreen 每帧包裹 3D 渲染调用）：换物品即重置会话。 */
    static void beginInspectEffects(ItemStack item) {
        INSPECT_ACTIVE = true;
        if (INSPECT_DISABLED || ItemStack.isSameItemSameTags(item, INSPECT_ITEM)) {
            return;
        }
        resetInspectSession(item);
    }

    /** 检视页 end：只撤 active 标志；会话保留（同一物品下帧复用 BE/粒子池）。 */
    static void endInspectEffects() {
        INSPECT_ACTIVE = false;
    }

    private static void resetInspectSession(ItemStack item) {
        INSPECT_ITEM = item;
        INSPECT_PENDING.clear();
        INSPECT_PARTICLES.clear();
        INSPECT_BE = null;
        INSPECT_TICKER = null;
        INSPECT_STATE = null;
        INSPECT_LAST_TICK_NANOS = 0L;
        INSPECT_EFFECT_BOUNDS.clear();
        INSPECT_BER_ADDS_MASS.clear();
        if (INSPECT_DISABLED || !(item.getItem() instanceof BlockItem blockItem)) {
            return;
        }
        InspectEffectLevel level = inspectLevel();
        if (level == null) {
            disableInspectOnce("client level unavailable");
            return;
        }
        BlockState state = blockItem.getBlock().defaultBlockState();
        INSPECT_STATE = state;
        level.attach(state, null);
        if (blockItem.getBlock() instanceof EntityBlock entityBlock) {
            try {
                BlockEntity be = entityBlock.newBlockEntity(INSPECT_POS, state);
                // Written board/sign content rides the item's BlockEntityTag
                // (Forge storage: tag.BlockEntityTag) — load it so text-
                // carrying BEs preview their item state instead of a blank
                // freshly-placed board.
                CompoundTag beTag = BlockItem.getBlockEntityData(item);
                if (beTag != null && !beTag.isEmpty()) {
                    be.load(beTag);
                }
                be.setLevel(level);
                be.clearRemoved();
                INSPECT_BE = be;
                level.attach(state, be);
                @SuppressWarnings({"unchecked", "rawtypes"})
                BlockEntityTicker<BlockEntity> ticker = (BlockEntityTicker<BlockEntity>)
                        entityBlock.getTicker(level, state, (BlockEntityType) be.getType());
                INSPECT_TICKER = ticker;
            } catch (Throwable t) {
                INSPECT_BE = null;
                level.attach(state, null);
                disableInspectOnce("block entity attach failed: " + t);
            }
        }
    }

    private static InspectEffectLevel inspectLevel() {
        if (INSPECT_LEVEL != null) {
            return INSPECT_LEVEL;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) {
            return null;
        }
        INSPECT_LEVEL = new InspectEffectLevel(mc);
        return INSPECT_LEVEL;
    }

    private static void disableInspectOnce(String reason) {
        INSPECT_DISABLED = true;
        LOGGER.warn("[csgobox] inspect block effects disabled for the session: {}", reason);
    }

    /** 检视特效包络：烘焙模型 raw 顶点 ∪ BER 探针顶点，经 gui 链映射，规则同
     *  {@link #modelBounds}。无 BE / 探针超限 / 探针失败 → {@link #NO_BOUNDS}
     *  回落 {@link #previewPivot}。按模型缓存——探针渲染每帧跑不起。 */
    private static ModelBounds inspectEffectBounds(Minecraft mc, ItemStack item, BakedModel renderModel) {
        if (!INSPECT_ACTIVE || INSPECT_DISABLED || INSPECT_BE == null) {
            return NO_BOUNDS;
        }
        ModelBounds cached = INSPECT_EFFECT_BOUNDS.get(renderModel);
        if (cached != null) {
            return cached;
        }
        ModelBounds bounds = computeInspectEffectBounds(mc, renderModel);
        INSPECT_EFFECT_BOUNDS.put(renderModel, bounds);
        return bounds;
    }

    private static ModelBounds computeInspectEffectBounds(Minecraft mc, BakedModel renderModel) {
        BlockEntityRenderer<BlockEntity> ber = mc.getBlockEntityRenderDispatcher().getRenderer(INSPECT_BE);
        if (ber == null) {
            return NO_BOUNDS;
        }
        FloatArrayList xs = new FloatArrayList();
        FloatArrayList ys = new FloatArrayList();
        FloatArrayList zs = new FloatArrayList();
        collectRawVertices(renderModel, xs, ys, zs);
        // 模型自身 raw 盒（block 单位，未过 gui 链）——双绘判据的对照盒。
        float modelMinX = Float.MAX_VALUE, modelMinY = Float.MAX_VALUE, modelMinZ = Float.MAX_VALUE;
        float modelMaxX = -Float.MAX_VALUE, modelMaxY = -Float.MAX_VALUE, modelMaxZ = -Float.MAX_VALUE;
        for (int i = 0; i < xs.size(); i++) {
            modelMinX = Math.min(modelMinX, xs.getFloat(i));
            modelMinY = Math.min(modelMinY, ys.getFloat(i));
            modelMinZ = Math.min(modelMinZ, zs.getFloat(i));
            modelMaxX = Math.max(modelMaxX, xs.getFloat(i));
            modelMaxY = Math.max(modelMaxY, ys.getFloat(i));
            modelMaxZ = Math.max(modelMaxZ, zs.getFloat(i));
        }
        REUSABLE_POSE_STACK.setIdentity();
        FIXED_CONTEXT_PROBE.reset();
        try {
            ber.render(INSPECT_BE, 0.0F, REUSABLE_POSE_STACK, FIXED_CONTEXT_PROBE,
                    INSPECT_LIGHT, OverlayTexture.NO_OVERLAY);
        } catch (Throwable t) {
            ERRORED_BE_TYPES.add(INSPECT_BE.getType());
            LOGGER.warn("[csgobox] inspect preview: block entity probe errored, blacklisting {}",
                    BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(INSPECT_BE.getType()), t);
            return NO_BOUNDS;
        }
        if (!FIXED_CONTEXT_PROBE.any) {
            // BER 不画东西 → 模型自身包络足矣（回落 previewPivot）。
            return NO_BOUNDS;
        }
        // 模型顶点经 gui 链映射（真渲染里 ItemRenderer 会做），BER 顶点保持
        // raw——真渲染走 identity renderStack，不吃 gui 链。两侧各在自己的
        // 真实落屏空间取盒再取并（整包过 gui 曾把黑板包络整体挪位）。
        Matrix4f gui = guiRenderMatrix(renderModel);
        Vector3f v = new Vector3f();
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (int i = 0; i < xs.size(); i++) {
            v.set(xs.getFloat(i), ys.getFloat(i), zs.getFloat(i));
            v.mulPosition(gui);
            minX = Math.min(minX, v.x());
            minY = Math.min(minY, v.y());
            minZ = Math.min(minZ, v.z());
            maxX = Math.max(maxX, v.x());
            maxY = Math.max(maxY, v.y());
            maxZ = Math.max(maxZ, v.z());
        }
        FloatArrayList bx = FIXED_CONTEXT_PROBE.vx;
        FloatArrayList by = FIXED_CONTEXT_PROBE.vy;
        FloatArrayList bz = FIXED_CONTEXT_PROBE.vz;
        float rawMinX = Float.MAX_VALUE, rawMinY = Float.MAX_VALUE, rawMinZ = Float.MAX_VALUE;
        float rawMaxX = -Float.MAX_VALUE, rawMaxY = -Float.MAX_VALUE, rawMaxZ = -Float.MAX_VALUE;
        for (int i = 0; i < bx.size(); i++) {
            rawMinX = Math.min(rawMinX, bx.getFloat(i));
            rawMinY = Math.min(rawMinY, by.getFloat(i));
            rawMinZ = Math.min(rawMinZ, bz.getFloat(i));
            rawMaxX = Math.max(rawMaxX, bx.getFloat(i));
            rawMaxY = Math.max(rawMaxY, by.getFloat(i));
            rawMaxZ = Math.max(rawMaxZ, bz.getFloat(i));
        }
        // 双绘判据（2026-11-22 首测实测：Create 动力学族预览出现两根轴）：
        // BER 把方块模型经 SuperByteBuffer 绕自身轴自旋重画——AABB 不变，
        // raw 盒收在模型盒内，渲染它只会把同一几何画两遍；BER 视觉真正
        // 超出模型盒（附魔书悬浮、刷怪笼火焰）或模型侧无 quads（黑板）
        // 时才进预览。
        boolean berAddsMass;
        if (minX > maxX) {
            // 模型侧无 quads（空方块模型）：BER 即全部视觉（黑板）。
            berAddsMass = true;
        } else {
            berAddsMass = berExtendsBeyond(modelMinX, modelMaxX, rawMinX, rawMaxX)
                    || berExtendsBeyond(modelMinY, modelMaxY, rawMinY, rawMaxY)
                    || berExtendsBeyond(modelMinZ, modelMaxZ, rawMinZ, rawMaxZ);
        }
        INSPECT_BER_ADDS_MASS.put(renderModel, berAddsMass);
        if (minX > maxX) {
            // 模型侧无 quads（空方块模型）：包络 = BER raw 盒本身。
            minX = rawMinX;
            minY = rawMinY;
            minZ = rawMinZ;
            maxX = rawMaxX;
            maxY = rawMaxY;
            maxZ = rawMaxZ;
        } else {
            minX = Math.min(minX, rawMinX);
            minY = Math.min(minY, rawMinY);
            minZ = Math.min(minZ, rawMinZ);
            maxX = Math.max(maxX, rawMaxX);
            maxY = Math.max(maxY, rawMaxY);
            maxZ = Math.max(maxZ, rawMaxZ);
        }
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            return NO_BOUNDS;
        }
        float span = Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ));
        if (span <= 0.0F) {
            return NO_BOUNDS;
        }
        if (span > INSPECT_BER_SPAN_LIMIT) {
            // 信标光柱一类的无界 BER：整段跳过，模型自身包络继续。
            LOGGER.info("[csgobox] inspect preview: block entity visuals span {} blocks, skipping the BER",
                    String.format("%.2f", span));
            return NO_BOUNDS;
        }
        // 枢轴 = 双侧各自落屏空间并集盒中心（模型侧已过 gui 链、BER 侧 raw）。
        Vector3f center = new Vector3f((minX + maxX) * 0.5F,
                (minY + maxY) * 0.5F, (minZ + maxZ) * 0.5F);
        return ModelBounds.of(center, span);
    }

    /** BER raw 盒在单轴上是否超出模型 raw 盒跨度的 25%——自旋族绕自身轴
     *  转、AABB 不变则恒 false；外浮视觉（悬浮书、火焰、光柱）超出即 true。 */
    private static boolean berExtendsBeyond(float modelMin, float modelMax, float rawMin, float rawMax) {
        float span = modelMax - modelMin;
        if (span <= 1.0e-5F) {
            return rawMax - rawMin > 1.0e-3F;
        }
        float slack = span * 0.25F;
        return rawMin < modelMin - slack || rawMax > modelMax + slack;
    }

    /** 检视页 BER 渲染：identity pose 进主 batch，与物品 quads 一次 endBatch
     *  一起落屏；错误按 BE 类型拉黑（与蓝图路径同一张黑名单）。 */
    private static void renderInspectBlockEntity(Minecraft mc, PoseStack renderStack,
                                                 MultiBufferSource.BufferSource bufferSource,
                                                 BakedModel renderModel) {
        BlockEntity be = INSPECT_BE;
        // 只在 BER 探针判定"视觉超出模型盒"时才进主 batch（双绘判据，
        // 见 computeInspectEffectBounds）；Create 动力学族等自旋重画型
        // BER 在此被拦下，预览不再出现第二份方块模型。
        if (be == null || !Boolean.TRUE.equals(INSPECT_BER_ADDS_MASS.get(renderModel))
                || ERRORED_BE_TYPES.contains(be.getType())) {
            return;
        }
        BlockEntityRenderer<BlockEntity> ber = mc.getBlockEntityRenderDispatcher().getRenderer(be);
        if (ber == null) {
            return;
        }
        try {
            ber.render(be, 0.0F, renderStack, bufferSource, INSPECT_LIGHT, OverlayTexture.NO_OVERLAY);
        } catch (Throwable t) {
            ERRORED_BE_TYPES.add(be.getType());
            LOGGER.warn("[csgobox] inspect preview: block entity render errored, blacklisting {}",
                    BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType()), t);
        }
    }

    /** 检视页粒子：每 50ms 一拍推进（ticker → animateTick → 待生成落地 → 粒子
     *  tick），每帧按标准 ParticleRenderType 分组 tesselator 直绘。 */
    private static void renderInspectParticles(Minecraft mc) {
        if (INSPECT_STATE == null || INSPECT_DISABLED || INSPECT_LEVEL == null) {
            return;
        }
        float partial = inspectAdvanceTick();
        if (partial < 0.0F) {
            return;
        }
        Tesselator tesselator = Tesselator.getInstance();
        for (ParticleRenderType type : INSPECT_PARTICLE_SHEETS) {
            boolean any = false;
            for (InspectLiveParticle live : INSPECT_PARTICLES) {
                if (live.particle.getRenderType() == type) {
                    any = true;
                    break;
                }
            }
            if (!any) {
                continue;
            }
            try {
                type.begin(tesselator.getBuilder(), mc.getTextureManager());
                for (InspectLiveParticle live : INSPECT_PARTICLES) {
                    if (live.particle.getRenderType() != type) {
                        continue;
                    }
                    try {
                        live.particle.render(tesselator.getBuilder(), INSPECT_CAMERA, partial);
                    } catch (Throwable t) {
                        live.particle.remove();
                        live.ageTicks = Integer.MAX_VALUE - 1;
                    }
                }
            } catch (Throwable t) {
                disableInspectOnce("particle sheet render failed: " + t);
                return;
            } finally {
                try {
                    type.end(tesselator);
                } catch (Throwable ignored) {
                    // 缓冲状态已不可靠，下一帧重新 begin
                }
            }
        }
    }

    /** 20Hz 会话拍：BE client ticker（动画字段推进）→ block.animateTick（环境
     *  粒子）→ 待生成粒子落地 → 粒子 tick；返回本帧渲染 partial（拍内平滑插值）。
     *  会话禁用返回 -1。 */
    private static float inspectAdvanceTick() {
        if (INSPECT_DISABLED) {
            return -1.0F;
        }
        long now = Util.getNanos();
        if (INSPECT_LAST_TICK_NANOS == 0L) {
            INSPECT_LAST_TICK_NANOS = now;
        }
        if (now - INSPECT_LAST_TICK_NANOS >= INSPECT_TICK_NANOS) {
            INSPECT_LAST_TICK_NANOS = now;
            INSPECT_TICK_COUNT++;
            tickInspectSources();
            spawnInspectPending();
            advanceInspectParticles();
        }
        return Math.min(1.0F, (now - INSPECT_LAST_TICK_NANOS) / (float) INSPECT_TICK_NANOS);
    }

    private static void tickInspectSources() {
        if (INSPECT_TICKER != null && INSPECT_BE != null && INSPECT_LEVEL != null) {
            try {
                INSPECT_TICKER.tick(INSPECT_LEVEL, INSPECT_POS, INSPECT_STATE, INSPECT_BE);
            } catch (Throwable t) {
                INSPECT_TICKER = null;
                LOGGER.warn("[csgobox] inspect preview: block entity ticker errored, ticking off", t);
            }
        }
        if (INSPECT_STATE != null && INSPECT_LEVEL != null) {
            try {
                INSPECT_STATE.getBlock().animateTick(INSPECT_STATE, INSPECT_LEVEL, INSPECT_POS, INSPECT_RANDOM);
            } catch (Throwable t) {
                LOGGER.warn("[csgobox] inspect preview: animateTick errored, particle source off", t);
            }
        }
    }

    /** 捕获的 addParticle 落地：反射 makeParticle（按形状）直接造实例，不进
     *  世界粒子引擎、不污染场景。 */
    private static void spawnInspectPending() {
        if (INSPECT_PENDING.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Method makeParticle = inspectMakeParticleMethod();
        if (makeParticle == null) {
            INSPECT_PENDING.clear();
            return;
        }
        int failures = 0;
        for (InspectParticleSpawn spawn : INSPECT_PENDING) {
            try {
                Particle particle = (Particle) makeParticle.invoke(mc.particleEngine,
                        spawn.options(), spawn.x(), spawn.y(), spawn.z(),
                        spawn.vx(), spawn.vy(), spawn.vz());
                if (particle != null) {
                    INSPECT_PARTICLES.add(new InspectLiveParticle(particle));
                    if (INSPECT_PARTICLES.size() > 128) {
                        INSPECT_PARTICLES.remove(0);
                    }
                }
            } catch (Throwable t) {
                failures++;
            }
        }
        INSPECT_PENDING.clear();
        if (failures > 32) {
            disableInspectOnce("particle spawn kept failing: " + failures);
        }
    }

    /** {@code ParticleEngine.makeParticle} 按形状解析（ParticleOptions + 6 个
     *  double → Particle；方法名不参与匹配，SRG 生产运行时免疫）；null =
     *  粒子降级不出。 */
    private static Method inspectMakeParticleMethod() {
        if (INSPECT_MAKE_PARTICLE_SEARCHED) {
            return INSPECT_MAKE_PARTICLE;
        }
        INSPECT_MAKE_PARTICLE_SEARCHED = true;
        try {
            for (Method method : ParticleEngine.class.getDeclaredMethods()) {
                Class<?>[] params = method.getParameterTypes();
                if (params.length == 7 && params[0] == ParticleOptions.class
                        && params[1] == double.class && params[2] == double.class
                        && params[3] == double.class && params[4] == double.class
                        && params[5] == double.class && params[6] == double.class
                        && method.getReturnType() == Particle.class) {
                    method.setAccessible(true);
                    INSPECT_MAKE_PARTICLE = method;
                    return method;
                }
            }
        } catch (Throwable t) {
            LOGGER.warn("[csgobox] inspect preview: ParticleEngine.makeParticle unreachable", t);
        }
        LOGGER.warn("[csgobox] inspect preview: makeParticle not found by shape, particles degrade");
        return null;
    }

    private static void advanceInspectParticles() {
        if (INSPECT_PARTICLES.isEmpty()) {
            return;
        }
        for (Iterator<InspectLiveParticle> it = INSPECT_PARTICLES.iterator(); it.hasNext(); ) {
            InspectLiveParticle live = it.next();
            live.ageTicks++;
            try {
                live.particle.tick();
            } catch (Throwable t) {
                it.remove();
                continue;
            }
            int lifetime = live.particle.getLifetime();
            if (lifetime > 0 && live.ageTicks >= lifetime) {
                it.remove();
            }
        }
    }

    private static void captureInspectParticle(ParticleOptions options, double x, double y, double z,
                                               double vx, double vy, double vz) {
        if (INSPECT_PENDING.size() < 256) {
            INSPECT_PENDING.add(new InspectParticleSpawn(options, x, y, z, vx, vy, vz));
        }
    }

    /** 检视特效假世界：isClientSide 恒真（EntityBlock.getTicker 才会给出 client
     *  ticker）、addParticle 全捕获、声音与方块更新静默、getGameTime 走 20Hz
     *  会话时钟（驱动 gameTime 依赖的 BER 动画，如刷怪笼旋转）。 */
    private static final class InspectEffectLevel extends Level {
        private BlockState state = Blocks.AIR.defaultBlockState();
        private BlockEntity blockEntity;
        private final Minecraft mc;

        InspectEffectLevel(Minecraft mc) {
            super(new ClientLevel.ClientLevelData(Difficulty.PEACEFUL, false, false),
                    Level.OVERWORLD, mc.level.registryAccess(), mc.level.dimensionTypeRegistration(),
                    () -> InactiveProfiler.INSTANCE, true, false, 0L, 8);
            this.mc = mc;
            ensureInspectCamera();
        }

        void attach(BlockState state, BlockEntity blockEntity) {
            this.state = state;
            this.blockEntity = blockEntity;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return INSPECT_POS.equals(pos) ? this.state : Blocks.AIR.defaultBlockState();
        }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return INSPECT_POS.equals(pos) ? this.blockEntity : null;
        }

        // BlockAndTintGetter 桩集与 PrefabStructureLevel 同款：全亮、无方向
        // 阴影、不染色、光引擎不触达。
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

        @Override
        public void addParticle(ParticleOptions options, double x, double y, double z,
                                double vx, double vy, double vz) {
            captureInspectParticle(options, x, y, z, vx, vy, vz);
        }

        @Override
        public void addParticle(ParticleOptions options, boolean force, double x, double y, double z,
                                double vx, double vy, double vz) {
            captureInspectParticle(options, x, y, z, vx, vy, vz);
        }

        @Override
        public void addAlwaysVisibleParticle(ParticleOptions options, double x, double y, double z,
                                             double vx, double vy, double vz) {
            captureInspectParticle(options, x, y, z, vx, vy, vz);
        }

        @Override
        public void addAlwaysVisibleParticle(ParticleOptions options, boolean force, double x, double y,
                                             double z, double vx, double vy, double vz) {
            captureInspectParticle(options, x, y, z, vx, vy, vz);
        }

        @Override
        public long getGameTime() {
            return INSPECT_TICK_COUNT;
        }

        @Override
        public void sendBlockUpdated(BlockPos pos, BlockState oldState, BlockState newState, int flags) {
        }

        @Override
        public void gameEvent(GameEvent event, Vec3 pos, GameEvent.Context context) {
        }

        @Override
        public void levelEvent(Player player, int eventId, BlockPos pos, int data) {
        }

        @Override
        public ChunkSource getChunkSource() {
            return null;
        }

        @Override
        public LevelTickAccess<Block> getBlockTicks() {
            return BlackholeTickAccess.emptyLevelList();
        }

        @Override
        public LevelTickAccess<Fluid> getFluidTicks() {
            return BlackholeTickAccess.emptyLevelList();
        }

        @Override
        public java.util.List<? extends Player> players() {
            return List.of();
        }

        @Override
        public net.minecraft.world.flag.FeatureFlagSet enabledFeatures() {
            return net.minecraft.world.flag.FeatureFlagSet.of();
        }

        @Override
        public Holder<net.minecraft.world.level.biome.Biome> getUncachedNoiseBiome(int x, int y, int z) {
            return this.mc.level.getUncachedNoiseBiome(x, y, z);
        }

        @Override
        public void playSeededSound(Player player, double x, double y, double z,
                                    Holder<SoundEvent> sound, SoundSource source, float volume, float pitch, long seed) {
        }

        @Override
        public void playSeededSound(Player player, Entity entity, Holder<SoundEvent> sound,
                                    SoundSource source, float volume, float pitch, long seed) {
        }

        @Override
        public void playSound(Entity source, BlockPos pos, SoundEvent sound, SoundSource src, float volume, float pitch) {
        }

        @Override
        public void playSound(Player player, BlockPos pos, SoundEvent sound, SoundSource source, float volume, float pitch) {
        }

        @Override
        public void playSound(Player player, double x, double y, double z, SoundEvent sound,
                              SoundSource source, float volume, float pitch) {
        }

        @Override
        public void playSound(Player player, Entity entity, SoundEvent sound, SoundSource source, float volume, float pitch) {
        }

        @Override
        public void playLocalSound(BlockPos pos, SoundEvent sound, SoundSource source, float volume, float pitch, boolean loop) {
        }

        @Override
        public void playLocalSound(double x, double y, double z, SoundEvent sound, SoundSource source, float volume, float pitch, boolean loop) {
        }

        @Override
        public void globalLevelEvent(int eventId, BlockPos pos, int data) {
        }

        @Override
        public String gatherChunkSourceStats() {
            return "csgobox inspect preview";
        }

        @Override
        public Entity getEntity(int id) {
            return null;
        }

        @Override
        public MapItemSavedData getMapData(String mapId) {
            return null;
        }

        @Override
        public void setMapData(String mapId, MapItemSavedData data) {
        }

        @Override
        public int getFreeMapId() {
            return 0;
        }

        @Override
        public void destroyBlockProgress(int breakerId, BlockPos pos, int progress) {
        }

        @Override
        public Scoreboard getScoreboard() {
            return INSPECT_SCOREBOARD;
        }

        @Override
        public RecipeManager getRecipeManager() {
            return null;
        }

        @Override
        protected LevelEntityGetter<Entity> getEntities() {
            return INSPECT_EMPTY_ENTITIES;
        }
    }

    private static final LevelEntityGetter<Entity> INSPECT_EMPTY_ENTITIES = new LevelEntityGetter<>() {
        @Override
        public Entity get(int id) {
            return null;
        }

        @Override
        public Entity get(UUID uuid) {
            return null;
        }

        @Override
        public Iterable<Entity> getAll() {
            return List.of();
        }

        @Override
        public <U extends Entity> void get(EntityTypeTest<Entity, U> type, AbortableIterationConsumer<U> consumer) {
        }

        @Override
        public void get(AABB box, java.util.function.Consumer<Entity> consumer) {
        }

        @Override
        public <U extends Entity> void get(EntityTypeTest<Entity, U> type, AABB box,
                                           AbortableIterationConsumer<U> consumer) {
        }
    };

    /** {@code Camera.setPosition(Vec3)} 按形状解析一次（SRG 免疫）：把预览相机
     *  钉在世界原点，粒子顶点即块坐标；失败回落构造器默认（同为 Vec3.ZERO）。 */
    private static void ensureInspectCamera() {
        if (INSPECT_CAMERA_SET) {
            return;
        }
        INSPECT_CAMERA_SET = true;
        try {
            for (Method method : Camera.class.getDeclaredMethods()) {
                Class<?>[] params = method.getParameterTypes();
                if (params.length == 1 && params[0] == Vec3.class) {
                    method.setAccessible(true);
                    method.invoke(INSPECT_CAMERA, Vec3.ZERO);
                    return;
                }
            }
        } catch (Throwable ignored) {
            // 构造器默认即 Vec3.ZERO，失败不致命
        }
    }
}
