package com.reclizer.csgobox.v1_21_1.utils;

import com.reclizer.csgobox.utils.Quat;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.resource.GunDisplayInstance;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.fml.ModList;

import java.util.List;
import java.util.Optional;

/**
 * Ordered preview strategies for {@link AnimRenderOps}: the first strategy
 * whose {@link ItemPreviewStrategy#matches} returns true owns the item's
 * preview, in both the 3D (drag/inspect/terminal) and 2D (grid/strip) passes.
 * Current chain, in matching order:
 * <ol>
 *   <li>TACZ guns (optional dependency — the gate must precede every TACZ
 *       class touch, same discipline as AnimRenderOps itself: instance
 *       construction never loads TACZ classes, only gated method bodies
 *       do);</li>
 *   <li>SlashBlade: Resharped blades (weapon-rack entity proxy, zero
 *       SlashBlade class references);</li>
 *   <li>Touhou Little Maid soul slabs (车万女仆 魂符): INIT slab shows a
 *       random maid model per session, stored-maid slab shows the exact
 *       stored maid — both through TLM's own EntityMaidRenderer, zero TLM
 *       class references;</li>
 *   <li>Superb Warfare vehicle summon crates (entity proxy, zero SBW
 *       class references);</li>
 *   <li>Superb Warfare guns (FIXED-context baked render, zero SBW class
 *       references);</li>
 *   <li>Vanilla + modded spawn eggs (mob model via SpawnEggItem.getType);</li>
 *   <li>Armor pieces (invisible armor stand proxy);</li>
 *   <li>Generic summon containers ({@code minecraft:block_entity_data}
 *       carrying a resolvable {@code EntityType} — the Superb Warfare
 *       summon-crate convention, also matched for addon mods reusing it
 *       under their own namespaces);</li>
 *   <li>Create schematics (structure preview; Create's own file loader is
 *       reached by reflection — zero Create compile-time references).</li>
 * </ol>
 * Anything unmatched falls through to the vanilla baked-model path inside
 * {@link AnimRenderOps}, which carries two generic conventions of its own
 * (zero per-mod code): the same-id entity rule (flat sprite + entity sharing
 * the item's id, food items excluded, denylist escape hatch) and the
 * display-transform detection (an explicit {@code display.fixed} renders
 * under FIXED, everything else under GUI). New mod adaptations plug in here
 * instead of growing the if-chain; rendering bodies stay in AnimRenderOps so
 * the era-specific pose plumbing has exactly one home.
 */
final class ItemPreviewStrategies {

    private ItemPreviewStrategies() {
    }

    /** The ordered strategy chain; first match wins. */
    static final List<ItemPreviewStrategy> ALL = List.of(
            new TaczGunStrategy(),
            new SlashBladeStrategy(),
            new MaidSlabStrategy(),
            new SbwVehicleStrategy(),
            new SbwGunStrategy(),
            new SpawnEggStrategy(),
            new ArmorStandStrategy(),
            new EntityContainerStrategy(),
            new CreateSchematicStrategy());

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
            return ModList.get() != null && ModList.get().isLoaded(AnimRenderOps.TACZ_MOD_ID)
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
            // Without a loaded display the custom renderer paints the
            // missing-texture checkerboard across the card; suppress the draw
            // so the card frame alone shows until the display is available.
            return TimelessAPI.getGunDisplay(stack).isEmpty();
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

    /** Superb Warfare summon containers ({@code superbwarfare:container} with
     *  a block_entity_data "EntityType") carry a vehicle, not a box item: the
     *  3D pass renders the real vehicle model via SBW's own entity renderer,
     *  degrading to SBW's inventory icon / crate content. 2D grids draw the
     *  vehicle icon directly (SBW's decorator behaviour); no icon texture
     *  falls through to the crate's own FIXED-context 3D content. Vanilla
     *  registries only — no SBW class touched. */
    /** Touhou Little Maid soul slabs (车万女仆 魂符): the INIT slab carries no
     *  model of its own — TLM rolls a random maid at spawn time and the 3D
     *  pass mirrors that with one random id per session from the client
     *  model registry; the stored-maid slab loads its {@code maid_info} save
     *  tag so the preview shows the exact stored maid. Both draw through
     *  TLM's own EntityMaidRenderer via the shared entity preview path
     *  (vanilla MobRenderer subclass — the dispatcher renders it), zero TLM
     *  class references. 2D grids keep the flat slab icon. */
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
            AnimRenderOps.renderFixedContextModel3D(gg, stack, player, cx, cy, rotation, scale);
        }

        @Override
        public boolean render2D(LivingEntity entity, GuiGraphics gg, ItemStack stack,
                                float x, float y, float scale) {
            return false;
        }
    }

    /** Vanilla + modded spawn eggs: {@code SpawnEggItem.getType} resolves the
     *  egg's mob (EntityTag storage handled by the vanilla item, so this
     *  needs no NBT-shape knowledge; a bare egg falls back to its item's
     *  default type), and the 3D pass renders the actual mob model through
     *  the shared entity preview path, re-applying the egg's
     *  {@code entity_data} tag so variants (colour, profession) show like the
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
                type = ((SpawnEggItem) stack.getItem()).getType(stack);
            } catch (Throwable t) {
                type = null;
            }
            if (type == null) {
                AnimRenderOps.renderItem2DFlat(player, gg, stack, cx, cy, scale);
                return;
            }
            CustomData spawnData;
            try {
                spawnData = stack.getOrDefault(DataComponents.ENTITY_DATA, CustomData.EMPTY);
            } catch (Throwable t) {
                spawnData = CustomData.EMPTY;
            }
            AnimRenderOps.renderEntityPreviewForType(gg, stack, player, cx, cy, rotation, scale, type, spawnData);
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

    /** Generic summon containers: items whose
     *  {@code minecraft:block_entity_data} stores an {@code EntityType}
     *  string that resolves to a registered entity (Superb Warfare's
     *  summon-crate convention — addons like AshVehicle reuse it under their
     *  own namespace, which the SBW-specific gate rejects). The 3D pass
     *  renders that entity through the shared preview path and degrades to
     *  the flat icon; 2D grids keep the item's own look (the SBW decorator
     *  icons are SBW-specific and stay with that strategy, which always runs
     *  first). */
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
            AnimRenderOps.renderEntityPreviewForType(gg, stack, player, cx, cy, rotation, scale, type);
        }

        @Override
        public boolean render2D(LivingEntity entity, GuiGraphics gg, ItemStack stack,
                                float x, float y, float scale) {
            return false;
        }
    }

    /** Create schematics ({@code create:schematic}): the 3D pass renders the
     *  blueprint's building as tessellated blocks. The structure lives in a
     *  local .nbt file, loaded through Create's own {@code loadSchematic}
     *  resolved by reflection — the item id gate proves the mod is loaded
     *  before that class is ever named. Missing file, empty and over-budget
     *  structures degrade to the flat icon; 2D grids keep the paper icon. */
    private static final class CreateSchematicStrategy implements ItemPreviewStrategy {

        @Override
        public boolean matches(ItemStack stack) {
            if (stack.isEmpty()) {
                return false;
            }
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            return "create".equals(itemId.getNamespace()) && "schematic".equals(itemId.getPath());
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
}
