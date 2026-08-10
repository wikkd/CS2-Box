package com.reclizer.csgobox.terminal;

/**
 * Terminal-screen colour palette — HTML prototype {@code :root} CSS variables
 * plus the per-region hardcoded colours, as ARGB int constants.
 * Pure data: no MC imports (CONSTRAINT-001).
 */
public final class TerminalPalette {

    private TerminalPalette() {
    }

    // ---- stage chrome (HTML :root) ----
    /** Outside the frame. */
    public static final int OUTSIDE = 0xFF08090A;
    /** Panel frame. */
    public static final int FRAME = 0xFF4E481E;
    /** Title bar. */
    public static final int TOPBAR = 0xFF39444C;
    /** Close icon idle. */
    public static final int CLOSE = 0xFF46525A;
    public static final int CLOSE_HOVER = 0xFF5A6870;
    /** Panel title strip. */
    public static final int TITLE = 0xFF5C707E;

    // ---- body / dot grid ----
    public static final int BODY_TOP = 0xFF2B353D;
    public static final int BODY_BOTTOM = 0xFF222A30;
    public static final int BOTTOM_BAR = 0xFF222A30;
    /** Dot-grid point (alpha 22/255 white). */
    public static final int DOT = 0x16FFFFFF;

    // ---- text ----
    public static final int TEXT = 0xFFC9D2D6;
    public static final int TEXT_DIM = 0xFF8FA0AA;
    public static final int ICON_DIM = 0xFF8FA0AA;
    public static final int BATTERY = 0xFF3ECF6E;
    public static final int X = 0xFFE8ECEF;
    /** Dark text on the white final-offer card. */
    public static final int TEXT_WHITE_CARD = 0xFF17191C;
    public static final int TEXT_WHITE_CARD_DIM = 0xFF4A5157;
    /** Price green. */
    public static final int GREEN = 0xFF3ECF6E;
    /** Expired countdown. */
    public static final int COUNT_EXPIRED = 0xFFC96A5F;
    /** Countdown colon separators (dimmed). */
    public static final int COUNT_COLON = 0x8CCFD6DB;

    // ---- action bar (region 6) ----
    public static final int PILL_GREEN_BORDER = 0xFF4FA57D;
    public static final int PILL_GREEN_TEXT = 0xFF8CE0BA;
    public static final int PILL_GREEN_FILL = 0x33FFFFFF;
    public static final int PILL_GRAY_BORDER = 0xFF4A5D69;
    public static final int PILL_GRAY_TEXT = 0xFFC9D2D6;
    public static final int PILL_GRAY_FILL = 0x1FFFFFFF;
    public static final int BAR_WHITE = 0xFFD9E2E8;
    public static final int BAR_GRAY = 0xFF34404A;
    public static final int BLACK_BOX = 0xFF000000;
    public static final int WHITE = 0xFFFFFFFF;

    // ---- offer region (8) ----
    public static final int RARITY_LEFT = 0xFF35404A;
    public static final int RARITY_TEXT = 0xFFC9D2D6;
    public static final int RARITY_MILITARY = 0xFF8FA0AA;
    public static final int RARITY_RESTRICTED = 0xFF6B9AC4;
    public static final int RARITY_CLASSIFIED = 0xFFAC6BD4;
    public static final int RARITY_COVERT = 0xFFC4A06B;
    public static final int RARITY_GOLD = 0xFFD4C96B;
    public static final int META_DIM = 0xFF6C7A85;
    public static final int META_BOLD = 0xFFE4E9EC;
    /** Inspect capsule. */
    public static final int INSPECT_BG = 0xD9B9C2C9;
    public static final int INSPECT_HOVER = 0xFFDFE5E9;
    public static final int INSPECT_TEXT = 0xFF262C33;
    /** Wear tier tag + corner tab. */
    public static final int WEAR_BG = 0xD98F98A1;
    public static final int WEAR_TAB_BG = 0xFFCFD6DB;
    public static final int WEAR_TAB_TEXT = 0xFF20242A;

    // ---- chat (region 4/5) ----
    public static final int BUBBLE = 0xFF2B3138;
    public static final int BUBBLE_BORDER = 0xFF39444C;
    public static final int SYS_REFUSED = 0xFFC96A5F;
    public static final int SYS_REFUSED_DIM = 0xFFB4B4B8;
    public static final int HOLD_ACCEPT = 0xFF398A46;
    public static final int HOLD_REJECT = 0xFFB03434;

    // ---- tooltip / dropdown ----
    public static final int TOOLTIP_BG = 0xFF1B1F23;
    public static final int TOOLTIP_BORDER = 0xFF39444C;
    public static final int GLOW = 0x26FFFFFF;
    public static final int CAP_DIM = 0xFF8FA0AA;
    public static final int CAP_SELECTED = 0xFF3ECF6E;
    public static final int CHEVRON = 0xFF8FA0AA;
    /** Cap dropdown menu. */
    public static final int MENU_BG = 0xF221262A;
    public static final int MENU_BORDER = 0xFF39444C;
    public static final int MENU_OPT_HOVER = 0xFF2E353B;
    /** Wear bar edge ticks. */
    public static final int BAR_EDGE = 0xFF8FA0AA;

    // ---- offer card (left list) ----
    public static final int OFFER_CARD = 0xFF222A30;
    public static final int OFFER_CARD_BORDER = 0xFF39444C;
    public static final int OFFER_WHITE_CARD = 0xFFFFFFFF;
    public static final int SYS_FAILED = 0xFFC96A5F;
    public static final int WINDOW_DRAG = 0xFF3ECF6E;
    public static final int SYS_MUTED = 0xFFB4B4B8;
}
