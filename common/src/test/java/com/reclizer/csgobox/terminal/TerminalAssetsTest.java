package com.reclizer.csgobox.terminal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Guards the pre-baked terminal assets (Stage 0): every texture the screens
 * blit must exist in the shared resources with a valid PNG header and the
 * expected pixel size.
 */
final class TerminalAssetsTest {

    private static final String DIR = "/assets/csgobox/textures/gui/terminal/";

    

    @Test
    @DisplayName("all terminal textures exist with valid PNG headers and sizes")
    void assetsPresent() throws IOException {
        for (String[] a : ASSETS) {
            String name = a[0];
            int w = Integer.parseInt(a[1]);
            int h = Integer.parseInt(a[2]);
            try (InputStream in = getClass().getResourceAsStream(DIR + name)) {
                assertNotNull(in, "missing asset: " + name);
                byte[] head = in.readNBytes(24);
                assertEquals(0x89, head[0] & 0xFF, name + " PNG magic 1");
                assertEquals('P', head[1], name + " PNG magic 2");
                assertEquals('N', head[2], name + " PNG magic 3");
                assertEquals('G', head[3], name + " PNG magic 4");
                // IHDR: bytes 16..19 = width (BE), 20..23 = height (BE)
                int pw = ((head[16] & 0xFF) << 24) | ((head[17] & 0xFF) << 16)
                        | ((head[18] & 0xFF) << 8) | (head[19] & 0xFF);
                int ph = ((head[20] & 0xFF) << 24) | ((head[21] & 0xFF) << 16)
                        | ((head[22] & 0xFF) << 8) | (head[23] & 0xFF);
                assertEquals(w, pw, name + " width");
                assertEquals(h, ph, name + " height");
            }
        }
    }
}
