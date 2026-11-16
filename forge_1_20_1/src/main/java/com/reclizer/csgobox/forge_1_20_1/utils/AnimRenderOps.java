package com.reclizer.csgobox.forge_1_20_1.utils;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
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
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.SpellcasterIllager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.fml.ModList;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.slf4j.Logger;

import it.unimi.dsi.fastutil.floats.FloatArrayList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;

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
    private static final String TACZ_MOD_ID = "tacz";
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
    /** Cached model-space centre of each TACZ gun's default-pose bounding
     *  box (block units). Gun displays are singletons per gun id and are
     *  replaced on resource reload, so a WeakHashMap lets old models go. */
    private static final Map<BedrockGunModel, Vector3f> GUN_MODEL_CENTERS = new WeakHashMap<>();
    /** Cached largest-axis span (block units) of each TACZ gun model, keyed
     *  and lifecycle-managed like {@link #GUN_MODEL_CENTERS}. */
    private static final Map<BedrockGunModel, Float> GUN_MODEL_SPANS = new WeakHashMap<>();
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
    /** Cached centre + largest-axis span (block units, in the drag-rotation
     *  space) of each baked item model's geometry. Models are stable
     *  instances owned by the model manager and are replaced on resource
     *  reload, so a WeakHashMap lets old models go. */
    private record ModelBounds(Vector3f center, float span) {
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

    public static void renderBlurredBackground(GuiGraphics gg) {
        // 1.20.1 has no menu backdrop blur API (added in 1.20.2) — no-op.
    }

    /** 2D item icon with its top-left corner at (x, y), scaled (16px per
     *  block unit). Drawn through the shared buffer source and submitted
     *  immediately (endBatch) so an active scissor rect clips the icon. */
    /** 2D item icon centred at (x, y), scaled (16px per block unit).
     *  Custom-renderer models ({@code builtin/entity} — chest-like items
     *  drawn by a BEWLR) carry real 3D content that the flat icon path
     *  flattens to zero depth (the Z-scale-0 trick below), rendering
     *  nothing: those route to the FIXED-context 3D render instead, which
     *  is what an item frame shows. Skipped under shader mods (Iris/Oculus)
     *  — their GUI interception breaks custom 3D paths, and the flat path
     *  cannot draw them either. */
    public static void renderItem2D(LivingEntity entity, GuiGraphics gg, ItemStack stack, float x, float y, float scale) {
        // TACZ guns without a loaded display instance have no model to draw;
        // their custom renderer falls back to the missing-texture slot icon,
        // painting a magenta checkerboard across the card. Skip the draw so
        // the card frame alone shows until the display is available. The
        // isLoaded gate keeps instanceof IGun from ever executing (and thus
        // from class-loading TACZ) when the optional dependency is absent.
        if (ModList.get().isLoaded(TACZ_MOD_ID)
                && stack.getItem() instanceof IGun && TimelessAPI.getGunDisplay(stack).isEmpty()) {
            return;
        }
        // Superb Warfare summon crates: the 2D grids have no 3D pass, so the
        // vehicle icon is drawn here, exactly like SBW's own inventory
        // decorator. No icon texture -> fall through to the crate's own 3D
        // content below (same fallback as the 3D branch).
        if (isSuperbWarfareVehicleContainer(stack)) {
            CompoundTag vehicleTag = superWarfareContainerTag(stack);
            ResourceLocation vehicleIcon = vehicleTag == null ? null
                    : superbWarfareVehicleIcon(vehicleTag.getString("EntityType"));
            if (vehicleIcon != null) {
                renderVehicleIcon(gg, vehicleIcon, x, y, scale);
                return;
            }
        }
        BakedModel model = Minecraft.getInstance().getItemRenderer().getModel(stack, entity.level(), entity, 0);
        if (model.isCustomRenderer() && !isShaderModActive()) {
            renderFixedContextModel3D(gg, stack, entity, x, y, Quat.IDENTITY, scale);
            return;
        }
        renderItem2DFlat(entity, gg, stack, x, y, scale);
    }

    /** The flat icon body of {@link #renderItem2D} (never re-delegates to a
     *  3D path, so the FIXED-context failure fallback cannot loop). */
    private static void renderItem2DFlat(LivingEntity entity, GuiGraphics gg, ItemStack stack, float x, float y, float scale) {
        BakedModel model = Minecraft.getInstance().getItemRenderer().getModel(stack, entity.level(), entity, 0);
        PoseStack pose = gg.pose();
        pose.pushPose();
        pose.translate(x, y, 2F);
        pose.translate(8.0F * scale, 8.0F * scale, 0.0F);
        pose.scale(1.0F, -1.0F, 0F);
        pose.scale(16.0F * scale, 16.0F * scale, 0F);
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
        // Optional-dependency gate: without it the instanceof below would
        // class-load TACZ and crash clients that don't have it installed.
        if (ModList.get().isLoaded(TACZ_MOD_ID) && item.getItem() instanceof IGun) {
            Optional<GunDisplayInstance> display = TimelessAPI.getGunDisplay(item);
            // No loaded display -> nothing to draw (TACZ would fall back to the
            // missing-texture slot icon, painting a magenta checkerboard).
            if (display.isEmpty()) {
                return;
            }
            BedrockGunModel gunModel = display.get().getGunModel();
            if (gunModel == null) {
                return;
            }
            renderGunModel3D(gg, item, cx, cy, rotation, scale, gunModel, display.get());
            return;
        }
        // Superb Warfare summon containers (superbwarfare:container with a
        // tag.BlockEntityTag "EntityType") carry a vehicle, not a box item:
        // render the real vehicle 3D model via SBW's own entity renderer,
        // degrading to SBW's inventory icon / crate content. See
        // renderSuperbWarfareVehicle.
        if (isSuperbWarfareVehicleContainer(item)) {
            renderSuperbWarfareVehicle(gg, item, player, cx, cy, rotation, scale);
            return;
        }
        // Superb Warfare keeps its 3D gun model out of the GUI perspective
        // (separate-transforms routes gui -> flat icon); every other context
        // renders the real model — exactly what an item frame shows. See
        // renderFixedContextModel3D.
        if (isSuperbWarfareItem(item)) {
            renderFixedContextModel3D(gg, item, player, cx, cy, rotation, scale);
            return;
        }
        // Vanilla + modded spawn eggs: the egg's mob model via the shared
        // entity preview path (the egg's EntityTag variant is re-applied
        // every frame, so sheep colours / villager professions render like
        // the spawned mob).
        if (item.getItem() instanceof SpawnEggItem) {
            EntityType<?> eggType = null;
            try {
                // 1.20.1 getType reads the egg's EntityTag directly (null =
                // the egg item's default mob).
                eggType = ((SpawnEggItem) item.getItem()).getType(spawnEntityTag(item));
            } catch (Throwable ignored) {
                eggType = null;
            }
            if (eggType != null) {
                renderEntityPreviewForType(gg, item, player, cx, cy, rotation, scale, eggType, spawnEntityTag(item));
                return;
            }
        }
        // Armor pieces: humanoid slots on the shared armor stand — the worn
        // model lives in the humanoid armor layers, never in the GUI item
        // model; pieces whose mod registers no armor-stand layer render
        // nothing and degrade via the empty probe.
        if (item.getItem() instanceof ArmorItem) {
            renderArmorStandPreview(gg, item, player, cx, cy, rotation, scale);
            return;
        }
        // Generic summon-container convention: any item whose
        // tag.BlockEntityTag carries a resolvable EntityType string (the SBW
        // summon-crate shape, also matched for addon mods in their own
        // namespaces). The SBW-specific branch above always runs first and
        // keeps its tuned icon degrade chain.
        EntityType<?> summon = summonContainerEntityType(item);
        if (summon != null) {
            renderEntityPreviewForType(gg, item, player, cx, cy, rotation, scale, summon, null);
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
            renderFixedContextModel3D(gg, item, player, cx, cy, rotation, scale);
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
        // Same convention as the TACZ gun branch: translate(-centre) in block
        // units AFTER the 16px scale.
        Vector3f center = previewPivot(renderModel, renderModel != model);
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
        bufferSource.endBatch();
        RenderSystem.enableDepthTest();
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
    private static float fitScaleFor(float modelSpan, float scale) {
        if (modelSpan <= 1.0F || modelSpan <= 0.0F) {
            return scale;
        }
        return scale * PREVIEW_FILL / modelSpan;
    }

    /** Sentinel bounds for models without usable geometry (custom renderers
     *  or degenerate quads): unit span keeps the caller's scale untouched. */
    private static final ModelBounds NO_BOUNDS = new ModelBounds(new Vector3f(), 1.0F);

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
        ModelBounds bounds = new ModelBounds(center,
                Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ)));
        ITEM_MODEL_BOUNDS.put(model, bounds);
        return bounds;
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
    private static boolean isSuperbWarfareItem(ItemStack stack) {
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
    private static boolean isSuperbWarfareVehicleContainer(ItemStack stack) {
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
    private static CompoundTag superWarfareContainerTag(ItemStack stack) {
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
    private static void renderSuperbWarfareVehicle(GuiGraphics gg, ItemStack item, LivingEntity player,
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
        renderFixedContextModel3D(gg, item, player, cx, cy, rotation, scale);
    }

    /** The real 3D vehicle draw: same pose convention as every other 3D
     *  branch (cell top-left origin, drag quaternion, centre pivot), scaled
     *  by the probe-measured bounds so oversized vehicles stay inside the
     *  preview square, with the same {@link #SBW_FIT_BOOST} the gun branch
     *  uses (the caller guarantees real bounds here). Returns false when the
     *  render throws, so the caller can degrade to the icon path. */
    private static boolean renderEntityPreview(GuiGraphics gg, Entity vehicle, ModelBounds bounds,
                                           int cx, int cy, Quat rotation, float scale) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return false;
        float fitScale = fitScaleFor(bounds.span(), scale);
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
        // Rotate around the vehicle's geometric centre, not its origin (same
        // convention as every other 3D branch).
        Vector3f center = bounds.center();
        pose.translate(-center.x, -center.y, -center.z);
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
            EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
            PoseStack probeStack = new PoseStack();
            dispatcher.render(vehicle, 0.0D, 0.0D, 0.0D, 0.0F, 1.0F,
                    probeStack, FIXED_CONTEXT_PROBE, 15728880);
        } catch (Throwable t) {
            LOGGER.warn("Entity preview size probe failed; keeping the raw preview scale", t);
            rendered = false;
        }
        ModelBounds bounds = NO_BOUNDS;
        if (rendered && FIXED_CONTEXT_PROBE.any) {
            bounds = new ModelBounds(
                    new Vector3f(
                            (FIXED_CONTEXT_PROBE.minX + FIXED_CONTEXT_PROBE.maxX) * 0.5F,
                            (FIXED_CONTEXT_PROBE.minY + FIXED_CONTEXT_PROBE.maxY) * 0.5F,
                            (FIXED_CONTEXT_PROBE.minZ + FIXED_CONTEXT_PROBE.maxZ) * 0.5F),
                    Math.max(FIXED_CONTEXT_PROBE.maxX - FIXED_CONTEXT_PROBE.minX,
                            Math.max(FIXED_CONTEXT_PROBE.maxY - FIXED_CONTEXT_PROBE.minY,
                                    FIXED_CONTEXT_PROBE.maxZ - FIXED_CONTEXT_PROBE.minZ)));
        }
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
    private static ResourceLocation superbWarfareVehicleIcon(String entityId) {
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
    private static void renderVehicleIcon(GuiGraphics gg, ResourceLocation icon, float cx, float cy, float scale) {
        int size = Math.max(1, Math.round(16.0F * scale));
        blitTextured(gg, icon, Math.round(cx), Math.round(cy), size, size);
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
     *  2D icon. */
    private static void renderFixedContextModel3D(GuiGraphics gg, ItemStack item, LivingEntity player,
                                                  float cx, float cy, Quat rotation, float scale) {
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

    /** Offline probe sink for {@link #fixedContextBounds}: hands the probe
     *  render one vertex recorder regardless of render type and discards
     *  everything else, so the measurement draws nothing and touches no
     *  shared GPU batch. Vanilla-default VertexConsumer flows (foil glint,
     *  14-arg vertex) all funnel into these abstract methods. */
    private static final class FixedContextProbe implements MultiBufferSource, VertexConsumer {
        private float minX, minY, minZ, maxX, maxY, maxZ;
        private boolean any;

        void reset() {
            minX = minY = minZ = Float.MAX_VALUE;
            maxX = maxY = maxZ = -Float.MAX_VALUE;
            any = false;
        }

        @Override
        public VertexConsumer getBuffer(RenderType renderType) {
            return this;
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            minX = Math.min(minX, (float) x);
            minY = Math.min(minY, (float) y);
            minZ = Math.min(minZ, (float) z);
            maxX = Math.max(maxX, (float) x);
            maxY = Math.max(maxY, (float) y);
            maxZ = Math.max(maxZ, (float) z);
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
            bounds = new ModelBounds(
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
     *  draws the flat slot texture. */
    private static void renderGunModel3D(GuiGraphics gg, ItemStack item, int cx, int cy,
                                         Quat rotation, float scale,
                                         BedrockGunModel gunModel, GunDisplayInstance display) {
        // Auto-fit: gun models routinely span 2-3 blocks; shrink so the
        // largest axis stays inside the preview square (normal guns keep
        // their tuned look via the same PREVIEW_FILL rule as vanilla items).
        float fitScale = fitScaleFor(gunModelSpan(gunModel), scale);
        PoseStack pose = gg.pose();
        pose.pushPose();
        pose.translate(cx + 8.0F * scale, cy + 8.0F * scale, 100.0F);
        pose.scale(1.0F, -1.0F, 1.0F);
        SCRATCH_QUAT.set(rotation.x(), rotation.y(), rotation.z(), rotation.w()).normalize();
        pose.mulPose(SCRATCH_QUAT);
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
            return new ModelBounds(cached, GUN_MODEL_SPANS.getOrDefault(model, 1.0F));
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
            bounds = new ModelBounds(
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
    private static boolean isShaderModActive() {
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
    private static CompoundTag spawnEntityTag(ItemStack stack) {
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

    /** Armor preview on the shared armor stand: equip the piece (its
     *  {@code ArmorItem} slot), probe with it worn (bounds cached per item
     *  id), draw, and degrade to the flat icon. GUI never draws the worn
     *  model — it lives in the humanoid armor layers, which the stand's
     *  renderer carries; pieces whose mod registers no armor-stand layer
     *  render nothing and degrade via the empty probe. Animal armor (wolf/
     *  horse) uses its own renderer and is not covered. */
    private static void renderArmorStandPreview(GuiGraphics gg, ItemStack stack, LivingEntity player,
                                                int cx, int cy, Quat rotation, float scale) {
        Entity entity = entityPreviewInstance(EntityType.ARMOR_STAND, player.level());
        if (entity instanceof ArmorStand stand) {
            stand.setItemSlot(((ArmorItem) stack.getItem()).getEquipmentSlot(), stack);
            ModelBounds bounds = armorPreviewBounds(stack, stand);
            if (bounds != NO_BOUNDS && renderEntityPreview(gg, stand, bounds, cx, cy, rotation, scale)) {
                return;
            }
        }
        renderItem2DFlat(player, gg, stack, cx, cy, scale);
    }

    /** Bounds of an armor piece worn by the shared preview stand, cached per
     *  item id (the stand's entity type is constant but its worn model varies
     *  per piece). The stand must already be equipped when probing. */
    private static final Map<String, Object> ARMOR_PREVIEW_BOUNDS = new HashMap<>();

    private static ModelBounds armorPreviewBounds(ItemStack stack, ArmorStand stand) {
        String cacheKey = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        Object cached = ARMOR_PREVIEW_BOUNDS.get(cacheKey);
        if (cached instanceof ModelBounds bounds) {
            return bounds;
        }
        if (cached == NO_BOUNDS) {
            return NO_BOUNDS;
        }
        ModelBounds bounds = probeEntityBounds(stand);
        ARMOR_PREVIEW_BOUNDS.put(cacheKey, bounds == NO_BOUNDS ? NO_BOUNDS : bounds);
        return bounds;
    }

    /** Raw (uncached) probe of an entity's rendered geometry into the shared
     *  vertex recorder — the per-type cache lives in
     *  {@link #entityPreviewBounds} and the per-item armor cache in
     *  {@link #armorPreviewBounds}; both funnel here. */
    private static ModelBounds probeEntityBounds(Entity entity) {
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
            bounds = new ModelBounds(
                    new Vector3f(
                            (FIXED_CONTEXT_PROBE.minX + FIXED_CONTEXT_PROBE.maxX) * 0.5F,
                            (FIXED_CONTEXT_PROBE.minY + FIXED_CONTEXT_PROBE.maxY) * 0.5F,
                            (FIXED_CONTEXT_PROBE.minZ + FIXED_CONTEXT_PROBE.maxZ) * 0.5F),
                    Math.max(FIXED_CONTEXT_PROBE.maxX - FIXED_CONTEXT_PROBE.minX,
                            Math.max(FIXED_CONTEXT_PROBE.maxY - FIXED_CONTEXT_PROBE.minY,
                                    FIXED_CONTEXT_PROBE.maxZ - FIXED_CONTEXT_PROBE.minZ)));
        }
        return bounds;
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
}
