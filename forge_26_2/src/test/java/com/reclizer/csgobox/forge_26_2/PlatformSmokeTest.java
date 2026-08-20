package com.reclizer.csgobox.forge_26_2;

import com.reclizer.csgobox.forge_26_2.item.ModItems;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Minimal platform-layer test harness (JUnit 5), mirroring the v26_1_2 module.
 *
 * <p>Verifies the forge platform entry class is present and loadable without
 * running the Minecraft runtime: {@code Class.forName} with initialize=false
 * only resolves the binary name and never triggers the static initializer
 * (which touches Forge classes). Guards against mirror overwrites, package
 * renames, and version-drift that silently break the module while
 * compileJava still passes.</p>
 *
 * <p>Sync note: this module was brought up to the 1.0.7 line (terminal /
 * armory-point items), so those item fields are now ASSERTED present, not
 * asserted absent, as the old 1.0.6 baseline guard did.</p>
 */
class PlatformSmokeTest {

    private static final String ENTRY_CLASS = "com.reclizer.csgobox.forge_26_2.CsgoBox";

    @Test
    void platformEntryClassIsLoadable() throws ClassNotFoundException {
        Class<?> entry = Class.forName(ENTRY_CLASS, false, PlatformSmokeTest.class.getClassLoader());
        assertNotNull(entry, ENTRY_CLASS + " should be on the test classpath");
        assertEquals(ENTRY_CLASS, entry.getName());
    }

    @Test
    void expectedPlatformClassesArePresent() throws ClassNotFoundException {
        assertNotNull(Class.forName("com.reclizer.csgobox.forge_26_2.item.ModItems", false,
                PlatformSmokeTest.class.getClassLoader()));
        assertNotNull(Class.forName("com.reclizer.csgobox.forge_26_2.packet.Networking", false,
                PlatformSmokeTest.class.getClassLoader()));
        assertNotNull(Class.forName("com.reclizer.csgobox.forge_26_2.packet.PacketCsgoProgress", false,
                PlatformSmokeTest.class.getClassLoader()));
        assertNotNull(Class.forName("com.reclizer.csgobox.forge_26_2.gui.CsboxScreen", false,
                PlatformSmokeTest.class.getClassLoader()));
        assertNotNull(Class.forName("com.reclizer.csgobox.forge_26_2.config.CsboxConfig", false,
                PlatformSmokeTest.class.getClassLoader()));
    }

    @Test
    void v107ItemsAreDeclared() throws NoSuchFieldException {
        assertNotNull(ModItems.class.getDeclaredField("ITEM_CSGOBOX"));
        assertNotNull(ModItems.class.getDeclaredField("ITEM_CSGO_KEY0"));
        assertNotNull(ModItems.class.getDeclaredField("ITEM_CSGO_KEY1"));
        assertNotNull(ModItems.class.getDeclaredField("ITEM_CSGO_KEY2"));
        assertNotNull(ModItems.class.getDeclaredField("ITEM_CSGO_KEY3"));
        // 1.0.7-line statically-registered items.
        assertNotNull(ModItems.class.getDeclaredField("ITEM_ARMORY_POINT"));
    }

    /**
     * The village-exclusive premium box was permanently removed on
     * 2026-08-19; the field must never come back.
     */
    @Test
    void premiumBoxItemIsPermanentlyRemoved() {
        assertThrows(NoSuchFieldException.class,
                () -> ModItems.class.getDeclaredField("ITEM_PREMIUM_BOX"));
    }
}