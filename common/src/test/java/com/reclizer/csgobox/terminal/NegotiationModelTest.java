package com.reclizer.csgobox.terminal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link NegotiationModel}.
 */
final class NegotiationModelTest {

    private static NegotiationModel fresh() {
        NegotiationModel m = new NegotiationModel();
        m.start(100_000L);
        return m;
    }

    @Test
    @DisplayName("start -> TYPING round 1 with line.0, tick -> PENDING + OfferEntry")
    void startAndPending() {
        NegotiationModel m = fresh();
        assertEquals(NegotiationModel.Status.TYPING, m.status());
        assertEquals(1, m.round());
        assertEquals(1, m.history().size());
        assertNull(m.pending());
        NegotiationModel.LineEntry line = assertInstanceOf(
                NegotiationModel.LineEntry.class, m.history().get(0));
        assertTrue(Arrays.asList(NegotiationModel.LINES).contains(line.textKey()));
        m.tick(100_000L + NegotiationModel.TYPING_MS);
        assertEquals(NegotiationModel.Status.PENDING, m.status());
        assertNotNull(m.pending());
        assertInstanceOf(NegotiationModel.OfferEntry.class, m.history().get(1));
        NegotiationModel.OfferEntry oe = (NegotiationModel.OfferEntry) m.history().get(1);
        assertEquals(NegotiationModel.OFFER_PENDING, oe.status());
        assertTrue(oe.offer().wearVal() >= 0F && oe.offer().wearVal() < 1F,
                "wear is a csbox-style uniform roll in [0,1)");
    }

    @Test
    @DisplayName("typing window lock: actions ignored, PENDING at TYPING_MS")
    void typingLock() {
        NegotiationModel m = fresh();
        m.acceptNow(150_000L);
        m.rejectNow(150_001L);
        assertSame(NegotiationModel.Status.TYPING, m.status());
        assertNull(m.pending());
        m.tick(100_000L + NegotiationModel.TYPING_MS - 1L);
        assertSame(NegotiationModel.Status.TYPING, m.status());
        m.tick(100_000L + NegotiationModel.TYPING_MS);
        assertSame(NegotiationModel.Status.PENDING, m.status());
        assertNotNull(m.pending());
        assertEquals(1, m.pending().round());
        assertEquals(0, m.pending().skinIdx()); // round 1 -> skin 0 (ROUND_SKIN)
    }

    @Test
    @DisplayName("accept: PENDING -> ACCEPT_BUSY -> CLOSED next tick, card ACCEPTED, offer stable")
    void acceptFlow() {
        NegotiationModel m = fresh();
        m.tick(100_000L + NegotiationModel.TYPING_MS);
        NegotiationModel.Offer before = m.pending();
        m.acceptNow(200_000L);
        assertEquals(NegotiationModel.Status.ACCEPT_BUSY, m.status());
        assertSame(before, m.pending(), "offer must not change during accept");
        assertEquals(NegotiationModel.OFFER_ACCEPTED, lastOfferEntry(m).status());
        m.tick(200_001L);
        assertEquals(NegotiationModel.Status.CLOSED, m.status());
        assertTrue(m.history().stream().anyMatch(e ->
                e instanceof NegotiationModel.SystemEntry s
                        && "csgobox.terminal.sys.accepted".equals(s.textKey())));
    }

    @Test
    @DisplayName("accept during TYPING is ignored")
    void acceptWhileTypingIgnored() {
        NegotiationModel m = fresh();
        m.acceptNow(150_000L);
        assertEquals(NegotiationModel.Status.TYPING, m.status());
    }

    @Test
    @DisplayName("reject: card tagged REJECTED, next round after 450ms")
    void rejectFlow() {
        NegotiationModel m = fresh();
        m.tick(100_000L + NegotiationModel.TYPING_MS);
        m.rejectNow(200_000L);
        assertEquals(NegotiationModel.Status.REJECT_BUSY, m.status());
        assertEquals(NegotiationModel.OFFER_REJECTED, lastOfferEntry(m).status());
        m.tick(200_000L + NegotiationModel.REJECT_BUSY_MS);
        assertEquals(2, m.round());
        assertEquals(NegotiationModel.Status.TYPING, m.status());
    }

