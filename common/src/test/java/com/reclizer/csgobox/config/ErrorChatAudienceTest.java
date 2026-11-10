package com.reclizer.csgobox.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Guards the shared {@link ErrorChatAudience} enum against constant-name
 * drift: {@link CsboxConfigDefaults#JSON_ERROR_AUDIENCE} stores a constant
 * name that platform config builders and the shared Cloth Config screen
 * resolve with {@code valueOf}; a renamed constant would break every
 * platform's config load at runtime.
 */
final class ErrorChatAudienceTest {

    @Test
    void defaultConstantResolves() {
        assertSame(ErrorChatAudience.OP_ONLY,
                ErrorChatAudience.valueOf(CsboxConfigDefaults.JSON_ERROR_AUDIENCE));
    }

    @Test
    void unknownNameRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ErrorChatAudience.valueOf("NOT_A_CONSTANT"));
    }
}
