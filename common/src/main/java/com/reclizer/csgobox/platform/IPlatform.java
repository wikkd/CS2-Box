package com.reclizer.csgobox.platform;
public interface IPlatform {
    String mcVersion();
    IIdentifier parseId(String s);
    IRegistry registry();
    ITagParser tagParser();
    void spawnAtLocation(Object entity, Object itemStack);
    void logInfo(String message);
    void logDebug(String message);
    void logWarn(String message);
    void logError(String message, Throwable t);
}
