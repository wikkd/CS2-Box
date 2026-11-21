package com.reclizer.csgobox.forge_1_20_1.utils;

import com.reclizer.csgobox.utils.Quat;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.resource.GunDisplayInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraftforge.fml.ModList;
import org.joml.Quaternionf;

import java.util.List;
import java.util.Optional;

/**
 * Ordered preview strategies for {@link AnimRenderOps}: the first strategy
 * whose {@link ItemPreviewStrategy#matches} returns true owns the item's
 * preview, in both the 3D (drag/inspect/terminal) and 2D (grid/strip) passes.
 * Current chain, in matching order (mirrors the inline if-chain this
 * structure replaces, 2026-11-21):
 * <ol>
 *   <li>TACZ guns (optional dependency — the gate must precede every TACZ
 *       class touch, same discipline as AnimRenderOps itself: instance
 *       construction never loads TACZ classes, only gated method bodies
 *       do);</li>
 *   <li>Superb Warfare vehicle summon crates (entity proxy, zero SBW
 *       class references);</li>
 *   <li>Superb Warfare guns (FIXED-context baked render, zero SBW class
 *       references);</li>
 *   <li>Sophisticated Backpacks shells (FIXED-context baked render with a
 *       rest yaw showing the pocket face; zero SBP class references);</li>
 *   <li>Vanilla + modded spawn eggs (mob model via SpawnEggItem.getType);</li>
 *   <li>Armor pieces (invisible armor stand proxy);</li>
 *   <li>Generic summon containers ({@code tag.BlockEntityTag}
 *       carrying a resolvable {@code EntityType} — the Superb Warfare
 *       summon-crate convention, also matched for addon mods reusing it
 *       under their own namespaces);</li>
 *   <li>Create schematics (structure preview; Create's own file loader is
 *       reached by reflection — zero Create compile-time references);</li>
 *   <li>Prefab blueprints (the mod's gz-packed structure JSON through its
 *       own ghost-preview flow, reflectively — see
 *       {@link PrefabStructurePreview});</li>
 *   <li>SlashBlade: Resharped blades (weapon-rack entity proxy, zero
 *       SlashBlade class references);</li>
 *   <li>Touhou Little Maid soul slabs (车万女仆 魂符): INIT slab shows a
 *       random maid model per session, stored-maid slab shows the exact
 *       stored maid — both through TLM's own EntityMaidRenderer, zero TLM
 *       class references;</li>
 *   <li>Touhou Little Maid garage kits (车万女仆 手办);</li>
 *   <li>Touhou Little Maid chairs (车万女仆 坐垫).</li>
 * </ol>
 * Anything unmatched falls through to the vanilla baked-model path inside
 * {@link AnimRenderOps}, which carries the generic conventions of its own
 * (zero per-mod code): the same-id entity rule (flat sprite + entity sharing
 * the item's id, food items excluded), the block-backed flat-sprite fallback
 * and the generic custom-renderer branch (BEWLR items render under FIXED
 * with probe-based fit, 3D and 2D alike). New mod adaptations plug in here
 * instead of growing the if-chain; rendering bodies stay in AnimRenderOps so
 * the era-specific pose plumbing has exactly one home.
 */
final class ItemPreviewStrategies {

    private ItemPreviewStrategies() {
    }

    /** The ordered strategy chain; first match wins. */
    static final List<ItemPreviewStrategy> ALL = List.of(
            new TaczGunStrategy(),
            new SbwVehicleStrategy(),
            new SbwGunStrategy(),
            new SbpShellStrategy(),
            new SpawnEggStrategy(),
            new ArmorStandStrategy(),
            new EntityContainerStrategy(),
            new CreateSchematicStrategy(),
            new PrefabBlueprintStrategy(),
            new SlashBladeStrategy(),
            new MaidSlabStrategy(),
            new TlmGarageKitStrategy(),
            new TlmChairStrategy());

    /** One mod-specific preview override. Rendering delegates back into
     *  {@link AnimRenderOps} (package-private bodies, one pose convention). */
    interface ItemPreviewStrategy {

        boolean matches(ItemStack stack);

        /** Render the 3D preview (drag rotation). May deliberately draw
         *  nothing (e.g. a TACZ display still loading). */
        void render3D(GuiGraphics gg, ItemStack stack, LivingEntity player,
                      int cx, int cy, Quat rotation, float scale);

        /** Render the 2D grid cell. Returns true when handled; false falls
         *  through to the generic flow (custom-renderer FIXED context, then
         *  the flat icon). */
        boolean render2D(LivingEntity entity, GuiGraphics gg, ItemStack stack,
                         float x, float y, float scale);
    }

