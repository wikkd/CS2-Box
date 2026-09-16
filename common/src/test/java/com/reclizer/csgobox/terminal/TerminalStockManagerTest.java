package com.reclizer.csgobox.terminal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link TerminalStockManager} (v2.0.1 terminal stock).
 */
final class TerminalStockManagerTest {

    @Test
    @DisplayName("unlimited stock (-1) is always available and consumes nothing")
    void unlimitedStock() {
        TerminalStockManager.reset();
        assertTrue(TerminalStockManager.available("box1", -1));
        assertEquals(-1, TerminalStockManager.remaining("box1", -1));
        assertTrue(TerminalStockManager.consume("box1", -1, 0));
        assertTrue(TerminalStockManager.available("box1", -1));
    }

    @Test
    @DisplayName("limited stock decrements on consume and refuses when exhausted")
    void limitedStockDecrements() {
        TerminalStockManager.reset();
        assertTrue(TerminalStockManager.available("box2", 2));
        assertTrue(TerminalStockManager.consume("box2", 2, 60));
        assertTrue(TerminalStockManager.consume("box2", 2, 60));
        assertEquals(0, TerminalStockManager.remaining("box2", 2));
        assertFalse(TerminalStockManager.available("box2", 2));
        assertFalse(TerminalStockManager.consume("box2", 2, 60));
    }

    @Test
    @DisplayName("restock timer refills after restockMinutes; 0 = never")
    void restockRefills() {
        TerminalStockManager.reset();
        TerminalStockManager.consume("box3", 1, 60);
        assertEquals(0, TerminalStockManager.remaining("box3", 1));
        // Before the timer: still empty.
        TerminalStockManager.tick(System.currentTimeMillis() + 59 * 60_000L);
        assertEquals(0, TerminalStockManager.remaining("box3", 1));
        // After 60 minutes: refilled.
        TerminalStockManager.tick(System.currentTimeMillis() + 61 * 60_000L);
        assertEquals(1, TerminalStockManager.remaining("box3", 1));
        assertTrue(TerminalStockManager.available("box3", 1));
    }

    @Test
    @DisplayName("restockMinutes=0 means never auto-restock after exhaustion")
    void noRestockNeverRefills() {
        TerminalStockManager.reset();
        TerminalStockManager.consume("box4", 1, 0);
        assertEquals(0, TerminalStockManager.remaining("box4", 1));
        TerminalStockManager.tick(System.currentTimeMillis() + 10_000 * 60_000L);
        assertEquals(0, TerminalStockManager.remaining("box4", 1));
    }

    @Test
    @DisplayName("reset clears all stock")
    void resetClears() {
        TerminalStockManager.reset();
        TerminalStockManager.consume("box5", 3, 0);
        assertEquals(2, TerminalStockManager.remaining("box5", 3));
        TerminalStockManager.reset();
        assertEquals(3, TerminalStockManager.remaining("box5", 3), "fresh after reset");
    }
}
