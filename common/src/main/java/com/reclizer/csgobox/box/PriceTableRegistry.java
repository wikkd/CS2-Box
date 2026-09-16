package com.reclizer.csgobox.box;

/**
 * Cross-platform holder for the active central price table
 * ({@code config/csbox/_prices.json}).
 *
 * <p>Every platform's {@code BoxJsonLoader} publishes the table here after
 * each {@code loadAll()} / {@code reloadPreserving()} so read-only economy
 * code — such as the Armory Recycler's recycle value — can price a stack
 * without needing a box association. The dry-run {@code /csbox validate}
 * path never publishes (it must not mutate live state). Defaults to
 * {@link PriceTable#EMPTY}, so consumers never see null; a null parameter
 * degrades to {@link PriceTable#EMPTY} too.</p>
 *
 * <p>It also carries the terminal quote-cap ladder (报价上限) derived from the
 * active table by {@link QuoteCaps}: the server publishes both together, and
 * remote clients receive the ladder through the box-registry sync (they never
 * read {@code _prices.json} themselves). Keeping the derivation in one holder
 * means the action-bar selector and the server-side
 * {@code PacketTerminalClose} validation can never disagree.</p>
 */
public final class PriceTableRegistry {

    private static volatile PriceTable current = PriceTable.EMPTY;
    private static volatile int[] quoteCaps = QuoteCaps.NONE;

    private PriceTableRegistry() {
    }

    public static void set(PriceTable table) {
        current = table != null ? table : PriceTable.EMPTY;
        quoteCaps = QuoteCaps.tiers(current);
    }

    public static PriceTable get() {
        return current;
    }

    /**
     * Quote-cap tiers for the active table (strictly ascending, no duplicates;
     * empty when nothing in the table has a positive price). The array is
     * shared and must be treated as immutable by callers.
     */
    public static int[] quoteCaps() {
        return quoteCaps;
    }

    /**
     * Client-side ingest of the server's ladder from the box-registry sync.
     * Remote clients have no {@code _prices.json} of their own, so this is the
     * only way they learn the current tiers. A null argument means "no priced
     * tiers" (unlimited only).
     */
    public static void setQuoteCaps(int[] caps) {
        quoteCaps = caps != null ? caps.clone() : QuoteCaps.NONE;
    }

    /** True when {@code cap} is unlimited or one of the current tiers. */
    public static boolean isAllowedQuoteCap(int cap) {
        return QuoteCaps.isAllowed(cap, quoteCaps);
    }

    /**
     * Display fallback for a stored cap that is no longer in the ladder (the
     * table was edited between sessions): returns the cap unchanged when it is
     * still valid, otherwise {@link QuoteCaps#UNLIMITED}.
     */
    public static int normalizeQuoteCap(int cap) {
        return QuoteCaps.normalize(cap, quoteCaps);
    }
}