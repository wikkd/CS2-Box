package com.reclizer.csgobox.config;

/**
 * Loader-neutral read/write view over the per-platform {@code CsboxConfig}
 * bindings, consumed by the shared Cloth Config screen in
 * {@code mcshared/gui/config}. Platform config classes implement this
 * directly: the method names match their existing getters and the
 * {@code set*} writers introduced for the Cloth GUI, so implementing the
 * interface adds no new members. Saving stays platform-side (spec
 * {@code save()} on NeoForge/Forge, JSON write on Fabric) and is injected
 * separately.
 */
public interface CsboxConfigAccess {

    // --- general ---

    /** Global drop rate multiplier in percent. */
    int globalDropRatePercent();

    void setGlobalDropRatePercent(int value);

    // --- advanced ---

    boolean loadDefaultBoxes();

    void setLoadDefaultBoxes(boolean value);

    boolean enableAchievements();

    void setEnableAchievements(boolean value);

    boolean enableHotReload();

    void setEnableHotReload(boolean value);

    /** Max boxes per bulk open; 0 = unlimited. Server-authoritative. */
    int bulkOpenCount();

    void setBulkOpenCount(int value);

    ErrorChatAudience jsonErrorAudience();

    void setJsonErrorAudience(ErrorChatAudience value);

    boolean damageItemByWear();

    void setDamageItemByWear(boolean value);
}
