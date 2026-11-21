package com.reclizer.csgobox.fabric_1_21_1.utils;

import com.mojang.blaze3d.platform.GlConst;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
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
import net.minecraft.client.gui.screens.Screen;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
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
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.fabricmc.loader.api.FabricLoader;
import org.joml.Matrix4fStack;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.slf4j.Logger;

import it.unimi.dsi.fastutil.floats.FloatArrayList;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Single per-platform adaptation point for animation rendering primitives.
 * Screens and logic helpers (IconListTools / GuiItemMove) must call ONLY
 * through this class; version-varying render API lives here and nowhere else.
 * era: legacy
 */
public final class AnimRenderOps {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** TACZ (TaCZ: Refabricated on this platform) is an optional dependency;
     *  every TACZ class reference below must be reachable only after a
     *  FabricLoader gate, or clients without the mod crash with
     *  NoClassDefFoundError (see the 2.0.1 TerminalScreen rendering crash). */
    /** Package-private so the TACZ preview strategy gates on the same id. */
    static final String TACZ_MOD_ID = "tacz";
    /** Superb Warfare (卓越前线, Superb Warfare ReFabricated on this platform)
     *  item namespace. Unlike TACZ no loader gate is needed: the 3D branch
     *  only matches a registered item's namespace, and such an item cannot
     *  exist unless the mod is loaded. No Superb Warfare class is referenced. */
    private static final String SUPERB_WARFARE_MOD_ID = "superbwarfare";
    /** Set once the first supports3D() call has logged the render environment. */
    private static boolean logged3DEnvironment;
    private static final PoseStack REUSABLE_POSE_STACK = new PoseStack();
    /** Scratch quaternion for the drag rotation: {@code mulPose} copies the
     *  value immediately, so a single static instance is safe on the render
     *  thread and avoids one allocation per 3D item per frame. */
    private static final Quaternionf SCRATCH_QUAT = new Quaternionf();
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
     *  fills, so long guns read too small at the shared rule (user-tuned
     *  1.3x). Only applied when the probe produced real bounds. */
    private static final float SBW_FIT_BOOST = 1.3F;
    /** Cached model-space centre of each TACZ gun's default-pose bounding
     *  box (block units). Gun displays are singletons per gun id and are
     *  replaced on resource reload, so a WeakHashMap lets old models go. */
    private static final Map<BedrockGunModel, Vector3f> GUN_MODEL_CENTERS = new WeakHashMap<>();
    /** Cached largest-axis span (block units) of each TACZ gun model, keyed
     *  and lifecycle-managed like {@link #GUN_MODEL_CENTERS}. */
    private static final Map<BedrockGunModel, Float> GUN_MODEL_SPANS = new WeakHashMap<>();
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
     *  Mod-specific overrides are ordered strategies in
     *  {@link ItemPreviewStrategies}; unmatched items take the flat icon —
     *  this platform's 2D pass has no custom-renderer reroute, so
     *  builtin/entity items keep the flat grid cell. */
    public static void renderItem2D(LivingEntity entity, GuiGraphics gg, ItemStack stack, float x, float y, float scale) {
        // Mod-specific overrides first: the first matching strategy owns the
        // cell (TACZ suppress while a display is loading, else the flat icon).
        for (ItemPreviewStrategies.ItemPreviewStrategy strategy : ItemPreviewStrategies.ALL) {
            if (strategy.matches(stack) && strategy.render2D(entity, gg, stack, x, y, scale)) {
                return;
            }
        }
        renderItem2DFlat(entity, gg, stack, x, y, scale);
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
        // (TACZ gun model, SBW FIXED-context gun, SBP shell rest pose, spawn
        // eggs, armor stands, summon containers). Unmatched items fall
        // through to the vanilla baked-model path below; see
        // ItemPreviewStrategies for the chain and the TACZ class-loading
        // gate discipline.
        for (ItemPreviewStrategies.ItemPreviewStrategy strategy : ItemPreviewStrategies.ALL) {
            if (strategy.matches(item)) {
                strategy.render3D(gg, item, player, cx, cy, rotation, scale);
                return;
            }
        }
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
    private static float fitScaleFor(float modelSpan, float scale) {
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
     *  space the geometry is drawn in. Three steps IS the complete chain
     *  here: vanilla 1.21.1 has no right_rotation step or JSON key
     *  (bytecode-checked), while the NeoForge sibling module appends one via
     *  its own patch — do not "sync" either matrix to the other. */
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
     *  touched, so unlike the TACZ gate there is nothing to gate against.
     *  Package-private: the SBW gun preview strategy gates on it. */
    static boolean isSuperbWarfareItem(ItemStack stack) {
        return !stack.isEmpty() && SUPERB_WARFARE_MOD_ID.equals(
                BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace());
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
     *  Backpacks pocket-face flip; null = none).
     *  The ReFabricated port's model wiring is not separately verified: for
     *  plain (non-separate-transforms) items FIXED renders like GUI, so the
     *  worst case on that build is the unchanged flat-icon look.
     *  Package-private: the SBW gun and SBP shell preview strategies' 3D
     *  body, and the generic custom-renderer branch of renderItem3D. */
    static void renderFixedContextModel3D(GuiGraphics gg, ItemStack item, LivingEntity player,
                                          int cx, int cy, Quat rotation, float scale,
                                          Quaternionf restPose) {
        BakedModel model = Minecraft.getInstance().getItemRenderer().getModel(item, player.level(), player, 0);
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
            renderItem2D(player, gg, item, cx, cy, scale);
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
        // Auto-fit: gun models routinely span 2-3 blocks; shrink so the
        // largest axis stays inside the preview square (normal guns keep
        // their tuned look via the same PREVIEW_FILL rule as vanilla items).
        float fitScale = fitScaleFor(gunModelSpan(gunModel), scale);
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
                    !isShaderModActive(), FabricLoader.getInstance().isModLoaded("iris"),
                    FabricLoader.getInstance().isModLoaded("oculus"), FabricLoader.getInstance().isModLoaded("modernui"));
        }
        return !isShaderModActive();
    }

