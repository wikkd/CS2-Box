package com.reclizer.csgobox.forge_1_20_1.gui;

import com.reclizer.csgobox.utils.GuiRegion;
import net.minecraft.util.FormattedCharSequence;
import com.reclizer.csgobox.utils.OverlayColor;
import com.reclizer.csgobox.forge_1_20_1.utils.AnimRenderOps;
import com.reclizer.csgobox.forge_1_20_1.utils.GuiItemMove;
import com.reclizer.csgobox.forge_1_20_1.utils.RenderFontTool;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import com.reclizer.csgobox.forge_1_20_1.CsgoBox;
import com.reclizer.csgobox.forge_1_20_1.item.ItemCsgoBox;
import com.reclizer.csgobox.forge_1_20_1.packet.Networking;
import com.reclizer.csgobox.forge_1_20_1.packet.PacketCsgoBulkProgress;

import java.util.concurrent.ThreadLocalRandom;



/**
 * Total overview for a bulk box open. Shift+right-click the held box to
 * reach this screen; it counts matching boxes+keys client-side, lets the
 * player confirm, then sends the bulk request and opens the progress screen.
 */
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
        // 1.20.1 Screen only injects `minecraft` in init(Minecraft,...), not in the
        // constructor, so resolve the client singleton directly here.
        this.player = Minecraft.getInstance().player;
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
        int totalKeys = 0;
        boolean noKeyRequired = this.keyId == null || this.keyId.equals(new ResourceLocation("minecraft:air"));
        for (ItemStack stack : this.player.getInventory().items) {
            if (stack.getItem() instanceof ItemCsgoBox
                    && ItemStack.isSameItemSameTags(stack, this.templateBox)) {
                totalBoxes += stack.getCount();
            } else if (!noKeyRequired
                    && this.keyId.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))) {
                totalKeys += stack.getCount();
            }
        }
        for (ItemStack stack : this.player.getInventory().offhand) {
            if (stack.getItem() instanceof ItemCsgoBox
                    && ItemStack.isSameItemSameTags(stack, this.templateBox)) {
                totalBoxes += stack.getCount();
            } else if (!noKeyRequired
                    && this.keyId.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))) {
                totalKeys += stack.getCount();
            }
        }
        this.boxCount = totalBoxes;
        this.keyCount = this.player.getAbilities().instabuild
                ? Integer.MAX_VALUE
                : (noKeyRequired ? totalBoxes : totalKeys);
        this.openableCount = Math.min(totalBoxes, this.keyCount);
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
            int fill = UiBackdrop.fill();
            AnimRenderOps.fillGradient(guiGraphics, 0, 0, this.width, this.height, fill, fill);
        }
        render3DBox(guiGraphics, mouseX, mouseY);
        renderLabels(guiGraphics);
        renderButtons(guiGraphics, mouseX, mouseY);
    }

    private void render3DBox(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (this.templateBox.isEmpty() || this.player == null) return;

        GuiRegion.Region preview = GuiRegion.preview(this.width, this.height);
        int centerX = preview.centerX();
        int centerY = preview.centerY();
        int textureSize = preview.w();
        float scale = textureSize / 16F;

        guiGraphics.fillGradient(preview.x(), preview.y(),
                preview.right(), preview.bottom(),
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
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        int size = previewTextureSize();
        int x = previewPixelX();
        int y = previewPixelY();
        boolean isInRange = mouseX >= x && mouseX <= x + size
                && mouseY >= y && mouseY <= y + size;
        if (button == 0 && isInRange) {
            this.rotY = GuiItemMove.renderRotAngleY(dragX, this.rotY);
            this.rotX = GuiItemMove.renderRotAngleX(dragY, this.rotX);
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    private void renderLabels(GuiGraphics guiGraphics) {
        Style titleStyle = Style.EMPTY.withBold(true);
        Component title = Component.translatable("gui.csgobox.bulk.title").withStyle(titleStyle);
        float titleScale = 1.6F;
        float titleW = this.font.width(title) * titleScale;
        RenderFontTool.drawString(guiGraphics, this.font, title.getVisualOrderText(),
                (this.width - titleW) * 0.5F, this.height * 0.10F, 0, 0, titleScale, 0xFFFFFFFF);

        // Info rows start below the 3D preview region (GuiRegion.preview).
        // Use a more compact starting position to avoid overlapping with buttons.
        GuiRegion.Region preview = GuiRegion.preview(this.width, this.height);
        int rowY = preview.bottom() + 12;
        int rowSpacing = this.font.lineHeight + 6;
        // Ensure rowY doesn't go below 45% of height to leave room for buttons
        int minRowY = this.height * 45 / 100;
        if (rowY < minRowY) {
            rowY = minRowY;
        }
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
        var item = BuiltInRegistries.ITEM.get(keyId);
        if (item == null) return keyId.toString();
        ItemStack sample = item.getDefaultInstance();
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
                // Directly request the bulk open. The server re-validates
                // inventory and consumption authoritatively; no separate
                // confirmation screen is shown.
                long reqId = ThreadLocalRandom.current().nextLong();
                Networking.sendToServer(new PacketCsgoBulkProgress(reqId));
                Minecraft.getInstance().setScreen(new CsboxProgressScreen(this.player, reqId));
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
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
