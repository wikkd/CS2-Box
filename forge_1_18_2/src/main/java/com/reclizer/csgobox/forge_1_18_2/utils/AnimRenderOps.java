package com.reclizer.csgobox.forge_1_18_2.utils;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.reclizer.csgobox.utils.Quat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import com.mojang.math.Matrix4f;
import com.mojang.math.Quaternion;
import com.mojang.math.Vector3f;
import com.mojang.math.Vector4f;
import org.slf4j.Logger;

import it.unimi.dsi.fastutil.floats.FloatArrayList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Single per-platform adaptation point for animation rendering primitives.
 * 1.18.2 edition: MC has no GuiGraphics — everything is PoseStack-based, and
 * the model-view stack is a PoseStack (pushPose/mulPoseMatrix) instead of the
 * 1.20.1+ Matrix4fStack. era: legacy
 *
 * <p>1.18.2 notes: menu blur does not exist yet ({@link #renderBlurredBackground}
 * is a no-op); TACZ gun 3D render branches are not ported (TACZ 1.1.4's client
 * model API differs from 1.1.8 — box stacks fall back to the vanilla path,
 * identical to every platform without TACZ); scissor uses RenderSystem with
 * explicit GUI-to-framebuffer coordinate conversion.</p>
 */
public final class AnimRenderOps extends net.minecraft.client.gui.GuiComponent {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Set once the first supports3D() call has logged the render environment. */
    private static boolean logged3DEnvironment;
    private static final PoseStack REUSABLE_POSE_STACK = new PoseStack();
    private static final Quaternion SCRATCH_QUAT = new Quaternion(0, 0, 0, 1);
    private static final Object NO_CENTER = new Object();
    private static final float PREVIEW_FILL = 0.82F;

    private record ModelBounds(Vector3f center, float span) {
    }

    private static final Map<BakedModel, Object> ITEM_MODEL_BOUNDS = new WeakHashMap<>();

    private AnimRenderOps() {
    }

    /** Immediate-mode blit: 1.18.2 has no texture-carrying blit — bind first. */
    public static void blitTextured(PoseStack pose, ResourceLocation tex, int x, int y, int w, int h) {
        RenderSystem.setShaderTexture(0, tex);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(770, 771, 1, 771);
        blit(pose, x, y, w, h, 0F, 0F, w, h, w, h);
    }

    public static void blitTextured(PoseStack pose, ResourceLocation tex, int x, int y, int w, int h, int texW, int texH) {
        RenderSystem.setShaderTexture(0, tex);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(770, 771, 1, 771);
        blit(pose, x, y, w, h, 0F, 0F, texW, texH, texW, texH);
    }

    public static void blitTextured(PoseStack pose, ResourceLocation tex, int x, int y, int w, int h,
                                    int u, int v, int uw, int vh, int texW, int texH, int tint) {
        RenderSystem.setShaderTexture(0, tex);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(770, 771, 1, 771);
        RenderSystem.setShaderColor(((tint >> 16) & 0xFF) / 255F,
                ((tint >> 8) & 0xFF) / 255F, (tint & 0xFF) / 255F, ((tint >> 24) & 0xFF) / 255F);
        blit(pose, x, y, w, h, u, v, uw, vh, texW, texH);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
    }

    /** 1.18.2 has no GuiGraphics.enableScissor — convert GUI coords to the
     *  framebuffer window ourselves (GUI y grows down, GL y grows up). */
    /** 1.18.2: GuiComponent.fillGradient is protected — the facade re-exposes
     *  it under a distinct name (inherited via the GuiComponent base class). */
    public static void fillGradientOp(PoseStack pose, int x0, int y0, int x1, int y1, int c0, int c1) {
        fillGradient(pose, x0, y0, x1, y1, c0, c1, 0);
    }

    public static void scissor(PoseStack pose, int x, int y, int w, int h) {
        Window window = Minecraft.getInstance().getWindow();
        double scale = window.getGuiScale();
        int fbY = window.getHeight() - (int) Math.round((y + h) * scale);
        RenderSystem.enableScissor(
                (int) Math.round(x * scale), fbY,
                (int) Math.round(w * scale), (int) Math.round(h * scale));
    }

    public static void scissorDisable(PoseStack pose) {
        RenderSystem.disableScissor();
    }

    public static void setBlendNormal(PoseStack pose) {
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(770, 771, 1, 771);
    }

    /** 1.18.2 renders in immediate mode — nothing to flush. */
    public static void flush(PoseStack pose) {
    }

    /** MC 1.18.2 has no menu blur (added in 1.20.2+) — silent no-op. */
    public static void renderBlurredBackground(PoseStack pose) {
    }

    /** 2D item icon centred at (x, y), scaled (16px per block unit). */
    public static void renderItem2D(LivingEntity entity, PoseStack pose, ItemStack stack, float x, float y, float scale) {
        BakedModel model = Minecraft.getInstance().getItemRenderer()
                .getModel(stack, entity.level, entity, 0);
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
        Minecraft.getInstance().getItemRenderer().render(stack,
                ItemTransforms.TransformType.GUI, false,
                renderStack, bufferSource, 15728880, OverlayTexture.NO_OVERLAY, model);
        bufferSource.endBatch();
        RenderSystem.enableDepthTest();
        if (flat) Lighting.setupFor3DItems();
        pose.popPose();
        modelViewStack.popPose();
        RenderSystem.applyModelViewMatrix();
    }

    /** 3D rotating item preview (drag-to-rotate). */
    public static void renderItem3D(PoseStack pose, ItemStack item, LivingEntity player,
                                    int cx, int cy, Quat rotation, float scale) {
        if (item == null || item.isEmpty() || player == null) return;
        // Shader-mod fallback (Iris/Oculus): degrade 3D previews to the 2D icon
        // (see docs/SHADER-COMPAT.md).
        if (!supports3D()) {
            renderItem2D(player, pose, item, cx, cy, scale);
            return;
        }
        // Vanilla + modded spawn eggs: the egg's mob model via the shared
        // entity preview path (the egg's EntityTag variant is re-applied
        // every frame, so sheep colours / villager professions render like
        // the spawned mob).
        if (item.getItem() instanceof SpawnEggItem) {
            EntityType<?> eggType = null;
            try {
                // 1.18.2 getType reads the egg's EntityTag directly (null =
                // the egg item's default mob).
                eggType = ((SpawnEggItem) item.getItem()).getType(spawnEntityTag(item));
            } catch (Throwable t) {
                eggType = null;
            }
            if (eggType != null) {
                renderEntityPreviewForType(pose, item, player, cx, cy, rotation, scale, eggType, spawnEntityTag(item));
                return;
            }
        }
        // Armor pieces: humanoid slots on the shared armor stand — the worn
        // model lives in the humanoid armor layers, never in the GUI item
        // model; pieces whose mod registers no armor-stand layer render
        // nothing and degrade to the flat icon.
        if (item.getItem() instanceof ArmorItem) {
            renderArmorStandPreview(pose, item, player, cx, cy, rotation, scale);
            return;
        }
        // Generic summon-container convention: any item whose
        // tag.BlockEntityTag carries a resolvable EntityType string (the SBW
        // summon-crate shape; SBW itself has no 1.18.2 build per the
        // mod-version matrix, but the convention stays generic).
        EntityType<?> summon = summonContainerEntityType(item);
        if (summon != null) {
            renderEntityPreviewForType(pose, item, player, cx, cy, rotation, scale, summon, null);
            return;
        }
        BakedModel model = Minecraft.getInstance().getItemRenderer()
                .getModel(item, player.level, player, 0);
        // Block-backed flat sprites (mod food plates and the like): the item
        // model is a generated plane while the real 3D form only exists on
        // the placed block, so the preview renders the block's model instead.
        BakedModel renderModel = blockBackedModel(item, model);
        // Same-id entity convention (generic, no per-mod code): a flat-sprite
        // item whose registry id is also an entity type draws the entity —
        // vanilla kin (minecarts, armor stand, end crystal, item frames,
        // painting, trident) and modded summonables. Food items never match:
        // raw food ids collide with their animal entities and the item is
        // the truth there.
        if (renderModel == model && isFlatSpriteModel(model)) {
            EntityType<?> convention = conventionEntityType(item);
            if (convention != null) {
                renderEntityPreviewForType(pose, item, player, cx, cy, rotation, scale, convention, null);
                return;
            }
        }
        float fitScale = fitScaleFor(itemModelSpan(renderModel), scale);
        pose.pushPose();
        pose.translate(cx, cy, 100.0F);
        pose.translate(8.0F * scale, 8.0F * scale, 0.0F);
        pose.scale(1.0F, -1.0F, 1.0F);
        // 1.18.2 Quaternion has no value accessors — normalize via the common
        // Quat components before constructing the Mojang instance.
        float qx = rotation.x(), qy = rotation.y(), qz = rotation.z(), qw = rotation.w();
        float qlen = (float) Math.sqrt(qx * qx + qy * qy + qz * qz + qw * qw);
        if (qlen > 1.0E-6F) {
            qx /= qlen; qy /= qlen; qz /= qlen; qw /= qlen;
        }
        pose.mulPose(new Quaternion(qx, qy, qz, qw));
        Lighting.setupForEntityInInventory();
        pose.scale(16.0F * fitScale, 16.0F * fitScale, 16.0F * fitScale);
        Vector3f center = previewPivot(renderModel, renderModel != model);
        if (center != null) {
            pose.translate(-center.x(), -center.y(), -center.z());
        }
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
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
        Minecraft.getInstance().getItemRenderer().render(item,
                ItemTransforms.TransformType.GUI, false,
                renderStack, bufferSource, 15728880, OverlayTexture.NO_OVERLAY, renderModel);
        bufferSource.endBatch();
        RenderSystem.enableDepthTest();
        if (flat) Lighting.setupFor3DItems();
        pose.popPose();
        modelViewStack.popPose();
        RenderSystem.applyModelViewMatrix();
    }

    private static Vector3f itemModelCenter(BakedModel model) {
        return modelBounds(model).center();
    }

    private static float itemModelSpan(BakedModel model) {
        return modelBounds(model).span();
    }

    /** Pivot policy of the default 3D path, by measured model kind:
     *  block-backed previews (mod food plates) pivot on the base-footprint
     *  cuboid ({@link #blockBackedCenter}), generated-sprite slabs pivot on
     *  the vanilla slab's own cuboid centre ({@link #spriteCenter}), and
     *  real 3D models keep the vertex median ({@link #itemModelCenter}).
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
        Vector3f center = new Vector3f(0.5F, 0.5F, 0.5F);
        transformPoint(guiRenderMatrix(model), center);
        return center;
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
        transformPoint(guiRenderMatrix(model), center);
        BLOCK_BACKED_CENTERS.put(model, center);
        return center;
    }

    private static float fitScaleFor(float modelSpan, float scale) {
        if (modelSpan <= 1.0F || modelSpan <= 0.0F) {
            return scale;
        }
        return scale * PREVIEW_FILL / modelSpan;
    }

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
            renderer.multiply(Matrix4f.createTranslateMatrix(
                    gui.translation.x(), gui.translation.y(), gui.translation.z()));
            renderer.multiply(Quaternion.fromXYZ(
                    (float) Math.toRadians(gui.rotation.x()),
                    (float) Math.toRadians(gui.rotation.y()),
                    (float) Math.toRadians(gui.rotation.z())));
            renderer.multiply(Matrix4f.createScaleMatrix(
                    gui.scale.x(), gui.scale.y(), gui.scale.z()));
        }
        renderer.multiply(Matrix4f.createTranslateMatrix(-0.5F, -0.5F, -0.5F));
        return renderer;
    }

    /** Drag pivot + largest-axis span of the model's rendered geometry. The
     *  pivot is the raw-vertex-extent box centre mapped through the same
     *  transform chain — a transformed AABB midpoint drifts off the visual
     *  centre once the GUI isometric rotation mixes asymmetric axes
     *  (2026-11-14 aeronautics:wooden_propeller ~0.1 block off-hub orbit;
     *  2026-11-16 Kaleidoscope Cookery: the interim vertex median is
     *  density-sensitive, see the modelBounds comment). */
    private static ModelBounds modelBounds(BakedModel model) {
        Object cached = ITEM_MODEL_BOUNDS.get(model);
        if (cached instanceof ModelBounds bounds) {
            return bounds;
        }
        if (cached == NO_CENTER) {
            return NO_BOUNDS;
        }
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
        // rotation mixes the uneven axes (2026-11-14:
        // aeronautics:wooden_propeller). 2026-11-16 (Kaleidoscope Cookery
        // pack): the interim per-axis vertex MEDIAN is density-sensitive —
        // decorative zero-thickness planes (trash_can handle and inner
        // panels) and piled servings skew it 2-5 px off the visual centre
        // (trash_can z median 5.0 vs box centre 8.0), and hand models list
        // arbitrary face subsets so the skew is not predictable. The
        // raw-vertex-extent box centre is density-independent, equals the
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
            transformPoint(renderer, p);
            minX = Math.min(minX, p.x());
            minY = Math.min(minY, p.y());
            minZ = Math.min(minZ, p.z());
            maxX = Math.max(maxX, p.x());
            maxY = Math.max(maxY, p.y());
            maxZ = Math.max(maxZ, p.z());
        }
        if (rawX.isEmpty() || !(minX < maxX && minY < maxY && minZ < maxZ)) {
            ITEM_MODEL_BOUNDS.put(model, NO_CENTER);
            return NO_BOUNDS;
        }
        // Pivot on the raw-vertex-extent box centre (see above); largest axis
        // of the rendered bounding box stays the auto-fit metric (block units).
        Vector3f center = new Vector3f((rMinX + rMaxX) * 0.5F,
                (rMinY + rMaxY) * 0.5F, (rMinZ + rMaxZ) * 0.5F);
        transformPoint(renderer, center);
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
        Random random = new Random();
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

    /** 1.18.2 Matrix4f has no point-transform method — multiply manually via
     *  store(FloatBuffer) (GL column-major: translation lands at indices
     *  12/13/14, rows are 4 elements apart). */
    private static void transformPoint(Matrix4f m, Vector3f p) {
        java.nio.FloatBuffer buf = java.nio.ByteBuffer.allocateDirect(16 * 4).asFloatBuffer();
        m.store(buf);
        float x = p.x(), y = p.y(), z = p.z();
        float nx = buf.get(0) * x + buf.get(4) * y + buf.get(8) * z + buf.get(12);
        float ny = buf.get(1) * x + buf.get(5) * y + buf.get(9) * z + buf.get(13);
        float nz = buf.get(2) * x + buf.get(6) * y + buf.get(10) * z + buf.get(14);
        p.set(nx, ny, nz);
    }

    /** Whether 3D item previews can render in this client (Iris/Oculus force
     *  the 2D fallback; Modern UI deliberately does NOT — see
     *  docs/MODERN-UI-COMPAT.md). */
    public static boolean supports3D() {
        if (!logged3DEnvironment) {
            logged3DEnvironment = true;
            LOGGER.info("[csgobox] 3D preview env: supports3D={} iris={} oculus={} modernui={}",
                    !isShaderModActive(), ModList.get().isLoaded("iris"),
                    ModList.get().isLoaded("oculus"), ModList.get().isLoaded("modernui"));
        }
        return !isShaderModActive();
    }

    private static boolean isShaderModActive() {
        return ModList.get().isLoaded("iris") || ModList.get().isLoaded("oculus");
    }

    // ============= Generic entity previews (spawn eggs / armor / summon cards / same-id entities) =============

    /** Item ids whose same-named entity must never become the 3D preview:
     *  escape hatch for convention collisions (L4 finds one, add its
     *  {@code namespace:path} item id). Empty today. */
    private static final Set<String> ENTITY_CONVENTION_DENYLIST = Set.of();

    /** Parked client-side preview entities, cached per entity-type id (the
     *  model resources are heavy and renderers animate against the entity).
     *  Not added to the client world — rendered explicitly via the
     *  dispatcher. */
    // Strong map on purpose: a WeakHashMap keyed by the freshly-built registry-id
    // string drops every entry on the next GC, and the next frame then silently
    // recreates a fresh entity (default pose = static), so the inspect animation
    // drove an instance that was no longer the rendered one. Bounded by the
    // number of entity types ever previewed; stale-level entries are replaced on
    // first use after a world change (see entityPreviewInstance).
    private static final Map<String, Entity> ENTITY_PREVIEW_INSTANCES = new HashMap<>();

    /** Flat-sprite verdict per baked item model: true when the model's raw
     *  geometry is a 1/16-thick slab extruded along Z — the generated
     *  {@code item/generated} sprite shape at any layer count — versus real
     *  3D geometry that spans beyond the slab. 2026-11-14: the earlier
     *  "zero-thickness" and "one shared normal axis" tests never fired (the
     *  generator bakes an extruded silhouette with edge faces); see
     *  {@code measureFlatSprite}. */
    private static final Map<BakedModel, Boolean> FLAT_ITEM_MODELS = new WeakHashMap<>();

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
        Random random = new Random();
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
     *  match the placed look. Portion-counter integer properties
     *  — amount-style integer properties are maxed, {@code bites} (bites
     *  taken) is reset to the fresh state — so the preview shows the
     *  full/served state. The original model is returned unchanged when the
     *  item is not block-backed, its model is not flat, or the block has no
     *  measurable geometry. */
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
                    ForgeRegistries.ITEMS.getKey(item.getItem()), t);
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
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item.getItem());
        if (itemId == null || ENTITY_CONVENTION_DENYLIST.contains(itemId.toString())
                || item.getItem().isEdible()) {
            return null;
        }
        return ForgeRegistries.ENTITIES.containsKey(itemId)
                ? ForgeRegistries.ENTITIES.getValue(itemId) : null;
    }

    /** Generic summon-container convention: any item whose
     *  {@code tag.BlockEntityTag} (Forge's BlockItem BE data storage) stores
     *  an {@code EntityType} string that resolves to a registered entity.
     *  Type-only — the block entity tag is deliberately not loaded onto the
     *  preview entity. Null when the convention does not apply. */
    private static EntityType<?> summonContainerEntityType(ItemStack stack) {
        if (stack.isEmpty() || !stack.hasTag()) {
            return null;
        }
        try {
            CompoundTag beTag = stack.getTag().getCompound("BlockEntityTag");
            if (beTag.isEmpty() || !beTag.contains("EntityType", 8)) {
                return null;
            }
            ResourceLocation typeId = ResourceLocation.tryParse(beTag.getString("EntityType"));
            return typeId != null && ForgeRegistries.ENTITIES.containsKey(typeId)
                    ? ForgeRegistries.ENTITIES.getValue(typeId) : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Spawn-egg {@code EntityTag} (NBT storage of the egg's mob and its
     *  variant data); re-applied onto the shared preview instance every
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
     *  failure. Cached per entity type; not added to the client world. The
     *  entity stays fully visible: LivingEntity renderers skip the whole
     *  model for invisible entities, which starved the probe to zero
     *  vertices on every vanilla mob — the shadow pass is suppressed per
     *  render instead (see {@link #renderPreviewEntity}). */
    private static Entity entityPreviewInstance(EntityType<?> type, Level level) {
        String cacheKey = ForgeRegistries.ENTITIES.getKey(type).toString();
        Entity cached = ENTITY_PREVIEW_INSTANCES.get(cacheKey);
        if (cached != null && cached.level == level) {
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
                    ForgeRegistries.ENTITIES.getKey(type), t);
            return null;
        }
    }

    /** Entity bounds from the entity's own AABB (centre relative to the
     *  parked position + largest-axis span). The submit-node vertex probe of
     *  the newer platforms does not exist on 1.18.2; entity AABBs match
     *  their model extents, which makes this exact for vanilla-class
     *  preview entities (mobs, armor stands, vehicles-free 1.18.2). */
    private static ModelBounds entityAabbBounds(Entity entity) {
        AABB bb = entity.getBoundingBox();
        float span = (float) Math.max(bb.getXsize(), Math.max(bb.getYsize(), bb.getZsize()));
        if (span <= 0.0F) {
            return NO_BOUNDS;
        }
        return new ModelBounds(
                new Vector3f(
                        (float) ((bb.minX + bb.maxX) * 0.5D - entity.getX()),
                        (float) ((bb.minY + bb.maxY) * 0.5D - entity.getY()),
                        (float) ((bb.minZ + bb.maxZ) * 0.5D - entity.getZ())),
                span);
    }

    /** Entity-based 3D preview for an arbitrary EntityType (spawn eggs,
     *  summon cards, same-id entities): park a shared client entity, apply
     *  {@code spawnTag} every frame, fit the entity AABB into the preview
     *  square, draw, and degrade to the flat icon when the entity produces
     *  nothing usable or the render fails. */
    private static void renderEntityPreviewForType(PoseStack pose, ItemStack stack, LivingEntity player,
                                                   int cx, int cy, Quat rotation, float scale, EntityType<?> type,
                                                   CompoundTag spawnTag) {
        Entity entity = entityPreviewInstance(type, player.level);
        markPreviewMob(entity);
        if (entity != null) {
        // Entity.load resets yHeadRot/yBodyRot on every call; identical
        // spawn data must not be re-applied per frame or it wipes the
        // inspect animation state (see previewSpawnDataChanged).
            if (spawnTag != null
                    && previewSpawnDataChanged(ForgeRegistries.ENTITIES.getKey(type).toString(), stack)) {
                try {
                    entity.load(spawnTag);
                    // load() may carry Pos/Rotation from the save tag; keep
                    // the parked pose so the preview never drifts.
                    entity.setPos(0.0D, 64.0D, 0.0D);
                    entity.setYRot(180.0F);
                    entity.setXRot(0.0F);
                } catch (Throwable t) {
                    LOGGER.warn("Entity preview: applying the spawn tag failed; keeping the default look", t);
                }
            }
            ModelBounds bounds = entityAabbBounds(entity);
            if (bounds != NO_BOUNDS && renderEntityPreview(pose, entity, bounds, cx, cy, rotation, scale)) {
                return;
            }
        }
        renderItem2D(player, pose, stack, cx, cy, scale);
    }

    /** The real 3D entity draw: same pose convention as every other 3D
     *  branch (cell top-left origin, drag quaternion, centre pivot), scaled
     *  by the AABB bounds so oversized entities stay inside the preview
     *  square. Returns false when the render throws, so the caller can
     *  degrade to the flat icon. */
    private static boolean renderEntityPreview(PoseStack pose, Entity entity, ModelBounds bounds,
                                               int cx, int cy, Quat rotation, float scale) {
        float fitScale = fitScaleFor(bounds.span(), scale);
        pose.pushPose();
        pose.translate(cx, cy, 100.0F);
        pose.translate(8.0F * scale, 8.0F * scale, 0.0F);
        pose.scale(1.0F, -1.0F, 1.0F);
        // 1.18.2 Quaternion has no value accessors — normalize via the common
        // Quat components before constructing the Mojang instance (same as
        // the item branch).
        float qx = rotation.x(), qy = rotation.y(), qz = rotation.z(), qw = rotation.w();
        float qlen = (float) Math.sqrt(qx * qx + qy * qy + qz * qz + qw * qw);
        if (qlen > 1.0E-6F) {
            qx /= qlen; qy /= qlen; qz /= qlen; qw /= qlen;
        }
        pose.mulPose(new Quaternion(qx, qy, qz, qw));
        Lighting.setupForEntityInInventory();
        pose.scale(16.0F * fitScale, 16.0F * fitScale, 16.0F * fitScale);
        Vector3f center = bounds.center();
        pose.translate(-center.x(), -center.y(), -center.z());
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        PoseStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushPose();
        modelViewStack.mulPoseMatrix(pose.last().pose());
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
            modelViewStack.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.enableDepthTest();
            pose.popPose();
        }
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
            if (PREVIEW_MOB_MARK_LOG.add(ForgeRegistries.ENTITIES.getKey(entity.getType()).toString())) {
                LOGGER.info("[csgobox] preview mob armed: type={}",
                        ForgeRegistries.ENTITIES.getKey(entity.getType()));
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
            if (PREVIEW_MOB_DRIVE_LOG.add(ForgeRegistries.ENTITIES.getKey(entity.getType()).toString())) {
                LOGGER.info("[csgobox] preview mob anim: type={} (first drive)",
                        ForgeRegistries.ENTITIES.getKey(entity.getType()));
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
            living.animationSpeed = 0.0F;
            living.animationSpeedOld = 0.0F;
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
        float walkTarget = phase == 0 ? PREVIEW_MOB_WALK_TARGET : 0.0F;
        living.animationSpeedOld = living.animationSpeed;
        living.animationSpeed += (walkTarget - living.animationSpeed) * 0.4F;
        living.animationPosition += living.animationSpeed;


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
     *  {@code ArmorItem} slot), fit the stand AABB, draw, and degrade to the
     *  flat icon. GUI never draws the worn model — it lives in the humanoid
     *  armor layers, which the stand's renderer carries; pieces whose mod
     *  registers no armor-stand layer render nothing and degrade visually.
     *  Animal armor (wolf/horse) uses its own renderer and is not covered. */
    private static void renderArmorStandPreview(PoseStack pose, ItemStack stack, LivingEntity player,
                                                int cx, int cy, Quat rotation, float scale) {
        Entity entity = entityPreviewInstance(EntityType.ARMOR_STAND, player.level);
        if (entity instanceof ArmorStand stand) {
            stand.setItemSlot(((ArmorItem) stack.getItem()).getSlot(), stack);
            ModelBounds bounds = entityAabbBounds(stand);
            if (bounds != NO_BOUNDS && renderEntityPreview(pose, stand, bounds, cx, cy, rotation, scale)) {
                return;
            }
        }
        renderItem2D(player, pose, stack, cx, cy, scale);
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
