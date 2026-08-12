package com.reclizer.csgobox.v26_2.gui;

import com.reclizer.csgobox.v26_2.CsgoBox;
import com.reclizer.csgobox.v26_2.sounds.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

/**
 * Client-only screen openers for box items. Lives here (not on the item
 * classes) so a dedicated server never loads client GUI classes: the JVM
 * only resolves this class when {@code openScreen} is actually invoked,
 * which only ever happens from the client {@code ClickEvent}.
 */
public final class BoxScreenOpener {
    private BoxScreenOpener() {
    }

    /** Plays the open sound and shows the classic crate UI (Shift → bulk overview). */
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
                mc.setScreenAndShow(new CsboxBulkOverviewScreen());
            } else {
                mc.setScreenAndShow(new CsboxScreen());
            }
        });
    }

    /** The terminal opens its boot screen instead of the classic crate UI. */
    public static void openTerminal(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.execute(() -> mc.setScreenAndShow(new TerminalBootScreen(stack.copy())));
        }
    }
}
