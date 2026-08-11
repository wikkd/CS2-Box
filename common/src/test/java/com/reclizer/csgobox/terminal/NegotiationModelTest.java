package com.reclizer.csgobox.terminal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
        assertEquals("csgobox.terminal.line.0", line.textKey());
        m.tick(100_000L + NegotiationModel.TYPING_MS);
        assertEquals(NegotiationModel.Status.PENDING, m.status());
        assertNotNull(m.pending());
        assertInstanceOf(NegotiationModel.OfferEntry.class, m.history().get(1));
        NegotiationModel.OfferEntry oe = (NegotiationModel.OfferEntry) m.history().get(1);
        assertEquals(NegotiationModel.OFFER_PENDING, oe.status());
        assertEquals(0.11383486F, oe.offer().wearVal(), 1e-6F);
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
        assertEquals("csgobox.terminal.line.1",
                ((NegotiationModel.LineEntry) m.history().get(m.history().size() - 1)).textKey());
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
    @DisplayName("timing constants aligned with HTML")
    void timings() {
        assertEquals(450L, NegotiationModel.REJECT_BUSY_MS);
        assertEquals(0L, NegotiationModel.ACCEPT_BUSY_MS);
    }

    @Test
    @DisplayName("countdown ticks down per second and floors at zero")
    void countdown() {
        NegotiationModel m = fresh();
        m.tick(103_000L);
        assertEquals(NegotiationModel.COUNT_INITIAL_MS - 3_000L, m.countdownMs());
        m.tick(100_000L + NegotiationModel.COUNT_INITIAL_MS + 1_000L); // long jump drains the rest
        assertEquals(0, m.countdownMs());
        long prev = m.countdownMs();
        m.tick(100_000L + NegotiationModel.COUNT_INITIAL_MS + 5_000L);
        assertEquals(prev, m.countdownMs(), "must not go negative");
    }

    @Test
    @DisplayName("cap round-trips; counter label follows status")
    void capAndCounter() {
        NegotiationModel m = new NegotiationModel();
        assertEquals(NegotiationModel.CAP_UNLIMITED, m.cap());
        m.setCap(64);
        assertEquals(64, m.cap());
        m.start(0);
        assertEquals("csgobox.terminal.counter.preparing", m.counterLabel().key());
        m.tick(2000);
        assertEquals("csgobox.terminal.counter.offer", m.counterLabel().key());
        assertEquals(1, m.counterLabel().args()[0]);
        m.acceptNow(2000);
        m.tick(3000);
        assertEquals("csgobox.terminal.counter.done", m.counterLabel().key());
    }

    @Test
    @DisplayName("script data is coherent")
    void scriptData() {
        assertEquals(5, NegotiationModel.LINES.length);
        assertEquals(5, NegotiationModel.ROUND_LINE.length);
        assertEquals(5, NegotiationModel.ROUND_SKIN.length);
        assertEquals(3, NegotiationModel.SKIN_NAME_KEYS.length);
        for (int r : NegotiationModel.ROUND_SKIN) {
            assertTrue(r >= 0 && r < 3);
        }
        for (int r : NegotiationModel.ROUND_LINE) {
            assertTrue(r >= 0 && r < 5);
        }
        assertEquals(5, NegotiationModel.CAPS.length);
        assertFalse(NegotiationModel.SKIN_PRICE[0].isEmpty());
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