    @Test
    @DisplayName("reject advances: busy 450ms -> TYPING round 2 line.1 -> PENDING skin 2")
    void rejectAdvances() {
        NegotiationModel m = fresh();
        m.tick(100_000L + NegotiationModel.TYPING_MS);
        m.rejectNow(200_000L);
        assertSame(NegotiationModel.Status.REJECT_BUSY, m.status());
        m.tick(200_000L + NegotiationModel.REJECT_BUSY_MS - 1L);
        assertSame(NegotiationModel.Status.REJECT_BUSY, m.status());
        m.tick(200_000L + NegotiationModel.REJECT_BUSY_MS);
        assertSame(NegotiationModel.Status.TYPING, m.status());
        assertEquals(2, m.round());
        assertTrue(Arrays.asList(NegotiationModel.LINES).contains(
                ((NegotiationModel.LineEntry) m.history().get(m.history().size() - 1)).textKey()));
        m.tick(200_000L + NegotiationModel.REJECT_BUSY_MS + NegotiationModel.TYPING_MS);
        assertSame(NegotiationModel.Status.PENDING, m.status());
        assertEquals(2, m.pending().skinIdx()); // round 2 -> skin 2 (ROUND_SKIN)
    }

    @Test
    @DisplayName("round 5 reject -> FAILED + failed system entry")
    void finalRejectFails() {
        NegotiationModel m = fresh();
        for (int r = 1; r <= 5; r++) {
            m.tick(100_000L + r * 500_000L + NegotiationModel.TYPING_MS);
            m.rejectNow(100_000L + r * 500_000L + 1L);
            m.tick(100_000L + r * 500_000L + 1L + NegotiationModel.REJECT_BUSY_MS);
        }
        assertEquals(NegotiationModel.Status.FAILED, m.status());
        NegotiationModel.SystemEntry last = assertInstanceOf(
                NegotiationModel.SystemEntry.class,
                m.history().get(m.history().size() - 1));
        assertTrue(last.failed(), "final system entry must be the failed one");
    }

    @Test
    @DisplayName("generation token: restart invalidates stale state")
    void generation() {
        NegotiationModel m = fresh();
        m.tick(100_000L + NegotiationModel.TYPING_MS); // PENDING round 1
        m.rejectNow(200_000L);
        m.tick(200_000L + NegotiationModel.REJECT_BUSY_MS); // round 2 TYPING
        assertEquals(2, m.round());
        long gen = m.generation();
        m.start(500_000L); // restart mid-negotiation
        assertTrue(m.generation() > gen);
        assertEquals(1, m.round());
        assertSame(NegotiationModel.Status.TYPING, m.status());
        assertNull(m.pending());
        m.tick(500_000L + NegotiationModel.TYPING_MS);
        assertSame(NegotiationModel.Status.PENDING, m.status());
        assertEquals(1, m.pending().round());
    }

    @Test
    @DisplayName("snapshot/restore round-trips the full negotiation state")
    void snapshotRestore() {
        NegotiationModel m = fresh();
        m.tick(100_000L + NegotiationModel.TYPING_MS); // PENDING round 1
        m.rejectNow(200_000L);
        m.tick(200_000L + NegotiationModel.REJECT_BUSY_MS); // TYPING round 2
        m.setCap(200);
        NegotiationModel.Snapshot snap = m.snapshot();

        NegotiationModel m2 = new NegotiationModel();
        m2.restore(snap, 500_000L);
        assertEquals(2, m2.round());
        assertSame(NegotiationModel.Status.TYPING, m2.status());
        assertEquals(200, m2.cap());
        assertEquals(m.history(), m2.history());
        assertNull(m2.pending());

        // resume the typing window and land on the same round-2 offer data
        m2.tick(500_000L + NegotiationModel.TYPING_MS);
        assertSame(NegotiationModel.Status.PENDING, m2.status());
        assertEquals(2, m2.pending().round());
    }

