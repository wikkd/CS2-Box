package com.tiviacz.travelersbackpack.items;

import java.util.Objects;

import com.tiviacz.travelersbackpack.TravelersBackpack;
import com.tiviacz.travelersbackpack.compat.curios.TravelersBackpackCurio;
import com.tiviacz.travelersbackpack.inventory.BackpackWrapper;
import com.tiviacz.travelersbackpack.inventory.FluidTankItemWrapper;
import com.tiviacz.travelersbackpack.inventory.upgrades.tanks.TanksUpgrade;

import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;

/**
 * Drop-in replacement for the compiled anonymous class
 * {@code TravelersBackpackItem$2} (Travelers Backpack 9.1.58) — the
 * ICapabilityProvider attached to every backpack ItemStack.
 *
 * Behavioral clone of the original bytecode except for one guard: the curios
 * branch calls {@code TravelersBackpack.enableCurios()}, which reads the
 * SERVER config ({@code backpackSettings.backSlotIntegration}). SERVER specs
 * do not load on a client until a world exists, so any capability query
 * during mod loading trips Forge's dev-only strict check
 * ("Cannot get config value before config is loaded"). Epic Fight 20.15.2
 * scans item capabilities inside its ClientConfig load handler, so the FG7
 * dev client hard-crashed at boot (crash reports 2026-11-18_10.26 and
 * 2026-11-20_16.09). Production Forge never throws there (its own fallback
 * reads the default value), so the try/catch is a no-op outside dev and
 * "curios disabled until configured" inside dev — identical to what players
 * see in production.
 *
 * Compiled by scripts/patch-tb-capability-guard-1201.py against the TB jar
 * itself + the mavenizer recompiled forge jar, then spliced into
 * travelersbackpack-*-official.jar (remap pipeline hooks this after remap).
 */
class TravelersBackpackItem$2 implements ICapabilityProvider {

    final ItemStack val$stack;

    final TravelersBackpackItem this$0;

    TravelersBackpackItem$2(TravelersBackpackItem outer, ItemStack stack) {
        this.this$0 = outer;
        this.val$stack = stack;
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            BackpackWrapper wrapper = Objects.requireNonNull(BackpackWrapper.fromStack(val$stack));
            return LazyOptional.of(() -> wrapper).cast();
        }
        if (cap == ForgeCapabilities.FLUID_HANDLER_ITEM) {
            BackpackWrapper wrapper = BackpackWrapper.fromStack(val$stack);
            if (wrapper.getUpgradeManager().getUpgrade(TanksUpgrade.class).isPresent()) {
                FluidTankItemWrapper fluid = new FluidTankItemWrapper(val$stack,
                        wrapper.getUpgradeManager().getUpgrade(TanksUpgrade.class).get());
                return LazyOptional.of(() -> fluid).cast();
            }
        }
        if (curiosEnabledGuarded()) {
            return TravelersBackpackCurio.getCurioCapability(cap, val$stack);
        }
        return LazyOptional.empty();
    }

    private static boolean curiosEnabledGuarded() {
        try {
            return TravelersBackpack.enableCurios();
        } catch (IllegalStateException e) {
            // Forge dev-only strictness: SERVER spec not loaded yet on the
            // client (no world). Match Forge's own production fallback.
            return false;
        }
    }
}