    /** TACZ guns: the vanilla renderer only draws their flat slot texture, so
     *  the 3D pass renders the bedrock gun model directly. Every TACZ class
     *  reference sits behind the matches() gate — without TACZ the strategy
     *  matches nothing and no com.tacz class is ever resolved. */
    private static final class TaczGunStrategy implements ItemPreviewStrategy {

        @Override
        public boolean matches(ItemStack stack) {
            return ModList.get().isLoaded(AnimRenderOps.TACZ_MOD_ID)
                    && stack.getItem() instanceof IGun;
        }

        @Override
        public void render3D(GuiGraphics gg, ItemStack stack, LivingEntity player,
                             int cx, int cy, Quat rotation, float scale) {
            Optional<GunDisplayInstance> display = TimelessAPI.getGunDisplay(stack);
            // No loaded display -> nothing to draw (TACZ would fall back to the
            // missing-texture slot icon, painting a magenta checkerboard).
            if (display.isEmpty()) {
                return;
            }
            BedrockGunModel gunModel = display.get().getGunModel();
            if (gunModel == null) {
                return;
            }
            AnimRenderOps.renderGunModel3D(gg, stack, cx, cy, rotation, scale, gunModel, display.get());
        }

        @Override
        public boolean render2D(LivingEntity entity, GuiGraphics gg, ItemStack stack,
                                float x, float y, float scale) {
            Optional<GunDisplayInstance> display = TimelessAPI.getGunDisplay(stack);
            // Without a loaded display the custom renderer paints the
            // missing-texture checkerboard across the card; suppress the draw
            // so the card frame alone shows until the display is available.
            if (display.isEmpty()) {
                return true;
            }
            if (display.get().getGunModel() == null) {
                return true;
            }
            // Loaded guns: degrade to the inventory-style flat slot icon
            // (2026-11-20 preview-page report) — the fitted identity-rotation
            // gun model reads as a tiny muzzle-up stick at cell scale, while
            // the flat icon is exactly what vanilla inventories show and
            // needs no per-gun rotation tuning. The real 3D model stays on
            // the 3D drag preview, which renders it with a proper rotation.
            AnimRenderOps.renderItem2DFlat(entity, gg, stack, x, y, scale);
            return true;
        }
    }

    /** Superb Warfare summon containers ({@code superbwarfare:container} with
     *  a tag.BlockEntityTag "EntityType") carry a vehicle, not a box item: the
     *  3D pass renders the real vehicle model via SBW's own entity renderer,
     *  degrading to SBW's inventory icon / crate content. 2D grids draw the
     *  vehicle icon directly (SBW's decorator behaviour); no icon texture
     *  falls through to the crate's own FIXED-context 3D content. Vanilla
     *  registries only — no SBW class touched. */
    private static final class SbwVehicleStrategy implements ItemPreviewStrategy {

        @Override
        public boolean matches(ItemStack stack) {
            return AnimRenderOps.isSuperbWarfareVehicleContainer(stack);
        }

        @Override
        public void render3D(GuiGraphics gg, ItemStack stack, LivingEntity player,
                             int cx, int cy, Quat rotation, float scale) {
            AnimRenderOps.renderSuperbWarfareVehicle(gg, stack, player, cx, cy, rotation, scale);
        }

        @Override
        public boolean render2D(LivingEntity entity, GuiGraphics gg, ItemStack stack,
                                float x, float y, float scale) {
            CompoundTag vehicleTag = AnimRenderOps.superWarfareContainerTag(stack);
            ResourceLocation vehicleIcon = vehicleTag == null ? null
                    : AnimRenderOps.superbWarfareVehicleIcon(vehicleTag.getString("EntityType"));
            if (vehicleIcon == null) {
                return false;
            }
            AnimRenderOps.renderVehicleIcon(gg, vehicleIcon, x, y, scale);
            return true;
        }
    }

    /** Superb Warfare keeps its 3D gun model out of the GUI perspective
     *  (separate-transforms routes gui -> flat icon), so the 3D pass renders
     *  under {@code ItemDisplayContext.FIXED} — what an item frame shows. In
     *  2D grids the same generic custom-renderer branch already produces the
     *  FIXED model, so this strategy only owns the 3D pass. */
    private static final class SbwGunStrategy implements ItemPreviewStrategy {

        @Override
        public boolean matches(ItemStack stack) {
            return AnimRenderOps.isSuperbWarfareItem(stack);
        }

