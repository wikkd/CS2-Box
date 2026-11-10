package com.reclizer.csgobox.config;

/**
 * Who sees JSON load errors announced in chat when a player joins. Hoisted
 * from the per-platform {@code CsboxConfig} nested enums into common so the
 * shared Cloth Config screen ({@code mcshared/gui/config}) and every
 * platform config spec can reference a single type.
 */
public enum ErrorChatAudience {
    /** Only operators (permission level 2+) see the announcements. */
    OP_ONLY,
    /** Every player sees the announcements. */
    EVERYONE
}
