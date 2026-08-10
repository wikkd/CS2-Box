package com.reclizer.csgobox.terminal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link NegotiationModel}.
 */
final class NegotiationModelTest {

    @Test
    @DisplayName("start() enters TYPING with round 1 line")
    void start() {
        NegotiationModel m = new NegotiationModel();
        m.start(1000);
        assertSame(NegotiationModel.Status.TYPING, m.status());
        assertEquals(1, m.round());
        assertEquals(1, m.history().size());
        assertTrue(m.history().get(0) instanceof NegotiationModel.LineEntry);
        assertEquals("csgobox.terminal.line.0",
                ((NegotiationModel.LineEntry) m.history().get(0)).textKey());
        assertNull(m.pending());
    }

    @Test
    @DisplayName("typing window lock: actions ignored before 1100ms")
    void typingLock() {
        NegotiationModel m = new NegotiationModel();
        m.start(0);
        m.acceptNow(500);
        m.rejectNow(500);
        assertSame(NegotiationModel.Status.TYPING, m.status());
        assertNull(m.pending());
        m.tick(1099);
        assertSame(NegotiationModel.Status.TYPING, m.status());
        m.tick(1100);
        assertSame(NegotiationModel.Status.PENDING, m.status());
        assertTrue(m.pending() != null);
        assertEquals(1, m.pending().round());
        assertEquals(0, m.pending().skinIdx()); // round 1 -> skin 0 (HTML ROUNDS)
    }

    @Test
    @DisplayName("accept: PENDING -> ACCEPT_BUSY -> CLOSED, offer stays stable")
    void accept() {
        NegotiationModel m = new NegotiationModel();
        m.start(0);
        m.tick(2000);
        var before = m.pending();
        m.acceptNow(2000);
        assertSame(NegotiationModel.Status.ACCEPT_BUSY, m.status());
        assertSame(before, m.pending(), "offer must not change during accept");
        m.tick(2899);
        assertSame(NegotiationModel.Status.ACCEPT_BUSY, m.status());
        m.tick(2900);
        assertSame(NegotiationModel.Status.CLOSED, m.status());
        boolean accepted = m.history().stream().anyMatch(e ->
                e instanceof NegotiationModel.SystemEntry s
                        && "csgobox.terminal.sys.accepted".equals(s.textKey()));
        assertTrue(accepted);
    }

    @Test
    @DisplayName("reject: PENDING -> REJECT_BUSY -> next round TYPING (round<5)")
    void rejectAdvances() {
        NegotiationModel m = new NegotiationModel();
        m.start(0);
        m.tick(2000);
        m.rejectNow(2000);
        assertSame(NegotiationModel.Status.REJECT_BUSY, m.status());
        m.tick(2900); // 900ms busy
        assertSame(NegotiationModel.Status.TYPING, m.status());
        assertEquals(2, m.round());
        assertEquals("csgobox.terminal.line.1",
                ((NegotiationModel.LineEntry) m.history().get(m.history().size() - 1)).textKey());
        m.tick(4000); // 1100ms typing
        assertSame(NegotiationModel.Status.PENDING, m.status());
        assertEquals(2, m.pending().skinIdx()); // round 2 -> skin 2 (HTML ROUNDS)
    }

    @Test
    @DisplayName("reject at round 5: FAILED with failed system entry")
    void rejectFails() {
        NegotiationModel m = new NegotiationModel();
        m.start(0);
        long now = 0;
        for (int r = 1; r <= 5; r++) {
            now += 5000;
            m.tick(now);
            m.rejectNow(now);
            now += 5000;
            m.tick(now);
        }
        assertSame(NegotiationModel.Status.FAILED, m.status());
        boolean failed = m.history().stream().anyMatch(e ->
                e instanceof NegotiationModel.SystemEntry s && s.failed());
        assertTrue(failed, "failed system entry expected");
    }

    @Test
    @DisplayName("generation token: restart invalidates stale state")
    void generation() {
        NegotiationModel m = new NegotiationModel();
        m.start(0);
        m.tick(2000);
        m.rejectNow(2000);
        m.tick(2900); // round 2 TYPING
        assertEquals(2, m.round());
        long gen = m.generation();
        m.start(5000); // restart mid-negotiation
        assertTrue(m.generation() > gen);
        assertEquals(1, m.round());
        assertSame(NegotiationModel.Status.TYPING, m.status());
        assertNull(m.pending());
        m.tick(6100);
        assertSame(NegotiationModel.Status.PENDING, m.status());
        assertEquals(1, m.pending().round());
    }

    @Test
    @DisplayName("countdown ticks once per second and floors at zero")
    void countdown() {
        NegotiationModel m = new NegotiationModel();
        m.start(0);
        assertEquals(NegotiationModel.COUNT_INITIAL_MS, m.countdownMs());
        m.tick(999);
        assertEquals(NegotiationModel.COUNT_INITIAL_MS, m.countdownMs());
        m.tick(1000);
        assertEquals(NegotiationModel.COUNT_INITIAL_MS - 1000, m.countdownMs());
        m.tick(259064_000L + 1000); // long jump draining the rest
        assertEquals(0, m.countdownMs());
        long prev = m.countdownMs();
        m.tick(259065_000L + 1000);
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
}
