package com.reclizer.csgobox.forge_1_20_1;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlatformSmokeTest {
    @Test
    void entryClassLoadable() throws ClassNotFoundException {
        Class.forName("com.reclizer.csgobox.forge_1_20_1.CsgoBox");
    }
}