    @Test
    @DisplayName("restore of a PENDING snapshot keeps the pending offer")
    void restorePending() {
        NegotiationModel m = fresh();
        m.tick(100_000L + NegotiationModel.TYPING_MS);
        NegotiationModel.Offer offer = m.pending();
        NegotiationModel.Snapshot snap = m.snapshot();

        NegotiationModel m2 = new NegotiationModel();
        m2.restore(snap, 500_000L);
        assertSame(NegotiationModel.Status.PENDING, m2.status());
        assertEquals(offer, m2.pending());
        assertEquals(2, m2.history().size());
    }

    @Test
    @DisplayName("offer source drives becomePending (server pre-sampled offers)")
    void offerSource() {
        NegotiationModel m = fresh();
        NegotiationModel.Offer preset = new NegotiationModel.Offer(1, 1, 0.4F, 3, 1234, 567, false);
        m.setOfferSource(round -> round == 1 ? preset : null);
        m.tick(100_000L + NegotiationModel.TYPING_MS);
        assertEquals(preset, m.pending());
    }

    @Test
    @DisplayName("rejectForced advances server-side without the typing window")
    void rejectForced() {
        NegotiationModel m = fresh();
        m.rejectForced(200_000L);
        assertEquals(2, m.round());
        assertSame(NegotiationModel.Status.TYPING, m.status());
        assertEquals(NegotiationModel.OFFER_REJECTED,
                ((NegotiationModel.OfferEntry) m.history().get(1)).status());
    }

    @Test
    @DisplayName("fifth rejectForced -> FAILED, buyForced -> CLOSED")
    void forcedFinals() {
        NegotiationModel m = fresh();
        for (int r = 1; r <= 5; r++) {
            m.rejectForced(200_000L + r * 1_000L);
        }
        assertSame(NegotiationModel.Status.FAILED, m.status());

        NegotiationModel m2 = fresh();
        m2.buyForced(300_000L);
        assertSame(NegotiationModel.Status.CLOSED, m2.status());
        assertEquals(NegotiationModel.OFFER_ACCEPTED,
                ((NegotiationModel.OfferEntry) m2.history().get(1)).status());
    }

    @Test
    @DisplayName("syncClose pins TYPING/PENDING for exact reopen resume")
    void syncClose() {
        NegotiationModel m = fresh();
        // close while the round-1 offer is on screen
        m.tick(100_000L + NegotiationModel.TYPING_MS);
        NegotiationModel.Offer offer = m.pending();
        long countdown = m.countdownRemainingMs();
        m.syncClose(1, true, 150_000L, 64, 200_000L);
        assertSame(NegotiationModel.Status.PENDING, m.status());
        assertEquals(offer, m.pending());
        assertEquals(64, m.cap());
        assertEquals(countdown, m.countdownRemainingMs(), "countdown is server-authoritative, syncClose must not touch it");
        // no duplicate offer card
        assertEquals(2, m.history().size());

        NegotiationModel m2 = fresh();
        m2.syncClose(1, false, 0, NegotiationModel.CAP_UNLIMITED, 200_000L);
        assertSame(NegotiationModel.Status.TYPING, m2.status());
        assertNull(m2.pending());
        assertEquals(NegotiationModel.COUNT_INITIAL_MS, m2.countdownRemainingMs());
    }

    @Test
    @DisplayName("timing constants aligned with HTML")
    void timings() {
        assertEquals(450L, NegotiationModel.REJECT_BUSY_MS);
        assertEquals(0L, NegotiationModel.ACCEPT_BUSY_MS);
    }

    @Test
    @DisplayName("countdown ticks down per second; at zero the negotiation expires (FAILED)")
    void countdown() {
        NegotiationModel m = fresh();
        m.tick(103_000L);
        assertEquals(NegotiationModel.COUNT_INITIAL_MS - 3_000L, m.countdownRemainingMs());
        m.tick(100_000L + NegotiationModel.COUNT_INITIAL_MS + 1_000L); // long jump drains the rest
        assertEquals(0, m.countdownRemainingMs());
        assertSame(NegotiationModel.Status.FAILED, m.status());
        assertTrue(m.history().stream().anyMatch(e ->
                e instanceof NegotiationModel.SystemEntry se
                        && "csgobox.terminal.sys.timeout".equals(se.textKey())
                        && se.failed()));
        long prev = m.countdownRemainingMs();
        m.tick(100_000L + NegotiationModel.COUNT_INITIAL_MS + 5_000L);
        assertEquals(prev, m.countdownRemainingMs(), "must not go negative");
        assertSame(NegotiationModel.Status.FAILED, m.status(), "expired stays expired");
    }

