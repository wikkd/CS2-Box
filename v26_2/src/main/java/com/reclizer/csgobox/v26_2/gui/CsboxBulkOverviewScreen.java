package com.reclizer.csgobox.v26_2.gui;

import com.reclizer.csgobox.v26_2.CsgoBox;
import com.reclizer.csgobox.utils.GuiRegion;
import com.reclizer.csgobox.utils.OverlayColor;
import com.reclizer.csgobox.v26_2.utils.GuiItemMove;
import com.reclizer.csgobox.v26_2.utils.RenderFontTool;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import com.reclizer.csgobox.v26_2.item.ItemCsgoBox;
import com.reclizer.csgobox.v26_2.packet.PacketCsgoBulkProgress;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Total overview for a bulk box open. Shift+right-click the held box to
 * reach this screen; it counts matching boxes+keys client-side, lets the
 * player confirm, then sends the bulk request and opens the progress screen.
 */
public class CsboxBulkOverviewScreen extends Screen {
    private final Player player;
    private final ItemStack templateBox;
    private final Identifier keyId;

    private int boxCount;
    private int keyCount;
    private int openableCount;
    private long lastRecountTick = -1;

    private float rotX = 0;
    private float rotY = 0;

    public CsboxBulkOverviewScreen() {
        super(Minecraft.getInstance(), Minecraft.getInstance().font, Component.literal("csgo_bulk_overview"));
        this.player = this.minecraft != null ? this.minecraft.player : null;
        this.templateBox = this.player != null ? this.player.getItemInHand(InteractionHand.MAIN_HAND).copy() : ItemStack.EMPTY;
        Identifier resolvedKey = null;
        if (this.templateBox.getItem() instanceof ItemCsgoBox) {
            resolvedKey = ItemCsgoBox.getKey(this.templateBox);
        }
        this.keyId = resolvedKey;
        recount();
    }