    /** True when Iris (NeoForge/Fabric) or Oculus (Forge) is installed: their
     *  shader packs intercept/ignore custom GUI 3D paths, so 3D previews
     *  degrade to 2D for correctness (probabilities/gameplay unaffected).
     *  Modern UI is intentionally excluded — see supports3D(). */
    private static boolean isShaderModActive() {
        return FabricLoader.getInstance().isModLoaded("iris") || FabricLoader.getInstance().isModLoaded("oculus");
    }

    // ============= Generic entity previews (spawn eggs / armor / summon cards / same-id entities) =============

    /** Item ids whose same-named entity must never become the 3D preview:
     *  escape hatch for convention collisions (L4 finds one, add its
     *  {@code namespace:path} item id). Empty today. */
    private static final Set<String> ENTITY_CONVENTION_DENYLIST = Set.of();

    /** Shared client-side entities backing entity-based 3D previews (spawn-egg
     *  mobs, the armor stand, summon cards, same-id entities), keyed by
     *  entity type id string. The renderer binds animation state to the
     *  entity instance, so the same instance must be reused across frames;
     *  entity types are registry constants, and a resource reload swaps
     *  renderer state harmlessly (the entity is not in the world). */
    // Strong map on purpose: a WeakHashMap keyed by the freshly-built registry-id
    // string drops every entry on the next GC, and the next frame then silently
    // recreates a fresh entity (default pose = static), so the inspect animation
    // drove an instance that was no longer the rendered one. Bounded by the
    // number of entity types ever previewed; stale-level entries are replaced on
    // first use after a world change (see entityPreviewInstance).
    private static final Map<String, Entity> ENTITY_PREVIEW_INSTANCES = new HashMap<>();

    /** Probe-measured centre + span of each preview entity type's rendered
     *  geometry (block units), keyed by EntityType — registry constants live
     *  for the whole session, and a NO_CENTER sentinel suppresses re-probing
     *  renderers that emit nothing usable. */
    private static final Map<EntityType<?>, Object> ENTITY_PREVIEW_BOUNDS = new WeakHashMap<>();

    /** Probe-measured bounds of each armor piece's preview on the shared
     *  armor stand, keyed by the item's registry id — the stand's entity type
     *  is constant but its worn model varies per piece, so the entity-type
     *  key cannot be reused. */
    private static final Map<String, Object> ARMOR_PREVIEW_BOUNDS = new HashMap<>();

    /** Flat-sprite verdict per baked item model: true when the model's raw
     *  geometry is a 1/16-thick slab extruded along Z — the generated
     *  {@code item/generated} sprite shape at any layer count — versus real
     *  3D geometry that spans beyond the slab. 2026-11-14: the earlier
     *  "zero-thickness" and "one shared normal axis" tests never fired (the
     *  generator bakes an extruded silhouette with edge faces); see
     *  {@code measureFlatSprite}. */
    private static final Map<BakedModel, Boolean> FLAT_ITEM_MODELS = new WeakHashMap<>();

