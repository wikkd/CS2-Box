package com.reclizer.csgobox.forge_1_18_2.gui;

import com.reclizer.csgobox.forge_1_18_2.item.ItemCsgoBox;
import com.reclizer.csgobox.forge_1_18_2.box.BoxDefinition;
import com.reclizer.csgobox.forge_1_18_2.sounds.ModSounds;
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
        float vol = 1.0F;
        if (vol > 0) {
            var defSoundOpt = ItemCsgoBox.getDefinition(stack).flatMap(BoxDefinition::sound)
                    .flatMap(rl -> java.util.Optional.ofNullable(net.minecraftforge.registries.ForgeRegistries.SOUND_EVENTS.getValue(rl)));
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
