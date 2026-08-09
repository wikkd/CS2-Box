package com.reclizer.csgobox.v26_1_2;

import com.reclizer.csgobox.v26_1_2.item.ModItems;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Minimal platform-layer test harness (JUnit 5).
 *
 * Verifies the platform entry class is present and loadable without running
 * the Minecraft runtime: Class.forName with initialize=false only resolves the
 * binary name and never triggers the static initializer (which touches
 * NeoForge classes). Guards against mirror overwrites / package renames that
 * silently break a platform module while compileJava still passes.
 */
class PlatformSmokeTest {

    private static final String ENTRY_CLASS = "com.reclizer.csgobox.v26_1_2.CsgoBox";

    @Test
    void platformEntryClassIsLoadable() throws ClassNotFoundException {
        Class<?> entry = Class.forName(ENTRY_CLASS, false, PlatformSmokeTest.class.getClassLoader());
        assertNotNull(entry, ENTRY_CLASS + " should be on the test classpath");
        assertEquals(ENTRY_CLASS, entry.getName());
    }

    @Test
    void expectedPlatformClassesArePresent() throws ClassNotFoundException {
        assertNotNull(Class.forName("com.reclizer.csgobox.v26_1_2.item.ModItems", false,
                PlatformSmokeTest.class.getClassLoader()));
        assertNotNull(Class.forName("com.reclizer.csgobox.v26_1_2.packet.PacketCsgoProgress", false,
                PlatformSmokeTest.class.getClassLoader()));
        assertNotNull(Class.forName("com.reclizer.csgobox.v26_1_2.gui.CsboxScreen", false,
                PlatformSmokeTest.class.getClassLoader()));
    }

    @Test
    void armoryPointItemIsDeclared() throws NoSuchFieldException {
        assertNotNull(ModItems.class.getDeclaredField("ITEM_ARMORY_POINT"));
    }
}