        @Override
        public void render3D(GuiGraphics gg, ItemStack stack, LivingEntity player,
                             int cx, int cy, Quat rotation, float scale) {
            AnimRenderOps.renderFixedContextModel3D(gg, stack, player, cx, cy, rotation, scale, null);
        }

        @Override
        public boolean render2D(LivingEntity entity, GuiGraphics gg, ItemStack stack,
                                float x, float y, float scale) {
            return false;
        }
    }

    /** Sophisticated Backpacks (精妙背包) shells: the mod's parent-less model
     *  JSON declares its own fixed display with rotation [0,0,0] — overriding
     *  vanilla block/block's [0,180,0] — so the FIXED context (item frames and
     *  this preview alike) presents the strap face (strap side = +z in the
     *  base geometry). This strategy hands {@link AnimRenderOps
     *  #renderFixedContextModel3D} a rest yaw so the shell shows its pocket
     *  face at rest, matching the gui perspective's front view; the yaw is
     *  conjugated under the drag quaternion (model space) so dragging keeps
     *  rotating around the screen axes, and the envelope centre is symmetric
     *  under the yaw, so fit and centring are untouched. (2026-11-21
     *  maintainer call.) The stock jar's shell model is a builtin/entity
     *  BEWLR (BackpackDynamicModel$BackpackBakedModel#isCustomRenderer is the
     *  sole {@code true}, javap-verified 2026-11-21) — the 2D pass mirrors the
     *  generic custom-renderer branch (shader guard included) with the same
     *  rest pose, so grid cells match the 3D preview. */
    private static final class SbpShellStrategy implements ItemPreviewStrategy {

        /** SBP item namespace: a registered item's namespace proves the mod
         *  is loaded and no SBP class is ever referenced. Only the backpack
         *  shells reach this strategy — every upgrade item bakes as a plain
         *  model and takes the default quads path. */
        private static final String SOPHISTICATED_BACKPACKS_MOD_ID = "sophisticatedbackpacks";

        /** Rest pose: turn the shell from its strap face (the mod's own fixed
         *  display) to the pocket face. */
        private static final Quaternionf FRONT_REST_YAW =
                new Quaternionf().rotationY((float) Math.PI);

        @Override
        public boolean matches(ItemStack stack) {
            return !stack.isEmpty() && SOPHISTICATED_BACKPACKS_MOD_ID.equals(
                    BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace());
        }

        @Override
        public void render3D(GuiGraphics gg, ItemStack stack, LivingEntity player,
                             int cx, int cy, Quat rotation, float scale) {
            AnimRenderOps.renderFixedContextModel3D(gg, stack, player, cx, cy, rotation, scale, FRONT_REST_YAW);
        }

        @Override
        public boolean render2D(LivingEntity entity, GuiGraphics gg, ItemStack stack,
                                float x, float y, float scale) {
            BakedModel model = Minecraft.getInstance().getItemRenderer()
                    .getModel(stack, entity.level(), entity, 0);
            if (model.isCustomRenderer() && !AnimRenderOps.isShaderModActive()) {
                AnimRenderOps.renderFixedContextModel3D(gg, stack, entity, x, y, Quat.IDENTITY, scale, FRONT_REST_YAW);
                return true;
            }
            return false;
        }
    }

    /** Vanilla + modded spawn eggs: {@code SpawnEggItem.getType} resolves the
     *  egg's mob (1.20.1 reads the egg's EntityTag directly — null = the egg
     *  item's default mob), and the 3D pass renders the actual mob model
     *  through the shared entity preview path, re-applying the egg's
     *  EntityTag save tag so variants (colour, profession) show like the
     *  spawned mob. 2D grids keep the flat egg sprite — its designed
     *  inventory look and the cheapest render for dense strips. */
    private static final class SpawnEggStrategy implements ItemPreviewStrategy {

        @Override
        public boolean matches(ItemStack stack) {
            return !stack.isEmpty() && stack.getItem() instanceof SpawnEggItem;
        }

        @Override
        public void render3D(GuiGraphics gg, ItemStack stack, LivingEntity player,
                             int cx, int cy, Quat rotation, float scale) {
            EntityType<?> type;
            try {
                type = ((SpawnEggItem) stack.getItem()).getType(AnimRenderOps.spawnEntityTag(stack));
            } catch (Throwable t) {
                type = null;
            }
            if (type == null) {
                AnimRenderOps.renderItem2DFlat(player, gg, stack, cx, cy, scale);
                return;
            }
            AnimRenderOps.renderEntityPreviewForType(gg, stack, player, cx, cy, rotation, scale, type,
                    AnimRenderOps.spawnEntityTag(stack));
        }

