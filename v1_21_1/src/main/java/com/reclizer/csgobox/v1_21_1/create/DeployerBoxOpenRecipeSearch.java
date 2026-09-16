package com.reclizer.csgobox.v1_21_1.create;

import com.reclizer.csgobox.logic.BoxConstraintTracker;
import com.reclizer.csgobox.v1_21_1.CsgoBox;
import com.reclizer.csgobox.v1_21_1.box.BoxDefinition;
import com.reclizer.csgobox.v1_21_1.box.BoxRegistry;
import com.reclizer.csgobox.v1_21_1.item.ItemCsgoBox;
import com.reclizer.csgobox.v1_21_1.packet.BoxOpenExecutor;
import com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity;
import com.simibubi.create.content.kinetics.deployer.DeployerFakePlayer;
import com.simibubi.create.content.kinetics.deployer.DeployerRecipeSearchEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;

import java.util.Optional;

/**
 * Mechanical-deployer box opening through Create's official recipe search hook
 * (v2.2.0 Create integration rewrite).
 *
 * <p>When a deployer points at a crate riding a belt/depot, Create builds a
 * two-slot {@link RecipeWrapper} (slot 0 = platform crate, slot 1 = deployer's
 * held item), posts {@link DeployerRecipeSearchEvent} and asks every source for
 * a recipe (its own sources use priority 100/50). We answer with a
 * {@link DeployerBoxOpenRecipe} at a much higher priority, whose output is
 * injected lazily through {@code enforceNextResult} — so the crate is rolled
 * exactly once, at the moment Create actually commits the recipe. Create then
 * consumes the held key and replaces the platform crate with the prize, using
 * its own belt/depot pipeline (no reflection, no manual platform item edits).
 *
 * <p>The roll itself still runs through {@link BoxOpenExecutor} (constraints,
 * cooldown, permission gate, pity, {@code BoxOpeningEvent}/{@code BoxOpenedEvent},
 * achievements) with the deployer's fake player, keyed per deployer so two
 * machines never share cooldowns or pity streaks. Key consumption is disabled
 * in the executor — Create owns the held item.
 */
@EventBusSubscriber(modid = CsgoBox.MODID)
public final class DeployerBoxOpenRecipeSearch {

    /** Above Create's own recipe sources (item_application 100 / deploying 50). */
    private static final int PRIORITY = 10_000;

    private DeployerBoxOpenRecipeSearch() {
    }

    @SubscribeEvent
    public static void onDeployerRecipeSearch(DeployerRecipeSearchEvent event) {
        try {
            RecipeWrapper inv = event.getInventory();
            if (inv == null) {
                return;
            }
            ItemStack box = inv.getItem(0);
            ItemStack held = inv.getItem(1);
            if (!(box.getItem() instanceof ItemCsgoBox)) {
                return;
            }
            ResourceLocation boxId = ItemCsgoBox.getBoxId(box);
            BoxDefinition def = boxId == null ? null : BoxRegistry.get(boxId);
            if (def == null || def.isTerminal()) {
                return;
            }

            DeployerBlockEntity deployer = event.getBlockEntity();
            Level level = deployer == null ? null : deployer.getLevel();
            DeployerFakePlayer fp = deployer == null ? null : deployer.getPlayer();
            if (level == null || fp == null) {
                return;
            }
            if (!DeployerBoxOpenRecipe.keyMatches(held, def)) {
                return;
            }

            // Per-deployer identity: cooldown / per-player caps / pity streaks
            // must not be shared by every deployer on the server.
            String trackerKey = "fake:" + level.dimension().location() + ":" + deployer.getBlockPos();

            // Cheap pre-checks so a capped/cooling-down machine does not even
            // offer a recipe (the executor re-checks authoritatively).
            if (!BoxConstraintTracker.underPerPlayerCap(
                    trackerKey, boxId.toString(), def.maxPerPlayer())) {
                return;
            }
            if (!BoxConstraintTracker.cooldownElapsed(
                    trackerKey, boxId.toString(), def.cooldownSeconds(), level.getGameTime())) {
                return;
            }
            if (!def.permission().isBlank() && !CsgoBox.PERMISSION_GATE.test(fp, def.permission())) {
                return;
            }

            DeployerBoxOpenRecipe recipe = DeployerBoxOpenRecipe.newInstance();
            // Lazy: the executor runs only when Create commits the recipe
            // (roll + constraints + events + achievements), keeping the roll
            // and the platform replacement atomic from the player's view.
            recipe.enforceNextResult(() -> {
                BoxOpenExecutor.Outcome outcome =
                        BoxOpenExecutor.execute(fp, box, false, false, trackerKey, false);
                return outcome == null ? ItemStack.EMPTY : outcome.giveItem();
            });

            event.addRecipe(() -> Optional.of(new RecipeHolder<>(
                    ResourceLocation.fromNamespaceAndPath(CsgoBox.MODID, "deployer_box_open"),
                    recipe)), PRIORITY);
            CsgoBox.LOGGER.debug("[csgobox-create] offered deployer recipe for {} (key={})",
                    boxId, held.isEmpty() ? "<empty hand>" : held);
        } catch (Throwable t) {
            // Optional-dependency discipline: never break Create's tick loop.
            CsgoBox.LOGGER.debug("[csgobox-create] deployer recipe search failed", t);
        }
    }
}