    @Test
    @DisplayName("tickServer advances only the countdown, never round transitions")
    void tickServerCountdownOnly() {
        NegotiationModel m = fresh();
        m.tickServer(103_000L);
        assertEquals(NegotiationModel.COUNT_INITIAL_MS - 3_000L, m.countdownRemainingMs());
        // still TYPING round 1 — the offer reveal is the client's job
        assertSame(NegotiationModel.Status.TYPING, m.status());
        assertEquals(1, m.round());
        assertFalse(m.tickServer(104_000L));
    }

    @Test
    @DisplayName("tickServer expiry returns true once and only on the draining pass")
    void tickServerExpiry() {
        NegotiationModel m = fresh();
        m.tickServer(103_000L);
        assertTrue(m.tickServer(100_000L + NegotiationModel.COUNT_INITIAL_MS + 1_000L));
        assertSame(NegotiationModel.Status.FAILED, m.status());
        assertFalse(m.tickServer(100_000L + NegotiationModel.COUNT_INITIAL_MS + 5_000L),
                "finished sessions never re-expire");
    }

    @Test
    @DisplayName("cap round-trips; counter label follows status")
    void capAndCounter() {
        NegotiationModel m = new NegotiationModel();
        assertEquals(NegotiationModel.CAP_UNLIMITED, m.cap());
        m.setCap(64);
        assertEquals(64, m.cap());
        m.start(0);
        assertEquals("csgobox.terminal.counter.preparing", m.counterLabel(6).key());
        m.tick(2000);
        assertEquals("csgobox.terminal.counter.offer", m.counterLabel(6).key());
        assertEquals(1, m.counterLabel(6).args()[0]);
        assertEquals("¥6", m.counterLabel(6).args()[1]);
        m.acceptNow(2000);
        m.tick(3000);
        assertEquals("csgobox.terminal.counter.done", m.counterLabel(6).key());
    }

    @Test
    @DisplayName("script data is coherent")
    void scriptData() {
        assertEquals(12, NegotiationModel.LINES.length);
        assertEquals(5, NegotiationModel.ROUND_SKIN.length);
        assertEquals(3, NegotiationModel.SKIN_NAME_KEYS.length);
        for (int r : NegotiationModel.ROUND_SKIN) {
            assertTrue(r >= 0 && r < 3);
        }
        assertEquals(5, NegotiationModel.CAPS.length);
        assertFalse(NegotiationModel.SKIN_PRICE[0].isEmpty());
    }

    @Test
    @DisplayName("session line draw: 5 unique lines from the pool")
    void sessionLinesUnique() {
        NegotiationModel m = fresh();
        Set<String> seen = new HashSet<>();
        for (int r = 1; r <= 5; r++) {
            m.tick(100_000L + r * 500_000L + NegotiationModel.TYPING_MS);
            NegotiationModel.LineEntry line = null;
            for (int i = m.history().size() - 1; i >= 0; i--) {
                if (m.history().get(i) instanceof NegotiationModel.LineEntry le
                        && le.round() == r) {
                    line = le;
                    break;
                }
            }
            assertNotNull(line, "round " + r + " must have a dealer line");
            assertTrue(Arrays.asList(NegotiationModel.LINES).contains(line.textKey()));
            assertTrue(seen.add(line.textKey()), "round " + r + " repeats a dealer line");
            m.rejectNow(100_000L + r * 500_000L + 1L);
            m.tick(100_000L + r * 500_000L + 1L + NegotiationModel.REJECT_BUSY_MS);
        }
    }

    private static NegotiationModel.OfferEntry lastOfferEntry(NegotiationModel m) {
        for (int i = m.history().size() - 1; i >= 0; i--) {
            if (m.history().get(i) instanceof NegotiationModel.OfferEntry oe) {
                return oe;
            }
        }
        throw new AssertionError("no offer entry");
    }
}