        @Override
        public boolean render2D(LivingEntity entity, GuiGraphics gg, ItemStack stack,
                                float x, float y, float scale) {
            return false;
        }
    }

    /** Armor pieces: GUI never draws the worn model (it lives in the humanoid
     *  armor layers, not the baked item model), so the 3D pass equips an
     *  invisible armor stand with the piece and renders the stand — trims,
     *  enchant glints and modded armor models that register an armor-stand
     *  layer come along. Pieces whose mod registers no armor-stand layer
     *  render nothing (empty probe) and degrade to the flat icon. Animal
     *  armor (wolf/horse) uses its own renderer and is not covered. */
    private static final class ArmorStandStrategy implements ItemPreviewStrategy {

        @Override
        public boolean matches(ItemStack stack) {
            return !stack.isEmpty() && stack.getItem() instanceof ArmorItem;
        }

        @Override
        public void render3D(GuiGraphics gg, ItemStack stack, LivingEntity player,
                             int cx, int cy, Quat rotation, float scale) {
            AnimRenderOps.renderArmorStandPreview(gg, stack, player, cx, cy, rotation, scale);
        }

        @Override
        public boolean render2D(LivingEntity entity, GuiGraphics gg, ItemStack stack,
                                float x, float y, float scale) {
            return false;
        }
    }

    /** Generic summon containers: items whose {@code tag.BlockEntityTag}
     *  stores an {@code EntityType} string that resolves to a registered
     *  entity (Superb Warfare's summon-crate convention — addons like
     *  AshVehicle reuse it under their own namespaces, which the SBW-specific
     *  gate rejects). The 3D pass renders that entity through the shared
     *  preview path and degrades to the flat icon; 2D grids keep the item's
     *  own look (the SBW decorator icons are SBW-specific and stay with that
     *  strategy, which always runs first). */
    private static final class EntityContainerStrategy implements ItemPreviewStrategy {

        @Override
        public boolean matches(ItemStack stack) {
            return AnimRenderOps.summonContainerEntityType(stack) != null;
        }

        @Override
        public void render3D(GuiGraphics gg, ItemStack stack, LivingEntity player,
                             int cx, int cy, Quat rotation, float scale) {
            EntityType<?> type = AnimRenderOps.summonContainerEntityType(stack);
            if (type == null) {
                AnimRenderOps.renderItem2DFlat(player, gg, stack, cx, cy, scale);
                return;
            }
            AnimRenderOps.renderEntityPreviewForType(gg, stack, player, cx, cy, rotation, scale, type, null);
        }

        @Override
        public boolean render2D(LivingEntity entity, GuiGraphics gg, ItemStack stack,
                                float x, float y, float scale) {
            return false;
        }
    }

    /** Create schematics ({@code create:schematic}): the 3D pass renders the
     *  blueprint's building as tessellated blocks. The structure lives in a
     *  local .nbt file (top-level tag {@code Owner}/{@code File} on this
     *  platform), loaded through Create's own {@code loadSchematic} resolved
     *  by reflection — the item id gate proves the mod is loaded before that
     *  class is ever named. Missing file, empty and over-budget structures
     *  degrade to the flat icon; 2D grids keep the paper icon. */
    private static final class CreateSchematicStrategy implements ItemPreviewStrategy {

        @Override
        public boolean matches(ItemStack stack) {
            return AnimRenderOps.isCreateSchematicItem(stack);
        }

        @Override
        public void render3D(GuiGraphics gg, ItemStack stack, LivingEntity player,
                             int cx, int cy, Quat rotation, float scale) {
            AnimRenderOps.renderCreateSchematicPreview(gg, stack, player, cx, cy, rotation, scale);
        }

        @Override
        public boolean render2D(LivingEntity entity, GuiGraphics gg, ItemStack stack,
                                float x, float y, float scale) {
            return false;
        }
    }

    /** Prefab blueprints ({@code prefab:item_*} blueprint family): the 3D
     *  pass renders the blueprint's whole building — the mod's gz-packed
     *  structure JSON deserialized through its own ghost-preview flow,
     *  reflectively (see {@link PrefabStructurePreview}). Missing/over-budget
     *  structures degrade to the flat icon; 2D grids keep the flat icon. */
    private static final class PrefabBlueprintStrategy implements ItemPreviewStrategy {

        @Override
        public boolean matches(ItemStack stack) {
            return PrefabStructurePreview.isPrefabBlueprint(stack);
        }

        @Override
        public void render3D(GuiGraphics gg, ItemStack stack, LivingEntity player,
                             int cx, int cy, Quat rotation, float scale) {
            AnimRenderOps.renderPrefabStructurePreview(gg, stack, player, cx, cy, rotation, scale);
        }

