package com.reclizer.csgobox.forge_1_20_1.create;

import com.reclizer.csgobox.forge_1_20_1.CsgoBox;
import com.reclizer.csgobox.forge_1_20_1.box.BoxDefinition;
import com.reclizer.csgobox.forge_1_20_1.box.BoxRegistry;
import com.reclizer.csgobox.forge_1_20_1.item.ItemCsgoBox;
import com.reclizer.csgobox.forge_1_20_1.packet.BoxOpenExecutor;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * v2.2.0 Create deployer compat: a mechanical deployer holding the crate's
 * configured key, pointing sideways at a Create belt/depot carrying a box
 * item, opens the crate with the exact same server-authoritative pipeline as a
 * manual open (constraints, pity, {@code BoxOpeningEvent}/{@code BoxOpenedEvent}).
 *
 * <p>Mechanics: Create's deployer simulates a right click through a fake
 * player ({@code DeployerHandler -> RightClickBlock}); this subscriber sees the
 * event, reads the item riding the platform through Create's stable public
 * transport behaviour (reflection, no compile dependency), matches the key in
 * the deployer's hand against the box definition, then runs
 * {@link BoxOpenExecutor}.</p>
 *
 * <ul>
 *   <li>Deployer (fake player): the prize <b>replaces the crate on the
 *       platform</b> so belts/arms carry it away; the key is consumed from the
 *       deployer (Create syncs the fake player's hand back into the
 *       deployer's held item).</li>
 *   <li>Real player: the prize goes into the player's inventory and the crate
 *       is consumed from the platform.</li>
 * </ul>
 *
 * <p>Place the deployer pointing <b>sideways</b> at the platform: Create
 * reserves the straight-down direction over platforms for its own belt
 * processing and never activates there.</p>
 */
@Mod.EventBusSubscriber(modid = CsgoBox.MODID)
public final class DeployerBoxOpen {

    private DeployerBoxOpen() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Level level = event.getLevel();
        if (level.isClientSide) {
            return;
        }
        BlockPos pos = event.getPos();
        if (!isCreatePlatform(level, pos)) {
            return;
        }

        final boolean[] handled = {false};
        CreatePlatformItems.process(level, pos, stack -> {
            if (handled[0]) {
                return CreatePlatformItems.ProcessResult.none();
            }
            if (!(stack.getItem() instanceof ItemCsgoBox)) {
                return CreatePlatformItems.ProcessResult.none();
            }
            ResourceLocation boxId = ItemCsgoBox.getBoxId(stack);
            BoxDefinition def = boxId == null ? null : BoxRegistry.get(boxId);
            if (def == null || def.isTerminal()) {
                return CreatePlatformItems.ProcessResult.none();
            }

            boolean byDeployer = player instanceof FakePlayer;
            if (!shouldOpen(player, def, byDeployer)) {
                return CreatePlatformItems.ProcessResult.none();
            }
            // Deployer: prize replaces the crate on the platform; the caller
            // consumes the platform item itself (consumeBox=false). Real
            // player: crate is removed from the platform and the prize lands
            // in the inventory (consumeBox=false too — never shrink the
            // platform copy).
            // Deployers share one FakePlayer UUID per loader; pass an explicit
            // per-deployer identity so cooldown / per-player caps / pity
            // streaks are not shared by every deployer on the server.
            // (v2.2.0-fix)
            String trackerKey = player instanceof FakePlayer
                    ? "fake:" + level.dimension().location() + ":" + pos
                    : null;
            BoxOpenExecutor.Outcome outcome = BoxOpenExecutor.execute(player, stack, !byDeployer, false, trackerKey);
            if (outcome == null) {
                return CreatePlatformItems.ProcessResult.none();
            }
            handled[0] = true;
            return byDeployer
                    ? CreatePlatformItems.ProcessResult.replace(outcome.giveItem())
                    : CreatePlatformItems.ProcessResult.remove();
        });

        if (handled[0]) {
            // Swallow the rest of Create's right-click pipeline (and any other
            // handler) — the crate is already opened.
            event.setCanceled(true);
            event.setUseBlock(Event.Result.DENY);
            event.setUseItem(Event.Result.DENY);
        }
    }

    /** Only belt and depot are supported platforms (both expose the transport behaviour). */
    static boolean isCreatePlatform(Level level, BlockPos pos) {
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(level.getBlockState(pos).getBlock());
        if (id == null || !"create".equals(id.getNamespace())) {
            return false;
        }
        String path = id.getPath();
        return "belt".equals(path) || "depot".equals(path);
    }

    /**
     * Deployers (fake players) may open a no-key box with an empty hand and a
     * keyed box with the matching key. Real players must hold the key and are
     * deliberately left out of no-key boxes so Create's native pickup
     * interaction on depots keeps working.
     */
    private static boolean shouldOpen(Player player, BoxDefinition def, boolean byDeployer) {
        ResourceLocation key = def.keyItem();
        if (key == null || "minecraft:air".equals(key.toString())) {
            return byDeployer;
        }
        ItemStack hand = player.getMainHandItem();
        return !hand.isEmpty() && key.equals(ForgeRegistries.ITEMS.getKey(hand.getItem()));
    }
}