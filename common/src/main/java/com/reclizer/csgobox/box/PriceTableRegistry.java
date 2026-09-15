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
 */
public final class PriceTableRegistry {

    private static volatile PriceTable current = PriceTable.EMPTY;

    private PriceTableRegistry() {
    }

    public static void set(PriceTable table) {
        current = table != null ? table : PriceTable.EMPTY;
    }

    public static PriceTable get() {
        return current;
    }
}