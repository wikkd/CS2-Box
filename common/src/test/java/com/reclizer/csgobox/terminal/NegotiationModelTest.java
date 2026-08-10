package com.reclizer.csgobox.terminal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class NegotiationModelTest {

    private static NegotiationModel fresh() {
        NegotiationModel m = new NegotiationModel();
        m.start(100_000L);
        return m;
    }

    @Test
    @DisplayName("start -> TYPING round 1, tick -> PENDING + OfferEntry")
    void startAndPending() {
        NegotiationModel m = fresh();
        assertEquals(NegotiationModel.Status.TYPING, m.status());
        assertEquals(1, m.round());
        assertInstanceOf(NegotiationModel.LineEntry.class, m.history().get(0));
        m.tick(100_000L + NegotiationModel.TYPING_MS);
        assertEquals(NegotiationModel.Status.PENDING, m.status());
        assertNotNull(m.pending());
        assertInstanceOf(NegotiationModel.OfferEntry.class, m.history().get(1));
        NegotiationModel.OfferEntry oe = (NegotiationModel.OfferEntry) m.history().get(1);
        assertEquals(NegotiationModel.OFFER_PENDING, oe.status());
        assertEquals(0.1139F, oe.offer().wearVal(), 1e-6F);
    }

    @Test
    @DisplayName("accept: PENDING -> ACCEPT_BUSY -> CLOSED next tick, card tagged ACCEPTED")
    void acceptFlow() {
        NegotiationModel m = fresh();
        m.tick(100_000L + NegotiationModel.TYPING_MS);
        m.acceptNow(200_000L);
        assertEquals(NegotiationModel.Status.ACCEPT_BUSY, m.status());
        assertEquals(NegotiationModel.OFFER_ACCEPTED, lastOfferEntry(m).status());
        m.tick(200_001L);
        assertEquals(NegotiationModel.Status.CLOSED, m.status());
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
    @DisplayName("round 5 reject -> FAILED + failed system entry")
    void finalRejectFails() {
        NegotiationModel m = fresh();
        for (int r = 1; r <= 5; r++) {
            m.tick(100_000L + r * 500_000L + NegotiationModel.TYPING_MS);
            m.rejectNow(100_000L + r * 500_000L + 1L);
            m.tick(100_000L + r * 500_000L + 1L + NegotiationModel.REJECT_BUSY_MS);
        }
        assertEquals(NegotiationModel.Status.FAILED, m.status());
        assertInstanceOf(NegotiationModel.SystemEntry.class,
                m.history().get(m.history().size() - 1));
    }

    @Test
    @DisplayName("timing constants aligned with HTML")
    void timings() {
        assertEquals(450L, NegotiationModel.REJECT_BUSY_MS);
        assertEquals(0L, NegotiationModel.ACCEPT_BUSY_MS);
    }

    @Test
    @DisplayName("countdown ticks down per second")
    void countdown() {
        NegotiationModel m = fresh();
        m.tick(103_000L);
        assertEquals(NegotiationModel.COUNT_INITIAL_MS - 3_000L, m.countdownMs());
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
