package com.reclizer.csgobox.v26_2.platform;

import com.reclizer.csgobox.platform.IIdentifier;
import com.reclizer.csgobox.platform.IPlatform;
import com.reclizer.csgobox.platform.IRegistry;
import com.reclizer.csgobox.platform.ITagParser;

public final class Platform26V2 implements IPlatform {
    @Override
    public String mcVersion() {
        return "26.2";
    }

    @Override
    public IIdentifier parseId(String s) {
        throw new UnsupportedOperationException("Stage 4");
    }

    @Override
    public IRegistry registry() {
        throw new UnsupportedOperationException("Stage 4");
    }

    @Override
    public ITagParser tagParser() {
        throw new UnsupportedOperationException("Stage 4");
    }

    @Override
    public void spawnAtLocation(Object entity, Object itemStack) {
        throw new UnsupportedOperationException("Stage 4");
    }

    @Override
    public void logInfo(String message) {
        System.out.println("[csgobox/26] " + message);
    }

    @Override
    public void logDebug(String message) {
        // noop
    }

    @Override
    public void logWarn(String message) {
        System.err.println("[csgobox/26 WARN] " + message);
    }

    @Override
    public void logError(String message, Throwable t) {
        System.err.println("[csgobox/26 ERROR] " + message);
        if (t != null) {
            t.printStackTrace();
        }
    }
}