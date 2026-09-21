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
        assertTrue(TerminalStockManager.consume("box1", -1));
        assertTrue(TerminalStockManager.available("box1", -1));
    }

    @Test
    @DisplayName("limited stock decrements on consume and refuses when exhausted")
    void limitedStockDecrements() {
        TerminalStockManager.reset();
        assertTrue(TerminalStockManager.available("box2", 2));
        assertTrue(TerminalStockManager.consume("box2", 2));
        assertTrue(TerminalStockManager.consume("box2", 2));
        assertEquals(0, TerminalStockManager.remaining("box2", 2));
        assertFalse(TerminalStockManager.available("box2", 2));
        assertFalse(TerminalStockManager.consume("box2", 2));
    }

    @Test
    @DisplayName("an exhausted terminal stays empty until reset (no auto-restock)")
    void noAutoRestock() {
        TerminalStockManager.reset();
        TerminalStockManager.consume("box4", 1);
        assertEquals(0, TerminalStockManager.remaining("box4", 1));
        assertEquals(0, TerminalStockManager.remaining("box4", 1), "still empty over time");
    }

    @Test
    @DisplayName("reset clears all stock")
    void resetClears() {
        TerminalStockManager.reset();
        TerminalStockManager.consume("box5", 3);
        assertEquals(2, TerminalStockManager.remaining("box5", 3));
        TerminalStockManager.reset();
        assertEquals(3, TerminalStockManager.remaining("box5", 3), "fresh after reset");
    }
}
