package com.reclizer.csgobox.v1_21_1.gui.terminal;

import com.reclizer.csgobox.terminal.NegotiationModel;
import com.reclizer.csgobox.terminal.TerminalAnims;
import com.reclizer.csgobox.terminal.TerminalPalette;
import com.reclizer.csgobox.v1_21_1.utils.AnimRenderOps;
import com.reclizer.csgobox.v1_21_1.utils.RenderFontTool;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Region 9+10+11: countdown (flip on second change), random-item slot
 * (2.5s cycle with scale pop, real MC items via renderItem2D) and the
 * collection strip (xp name + rarity dots, filled with glow / hollow).
 *
 * era: decoupled
 */
public final class TerminalBottomRow {

    public static final ResourceLocation TEX_CIRCLE_GLOW = ResourceLocation.fromNamespaceAndPath("csgobox", "gui/terminal/terminal_circle_glow");

    /** 10 random items cycled by region 10 (HTML MC_ITEMS). */
    private static final ItemStack[] MC_ITEMS = {
            new ItemStack(Items.GRASS_BLOCK),
            new ItemStack(Items.DIAMOND),
            new ItemStack(Items.GOLD_INGOT),
            new ItemStack(Items.APPLE),
            new ItemStack(Items.TORCH),
            new ItemStack(Items.ENDER_PEARL),
            new ItemStack(Items.CREEPER_HEAD),
            new ItemStack(Items.BREAD),
            new ItemStack(Items.REDSTONE),
            new ItemStack(Items.IRON_SWORD),
    };

    private String lastCountdown = "";
    private long countdownFlipAtMs;

    public void render(GuiGraphics gg, int x0, int y0, int x1, int y1,
                       long nowMs, NegotiationModel model, Player player) {
        AnimRenderOps.fill(gg, x0, y0, x1, y1, 0xFF1F2428);
        AnimRenderOps.fill(gg, x0, y0, x1, y0 + 1, 0xFF39444C);
        Font font = Minecraft.getInstance().font;
        int midY = (y0 + y1) >> 1;

        // ---- region 9: countdown ----
        int cw = Math.round((x1 - x0) * 0.20F);
        int cx0 = x0 + (int) ((x1 - x0) * 0.035F);
        RenderFontTool.drawSpacedText(gg, font,
                Component.translatable("csgobox.terminal.validity").getString(),
                cx0, y0 + 4, 2F, 1.5F, TerminalPalette.TITLE);
        String text = TerminalAnims.countdownText(model.countdownMs());
        boolean expired = model.countdownMs() <= 0;
        if (!text.equals(lastCountdown)) {
            lastCountdown = text;
            countdownFlipAtMs = nowMs;
        }
        float flip = TerminalAnims.counterFlip(nowMs, countdownFlipAtMs);
        float slide = (1F - flip) * 8F;
        int color = expired ? TerminalPalette.COUNT_EXPIRED : TerminalPalette.WHITE;
        int digitsW = Math.round(font.width(text) * 2.625F) + 1 * (text.length() - 1);
        RenderFontTool.drawSpacedText(gg, font, text,
                cx0 + 2 + (cw - digitsW) / 2F, midY - 8 + slide, 0.5F, 2.625F, color);

        // ---- region 10: random item slot (2.5s cycle) ----
        int slotX = x0 + (int) ((x1 - x0) * 0.30F);
        int slotW = Math.round((x1 - x0) * 0.075F);
        int slotCy = midY;
        long slotStart = nowMs - (nowMs % TerminalAnims.SLOT_SWAP_MS);
        int idx = TerminalAnims.slotIndex(nowMs, MC_ITEMS.length);
        float pop = TerminalAnims.swapPop(nowMs, slotStart);
        // badge glow + ring
        AnimRenderOps.blitTextured(gg, TEX_CIRCLE_GLOW, slotX - slotW / 2, slotCy - slotW / 2,
                slotW, slotW, 128, 128);
        AnimRenderOps.fill(gg, slotX - slotW / 2, slotCy - slotW / 2,
                slotX - slotW / 2 + slotW, slotCy - slotW / 2 + 1, 0xFF39444C);
        AnimRenderOps.fill(gg, slotX - slotW / 2, slotCy + slotW / 2 - 1,
                slotX + slotW / 2, slotCy + slotW / 2, 0xFF39444C);
        AnimRenderOps.fill(gg, slotX - slotW / 2, slotCy - slotW / 2,
                slotX - slotW / 2 + 1, slotCy + slotW / 2, 0xFF39444C);
        AnimRenderOps.fill(gg, slotX + slotW / 2 - 1, slotCy - slotW / 2,
                slotX + slotW / 2, slotCy + slotW / 2, 0xFF39444C);
        float scale = slotW / 2.2F * (0.55F + 0.45F * pop);
        AnimRenderOps.renderItem2D(player, gg, MC_ITEMS[idx],
                slotX, slotCy, Math.max(1F, scale / 16F));

        // ---- region 11: collection strip (xp name + rarity dots) ----
        int xpX = x0 + (int) ((x1 - x0) * 0.50F);
        RenderFontTool.drawSpacedText(gg, font,
                Component.translatable("csgobox.terminal.collection").getString(),
                xpX, y0 + 4, 2F, 1.5F, TerminalPalette.TITLE);
        int dotY = midY - 4;
        int dotX = xpX;
        for (NegotiationModel.DotGroup g : NegotiationModel.DOT_GROUPS) {
            for (int v : g.pattern) {
                drawDot(gg, dotX, dotY, v == 1, g.color);
                dotX += 14;
            }
            dotX += 8;
        }
    }

    /** FormattedCharSequence wrapper for plain strings. */
    private static FormattedCharSequence fcs(String s) {
        return FormattedCharSequence.forward(s, Style.EMPTY);
    }

    /** Filled dot with glow / hollow dot (1px ring). */
    private void drawDot(GuiGraphics gg, int x, int y, boolean filled, int color) {
        if (filled) {
            AnimRenderOps.blitTextured(gg, TEX_CIRCLE_GLOW, x - 3, y - 3, 12, 12, 128, 128);
            AnimRenderOps.fill(gg, x, y, x + 6, y + 6, color);
        } else {
            AnimRenderOps.fill(gg, x, y, x + 6, y + 1, color);
            AnimRenderOps.fill(gg, x, y + 5, x + 6, y + 6, color);
            AnimRenderOps.fill(gg, x, y, x + 1, y + 6, color);
            AnimRenderOps.fill(gg, x + 5, y, x + 6, y + 6, color);
        }
    }
}
