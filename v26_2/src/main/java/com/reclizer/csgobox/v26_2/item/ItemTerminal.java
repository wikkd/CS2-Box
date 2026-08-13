package com.reclizer.csgobox.v26_2.item;

import com.reclizer.csgobox.v26_2.gui.BoxScreenOpener;
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

    /**
     * Terminals are unstackable (one uid/lock per terminal). In 26.x
     * Properties.stacksTo() writes a MAX_STACK_SIZE component, and the
     * initializer chain runs values added earlier AFTER later ones — so any
     * stacksTo(N) already on {@code properties} overrides this stacksTo(1).
     * Callers must NOT pre-set stacksTo on the Properties they pass in.
     */
    public ItemTerminal(Properties properties) {
        super(properties, 1);
    }

    /**
     * Hard guarantee that terminals are unstackable no matter what the
     * Properties carried: every default instance is stamped MAX_STACK_SIZE=1
     * (the 26.x initializer chain lets a pre-set stacksTo(N) override the
     * constructor, so this is the final enforcement point).
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
