package com.reclizer.csgobox.forge_1_20_1.create;

import com.reclizer.csgobox.forge_1_20_1.CsgoBox;
import com.reclizer.csgobox.forge_1_20_1.box.BoxDefinition;
import com.reclizer.csgobox.forge_1_20_1.box.BoxRegistry;
import com.reclizer.csgobox.forge_1_20_1.item.ItemCsgoBox;
import com.reclizer.csgobox.forge_1_20_1.packet.BoxOpenExecutor;
import com.reclizer.csgobox.logic.BoxConstraintTracker;
import com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity;
import com.simibubi.create.content.kinetics.deployer.DeployerFakePlayer;
import com.simibubi.create.content.kinetics.deployer.DeployerRecipeSearchEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.wrapper.RecipeWrapper;

import java.util.Optional;

/**
 * Mechanical-deployer box opening through Create's official recipe search hook
 * (forge_1_20_1, Create 6.0.8).
 *
 * <p>When a deployer points at a crate riding a belt/depot, Create builds a
 * two-slot {@link RecipeWrapper} (slot 0 = platform crate, slot 1 = deployer's
 * held item), posts {@link DeployerRecipeSearchEvent} and asks every source
 * for a recipe (its own sources use priority 100/50). We answer with a
 * {@link DeployerBoxOpenRecipe} at a much higher priority, whose output is
 * injected lazily through {@code enforceNextResult} — so the crate is rolled
 * exactly once, at the moment Create actually commits the recipe. Create then
 * consumes the held key and replaces the platform crate with the prize.</p>
 *
 * <p>The roll still runs through {@link BoxOpenExecutor} (constraints,
 * cooldown, permission gate, pity, events, achievements) with the deployer's
 * fake player, keyed per deployer. Key consumption is disabled in the
 * executor — Create owns the held item.</p>
 */
@Mod.EventBusSubscriber(modid = CsgoBox.MODID)
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

            // 1.20.1 carries bare Recipe values (no RecipeHolder wrapper).
            event.addRecipe(() -> Optional.of(recipe), PRIORITY);
            CsgoBox.LOGGER.debug("[csgobox-create] offered deployer recipe for {} (key={})",
                    boxId, held.isEmpty() ? "<empty hand>" : held);
        } catch (Throwable t) {
            // Optional-dependency discipline: never break Create's tick loop.
            CsgoBox.LOGGER.debug("[csgobox-create] deployer recipe search failed", t);
        }
    }
}