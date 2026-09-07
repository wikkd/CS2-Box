package com.reclizer.csgobox.config;

/**
 * Single source of truth for every {@code CsboxConfig} default value and its
 * legal range, shared by all platform builders (NeoForge
 * {@code ModConfigSpec} and Forge {@code ForgeConfigSpec}).
 *
 * <p>Enum defaults are stored as their constant names because the enum types
 * themselves are declared per platform; builders resolve them with
 * {@code valueOf}. Values here must match the behaviour documented in
 * {@code docs/CONFIGURATION.md}.</p>
 */
public final class CsboxConfigDefaults {

    /** Sentinel upper bound meaning "no limit" for unbounded config ranges. */
    public static final int NO_UPPER_BOUND = Integer.MAX_VALUE;

    // --- general ---

    /** Global drop rate multiplier in percent; 0 = off, no upper bound. */
    public static final int GLOBAL_DROP_RATE_PERCENT = 100;
    public static final int GLOBAL_DROP_RATE_PERCENT_MIN = 0;

    // --- advanced ---

    /** Auto-load box definitions from config/csbox/*.json on startup. */
    public static final boolean LOAD_DEFAULT_BOXES = true;
    /** Achievements off still accumulate stats. */
    public static final boolean ENABLE_ACHIEVEMENTS = true;
    /** Watch config/csbox/*.json and auto-reload on file changes (300ms debounce). */
    public static final boolean ENABLE_HOT_RELOAD = true;
    /** Max boxes per bulk open; 0 = unlimited. Server-authoritative. */
    public static final int BULK_OPEN_COUNT = 0;
    public static final int BULK_OPEN_COUNT_MIN = 0;
    /** Who sees JSON load errors in chat on join: OP_ONLY or EVERYONE. */
    public static final String JSON_ERROR_AUDIENCE = "OP_ONLY";
    /** Drawn durable items lose durability proportional to their wear value. */
    public static final boolean DAMAGE_ITEM_BY_WEAR = true;

    private CsboxConfigDefaults() {
    }
}
