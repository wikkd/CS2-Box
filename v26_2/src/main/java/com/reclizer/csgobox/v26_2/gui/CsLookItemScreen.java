package com.reclizer.csgobox.v26_2.gui;

import com.reclizer.csgobox.v26_2.CsgoBox;
import com.reclizer.csgobox.v26_2.sounds.ModSounds;
import com.reclizer.csgobox.v26_2.utils.ButtonPalette;
import com.reclizer.csgobox.utils.ColorTools;
import com.reclizer.csgobox.v26_2.utils.GuiItemMove;
import com.reclizer.csgobox.utils.OverlayColor;
import com.reclizer.csgobox.v26_2.utils.RenderFontTool;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class CsLookItemScreen extends Screen {
    private final Player player;
    private final ItemStack openItem;
    private final int grade;
    private float rotX = 0;
    private float rotY = 0;

    /** Displays the server-authoritative reward after the progress animation completes. */
    public CsLookItemScreen(ItemStack item, int grade) {
        super(Minecraft.getInstance(), Minecraft.getInstance().font, Component.literal("look_item"));
        this.player = Minecraft.getInstance().player;
        this.openItem = item == null ? ItemStack.EMPTY : item.copy();
        this.grade = grade;
        if (this.player != null && !this.openItem.isEmpty()) {
            float vol = CsgoBox.CONFIG.finishSoundVolume() / 100F;
            if (vol > 0) {
                player.playSound(ModSounds.CS_FINSH.get(), vol * 10F, 1F);
            }
        }
    }

    private int backButtonWidth() {
        return Math.max(64, this.width * 7 / 100);
    }

    private int backButtonX() {
        return Math.max(8, this.width - backButtonWidth() - 16);
    }

    // Preview geometry shared by renderBg (positions the PIP render state)
    // and mouseDragged (keeps the drag-detection rectangle in lock-step with
    // what the user actually sees). Mirrors CsboxScreen's helper trio so the
    // result screen and the box-opening screen render the same held item at
    // the same on-screen pixel size.
    private int previewTextureSize() {
        int containerTop = this.height * 22 / 100;
        int containerBottom = this.height * 88 / 100;
        int containerHeight = containerBottom - containerTop;
        return Math.max(144, Math.min(this.width * 28 / 100, containerHeight * 72 / 100));
    }

    private int previewPixelX() {
        return (this.width - previewTextureSize()) / 2;
    }

    private int previewPixelY() {
        // Container = vertical band between the grade bar and the divider
        // above the button. Centre the crate within it so the result focus
        // sits in the visual middle of the screen.
        int containerTop = this.height * 22 / 100;
        int containerBottom = this.height * 88 / 100;
        return (containerTop + containerBottom - previewTextureSize()) / 2;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks) {
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTicks);
        renderLookBackground(guiGraphics);
        // renderBg before renderLabels: button rectangle must be drawn first,
        // then the centered button text on top. Reversed order (the previous
        // behaviour) caused the text to be hidden behind the rectangle.
        // Title (y=5%) and grade label (y=11%) sit in their own band, so
        // swapping the two render calls has no other z-order side-effects.
        renderBg(guiGraphics, mouseX, mouseY);
        renderLabels(guiGraphics, mouseX, mouseY);
    }

    private void renderLookBackground(GuiGraphicsExtractor guiGraphics) {
        if (this.minecraft != null && this.minecraft.level != null) {
            guiGraphics.fillGradient(0, 0, this.width, this.height,
                    OverlayColor.getBackgroundColor(), OverlayColor.getBackgroundColor());
        }
    }

    private void renderBg(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        // hideGui removed in MC 26.2 — see CsboxScreen.onClose() comment.
        if (openItem.isEmpty()) return;

        float scale = previewTextureSize() / 16F;
        guiGraphics.fill(this.width * 25 / 100, this.height * 92 / 100,
                this.width * 75 / 100, this.height * 92 / 100 + 1, 0xFFD3D3D3);
        guiGraphics.fill(this.width * 33 / 100, this.height * 17 / 100,
                this.width * 67 / 100, this.height * 17 / 100 + 4, ColorTools.colorItems(grade));
        // Centre the result preview in the available band; mouseDragged below
        // uses the same previewPixelX/Y/size so drag-detection matches what
        // the user sees.
        GuiItemMove.renderItemInInventoryFollowsMouse(guiGraphics,
                previewPixelX(), previewPixelY(),
                this.rotX, this.rotY, openItem, this.player, scale);

        int btnX = backButtonX();
        int btnY = this.height * 94 / 100;
        int btnW = backButtonWidth();
        int btnH = this.height * 5 / 100;
        boolean hoverButton = ButtonPalette.isInside(mouseX, mouseY, btnX, btnY, btnW, btnH);
        ButtonPalette.drawButton(guiGraphics, ButtonPalette.DANGER, btnX, btnY, btnW, btnH, hoverButton);
    }

    private void renderLabels(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        if (openItem.isEmpty()) return;

        Style style = Style.EMPTY.withBold(true);
        int titleMaxWidth = Math.round(this.width * 54F / 100F);
        float titleScale = 1.6F;
        float titleX = centeredTextX(openItem.getItem().getName(openItem).getString(), titleScale, titleMaxWidth);
        RenderFontTool.drawStringClamped(guiGraphics, this.font, openItem.getItem().getName(openItem),
                titleX, this.height * 5F / 100F, 0, 0, titleScale,
                titleMaxWidth, 0xFFFFFFFF);
        float gradeX = centeredTextX(Component.translatable("gui.csgobox.csgo_box.grade" + grade).getString(), 1.0F,
                Math.round(this.width * 24F / 100F));
        renderText(guiGraphics,
                Component.translatable("gui.csgobox.csgo_box.grade" + grade).getVisualOrderText(),
                gradeX, this.height * 11.5F / 100F, 1F);
        // Button text colour tracks the panel painted in renderBg: hovered
        // button -> textColorHover, otherwise -> textColor. The previous
        // implementation used a constant white that clashed with the warm
        // danger fill.
        int bx = backButtonX();
        int by = this.height * 94 / 100;
        int bw = backButtonWidth();
        int bh = this.height * 5 / 100;
        boolean backHover = ButtonPalette.isInside(mouseX, mouseY, bx, by, bw, bh);
        int backText = backHover ? ButtonPalette.DANGER.textColorHover() : ButtonPalette.DANGER.textColor();
        renderCenteredText(guiGraphics,
                Component.translatable("gui.csgobox.csgo_box.back_box").withStyle(style).getVisualOrderText(),
                bx, by, bw, bh, 0.8F, backText);
    }

    private void renderText(GuiGraphicsExtractor guiGraphics, FormattedCharSequence text, float x, float y, float scale) {
        RenderFontTool.drawString(guiGraphics, this.font, text, x, y, 0, 0, scale, 0xFFFFFFFF);
    }

    private float centeredTextX(String text, float scale, int maxWidth) {
        float renderedWidth = Math.min(this.font.width(text) * scale, maxWidth);
        return (this.width - renderedWidth) / 2.0F;
    }

    private void renderCenteredText(GuiGraphicsExtractor guiGraphics, FormattedCharSequence text,
                                    int x, int y, int w, int h, float scale, int color) {
        float textX = x + (w - this.font.width(text) * scale) / 2.0F;
        float textY = y + (h - this.font.lineHeight * scale) / 2.0F + 1;
        RenderFontTool.drawString(guiGraphics, this.font, text, textX, textY, 0, 0, scale, color);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        double mouseX = event.x();
        double mouseY = event.y();
        // Match the actual rendered crate rectangle (centred horizontally
        // and vertically inside the 16%–92% container). The previous code
        // used width*37% + frameWidth as the centre of an upper-left
        // rectangle, which no longer matched the centred draw site.
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

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int btnX = backButtonX();
        int btnY = this.height * 94 / 100;
        int btnW = backButtonWidth();
        int btnH = this.height * 5 / 100;
        if (event.button() == 0 && ButtonPalette.isInside(event.x(), event.y(), btnX, btnY, btnW, btnH)) {
            this.onClose();
            return true;
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

    @Override
    public void onClose() {
        // hideGui removed in MC 26.2 — see CsboxScreen.onClose() comment.
        super.onClose();
    }

    @Override
    public final void tick() {
        super.tick();
        if (this.minecraft == null || this.minecraft.player == null) return;
        if (!this.minecraft.player.isAlive() || this.minecraft.player.isRemoved()) {
            this.onClose();
        }
    }
}
