package com.reclizer.csgobox.v26_2.gui.terminal;

import com.reclizer.csgobox.terminal.NegotiationModel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Resolves the ACTUAL offered item for each negotiation round from the
 * terminal box's grade pools, so the offer card thumb (region 5), the 3D
 * preview + name + rarity (region 8) and the bottom slot (region 10) all
 * show the same real item. One sample per round; one fixed slot item per
 * terminal session.
 *
 * era: decoupled
 */
public final class TerminalOfferItems {

    /** Armory Point offer price per box grade (recycle yield × 2, integers only). */
    private static final int[] GRADE_PRICE = {6, 10, 16, 22, 30};

    private static List<ItemStack>[] gradePools;
    private static final Map<Integer, ItemStack> ROUND_ITEM = new HashMap<>();
    private static final Map<Integer, Integer> ROUND_GRADE = new HashMap<>();
    private static ItemStack sessionItem;
    private static final Random RND = new Random();

    private TerminalOfferItems() {
    }

    public static void setGradePools(List<ItemStack>[] pools) {
        gradePools = pools;
        ROUND_ITEM.clear();
        ROUND_GRADE.clear();
        sessionItem = null;
    }

    /** 每轮一次取样（缓存），等级为空时向下退级，全空回退铁剑。 */
    public static ItemStack itemFor(NegotiationModel.Offer offer) {
        return ROUND_ITEM.computeIfAbsent(offer.round(), r -> {
            for (int g = gradeForOffer(offer); g >= 1; g--) {
                List<ItemStack> pool = gradePools != null && g < gradePools.length
                        ? gradePools[g] : null;
                if (pool != null && !pool.isEmpty()) {
                    ROUND_GRADE.put(r, g);
                    return pool.get(RND.nextInt(pool.size())).copy();
                }
            }
            ROUND_GRADE.put(r, 1);
            return new ItemStack(Items.IRON_SWORD);
        });
    }

    /** Box grade (1..5) of the round's offered item. */
    public static int gradeFor(NegotiationModel.Offer offer) {
        itemFor(offer); // ensure sampled & cached
        return ROUND_GRADE.getOrDefault(offer.round(), 1);
    }

    /** Display name of the round's actual offered item. */
    public static String nameFor(NegotiationModel.Offer offer) {
        return itemFor(offer).getHoverName().getString();
    }

    /** Rarity tier key ("mil_spec".."contraband") of the offered item. */
    public static String rarityKeyFor(NegotiationModel.Offer offer) {
        return NegotiationModel.rarityKeyForGrade(gradeFor(offer));
    }

    /** Whole Armory Point price of the offered item (no decimals). */
    public static int priceFor(NegotiationModel.Offer offer) {
        int g = gradeFor(offer);
        return GRADE_PRICE[Math.max(0, Math.min(g - 1, GRADE_PRICE.length - 1))];
    }

    /** One fixed random item per terminal session (region 10 slot). */
    public static ItemStack sessionItem() {
        if (sessionItem == null) {
            List<ItemStack> all = new ArrayList<>();
            if (gradePools != null) {
                for (int g = 1; g < gradePools.length; g++) {
                    if (gradePools[g] != null) {
                        all.addAll(gradePools[g]);
                    }
                }
            }
            sessionItem = all.isEmpty()
                    ? new ItemStack(Items.DIAMOND)
                    : all.get(RND.nextInt(all.size())).copy();
        }
        return sessionItem;
    }

    /**
     * Script skin -> box grade: random base grade 1..5 so every rarity tier
     * (军规级..违禁) can show up; {@link #itemFor} falls back down the pools
     * when the sampled tier is empty.
     */
    private static int gradeForOffer(NegotiationModel.Offer offer) {
        return 1 + RND.nextInt(5);
    }
}
