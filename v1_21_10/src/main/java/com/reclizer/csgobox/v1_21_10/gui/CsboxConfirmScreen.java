package com.reclizer.csgobox.v1_21_10.gui;

import com.reclizer.csgobox.v1_21_10.utils.RenderFontTool;
import com.reclizer.csgobox.utils.OverlayColor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Second-step confirmation shown before a bulk open is actually requested.
 * The overview screen counts boxes/keys and navigates here; this screen
 * restates what will be consumed and only sends
 * {@link com.reclizer.csgobox.v1_21_10.packet.PacketCsgoBulkProgress} after
 * the player explicitly confirms.
 */
public class CsboxConfirmScreen extends Screen {
    private final Player player;
    private final ItemStack templateBox;
    private final int boxCount;
    private final int keyCount;
    private final int openableCount;

    public CsboxConfirmScreen(Player player, ItemStack templateBox, int boxCount, int keyCount, int openableCount) {
        super(Component.literal("csgo_bulk_confirm"));
        this.player = player;
        this.templateBox = templateBox;
        this.boxCount = boxCount;
        this.keyCount = keyCount;
        this.openableCount = openableCount;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int buttonWidth() {
        return Math.max(96, this.width * 12 / 100);
    }

    private int confirmButtonX() {
        return Math.max(8, this.width / 2 - buttonWidth() - 8);
    }

    private int backButtonX() {
        return this.width / 2 + 8;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
        if (this.minecraft != null && this.minecraft.level != null) {
            guiGraphics.fillGradient(0, 0, this.width, this.height,
                    OverlayColor.getBackgroundColor(), OverlayColor.getBackgroundColor());
        }
        renderLabels(guiGraphics);
        renderButtons(guiGraphics, mouseX, mouseY);
    }

    private void renderLabels(GuiGraphics guiGraphics) {
        Style titleStyle = Style.EMPTY.withBold(true);
        Component title = Component.translatable("gui.csgobox.bulk.confirm_title").withStyle(titleStyle);
        RenderFontTool.drawString(guiGraphics, this.font, title.getVisualOrderText(),
                (this.width - this.font.width(title)) * 0.5F, this.height * 0.22F, 0, 0, 1.4F, 0xFFFFFFFF);

        Component boxName = this.templateBox.getItem().getName(this.templateBox);
        int rowY = this.height * 36 / 100;
        int rowSpacing = this.font.lineHeight + 6;

        drawCentered(guiGraphics, Component.translatable("gui.csgobox.bulk.box_name", boxName.getString()),
                rowY, 0xFFEFEFEF);
        rowY += rowSpacing;
        drawCentered(guiGraphics, Component.translatable("gui.csgobox.bulk.box_count", this.boxCount),
                rowY, 0xFF55FF55);
        rowY += rowSpacing;
        drawCentered(guiGraphics, Component.translatable(
                        this.player.getAbilities().instabuild
                                ? "gui.csgobox.bulk.key_count_infinite"
                                : this.keyCount == Integer.MAX_VALUE
                                        ? "gui.csgobox.bulk.key_count_no_key"
                                        : "gui.csgobox.bulk.key_count",
                        this.keyCount == Integer.MAX_VALUE ? this.boxCount : this.keyCount),
                rowY, 0xFF55FF55);
        rowY += rowSpacing * 2;
        drawCentered(guiGraphics,
                Component.translatable("gui.csgobox.bulk.confirm_will_consume",
                        this.openableCount, this.openableCount)
                        .withStyle(Style.EMPTY.withBold(true)),
                rowY, 0xFFFFD700);
    }

    private void drawCentered(GuiGraphics guiGraphics, Component text, int y, int color) {
        FormattedCharSequence seq = text.getVisualOrderText();
        float w = this.font.width(seq);
        RenderFontTool.drawString(guiGraphics, this.font, seq,
                (this.width - w) * 0.5F, y, 0, 0, 1F, color);
    }

    private void renderButtons(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int btnY = this.height * 60 / 100;
        int btnH = this.height * 5 / 100;
        int confirmX = confirmButtonX();
        int backX = backButtonX();
        int w = buttonWidth();
        boolean confirmHover = isInside(mouseX, mouseY, confirmX, btnY, w, btnH);
        boolean backHover = isInside(mouseX, mouseY, backX, btnY, w, btnH);

        int confirmFill = confirmHover ? 0xFF00CC00 : 0xFF008800;
        int confirmBorder = confirmHover ? 0xFF00FF00 : 0xFF00AA00;
        drawButton(guiGraphics, confirmX, btnY, w, btnH, confirmFill, confirmBorder);
        int backFill = backHover ? 0xFFCC4444 : 0xFFAA0000;
        int backBorder = backHover ? 0xFFFF4444 : 0xFFFF0000;
        drawButton(guiGraphics, backX, btnY, w, btnH, backFill, backBorder);

        Style style = Style.EMPTY.withBold(true);
        drawCenteredText(guiGraphics, Component.translatable("gui.csgobox.bulk.confirm_confirm").withStyle(style),
                confirmX, btnY, w, btnH, 0.9F, 0xFFFFFFFF);
        drawCenteredText(guiGraphics, Component.translatable("gui.csgobox.bulk.confirm_back").withStyle(style),
                backX, btnY, w, btnH, 0.9F, 0xFFFFFFFF);
    }

    private void drawButton(GuiGraphics guiGraphics, int x, int y, int w, int h, int fillColor, int borderColor) {
        guiGraphics.fill(x, y, x + w, y + h, borderColor);
        guiGraphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, fillColor);
    }

    private void drawCenteredText(GuiGraphics guiGraphics, Component text,
                                   int x, int y, int w, int h, float scale, int color) {
        FormattedCharSequence seq = text.getVisualOrderText();
        float textW = this.font.width(seq) * scale;
        float textX = x + (w - textW) / 2.0F;
        float textY = y + (h - this.font.lineHeight * scale) / 2.0F + 1;
        RenderFontTool.drawString(guiGraphics, this.font, seq, textX, textY, 0, 0, scale, color);
    }

    private static boolean isInside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            double mouseX = event.x();
            double mouseY = event.y();
            int btnY = this.height * 60 / 100;
            int btnH = this.height * 5 / 100;
            int confirmX = confirmButtonX();
            int backX = backButtonX();
            int w = buttonWidth();
            if (isInside(mouseX, mouseY, confirmX, btnY, w, btnH) && this.openableCount > 0 && this.player != null) {
                long reqId = java.util.concurrent.ThreadLocalRandom.current().nextLong();
                net.minecraft.client.multiplayer.ClientPacketListener conn = net.minecraft.client.Minecraft.getInstance().getConnection();
                if (conn != null) {
                    conn.send(new net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket(
                            new com.reclizer.csgobox.v1_21_10.packet.PacketCsgoBulkProgress(reqId)));
                }
                Minecraft.getInstance().setScreen(new CsboxProgressScreen(this.player, reqId));
                return true;
            }
            if (isInside(mouseX, mouseY, backX, btnY, w, btnH)) {
                this.onClose();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == 256) {
            this.onClose();
            return true;
        }
        return super.keyPressed(event);
    }
}
