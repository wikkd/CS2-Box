package com.reclizer.csgobox.forge_26_1_2.item;

import com.reclizer.csgobox.forge_26_1_2.gui.TerminalBootScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

/**
 * Terminal device item — a box-type item that opens the terminal-style UI
 * ({@code gui.TerminalScreen}) instead of the classic crate screen.
 *
 * <p>Extends {@link ItemCsgoBox} so the whole server-authoritative open
 * pipeline (box_id component resolution, key consumption, server RNG, stats,
 * {@code BoxOpenedEvent}) accepts it unchanged: the server only checks
 * {@code instanceof ItemCsgoBox}. The bound box definition is read from the
 * same {@code csgobox:box_id} component as regular boxes.
 */
public class ItemTerminal extends ItemCsgoBox {

    public ItemTerminal(Properties properties) {
        super(properties, 1);
    }

    /** The terminal opens its boot screen instead of the classic crate UI. */
    @Override
    public void openScreen(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.execute(() -> mc.setScreen(new TerminalBootScreen(stack.copy())));
        }
    }
}
