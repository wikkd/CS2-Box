package com.reclizer.csgobox.forge_26_1_2;

import com.reclizer.csgobox.forge_26_1_2.item.ModItems;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
 * <p>Sync note: the module tracks the v26_1_2 baseline as of the 1.0.7 dev
 * line (terminal / premium box / armory-point items are now expected). The
 * declared-field assertions below guard against accidental field removal or
 * mirror overwrites that drop the synced registrations.</p>
 */
class PlatformSmokeTest {

    private static final String ENTRY_CLASS = "com.reclizer.csgobox.forge_26_1_2.CsgoBox";

    @Test
    void platformEntryClassIsLoadable() throws ClassNotFoundException {
        Class<?> entry = Class.forName(ENTRY_CLASS, false, PlatformSmokeTest.class.getClassLoader());
        assertNotNull(entry, ENTRY_CLASS + " should be on the test classpath");
        assertEquals(ENTRY_CLASS, entry.getName());
    }

    @Test
    void expectedPlatformClassesArePresent() throws ClassNotFoundException {
        assertNotNull(Class.forName("com.reclizer.csgobox.forge_26_1_2.item.ModItems", false,
                PlatformSmokeTest.class.getClassLoader()));
        assertNotNull(Class.forName("com.reclizer.csgobox.forge_26_1_2.packet.Networking", false,
                PlatformSmokeTest.class.getClassLoader()));
        assertNotNull(Class.forName("com.reclizer.csgobox.forge_26_1_2.packet.PacketCsgoProgress", false,
                PlatformSmokeTest.class.getClassLoader()));
        assertNotNull(Class.forName("com.reclizer.csgobox.forge_26_1_2.gui.CsboxScreen", false,
                PlatformSmokeTest.class.getClassLoader()));
        assertNotNull(Class.forName("com.reclizer.csgobox.forge_26_1_2.config.CsboxConfig", false,
                PlatformSmokeTest.class.getClassLoader()));
    }

    @Test
    void v106BaselineItemsAreDeclared() throws NoSuchFieldException {
        assertNotNull(ModItems.class.getDeclaredField("ITEM_CSGOBOX"));
        assertNotNull(ModItems.class.getDeclaredField("ITEM_CSGO_KEY0"));
        assertNotNull(ModItems.class.getDeclaredField("ITEM_CSGO_KEY1"));
        assertNotNull(ModItems.class.getDeclaredField("ITEM_CSGO_KEY2"));
        assertNotNull(ModItems.class.getDeclaredField("ITEM_CSGO_KEY3"));
    }

    @Test
    void v107TerminalItemsAreDeclared() throws NoSuchFieldException {
        assertNotNull(ModItems.class.getDeclaredField("ITEM_TERMINAL"));
        assertNotNull(ModItems.class.getDeclaredField("ITEM_PREMIUM_BOX"));
        assertNotNull(ModItems.class.getDeclaredField("ITEM_ARMORY_POINT"));
    }
}
