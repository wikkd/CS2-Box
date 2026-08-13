package com.reclizer.csgobox.v1_21_1.item;

import com.reclizer.csgobox.v1_21_1.gui.BoxScreenOpener;
import net.minecraft.core.component.DataComponents;
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

    public ItemTerminal() {
        super(1);
    }

    /**
     * Hard guarantee that terminals are unstackable no matter how the stack
     * was created: every default instance is stamped MAX_STACK_SIZE=1, so a
     * terminal can never group up (one uid/lock per terminal).
     */
    @Override
    public ItemStack getDefaultInstance() {
        ItemStack stack = super.getDefaultInstance();
        stack.set(DataComponents.MAX_STACK_SIZE, 1);
        return stack;
    }

    /** The terminal opens its boot screen instead of the classic crate UI. */
    @Override
    public void openScreen(ItemStack stack) {
        BoxScreenOpener.openTerminal(stack);
    }
}
