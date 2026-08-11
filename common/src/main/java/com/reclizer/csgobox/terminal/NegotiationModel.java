package com.reclizer.csgobox.terminal;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Terminal negotiation state machine (HTML prototype design/terminal-chat.html):
 * 5-round script with a dealer typing window, accept/reject busy locks and a
 * generation token so a restarted negotiation invalidates stale timings.
 *
 * <pre>
 *   IDLE --presentRound--> TYPING --(1100ms)--> PENDING
 *        PENDING --acceptNow--> ACCEPT_BUSY --(0ms)--> CLOSED (next tick)
 *        PENDING --rejectNow--> REJECT_BUSY --(450ms)--> round<5 ? TYPING(round+1) : FAILED
 * </pre>
 *
 * Pure Java (no MC imports — CONSTRAINT-001). Drive with {@link #tick(long)}
 * every frame using the client clock; state transitions take timestamps so
 * they are independent of tick rate.
 */
public final class NegotiationModel {

    public enum Status { IDLE, TYPING, PENDING, ACCEPT_BUSY, REJECT_BUSY, CLOSED, FAILED }

    /** A stable per-round offer (generated once when the round becomes PENDING). */
    public record Offer(int round, int skinIdx, float wearVal, int style, int no, int pattern,
                        boolean finalRound) {
    }

    /** Dealer chat line (bubble). */
    public record LineEntry(int round, String textKey, long atMs) {
    }

    /** System bubble (已接受 / 已拒绝 / 谈判破裂). */
    public record SystemEntry(String textKey, boolean failed, long atMs) {
    }

    /** Offer card appended when the round becomes PENDING. */
    public record OfferEntry(Offer offer, long atMs, int status) {
    }

    // ---- offer card status ----

    public static final int OFFER_PENDING = 0, OFFER_REJECTED = 1, OFFER_ACCEPTED = 2;

    // ---- script (HTML LINES / SKINS / ROUNDS) ----

    public static final int MAX_ROUNDS = 5;
    public static final String[] LINES = {
            "csgobox.terminal.line.0",
            "csgobox.terminal.line.1",
            "csgobox.terminal.line.2",
            "csgobox.terminal.line.3",
            "csgobox.terminal.line.4",
    };
    public static final int[] ROUND_LINE = {0, 1, 2, 3, 4};
    public static final int[] ROUND_SKIN = {0, 2, 1, 2, 0};

    public static final String[] SKIN_NAME_KEYS = {
            "csgobox.terminal.skin.0",
            "csgobox.terminal.skin.1",
            "csgobox.terminal.skin.2",
    };
    public static final String[] SKIN_WEAR_KEYS = {
            "gui.csgobox.csgo_box.wear_mw",
            "gui.csgobox.csgo_box.wear_ww",
            "gui.csgobox.csgo_box.wear_ft",
    };
    public static final float[] SKIN_WEAR_VAL = {0.11383486F, 0.40218743F, 0.30740085F};
    /** Offer price in whole Armory Points (no decimals — the mod's currency). */
    public static final String[] SKIN_PRICE = {"22", "16", "16"};
    /** Five rarity tier keys (grade 1..5, CS2-style): 军规级/受限级/保密级/隐秘级/违禁. */
    public static final String[] RARITY_TIER_KEYS = {
            "mil_spec", "restricted", "classified", "covert", "contraband"
    };
    /** Rarity tier key per script skin (skin0 -> grade4 covert, skin1/2 -> grade3 classified). */
    public static final String[] SKIN_RARITY = {"covert", "classified", "classified"};

    /** Rarity tier key for a box grade (1..5, clamped). */
    public static String rarityKeyForGrade(int grade) {
        return RARITY_TIER_KEYS[Math.max(0, Math.min(grade - 1, RARITY_TIER_KEYS.length - 1))];
    }
    /** weapon preset id: "pistol" | "rifle" | "smg" (matches weapon_*.png). */
    public static final String[] SKIN_WP = {"pistol", "rifle", "smg"};
    /** weapon gradient endpoints (baked into weapon_*.png, kept for tinting). */
    public static final int[] SKIN_C1 = {0xFFCFE8F5, 0xFFE878BC, 0xFFD05050};
    public static final int[] SKIN_C2 = {0xFF5A8FC0, 0xFF7D4A86, 0xFF5A2626};

    // ---- timings ----

    public static final long TYPING_MS = 1100L;
    public static final long ACCEPT_BUSY_MS = 0L;
    public static final long REJECT_BUSY_MS = 450L;
    /** Countdown start: 2d 23:57:45. */
    public static final long COUNT_INITIAL_MS = (2L * 86400L + 23L * 3600L + 57L * 60L + 45L) * 1000L;

    // ---- cap ----

    public static final int[] CAPS = {30, 64, 200, 400, 800};
    public static final int CAP_UNLIMITED = -1;

    // ---- region 11 collection strip (HTML DOT_GROUPS) ----

    /** Dot groups: colour + fill pattern (1 = filled, 0 = hollow). */
    public static final DotGroup[] DOT_GROUPS = {
            new DotGroup(0xFF4B69FF, new int[]{1, 0, 1, 0, 1, 0, 0, 0}),
            new DotGroup(0xFF8847FF, new int[]{1, 0, 0, 0, 0, 0}),
            new DotGroup(0xFFC93CD6, new int[]{1, 1, 0}),
            new DotGroup(0xFFDE4B4B, new int[]{0, 0}),
            new DotGroup(0xFFE5C558, new int[]{0}),
    };

    /** A dot group in the collection strip. */
    public static final class DotGroup {
        public final int color;
        public final int[] pattern;

        public DotGroup(int color, int[] pattern) {
            this.color = color;
            this.pattern = pattern;
        }
    }

    // ---- state ----

    private final List<Object> history = new ArrayList<>();
    private final Random rnd = new Random();
    private Status status = Status.IDLE;
    private int round = 0;
    private long roundStartMs;
    private long statusSinceMs;
    private long generation = 0;
    private int cap = CAP_UNLIMITED;
    private long countdownMs = COUNT_INITIAL_MS;
    private long lastCountMs;
    private boolean countdownStarted;
    private Offer pending;

    // ---- lifecycle ----

    /** Start a fresh negotiation (generation token invalidates stale state). */
    public void start(long nowMs) {
        generation++;
        history.clear();
        status = Status.IDLE;
        round = 0;
        pending = null;
        cap = CAP_UNLIMITED;
        countdownMs = COUNT_INITIAL_MS;
        lastCountMs = nowMs;
        countdownStarted = true;
        presentRound(nowMs);
    }

    /** Advance the machine for the given client clock. */
    public void tick(long nowMs) {
        if (!countdownStarted) {
            lastCountMs = nowMs;
            countdownStarted = true;
        }
        if (nowMs - lastCountMs >= 1000L) {
            long deltaMs = nowMs - lastCountMs;
            long steps = deltaMs / 1000L;
            if (countdownMs > 0) {
                countdownMs = Math.max(0L, countdownMs - steps * 1000L);
            }
            lastCountMs += steps * 1000L;
        }
        switch (status) {
            case TYPING -> {
                if (nowMs - roundStartMs >= TYPING_MS) {
                    becomePending(nowMs);
                }
            }
            case ACCEPT_BUSY -> {
                if (nowMs - statusSinceMs >= ACCEPT_BUSY_MS) {
                    status = Status.CLOSED;
                    statusSinceMs = nowMs;
                }
            }
            case REJECT_BUSY -> {
                if (nowMs - statusSinceMs >= REJECT_BUSY_MS) {
                    if (round < MAX_ROUNDS) {
                        presentRound(nowMs);
                    } else {
                        status = Status.FAILED;
                        statusSinceMs = nowMs;
                        history.add(new SystemEntry("csgobox.terminal.sys.failed", true, nowMs));
                    }
                }
            }
            default -> {
            }
        }
    }

    /** Accept the current offer (only valid while PENDING — typing window lock). */
    public void acceptNow(long nowMs) {
        if (status != Status.PENDING) {
            return;
        }
        status = Status.ACCEPT_BUSY;
        statusSinceMs = nowMs;
        markOfferStatus(OFFER_ACCEPTED);
        history.add(new SystemEntry("csgobox.terminal.sys.accepted", false, nowMs));
    }

    /** Reject the current offer (only valid while PENDING). */
    public void rejectNow(long nowMs) {
        if (status != Status.PENDING) {
            return;
        }
        status = Status.REJECT_BUSY;
        statusSinceMs = nowMs;
        markOfferStatus(OFFER_REJECTED);
        history.add(new SystemEntry("csgobox.terminal.sys.rejected", false, nowMs));
    }

    // ---- accessors ----

    public Status status() {
        return status;
    }

    public int round() {
        return round;
    }

    public long generation() {
        return generation;
    }

    public int cap() {
        return cap;
    }

    public void setCap(int cap) {
        this.cap = cap;
    }

    public long countdownMs() {
        return countdownMs;
    }

    public Offer pending() {
        return pending;
    }

    /** Last offer card entry, or null. */
    public OfferEntry lastOfferEntry() {
        for (int i = history.size() - 1; i >= 0; i--) {
            if (history.get(i) instanceof OfferEntry oe) {
                return oe;
            }
        }
        return null;
    }

    /** Chat history snapshot (immutable). */
    public List<Object> history() {
        return List.copyOf(history);
    }

    /** Display price of the current offer (HTML price formatting, integers only). */
    public String offerPrice() {
        Offer off = pending;
        return off == null ? "0" : SKIN_PRICE[off.skinIdx()];
    }

    /** Counter label for region 6: lang key + translatable args. */
    public CounterLabel counterLabel() {
        return switch (status) {
            case CLOSED -> new CounterLabel("csgobox.terminal.counter.done");
            case FAILED -> new CounterLabel("csgobox.terminal.counter.failed");
            case IDLE -> new CounterLabel("csgobox.terminal.counter.wait");
            default -> pending == null
                    ? new CounterLabel("csgobox.terminal.counter.preparing")
                    : new CounterLabel("csgobox.terminal.counter.offer", round, "¥" + offerPrice());
        };
    }

    /** Lang key plus translatable args (platform renders via Component). */
    public record CounterLabel(String key, Object... args) {
    }

    // ---- internals ----

    private void markOfferStatus(int status) {
        for (int i = history.size() - 1; i >= 0; i--) {
            if (history.get(i) instanceof OfferEntry oe) {
                history.set(i, new OfferEntry(oe.offer(), oe.atMs(), status));
                return;
            }
        }
    }

    private void presentRound(long nowMs) {
        round++;
        status = Status.TYPING;
        roundStartMs = nowMs;
        statusSinceMs = nowMs;
        pending = null;
        history.add(new LineEntry(round, LINES[ROUND_LINE[round - 1]], nowMs));
    }

    private void becomePending(long nowMs) {
        status = Status.PENDING;
        statusSinceMs = nowMs;
        int skinIdx = ROUND_SKIN[round - 1];
        float wearVal = SKIN_WEAR_VAL[skinIdx];
        boolean finalRound = round == MAX_ROUNDS;
        Offer offer = new Offer(round, skinIdx, wearVal,
                rnd.nextInt(5),                    // style 0..4 (style.* keys)
                1000 + rnd.nextInt(900),           // serial no
                rnd.nextInt(1000),                 // pattern
                finalRound);
        pending = offer;
        history.add(new OfferEntry(offer, nowMs, OFFER_PENDING));
    }
}
