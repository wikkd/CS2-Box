package com.reclizer.csgobox.v1_21_1.item;

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
        super();
    }
}