    private void recount() {
        if (this.player == null) {
            boxCount = 0;
            keyCount = 0;
            openableCount = 0;
            return;
        }
        if (this.minecraft != null && this.minecraft.level != null) {
            long now = this.minecraft.level.getGameTime();
            if (lastRecountTick >= 0 && now - lastRecountTick < 10) {
                return;
            }
            lastRecountTick = now;
        }
        int totalBoxes = 0;
        int totalKeys = 0;
        boolean noKeyRequired = this.keyId == null || this.keyId.equals(Identifier.parse("minecraft:air"));
        for (ItemStack stack : this.player.getInventory().getNonEquipmentItems()) {
            if (stack.getItem() instanceof ItemCsgoBox
                    && ItemStack.isSameItemSameComponents(stack, this.templateBox)) {
                totalBoxes += stack.getCount();
            } else if (!noKeyRequired
                    && this.keyId.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))) {
                totalKeys += stack.getCount();
            }
        }
        this.boxCount = totalBoxes;
        this.keyCount = noKeyRequired ? totalBoxes : totalKeys;
        this.openableCount = Math.min(totalBoxes, this.keyCount);
        // Mirror the server-enforced bulkOpenCount cap (0 = unlimited) so the
        // UI never promises more than the server will actually open.
        int limit = CsgoBox.CONFIG.bulkOpenCount();
        if (limit > 0) {
            this.openableCount = Math.min(this.openableCount, limit);
        }
    }

    private static ItemStack keySample(Identifier keyId) {
        var ref = BuiltInRegistries.ITEM.get(keyId).orElse(null);
        if (ref == null) return ItemStack.EMPTY;
        return ref.value().getDefaultInstance();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int buttonWidth() {
        return GuiRegion.actionPair(this.width, this.height, 8)[0].w();
    }

    private int openButtonX() {
        return GuiRegion.actionPair(this.width, this.height, 8)[0].x();
    }

    private int backButtonX() {
        return GuiRegion.actionPair(this.width, this.height, 8)[1].x();
    }

    @Override
    public void tick() {
        super.tick();
        recount();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks) {
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTicks);
        if (this.minecraft != null && this.minecraft.level != null) {
            guiGraphics.fillGradient(0, 0, this.width, this.height,
                    OverlayColor.getBackgroundColor(), OverlayColor.getBackgroundColor());
        }
        render3DBox(guiGraphics, mouseX, mouseY);
        renderLabels(guiGraphics);
        renderButtons(guiGraphics, mouseX, mouseY);
    }

    private void render3DBox(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        if (this.templateBox.isEmpty() || this.player == null) return;

        int centerX = this.width / 2;
        int centerY = this.height * 42 / 100;
        int textureSize = Math.max(144, Math.min(this.width * 22 / 100, this.height * 30 / 100));
        float scale = textureSize / 16F;

        guiGraphics.fillGradient(centerX - textureSize / 2, centerY - textureSize / 2,
                centerX + textureSize / 2, centerY + textureSize / 2,
                OverlayColor.panel(), OverlayColor.panelHover());

        GuiItemMove.renderItemInInventoryFollowsMouse(guiGraphics,
                centerX - textureSize / 2, centerY - textureSize / 2,
                this.rotX, this.rotY, this.templateBox, this.player, scale);
    }

    // Preview geometry shared by render3DBox and mouseDragged
    private int previewTextureSize() {
        return GuiRegion.preview(this.width, this.height).w();
    }

    private int previewPixelX() {
        return GuiRegion.preview(this.width, this.height).x();
    }

    private int previewPixelY() {
        return GuiRegion.preview(this.width, this.height).y();
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        double mouseX = event.x();
        double mouseY = event.y();
        int size = previewTextureSize();
        int x = previewPixelX();
        int y = previewPixelY();
        boolean isInRange = mouseX >= x && mouseX <= x + size
                && mouseY >= y && mouseY <= y + size;
        if (event.button() == 0 && isInRange) {
            this.rotX = GuiItemMove.renderRotAngleX(dragX, this.rotX);
            this.rotY = GuiItemMove.renderRotAngleY(dragY, this.rotY);
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    private void renderLabels(GuiGraphicsExtractor guiGraphics) {
        Style titleStyle = Style.EMPTY.withBold(true);
        Component title = Component.translatable("gui.csgobox.bulk.title").withStyle(titleStyle);
        RenderFontTool.drawString(guiGraphics, this.font, title.getVisualOrderText(),
                (this.width - this.font.width(title)) * 0.5F, this.height * 0.10F, 0, 0, 1.6F, 0xFFFFFFFF);

        int rowY = this.height * 28 / 100;
        int rowSpacing = this.font.lineHeight + 6;
        Component boxName = this.templateBox.getItem().getName(this.templateBox);
        Style row = Style.EMPTY;
        drawCentered(guiGraphics, Component.translatable("gui.csgobox.bulk.box_name", boxName.getString()).withStyle(row),
                rowY, 0xFFEFEFEF);
        rowY += rowSpacing * 2;
        drawCentered(guiGraphics, Component.translatable("gui.csgobox.bulk.box_count", this.boxCount).withStyle(row),
                rowY, 0xFF55FF55);
        rowY += rowSpacing;
        if (this.keyId == null) {
            drawCentered(guiGraphics, Component.translatable("gui.csgobox.bulk.key_count_no_key", this.boxCount).withStyle(row),
                    rowY, 0xFF55FF55);
        } else {
            String keyDisplay = keyName(this.keyId);
            drawCentered(guiGraphics, Component.translatable("gui.csgobox.bulk.key_count", keyDisplay, this.keyCount).withStyle(row),
                    rowY, 0xFF55FF55);
        }
        rowY += rowSpacing;
        drawCentered(guiGraphics, Component.translatable("gui.csgobox.bulk.openable_count", this.openableCount).withStyle(row.withBold(true)),
                rowY, 0xFFFFD700);

        if (this.openableCount == 0) {
            rowY += rowSpacing * 2;
            drawCentered(guiGraphics, Component.translatable("gui.csgobox.bulk.cannot_open").withStyle(row.withBold(true)),
                    rowY, 0xFFFF4444);
        }
    }

    private void drawCentered(GuiGraphicsExtractor guiGraphics, Component text, int y, int color) {
        FormattedCharSequence seq = text.getVisualOrderText();
        float w = this.font.width(seq);
        RenderFontTool.drawString(guiGraphics, this.font, seq,
                (this.width - w) * 0.5F, y, 0, 0, 1F, color);
    }

    private static String keyName(Identifier keyId) {
        var item = BuiltInRegistries.ITEM.get(keyId).orElse(null);
        if (item == null) return keyId.toString();
        ItemStack sample = item.value().getDefaultInstance();
        return sample.getHoverName().getString();
    }

    private void renderButtons(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        int btnY = this.height * 78 / 100;
        int btnH = this.height * 5 / 100;
        int openX = openButtonX();
        int backX = backButtonX();
        int w = buttonWidth();
        boolean openHover = isInside(mouseX, mouseY, openX, btnY, w, btnH);
        boolean backHover = isInside(mouseX, mouseY, backX, btnY, w, btnH);
        boolean canOpen = this.openableCount > 0;
        int openFill = canOpen ? (openHover ? 0xFF00CC00 : 0xFF008800) : OverlayColor.panelDisabled();
        int openBorder = canOpen ? (openHover ? 0xFF00FF00 : 0xFF00AA00) : OverlayColor.dividerDim();
        drawButton(guiGraphics, openX, btnY, w, btnH, openFill, openBorder);
        int backFill = backHover ? 0xFFCC4444 : 0xFFAA0000;
        int backBorder = backHover ? 0xFFFF4444 : 0xFFFF0000;
        drawButton(guiGraphics, backX, btnY, w, btnH, backFill, backBorder);

        Style style = Style.EMPTY.withBold(true);
        drawCenteredText(guiGraphics, Component.translatable("gui.csgobox.bulk.confirm").withStyle(style),
                openX, btnY, w, btnH, 0.9F, canOpen ? 0xFFFFFFFF : 0xFFAAAAAA);
        drawCenteredText(guiGraphics, Component.translatable("gui.csgobox.csgo_box.back_box").withStyle(style),
                backX, btnY, w, btnH, 0.9F, 0xFFFFFFFF);
    }

    private void drawButton(GuiGraphicsExtractor guiGraphics, int x, int y, int w, int h, int fillColor, int borderColor) {
        guiGraphics.fill(x, y, x + w, y + h, borderColor);
        guiGraphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, fillColor);
    }

    private void drawCenteredText(GuiGraphicsExtractor guiGraphics, Component text,
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
            int btnY = this.height * 78 / 100;
            int btnH = this.height * 5 / 100;
            int openX = openButtonX();
            int backX = backButtonX();
            int w = buttonWidth();
            if (isInside(mouseX, mouseY, openX, btnY, w, btnH) && this.openableCount > 0 && this.player != null) {
                // Second step: confirmation screen restates the exact
                // consumption before the bulk request is sent.
                Minecraft.getInstance().setScreenAndShow(new CsboxConfirmScreen(
                        this.player, this.templateBox, this.boxCount, this.keyCount, this.openableCount));
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