    /** True when the model's raw geometry is a flat plane; custom renderers
     *  (no measurable quads) always answer false so they keep their
     *  dedicated handling. */
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
     *  full/served state. The original model is returned
     *  unchanged when the item is not block-backed, its model is not flat,
     *  or the block has no measurable geometry. */
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
     *  chicken vs. the chicken entity — the food item is the truth), and the
     *  denylist overrides everything. Null when the convention does not
     *  apply. */
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
     *  string that resolves to a registered entity (the Superb Warfare
     *  summon-crate convention; SBW's own crates are previewed by the
     *  namespace-gated logic on the other platforms, so this stays generic
     *  for anything reusing the convention under any namespace). Type-only —
     *  the block entity tag is deliberately not loaded onto the preview
     *  entity (block-entity fields are not entity save data). Null when the
     *  convention does not apply. */
    static EntityType<?> summonContainerEntityType(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        try {
            CompoundTag tag = summonContainerTag(stack);
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

    /** The summon-container NBT: the modern
     *  {@code minecraft:block_entity_data} component. */
    private static CompoundTag summonContainerTag(ItemStack stack) {
        var customData = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        return customData == null ? null : customData.copyTag();
    }

    /** Spawn-egg entity data ({@code minecraft:entity_data}); applied onto
     *  the shared preview instance every frame so variants (sheep colour,
     *  villager profession) render like the spawned mob. Package-private:
     *  the spawn-egg preview strategy's payload fetch. */
    static CustomData entityDataOf(ItemStack stack) {
        try {
            return stack.getOrDefault(DataComponents.ENTITY_DATA, CustomData.EMPTY);
        } catch (Throwable t) {
            return CustomData.EMPTY;
        }
    }

    /** Flat-body 2D draw for degrade paths: renders the item model itself
     *  (not {@link #renderItem2D}, whose custom-renderer branch would
     *  re-delegate here and loop for builtin/entity models). */
    static void renderItem2DFlat(LivingEntity entity, GuiGraphics gg, ItemStack stack, float x, float y, float scale) {
        BakedModel model = Minecraft.getInstance().getItemRenderer().getModel(stack, entity.level(), entity, 0);
        PoseStack pose = gg.pose();
        pose.pushPose();
        pose.translate(x, y, 2F);
        pose.translate(8.0F * scale, 8.0F * scale, 0.0F);
        // Depth restored (v1_21_1 renderItem2DFlat carries the full note): a
        // Z scale of 0 collapsed every face onto one depth plane, so hollow
        // open-front models baked their backplate over the front shelving.
        // Z=1 keeps a thin slab around the z=2 layer — card fills (z=0) stay
        // under the icon, big 3D previews (z=100+) stay over it.
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

    /** The parked client-side preview entity for an entity type, or null on
     *  failure. Cached per entity type: the model resources are heavy and
     *  the renderer animates against the entity, so a per-frame allocation
     *  would reload/replay every frame. Not added to the client world — it
     *  is rendered explicitly via the dispatcher. Parked at a fixed
     *  in-world position with a neutral yaw, and fully visible: LivingEntity
     *  renderers skip the whole model for invisible entities, which starved
     *  the probe to zero vertices on every vanilla mob — the shadow pass is
     *  suppressed per render instead (see {@link #renderPreviewEntity}). */
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

    /** Centre + largest-axis span (block units) of a preview entity's
     *  rendered geometry, measured by probing one real entity render into
     *  the vertex recorder (entity renderers emit through the same
     *  VertexConsumer funnel as item renderers). Cached per entity type;
     *  a failed or empty probe keeps the raw preview scale. One-shot INFO
     *  log per entity type keeps runtime evidence for fresh preview
     *  entities. */
    private static ModelBounds entityPreviewBounds(Entity entity) {
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
            bounds = entityProbeBoundsFromVertices();
        }
        return bounds;
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
     *  axis for the auto-fit. */
    private static ModelBounds entityProbeBoundsFromVertices() {
        return new ModelBounds(
                new Vector3f((FIXED_CONTEXT_PROBE.minX + FIXED_CONTEXT_PROBE.maxX) * 0.5F,
                        (FIXED_CONTEXT_PROBE.minY + FIXED_CONTEXT_PROBE.maxY) * 0.5F,
                        (FIXED_CONTEXT_PROBE.minZ + FIXED_CONTEXT_PROBE.maxZ) * 0.5F),
                entityProbeHub(),
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

    /** Per-axis median of a coordinate sample (mean of the two middle values
     *  on even counts). The fallback drag hub when an entity probe yields no
     *  reconstructable cube (see {@link #entityProbeHub}); item models use
     *  the raw envelope-box centre instead (hollow shells drag the median
     *  onto a wall). */
    private static float median(FloatArrayList values) {
        float[] sorted = values.toFloatArray();
        java.util.Arrays.sort(sorted);
        int n = sorted.length;
        return (n & 1) == 1 ? sorted[n / 2] : (sorted[n / 2 - 1] + sorted[n / 2]) * 0.5F;
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

    /** The real 3D entity draw: same pose convention as every other 3D
     *  branch (cell top-left origin, drag quaternion, centre pivot), scaled
     *  by the probe-measured bounds so oversized entities stay inside the
     *  preview square, with the shared {@link #SBW_FIT_BOOST} the other
     *  entity previews use (the caller guarantees real bounds here).
     *  Returns false when the render throws, so the caller can degrade to
     *  the flat icon. */
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
     *  summon cards, same-id entities): park a shared client entity, probe
     *  its geometry, draw, and degrade to the item's flat icon when the
     *  entity produces nothing usable or the render fails. */
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
     *  never change the largest axis. */
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

    /** Armor preview on the shared armor stand: equip the piece (its
     *  {@code ArmorItem} slot), probe with it worn (bounds cached per item
     *  id), draw, and degrade to the flat icon. GUI never draws the worn
     *  model — it lives in the humanoid armor layers, which the stand's
     *  renderer carries; pieces whose mod registers no armor-stand layer
     *  render nothing and degrade via the empty probe. Animal armor (wolf/
     *  horse) uses its own renderer and is not covered. Package-private: the
     *  armor preview strategy's 3D body. */
    static void renderArmorStandPreview(GuiGraphics gg, ItemStack stack, LivingEntity player,
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
}
