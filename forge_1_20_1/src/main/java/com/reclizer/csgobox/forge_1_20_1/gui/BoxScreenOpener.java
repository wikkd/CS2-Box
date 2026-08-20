package com.reclizer.csgobox.forge_1_20_1.gui;

import com.reclizer.csgobox.forge_1_20_1.CsgoBox;
import com.reclizer.csgobox.forge_1_20_1.sounds.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

public final class BoxScreenOpener {
    private BoxScreenOpener() {
    }

    public static void openClassic(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) {
            return;
        }
        float vol = CsgoBox.CONFIG.openSoundVolume() / 100F;
        if (vol > 0) {
            mc.player.playSound(ModSounds.CS_OPEN.get(), vol * 10F, 1F);
        }
        boolean shift = mc.options.keyShift.isDown();
        mc.execute(() -> {
            if (shift) {
                mc.setScreen(new CsboxBulkOverviewScreen());
            } else {
                mc.setScreen(new CsboxScreen());
            }
        });
    }

    public static void openTerminal(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.execute(() -> mc.setScreen(new TerminalBootScreen(stack.copy())));
        }
    }
}
