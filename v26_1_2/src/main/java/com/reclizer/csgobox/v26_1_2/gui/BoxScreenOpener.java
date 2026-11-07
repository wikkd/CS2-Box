package com.reclizer.csgobox.v26_1_2.gui;

import com.reclizer.csgobox.v26_1_2.item.ItemCsgoBox;
import com.reclizer.csgobox.v26_1_2.box.BoxDefinition;
import com.reclizer.csgobox.v26_1_2.sounds.ModSounds;
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
        float vol = 1.0F;
        if (vol > 0) {
            java.util.Optional<net.minecraft.sounds.SoundEvent> defSoundOpt = ItemCsgoBox.getDefinition(stack)
                    .flatMap(BoxDefinition::sound)
                    .flatMap(rl -> net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.get(rl))
                    .map(net.minecraft.core.Holder::value);
            if (defSoundOpt.isPresent()) {
                mc.player.playSound(defSoundOpt.get(), vol * 10F, 1F);
            } else {
            mc.player.playSound(ModSounds.CS_OPEN.get(), vol * 10F, 1F);
            }
        }
        boolean shift = mc.options.keyShift.isDown();
        mc.execute(() -> {
            if (shift && ItemCsgoBox.getDefinition(stack).map(d -> d.allowBulk()).orElse(true)) {
                mc.setScreen(new CsboxBulkOverviewScreen());
            } else {
                mc.setScreen(new CsboxScreen());
            }
        });
    }

    /** The terminal opens its boot screen instead of the classic crate UI. */
    public static void openTerminal(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            if (mc.player != null) {
                float vol = 1.0F;
                if (vol > 0) {
                    mc.player.playSound(ModSounds.TERMINAL_OPEN_UI.get(), vol * 10F, 1F);
                }
            }
            mc.execute(() -> mc.setScreen(new TerminalBootScreen(stack.copy())));
        }
    }
}
