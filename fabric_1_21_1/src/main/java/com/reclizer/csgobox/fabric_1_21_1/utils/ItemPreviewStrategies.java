package com.reclizer.csgobox.fabric_1_21_1.utils;

import com.reclizer.csgobox.utils.Quat;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.resource.GunDisplayInstance;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import org.joml.Quaternionf;

import java.util.List;
import java.util.Optional;

/**
 * Ordered preview strategies for {@link AnimRenderOps}: the first strategy
 * whose {@link ItemPreviewStrategy#matches} returns true owns the item's
 * preview, in both the 3D (drag/inspect/terminal) and 2D (grid/strip) passes.
 * Current chain, in matching order:
 * <ol>
 *   <li>TACZ guns (TaCZ: Refabricated — optional dependency, the gate must
 *       precede every TACZ class touch: instance construction never loads
 *       TACZ classes, only gated method bodies do);</li>
 *   <li>Superb Warfare guns (ReFabricated; FIXED-context baked render, zero
 *       SBW class references);</li>
 *   <li>Sophisticated Backpacks shells (FIXED-context baked render with a
 *       rest yaw showing the pocket face; zero SBP class references);</li>
 *   <li>Vanilla + modded spawn eggs (mob model via SpawnEggItem.getType);</li>
 *   <li>Armor pieces (invisible armor stand proxy);</li>
 *   <li>Generic summon containers ({@code minecraft:block_entity_data}
 *       carrying a resolvable {@code EntityType} — the Superb Warfare
 *       summon-crate convention, also matched for addon mods reusing it
 *       under their own namespaces).</li>
 * </ol>
 * Anything unmatched falls through to the vanilla baked-model path inside
 * {@link AnimRenderOps}, which carries two generic conventions of its own
 * (zero per-mod code): the same-id entity rule (flat sprite + entity sharing
 * the item's id, food items excluded) and the generic custom-renderer branch
 * in the 3D pass (BEWLR items render under FIXED with probe-based fit).
 * Platform note: this port mirrors the v1_21_1 strategy structure, trimmed to
 * the mods with a Fabric build — no SBW vehicle strategy (the generic
 * container convention covers the summon crates here), no SlashBlade / TLM /
 * Create / Prefab strategies (no Fabric builds), and the 2D pass keeps the
 * flat icon for custom-renderer models (no 2D BEWLR reroute on this
 * platform). New mod adaptations plug in here instead of growing the
 * if-chain; rendering bodies stay in AnimRenderOps so the era-specific pose
 * plumbing has exactly one home.
 */
final class ItemPreviewStrategies {

    private ItemPreviewStrategies() {
    }

    /** The ordered strategy chain; first match wins. */
    static final List<ItemPreviewStrategy> ALL = List.of(
            new TaczGunStrategy(),
            new SbwGunStrategy(),
            new SbpShellStrategy(),
            new SpawnEggStrategy(),
            new ArmorStandStrategy(),
            new EntityContainerStrategy());

    /** One mod-specific preview override. Rendering delegates back into
     *  {@link AnimRenderOps} (package-private bodies, one pose convention). */
    interface ItemPreviewStrategy {

        boolean matches(ItemStack stack);

        /** Render the 3D preview (drag rotation). May deliberately draw
         *  nothing (e.g. a TACZ display still loading). */
        void render3D(GuiGraphics gg, ItemStack stack, LivingEntity player,
                      int cx, int cy, Quat rotation, float scale);

        /** Render the 2D grid cell. Returns true when handled; false falls
         *  through to the generic flow (the flat icon on this platform). */
        boolean render2D(LivingEntity entity, GuiGraphics gg, ItemStack stack,
                         float x, float y, float scale);
    }

    /** TACZ guns (TaCZ: Refabricated): the vanilla renderer only draws their
     *  flat slot texture, so the 3D pass renders the bedrock gun model
     *  directly. Every TACZ class reference sits behind the matches() gate —
     *  without TACZ the strategy matches nothing and no com.tacz class is
     *  ever resolved. */
    private static final class TaczGunStrategy implements ItemPreviewStrategy {

        @Override
        public boolean matches(ItemStack stack) {
            return FabricLoader.getInstance().isModLoaded(AnimRenderOps.TACZ_MOD_ID)
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
            // the flat icon is exactly what vanilla inventories show. The real
            // 3D model stays on the 3D drag preview, which renders it with a
            // proper rotation.
            AnimRenderOps.renderItem2DFlat(entity, gg, stack, x, y, scale);
            return true;
        }
    }

    /** Superb Warfare (ReFabricated) keeps its 3D gun model out of the GUI
     *  perspective (separate-transforms routes gui -> flat icon), so the 3D
     *  pass renders under {@code ItemDisplayContext.FIXED} — what an item
     *  frame shows. The ReFabricated port's model wiring is not separately
     *  verified: for plain (non-separate-transforms) items FIXED renders like
     *  GUI, so the worst case on that build is the unchanged flat-icon look.
     *  In 2D grids the item's own flat icon already matches vanilla, so this
     *  strategy only owns the 3D pass. */
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
     *  maintainer call.) The Fabric port's shell model is a builtin/entity
     *  BEWLR (custom loader, same mechanism as NeoForge — javap-verified
     *  2026-11-21). Platform note: this port's 2D pass has no custom-renderer
     *  reroute, so 2D grid cells keep the shell's flat icon (as before this
     *  strategy existed). */
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
            // No 2D custom-renderer reroute on this platform: the generic
            // flat icon is the established grid cell for BEWLR items here.
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
            AnimRenderOps.renderEntityPreviewForType(gg, stack, player, cx, cy, rotation, scale, type,
                    AnimRenderOps.entityDataOf(stack));
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
     *  own namespaces). The 3D pass renders that entity through the shared
     *  preview path and degrades to the flat icon; 2D grids keep the item's
     *  own look. (This platform has no SBW-specific vehicle strategy: the
     *  summon crates are covered here, icons included.) */
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
}
