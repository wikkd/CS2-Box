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
    public static final int CLOSE_HOVER = 0xFF5A6870;
    /** Panel title strip. */
    public static final int TITLE = 0xFF5C707E;
    /** Title strip gradient top (HTML .title-strip linear-gradient start). */
    public static final int TITLE_TOP = 0xFF66798A;
    /** Action bar background (HTML .action-bar). */
    public static final int ACTION_BG = 0xFF262C33;
    /** Action bar top row text (HTML .action-top #cfd6db). */
    public static final int ACTION_TEXT = 0xFFCFD6DB;
    /** Light body gradient (HTML .count-body/.xp-body/.right-body). */
    public static final int BODY_LIGHT_TOP = 0xFF3D4650;
    public static final int BODY_LIGHT_BOTTOM = 0xFF333B45;
    /** Offer-card thumbnail backdrop (HTML .offer-card .thumb). */
    public static final int THUMB_TOP = 0xFF1A1E23;
    public static final int THUMB_BOTTOM = 0xFF14171B;
    /** Offer-card head line (HTML .offer-head). */
    public static final int OFFER_HEAD = 0xFFE5C558;
    /** Meta row / wear text (HTML .r8-meta, .offer-wear #9aa4ad) — brightened
     *  after the visual audit reported ~1.5:1 contrast on the dark body. */
    public static final int META_TEXT = 0xFFAEB9C1;
    /** Offer-card item name (HTML .offer-name #f2f5f7). */
    public static final int CARD_NAME = 0xFFF2F5F7;
    /** Offer-card price line (HTML .offer-price #dfe5e9, non-final). */
    public static final int OFFER_PRICE = 0xFFDFE5E9;

    // ---- body / dot grid ----

    // ---- text ----
    public static final int TEXT = 0xFFC9D2D6;
    public static final int BATTERY = 0xFF3ECF6E;
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
    /** Accept pill (HTML .pill.accept border/text = --accept #398a46). */
    public static final int PILL_GREEN_BORDER = 0xFF398A46;
    public static final int PILL_GREEN_TEXT = 0xFF398A46;
    /** Reject pill (HTML .pill.reject border #7d858d, text #dfe5e9). */
    public static final int PILL_GRAY_BORDER = 0xFF7D858D;
    public static final int PILL_GRAY_TEXT = 0xFFDFE5E9;
    public static final int BAR_WHITE = 0xFFD9E2E8;
    /** Light-grey track behind the wear slider arrow (HTML .r8-bar track). */
    public static final int BAR_TRACK = 0xFF93A0AA;
    public static final int BLACK_BOX = 0xFF000000;
    public static final int WHITE = 0xFFFFFFFF;

    // ---- offer region (8) ----
    /** CS2-style 5-tier rarity colours (mil-spec blue .. contraband gold). */
    public static final int RARITY_MILITARY = 0xFF4B69FF;
    public static final int RARITY_RESTRICTED = 0xFF8847FF;
    public static final int RARITY_CLASSIFIED = 0xFFD32CE6;
    public static final int RARITY_COVERT = 0xFFEB4B4B;
    public static final int RARITY_GOLD = 0xFFE4AE39;

    /** Tier colour for a box grade (1..5), matching {@code ColorTools.colorItems}
     *  used by the box-opening screens (blue/purple/magenta/red/gold). */
    public static int rarityColorForGrade(int grade) {
        int idx = Math.max(0, Math.min(grade - 1, 4));
        return switch (idx) {
            case 0 -> RARITY_MILITARY;
            case 1 -> RARITY_RESTRICTED;
            case 2 -> RARITY_CLASSIFIED;
            case 3 -> RARITY_COVERT;
            default -> RARITY_GOLD;
        };
    }
    /** Inspect capsule — dark solid bg + light text (was pale grey + dark text,
     *  read as a disabled state by the vision audit). */
    public static final int INSPECT_BG = 0xFF33404A;
    public static final int INSPECT_HOVER = 0xFF46545F;
    public static final int INSPECT_TEXT = 0xFFE4E9EC;
    /** Wear tier tag + corner tab — dark solid bg (was translucent pale grey). */
    public static final int WEAR_BG = 0xFF2A3138;

    // ---- chat (region 4/5) ----
    public static final int BUBBLE = 0xFF2B3138;
    public static final int HOLD_ACCEPT = 0xFF398A46;
    public static final int HOLD_REJECT = 0xFFB03434;

    // ---- tooltip / dropdown ----
    public static final int TOOLTIP_BG = 0xFF1B1F23;
    public static final int TOOLTIP_BORDER = 0xFF39444C;
    public static final int CAP_DIM = 0xFF8FA0AA;
    public static final int CAP_SELECTED = 0xFF3ECF6E;
    /** Cap dropdown menu. */
    public static final int MENU_BG = 0xFF0B0D0F;
    public static final int MENU_BORDER = 0xFF2C3238;
    public static final int MENU_OPT_HOVER = 0xFF2E353B;
    // ---- offer card (left list) ----
    public static final int OFFER_CARD = 0xFF222A30;
    public static final int OFFER_CARD_BORDER = 0xFF39444C;
    public static final int OFFER_WHITE_CARD = 0xFFFFFFFF;
    public static final int SYS_FAILED = 0xFFC96A5F;
    public static final int SYS_MUTED = 0xFF8F98A1;
}
