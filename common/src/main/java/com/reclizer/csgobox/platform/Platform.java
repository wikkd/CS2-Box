package com.reclizer.csgobox.platform;
public final class Platform {
    private static volatile IPlatform instance;
    private Platform() {}
    public static void set(IPlatform platform) { instance = platform; }
    public static IPlatform get() {
        IPlatform p = instance;
        if (p == null) {
            throw new IllegalStateException("Platform not initialized — call Platform.set(new PlatformImpl()) from your @Mod entry.");
        }
        return p;
    }
    public static boolean isInitialized() { return instance != null; }
}