        @Override
        public boolean render2D(LivingEntity entity, GuiGraphics gg, ItemStack stack,
                                float x, float y, float scale) {
            return false;
        }
    }

    /** SlashBlade (拔刀剑: Resharped) blades: the mod's BEWLR draws the real
     *  3D blade model only when the stack is mounted in its BladeStandEntity
     *  (the weapon rack) — every other FIXED context draws the flat icon.
     *  The 3D pass parks a client-side rack entity, mounts the blade through
     *  its two public fields, and lets the mod's own rack renderer pose the
     *  blade horizontally exactly like the rack does; zero SlashBlade class
     *  references (namespace + cached reflective instanceof gate, vanilla
     *  registry entity). 2D grids keep the flat icon. */
    private static final class SlashBladeStrategy implements ItemPreviewStrategy {

        @Override
        public boolean matches(ItemStack stack) {
            return AnimRenderOps.isSlashBladeBlade(stack);
        }

        @Override
        public void render3D(GuiGraphics gg, ItemStack stack, LivingEntity player,
                             int cx, int cy, Quat rotation, float scale) {
            AnimRenderOps.renderSlashBladeRackPreview(gg, stack, player, cx, cy, rotation, scale);
        }

        @Override
        public boolean render2D(LivingEntity entity, GuiGraphics gg, ItemStack stack,
                                float x, float y, float scale) {
            return false;
        }
    }

    /** Touhou Little Maid soul slabs (车万女仆 魂符): the INIT slab carries no
     *  model of its own — TLM rolls a random maid at spawn time and the 3D
     *  pass mirrors that with one random id per session from the client
     *  model registry; the stored-maid slab loads its MaidInfo save tag so
     *  the preview shows the exact stored maid. Both draw through TLM's own
     *  EntityMaidRenderer via the shared entity preview path (vanilla
     *  MobRenderer subclass — the dispatcher renders it), zero TLM class
     *  references. 2D grids keep the flat slab icon. */
    private static final class MaidSlabStrategy implements ItemPreviewStrategy {

        @Override
        public boolean matches(ItemStack stack) {
            return AnimRenderOps.isMaidSlabItem(stack);
        }

        @Override
        public void render3D(GuiGraphics gg, ItemStack stack, LivingEntity player,
                             int cx, int cy, Quat rotation, float scale) {
            AnimRenderOps.renderMaidSlabPreview(gg, stack, player, cx, cy, rotation, scale);
        }

        @Override
        public boolean render2D(LivingEntity entity, GuiGraphics gg, ItemStack stack,
                                float x, float y, float scale) {
            return false;
        }
    }

    /** Touhou Little Maid garage kits (车万女仆 手办): the stored entity (a
     *  maid by default, any entity for crafted kits) renders through the
     *  shared entity preview path so the figure fills the preview square like
     *  the soul slabs do. Zero TLM class references. 2D grids keep the mod's
     *  own fixed-context icon (the generic BEWLR reroute). */
    private static final class TlmGarageKitStrategy implements ItemPreviewStrategy {

        @Override
        public boolean matches(ItemStack stack) {
            return AnimRenderOps.isTlmGarageKitItem(stack);
        }

        @Override
        public void render3D(GuiGraphics gg, ItemStack stack, LivingEntity player,
                             int cx, int cy, Quat rotation, float scale) {
            AnimRenderOps.renderTlmGarageKitPreview(gg, stack, player, cx, cy, rotation, scale);
        }

        @Override
        public boolean render2D(LivingEntity entity, GuiGraphics gg, ItemStack stack,
                                float x, float y, float scale) {
            return false;
        }
    }

    /** Touhou Little Maid chairs (车万女仆 坐垫): the ModelId NBT selects one of
     *  the mod's resource-pack cushion models; park the mod's own chair
     *  entity and let its renderer draw the model. Zero TLM class references.
     *  2D grids keep the mod's own fixed-context icon. */
    private static final class TlmChairStrategy implements ItemPreviewStrategy {

        @Override
        public boolean matches(ItemStack stack) {
            return AnimRenderOps.isTlmChairItem(stack);
        }

        @Override
        public void render3D(GuiGraphics gg, ItemStack stack, LivingEntity player,
                             int cx, int cy, Quat rotation, float scale) {
            AnimRenderOps.renderTlmChairPreview(gg, stack, player, cx, cy, rotation, scale);
        }

        @Override
        public boolean render2D(LivingEntity entity, GuiGraphics gg, ItemStack stack,
                                float x, float y, float scale) {
            return false;
        }
    }
}
