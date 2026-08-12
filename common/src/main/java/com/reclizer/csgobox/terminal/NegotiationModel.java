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

    /**
     * Serialisable negotiation state — server sessions and reopen restore.
     * {@code countdownDeadlineMs} is an ABSOLUTE deadline on the world clock
     * (world game ticks × 50): it advances only while the world runs, so a
     * server stop / crash never grants free time and offline time never counts.
     */
    public record Snapshot(int round, Status status, long generation, int cap, long countdownDeadlineMs,
                           Offer pending, List<Object> history) {
    }

    /** Dealer chat line (bubble). */
    public record LineEntry(int round, String textKey, long atMs) {
    }

    /**
     * System bubble (已接受 / 已拒绝 / 谈判破裂). {@code args} optionally
     * carries translatable placeholders (e.g. the terminal owner's name in the
     * "locked" refusal) — null means "no args" and the client falls back to
     * its default single-arg behaviour.
     */
    public record SystemEntry(String textKey, boolean failed, long atMs, String[] args) {
        public SystemEntry(String textKey, boolean failed, long atMs) {
            this(textKey, failed, atMs, null);
        }
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
    /** Whole Armory Point price per box grade (1..5, index 0 unused). */
    public static final int[] GRADE_PRICE = {6, 10, 16, 22, 30};
    /** Dealer line when the player cancels a trade or lacks Armory Points. */
    public static final String LINE_RECONSIDER = "csgobox.terminal.line.reconsider";
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

    /** Whole Armory Point price for a box grade (1..5, clamped). */
    public static int priceForGrade(int grade) {
        return GRADE_PRICE[Math.max(0, Math.min(grade - 1, GRADE_PRICE.length - 1))];
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
    /** Countdown start: 3 hours. */
    public static final long COUNT_INITIAL_MS = 3L * 3600L * 1000L;

    // ---- cap ----

    public static final int[] CAPS = {30, 64, 200, 400, 800};
    public static final int CAP_UNLIMITED = -1;

    /**
     * The only cap values a client may report back (a display preference in
     * the action bar). Anything else is rejected so junk values never get
     * persisted into a session snapshot.
     */
    public static boolean isValidCap(int cap) {
        if (cap == CAP_UNLIMITED) {
            return true;
        }
        for (int c : CAPS) {
            if (c == cap) {
                return true;
            }
        }
        return false;
    }

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
    private OfferSource offerSource;
    private Status status = Status.IDLE;
    private int round = 0;
    private long roundStartMs;
    private long statusSinceMs;
    private long generation = 0;
    private int cap = CAP_UNLIMITED;
    private long countdownDeadlineMs = COUNT_INITIAL_MS;
    private long lastTickMs;
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
        countdownDeadlineMs = nowMs + COUNT_INITIAL_MS;
        lastTickMs = nowMs;
        presentRound(nowMs);
    }

    /** Replace the whole state with a server-provided snapshot (reopen resume). */
    public void restore(Snapshot snap, long nowMs) {
        history.clear();
        history.addAll(snap.history());
        status = snap.status();
        round = snap.round();
        generation = snap.generation();
        cap = snap.cap();
        countdownDeadlineMs = snap.countdownDeadlineMs();
        pending = snap.pending();
        roundStartMs = nowMs;
        statusSinceMs = nowMs;
        lastTickMs = nowMs;
    }

    /** Full state snapshot for server sessions / reopen transport. */
    public Snapshot snapshot() {
        return new Snapshot(round, status, generation, cap, countdownDeadlineMs,
                pending, List.copyOf(history));
    }

    /** Advance the machine for the given client clock. */
    public void tick(long nowMs) {
        tickServer(nowMs);
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

    /**
     * Server-side clock (driven by the vanilla server tick, once per second):
     * checks the absolute deadline and expires the negotiation — never the
     * round transitions, because the client owns the typing/offer-reveal
     * animation. {@code nowMs} must be the WORLD clock (game ticks × 50), so
     * the countdown follows world running time and pauses/stops with the world.
     * When the deadline passes the negotiation expires (dealer leaves), which
     * also releases the terminal session lock on the server.
     *
     * @return true when the deadline just passed and the negotiation expired
     */
    public boolean tickServer(long nowMs) {
        if (status == Status.CLOSED || status == Status.FAILED) {
            return false;
        }
        lastTickMs = nowMs;
        if (nowMs >= countdownDeadlineMs) {
            expire(nowMs);
            return true;
        }
        return false;
    }

    /** Timeout: the dealer left before a deal — session-wide, releases the lock. */
    private void expire(long nowMs) {
        status = Status.FAILED;
        statusSinceMs = nowMs;
        pending = null;
        ensureOfferEntry(nowMs);
        history.add(new SystemEntry("csgobox.terminal.sys.timeout", true, nowMs));
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

    /**
     * Server-authoritative reject: the server never watches the client's
     * typing window, so unlike {@link #rejectNow} this advances unconditionally
     * to the next round (or FAILED on round 5).
     */
    public void rejectForced(long nowMs) {
        status = Status.REJECT_BUSY;
        statusSinceMs = nowMs;
        pending = null;
        ensureOfferEntry(nowMs);
        markOfferStatus(OFFER_REJECTED);
        history.add(new SystemEntry("csgobox.terminal.sys.rejected", false, nowMs));
        if (round < MAX_ROUNDS) {
            presentRound(nowMs);
        } else {
            status = Status.FAILED;
            statusSinceMs = nowMs;
            history.add(new SystemEntry("csgobox.terminal.sys.failed", true, nowMs));
        }
    }

    /** Server-authoritative buy success — the negotiation is finished. */
    public void buyForced(long nowMs) {
        status = Status.CLOSED;
        statusSinceMs = nowMs;
        pending = null;
        ensureOfferEntry(nowMs);
        markOfferStatus(OFFER_ACCEPTED);
        history.add(new SystemEntry("csgobox.terminal.sys.accepted", false, nowMs));
    }

    /**
     * Apply the client's on-close view so a reopen resumes at the exact same
     * round/status (TYPING vs PENDING) instead of replaying the typing window.
     */
    public void syncClose(int round, boolean pendingVisible, long pendingAtMs, int cap, long nowMs) {
        if (status == Status.CLOSED || status == Status.FAILED) {
            return;
        }
        if (round < 1 || round > MAX_ROUNDS) {
            return;
        }
        this.round = round;
        this.status = pendingVisible ? Status.PENDING : Status.TYPING;
        this.pending = pendingVisible ? currentOffer() : null;
        // The countdown is server-authoritative (tickServer) — never overwritten
        // from the client, otherwise a reopen would resurrect expired time.
        this.cap = cap;
        roundStartMs = nowMs;
        statusSinceMs = nowMs;
        if (pendingVisible && this.pending != null && !hasOfferEntryFor(round)) {
            history.add(new OfferEntry(this.pending, Math.max(0, pendingAtMs), OFFER_PENDING));
        }
    }

    /** Provider for the offer of a round — server sessions supply pre-sampled offers. */
    public interface OfferSource {
        Offer offerFor(int round);
    }

    public void setOfferSource(OfferSource source) {
        this.offerSource = source;
    }

    /** Dealer chat line: "think it over" — keeps the current offer pending. */
    public void dealerReconsider(long nowMs) {
        history.add(new LineEntry(Math.max(1, round), LINE_RECONSIDER, nowMs));
    }

    /** Add a plain system bubble (e.g. insufficient Armory Points). */
    public void addSystem(String textKey, long nowMs) {
        history.add(new SystemEntry(textKey, false, nowMs));
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

    /** Remaining countdown at the last clock input — display only, the server expiry is deadline-based. */
    public long countdownRemainingMs() {
        return Math.max(0L, countdownDeadlineMs - lastTickMs);
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

    private boolean hasOfferEntryFor(int targetRound) {
        for (int i = history.size() - 1; i >= 0; i--) {
            if (history.get(i) instanceof OfferEntry oe && oe.offer().round() == targetRound) {
                return true;
            }
        }
        return false;
    }

    /** The server's model never ticks, so an offer card may be missing — add it. */
    private void ensureOfferEntry(long nowMs) {
        if (!hasOfferEntryFor(round)) {
            history.add(new OfferEntry(currentOffer(), nowMs, OFFER_PENDING));
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
        Offer offer = currentOffer();
        pending = offer;
        history.add(new OfferEntry(offer, nowMs, OFFER_PENDING));
    }

    private Offer currentOffer() {
        if (pending != null && pending.round() == round) {
            return pending;
        }
        if (offerSource != null) {
            Offer offered = offerSource.offerFor(round);
            if (offered != null) {
                return offered;
            }
        }
        int skinIdx = ROUND_SKIN[round - 1];
        float wearVal = SKIN_WEAR_VAL[skinIdx];
        boolean finalRound = round == MAX_ROUNDS;
        return new Offer(round, skinIdx, wearVal,
                rnd.nextInt(5),                    // style 0..4 (style.* keys)
                1000 + rnd.nextInt(900),           // serial no
                rnd.nextInt(1000),                 // pattern
                finalRound);
    }
}
