package com.reclizer.csgobox.fabric_1_21_1;

import com.reclizer.csgobox.fabric_1_21_1.item.ModItems;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Minimal platform smoke test (mirrors the other platforms' PlatformSmokeTest):
 * verifies the entry classes load and the permanently-removed premium box item
 * never comes back. No MC runtime is initialized.
 */
class PlatformSmokeTest {

    @Test
    void entryClassesLoad() {
        assertDoesNotThrow(() -> Class.forName("com.reclizer.csgobox.fabric_1_21_1.CsgoBox"));
        assertDoesNotThrow(() -> Class.forName("com.reclizer.csgobox.fabric_1_21_1.CsgoBoxClient"));
    }

    @Test
    void networkingExposesPayloadWiring() {
        assertDoesNotThrow(() -> Class.forName("com.reclizer.csgobox.fabric_1_21_1.net.Networking"));
    }

    @Test
    void premiumBoxItemIsPermanentlyRemoved() throws Exception {
        List<String> fieldNames = new ArrayList<>();
        for (Class<?> c = ModItems.class; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                fieldNames.add(field.getName());
            }
        }
        assertFalse(fieldNames.stream().anyMatch(n -> n.toLowerCase().contains("premium")),
                "premium_supply_box was permanently removed on 2026-08-19 and must never return");
        assertTrue(fieldNames.contains("ITEM_GUN_CRATE"), "fixed box items must stay registered");
        assertNotNull(fieldNames);
    }
}
