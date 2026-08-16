package com.reclizer.csgobox.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link EntityChineseMap}.
 */
final class EntityChineseMapTest {

    @Test
    void knownVanillaEntityReturnsChinese() {
        assertEquals("僵尸", EntityChineseMap.getDisplayName("minecraft:zombie"));
        assertEquals("苦力怕", EntityChineseMap.getDisplayName("minecraft:creeper"));
        assertEquals("末影龙", EntityChineseMap.getDisplayName("minecraft:ender_dragon"));
        assertEquals("村民", EntityChineseMap.getDisplayName("minecraft:villager"));
    }

    @Test
    void unknownNamespacedEntityFallsBackToPath() {
        assertEquals("custom_thing", EntityChineseMap.getDisplayName("minecraft:custom_thing"));
        assertEquals("weird_entity", EntityChineseMap.getDisplayName("some_mod:weird_entity"));
    }

    @Test
    void noNamespaceReturnsIdAsIs() {
        assertEquals("plain_entity", EntityChineseMap.getDisplayName("plain_entity"));
    }

    @Test
    void displayNameFullKnownIncludesId() {
        String full = EntityChineseMap.getDisplayNameFull("minecraft:zombie");
        assertTrue(full.contains("僵尸"), "expected Chinese name in full, got: " + full);
        assertTrue(full.contains("minecraft:zombie"), "expected id in full, got: " + full);
    }

    @Test
    void displayNameFullUnknownReturnsId() {
        assertEquals("unknown_mod:xyz", EntityChineseMap.getDisplayNameFull("unknown_mod:xyz"));
        assertEquals("plain", EntityChineseMap.getDisplayNameFull("plain"));
    }
}
