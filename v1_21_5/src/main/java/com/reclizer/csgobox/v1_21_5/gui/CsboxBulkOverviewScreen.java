package com.reclizer.csgobox.v1_21_5.gui;

import com.reclizer.csgobox.v1_21_5.CsgoBox;
import com.reclizer.csgobox.utils.GuiRegion;
import com.reclizer.csgobox.utils.OverlayColor;
import com.reclizer.csgobox.v1_21_5.utils.GuiItemMove;
import com.reclizer.csgobox.v1_21_5.utils.RenderFontTool;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import com.reclizer.csgobox.v1_21_5.item.ItemCsgoBox;
import com.reclizer.csgobox.v1_21_5.packet.PacketCsgoBulkProgress;

import java.util.concurrent.ThreadLocalRandom;

public class CsboxBulkOverviewScreen extends Screen {
    private final Player player;
    private final ItemStack templateBox;
    private final ResourceLocation keyId;

    private int boxCount;
    private int keyCount;
    private int openableCount;
    private long lastRecountTick = -1;

    private float rotX = 0;
    private float rotY = 0;

    public CsboxBulkOverviewScreen() {
        super(Component.literal("csgo_bulk_overview"));
        this.minecraft = Minecraft.getInstance();
        this.player = this.minecraft != null ? this.minecraft.player : null;
        this.templateBox = this.player != null ? this.player.getItemInHand(InteractionHand.MAIN_HAND).copy() : ItemStack.EMPTY;
        ResourceLocation resolvedKey = null;
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
        for (int i = 0; i < 36; i++) {
            ItemStack stack = this.player.getInventory().getItem(i);
            if (stack.getItem() instanceof ItemCsgoBox
                    && ItemStack.isSameItemSameComponents(stack, this.templateBox)) {
                totalBoxes += stack.getCount();
            }
        }
        int totalKeys;
        if (this.keyId == null || "minecraft:air".equals(this.keyId.toString())) {
            totalKeys = Integer.MAX_VALUE;
        } else {
            totalKeys = 0;
            for (int i = 0; i < 36; i++) {
                ItemStack stack = this.player.getInventory().getItem(i);
                if (this.keyId.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))) {
                    totalKeys += stack.getCount();
                }
            }
        }
        this.boxCount = totalBoxes;
        this.keyCount = this.player.getAbilities().instabuild
                ? Integer.MAX_VALUE
                : ((totalKeys == Integer.MAX_VALUE) ? totalBoxes : totalKeys);
        this.openableCount = Math.min(totalBoxes, totalKeys == Integer.MAX_VALUE ? totalBoxes : totalKeys);
        // Mirror the server-enforced bulkOpenCount cap (0 = unlimited) so the
        // UI never promises more than the server will actually open.
        int limit = CsgoBox.CONFIG.bulkOpenCount();
        if (limit > 0) {
            this.openableCount = Math.min(this.openableCount, limit);
        }
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
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
        if (this.minecraft != null && this.minecraft.level != null) {
            guiGraphics.fillGradient(0, 0, this.width, this.height,
                    OverlayColor.getBackgroundColor(), OverlayColor.getBackgroundColor());
        }
        render3DBox(guiGraphics, mouseX, mouseY);
        renderLabels(guiGraphics);
        renderButtons(guiGraphics, mouseX, mouseY);
    }

    private void render3DBox(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (this.templateBox.isEmpty() || this.player == null) return;

        int centerX = this.width / 2;
        int centerY = this.height * 42 / 100;
        float frameWidth = this.width * 22 / 100F;
        float scale = frameWidth / 16F;

        guiGraphics.fillGradient(centerX - (int) frameWidth, centerY - (int) (frameWidth * 0.8F),
                centerX + (int) frameWidth, centerY + (int) (frameWidth * 0.8F),
                OverlayColor.panel(), OverlayColor.panelHover());

        GuiItemMove.renderItemInInventoryFollowsMouse(guiGraphics, centerX, centerY,
                this.rotX, this.rotY, this.templateBox, this.player, scale);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        float frameWidth = this.width * 22F / 100F;
        float itemCenterX = this.width / 2F;
        float itemCenterY = this.height * 42F / 100F;
        float range = frameWidth * 0.7F;
        boolean isInRange = mouseX >= itemCenterX - range && mouseX <= itemCenterX + range
                && mouseY >= itemCenterY - range && mouseY <= itemCenterY + range;
        if (button == 0 && isInRange) {
            this.rotX = GuiItemMove.renderRotAngleX(dragX, this.rotX);
            this.rotY = GuiItemMove.renderRotAngleY(dragY, this.rotY);
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    private void renderLabels(GuiGraphics guiGraphics) {
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
        String keyDisplay = (this.keyId == null) ? "—" : keyName(this.keyId);
        if (this.keyId != null && this.player.getAbilities().instabuild) {
            drawCentered(guiGraphics, Component.translatable("gui.csgobox.bulk.key_count_infinite").withStyle(row),
                    rowY, 0xFF55FF55);
        } else if (this.keyId == null) {
            drawCentered(guiGraphics, Component.translatable("gui.csgobox.bulk.key_count_no_key", this.boxCount).withStyle(row),
                    rowY, 0xFF55FF55);
        } else {
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

    private void drawCentered(GuiGraphics guiGraphics, Component text, int y, int color) {
        FormattedCharSequence seq = text.getVisualOrderText();
        float w = this.font.width(seq);
        RenderFontTool.drawString(guiGraphics, this.font, seq,
                (this.width - w) * 0.5F, y, 0, 0, 1F, color);
    }

    private static String keyName(ResourceLocation keyId) {
        var ref = BuiltInRegistries.ITEM.get(keyId).orElse(null);
        if (ref == null) return keyId.toString();
        ItemStack sample = ref.value().getDefaultInstance();
        return sample.getHoverName().getString();
    }

    private void renderButtons(GuiGraphics guiGraphics, int mouseX, int mouseY) {
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
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int btnY = this.height * 78 / 100;
            int btnH = this.height * 5 / 100;
            int openX = openButtonX();
            int backX = backButtonX();
            int w = buttonWidth();
            if (isInside(mouseX, mouseY, openX, btnY, w, btnH) && this.openableCount > 0 && this.player != null) {
                // Second step: confirmation screen restates the exact
                // consumption before the bulk request is sent.
                Minecraft.getInstance().setScreen(new CsboxConfirmScreen(
                        this.player, this.templateBox, this.boxCount, this.keyCount, this.openableCount));
                return true;
            }
            if (isInside(mouseX, mouseY, backX, btnY, w, btnH)) {
                this.onClose();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int key, int b, int c) {
        if (key == 256) {
            this.onClose();
            return true;
        }
        return super.keyPressed(key, b, c);
    }
}
